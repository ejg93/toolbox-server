// 순수본 코드 검사(5-10) — file:// 로 연 순수본 code_check.html 에 5-1 픽스처를 하나씩 붙여 넣고 결과 표의 (줄, 규칙) 을 모은다.
// PureCodeCheckTest 가 부른다(앱 없이). 손으로: node scripts/puppeteer/corpus-check-pure.js <순수본 파일 URL> <픽스처 폴더> <출력 폴더>
// 언어는 확장자(#lang 옵션 java·jsp·js·ts·tsx·xml·html·properties). 출력: <출력>/summary.json — 원문은 안 싣는다
const fs = require('fs');
const path = require('path');
const puppeteer = require('C:/workspace/node_modules/puppeteer');

const [PAGE, FIXTURES, OUT] = process.argv.slice(2);
// 정규식 규칙 묶음만 — file·java·mybatis 는 순수본에 없다
const DIRS = ['common', 'jsp', 'tsx', 'security'];

(async () => {
  const browser = await puppeteer.launch({ headless: 'new' });
  const page = await browser.newPage();
  const errs = [];
  page.on('pageerror', e => errs.push(e.message));
  page.on('console', m => { if (m.type() === 'error') errs.push(m.text()); });
  await page.goto(PAGE, { waitUntil: 'load' });

  const files = [];
  for (const d of DIRS) {
    for (const name of fs.readdirSync(path.join(FIXTURES, d)).sort()) {
      const text = fs.readFileSync(path.join(FIXTURES, d, name), 'utf8');
      const lang = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
      const hits = await page.evaluate((t, l) => {
        document.getElementById('src').value = t;
        document.getElementById('lang').value = l;
        document.getElementById('run').click();
        return Array.from(document.querySelectorAll('#result tbody tr')).map(tr => {
          const td = tr.querySelectorAll('td');
          return { line: Number(td[0].textContent), rule: td[2].textContent };
        });
      }, text, lang);
      files.push({ file: d + '/' + name, lang, hits });
    }
  }
  await browser.close();
  fs.mkdirSync(OUT, { recursive: true });
  fs.writeFileSync(path.join(OUT, 'summary.json'), JSON.stringify({ files, pageErrors: errs }, null, 1));
})().catch(e => { console.error(e); process.exit(1); });
