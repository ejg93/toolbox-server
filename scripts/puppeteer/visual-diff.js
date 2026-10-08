// 시각 하네스 비교(1-48, 설계 18) — out/visual/<before> 와 out/visual/<after> 의 같은 이름 PNG 를 픽셀로 맞댄다.
// 실행: node scripts/puppeteer/visual-diff.js <beforeLabel> <afterLabel>
// 출력: out/visual/<after>/diff/<name>.png(다른 픽셀을 빨강으로 덧칠) · diff.json(바뀐 비율 내림차순) · report.html(전·후·차 세 그림)
// 이미지 비교 패키지 없이 Puppeteer 페이지의 캔버스로 센다 — 그림은 data: URL 로 넣는다(file:// 그림은 캔버스가 오염돼 못 읽는다).
const fs = require('fs');
const path = require('path');
const puppeteer = require('C:/workspace/node_modules/puppeteer');

const ROOT = path.join(__dirname, '..', '..');
const [A, B] = process.argv.slice(2);
if (!A || !B) { console.error('쓰는 법: node scripts/puppeteer/visual-diff.js <beforeLabel> <afterLabel>'); process.exit(2); }
const DIR_A = path.join(ROOT, 'out', 'visual', A);
const DIR_B = path.join(ROOT, 'out', 'visual', B);
const DIFF = path.join(DIR_B, 'diff');
const pngs = d => fs.existsSync(d) ? fs.readdirSync(d).filter(f => f.endsWith('.png')) : [];

(async () => {
  const a = new Set(pngs(DIR_A)), b = new Set(pngs(DIR_B));
  if (!a.size || !b.size) { console.error('그림이 없다: ' + (a.size ? DIR_B : DIR_A)); process.exit(2); }
  fs.mkdirSync(DIFF, { recursive: true });
  const both = [...a].filter(n => b.has(n)).sort();
  const browser = await puppeteer.launch({ headless: 'new' });
  const page = await browser.newPage();
  await page.goto('about:blank');
  const rows = [];
  try {
    for (const name of both) {
      const da = 'data:image/png;base64,' + fs.readFileSync(path.join(DIR_A, name)).toString('base64');
      const db = 'data:image/png;base64,' + fs.readFileSync(path.join(DIR_B, name)).toString('base64');
      const r = await page.evaluate(async (sa, sb) => {
        const load = s => new Promise((ok, no) => { const i = new Image(); i.onload = () => ok(i); i.onerror = no; i.src = s; });
        const [ia, ib] = await Promise.all([load(sa), load(sb)]);
        const w = Math.max(ia.width, ib.width), h = Math.max(ia.height, ib.height);
        const ctx = (im) => { const c = document.createElement('canvas'); c.width = w; c.height = h; const x = c.getContext('2d'); x.drawImage(im, 0, 0); return x; };
        const xa = ctx(ia), xb = ctx(ib);
        const pa = xa.getImageData(0, 0, w, h).data, pb = xb.getImageData(0, 0, w, h);
        const d = pb.data;
        let changed = 0;
        for (let i = 0; i < d.length; i += 4) {
          const px = (i / 4) % w, py = Math.floor(i / 4 / w);
          const out = px >= ia.width || py >= ia.height || px >= ib.width || py >= ib.height;
          if (out || Math.abs(pa[i] - d[i]) > 32 || Math.abs(pa[i + 1] - d[i + 1]) > 32 || Math.abs(pa[i + 2] - d[i + 2]) > 32) {
            changed++; d[i] = 255; d[i + 1] = 0; d[i + 2] = 0; d[i + 3] = 255;
          } else { d[i + 3] = 70; }
        }
        xb.putImageData(pb, 0, 0);
        return { changed, total: w * h, sizeA: ia.width + 'x' + ia.height, sizeB: ib.width + 'x' + ib.height, png: xb.canvas.toDataURL('image/png') };
      }, da, db);
      fs.writeFileSync(path.join(DIFF, name), Buffer.from(r.png.split(',')[1], 'base64'));
      rows.push({ name, changedPct: Math.round(r.changed / r.total * 10000) / 100, sizeA: r.sizeA, sizeB: r.sizeB });
    }
  } finally {
    await browser.close();
  }
  rows.sort((x, y) => y.changedPct - x.changedPct);
  const onlyA = [...a].filter(n => !b.has(n)).sort(), onlyB = [...b].filter(n => !a.has(n)).sort();
  fs.writeFileSync(path.join(DIR_B, 'diff.json'), JSON.stringify({ before: A, after: B, rows, onlyBefore: onlyA, onlyAfter: onlyB }, null, 1));
  const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;');
  const rel = (lab, n) => '../' + lab + '/' + n;
  const html = '<!doctype html><meta charset="utf-8"><title>visual diff ' + esc(A) + ' → ' + esc(B) + '</title>'
    + '<style>body{font:12px Consolas,monospace;background:#1e1e1e;color:#d4d4d4;margin:16px}table{border-collapse:collapse}'
    + 'td{vertical-align:top;padding:4px;border-bottom:1px solid #3c3c3c}img{width:420px;border:1px solid #3c3c3c}h1{font-size:14px}</style>'
    + '<h1>' + esc(A) + ' → ' + esc(B) + ' · ' + rows.length + '쌍 · 바뀜 ' + rows.filter(r => r.changedPct > 0).length
    + (onlyA.length ? ' · 전에만 ' + onlyA.length : '') + (onlyB.length ? ' · 후에만 ' + onlyB.length : '') + '</h1><table>'
    + rows.map(r => '<tr><td>' + esc(r.name) + '<br>' + r.changedPct + '%<br>' + esc(r.sizeA) + ' → ' + esc(r.sizeB) + '</td>'
      + '<td><img src="' + rel(A, r.name) + '"></td><td><img src="' + r.name + '"></td><td><img src="diff/' + r.name + '"></td></tr>').join('')
    + '</table>';
  fs.writeFileSync(path.join(DIR_B, 'report.html'), html);
  const top = rows.filter(r => r.changedPct > 0).slice(0, 15);
  console.log(A + ' → ' + B + ' · ' + rows.length + '쌍 · 바뀜 ' + top.length + (top.length ? '' : '(전부 0%)'));
  for (const r of top) console.log('  ' + r.changedPct.toFixed(2).padStart(6) + '%  ' + r.name);
  if (onlyA.length) console.log('  전에만 ' + onlyA.length + ': ' + onlyA.slice(0, 5).join(', '));
  if (onlyB.length) console.log('  후에만 ' + onlyB.length + ': ' + onlyB.slice(0, 5).join(', '));
  console.log('보고서: ' + path.join(DIR_B, 'report.html'));
})().catch(e => { console.error(e); process.exit(1); });
