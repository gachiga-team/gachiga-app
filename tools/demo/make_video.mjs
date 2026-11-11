// 같이가 시연 영상 편집: record_demo.sh가 만든 장면에 한글 자막을 넣고 하나로 이어 붙인다.
//
// 사용: node tools/demo/make_video.mjs <장면 폴더> <출력 mp4>
//   장면 폴더에는 sNN_*.mp4, web_settle.png(동승자 폰 화면), malgun.ttf, malgunbd.ttf가 있어야 한다.
//   FFMPEG 환경 변수로 ffmpeg 경로를 줄 수 있다.
//
// 녹화(adb screenrecord)에는 소리가 없어서, 앱이 말한 문장은 "[음성]" 자막으로 보여 준다.
import { execFileSync } from 'node:child_process';
import { writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { join, resolve } from 'node:path';

const [dirArg, outArg] = process.argv.slice(2);
if (!dirArg || !outArg) { console.error('usage: node make_video.mjs <scene dir> <out.mp4>'); process.exit(1); }
const dir = resolve(dirArg);
const out = resolve(outArg);
const ffmpeg = process.env.FFMPEG || 'ffmpeg';
const work = join(dir, 'work');
mkdirSync(work, { recursive: true });

// 장면표: [파일, 시작초, 끝초, 배속, 자막 1줄(설명), 자막 2줄(음성·근거)]
const scenes = [
  ['card:intro', 0, 4.5, 1, '같이가 (GachiGa) · 달린 만큼 나누는 그룹 여행', 'Pleos Connect Emulator + Virtual Console (전기차) 실제 동작 화면'],
  ['s01_home.mp4', 0, 5, 1, '정차 중 홈: 주요 버튼 2개 (여행 시작 · 말하기)', '주행거리·배터리·기어는 Pleos Vehicle SDK, 정산 기준(용량·단가)을 항상 표시'],
  ['s02_navi.mp4', 0, 14, 1, '내비에 목적지(강릉역)를 정하면 여행 기록을 먼저 제안', '[음성] 강릉역으로 출발하시네요. 여행 기록을 시작할까요?'],
  ['s03_start.mp4', 9.5, 29, 1, '여행 시작 · 인원 선택 (운전자 포함 3명)', '[음성] 3명, 여행을 시작할게요.  ·  예상 통행료 6,860원을 기록했어요. (NaviHelper)'],
  ['s04_drive.mp4', 3, 20, 1, '주행 중(기어 D): 큰 숫자 2개와 말하기 버튼만, 터치 입력 잠금', 'CarUxRestrictions + 기어로 주행 판단 · 2초 안에 읽히는 화면'],
  ['s05_expense.mp4', 10, 31, 2, '휴게소 정차: 주차비 3,000원 입력', '76dp 숫자 패드 · 정차 중에만 열림 (2배속)'],
  ['s06a_charge.mp4', 0, 8.2, 1, '정차 중 배터리 79% → 99%: 충전으로 감지', '30초 동안 더 오르지 않으면 1건으로 확정'],
  ['s06b_amount.mp4', 0, 29, 2, '[음성] 충전하셨네요. 얼마 들었나요?  →  8,000원 입력', '충전 금액으로 단가를 다시 계산: 519원/kWh (2배속)'],
  ['s07_end.mp4', 0, 39.5, 1.5, '다시 주행 → 도착 후 정차(기어 P)에서 여행 종료', '[음성] 총 88킬로미터를 달렸어요. 한 사람당 4,620원이에요. (1.5배속)'],
  ['s08_summary.mp4', 0, 22, 1, '근거 있는 정산: 차량 측정 + 충전 금액 + 내비 예상 통행료 + 입력값', 'QR 정산서: 동승자가 폰 카메라로 찍어서 확인'],
  ['still:web_settle.png', 0, 7, 1, '동승자 폰에서 보는 정산서 (서버 없음, 정산 내용은 QR 주소에만)', '동승자 2명 → 각자 운전자에게 4,620원'],
  ['s10_history.mp4', 4, 17, 1, '지난 여행과 월간 리포트 (평소에는 유류비·전비 관리)', '정산 금액과 인원은 끝난 뒤에도 고칠 수 있음'],
  ['card:outro', 0, 5, 1, '같이가 · 차량이 측정한 값으로 근거 있는 여행 정산', 'Pleos SDK: Vehicle · NaviHelper · SpeechToText · TextToSpeech  +  CarUxRestrictions'],
];

const W = 1920, H = 1080, FPS = 30;
const run = (args) => execFileSync(ffmpeg, ['-v', 'error', '-y', ...args], { cwd: dir, stdio: ['ignore', 'inherit', 'inherit'] });

// 자막은 파일로 넘긴다 (한글과 특수문자 escape 문제를 피한다). 글꼴은 장면 폴더의 상대 경로로 쓴다.
const caption = (i, l1, l2) => {
  writeFileSync(join(work, `c${i}_1.txt`), l1, 'utf8');
  writeFileSync(join(work, `c${i}_2.txt`), l2, 'utf8');
  return [
    `drawbox=x=0:y=ih-170:w=iw:h=170:color=black@0.78:t=fill`,
    `drawtext=fontfile=malgunbd.ttf:textfile=work/c${i}_1.txt:expansion=none:fontsize=44:fontcolor=white:x=60:y=h-150`,
    `drawtext=fontfile=malgun.ttf:textfile=work/c${i}_2.txt:expansion=none:fontsize=32:fontcolor=0xFFD27F:x=60:y=h-82`,
  ].join(',');
};

const parts = [];
scenes.forEach(([src, start, end, speed, l1, l2], i) => {
  const part = `work/p${String(i).padStart(2, '0')}.mp4`;
  const dur = (end - start) / speed;
  const norm = `scale=${W}:${H}:force_original_aspect_ratio=decrease,pad=${W}:${H}:(ow-iw)/2:(oh-ih)/2:color=0x101418,fps=${FPS},format=yuv420p`;
  if (src.startsWith('card:')) {
    // 시작·끝 카드: 어두운 배경에 앱 이름과 설명
    writeFileSync(join(work, `t${i}.txt`), '같이가', 'utf8');
    run(['-f', 'lavfi', '-i', `color=c=0x101418:s=${W}x${H}:r=${FPS}:d=${dur}`,
      '-vf', [`drawtext=fontfile=malgunbd.ttf:textfile=work/t${i}.txt:expansion=none:fontsize=140:fontcolor=0x4FC3F7:x=(w-tw)/2:y=h/2-200`, caption(i, l1, l2), 'format=yuv420p'].join(','),
      '-t', String(dur), '-an', '-c:v', 'libx264', '-preset', 'medium', '-crf', '20', part]);
  } else if (src.startsWith('still:')) {
    // 정지 화면(동승자 폰 정산서)을 가운데에 놓는다
    run(['-loop', '1', '-t', String(dur), '-i', src.slice(6),
      '-vf', [`scale=-1:${H - 220}`, `pad=${W}:${H}:(ow-iw)/2:20:color=0x101418`, `fps=${FPS}`, caption(i, l1, l2), 'format=yuv420p'].join(','),
      '-t', String(dur), '-an', '-c:v', 'libx264', '-preset', 'medium', '-crf', '20', part]);
  } else {
    if (!existsSync(join(dir, src))) throw new Error(`missing ${src}`);
    run(['-ss', String(start), '-to', String(end), '-i', src,
      '-vf', [`setpts=PTS/${speed}`, norm, caption(i, l1, l2)].join(','),
      '-an', '-c:v', 'libx264', '-preset', 'medium', '-crf', '20', part]);
  }
  parts.push(part);
  console.log(`scene ${i} ${src} ${dur.toFixed(1)}s`);
});

writeFileSync(join(work, 'list.txt'), parts.map((p) => `file '${p.replace('work/', '')}'`).join('\n'), 'utf8');
run(['-f', 'concat', '-safe', '0', '-i', 'work/list.txt', '-c', 'copy', '-movflags', '+faststart', out]);
console.log(`done: ${out}`);
