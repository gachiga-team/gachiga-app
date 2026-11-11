# 같이가 (GachiGa)

> 달린 만큼 나누는 그룹 여행

'여행 시작' 한마디로 일행 차량을 하나의 여행으로 묶고, 여행이 끝나면 **차량이 직접 측정한 주행거리와 연료·배터리 데이터**로 비용을 자동 정산해 주는 그룹 로드트립 차량 앱.

- 왜 만드는지와 설계 원칙: [intent.md](intent.md)
- 1단계 구현 명세와 진행 상황: [spec.md](spec.md)
- 차량용 화면 기준 (2초 원칙, 76dp 터치, 주행 중 제한): [UI_GUIDELINES.md](UI_GUIDELINES.md)

## 지금 되는 것 (1단계, 차량 1대)

| 기능 | 설명 |
|---|---|
| 여행 시작 | 인원(운전자 포함 1~9명)을 고르면 출발 시점의 주행거리와 배터리(연료) %를 저장한다. 앱을 다시 켜도 이어진다 |
| 자동 기록 | 차량 SDK로 주행거리와 배터리 사용량을 쌓는다. 일시정지 중에는 세지 않는다 |
| 충전·주유 감지 | 정차 중 배터리(연료)가 3%p 이상 오르면 30초 뒤 1건으로 감지하고 금액을 묻는다 |
| 비용 입력 | 통행료·주차비·기타 비용을 정차 중에 입력한다 |
| 정산 | 쓴 만큼의 에너지 비용 + 기타 비용을 인원수로 나누고, 동승자가 운전자에게 보낼 금액을 근거와 함께 보여 준다 |
| 지난 여행 | 끝난 여행 목록과 이번 달 리포트. 나중에 충전·주유 금액이나 인원을 고치면 정산을 다시 계산한다 |
| 내비 연동 | 내비 경로의 예상 통행료를 자동 기록, 출발 때 여행 시작 제안, 도착 때 종료 제안, 도착 배터리 부족 경고 |
| QR 정산서 | 동승자가 폰으로 찍으면 모바일 웹 정산서가 열린다 (서버 없음) |
| 휴식 안내 | 2시간 연속 운전하면 음성으로 휴식을 권한다 |
| 주행 중 잠금 | 기어가 P가 아니면 한 줄 정보와 말하기 버튼만 보이고, 터치 입력을 막는다 |
| 음성 | "여행 시작, 3명", "통행료 사천팔백 원", "여행 종료" 같은 말을 알아듣고 결과를 음성으로 알려 준다 |
| 데모 모드 | Virtual Console 없이 앱 안 버튼으로 주행·정차·충전을 흉내 낸다 (화면에 "데모 모드" 표시) |

## 테스트 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect Emulator v2.1.0 (Automotive, API 34). 에뮬레이터 차량은 **전기차** |
| 차량 데이터 | Pleos Connect Virtual Console, 또는 앱 안의 데모 모드 |
| 빌드 | Android Studio 2026.2 (번들 JDK 25), Gradle 9.6.0, AGP 9.5.1, Kotlin 2.4.21 |
| SDK 버전 | minSdk 28, targetSdk 34, compileSdk 37 |
| 사용 SDK | Pleos Connect Vehicle 2.3.2 (Odometer, Fuel, EvBattery, Gear), SpeechToText·TextToSpeech 3.3.1 (ON_DEVICE), NaviHelper 3.0.5, Android CarUxRestrictions |
| 권한 | `pleos.car.permission.CAR_DRIVE`, `CAR_ENERGY`, `STT_SERVICE`, `TTS_SERVICE`, `NAVI_ROUTE` (debug 빌드만 `NAVI_ROUTE_SEARCH` 추가) |

---

## 실행 방법

### 0. 처음 한 번만: 개발 환경 준비

1. **Android Studio**를 설치한다 (JDK가 함께 들어 있다).
2. **Pleos 에뮬레이터 이미지 받기**
   - Tools → SDK Manager → **SDK Update Sites** 탭 → **+**
     - Name: `Pleos Connect System Image`
     - URL (Windows/Linux): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
     - URL (Apple Silicon Mac): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
   - **SDK Platforms** 탭 → 오른쪽 아래 **Show Package Details** 체크 → **Android API 34** 아래의 **Pleos Connect** 시스템 이미지 선택 → Apply → Android Studio 재시작
3. **AVD 만들기**
   - Tools → Device Manager → **Create device** → **Automotive** → **Pleos Connect v2.1.0**
   - 시스템 이미지: **API 34, `Pleos Connect v2.1.0 APIs`**
     > 일반 Android Automotive 이미지(API 35 등)를 고르면 Pleos 차량 서비스가 없어서 차량 값을 읽지 못한다. 반드시 Pleos 이미지를 고른다.
   - Advanced Settings: RAM **4 GB**, Default boot **Cold**
4. Android Studio에서 이 저장소 폴더를 연다(File → Open). 처음 Gradle 동기화는 몇 분 걸린다. Pleos SDK는 `nexus-playground.pleos.ai` 저장소에서 계정 없이 받아진다.

### 1. 앱 실행 (매번)

1. Device Manager에서 `Pleos Connect v2.1.0` 옆의 **▶**를 누른다. Cold boot라 1~2분 걸린다.
2. 위쪽 기기 선택 칸에서 이 에뮬레이터를 고르고 **Run ▶** (`app`)를 누른다.
3. 앱은 에뮬레이터 화면 **오른쪽 패널**에 뜬다.
   - 내비 화면이 대신 보이면, 아래 작업 표시줄 가운데 **앱 목록(네모 4개)**에서 같이가를 누르거나 Run을 한 번 더 누른다.
   - 터미널에서 열 때는 Pleos 사용자 공간을 지정해야 한다: `adb shell am start --user 10 -n com.gachiga.app/.MainActivity`

### 2. 차량 값 바꾸기: Virtual Console (매번)

1. Android Studio 아래 **Terminal** 탭에서 포트를 연결한다.

   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" forward tcp:8088 tcp:8088
   ```

   (Mac/Linux: `~/Library/Android/sdk/platform-tools/adb forward tcp:8088 tcp:8088` 등 SDK 경로의 adb)
2. 브라우저에서 `http://127.0.0.1:8088/`을 연다.
   - **이 탭을 앞에 띄워 둔 동안에만 값이 바뀐다.** 탭을 내리거나 다른 탭으로 가면 `emulator_inactive`로 끊긴다.
3. 쓰는 항목
   - **Gear**: P(정차) / D(주행). 앱은 기어로 주행·정차를 판단한다.
   - **Odometer**: 누적 주행거리 (km)
   - **EV battery → SoC**: 배터리 %

### 3. 확인 순서 (전체 흐름, 약 3분)

| 순서 | Virtual Console | 앱에서 확인할 것 |
|---|---|---|
| 1 | Gear **P** | "정차 중 · ○○ km · 배터리 ○○%" |
| 2 | — | **여행 시작** → 인원 선택 → "여행 중 · N명" |
| 3 | Gear **D**, Odometer를 올리고 SoC를 내린다 | 버튼이 사라지고 "여행 중 · 52.0 km · 배터리 −12%" 한 줄만 보인다 |
| 4 | Gear **P** | 정차 화면으로 돌아온다 → **비용 추가** → 통행료 입력 |
| 5 | P 상태에서 SoC를 **3% 이상 올린다** | 30초 뒤 "충전하셨네요. 얼마 들었나요?" → 금액 입력. 놓치면 **충전 금액 입력** 버튼으로 나중에 넣는다 |
| 6 | — | **일시정지** → D로 주행 → P → **다시 시작**. 일시정지 중 거리는 빠져야 한다 |
| 7 | Gear **P** | **여행 종료** → 정산 화면 (거리, 단가와 근거, 1인 부담, 운전자 부담) |
| 8 | — | 정산 화면에서 **금액 입력**/**인원 고치기** → 금액이 다시 계산된다. 홈의 **지난 여행**에서 다시 열 수 있다 |

### 4. 음성 확인

1. 에뮬레이터가 PC 마이크를 쓰게 한다: 에뮬레이터 툴바 **⋯ (Extended controls) → Microphone → "Virtual microphone uses host audio input"** 켜기.
   에뮬레이터를 새로 켜면 풀릴 수 있다. 터미널로는 `adb emu avd hostmicon`.
2. 앱의 주황색 **말하기**를 누르고 "듣고 있어요…"가 보이면 PC 마이크에 말한다. 처음 한 번은 준비에 몇 초 더 걸린다.

| 말 | 동작 |
|---|---|
| "여행 시작, 3명" / "여행 시작" | 시작. 인원이 없으면 "몇 명이 함께 가나요?"라고 다시 묻는다 |
| "통행료 사천팔백 원", "주차비 삼천 원" | 비용 기록 후 음성으로 확인 |
| (충전 감지 후) "육천팔백 원" | 충전 금액 입력 |
| "일시정지" / "다시 시작" | 기록 멈춤·재개 |
| "여행 종료" → "네" | 종료하고 거리와 1인 부담을 읽어 준다 (정차 중에만) |

음성을 쓸 수 없는 환경이면 말하기 버튼이 보이지 않고, 같은 기능을 터치로 쓴다.

### 5. Virtual Console 없이 빠르게 보기: 데모 모드

여행이 없는 화면에서 **차량 설정** → 아래쪽 **데모 모드 켜기**를 누른다.
- **10 km 주행**: 주행 상태가 된다 → 주행 모드 화면의 **정차 (데모)**로 돌아온다
- **충전 +40%**: 정차 중 누르면 30초 뒤 충전 감지 창이 뜬다

데모 모드에서는 Pleos 실제 값 대신 앱 안의 가짜 값을 쓴다. 화면에 "데모 모드" 배지가 항상 보인다.

### 6. 단위 테스트

정산, 충전 감지, 주행·정차 추정, 한국어 금액 읽기, 음성 명령 해석 (28개).

- Android Studio: 프로젝트 창의 `app/src/test` 오른쪽 클릭 → **Run 'Tests in…'**
- 터미널:

  ```powershell
  .\gradlew.bat :app:testDebugUnitTest
  ```

  (Mac/Linux: `./gradlew :app:testDebugUnitTest`)

### 자주 막히는 곳

| 증상 | 원인과 해결 |
|---|---|
| "이 차량에서 주행거리나 연료·배터리 값을 읽지 못했어요" | 앱을 켠 직후 차량 서비스 연결 중이면 잠깐 보일 수 있다. 계속 보이면 AVD가 Pleos 이미지(API 34)인지 확인한다 |
| Virtual Console에서 값을 바꿔도 앱이 그대로 | 브라우저 탭이 뒤로 가 있으면 끊긴다. 탭을 앞으로 가져오고 `adb forward`를 다시 실행한다 |
| 화면이 계속 "여행 중 · …" 한 줄만 보임 | 기어가 D다. Virtual Console에서 P로 바꾼다 |
| 여행 종료 버튼이 눌리지 않음 | 정차(기어 P) 중에만 끝낼 수 있다 |
| 말하기 버튼이 없음 | 음성 SDK 초기화에 실패한 경우다. 터치로 쓴다 |
| 데모 모드 버튼이 없음 | 차량 설정 화면 아래쪽으로 옮겼다 (여행 중에는 숨김) |

---

## 시연 영상

- [`demo/gachiga_demo.mp4`](demo/gachiga_demo.mp4) (2분 46초, 1920×1080, 한글 자막)
- Pleos Connect Emulator와 Virtual Console(전기차)에서 실제로 동작하는 화면이다. 화면 녹화에 소리가 없어서, 앱의 음성 안내는 `[음성]` 자막으로 넣었다.
- 순서: 홈 → 내비 목적지 설정과 여행 기록 제안 → 여행 시작(예상 통행료 자동 기록) → 주행 화면 → 주차비 입력 → 충전 감지와 금액 입력 → 종료와 정산 → QR 정산서와 동승자 폰 화면 → 지난 여행·월간 리포트

다시 찍는 방법 (debug 빌드 설치, Virtual Console 활성 상태에서):

```bash
bash tools/demo/record_demo.sh out/demo
```

```bash
node tools/demo/make_video.mjs out/demo out/demo/gachiga_demo.mp4
```

- 장면 폴더에는 웹 정산서 화면 `web_settle.png`와 한글 글꼴(`malgun.ttf`, `malgunbd.ttf`)을 함께 둔다. 편집에는 ffmpeg가 필요하다.

## QR 정산서 웹 페이지

정산 화면의 **QR 정산서**를 동승자가 폰으로 찍으면 [`web/settle/index.html`](web/settle/index.html)이 열린다. 서버는 없고, 정산 내용은 주소의 `#` 뒤에 담긴다.

웹 페이지를 켜는 방법:
1. GitHub 저장소 Settings → Pages → Branch `main`, 폴더 `/ (root)`로 저장한다.
2. `gradle.properties`에 주소를 넣는다: `gachiga.settleUrl=https://<계정>.github.io/<저장소>/web/settle/`
3. 다시 빌드한다. 주소가 비어 있으면 QR에 정산 내용을 글자로 담는다 (폰 카메라로 찍으면 글로 보인다).

## APK

- 위치: [`apk/TeamGachiGa_GachiGa_release.apk`](apk/TeamGachiGa_GachiGa_release.apk) (v0.1.0, release 서명, 약 47MB)
- 설치: Pleos 에뮬레이터를 켠 뒤 APK 파일을 에뮬레이터 창에 끌어다 놓거나, `adb install apk/TeamGachiGa_GachiGa_release.apk`
  - 같은 패키지의 debug 빌드가 이미 깔려 있으면 서명이 달라 설치가 거부된다. 먼저 `adb uninstall com.gachiga.app`
- 팀명이 정해지면 파일 이름만 바꾼다 (다시 빌드할 필요 없음).
- release 서명 정보는 저장소에 올리지 않는다. `local.properties` 또는 환경 변수 `GACHIGA_STORE_FILE`, `GACHIGA_STORE_PASSWORD`, `GACHIGA_KEY_ALIAS`, `GACHIGA_KEY_PASSWORD`로 지정한다.
