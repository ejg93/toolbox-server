// dev_tools 백엔드본 새 탭(4-9) 클릭 검증 — HtmlUnit 이 dev_tools 를 못 읽어(JS_OFF) 여기서 본다.
// 실행: 서버를 띄운 뒤  node scripts/puppeteer/smoke-devtools.js [http://127.0.0.1:41780]
// Puppeteer 는 C:/workspace/node_modules — 집 검증 전용, 반입 안 함. 경로에 한글 금지(CLAUDE.md 검증).
// 탭 넷(폴더 비교·로그 SQL 복원·INSERT 생성기·기존 탭 하나)을 열고 API 를 한 번씩 부른 뒤 콘솔 오류 0 을 본다.
// 스크린샷은 target/puppeteer/devtools-*.png.
const fs = require('fs');
const os = require('os');
const path = require('path');
const puppeteer = require('C:/workspace/node_modules/puppeteer');

const BASE = (process.argv[2] || 'http://127.0.0.1:41780').replace(/\/$/, '');
const ROOT = path.join(__dirname, '..', '..');
const SHOTS = path.join(ROOT, 'target', 'puppeteer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const fail = [];
const check = (ok, what) => { console.log((ok ? '  [통과] ' : '  [실패] ') + what); if (!ok) fail.push(what); };

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'devtools-'));
  const a = path.join(tmp, 'a'), b = path.join(tmp, 'b');
  fs.mkdirSync(a); fs.mkdirSync(b);
  fs.writeFileSync(path.join(a, 'x.jsp'), '1\n2\n3\n');
  fs.writeFileSync(path.join(b, 'x.jsp'), '1\n<b>둘</b>\n3\n');
  fs.writeFileSync(path.join(a, 'only.jsp'), 'a');

  const browser = await puppeteer.launch({ headless: 'new' });
  const page = await browser.newPage();
  const errs = [];
  page.on('pageerror', e => errs.push('pageerror: ' + e.message));
  // 「Failed to load resource」 콘솔 줄은 응답으로 센다 — 브라우저가 알아서 부르는 /favicon.ico 404 는 뺀다
  page.on('console', m => { if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) errs.push('console: ' + m.text()); });
  page.on('response', r => { if (r.status() >= 400 && !r.url().endsWith('/favicon.ico')) errs.push(r.status() + ' ' + r.url()); });
  await page.setViewport({ width: 1400, height: 900 });
  await page.goto(BASE + '/tools/dev_tools.html', { waitUntil: 'networkidle0' });

  // 폴더 비교
  await page.evaluate(() => switchTab('fdiff'));
  await page.type('#fd_a', a);
  await page.type('#fd_b', b);
  await page.click('#fd_run');
  await page.waitForFunction(() => /다름 \d/.test(document.getElementById('fd_msg').textContent), { timeout: 5000 }).catch(() => {});
  check((await page.$eval('#fd_msg', e => e.textContent)).startsWith('다름 1 · A 만 1'), '폴더 비교 분류');
  await page.click('#fd_list tr.link');
  await page.waitForSelector('#fd_file td.add', { timeout: 5000 }).catch(() => {});
  check(await page.$eval('#fd_file', e => e.textContent.includes('<b>둘</b>')), '줄 비교 — 태그는 글자로(textContent)');
  check(await page.$eval('#fd_file', e => e.querySelectorAll('b').length === 0), '줄 비교 — HTML 로 안 풀림');
  await page.screenshot({ path: path.join(SHOTS, 'devtools-fdiff.png') });

  // 로그 SQL 복원
  await page.evaluate(() => switchTab('logsql'));
  await page.$eval('#ls_in', e => { e.value = '==>  Preparing: SELECT * FROM T WHERE A = ? AND B = ?\n==> Parameters: 1(Integer), <img src=x onerror=alert(1)>(String)\n'; });
  await page.click('#ls_run');
  await page.waitForSelector('#ls_out .card pre', { timeout: 5000 }).catch(() => {});
  check(await page.$eval('#ls_out', e => e.textContent.includes("A = 1 AND B = '<img src=x onerror=alert(1)>'")), '로그 SQL 복원');
  check(await page.$eval('#ls_out', e => e.querySelectorAll('img').length === 0), '복원문 — HTML 로 안 풀림');
  await page.screenshot({ path: path.join(SHOTS, 'devtools-logsql.png') });

  // INSERT 생성기 「스냅샷/접속에서」 — 스냅샷이 없으면 안내 문구, 있으면 첫 스냅샷의 첫 테이블로 한 번
  await page.evaluate(() => switchTab('dummy'));
  const snaps = await page.$$eval('#ins_snap option', os => os.length - 1);
  if (snaps > 0) {
    await page.select('#ins_snap', await page.$eval('#ins_snap option:nth-child(2)', o => o.value));
    await page.waitForFunction(() => document.querySelectorAll('#ins_tables option').length > 0, { timeout: 5000 }).catch(() => {});
    const t = await page.$eval('#ins_tables option', o => o.value).catch(() => '');
    await page.type('#ins_table', t);
    await page.click('#ins_srv_btn');
    await page.waitForFunction(() => /생성함|실패/.test(document.getElementById('ins_srv_msg').textContent), { timeout: 5000 }).catch(() => {});
    check((await page.$eval('#ins_srv_msg', e => e.textContent)).startsWith('생성함'), 'INSERT 서버 생성(' + t + ')');
  } else {
    await page.click('#ins_srv_btn');
    check((await page.$eval('#ins_srv_msg', e => e.textContent)).includes('스냅샷과 테이블'), 'INSERT 서버 생성 — 스냅샷 없음 안내');
  }
  await page.screenshot({ path: path.join(SHOTS, 'devtools-insert.png') });

  // 주석 삭제(4-3) — 백엔드본 TB_CMT 가 순수본 골든(dump-strip.js)과 같은지
  const { CASES, FIX, OUT, stripBackend, render } = require('./dump-strip.js');
  for (const [file, lang] of Object.entries(CASES)) {
    const text = fs.readFileSync(path.join(FIX, file), 'utf8').replace(/\r\n/g, '\n');
    const want = fs.readFileSync(path.join(OUT, file + '.txt'), 'utf8').replace(/\r\n/g, '\n');
    check(render(lang, await stripBackend(page, text, lang)) === want, '주석 삭제 골든 — ' + file);
  }

  // 폴더 일괄(4-3) — CRLF java 하나 + 주석 없는 java 하나 + glob 밖 txt → 미리보기 표 1행 → 적용 → 결과가 골든 본문(CRLF 그대로)
  const sb = path.join(tmp, 'strip');
  fs.mkdirSync(path.join(sb, 'sub'), { recursive: true });
  const javaSrc = fs.readFileSync(path.join(FIX, 'sample.java'), 'utf8').replace(/\r\n/g, '\n');
  fs.writeFileSync(path.join(sb, 'sub', 'A.java'), javaSrc.replace(/\n/g, '\r\n'));
  fs.writeFileSync(path.join(sb, 'B.java'), 'class B {}\n');
  fs.writeFileSync(path.join(sb, 'c.txt'), '// glob 밖\n');
  await page.evaluate(() => switchTab('textdisplay'));
  await page.$eval('#td_lang', e => { e.value = 'java'; });
  await page.type('#td_dir', sb);
  await page.click('#td_batchPreview');
  await page.waitForFunction(() => /^미리보기 \d+개/.test(document.getElementById('td_batchMsg').textContent), { timeout: 5000 }).catch(() => {});
  check((await page.$eval('#td_batchMsg', e => e.textContent)).startsWith('미리보기 2개 · 바뀌는 것 1개'), '폴더 일괄 미리보기');
  check(fs.readFileSync(path.join(sb, 'sub', 'A.java'), 'utf8').includes('// 한 줄 주석'), '미리보기는 쓰지 않는다');
  await page.click('#td_batchApply');
  await page.waitForFunction(() => /^적용 \d+\/\d+개/.test(document.getElementById('td_batchMsg').textContent), { timeout: 5000 }).catch(() => {});
  check((await page.$eval('#td_batchMsg', e => e.textContent)).startsWith('적용 1/1개'), '폴더 일괄 적용');
  const golden = fs.readFileSync(path.join(OUT, 'sample.java.txt'), 'utf8').replace(/\r\n/g, '\n').split('\n\n').slice(1).join('\n\n');
  check(fs.readFileSync(path.join(sb, 'sub', 'A.java'), 'utf8') === golden.replace(/\n/g, '\r\n'), '적용 결과 = 골든 본문, CRLF 그대로');
  const backup = await page.$eval('#td_batchOut', e => (/백업 (.+)$/m.exec(e.textContent) || [])[1] || '');
  check(await page.$eval('#td_batchOut', e => e.textContent.includes('sub/A.java')), '결과표에 파일');
  check(/[\\\/]backup[\\\/]sub[\\\/]A\.java/.test(backup) || (await page.$eval('#td_batchOut', e => /backup[\\\/]sub[\\\/]A\.java/.test(e.textContent))), '백업 경로 표시');
  await page.screenshot({ path: path.join(SHOTS, 'devtools-strip.png') });

  // 기존 탭 하나 — 새 스크립트가 기존 JS 를 안 깨는지
  await page.evaluate(() => switchTab('json'));
  check(await page.$eval('#page-json', e => e.classList.contains('active')), '기존 탭(JSON) 전환');

  check(errs.length === 0, '콘솔 오류 0' + (errs.length ? ' — ' + errs.join(' | ') : ''));
  await browser.close();
  fs.rmSync(tmp, { recursive: true, force: true });
  console.log(fail.length ? '실패 ' + fail.length : '전부 통과');
  process.exit(fail.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
