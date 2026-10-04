// DB 스냅샷 · DTO 생성 화면(db_browser) 스냅샷 찍기·중지·완료 문구 검증(1-16) — HtmlUnit 은 EventSource 가 없어 여기서 본다.
// 실행: node scripts/puppeteer/smoke-dbbrowser.js   (먼저 scripts/mvn.sh -q package -DskipTests 로 target/app.jar)
// 임시 폴더에 프로필 둘(big — H2 메모리 DB 표 1500 개, zero — 없는 스키마)과 data 를 만들고 서버를 따로 띄운다.
// 찍기 → 진행 「테이블 」 → 중지 → 「중지함」 · 다시 찍기 → 「완료 — 스냅샷 #」·toolbox.mv.db · zero 로 찍기 → 「표 0개」.
// Puppeteer 는 C:/workspace/node_modules — 집 검증 전용, 반입 안 함. 경로에 한글 금지(CLAUDE.md 검증).
// 스크린샷은 target/puppeteer/dbbrowser-*.png.
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawn } = require('child_process');
const puppeteer = require('C:/workspace/node_modules/puppeteer');

const ROOT = path.join(__dirname, '..', '..');
const SHOTS = path.join(ROOT, 'target', 'puppeteer');
const JAVA = process.env.JAVA || 'C:/Program Files/Java/jdk-17.0.19/bin/java';
const PORT = 41781;
const BASE = 'http://127.0.0.1:' + PORT;
const sleep = ms => new Promise(r => setTimeout(r, ms));
const fail = [];
const check = (ok, what) => { console.log((ok ? '  [통과] ' : '  [실패] ') + what); if (!ok) fail.push(what); };
const slash = p => p.split(path.sep).join('/');

async function waitText(page, sel, re, ms) {
  await page.waitForFunction((s, src) => new RegExp(src).test(document.querySelector(s).textContent), { timeout: ms }, sel, re.source)
    .catch(() => {});
  return page.$eval(sel, e => e.textContent);
}

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'dbbrowser-'));
  const profiles = path.join(tmp, 'profiles');
  fs.mkdirSync(profiles);
  const ddl = [];
  for (let i = 1; i <= 1500; i++) {
    const n = 'T' + String(i).padStart(4, '0');
    ddl.push('CREATE TABLE IF NOT EXISTS ' + n + '(ID INT PRIMARY KEY, NAME VARCHAR(20), AMT DECIMAL(10,2), CREATED TIMESTAMP);');
  }
  const script = path.join(tmp, 'big.sql');
  fs.writeFileSync(script, ddl.join('\n') + '\n');
  const url = "jdbc:h2:mem:big;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM '" + slash(script) + "'";
  const conn = '  - id: h2\n    dialect: h2\n    url: "' + url + '"\n    user: sa\n';
  fs.writeFileSync(path.join(profiles, 'big.yaml'), 'name: big\nconnections:\n' + conn + 'scope:\n  schemas: [PUBLIC]\n');
  fs.writeFileSync(path.join(profiles, 'zero.yaml'), 'name: zero\nconnections:\n' + conn + 'scope:\n  schemas: [NOPE]\n');

  const server = spawn(JAVA, ['-jar', path.join(ROOT, 'target', 'app.jar'), 'serve', '--no-browser', '--port', String(PORT),
    '--profile', 'big', '--profiles-dir', profiles, '--data-dir', path.join(tmp, 'data')], { cwd: tmp, stdio: 'ignore' });
  let browser;
  try {
    for (let i = 0; i < 60; i++) {
      try { if ((await fetch(BASE + '/api/ping')).ok) break; } catch (e) { /* 아직 */ }
      await sleep(500);
    }
    browser = await puppeteer.launch({ headless: 'new' });
    const page = await browser.newPage();
    const errs = [];
    page.on('pageerror', e => errs.push('pageerror: ' + e.message));
    page.on('console', m => { if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) errs.push('console: ' + m.text()); });
    page.on('response', r => { if (r.status() >= 400 && !r.url().endsWith('/favicon.ico')) errs.push(r.status() + ' ' + r.url()); });
    await page.setViewport({ width: 1400, height: 900 });
    await page.goto(BASE + '/tools/db_browser.html', { waitUntil: 'networkidle0' });
    check((await page.title()) === 'DB 스냅샷 · DTO 생성', '이름');

    // 찍기 → 진행 → 중지
    await page.click('#conns .item');
    check(await page.$eval('#snapStop', e => e.disabled), '찍기 전 중지 꺼짐');
    await page.click('#snapTake');
    const prog = await waitText(page, '#snapMsg', /테이블 \d+\/1500/, 30000);
    check(/테이블 \d+\/1500 — T\d{4}/.test(prog), '진행 문구 「테이블 d/t — 이름」: ' + prog);
    check(!(await page.$eval('#snapStop', e => e.disabled)), '진행 중 중지 켜짐');
    await page.click('#snapStop');
    const stopped = await waitText(page, '#snapMsg', /중지함/, 30000);
    check(stopped === '중지함 — 저장하지 않았다', '중지 문구: ' + stopped);
    check(await page.$eval('#snapStop', e => e.disabled) && !(await page.$eval('#snapTake', e => e.disabled)), '중지 뒤 버튼');
    const list1 = await (await fetch(BASE + '/api/meta/snapshots')).json();
    check(list1.length === 0, '중지면 저장 0');
    await page.screenshot({ path: path.join(SHOTS, 'dbbrowser-stopped.png') });

    // 다시 찍기 → 완료
    await page.click('#snapTake');
    const done = await waitText(page, '#snapMsg', /^(완료|표 0개)|실패|closed/, 180000);
    check(done.startsWith('완료 — 스냅샷 #') && done.includes('테이블 1500') && done.includes('toolbox.mv.db') && done.endsWith('(H2)'),
      '완료 문구: ' + done);
    await waitText(page, '#snapScope', /범위/, 5000);
    const scope = await page.$eval('#snapScope', e => e.textContent);
    check(scope === '범위: 스키마 PUBLIC', '범위 줄: ' + scope);
    check((await page.$eval('#snap', e => e.options[e.selectedIndex].textContent)).endsWith(' · 거름'), '거른 스냅샷 라벨');
    await page.screenshot({ path: path.join(SHOTS, 'dbbrowser-done.png') });

    // 표 0개 안내 — zero 프로필
    await page.select('#profile', 'zero');
    await waitText(page, '#connMsg', /프로필을 바꿨다/, 5000);
    await page.waitForSelector('#conns .item');
    await page.click('#conns .item');
    await page.click('#snapTake');
    // 앞 「완료 — 스냅샷 #1」 이 남아 있다 — 이번 결과(#2 이거나 표 0개)를 기다린다
    const zero = await waitText(page, '#snapMsg', /^(표 0개|완료 — 스냅샷 #2)/, 30000);
    check(zero === '표 0개 — 프로필 scope.schemas 가 접속 계정의 스키마와 맞는지 본다', '표 0개 안내: ' + zero);
    await page.screenshot({ path: path.join(SHOTS, 'dbbrowser-zero.png') });

    check(errs.length === 0, '콘솔 오류 0' + (errs.length ? ' — ' + errs.join(' | ') : ''));
  } finally {
    if (browser) await browser.close();
    server.kill();
    await sleep(1000);
    fs.rmSync(tmp, { recursive: true, force: true, maxRetries: 5, retryDelay: 500 });
  }
  console.log(fail.length ? '실패 ' + fail.length : 'db_browser 스모크 통과');
  process.exit(fail.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
