// 휴게소 목록(app/src/main/assets/rest_areas.tsv)을 만든다. 함께 쉬기 제안에 쓴다.
//
// 자료: OpenStreetMap (© OpenStreetMap contributors, ODbL 1.0). highway=services 이면서 이름에 "휴게소"가 있는 곳.
// Pleos NaviHelper에는 장소 검색 API가 없어서, 앱에 목록을 넣고 가까운 곳을 직접 고른다.
//
// 사용: node tools/restareas/build.mjs [받아 둔 응답.json]
import { readFileSync, writeFileSync } from 'node:fs';

const query = '[out:json][timeout:90];area["ISO3166-1"="KR"][admin_level=2]->.kr;(nwr["highway"="services"]["name"](area.kr););out center tags;';
let data;
if (process.argv[2]) {
  data = JSON.parse(readFileSync(process.argv[2], 'utf8')); // 미리 받아 둔 Overpass 응답
} else {
  const res = await fetch('https://overpass-api.de/api/interpreter', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'User-Agent': 'restarea-fetch/1.0' },
    body: 'data=' + encodeURIComponent(query),
  });
  if (!res.ok) throw new Error(`overpass ${res.status}`);
  data = await res.json();
}

const dist = (a, b) => {
  const R = 6371000, r = Math.PI / 180;
  const dLat = (b.lat - a.lat) * r, dLon = (b.lon - a.lon) * r;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * r) * Math.cos(b.lat * r) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
};

const areas = [];
for (const e of data.elements) {
  const name = String(e.tags?.name || '').replace(/\s+/g, ' ').replace(/\t/g, ' ').trim();
  const lat = e.lat ?? e.center?.lat;
  const lon = e.lon ?? e.center?.lon;
  if (!name.includes('휴게소') || name.includes('주유소') || lat == null || lon == null) continue;
  const item = { name, lat: Number(lat.toFixed(5)), lon: Number(lon.toFixed(5)) };
  // 같은 휴게소가 점·면으로 겹쳐 들어온 것은 하나만 남긴다 (300m 안). 방향이 적힌 이름을 우선한다.
  const dup = areas.findIndex((a) => dist(a, item) < 300);
  if (dup < 0) areas.push(item);
  else if (/방향|상행|하행/.test(item.name) && !/방향|상행|하행/.test(areas[dup].name)) areas[dup] = item;
}
areas.sort((a, b) => a.name.localeCompare(b.name, 'ko'));

const out = new URL('../../app/src/main/assets/rest_areas.tsv', import.meta.url);
writeFileSync(out, '# © OpenStreetMap contributors (ODbL 1.0). 이름<TAB>위도<TAB>경도\n' + areas.map((a) => `${a.name}\t${a.lat}\t${a.lon}`).join('\n') + '\n', 'utf8');
console.log(`${areas.length} rest areas`);
