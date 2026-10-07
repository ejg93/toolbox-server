// 탭 여럿을 연 채 스냅샷 중지(0-50) — 브라우저는 한 서버에 연결 6개까지만 연다. 숨은 탭이 배지 SSE 를 쥐면 중지 DELETE 가 줄을 섰다.
// 실행: node scripts/puppeteer/smoke-tabs.js   (먼저 scripts/mvn.sh -q package -DskipTests 로 target/app.jar)
// 임시 폴더에 H2 표 1500 개 프로필과 data 를 만들고 서버를 따로 띄운다. 다른 탭 다섯(배지 「백엔드 연결」)을 연 뒤
// db_browser 탭을 앞으로(bringToFront — 나머지가 실제로 hidden) → 찍기 → 중지 → 5초 안에 「중지함」 · DELETE cancelled:true · 저장 0.
// Puppeteer 는 C:/workspace/node_modules — 집 검증 전용, 반입 안 함. 경로에 한글 금지(CLAUDE.md 검증).
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawn } = require('child_process');
const puppeteer = require('C:/workspace/node_modules/puppeteer');

const ROOT = path.join(__dirname, '..', '..');
const JAVA = process.env.JAVA || 'C:/Program Files/Java/jdk-17.0.19/bin/java';
const PORT = 41782;
const BASE = 'http://127.0.0.1:' + PORT;
const sleep = ms => new Promise(r => setTimeout(r, ms));
const fail = [];
let pass = 0;
const check = (ok, what) => { console.log((ok ? '  [통과] ' : '  [실패] ') + what); if (ok) pass++; else fail.push(what); };
const slash = p => p.split(path.sep).join('/');

async function waitText(page, sel, re, ms) {
  await page.waitForFunction((s, src) => new RegExp(src).test(document.querySelector(s).textContent), { timeout: ms }, sel, re.source)
    .catch(() => {});
  return page.$eval(sel, e => e.textContent);
}

(async () => {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'tabs-'));
  const profiles = path.join(tmp, 'profiles');
  fs.mkdirSync(profiles);
  const ddl = [];
  for (let i = 1; i <= 1500; i++) {
    ddl.push('CREATE TABLE IF NOT EXISTS T' + String(i).padStart(4, '0') + '(ID INT PRIMARY KEY, NAME VARCHAR(20));');
  }
  const script = path.join(tmp, 'big.sql');
  fs.writeFileSync(script, ddl.join('\n') + '\n');
  const url = "jdbc:h2:mem:big;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM '" + slash(script) + "'";
  fs.writeFileSync(path.join(profiles, 'big.yaml'), 'name: big\nconnections:\n  - id: h2\n    dialect: h2\n    url: "' + url
    + '"\n    user: sa\nscope:\n  schemas: [PUBLIC]\n');

  const server = spawn(JAVA, ['-jar', path.join(ROOT, 'target', 'app.jar'), 'serve', '--no-browser', '--port', String(PORT),
    '--profile', 'big', '--profiles-dir', profiles, '--data-dir', path.join(tmp, 'data')], { cwd: tmp, stdio: 'ignore' });
  let browser;
  try {
    for (let i = 0; i < 60; i++) {
      try { if ((await fetch(BASE + '/api/ping')).ok) break; } catch (e) { /* 아직 */ }
      await sleep(500);
    }
    browser = await puppeteer.launch({ headless: 'new' });
    const others = [];
    for (let i = 0; i < 5; i++) {
      const t = await browser.newPage();
      await t.goto(BASE + '/tools/' + (i % 2 ? 'code_check.html' : 'index.html'), { waitUntil: 'domcontentloaded' });
      await waitText(t, '#tb-mode-badge', /백엔드 연결/, 10000);
      others.push(t);
    }
    const page = await browser.newPage();
    let deleted = '';
    page.on('response', r => { if (r.request().method() === 'DELETE') r.text().then(x => { deleted = x; }); });
    await page.goto(BASE + '/tools/db_browser.html', { waitUntil: 'domcontentloaded' });
    await page.bringToFront();
    await sleep(1000);
    const vis = await Promise.all(others.map(t => t.evaluate(() => document.visibilityState)));
    check(vis.every(v => v === 'hidden'), '다른 탭 다섯이 숨음: ' + vis.join(','));
    await page.waitForSelector('#conns .item', { timeout: 10000 });
    await page.click('#conns .item');
    await page.click('#snapTake');
    const prog = await waitText(page, '#snapMsg', /테이블 \d+\/1500/, 30000);
    check(/테이블 \d+\/1500/.test(prog), '진행 문구: ' + prog);
    const t0 = Date.now();
    await page.click('#snapStop');
    const stopped = await waitText(page, '#snapMsg', /중지함|완료/, 30000);
    check(stopped === '중지함 — 저장하지 않았다' && Date.now() - t0 < 5000, '5초 안에 중지: ' + stopped + ' (' + (Date.now() - t0) + 'ms)');
    await sleep(500);
    check(deleted.includes('"cancelled":true'), 'DELETE 응답: ' + deleted);
    const list = await (await fetch(BASE + '/api/meta/snapshots')).json();
    check(list.length === 0, '저장 0: ' + list.length);
  } finally {
    if (browser) await browser.close();
    server.kill();
    await sleep(1000);
    fs.rmSync(tmp, { recursive: true, force: true, maxRetries: 5, retryDelay: 500 });
  }
  console.log('통과 ' + pass + ' · 실패 ' + fail.length);
  process.exit(fail.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
