// 화면 시각 검증 하네스(1-48, 설계 18) — 도구 열둘 + 런처를 어둡게·밝게 × 1400·375 로 찍고, 보이는 버튼의 계산 스타일을 판정한다.
// 실행: node scripts/puppeteer/visual.js <label> [--only tool1,tool2] [--port 41783] [--keep]
//   (먼저 scripts/mvn.sh -q package -DskipTests 로 target/app.jar)
// 출력: out/visual/<label>/ — <tool>-<state>-<dark|light>-<1400|375>.png · styles.json · console.json · summary.txt
// 판정(styles.json): ① 배경에 묻힘(칠 = 둘레 바탕이고 테두리도 안 보임) ② 대비 부족(글/칠 < 3.0) ③ 체계 어긋남(.btn-p≠accent·.btn-green≠green·.btn-red 글≠red).
//   label 이 before 면 판정은 기록만 한다. 콘솔 오류(pageerror·console error·응답 400 이상)와 동작 확인(checks)은 어느 label 이든 실패다.
// 자급 픽스처 — 임시 폴더에 H2 메모리 DB(INIT=RUNSCRIPT)·프로필·project.root(fixtures/analyze·check 사본). 데모 Oracle 은 안 쓴다(전후 데이터가 흔들린다).
// Puppeteer 는 C:/workspace/node_modules — 집 검증 전용, 반입 안 함. 경로에 한글 금지(CLAUDE.md 검증). 대기는 networkidle2(ToolsFolderTest).
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawn } = require('child_process');
const puppeteer = require('C:/workspace/node_modules/puppeteer');

const ROOT = path.join(__dirname, '..', '..');
const JAVA = process.env.JAVA || 'C:/Program Files/Java/jdk-17.0.19/bin/java';
const args = process.argv.slice(2);
const LABEL = args[0];
if (!LABEL || LABEL.startsWith('--')) { console.error('쓰는 법: node scripts/puppeteer/visual.js <label> [--only a,b] [--port n] [--keep]'); process.exit(2); }
const opt = name => { const i = args.indexOf(name); return i > 0 ? args[i + 1] : null; };
const ONLY = opt('--only') ? opt('--only').split(',') : null;
const PORT = Number(opt('--port') || 41783);
const KEEP = args.includes('--keep');
const BASE = 'http://127.0.0.1:' + PORT;
const OUT = path.join(ROOT, 'out', 'visual', LABEL);
const JUDGE = LABEL !== 'before';
const THEMES = ['dark', 'light'];
const WIDTHS = [1400, 375];
const sleep = ms => new Promise(r => setTimeout(r, ms));
const slash = p => p.split(path.sep).join('/');

async function waitText(page, sel, re, ms) {
  await page.waitForFunction((s, src) => { const e = document.querySelector(s); return e && new RegExp(src).test(e.textContent); },
    { timeout: ms }, sel, re.source).catch(() => {});
  return page.$eval(sel, e => e.textContent).catch(() => '');
}
const click = (page, sel) => page.$eval(sel, e => e.click());

// 화면마다 상태 — 한 번 연 페이지에서 차례로 찍는다
const DEV_TABS = async page => page.$$eval('#main-tabs .tab[data-tab]', ts => ts.map(t => t.getAttribute('data-tab')));
const TOOLS = [
  { tool: 'index', states: [{ name: 'main' }] },
  { tool: 'special_chars', states: [{ name: 'main' }] },
  { tool: 'sql_snippets', states: [{ name: 'oracle' }, { name: 'mysql', prep: p => p.evaluate(() => switchDb('mysql')) }] },
  { tool: 'table_builder', states: [{ name: 'main' }] },
  { tool: 'jsp_formatter', states: [{ name: 'paste' }, { name: 'dir', prep: p => click(p, '#tab-dir') }] },
  { tool: 'dev_tools', states: 'dev' },
  { tool: 'db_browser', states: [{ name: 'snap' }, { name: 'ddl', prep: p => click(p, '#dtoTabDdl') }] },
  { tool: 'deliverable_sql', states: [{ name: 'main' }] },
  { tool: 'logical_name', states: [{ name: 'main' }, { name: 'run', prep: async p => {
    await click(p, '#run'); await waitText(p, '#runMsg', /^(컬럼|산출물 범위)|실패|없다/, 15000);
  } }] },
  { tool: 'spring_source_generator', states: [{ name: 'main' }] },
  { tool: 'code_check', states: [{ name: 'run', prep: async p => {
    await p.waitForFunction(() => document.getElementById('dir').value, { timeout: 5000 }).catch(() => {});
    await click(p, '#runDir'); await waitText(p, '#msg', /^파일 \d+|중지|FAILED|ERROR|실패/, 30000);
  } }, { name: 'deploy', prep: p => click(p, '#tabDeploy') }] },
  { tool: 'program_analysis', states: [{ name: 'run', prep: async p => {
    await p.waitForFunction(() => document.getElementById('dir').value, { timeout: 5000 }).catch(() => {});
    await click(p, '#run'); await waitText(p, '#msg', /프로그램 \d+|FAILED|ERROR|실패/, 30000);
  } }, { name: 'crud', prep: p => click(p, '#tabCrud') }] },
];

// 픽스처 — 표 다섯 + 뷰 하나, 코멘트(마스킹 탐지·논리명 변환이 보이게)
const DDL = [
  'CREATE TABLE IF NOT EXISTS TB_CUST_MST(CUST_ID VARCHAR(20) PRIMARY KEY, CUST_NM VARCHAR(50), MBTLNUM VARCHAR(20), EMAIL VARCHAR(100), REG_DT DATE);',
  "COMMENT ON TABLE TB_CUST_MST IS '고객 마스터';",
  "COMMENT ON COLUMN TB_CUST_MST.CUST_NM IS '고객명';",
  "COMMENT ON COLUMN TB_CUST_MST.MBTLNUM IS '휴대폰번호';",
  "COMMENT ON COLUMN TB_CUST_MST.EMAIL IS '이메일';",
  'CREATE TABLE IF NOT EXISTS TB_ORDER_MST(ORDER_NO VARCHAR(20) PRIMARY KEY, CUST_ID VARCHAR(20), ORDER_AMT DECIMAL(12,2), ORDER_DT DATE);',
  'CREATE TABLE IF NOT EXISTS TB_CODE(GROUP_CD VARCHAR(10), CODE VARCHAR(10), CODE_NM VARCHAR(50), USE_YN CHAR(1), PRIMARY KEY (GROUP_CD, CODE));',
  'CREATE TABLE IF NOT EXISTS TB_USE_HIST(USE_YN CHAR(1), QWZX_CD VARCHAR(5));',
  'CREATE TABLE IF NOT EXISTS ZZ_SKIP(ZZQX_CD VARCHAR(5));',
  'CREATE VIEW IF NOT EXISTS V_CUST AS SELECT CUST_ID, CUST_NM FROM TB_CUST_MST;',
];

function copyDir(from, to) {
  if (!fs.existsSync(from)) return;
  fs.mkdirSync(to, { recursive: true });
  for (const e of fs.readdirSync(from, { withFileTypes: true })) {
    const a = path.join(from, e.name), b = path.join(to, e.name);
    if (e.isDirectory()) copyDir(a, b); else fs.copyFileSync(a, b);
  }
}

// 브라우저 안 — 보이는 버튼의 계산 스타일 + 토큰
function collectButtons() {
  const rgb = v => { const d = document.createElement('div'); d.style.color = v; document.body.appendChild(d); const c = getComputedStyle(d).color; d.remove(); return c; };
  const tok = n => { const v = getComputedStyle(document.documentElement).getPropertyValue(n).trim(); return v ? rgb(v) : null; };
  const opaque = c => c && c !== 'transparent' && !/rgba\(.*,\s*0\)$/.test(c);
  const bgOf = el => {
    for (let e = el.parentElement; e; e = e.parentElement) { const c = getComputedStyle(e).backgroundColor; if (opaque(c)) return c; }
    return getComputedStyle(document.documentElement).backgroundColor;
  };
  const out = [];
  for (const b of document.querySelectorAll('button')) {
    if (b.offsetParent === null) continue;
    const s = getComputedStyle(b);
    out.push({ id: b.id || null, cls: b.className || '', text: (b.textContent || '').trim().slice(0, 20), bg: s.backgroundColor, color: s.color,
      border: s.borderTopWidth + ' ' + s.borderTopColor, afterBg: getComputedStyle(b, '::after').backgroundColor, disabled: b.disabled,
      ancestorBg: bgOf(b) });
  }
  return { buttons: out, tokens: { accent: tok('--accent'), green: tok('--green'), red: tok('--red'), btn: tok('--btn') } };
}

// 판정 — 노드 쪽
const parse = c => { const m = /rgba?\(([^)]+)\)/.exec(c || ''); if (!m) return null; const v = m[1].split(',').map(x => parseFloat(x)); return { r: v[0], g: v[1], b: v[2], a: v.length > 3 ? v[3] : 1 }; };
const lum = c => { const f = x => { x /= 255; return x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4); }; return 0.2126 * f(c.r) + 0.7152 * f(c.g) + 0.0722 * f(c.b); };
const ratio = (a, b) => { const x = lum(a), y = lum(b); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); };
const same = (a, b) => a && b && Math.abs(a.r - b.r) < 3 && Math.abs(a.g - b.g) < 3 && Math.abs(a.b - b.b) < 3;
// 겹쳐 칠: 투명(a<1) 칠은 둘레 바탕 위에 섞는다
const over = (top, under) => !top ? under : top.a >= 1 ? top : { r: top.r * top.a + under.r * (1 - top.a), g: top.g * top.a + under.g * (1 - top.a), b: top.b * top.a + under.b * (1 - top.a), a: 1 };
// 순수본 그대로라 바꿀 수 없는 것 · 칠 없이 글자로만 보이는 것(설계 18 D5 — 로컬 유지 클래스)
const SKIP_TOOLS = new Set(['sql_snippets']);
const SKIP_CLS = /\b(snip-btn|fav-btn|clear-btn|db-tab|sql-db-btn|radio-btn)\b/;

function judge(tool, data) {
  const out = [];
  if (SKIP_TOOLS.has(tool)) return out;
  const t = data.tokens;
  for (const b of data.buttons) {
    if (b.disabled || SKIP_CLS.test(b.cls)) continue;
    const anc = parse(b.ancestorBg) || { r: 0, g: 0, b: 0, a: 1 };
    const isDl = /\bdl\b/.test(b.cls);
    const fill = over(parse(isDl ? b.afterBg : b.bg), anc);
    const bw = parseFloat(b.border) || 0;
    const bc = over(parse(b.border.split(' ').slice(1).join(' ')), anc);
    const who = (b.id ? '#' + b.id : '') + ' 「' + b.text + '」';
    if (same(fill, anc) && !isDl && (bw === 0 || ratio(bc, anc) < 1.3)) out.push('배경에 묻힘 ' + who);
    const fg = over(parse(b.color), fill);
    if (b.text && ratio(fg, fill) < 3.0) out.push('대비 부족 ' + ratio(fg, fill).toFixed(2) + ' ' + who);
    if (/\bbtn-p\b/.test(b.cls) && t.accent && !same(fill, parse(t.accent))) out.push('체계 어긋남(btn-p≠accent) ' + who);
    if (/\bbtn-green\b/.test(b.cls) && t.green && !same(fill, parse(t.green))) out.push('체계 어긋남(btn-green≠green) ' + who);
    if (/\bbtn-red\b/.test(b.cls) && t.red && !same(parse(b.color), parse(t.red))) out.push('체계 어긋남(btn-red 글≠red) ' + who);
  }
  return out;
}

(async () => {
  fs.rmSync(OUT, { recursive: true, force: true });
  fs.mkdirSync(OUT, { recursive: true });
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'visual-'));
  const profiles = path.join(tmp, 'profiles');
  fs.mkdirSync(profiles);
  const proj = path.join(tmp, 'proj');
  copyDir(path.join(ROOT, 'src', 'test', 'resources', 'fixtures', 'analyze', 'java'), path.join(proj, 'src', 'main', 'java'));
  copyDir(path.join(ROOT, 'src', 'test', 'resources', 'fixtures', 'analyze', 'mapper'), path.join(proj, 'src', 'main', 'resources', 'mapper'));
  copyDir(path.join(ROOT, 'src', 'test', 'resources', 'fixtures', 'check', 'jsp'), path.join(proj, 'src', 'main', 'webapp', 'jsp'));
  const script = path.join(tmp, 'visual.sql');
  fs.writeFileSync(script, DDL.join('\n') + '\n');
  const url = "jdbc:h2:mem:visual;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM '" + slash(script) + "'";
  fs.writeFileSync(path.join(profiles, 'visual.yaml'), [
    'name: visual',
    'project:', '  root: ' + slash(proj), '  encoding: UTF-8', '  lineEnding: LF',
    'connections:', '  - id: h2', '    dialect: h2', '    url: "' + url + '"', '    user: sa',
    'defaultConnection: h2',
    'scope:', '  schemas: [PUBLIC]',
    'deliverable:', '  filter:', '    exclude: { prefixes: [ZZ_] }',
    'framework: egov35',
    'output:', '  dir: ' + slash(path.join(tmp, 'out')),
  ].join('\n') + '\n');

  const server = spawn(JAVA, ['-jar', path.join(ROOT, 'target', 'app.jar'), 'serve', '--no-browser', '--port', String(PORT),
    '--profile', 'visual', '--profiles-dir', profiles, '--data-dir', path.join(tmp, 'data')], { cwd: tmp, stdio: 'ignore', detached: KEEP });
  if (KEEP) server.unref();   // --keep — node 가 끝나도 서버가 남게(윈도는 detached 가 아니면 같이 꺼진다)
  let browser;
  const styles = {}, consoleLog = {}, checks = [], judged = [];
  let shots = 0;
  const check = (ok, what, skip) => { checks.push({ what, result: skip ? 'skip' : ok ? 'pass' : 'fail' }); console.log((skip ? '  [건너뜀] ' : ok ? '  [통과] ' : '  [실패] ') + what); };
  try {
    let up = false;
    for (let i = 0; i < 60 && !up; i++) {
      try { up = (await fetch(BASE + '/api/ping')).ok; } catch (e) { /* 아직 */ }
      if (!up) await sleep(500);
    }
    if (!up) throw new Error('서버가 안 떴다 — target/app.jar 를 먼저 만든다');
    await fetch(BASE + '/api/meta/snapshot', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ connId: 'h2' }) });
    let snaps = [];
    for (let i = 0; i < 60 && !snaps.length; i++) { snaps = await (await fetch(BASE + '/api/meta/snapshots')).json(); if (!snaps.length) await sleep(500); }
    if (!snaps.length) throw new Error('스냅샷이 안 만들어졌다');
    const themed = (await fetch(BASE + '/tools/common.css')).ok;   // 0-51 뒤부터 테마 확인을 건다

    browser = await puppeteer.launch({ headless: 'new' });
    const page = await browser.newPage();
    let errs = [];
    page.on('pageerror', e => errs.push('pageerror: ' + e.message));
    page.on('console', m => { if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) errs.push('console: ' + m.text()); });
    page.on('response', r => { if (r.status() >= 400 && !r.url().endsWith('/favicon.ico')) errs.push(r.status() + ' ' + r.url()); });
    const open = async tool => {
      await page.goto(BASE + '/tools/' + tool + '.html', { waitUntil: 'networkidle2' });
      await page.waitForFunction(() => /^백엔드 연결/.test((document.getElementById('tb-mode-badge') || {}).textContent || ''), { timeout: 5000 }).catch(() => {});
    };
    await open('index');
    await page.evaluate(() => { try { localStorage.removeItem('tb-theme'); } catch (e) { /* 막힘 */ } });

    for (const theme of THEMES) {
      await page.emulateMediaFeatures([{ name: 'prefers-color-scheme', value: theme }]);
      for (const width of WIDTHS) {
        await page.setViewport({ width, height: 900 });
        for (const t of TOOLS) {
          if (ONLY && !ONLY.includes(t.tool)) continue;
          errs = [];
          await open(t.tool);
          let states = t.states;
          if (states === 'dev') {
            states = (await DEV_TABS(page)).map(n => ({ name: n, prep: p => p.$eval('#main-tabs .tab[data-tab="' + n + '"]', e => e.click()) }));
            const i = states.findIndex(s => s.name === 'dummy');
            if (i >= 0) states.splice(i + 1, 0, { name: 'dummy-snap', prep: p => click(p, '#dm-tab-snap') });
          }
          for (const s of states) {
            if (s.prep) { try { await s.prep(page); } catch (e) { errs.push('prep ' + s.name + ': ' + e.message); } }
            await sleep(300);
            const name = t.tool + '-' + s.name + '-' + theme + '-' + width;
            await page.screenshot({ path: path.join(OUT, name + '.png'), fullPage: true });
            shots++;
            const data = await page.evaluate(collectButtons);
            const found = judge(t.tool, data);
            styles[name] = { tokens: data.tokens, buttons: data.buttons, findings: found };
            for (const f of found) judged.push(name + ': ' + f);
          }
          if (errs.length) consoleLog[t.tool + '-' + theme + '-' + width] = errs.slice();
        }
      }
    }

    // 동작 확인 — 실브라우저에서만 잴 수 있는 것(HtmlUnit 에 없음)
    await page.emulateMediaFeatures([{ name: 'prefers-color-scheme', value: 'dark' }]);
    await page.setViewport({ width: 1400, height: 900 });
    if (!ONLY || ONLY.includes('logical_name')) {
      await open('logical_name');
      await click(page, '#maskFind');
      await page.waitForFunction(() => document.querySelectorAll('#maskTbl tbody tr').length >= 2, { timeout: 10000 }).catch(() => {});
      const ind = await page.evaluate(() => {
        const all = document.getElementById('maskAll');
        if (!all) return 'maskAll 없음';
        all.click(); all.click();
        const rows = Array.from(document.querySelectorAll('#maskTbl tbody input[type=checkbox]')).filter(b => !b.disabled);
        if (!rows.length || !rows.every(b => b.checked)) return '전체 켬 실패';
        rows[0].click();
        return all.indeterminate === true ? 'ok' : 'indeterminate=' + all.indeterminate;
      });
      check(ind === 'ok', '① 마스킹 머리 체크 — 하나 풀면 중간 상태: ' + ind);
      const cards = await page.evaluate(() => {
        const $ = id => document.getElementById(id);
        $('srcCsv').click();
        const a = $('optCsv').classList.contains('on') && !$('optSnap').classList.contains('on') && $('snap').disabled && $('dialectRow').parentNode.id === 'optCsv';
        $('srcSnap').click();
        const b = $('optSnap').classList.contains('on') && !$('optCsv').classList.contains('on') && !$('snap').disabled && $('snapDb').textContent.length > 0;
        return a && b ? 'ok' : 'csv=' + a + ' snap=' + b;
      });
      check(cards === 'ok', '② 컬럼 목록 카드 둘 중 하나: ' + cards);
    }
    if (!ONLY || ONLY.includes('index')) {
      await open('index');
      const has = await page.$('#tb-theme');
      if (!themed || !has) check(true, '③ 테마 토글(#tb-theme) — 0-51 전', true);
      else {
        const seq = [];
        await click(page, '#tb-theme'); seq.push(await page.evaluate(() => document.documentElement.getAttribute('data-theme')));
        await click(page, '#tb-theme'); seq.push(await page.evaluate(() => document.documentElement.getAttribute('data-theme')));
        await page.reload({ waitUntil: 'networkidle2' }); seq.push(await page.evaluate(() => document.documentElement.getAttribute('data-theme')));
        await page.waitForSelector('#tb-theme', { timeout: 5000 }).catch(() => {});
        await click(page, '#tb-theme'); seq.push(await page.evaluate(() => document.documentElement.getAttribute('data-theme')));
        seq.push(await page.evaluate(() => localStorage.getItem('tb-theme')));
        check(JSON.stringify(seq) === JSON.stringify(['dark', 'light', 'light', null, null]), '③ 테마 토글 자동→어둡게→밝게(새로고침 뒤 유지)→자동: ' + JSON.stringify(seq));
      }
    }
    if (!ONLY || ONLY.includes('sql_snippets')) {
      if (!themed) check(true, '④ sql_snippets 공용 CSS 끼우기 — 0-51 전', true);
      else {
        await open('sql_snippets');
        const ord = await page.evaluate(() => {
          const l = document.querySelector('link[href="/tools/common.css"]');
          const st = document.head.querySelector('style');
          if (!l) return 'link 없음';
          return st && (l.compareDocumentPosition(st) & Node.DOCUMENT_POSITION_FOLLOWING) ? 'ok' : 'link 가 첫 <style> 뒤';
        });
        check(ord === 'ok', '④ sql_snippets — common.css link 가 첫 <style> 앞: ' + ord);
      }
    }
  } finally {
    if (browser) await browser.close();
    if (KEEP) {
      console.log('서버를 남김 — ' + BASE + ' (PID ' + server.pid + ', 끝내려면 taskkill /PID ' + server.pid + ' /F) · 임시 폴더 ' + tmp);
    } else {
      server.kill();
      await sleep(1000);
      fs.rmSync(tmp, { recursive: true, force: true, maxRetries: 5, retryDelay: 500 });
    }
  }
  fs.writeFileSync(path.join(OUT, 'styles.json'), JSON.stringify(styles, null, 1));
  fs.writeFileSync(path.join(OUT, 'console.json'), JSON.stringify(consoleLog, null, 1));
  const errCount = Object.values(consoleLog).reduce((n, a) => n + a.length, 0);
  const failed = checks.filter(c => c.result === 'fail');
  const summary = [
    'label ' + LABEL + ' · 샷 ' + shots + ' · 판정 ' + judged.length + (JUDGE ? '' : '(기록만)') + ' · 콘솔 오류 ' + errCount
      + ' · 확인 통과 ' + checks.filter(c => c.result === 'pass').length + ' 실패 ' + failed.length + ' 건너뜀 ' + checks.filter(c => c.result === 'skip').length,
    ...judged.slice(0, 200).map(j => '판정 ' + j),
    ...Object.entries(consoleLog).map(([k, v]) => '오류 ' + k + ': ' + v.join(' | ')),
    ...failed.map(c => '확인 실패 ' + c.what),
  ].join('\n');
  fs.writeFileSync(path.join(OUT, 'summary.txt'), summary + '\n');
  console.log(summary.split('\n').slice(0, 40).join('\n'));
  const bad = (JUDGE && judged.length) || errCount || failed.length;
  process.exit(bad ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
