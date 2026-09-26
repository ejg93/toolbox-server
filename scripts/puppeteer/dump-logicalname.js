// 순수본 논리명 변환기를 실제로 돌려 결과를 골든으로 뜬다(3-2·3-6). 자바 이식(core.logical)이 이 결과와 같아야 한다.
// 실행: node scripts/puppeteer/dump-logicalname.js   (Puppeteer 는 C:/workspace/node_modules — 집 검증 전용, 반입 안 함)
// 전제: 무시토큰 TB, 사용자 사전 비움, 토큰 우선순위 기본(기관표준단어), DB명 SAMPLE, 용어 검토 제외 끔(순수본 기본값)
// 경로에 한글이 있으면 Node 22 + puppeteer require 가 크래시한다 — 이 스크립트 경로는 영문. 데이터 파일 경로의 한글은 괜찮다.
// 입력은 저장소 안 사본 — 동봉 행안부 CSV, 테스트 리소스의 기관·컬럼 CSV.
const fs = require('fs');
const path = require('path');
const puppeteer = require('C:/workspace/node_modules/puppeteer');

const ROOT = path.join(__dirname, '..', '..');
const TOOL = 'file:///' + path.join(ROOT, 'pure', 'tools').replace(/\\/g, '/') + '/' + encodeURIComponent('논리명_변환기.html');
const IN = {
  dictFile: path.join(ROOT, 'src', 'main', 'resources', 'dict', 'moi-words-20251101.csv'),
  ovFile: path.join(ROOT, 'src', 'test', 'resources', 'sample', 'logical', 'org-words.csv'),
  colFile: path.join(ROOT, 'src', 'test', 'resources', 'sample', 'logical', 'columns-1000.csv'),
};
const OUT = path.join(ROOT, 'src', 'test', 'resources', 'golden', 'logical');
const sleep = ms => new Promise(r => setTimeout(r, ms));

(async () => {
  const browser = await puppeteer.launch({ headless: 'new' });
  const page = await browser.newPage();
  const errs = [];
  page.on('pageerror', e => errs.push(e.message));
  page.on('dialog', async d => { await d.accept(); });
  await page.goto(TOOL, { waitUntil: 'load' });
  // 브라우저에 남은 사용자 사전이 없게
  await page.evaluate(() => { try { localStorage.clear(); } catch (e) {} USER = {}; });

  const up = async (id, ms) => { const el = await page.$('#' + id); await el.uploadFile(IN[id]); await sleep(ms); };
  await up('dictFile', 1500);
  await up('ovFile', 800);
  await page.evaluate(() => { const s = document.getElementById('skipTok'); s.value = 'TB'; if (s.onchange) s.onchange(); });
  await up('colFile', 1500);
  await page.evaluate(() => runBtn());
  await sleep(3000);

  const result = await page.evaluate(() => {
    const pick = r => ({ owner: r.owner, table: r.table, col: r.col, name: r.name, src: r.src, missing: r.missing,
      dtype: r.dtype, dlen: r.dlen, dscale: r.dscale, pk: r.pk, nnull: r.nnull, ord: r.ord, isTable: !!r.isTable });
    const ALL = ROWS.concat(TROWS);
    const n = s => ALL.filter(r => r.src === s).length;
    return {
      rows: ROWS.map(pick),
      tableRows: TROWS.map(pick),
      rank: RANK.map(r => [r[0], r[1]]),
      usedTokens: USEDTOK,
      usedWords: Array.from(USEDWORD).sort(),
      stats: { columns: ROWS.length, tables: TROWS.length, exact: n('given') + n('user') + n('word'),
        multi: n('multi'), mix: n('mix'), none: n('none') },
    };
  });

  // 3-6 — 후보 CSV 넷. Blob 을 가로채 본문을 뜨고 내려받기는 막는다
  const csvs = await page.evaluate(() => {
    window.__cap = [];
    const OB = window.Blob;
    window.Blob = function (parts, opts) { window.__cap.push(parts.join('')); return new OB(parts, opts); };
    HTMLAnchorElement.prototype.click = function () {};
    URL.createObjectURL = function () { return 'blob:captured'; };
    URL.revokeObjectURL = function () {};
    window.confirm = function () { return true; };
    window.alert = function () {};
    document.getElementById('termDb').value = 'SAMPLE';
    const out = {};
    const take = (name, fn) => { const before = window.__cap.length; fn(); out[name] = window.__cap.length > before ? window.__cap[window.__cap.length - 1] : null; };
    take('terms', expTerms);
    take('words', expStdWords);
    take('domains', expDomains);
    take('worduse', expWordUse);
    return out;
  });
  await browser.close();

  if (errs.length) { console.error('페이지 오류:', errs); process.exit(1); }
  fs.mkdirSync(OUT, { recursive: true });
  fs.writeFileSync(path.join(OUT, 'sample-rows.json'), JSON.stringify({ rows: result.rows, tableRows: result.tableRows }, null, 2) + '\n');
  fs.writeFileSync(path.join(OUT, 'sample-rank.json'), JSON.stringify({ stats: result.stats, rank: result.rank,
    usedTokens: result.usedTokens, usedWords: result.usedWords }, null, 2) + '\n');
  for (const [k, v] of Object.entries(csvs)) {
    if (v == null) { console.error('CSV 를 못 떴다: ' + k); process.exit(1); }
    fs.writeFileSync(path.join(OUT, 'candidates-' + k + '.csv'), v);
  }
  console.log('stats', JSON.stringify(result.stats), 'rank top', JSON.stringify(result.rank.slice(0, 4)));
})();
