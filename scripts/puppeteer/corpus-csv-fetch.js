// 공공데이터포털 파일데이터(CSV) 표본 받기(V-4). 목록에서 파일데이터 ID 를 뽑고, 상세 페이지의 「다운로드」 를 눌러 받는다.
// 로그인 없이 받히는 것만. 라이선스는 상세 페이지의 이용허락범위가 「이용허락범위 제한 없음」(공공누리 1유형 결)인 것만 남긴다.
// 실행: node scripts/puppeteer/corpus-csv-fetch.js <표본 폴더> [개수=100]   → <표본>/csv/<ID>.csv + <표본>/csv/SOURCES.tsv
// 외부 통신은 이 받기 스크립트뿐(집, 한 번) — 앱·테스트는 받은 파일만 읽는다(절대 규칙 1 은 런타임).
const fs = require('fs');
const path = require('path');
const puppeteer = require('C:/workspace/node_modules/puppeteer');

const [CORPUS, WANT = '100'] = process.argv.slice(2);
const OUT = path.join(CORPUS, 'csv');
const TMP = path.join(CORPUS, '.csv-download');
const sleep = ms => new Promise(r => setTimeout(r, ms));

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  fs.rmSync(TMP, { recursive: true, force: true });
  fs.mkdirSync(TMP, { recursive: true });
  const browser = await puppeteer.launch({ headless: 'new' });
  const page = await browser.newPage();
  // 「변경된 데이터」 알림 확인창 — 처리하지 않으면 페이지가 멈춘다(첫 판 20분에 3개)
  page.on('dialog', d => d.accept().catch(() => {}));
  const cdp = await page.createCDPSession();
  await cdp.send('Browser.setDownloadBehavior', { behavior: 'allow', downloadPath: TMP });

  const ids = [];
  for (let p = 1; p <= 10 && ids.length < 1000; p++) {
    await page.goto('https://www.data.go.kr/tcs/dss/selectDataSetList.do?dType=FILE&extsn=CSV&perPage=100&sort=updtDt&currentPage=' + p,
      { waitUntil: 'domcontentloaded', timeout: 60000 });
    const got = await page.$$eval('a[href*="/fileData.do"]', as => as.map(a => (a.getAttribute('href').match(/\/data\/(\d+)\/fileData/) || [])[1]).filter(Boolean));
    got.forEach(i => { if (!ids.includes(i)) ids.push(i); });
  }
  const rows = ['id\tfile\tlicense\ttitle'];
  let ok = 0;
  for (const id of ids) {
    if (ok >= +WANT) break;
    try {
      await page.goto('https://www.data.go.kr/data/' + id + '/fileData.do', { waitUntil: 'domcontentloaded', timeout: 60000 });
      const info = await page.evaluate(() => {
        const t = document.body.innerText;
        const lic = (t.match(/이용허락범위\s*\n?\s*([^\n]+)/) || [])[1] || '';
        const title = (document.querySelector('h3, .data-title, title') || {}).textContent || '';
        return { lic: lic.trim(), title: title.trim().slice(0, 80) };
      });
      if (!/제한\s*없음/.test(info.lic)) continue;
      const before = new Set(fs.readdirSync(TMP));
      const btn = await page.$('a[onclick*="fn_fileDataDown"], button[onclick*="fn_fileDataDown"]');
      if (!btn) continue;
      await btn.click();
      let got = null;
      for (let i = 0; i < 20 && !got; i++) {
        await sleep(500);
        got = fs.readdirSync(TMP).find(f => !before.has(f) && !f.endsWith('.crdownload'));
      }
      if (!got || !/\.csv$/i.test(got)) { if (got) fs.rmSync(path.join(TMP, got), { force: true }); continue; }
      const size = fs.statSync(path.join(TMP, got)).size;
      if (size > 3 * 1024 * 1024 || size < 100) { fs.rmSync(path.join(TMP, got), { force: true }); continue; } // 3MB 넘는 것은 뺀다(표본 크기)
      fs.renameSync(path.join(TMP, got), path.join(OUT, id + '.csv'));
      rows.push([id, got.replace(/\t/g, ' '), info.lic, info.title.replace(/\t/g, ' ')].join('\t'));
      ok++;
    } catch (e) { /* 한 건 실패는 건너뛴다 */ }
  }
  await browser.close();
  fs.rmSync(TMP, { recursive: true, force: true });
  fs.writeFileSync(path.join(OUT, 'SOURCES.tsv'), rows.join('\n') + '\n');
  console.log('CSV ' + ok + ' 받음(후보 ' + ids.length + ')');
})().catch(e => { console.error(e); process.exit(1); });
