# spec.md — 같이가 1단계: 트립 로그 MVP

> 범위: 2026-11-15 1차 접수까지. 차량 1대, 서버 없음.
> 이 앱을 만드는 이유와 원칙은 [intent.md](intent.md)에 있다.
> 표기: **[확인 필요]** 는 문서로 확인하지 못해 에뮬레이터에서 직접 확인해야 하는 항목이다.

---

## 1. 1단계 범위

### 포함
| ID | 기능 | 요약 |
|---|---|---|
| F1 | 여행 시작 | 인원수를 받고, 출발 시점의 주행거리와 에너지 %를 저장한다 |
| F2 | 주행 중 기록 | 주행거리와 에너지 %를 구독해 구간 기록을 쌓는다 |
| F3 | 주유·충전 감지 | 정차 중 에너지 %가 오르면 주유(충전)로 판단하고 금액을 묻는다 |
| F4 | 비용 입력 | 통행료, 주차비 등 기타 비용을 정차 중에 입력한다 |
| F5 | 여행 일시정지·종료 | 일시정지 중에는 거리와 사용량을 집계하지 않는다 |
| F6 | 차량 1대 정산 | 총비용을 인원수로 나눠 1인당 금액과 운전자에게 보낼 금액을 보여 준다 |
| F7 | 주행 중 화면 잠금 | 주행 중에는 한 줄 정보만 보여 주고 터치 입력을 막는다 |
| F8 | 음성 입출력 | STT·TTS를 `ON_DEVICE` 모드로 시도하고, 안 되면 터치로 대체한다 |
| F9 | 차량 설정 | 연료 종류, 탱크 용량(배터리 kWh), 단가를 설정한다 |
| F10 | 데모 모드 | Virtual Console로 바꿀 수 없는 값을 앱 안에서 시뮬레이션한다 |

### 제외 (2단계 이후)
- 컨보이 모드, 서버 동기화, 그룹 코드·QR 참여
- 여러 차량 합산 정산, QR 모바일 웹 정산서, 송금 링크
- 함께 쉬기 제안, NaviHelper 경유지 추가, FusedLocation 경로 기록
- 시동 기준 일차 자동 분리. 데이터 모델에 `dayIndex`만 준비해 둔다.
- 오피넷 유가 연동, 월간 리포트

---

## 2. 기술 스택

| 항목 | 값 | 비고 |
|---|---|---|
| 언어 | Kotlin 2.4.x (AGP 9 내장 Kotlin) | |
| UI | Jetpack Compose (Material 3) | |
| 빌드 | Gradle 9.6 (Kotlin DSL), AGP 9.5, Android Studio 2026.2 번들 JDK 25 | 주최 측 요건: Gradle 8.0 이상. 번들 JDK 25에서 돌아가는 버전으로 맞췄다 |
| SDK 버전 | minSdk 28, compileSdk 37, targetSdk 34 | minSdk 28은 차량 보안 요건. targetSdk는 에뮬레이터 이미지와 같은 API 34. compileSdk는 최신 AndroidX 요구 |
| 로컬 DB | Room (KSP) | |
| 비동기 | Kotlin Coroutines, Flow | SDK 콜백은 `callbackFlow`로 감싼다 |
| DI | 수동 DI (`AppContainer`) | 범위가 작아 Hilt는 쓰지 않는다 |
| 차량 SDK | `ai.pleos.playground:Vehicle:2.3.2` | 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/` |
| 음성 SDK | `ai.pleos.playground:SpeechToText:3.3.1`, `ai.pleos.playground:TextToSpeech:3.3.1` | Vehicle과 같은 저장소. ON_DEVICE 모드라 앱 등록(Client ID/Secret)은 쓰지 않는다 |
| 앱 이름·패키지명 | 같이가 (GachiGa), `com.gachiga.app` | 2026-11-11 확정. 부제 "달린 만큼 나누는 그룹 여행" |

---

## 3. 아키텍처

```
ui (Compose 화면, ViewModel)
  └─ domain (TripEngine: 상태 머신, 주유 감지, 정산 계산 — 순수 Kotlin)
       ├─ data/trip   (Room: Trip, Segment, Expense, Refuel)
       ├─ data/vehicle (VehicleDataSource 인터페이스)
       │     ├─ PleosVehicleDataSource   — Vehicle SDK
       │     ├─ CarPropertyMotionSource  — 속도·기어 (확인되면)
       │     └─ DemoVehicleDataSource    — 데모 모드
       └─ data/voice  (VoiceIO 인터페이스)
             ├─ PleosVoiceIO   — STT·TTS ON_DEVICE
             └─ NoopVoiceIO    — 음성 미지원 시 (터치 대체)
```

- 정산 계산과 주유 감지 로직은 Android 의존성이 없는 순수 Kotlin으로 두고 단위 테스트한다.
- 기록은 앱 프로세스 범위의 `TripRecorder`가 한다. 화면이 꺼지거나 다른 앱으로 가도 프로세스가 살아 있는 동안 기록이 이어지고, 프로세스가 다시 뜨면 진행 중인 여행을 DB에서 이어받는다.
  - 포그라운드 서비스는 1단계에서 쓰지 않는다. Android 14(targetSdk 34)에서는 서비스 유형과 그에 맞는 권한(`FOREGROUND_SERVICE_SPECIAL_USE` 등)이 더 필요하고, App Market 권한 심사 대상이 늘어나기 때문이다. 실제 차량에서 프로세스가 정리되는 일이 잦으면 2단계에서 다시 검토한다.

### 3.1 VehicleDataSource

```kotlin
interface VehicleDataSource {
    suspend fun capabilities(): VehicleCapabilities          // 무엇을 읽을 수 있는지
    fun odometerKm(): Flow<Double>                           // 누적 주행거리
    fun energyPercent(): Flow<Double>                        // 연료 % 또는 배터리 SoC %
    fun motion(): Flow<MotionState>                          // DRIVING / PARKED / UNKNOWN
}

data class VehicleCapabilities(
    val odometer: Boolean,
    val fuelPercent: Boolean,
    val evSoc: Boolean,
    val speedOrGear: Boolean,
)
```

**Pleos 구현이 쓰는 API** (SDK 2.3.2 클래스 파일과 에뮬레이터에서 확인, 2026-11-11)

| 값 | 확인 | 조회 | 구독 | 값 타입 | 권한 |
|---|---|---|---|---|---|
| 누적 주행거리 | `Odometer.checkCurrentDistanceDrivenCapabilities` | `getCurrentDistanceDriven` | `registerDistanceDriven` | `Float?`, km | `pleos.car.permission.CAR_DRIVE` |
| 연료 % | `Fuel.checkFuelPercentCapabilities` | `getFuelPercent` | `registerFuelPercent` | `Int?` | `pleos.car.permission.CAR_ENERGY` |
| 배터리 SoC % | `EvBattery.checkEvBatteryStateOfChargeCapabilities` | `getEvBatteryStateOfCharge` | `registerEvBatteryStateOfCharge` | `Int?` | `pleos.car.permission.CAR_ENERGY` |
| 기어 | 없음 (한 번 읽어 보고 판단) | `Gear.getCurrentGearSelectionState` | `registerGearSelection` | `VehicleGear?` (P, R, N, D, 1~9, UNKNOWN) | `pleos.car.permission.CAR_DRIVE` |

- 콜백 값은 모두 nullable이다. null은 버린다.
- `Gear` 도메인은 공식 문서 목록에 없지만 SDK에 있다(`Vehicle.getGear()`).
- 주행거리는 값이 그대로여도 약 4초마다 갱신 이벤트가 온다.
- **연결 지연:** `initialize()` 직후 첫 호출은 차량 서비스 연결이 끝나지 않아 3초 넘게 응답이 없을 수 있다. 시간 초과는 '지원 안 함'이 아니라 '모름'으로 두고 1초 뒤 다시 묻는다(최대 5회). 모든 항목의 답을 받은 뒤에만 결과를 확정한다.

- 사용 순서: `Vehicle(context)` → `initialize()` → `getOdometer()` 등 → `checkXxxCapabilities` → `AccessType`이 `READ` 또는 `READ_WRITE`일 때만 get/register → 종료 시 unregister → `release()`.
- 비동기 콜백이 끝나기 전에 `release()`를 부르지 않는다.
- `onFailed`의 `VehicleException.errorCode`는 로그로 남기고, 해당 값을 '지원 안 함'으로 처리한다.
- **연료 종류 결정:** `fuelPercent`만 지원하면 내연기관, `evSoc`만 지원하면 전기차로 본다. 둘 다 지원하면 설정값을 따르고, 둘 다 안 되면 데모 모드를 권한다.

### 3.2 주행/정차 판별 (MotionState)

1. **우선:** Pleos `Gear` 도메인으로 기어를 읽는다(권한 `CAR_DRIVE`, 추가 권한 없음).
   - P면 `PARKED`, UNKNOWN이면 `UNKNOWN`, 그 밖의 기어(R, N, D, 수동 단수)는 `DRIVING`. N도 차가 움직일 수 있으므로 잠근다.
   - 속도는 쓰지 않는다. Android `CarPropertyManager`도 쓰지 않는다.
2. **대체:** 위가 안 되면 주행거리로 추정한다.
   - 최근 20초 사이에 주행거리가 0.05 km 넘게 늘면 `DRIVING`.
   - 60초 동안 주행거리 변화가 없으면 `PARKED`.
   - 그 사이는 직전 상태를 유지한다.
3. 판별할 수 없으면 `UNKNOWN`으로 두고, 안전을 위해 `DRIVING`처럼 다룬다(입력 잠금).
4. **예외:** 여행 기록에 필요한 값(주행거리, 그리고 연료 % 또는 배터리 % 중 하나)을 읽지 못하는 상태(`VehicleCapabilities.canRecordTrip == false`)에서는 잠그지 않는다. 이때 화면은 "차량 값을 읽지 못했다"는 안내와 데모 모드·설정 진입만 보여 주고, 여행 시작 같은 기록 기능은 막는다.
   - 이유: 잠그면 데모 모드를 켤 수도 없어 앱을 아예 쓸 수 없다. 기록할 데이터가 없으니 주행 중 입력할 것도 사실상 없다.
   - 차량 값을 하나라도 읽을 수 있게 되면 3번 규칙(`UNKNOWN` → 잠금)을 그대로 따른다.

---

## 4. 데이터 모델 (Room)

```kotlin
@Entity data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val status: TripStatus,               // ACTIVE, PAUSED, ENDED
    val headcount: Int,                   // 운전자 포함 인원, 1..9
    val energyType: EnergyType,           // FUEL, EV
    val capacity: Double,                 // L 또는 kWh (시작 시점 설정값 복사)
    val defaultUnitPrice: Int,            // 원/L 또는 원/kWh (시작 시점 설정값 복사)
    val startedAt: Long, val endedAt: Long?,
    val startOdometerKm: Double, val endOdometerKm: Double?,
    val startEnergyPercent: Double, val endEnergyPercent: Double?,
)

@Entity data class Segment(              // ACTIVE 구간 하나. 일시정지하면 닫히고 재개하면 새로 열린다
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long, val dayIndex: Int = 1,
    val startedAt: Long, val endedAt: Long?,
    val startOdometerKm: Double, val endOdometerKm: Double?,
    val energyUsedPercent: Double = 0.0,  // 구간 중 에너지 % 하락 누적 (주유로 인한 상승은 제외)
)

@Entity data class Refuel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long, val detectedAt: Long,
    val percentBefore: Double, val percentAfter: Double,
    val amountWon: Int?,                  // 사용자가 입력한 금액. null이면 미입력
    val source: Source,                   // DETECTED, MANUAL
)

@Entity data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long, val createdAt: Long,
    val category: Category,               // TOLL, PARKING, OTHER
    val amountWon: Int, val input: InputMethod, // VOICE, TOUCH
)
```

- 차량 설정(F9)은 DataStore에 둔다: `energyType`, `fuelCapacityL`(기본 50), `fuelPriceWonPerL`(기본 1,700, 가정치), `evCapacityKWh`(기본 77), `evPriceWonPerKWh`(기본 350, 가정치), `demoMode`.
- 기본 단가는 가정치임을 설정 화면과 정산서에 표시한다.

---

## 5. 기능 명세와 완료 조건

### F1. 여행 시작
- 진입: 홈의 "여행 시작" 버튼(정차 중), 또는 음성 "여행 시작, 4명".
- 인원수는 1~9명. 음성에서 숫자를 못 읽으면 인원 선택 화면을 띄운다(정차 중). 주행 중이면 기본값 1명으로 시작하고 나중에 고칠 수 있게 한다.
- 시작 시 현재 주행거리와 에너지 %를 읽어 `Trip`과 첫 `Segment`를 만든다. 둘 중 하나라도 읽지 못하면 시작하지 않고 이유를 보여 준다.
- 진행 중인 여행이 있으면 새 여행을 시작하지 않는다.
- **완료 조건:** 시작 후 앱을 강제 종료하고 다시 열어도 같은 여행이 `ACTIVE`로 이어진다.

### F2. 주행 중 기록
- `TripRecorder`가 주행거리와 에너지 % 구독값을 받는다.
- 에너지 %가 내려가면 그 차이를 현재 `Segment.energyUsedPercent`에 더한다. 올라가면 F3으로 넘긴다.
  - 기준값은 내려갈 때만 따라 내려간다. 주행 중 잠깐 올랐다 내려온 흔들림은 사용량으로 세지 않는다(`EnergyTracker`).
- 열린 구간의 `endOdometerKm`은 구간이 닫히기 전에도 최신 주행거리로 갱신한다. 열려 있는지는 `endedAt == null`로 판단한다.
- DB 쓰기는 값이 바뀔 때만 하되, 최소 10초 간격으로 모아서 쓴다.
- **완료 조건:** Virtual Console에서 주행거리를 늘리면 홈 화면의 "이번 여행 거리"가 5초 안에 바뀐다.

### F3. 주유·충전 감지
- 조건: `MotionState == PARKED`이고, 에너지 %가 정차 시작 시점보다 **3%p 이상** 오른 뒤 30초 동안 더 오르지 않을 때 주유(충전) 1건으로 확정한다.
- 확정되면 TTS로 "주유하셨네요. 얼마 넣으셨어요?"라고 묻고 STT로 금액을 받는다. 음성이 안 되면 금액 입력 시트를 띄운다.
- 30초 안에 입력을 시작하지 않거나 건너뛰면 `amountWon = null`로 저장한다. 숫자를 누르기 시작하면 자동으로 닫지 않는다.
- 금액을 놓친 건은 정차 화면의 "충전(주유) 금액 입력" 버튼으로 나중에 입력한다. 여행이 끝난 뒤 입력하는 화면은 F6에서 만든다.
- 주행 중 상승(센서 흔들림)은 무시한다.
- 오르는 도중 출발하면(정차 → 주행) 그 자리에서 확정한다.
- **완료 조건:** 데모 모드나 Virtual Console에서 정차 상태로 %를 30→80으로 올리면 1건만 감지되고 금액 입력을 요청한다.

### F4. 비용 입력
- 정차 중 "비용 추가" → 종류(통행료, 주차비, 기타) → 금액.
- 음성 "통행료 3천 2백 원"도 받는다. 한국어 금액 파싱: "만, 천, 백" 단위와 아라비아 숫자를 모두 처리한다.
- 주행 중에는 버튼을 숨긴다(음성만 가능).
- **완료 조건:** 금액 파서 단위 테스트에서 "삼천이백 원", "3천 2백원", "12,500원", "만 오천 원"을 모두 맞게 읽는다.

### F5. 일시정지·종료
- 일시정지: 현재 `Segment`를 닫는다. 그동안의 주행거리·에너지 변화는 집계하지 않는다. 주유 감지는 계속한다(여행 중 주유는 여행 비용에 들어가므로).
- 재개: 새 `Segment`를 연다.
- 종료: 정차 중에만 가능하다. 마지막 값을 저장하고 정산 화면으로 간다. 실수 방지를 위해 한 번 확인한다.
- **완료 조건:** 일시정지 중에 늘어난 주행거리는 여행 거리에 들어가지 않는다.

### F6. 차량 1대 정산

**계산식**
```
총 거리(km)        = Σ Segment(endOdometerKm − startOdometerKm)
사용 에너지(L/kWh) = Σ Segment.energyUsedPercent / 100 × capacity
적용 단가(원/단위) = 금액이 입력된 주유 건이 있으면
                       Σ amountWon / Σ((percentAfter − percentBefore)/100 × capacity)
                     없으면 Trip.defaultUnitPrice
에너지 비용(원)    = round(사용 에너지 × 적용 단가)
기타 비용(원)      = Σ Expense.amountWon
총비용(원)         = 에너지 비용 + 기타 비용
1인 부담(원)       = floor(총비용 / headcount / 10) × 10
운전자에게 보낼 돈 = 1인 부담 (운전자를 뺀 각 동승자)
운전자 부담        = 총비용 − 1인 부담 × (headcount − 1)   // 10원 미만 나머지는 운전자가 낸다
연비               = 총 거리 / 사용 에너지 (km/L 또는 km/kWh)
```

- 주유 금액 자체를 비용으로 더하지 않는다. 이번 여행에서 **쓴 만큼**만 나눈다. 주유 금액은 단가를 구하는 데만 쓴다.
- 정산서에는 각 숫자 옆에 근거를 표시한다: "차량 측정", "입력값", "기본 단가로 추정" / "충전 금액으로 계산".
- `headcount == 1`이면 나눌 금액 대신 총비용만 크게 보여 준다.

**정산 화면** (여행 종료 직후와 지난 여행에서 같은 화면)
- 맨 위에 "동승자 N명이 각자 운전자에게 ○○원"을 가장 크게 보여 준다.
- 계산 근거(거리, 사용량, 단가, 비용), 주유·충전 내역(시각, % 변화, 금액), 기타 비용 내역을 보여 준다.
- **끝난 뒤 고칠 수 있는 것:** 주유·충전 금액(미입력 → 입력, 입력값 수정), 인원. 고치면 정산을 바로 다시 계산한다.
  차량이 측정한 거리·사용량은 고치지 않는다(intent.md 원칙 2).
- **지난 여행 목록:** 홈의 "지난 여행". 최근 여행부터 날짜·거리·인원·총비용·1인 부담, 금액 미입력 건수를 보여 주고, 누르면 정산 화면을 연다.
- **확인 (11/11, 에뮬레이터):** 40km, 배터리 10%p(84kWh) 여행에서 충전 금액을 건너뛰고 종료 → 기본 단가 347원으로 1인 970원. 충전 7,000원 입력 → 단가 417원/kWh, 1인 1,160원·운전자 1,180원. 인원 2명으로 수정 → 1,750원. 지난 여행 목록에 반영되고 다시 열림.
- **완료 조건:** 순수 Kotlin 단위 테스트로 다음을 검증한다.
  - 주유 없음, 기본 단가 사용
  - 주유 1건(금액 입력), 단가 역산
  - 주유 2건 중 1건만 금액 입력 (입력된 건만으로 단가 계산)
  - 일시정지 구간 제외
  - 10원 단위 내림과 운전자 나머지 부담 (예: 총비용 100,005원 ÷ 3명 → 1인 33,330원, 운전자 33,345원)

### F7. 주행 중 화면 잠금
- 화면 기준은 [UI_GUIDELINES.md](UI_GUIDELINES.md)를 따른다.
- **주행 판단:** `CarUxRestrictions.isRequiresDistractionOptimization()`이 true이거나(Pleos 요구, 기어와 무관), 기록할 수 있는 차량에서 `MotionState`가 `DRIVING`·`UNKNOWN`이면 홈은 **주행 모드 화면**으로 바뀐다. 에뮬레이터 확인(11/11): 기어 D → 제한 true, P → false.
  - 큰 숫자 2개: "52.0 km"(64sp) + "배터리 −12%"(44sp). 위에 "여행 중 · 3명"
  - 버튼 없음. 음성 버튼 하나만 둔다(STT를 쓸 수 있을 때).
- 상세 화면(정산, 설정, 기록 목록)은 정차 중에만 열린다. 주행 중 진입하면 주행 모드 화면으로 돌아간다.
- AAOS 운전자 주의 분산 정책: 주행 중에도 보여야 하는 Activity라 `distractionOptimized` 메타데이터를 둔다. 에뮬레이터에서 주행 중에도 앱이 막히지 않고 주행 모드 화면이 보이는 것을 확인했다(11/11).
- **완료 조건:** Virtual Console에서 기어를 D로(또는 주행거리를 계속 늘려) 주행 상태로 만들면 3초 안에 주행 모드 화면으로 바뀌고, 입력 UI가 사라진다.

### F8. 음성 입출력
- 앱 시작 시 `SpeechToText(context, Mode.ON_DEVICE)`, `TextToSpeech(context, Mode.ON_DEVICE)`로 `initialize(OnInitializeListener)`를 부른다. 성공하면 로캘을 `ko-KR`로 둔다.
- 초기화에 실패하면 말하기 버튼을 숨긴다. 기능은 모두 터치로 쓸 수 있어야 한다.
- **권한:** `pleos.car.permission.STT_SERVICE`, `pleos.car.permission.TTS_SERVICE`가 필요하다(없으면 `initialize()`가 `SecurityException`). 문서에는 없고 SDK에서 확인했다. 녹음은 Pleos 음성 서비스(`ai.pleos.playground.caas`)가 하므로 `RECORD_AUDIO`는 선언하지 않는다. SDK가 `INTERNET`을 자동으로 합친다.
- **듣기 흐름 (에뮬레이터 확인):** `request()` → `onReady` → (말함) → `onEndedRecognition` → `onUpdated(최종 문장, true)`. 말 끝남이 최종 문장보다 먼저 오므로 끝남 뒤 2.5초 동안 최종 문장을 기다린다. 첫 사용 때는 `onReady`까지 8초 넘게 걸릴 수 있어서 준비 대기 12초 + 준비 후 10초를 상한으로 둔다.
- **말하기:** `speak(text, isFinal = true)` → `onDone`까지 기다린 뒤 다음 동작(듣기 등)을 한다. 안내 음성을 다시 인식하지 않게 하기 위해서다.
- 1단계 음성 명령(`VoiceCommand`): "여행 시작(, N명)", "일시정지", "다시 시작", "여행 종료"(→ "여행을 끝낼까요?" → "네"), "통행료/주차비 N원", 금액만("6만 원", 주유·충전 금액 답).
  - 인원은 "4명", "세 명", "오명" 모두 읽는다. 1~9명 밖이거나 못 들으면 정차 중엔 인원 선택 화면, 주행 중엔 1명으로 시작한다.
  - "오늘"의 '오'를 5로 읽지 않도록, 원·만·천·백·아라비아 숫자가 있을 때만 금액으로 본다.
- TTS 안내: 여행 시작 확인("3명, 여행을 시작할게요."), 주유·충전 감지 질문, 비용 기록 확인, 종료 요약("총 72킬로미터를 달렸어요. 한 사람당 3,350원이에요.").
- 명령 해석은 앱이 한다(STT SDK는 텍스트만 돌려주고 명령을 실행하지 않는다).
- 말하기 버튼은 정차 화면과 주행 모드 화면 모두에 있다. 주행 중 유일한 입력이다(F7).

### F9. 차량 설정
- 처음 실행할 때 한 번 보여 준다. 연료 종류, 용량(kWh 또는 L), 기본 단가(원/kWh 또는 원/L).
  "기본값으로 시작"을 누르면 기본값을 쓰고 다시 묻지 않는다.
- 차량이 연료·배터리 중 하나만 지원하면(capability 확인) 종류를 고정하고 "전기차로 확인됐어요"처럼 알려 준다.
- 입력 범위(`SettingsInput`): 전기차 10~200kWh·50~2,000원/kWh, 내연기관 20~150L·500~5,000원/L. 벗어나면 저장하지 않고 이유를 보여 준다.
- 홈 화면에 "정산 기준: 배터리 84.0 kWh · 기본 단가 347원/kWh"를 늘 보여 준다(intent.md 원칙 2).
- 진행 중인 여행에는 바뀐 설정이 적용되지 않는다(시작 시점 값을 `Trip`에 복사). 설정 화면에서도 그렇게 알려 준다.
- 앱을 켠 직후 차량 확인이 끝나기 전에는 값과 경고 대신 "차량 정보를 확인하는 중이에요…"만 보여 준다.
- **확인 (11/11, 에뮬레이터):** 첫 실행에 설정 화면 표시, 전기차로 고정, 5kWh 입력 시 오류, 84kWh·347원 저장 후 홈 반영, 다시 켜도 값 유지·설정 화면 재표시 없음.

### F10. 데모 모드
- 설정 화면에서 켠다. 켜면 `DemoVehicleDataSource`가 실제 차량 값 대신 쓰인다.
- 데모 패널(정차 중 화면 하단): "10 km 주행", "정차", "주유 +50%", "충전 +40%" 버튼.
- Virtual Console에서 연료량을 바꿀 수 없을 때만 시연에 쓴다. 화면에 "데모 모드" 배지를 항상 보여 준다(심사에서 오해가 없도록).

---

### F1~F5 에뮬레이터 확인 결과 (2026-11-11)

Pleos 에뮬레이터 + Virtual Console(전기차)로 확인했다.

| 기능 | 확인한 것 |
|---|---|
| F1 | 3명으로 시작 → 전기차로 인식, 시작 값 20,000km·80% 저장. 강제 종료 후 다시 열어도 같은 여행이 ACTIVE로 이어짐 |
| F2 | 주행 52km, 배터리 80→68% → "52.0 km · 배터리 12.0%p 사용 (9.2 kWh)" |
| F3 | 정차 중 68→90% → 30초 뒤 1건만 감지, 금액 입력 창 표시. 나중에 6,800원 입력 → 단가 401원/kWh |
| F4 | 통행료 4,800원 입력 → 기타 비용과 1인 부담에 반영 |
| F5 | 일시정지 중 15km·4%p는 빠지고 재개 후 20km·5%p만 더해짐(72km, 17%p). 종료 확인 → 정산 결과: 총 10,055원, 1인 3,350원, 운전자 3,355원 (손 계산과 일치) |
| F7 | 기어 D에서 "여행 중 · 52.0 km · 배터리 −12%" 한 줄만 표시, 버튼 없음 |

## 6. 화면 목록

| 화면 | 주행 중 | 내용 |
|---|---|---|
| 홈 (여행 없음) | 한 줄만 | 여행 시작, 최근 여행 1건, 설정 |
| 홈 (여행 중, 정차) | 주행 모드로 전환 | 거리, 에너지 사용량, 예상 1인 부담, 비용 추가, 일시정지, 종료 |
| 주행 모드 | 표시 | 한 줄 정보, 음성 버튼 |
| 인원 선택 | 불가 | 1~9 큰 버튼 |
| 금액 입력 시트 | 불가 | 숫자 패드, 종류 선택 |
| 정산 | 불가 | 계산 근거, 1인 부담, 운전자에게 보낼 금액 |
| 여행 기록 | 불가 | 지난 여행 목록, 미입력 주유 금액 수정 |
| 설정 | 불가 | 연료 종류, 용량, 단가, 데모 모드 |

- 차량 디스플레이 기준으로 글씨와 터치 영역을 크게 한다(본문 24sp 이상, 버튼 높이 76dp 이상). 가로 화면 기준.

---

## 7. 매니페스트와 권한

```xml
<uses-feature android:name="android.hardware.type.automotive" android:required="true" />
<uses-permission android:name="pleos.car.permission.CAR_DRIVE" />   <!-- Odometer, Gear -->
<uses-permission android:name="pleos.car.permission.CAR_ENERGY" />  <!-- Fuel, EvBattery -->
<uses-permission android:name="pleos.car.permission.STT_SERVICE" /> <!-- SpeechToText -->
<uses-permission android:name="pleos.car.permission.TTS_SERVICE" /> <!-- TextToSpeech -->
<!-- 기어는 CAR_DRIVE로 읽으므로 CAR_SPEED·CAR_POWERTRAIN은 필요 없다. 녹음은 Pleos 음성 서비스가 하므로 RECORD_AUDIO도 없다 -->
```
- `FOREGROUND_SERVICE`는 쓰지 않으므로 선언하지 않는다(3절). 2단계에서 포그라운드 서비스를 쓰게 되면 그때 추가한다.

- 승인되지 않은 권한은 넣지 않는다(App Market 심사 반려 사유). `CAR_TOLL`은 쓸 API가 없으므로 넣지 않는다.

---

## 8. 시연 시나리오 (영상 약 2분)

1. 정차 상태에서 앱 실행 → 차량 설정에 감지된 연료 종류가 표시됨.
2. "여행 시작, 3명" (또는 버튼) → TTS "3명, 여행을 시작할게요".
3. Virtual Console로 주행(기어 D, 주행거리 증가) → 앱이 주행 모드 화면으로 바뀜. 터치 UI가 사라진 것을 보여 줌.
4. 정차(기어 P) → 상세 화면 복귀. "통행료 4천 8백 원" 입력.
5. 정차 중 에너지 % 상승(Virtual Console 또는 데모 모드) → "주유하셨네요. 얼마 넣으셨어요?" → "6만 원".
6. 다시 주행 후 정차 → "여행 종료" → 정산 화면: 거리, 사용량, 단가 근거, 1인 부담, 운전자에게 보낼 금액.

---

## 9. 1단계 진행 상황과 남은 일 (2026-11-11 기준)

### 끝난 것

| 항목 | 상태 |
|---|---|
| intent.md, spec.md, 프로젝트 뼈대, 개발 환경 | 완료 |
| V1~V3, V6 (차량 값·기어·SDK 저장소) | 에뮬레이터에서 확인 |
| F1~F5 여행 시작·기록·충전 감지·비용·일시정지·종료 | 에뮬레이터에서 전체 흐름 확인 |
| F7 주행 중 잠금 | 확인 |
| F8 음성 | STT·TTS 연결, TTS 재생 확인. **사람 목소리 명령 인식은 미확인** |
| F10 데모 모드 | 동작 |
| F9 차량 설정 | 에뮬레이터에서 확인 (11/11) |
| F6 정산 화면·지난 여행·끝난 뒤 수정 | 에뮬레이터에서 확인 (11/11) |
| 단위 테스트 | 31개 통과 |
| README 실행 방법 | 완료 |

### 남은 일 (우선순위 순)

| # | 할 일 | 왜 | 목표일 |
|---|---|---|---|
| ~~1~~ | ~~**F9 차량 설정 화면**~~ | **완료 (11/11)** | |
| ~~2~~ | ~~**F6 정산 화면 다듬기**~~ | **완료 (11/11)** | |
| ~~3~~ | ~~**화면 다듬기**~~ **완료 (11/11)**: UI_GUIDELINES.md 기준으로 76dp 터치·24dp 간격·32sp 글자, 주요 버튼 2~3개, 큰 숫자, Asta Sans, CarUxRestrictions, TTS 볼륨 낮춤 | 심사위원이 영상에서 보는 첫인상 | 11/13 |
| ~~4~~ | ~~**release APK**~~ | **완료 (11/11)**: `apk/TeamGachiGa_GachiGa_release.apk`, 에뮬레이터 설치·실행 확인. 팀명이 정해지면 파일 이름만 바꾼다 | |
| 5 | **시연 영상**: 8절 시나리오를 Virtual Console API 스크립트로 자동 재생하고 `adb screenrecord`로 녹화 | 제출 가산점. 스크립트로 하면 다시 찍기 쉽다 | 11/13~14 |
| 6 | **음성 직접 확인**(V4 남은 부분): 사람이 말한 명령이 인식되는지 | 차량특화성의 핵심. 안 되면 영상에서는 터치로 대체 | 11/12까지 |
| 7 | **GitHub**: ~~저장소 만들기, push~~ **완료 (11/11)** → `gachiga-team/gachiga-app` (비공개). 남은 것: 공지된 심사용 계정을 읽기 권한으로 초대하고, 마감 전에 그 계정으로 접근되는지 확인 | 제출 | 11/14 |
| 8 | **기획서 고치기**: "통행료 자동 수집" 표현, 시연 차량이 전기차라는 점, 확인한 SDK·권한 반영 | 문서와 실제 앱이 어긋나지 않게 | 11/14 |
| 9 | 여유분과 제출 (23:59 마감) | | 11/15 |

### 점수 전략: 1차 접수 전에 더할 기능 (11/11 정리)

심사 기준별로 지금 약한 곳을 채우는 순서다. 조사 결과(11/11):
- **ETCS(하이패스)**: 차량 안에는 거래 정보(통행료, 잔액)가 흐르지만 공개 SDK(Toll)에는 음성 안내 볼륨 API뿐이다. 내부 서비스 접근은 승인되지 않은 권한이라 심사 반려 사유 → **쓰지 않는다.** 기획서 확장 계획에 "ETCS 거래 API가 열리면 실제 결제 금액으로 자동화"로 적는다.
- **NaviHelper 3.0.5**(Playground '사용 가능'): 경로 시작 정보에 **예상 통행료**(`tollFare`)와 **도착 시 예상 배터리**(`remainDestinationChargingValue`)가 있고, 목적지 도착 이벤트가 있다. 읽기·구독은 `pleos.car.permission.NAVI_ROUTE` 하나로 된다.

| 순위 | 기능 | 심사 기준 | 내용 |
|---|---|---|---|
| N1 | **내비 연동: 예상 통행료 자동 기록** | 차량특화성, 기술활용성 | 내비 경로가 시작되면 예상 통행료를 "통행료(내비 예상)"으로 자동 기록. 같은 목적지로 다시 경로를 잡으면 덮어쓴다. 정산 화면에서 지울 수 있다 |
| N2 | **내비 연동: 출발·도착 음성 제안** | 차량특화성, 창의성 | 경로가 시작됐는데 여행이 없으면 "○○로 출발하시네요. 여행 기록을 시작할까요?". 목적지에 도착하면 "도착했어요. 여행을 끝낼까요?". 여행 이름에 목적지를 남긴다 |
| N3 | **내비 연동: 도착 배터리 경고** | 차량특화성 | 도착 예상 배터리가 15% 미만이면 "도착 때 배터리가 8%로 예상돼요. 가는 길에 충전하세요"라고 음성 안내 (spec 기능 3 '함께 쉬기 제안'의 1대 버전) |
| N4 | **장시간 운전 휴식 안내** | 차량특화성(안전) | 2시간 연속 주행하면 "2시간 운전하셨어요. 다음 휴게소에서 쉬어 가세요" 음성 안내 |
| N5 | **QR 정산서** | 창의성, 사업성, 시연 임팩트 | 정차 중 정산 화면에 QR. 동승자가 폰으로 찍으면 모바일 웹 정산서(저장소의 정적 페이지)가 열린다. 서버 없이 데이터는 URL에 담는다 |
| N6 | **월간 리포트** | 사업성(리텐션) | 지난 여행 화면 위에 이번 달 여행 횟수, 거리, 비용, 평균 전비·연비 |

순서: N1~N3(오늘) → N4·N6(빠른 것) → N5 → 시연 영상·GitHub·기획서.

**N1~N3 구현과 확인 (11/11, 에뮬레이터 + 내비 실제 경로 "강릉역")**
- SDK: `ai.pleos.playground:NaviHelper:3.0.5`, 권한 `NAVI_ROUTE`(release). 테스트 경로를 거는 `NAVI_ROUTE_SEARCH`는 debug 매니페스트에만 있다 (설정 화면 "개발용" 버튼).
- 단위(실측): 거리 m, 시간 초, 통행료 원, **도착 예상 배터리는 Wh** (49,454Wh = 내비 화면 63%). 앱은 여행 용량으로 나눠 %로 바꾼다.
- 경로를 잡았는데 여행이 없으면 "강릉역으로 출발하시네요. 여행 기록을 시작할까요?" 음성 제안 → 여행 시작 시 예상 통행료 6,860원 자동 기록, "예상 통행료 6,860원을 기록했어요" 안내.
- 화면: 머리말 "강릉역으로 가는 중 · 2명", 정산 제목 "강릉역 여행 정산", 비용 "통행료 (내비 예상, 강릉역) 6,860원" + 삭제 버튼. 삭제하면 바로 다시 계산.
- DB v2 마이그레이션(Trip.destinationName, Expense.note): 이전 버전의 진행 중 여행을 그대로 이어받는 것 확인.
- 도착 이벤트(N2 종료 제안)와 배터리 경고(N3)는 코드와 단위 테스트까지. 에뮬레이터에서 195km를 실제로 주행해 도착시킬 수 없어 화면 확인은 못 했다.
- 내비 경로 시작 이벤트가 앱이 화면 뒤에 있을 때는 늦게 들어오는 경우가 있었다. 여행 시작 때 `getDestinationInfo`로 다시 묻는다.

**N4·N6 구현과 확인 (11/11)**
- N4 `FatigueTimer`: 2시간 연속 주행하면 안내, 이후 1시간마다. 10분 넘게 쉬어야 초기화(신호 대기 같은 짧은 정차는 쉰 것으로 보지 않음). debug 빌드는 시연용으로 2분·1분. 에뮬레이터에서 기어 D 2분 뒤 "2분 동안 운전하셨어요. 다음 휴게소에서 잠시 쉬어 가세요." 확인.
- N6 `MonthlyReport`: 지난 여행 화면 위에 "11월 리포트": 여행 횟수, 달린 거리, 든 비용, 평균 전비(같은 에너지 종류끼리). 에뮬레이터에서 2번·120km·6,738원·6.2km/kWh 확인.

**N5 QR 정산서 구현과 확인 (11/11)**
- 정산 화면의 "QR 정산서" → 큰 QR (정차 중에만. UI_GUIDELINES.md 3절). QR 그림은 ZXing core 3.5.4(Apache 2.0).
- 서버 없음: 정산 내용을 JSON → UTF-8 → base64url로 바꿔 웹 주소의 `#` 뒤에 붙인다(`SettlementShare`). `#` 뒤는 서버로 가지 않아 호스팅하는 곳에도 남지 않는다. 위치·경로는 담지 않는다.
- 웹 정산서: `web/settle/index.html` (외부 스크립트 없는 정적 페이지, 다크 모드 지원). 동승자 1인 금액, "금액 복사", 계산 근거, 비용 내역.
- 웹 주소는 빌드 설정 `gachiga.settleUrl`(gradle.properties)로 받는다. **비어 있으면 QR에 글자 정산서를 담는다** (폰 카메라로 찍으면 글로 보임). GitHub Pages를 켜면 주소를 넣고 다시 빌드한다.
- 확인: 앱이 만든 데이터로 웹 정산서를 폰 크기 화면에서 열어 표시 확인. 에뮬레이터 화면의 QR을 ZXing으로 디코딩해 한글 정산 내용이 그대로 나오는 것 확인.

### 사람이 정해야 하는 것

- ~~앱 이름~~: **같이가 (GachiGa)로 확정 (11/11).** 패키지명 `com.gachiga.app`.
- **팀명**: APK 파일 이름(`팀명_앱이름_release.apk`)에 들어간다. 정해질 때까지 `TeamGachiGa`를 임시로 쓴다. 파일 이름만 바꾸면 되고 다시 빌드할 필요는 없다.
- **GitHub 공개 여부**: 비공개면 공지된 심사용 계정을 초대해야 한다.
- **송금 링크**: 1단계에서는 넣지 않는다(정산 금액만 보여 줌). 2단계 QR 정산서에서 데모로 보여 줄지 정한다.

### 2단계 (12/3 PT까지) 미리 보기

컨보이 모드(차량 2대 위치·음성 알림, FusedLocation), 서버 동기화(Firebase 등), 여러 차량 합산 정산, QR 모바일 웹 정산서, 함께 쉬기 제안(NaviHelper). 포그라운드 서비스 필요 여부도 이때 다시 본다(3절).

---

## 10. 개발 환경 설치 순서 (현재 이 PC에 아무것도 없음)

1. Android Studio 최신 버전 설치(JDK 17 포함).
2. SDK Manager → SDK Update Sites에 `Pleos Connect System Image` 추가: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml` (Windows)
3. SDK Platforms → Show Package Details → API 34의 Pleos Connect 시스템 이미지 설치 → Android Studio 재시작.
4. Device Manager → Create device → Automotive → `Pleos Connect v2.1.0` → RAM 4GB, Default boot `Cold` → API 34 `Pleos Connect v2.1.0 APIs`.
5. 마이크 접근 설정: 에뮬레이터가 PC 마이크를 쓰게 한다. Extended controls → Microphone → "Virtual microphone uses host audio input", 또는 `adb emu avd hostmicon`. 에뮬레이터를 다시 켜면 풀릴 수 있다.
6. Virtual Console: 에뮬레이터 실행 후 `adb forward tcp:8088 tcp:8088` → 브라우저에서 `http://127.0.0.1:8088/`.
   - **페이지를 브라우저에 열어 둔 동안에만** 값 변경이 받아들여진다. 닫혀 있거나 탭이 백그라운드로 가면 `{"error":"emulator_inactive"}`(503)가 온다.
     페이지는 1초마다 `POST /browser/session?client=<id>`를 보낸다. 스크립트로 시연할 때는 이 요청을 대신 보내면 브라우저 없이도 쓸 수 있다.
   - 값 변경 API(스크립트로 시연 자동화할 때): `PUT /vehicle/odometer?traveled_distance_km=N`, `PUT /vehicle/ev-battery?field=soc&value=N`, `PUT /vehicle/gear?gear=Park|Reverse|Neutral|Drive`.
   - Pleos 런처에서 앱은 오른쪽 패널에 뜬다. `adb shell am start`로 열 때는 `--user 10`을 붙인다.
7. Pleos Playground 가입 → My Project 생성. 1단계에서는 Vehicle 상태 조회가 '사용 가능'이라 신청 없이 쓸 수 있다. Client ID/Secret은 STT·TTS 서버 모드에만 필요하므로 1단계(ON_DEVICE)에서는 쓰지 않는다.

---

## 11. 에뮬레이터에서 먼저 확인할 것 (11/11)

결과에 따라 이 문서의 해당 절을 고친다.

| # | 확인할 것 | 영향 받는 절 | 안 되면 |
|---|---|---|---|
| V1 | Virtual Console에서 연료 %를 바꿀 수 있는가 | 3.1, 8 | **확인됨 (11/11): 안 된다.** 에뮬레이터 차량은 전기차라 `Fuel`이 `ERROR_NOT_SUPPORTED_PROPERTY`. 배터리 SoC는 바꿀 수 있다 → **시연은 전기차(충전)로 한다** |
| V2 | Virtual Console의 주행거리 변경이 `getCurrentDistanceDriven`/`registerDistanceDriven`에 반영되는가, 단위는 km인가 | 3.1, F2 | **확인됨 (11/11):** 바로 반영되고 단위는 km. 배터리 SoC, 기어도 반영된다 |
| V3 | 속도·기어를 읽을 수 있는가, 필요한 권한은 | 3.2, 7 | **확인됨 (11/11):** Pleos `Gear` 도메인으로 기어를 읽는다. 권한은 `CAR_DRIVE` |
| V4 | STT·TTS `ON_DEVICE`가 에뮬레이터에서 한국어로 동작하는가, Gradle 좌표와 권한은 | F8, 2 | **일부 확인 (11/11):** 좌표·권한 확인, 둘 다 초기화 성공. TTS 한국어 재생 완료(onDone)까지 확인. STT는 PC 마이크의 주변 소리를 "제가"로 인식하는 것까지 확인. **사람이 말한 명령 문장 인식은 아직 확인 못 함** |
| V5 | 주행 중 앱 Activity가 차단되는가 (`distractionOptimized` 필요 여부) | F7 | 메타데이터 추가 |
| V6 | ~~Pleos Maven 저장소에서 `Vehicle:2.3.2`가 인증 없이 받아지는가~~ **확인됨 (11/10)**: 인증 없이 받아진다 | 2 | — |

---

## 12. 빌드·제출

- release 빌드는 별도 keystore로 서명한다. keystore와 비밀번호는 저장소에 올리지 않는다(`.gitignore`에 `*.jks`, `*.keystore` 이미 있음). 서명 정보는 `local.properties` 또는 환경 변수로 받는다.
- APK 경로: `apk/TeamGachiGa_GachiGa_release.apk` (팀명 미정이라 임시 이름).
- 서명 키는 저장소 밖(`~/.gachiga/gachiga-release.jks`)에 있고, 비밀번호는 `local.properties`에만 있다. **키를 잃으면 같은 앱으로 업데이트할 수 없으니 팀 안에서 안전하게 백업한다.** 최종 APK(2027-02)도 같은 키로 서명해야 한다.
- README.md: 앱 이름과 한 줄 소개, 실행 방법, 테스트 환경(Pleos Connect Emulator v2.1.0, API 34), 필요한 설정, APK 위치.
- 저장소, 커밋, README, 영상에 팀원 실명과 연락처를 넣지 않는다. 커밋 작성자 이름과 이메일도 익명 계정으로 설정한다.
