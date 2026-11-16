// 같이 가기 확인용 가짜 일행 차량. 에뮬레이터가 한 대뿐일 때 2호차 역할을 한다.
//
// 사용: node tools/demo/fake-car.mjs <그룹 번호> <상태 파일.json> [서버 주소]
//   상태 파일을 2초마다 다시 읽어 서버에 올린다. 파일을 고치면 차량 상태가 바뀐다.
//   예: {"lat":37.5,"lon":127.46,"heading":90,"moving":true,"energy":60,"ev":true,"km":12,"cost":8000,"headcount":3,"done":false}
//   파일에 "say":"다음 휴게소에서 쉬자" 를 넣으면 그 말을 한 번 보낸다 ("kind"로 종류 지정, 기본 voice).
//   파일에 "leave":true 를 넣으면 그룹에서 나가고 끝낸다.
import { readFileSync } from 'node:fs';

const [code, file, base = 'http://127.0.0.1:8787'] = process.argv.slice(2);
if (!code || !file) { console.error('usage: node fake-car.mjs <code> <state.json> [server]'); process.exit(1); }

const post = async (path, body = {}) => {
  const r = await fetch(base + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  if (!r.ok) throw new Error(`${path} ${r.status} ${await r.text()}`);
  return r.json();
};

const me = await post(`/rooms/${code}/join`);
console.log(`joined ${code} as ${me.label}`);
let after = 0;
let lastSaid = null;

for (;;) {
  let state = {};
  try { state = JSON.parse(readFileSync(file, 'utf8')); } catch (e) { console.error('state file:', e.message); }
  if (state.leave) { await post(`/rooms/${code}/leave`, { key: me.key }); console.log('left'); break; }
  if (state.say && state.say !== lastSaid) {
    lastSaid = state.say;
    await post(`/rooms/${code}/messages`, { key: me.key, kind: state.kind || 'voice', text: state.say });
    console.log(`said: ${state.say}`);
  }
  const s = await post(`/rooms/${code}/sync`, { key: me.key, state, after });
  for (const m of s.messages) if (m.fromId !== me.id) console.log(`message from ${m.from} [${m.kind}] ${m.text}`);
  after = s.seq;
  const others = s.members.filter((m) => m.id !== me.id).map((m) => `${m.label} ${m.lat ?? '-'},${m.lon ?? '-'} ${m.moving ? '주행' : '정차'} ${m.energy ?? '-'}% ${m.cost}원 ${m.done ? '끝' : ''}`);
  console.log(new Date().toISOString().slice(11, 19), others.join(' | '));
  await new Promise((r) => setTimeout(r, 2000));
}
