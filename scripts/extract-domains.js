// 순수본 논리명 변환기 HTML 에 박힌 DOMAINDB(행안부 공통표준도메인 표)를 CSV 로 뽑는다(3-1).
// 실행: node scripts/extract-domains.js  → src/main/resources/dict/moi-domains.csv
// 순수본을 sync-pure.sh 로 새로 끌어왔으면 다시 돌리고 diff 를 본다.
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..');
const html = fs.readFileSync(path.join(root, 'pure', 'tools', '논리명_변환기.html'), 'utf8');
const start = html.indexOf('const DOMAINDB=[');
const end = html.indexOf('];', start);
if (start < 0 || end < 0) { console.error('DOMAINDB 를 못 찾았다'); process.exit(1); }
const rows = JSON.parse(html.slice(start + 'const DOMAINDB='.length, end + 1));
const head = ['그룹', '분류', '도메인명', '타입', '길이', '소수점', '저장형식', '표현형식', '단위', '허용값', '설명'];
const q = s => { s = String(s == null ? '' : s); return /[",\r\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s; };
const out = [head.join(',')].concat(rows.map(r => { if (r.length !== 11) throw new Error('열 수 ' + r.length); return r.map(q).join(','); }));
const file = path.join(root, 'src', 'main', 'resources', 'dict', 'moi-domains.csv');
fs.mkdirSync(path.dirname(file), { recursive: true });
fs.writeFileSync(file, out.join('\n') + '\n', 'utf8');
console.log('도메인 ' + rows.length + '행 → ' + path.relative(root, file));
