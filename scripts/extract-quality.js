// 순수본 산출물_sql.html 의 90번(데이터 품질 진단·정제) SQL 8종 × 방언 5 와 메모를 JSON 으로 뽑는다(2-6).
// 실행: node scripts/extract-quality.js  → src/main/resources/quality/d90.json
// 순수본을 sync-pure.sh 로 새로 끌어왔으면 다시 돌리고 diff 를 본다. QualitySqlTest 골든이 바뀐 자리를 짚는다.
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..');
const lines = fs.readFileSync(path.join(root, 'pure', 'tools', '산출물_sql.html'), 'utf8').split(/\r?\n/);
const start = lines.findIndex(l => l.startsWith('var DOCS = ['));
let end = -1;
for (let i = start + 1; i < lines.length; i++) { if (lines[i].startsWith('];')) { end = i; break; } }
if (start < 0 || end < 0) { console.error('DOCS 를 못 찾았다'); process.exit(1); }
// DOCS 는 문자열·객체 리터럴뿐인 데이터다(순수본 동결본)
const DOCS = new Function(lines.slice(start, end + 1).join('\n') + ';return DOCS')();
const d90 = DOCS.find(d => d.key === 'd90');
if (!d90 || d90.sql.length !== 8) { console.error('90번 SQL 이 8개가 아니다'); process.exit(1); }
const IDS = ['format', 'nopk', 'keydup', 'nullish', 'dirty', 'datefix', 'datecheck', 'codes'];
const out = {
  sub: d90.sub,
  kinds: d90.sql.map((s, i) => ({ id: IDS[i], title: s.title, q: s.q })),
  notes: d90.notes || []
};
const file = path.join(root, 'src', 'main', 'resources', 'quality', 'd90.json');
fs.mkdirSync(path.dirname(file), { recursive: true });
fs.writeFileSync(file, JSON.stringify(out, null, 2) + '\n', 'utf8');
console.log('품질 진단 ' + out.kinds.length + '종 → ' + path.relative(root, file));
