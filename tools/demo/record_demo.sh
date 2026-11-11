#!/usr/bin/env bash
# 같이가 시연 영상 장면 녹화 스크립트 (spec.md 8절 시나리오 + 내비 연동·QR).
#
# 준비
#   1. Pleos Connect Emulator(전기차)를 켜고 debug 빌드를 설치한다 (DebugReceiver가 필요하다).
#   2. adb forward tcp:8088 tcp:8088, 그리고 Virtual Console(http://127.0.0.1:8088/)을 활성 상태로 둔다.
#      (브라우저 탭을 앞에 띄워 두거나 POST /browser/session 하트비트를 보낸다)
# 실행
#   bash tools/demo/record_demo.sh <출력 폴더>
# 결과
#   <출력 폴더>/sNN_*.mp4 장면 영상과 speak.log(앱이 말한 문장). 자막과 이어 붙이기는 make_video.sh가 한다.
#
# 녹화에는 소리가 담기지 않는다(adb screenrecord). 앱의 음성 안내는 speak.log로 자막을 만든다.
set -u
OUT="${1:?usage: record_demo.sh <out dir>}"
mkdir -p "$OUT"
export MSYS_NO_PATHCONV=1
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
VC=http://127.0.0.1:8088
PKG=com.gachiga.app

vc()    { curl -s -o /dev/null -X PUT "$VC$1"; }
gear()  { vc "/vehicle/gear?gear=$1"; }
odo()   { vc "/vehicle/odometer?traveled_distance_km=$1"; }
soc()   { vc "/vehicle/ev-battery?field=soc&value=$1"; }
front() { "$ADB" shell am start --user 10 -n $PKG/.MainActivity >/dev/null 2>&1; }

# 화면에서 글자가 정확히 같은 요소의 가운데를 누른다.
tap() {
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local b; b=$("$ADB" shell cat /sdcard/ui.xml | grep -o "<node[^>]*text=\"$1\"[^>]*>" | head -1 | grep -o 'bounds="[^"]*"' | grep -oE '[0-9]+' | tr '\n' ' ')
  if [ -z "$b" ]; then echo "  ! not found: $1"; return 1; fi
  set -- $b
  "$ADB" shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

REC_PID=""
rec()  { echo "● $1"; "$ADB" shell rm -f "/sdcard/$1.mp4"; "$ADB" shell screenrecord --bit-rate 8000000 --size 1920x1080 "/sdcard/$1.mp4" & REC_PID=$!; sleep 1; }
stop() { "$ADB" shell pkill -INT screenrecord; wait "$REC_PID" 2>/dev/null; sleep 1; "$ADB" pull "/sdcard/$1.mp4" "$OUT/$1.mp4" >/dev/null && echo "  saved $1.mp4"; }
mark() { echo "=== $1" >> "$OUT/speak.log"; "$ADB" logcat -c; }
speaks() { "$ADB" logcat -d | grep -o "PleosVoice: speak: .*" | sed 's/PleosVoice: speak: //' >> "$OUT/speak.log"; }

: > "$OUT/speak.log"

echo "준비: 차량 정차, 앱 초기화, 첫 실행 설정"
gear Park; odo 70000; soc 85
"$ADB" shell pm clear --user 10 $PKG >/dev/null
front; sleep 18
tap "기본값으로 시작"; sleep 4

# 1. 홈
mark s01; rec s01_home; sleep 6; stop s01_home; speaks

# 2. 내비에 목적지를 정하면 여행 기록을 제안한다
mark s02; rec s02_navi
"$ADB" shell am broadcast --user 10 -n $PKG/.DebugReceiver -a com.gachiga.app.DEBUG_TEST_ROUTE >/dev/null
sleep 7; front; sleep 6
stop s02_navi; speaks
sleep 20   # 음성 답을 기다리는 시간 (녹화하지 않음)

# 3. 여행 시작 (인원 선택) → 예상 통행료 자동 기록
mark s03; rec s03_start
tap "여행 시작"; sleep 2; tap "3명"; sleep 9
stop s03_start; speaks

# 4. 주행: 큰 숫자 2개 + 말하기 버튼, 터치 잠금
mark s04; rec s04_drive
gear Drive; sleep 2
km=70000; b=85
for i in $(seq 1 12); do km=$((km + 4)); [ $((i % 2)) -eq 0 ] && b=$((b - 1)); odo $km; soc $b; sleep 1; done
sleep 2
stop s04_drive; speaks

# 5. 휴게소 정차: 주차비 입력
mark s05; rec s05_expense
gear Park; sleep 4
tap "비용 추가"; sleep 2; tap "주차비"; sleep 1; tap "3"; tap "0"; tap "00"; sleep 1; tap "확인"; sleep 3
stop s05_expense; speaks

# 6. 정차 중 충전 → 30초 뒤 감지 → 금액 입력
mark s06; rec s06a_charge
for v in 84 89 94 99; do soc $v; sleep 1; done; sleep 2
stop s06a_charge; speaks
sleep 34   # 충전이 멈춘 뒤 30초 기다림 (녹화하지 않음)
mark s06b; rec s06b_amount
sleep 3; tap "8"; tap "00"; tap "0"; sleep 1; tap "확인"; sleep 4
stop s06b_amount; speaks

# 7. 다시 주행 → 도착 → 정차 후 여행 종료
mark s07; rec s07_end
gear Drive; sleep 2
for i in $(seq 1 8); do km=$((km + 5)); odo $km; [ $((i % 2)) -eq 0 ] && soc $((99 - i / 2)); sleep 1; done
gear Park; sleep 4
tap "여행 종료"; sleep 2; tap "여행 종료"; sleep 10
stop s07_end; speaks
"$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
"$ADB" shell cat /sdcard/ui.xml | grep -o 'text="[^"]\+"' | sed 's/text=//' > "$OUT/summary_texts.txt"

# 8. 정산 근거 훑어보기 + QR 정산서
mark s08; rec s08_summary
sleep 3
"$ADB" shell input swipe 1600 1150 1600 450 1500; sleep 3
"$ADB" shell input swipe 1600 450 1600 1150 800; sleep 2
tap "QR 정산서"; sleep 6; tap "닫기"; sleep 1
stop s08_summary; speaks

# 9. 지난 여행과 월간 리포트
mark s10; rec s10_history
"$ADB" shell input swipe 1600 1200 1600 250 400; sleep 1
tap "확인"; sleep 2; tap "지난 여행"; sleep 5
stop s10_history; speaks

# 정산 숫자 (웹 정산서 화면을 같은 숫자로 만들 때 쓴다)
"$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
"$ADB" shell cat /sdcard/ui.xml | grep -o 'text="[^"]\+"' | sed 's/text=//' > "$OUT/history_texts.txt"
echo "끝: $OUT"
