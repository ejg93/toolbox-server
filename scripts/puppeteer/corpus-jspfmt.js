// jsp_formatter 실물 표본(V-3) — 떠 있는 서버의 백엔드본 jsp_formatter 에서 formatJsp·compareDoc 를 표본 JSP 전부에 돌린다.
// JspFmtCorpusTest 가 앱을 띄우고 부른다. 손으로: node scripts/puppeteer/corpus-jspfmt.js <서버 URL> <표본 폴더> <출력 폴더>
// 옵션은 화면 기본값(opts()). 멱등은 결과를 한 번 더 포맷해 같은지. 출력: <출력>/summary.json(수만 — 코드 본문 없음)
const fs = require('fs');
const path = require('path');
const puppeteer = require('C:/workspace/node_modules/puppeteer');
const { corpusFiles, decode } = require('./corpus-common.js');

const [BASE, CORPUS, OUT] = process.argv.slice(2);

(async () => {
  const files = corpusFiles(CORPUS, r => r.toLowerCase().endsWith('.jsp'));
  const browser = await puppeteer.launch({ headless: 'new' });
  const page = await browser.newPage();
  const errs = [];
  page.on('pageerror', e => errs.push(e.message));
  await page.goto(BASE.replace(/\/$/, '') + '/tools/jsp_formatter.html', { waitUntil: 'networkidle0' });

  const summary = [];
  for (const f of files) {
    const text = decode(fs.readFileSync(path.join(CORPUS, f.src, f.rel))).text;
    try {
      const r = await page.evaluate(t => {
        const o = opts();
        const out = formatJsp(t, o);
        const c = compareDoc(t, out);
        return {
          struct: c.struct.length, gapChg: c.gapChg.length, preChg: c.preChg.length, unbalanced: c.unbalanced.length,
          tooBig: !!c.tooBig, tokens: c.tokens, idem: formatJsp(out, o) === out, before: t.length, after: out.length
        };
      }, text);
      summary.push(Object.assign({ file: f.src + '/' + f.rel }, r));
    } catch (e) {
      summary.push({ file: f.src + '/' + f.rel, error: String(e.message).slice(0, 200) });
    }
  }
  await browser.close();
  fs.mkdirSync(OUT, { recursive: true });
  fs.writeFileSync(path.join(OUT, 'summary.json'), JSON.stringify({ pageErrors: errs, files: summary }));
  const n = k => summary.filter(x => x[k]).length;
  console.log('JSP ' + summary.length + ' · struct>0 ' + summary.filter(x => x.struct > 0).length + ' · 멱등 아님 '
    + summary.filter(x => x.idem === false).length + ' · 오류 ' + n('error') + ' · 페이지 오류 ' + errs.length);
})().catch(e => { console.error(e); process.exit(1); });
