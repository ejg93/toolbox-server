// 순수본 dev_tools 의 cmtStrip 으로 주석 삭제 픽스처를 돌려 골든을 뜬다(4-3). 백엔드본 폴더 일괄은 같은 JS 를 쓴다 —
// smoke-devtools.js 가 백엔드본 cmtStrip 결과를 이 골든과 대조한다.
// 실행: node scripts/puppeteer/dump-strip.js   (Puppeteer 는 C:/workspace/node_modules — 집 검증 전용, 반입 안 함)
// 옵션은 화면의 「주석 삭제」 버튼과 같다 — dropLine·squeeze·trimEnd 켬, 힌트 삭제 끔.
const fs = require('fs');
const path = require('path');
const puppeteer = require('C:/workspace/node_modules/puppeteer');

const ROOT = path.join(__dirname, '..', '..');
const TOOL = 'file:///' + path.join(ROOT, 'pure', 'tools', 'dev_tools.html').replace(/\\/g, '/');
const FIX = path.join(ROOT, 'src', 'test', 'resources', 'fixtures', 'strip');
const OUT = path.join(ROOT, 'src', 'test', 'resources', 'golden', 'strip');
// 픽스처 → 언어(화면 td_lang 값)
const CASES = {
  'sample.java': 'java', 'sample.js': 'js', 'sample.sql': 'sql',
  'sample.jsp': 'jsp', 'sample_mapper.xml': 'mybatis', 'sample.properties': 'props'
};

// 순수본 — cmtStrip 이 IIFE 안이라 화면 흐름으로 돈다: 입력칸(이벤트 없이 — 언어 자동 판별을 안 타게) → 언어 → 「주석 삭제」.
// 지운 수·남긴 힌트는 안내 문구에서 읽는다
async function stripPure(page, text, lang) {
  return page.evaluate((t, l) => {
    document.getElementById('codeInput').value = t;
    document.getElementById('td_lang').value = l;
    document.getElementById('td_killHint').checked = false;
    document.getElementById('td_btnStrip').click();
    const say = document.getElementById('td_stripInfo').textContent;
    const m = /주석 (\d+)개 삭제/.exec(say), h = /힌트 (\d+)개 남김/.exec(say);
    return { text: document.getElementById('codeInput').value, removed: m ? +m[1] : 0, hints: h ? +h[1] : 0 };
  }, text, lang);
}

// 백엔드본 — IIFE 가 내보낸 window.TB_CMT(폴더 일괄이 쓰는 것) 로 같은 옵션
async function stripBackend(page, text, lang) {
  return page.evaluate((t, l) => {
    const r = window.TB_CMT.strip(t, l, { dropLine: true, squeeze: true, trimEnd: true, killHint: false });
    return { text: r.removed ? r.text : t, removed: r.removed, hints: r.hints || 0 };
  }, text, lang);
}

// 골든 한 벌 — 머리 한 줄(언어·지운 수·남긴 힌트) + 빈 줄 + 결과
function render(lang, r) {
  return '# lang=' + lang + ' removed=' + r.removed + ' hints=' + r.hints + '\n\n' + r.text.replace(/\r\n/g, '\n');
}

module.exports = { CASES, FIX, OUT, stripBackend, render };

if (require.main === module) {
  (async () => {
    fs.mkdirSync(OUT, { recursive: true });
    const browser = await puppeteer.launch({ headless: 'new' });
    const page = await browser.newPage();
    const errs = [];
    page.on('pageerror', e => errs.push(e.message));
    await page.goto(TOOL, { waitUntil: 'load' });
    for (const [file, lang] of Object.entries(CASES)) {
      const text = fs.readFileSync(path.join(FIX, file), 'utf8').replace(/\r\n/g, '\n');
      const r = await stripPure(page, text, lang);
      fs.writeFileSync(path.join(OUT, file + '.txt'), render(lang, r));
      console.log(file, lang, 'removed', r.removed);
    }
    await browser.close();
    if (errs.length) { console.error('페이지 오류:', errs.join(' | ')); process.exit(1); }
  })().catch(e => { console.error(e); process.exit(1); });
}
