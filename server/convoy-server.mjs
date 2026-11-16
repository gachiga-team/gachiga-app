// 같이가 컨보이 중계 서버 (같이 가기).
//
// 일행 차량끼리 "가장 최근 상태"와 짧은 메시지만 주고받게 해 주는 작은 서버다.
// - 의존성 없음 (Node.js 18 이상). 실행: node server/convoy-server.mjs [포트]
// - 모든 값은 메모리에만 둔다. 위치 이력을 쌓지 않고, 차량마다 마지막 위치 하나만 가진다.
// - 차량이 여행을 끝내거나 그룹에서 나가면 그 차량의 위치를 바로 지운다.
// - 그룹은 마지막 소식 뒤 12시간이 지나면 지운다.
// - 실명이나 차량 번호는 받지 않는다. 차량은 참여 순서대로 "1호차", "2호차"로 부른다.
import { createServer } from 'node:http';
import { randomBytes, randomInt } from 'node:crypto';

const PORT = Number(process.argv[2] || process.env.PORT || 8787);
const ROOM_TTL_MS = 12 * 60 * 60 * 1000;
const MAX_MEMBERS = 6;
const MAX_MESSAGES = 50;
const MAX_BODY = 4096;
const MAX_TEXT = 200;
const MESSAGE_KINDS = new Set(['voice', 'rest', 'rest-ok']);

/** code -> { members: Map<key, member>, messages: [], seq, nextNo, touchedAt } */
const rooms = new Map();

const now = () => Date.now();
const num = (v, min, max) => (typeof v === 'number' && Number.isFinite(v) && v >= min && v <= max ? v : null);

function newCode() {
  for (let i = 0; i < 1000; i++) {
    const code = String(randomInt(1000, 10000));
    if (!rooms.has(code)) return code;
  }
  throw new Error('no free code');
}

function addMember(room) {
  const key = randomBytes(12).toString('hex');
  const member = { id: String(room.nextNo), label: `${room.nextNo}호차`, state: {}, seenAt: now(), left: false };
  room.nextNo += 1;
  room.members.set(key, member);
  return { key, member };
}

function addMessage(room, member, kind, text) {
  room.seq += 1;
  room.messages.push({ seq: room.seq, fromId: member.id, from: member.label, kind, text });
  if (room.messages.length > MAX_MESSAGES) room.messages.shift();
  return room.seq;
}

/** 받은 상태에서 아는 값만 고른다. 끝낸 차량의 위치는 받지 않는다. */
function cleanState(s) {
  if (!s || typeof s !== 'object') return {};
  const done = s.done === true;
  return {
    lat: done ? null : num(s.lat, -90, 90),
    lon: done ? null : num(s.lon, -180, 180),
    heading: done ? null : num(s.heading, 0, 360),
    moving: typeof s.moving === 'boolean' ? s.moving : null,
    energy: num(s.energy, 0, 100),
    ev: s.ev === true,
    km: num(s.km, 0, 100000) ?? 0,
    cost: num(s.cost, 0, 100000000) ?? 0,
    headcount: num(s.headcount, 1, 9) ?? 1,
    done,
  };
}

function view(room, afterSeq) {
  const t = now();
  return {
    members: [...room.members.values()].filter((m) => !m.left || m.state.done).map((m) => ({
      id: m.id,
      label: m.label,
      ...m.state,
      left: m.left,
      age: Math.round((t - m.seenAt) / 1000),
    })),
    messages: room.messages.filter((m) => m.seq > afterSeq),
    seq: room.seq,
  };
}

function handle(method, path, body) {
  if (method === 'GET' && path === '/health') return [200, { ok: true, rooms: rooms.size }];

  if (method === 'POST' && path === '/rooms') {
    const code = newCode();
    const room = { members: new Map(), messages: [], seq: 0, nextNo: 1, touchedAt: now() };
    rooms.set(code, room);
    const { key, member } = addMember(room);
    return [200, { code, key, id: member.id, label: member.label }];
  }

  const m = /^\/rooms\/(\d{4})\/(join|sync|messages|leave)$/.exec(path);
  if (!m || method !== 'POST') return [404, { error: 'not_found' }];
  const room = rooms.get(m[1]);
  if (!room) return [404, { error: 'no_room' }];
  room.touchedAt = now();

  if (m[2] === 'join') {
    const active = [...room.members.values()].filter((x) => !x.left).length;
    if (active >= MAX_MEMBERS) return [409, { error: 'full' }];
    const { key, member } = addMember(room);
    return [200, { code: m[1], key, id: member.id, label: member.label }];
  }

  const member = room.members.get(String(body.key || ''));
  if (!member || member.left) return [403, { error: 'not_member' }];
  member.seenAt = now();

  if (m[2] === 'sync') {
    member.state = cleanState(body.state);
    return [200, view(room, num(body.after, 0, 1e9) ?? 0)];
  }
  if (m[2] === 'messages') {
    const text = typeof body.text === 'string' ? body.text.trim().slice(0, MAX_TEXT) : '';
    const kind = MESSAGE_KINDS.has(body.kind) ? body.kind : 'voice';
    if (!text) return [400, { error: 'empty' }];
    return [200, { seq: addMessage(room, member, kind, text) }];
  }
  // leave: 위치를 지운다. 끝낸 차량의 정산 금액은 합산 정산을 위해 남긴다.
  member.left = true;
  member.state = { ...member.state, lat: null, lon: null, heading: null, moving: null };
  addMessage(room, member, 'left', '');
  return [200, { ok: true }];
}

const server = createServer((req, res) => {
  const send = (status, obj) => {
    const data = JSON.stringify(obj);
    res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
    res.end(data);
  };
  let raw = '';
  let tooBig = false;
  req.setEncoding('utf8');
  req.on('data', (chunk) => {
    raw += chunk;
    if (raw.length > MAX_BODY) { tooBig = true; req.destroy(); }
  });
  req.on('end', () => {
    if (tooBig) return;
    let body = {};
    if (raw) {
      try { body = JSON.parse(raw); } catch { return send(400, { error: 'bad_json' }); }
      if (!body || typeof body !== 'object') body = {};
    }
    try {
      const [status, obj] = handle(req.method, new URL(req.url, 'http://x').pathname, body);
      send(status, obj);
    } catch (e) {
      send(500, { error: 'server' });
    }
  });
});

setInterval(() => {
  const t = now();
  for (const [code, room] of rooms) if (t - room.touchedAt > ROOM_TTL_MS) rooms.delete(code);
}, 60_000).unref();

server.listen(PORT, () => console.log(`gachiga convoy server on :${PORT}`));
