// 컨보이 서버 확인용 스크립트. 서버를 띄운 뒤 실행: node server/test-convoy.mjs [주소]
import assert from 'node:assert/strict';

const base = process.argv[2] || 'http://127.0.0.1:8787';
const post = async (path, body = {}) => {
  const r = await fetch(base + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  return [r.status, await r.json()];
};

const [, a] = await post('/rooms');
assert.match(a.code, /^\d{4}$/);
assert.equal(a.label, '1호차');

const [, b] = await post(`/rooms/${a.code}/join`);
assert.equal(b.label, '2호차');
assert.equal((await post('/rooms/0000/join'))[0], 404);

// 1호차가 위치를 올리고, 2호차가 받아 본다
await post(`/rooms/${a.code}/sync`, { key: a.key, state: { lat: 37.5, lon: 127.5, heading: 90, moving: true, energy: 80, ev: true, km: 12.3, cost: 4000, headcount: 3 } });
let [, s] = await post(`/rooms/${a.code}/sync`, { key: b.key, state: { lat: 37.5, lon: 127.46, moving: true, headcount: 2 }, after: 0 });
assert.equal(s.members.length, 2);
assert.equal(s.members[0].lat, 37.5);
assert.equal(s.members[0].headcount, 3);
assert.ok(!('key' in s.members[0]));

// 음성 메시지 전달
await post(`/rooms/${a.code}/messages`, { key: a.key, kind: 'voice', text: '다음 휴게소에서 쉬자' });
[, s] = await post(`/rooms/${a.code}/sync`, { key: b.key, state: {}, after: 0 });
assert.equal(s.messages.length, 1);
assert.equal(s.messages[0].from, '1호차');
const seq = s.seq;
[, s] = await post(`/rooms/${a.code}/sync`, { key: b.key, state: {}, after: seq });
assert.equal(s.messages.length, 0);

// 여행을 끝낸 차량은 위치가 지워지고 금액만 남는다
[, s] = await post(`/rooms/${a.code}/sync`, { key: a.key, state: { lat: 37.7, lon: 128.9, done: true, cost: 41000, headcount: 3 } });
const one = s.members.find((m) => m.id === '1');
assert.equal(one.lat, null);
assert.equal(one.cost, 41000);
assert.equal(one.done, true);

// 나가면 더는 올릴 수 없고, 끝내지 않은 차량은 목록에서 빠진다
await post(`/rooms/${a.code}/leave`, { key: b.key });
assert.equal((await post(`/rooms/${a.code}/sync`, { key: b.key, state: {} }))[0], 403);
[, s] = await post(`/rooms/${a.code}/sync`, { key: a.key, state: { done: true, cost: 41000, headcount: 3 }, after: seq });
assert.deepEqual(s.members.map((m) => m.id), ['1']);
assert.equal(s.messages.at(-1).kind, 'left');

assert.equal((await post(`/rooms/${a.code}/sync`, { key: 'wrong', state: {} }))[0], 403);
console.log('convoy server ok');
