# 같이가 (GachiGa, 가칭)

'여행 시작' 한마디로 일행 차량을 하나의 여행으로 묶고, 여행이 끝나면 차량이 직접 측정한 주행거리와 연료·배터리 데이터로 비용을 자동 정산해 주는 그룹 로드트립 차량 앱.

- 왜 만드는지와 설계 원칙: [intent.md](intent.md)
- 1단계 구현 명세: [spec.md](spec.md)

> 현재 상태: 1단계(트립 로그 MVP) 프로젝트 뼈대. 여행 기록과 정산 화면은 개발 중이다.

## 테스트 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect Emulator v2.1.0 (Automotive, API 34) |
| 차량 데이터 | Pleos Connect Virtual Console, 또는 앱 안의 데모 모드 |
| 빌드 | Android Studio 2026.2 (번들 JDK 25), Gradle 9.6.0, AGP 9.5.1, Kotlin 2.4.21 |
| SDK | minSdk 28, targetSdk 34, compileSdk 37 |
| 사용 SDK | Pleos Connect Vehicle SDK 2.3.2 (Odometer, Fuel, EvBattery) |

## 실행 방법

1. Android Studio에서 이 폴더를 연다. Gradle 동기화가 Pleos Maven 저장소(`nexus-playground.pleos.ai`)에서 Vehicle SDK를 받는다.
2. Pleos Connect Emulator를 설치하고 AVD를 만든다. 자세한 순서는 [spec.md 10절](spec.md#10-개발-환경-설치-순서-현재-이-pc에-아무것도-없음).
3. 에뮬레이터를 켜고 `app`을 실행한다.
4. 차량 값 조작: `adb forward tcp:8088 tcp:8088` 후 브라우저에서 `http://127.0.0.1:8088/` (Virtual Console).
   Virtual Console로 바꿀 수 없는 값은 앱의 "데모 모드 켜기"로 시뮬레이션한다.

단위 테스트(정산 계산):

```bash
./gradlew :app:testDebugUnitTest
```

> `gradlew` 스크립트는 Android Studio에서 처음 동기화한 뒤 생성한다.

## APK

- 위치: `apk/팀명_앱이름_release.apk` (1차 접수 전에 추가 예정)
- release 서명 정보는 저장소에 올리지 않는다. `local.properties` 또는 환경 변수 `GACHIGA_STORE_FILE`, `GACHIGA_STORE_PASSWORD`, `GACHIGA_KEY_ALIAS`, `GACHIGA_KEY_PASSWORD`로 지정한다.
