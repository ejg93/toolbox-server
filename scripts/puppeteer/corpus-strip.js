// 주석 삭제 실물 표본(V-2) — 떠 있는 서버의 백엔드본 dev_tools 에서 TB_CMT.strip 으로 표본 전부를 지운다.
// StripCorpusTest 가 앱을 띄우고 이 스크립트를 부른다. 손으로: node scripts/puppeteer/corpus-strip.js <서버 URL> <표본 폴더> <출력 폴더>
// 옵션은 화면 「주석 삭제」 버튼과 같다(dropLine·squeeze·trimEnd, 힌트 남김). JS 는 여기서 node vm 으로 전후 파싱을 잰다.
// 출력: <출력>/<출처>/<상대경로>(지운 결과, UTF-8) + <출력>/summary.json. 콘솔엔 합계 한 줄.
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const puppeteer = require('C:/workspace/node_modules/puppeteer');
const { corpusFiles, decode } = require('./corpus-common.js');

const [BASE, CORPUS, OUT] = process.argv.slice(2);
function lang(rel) {
  const ext = path.extname(rel).toLowerCase();
  switch (ext) {
    case '.java': return 'java';
    case '.jsp': return 'jsp';
    case '.js': return 'js';
    case '.jsx': case '.tsx': return 'jsx';
    case '.css': return 'css';
    case '.scss': case '.less': return 'less';
    case '.properties': return 'props';
    case '.sh': return 'sh';
    case '.bat': case '.cmd': return 'bat';
    case '.yaml': case '.yml': return 'yaml';
    case '.xml': return /(^|\/)mapper\//.test(rel) ? 'mybatis' : 'xml';
    case '.sql': return /(maria|mysql)/i.test(rel) ? 'mysql' : 'sql';
    default: return null;
  }
}

function jsParses(text) {
  try { new vm.Script(text); return true; } catch (e) { return false; }
}

(async () => {
  const files = corpusFiles(CORPUS, lang).map(f => Object.assign(f, { lang: lang(f.rel) }));
  const browser = await puppeteer.launch({ headless: 'new' });
  const page = await browser.newPage();
  const errs = [];
  page.on('pageerror', e => errs.push(e.message));
  await page.goto(BASE.replace(/\/$/, '') + '/tools/dev_tools.html', { waitUntil: 'networkidle0' });
  if (!(await page.evaluate(() => !!window.TB_CMT))) throw new Error('TB_CMT 가 없다');

  const summary = [];
  for (const f of files) {
    const dec = decode(fs.readFileSync(path.join(CORPUS, f.src, f.rel)));
    const text = dec.text;
    let r;
    try {
      r = await page.evaluate((t, l) => {
        const x = window.TB_CMT.strip(t, l, { dropLine: true, squeeze: true, trimEnd: true, killHint: false });
        return { text: x.removed ? x.text : t, removed: x.removed || 0, hints: x.hints || 0 };
      }, text, f.lang);
    } catch (e) {
      summary.push({ file: f.src + '/' + f.rel, lang: f.lang, error: String(e.message).slice(0, 200) });
      continue;
    }
    const dst = path.join(OUT, f.src, f.rel);
    fs.mkdirSync(path.dirname(dst), { recursive: true });
    fs.writeFileSync(dst, r.text, 'utf8');
    const row = { file: f.src + '/' + f.rel, lang: f.lang, enc: dec.enc, removed: r.removed, hints: r.hints };
    if (f.lang === 'js') { row.jsBefore = jsParses(text); row.jsAfter = jsParses(r.text); }
    summary.push(row);
  }
  await browser.close();
  fs.writeFileSync(path.join(OUT, 'summary.json'), JSON.stringify({ pageErrors: errs, files: summary }));
  const removed = summary.reduce((s, x) => s + (x.removed || 0), 0);
  console.log('표본 ' + summary.length + ' 파일 · 지운 주석 ' + removed + ' · 오류 ' + summary.filter(x => x.error).length + ' · 페이지 오류 ' + errs.length);
})().catch(e => { console.error(e); process.exit(1); });
