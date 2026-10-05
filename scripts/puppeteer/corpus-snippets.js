// sql_snippets 실물 표본(V-8) — 떠 있는 서버의 백엔드본 sql_snippets 에서 순수본 SNIPPETS 전부를 방언별 파라미터 값으로 렌더한다.
// DbCorpusBase 가 앱을 띄우고 부른다(CorpusNode). 손으로: node scripts/puppeteer/corpus-snippets.js <서버 URL> <표본 폴더> <출력 폴더>
// 값은 컨테이너에 넣는 실물 표본의 이름 — maria·oracle 은 eGov COMTNEMPLYRINFO, pg 는 같은 표(따옴표 없는 식별자라 소문자),
// mssql·sybase 는 chinook Employee. 스키마·사용자는 Testcontainers 기본값. 출력: <출력>/summary.json {tabs: {tab: [{key,label,sql}]}}
const fs = require('fs');
const path = require('path');
const puppeteer = require('C:/workspace/node_modules/puppeteer');

const [BASE, , OUT] = process.argv.slice(2);

const V = {
  oracle: { table: 'COMTNEMPLYRINFO', col: 'EMPLYR_ID', db: 'TEST', user: 'TEST', seq: 'EMPLOYEES_SEQ', kw: 'EMPLYR' },
  mysql: { table: 'COMTNEMPLYRINFO', col: 'EMPLYR_ID', db: 'test', user: 'test', seq: 'corpus_seq', kw: 'EMPLYR' },
  pg: { table: 'comtnemplyrinfo', col: 'emplyr_id', db: 'test', user: 'test', seq: 'corpus_seq', kw: 'emplyr' },
  mssql: { table: 'Employee', col: 'EmployeeId', db: 'master', user: 'sa', seq: 'corpus_seq', kw: 'Employee' },
  sybase: { table: 'Employee', col: 'EmployeeId', db: 'master', user: 'sa', seq: 'corpus_seq', kw: 'Employee' },
};

/** 파라미터 id·라벨 → 값. 모르는 것은 빈칸(순수본이 빈칸을 기본값으로 다룬다) */
function value(tab, id, label) {
  const v = V[tab];
  if (/^kill_/.test(id)) return '999999';                        // 없는 세션 — 어차피 실행하지 않는다(세션 조작)
  if (/seq_reset_val|^rs_val$/.test(id)) return '1000';
  if (/seq/.test(id) || /시퀀스/.test(label)) return v.seq;
  if (/(^|_)sql$/.test(id)) return 'SELECT * FROM ' + v.table;  // 쿼리를 받는 스니펫(실행 계획 등)
  if (id === 'sf_text') return 'SELECT';
  if (id === 'grep_kw') return v.kw;
  if (/days$/.test(id)) return '30';
  if (id === 'ls_min') return '1';
  if (id === 'pb_child') return '0';
  if (id === 'pb_sqlid') return 'abcdefghijklm';
  if (/^priv_user$/.test(id)) return v.user;
  if (/(^db_|_db$|^db$)/.test(id) || /DB명/.test(label)) return v.db;
  if (/tbl|tbl_search|rs_name/.test(id) || /테이블/.test(label)) return v.table; // col_tbl 은 표 — 컬럼보다 먼저(첫 판 col_list 가 컬럼을 받았다)
  if (/col/.test(id) || /컬럼/.test(label)) return v.col;
  if (/빈칸/.test(label)) return '';
  return 'CORPUS_OBJ';                                            // 뷰·함수·객체 이름 — 없는 객체(판정은 B)
}

(async () => {
  const browser = await puppeteer.launch({ headless: 'new' });
  const page = await browser.newPage();
  const errs = [];
  page.on('pageerror', e => errs.push(e.message));
  await page.goto(BASE.replace(/\/$/, '') + '/tools/sql_snippets.html', { waitUntil: 'networkidle2' });
  const params = {};
  const list = await page.evaluate(() => {
    const out = [];
    Object.keys(SNIPPETS).forEach(tab => SNIPPETS[tab].forEach(g => g.items.forEach(it => {
      out.push({ tab, key: it.key, label: it.label, params: (it.params || []).map(p => ({ id: p.id, label: p.label || '' })) });
    })));
    return out;
  });
  const tabs = {};
  for (const it of list) {
    const p = {};
    it.params.forEach(x => { p[x.id] = value(it.tab, x.id, x.label); });
    const sql = await page.evaluate((tab, key, p) => {
      for (const g of SNIPPETS[tab]) for (const x of g.items) if (x.key === key) return x.query(p);
      return null;
    }, it.tab, it.key, p);
    (tabs[it.tab] = tabs[it.tab] || []).push({ key: it.key, label: it.label, sql });
    params[it.tab + ':' + it.key] = p;
  }
  await browser.close();
  fs.mkdirSync(OUT, { recursive: true });
  fs.writeFileSync(path.join(OUT, 'summary.json'), JSON.stringify({ pageErrors: errs, files: [], tabs, params }));
  console.log(Object.keys(tabs).map(t => t + ' ' + tabs[t].length).join(' · ') + ' · 페이지 오류 ' + errs.length);
})().catch(e => { console.error(e); process.exit(1); });
