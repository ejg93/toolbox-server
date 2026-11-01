// dev_tools·table_builder JS 기능 실물 표본(V-7, V-5 카멜) — 떠 있는 서버의 백엔드본에서 돌린다.
// JsCorpusTest 가 앱을 띄우고 부른다. 손으로: node scripts/puppeteer/corpus-js.js <서버 URL> <표본 폴더> <출력 폴더>
// 입력 둘은 JsCorpusTest 가 <출력>/in-columns.txt(실물 컬럼명)·in-korean.txt(실물 한국어 줄)로 먼저 쓴다.
// 출력: <출력>/summary.json — 판정 재료만(원문 없음). 순수본 JS 를 자바로 옮기지 않고 브라우저에서 잰다.
const fs = require('fs');
const path = require('path');
const puppeteer = require('C:/workspace/node_modules/puppeteer');
const { corpusFiles, decode } = require('./corpus-common.js');

const [BASE, CORPUS, OUT] = process.argv.slice(2);
const read = f => decode(fs.readFileSync(path.join(CORPUS, f.src, f.rel))).text;

(async () => {
  const browser = await puppeteer.launch({ headless: 'new' });
  const errs = [];
  const open = async tool => {
    const p = await browser.newPage();
    p.on('pageerror', e => errs.push(tool + ': ' + e.message));
    await p.goto(BASE.replace(/\/$/, '') + '/tools/' + tool, { waitUntil: 'networkidle2' });
    return p;
  };
  const out = { pageErrors: errs, files: [] };
  const dev = await open('dev_tools.html');

  // JSON — JSONTestSuite y_(유효)·n_(무효). 포맷·압축 결과를 다시 JSON.parse 해 원본 파싱과 같은지, 16자리 넘는 정수 원문 보존
  for (const f of corpusFiles(CORPUS, r => r.endsWith('.json'))) {
    const t = read(f);
    const r = await dev.evaluate(t => {
      const o = { big: [] };
      let want;
      try { want = JSON.stringify(JSON.parse(t)); o.valid = true; } catch (e) { o.valid = false; }
      try {
        const node = jsonParseRaw(t.replace(/^\uFEFF/, ''));
        const pretty = jsonStringifyRaw(node, '  ', false), mini = jsonStringifyRaw(node, null, false);
        o.accepted = true;
        if (o.valid) o.same = JSON.stringify(JSON.parse(pretty)) === want && JSON.stringify(JSON.parse(mini)) === want;
        const bigs = t.match(/-?\d{16,}/g) || [];
        o.bigKept = bigs.every(b => mini.includes(b));
      } catch (e) { o.accepted = false; }
      return o;
    }, t);
    out.files.push(Object.assign({ kind: 'json', file: f.src + '/' + f.rel }, r));
  }

  // XML — 포맷 결과 DOM = 원본 DOM(공백만인 텍스트 노드 빼고). DTD 외부 로드는 브라우저 DOMParser 가 안 한다
  const xmls = corpusFiles(CORPUS, r => r.endsWith('.xml'));
  for (const f of xmls) {
    const t = read(f);
    const r = await dev.evaluate(t => {
      const canon = doc => {
        const walk = n => {
          if (n.nodeType === 3 || n.nodeType === 4) return n.nodeValue.trim() ? '"' + n.nodeValue.trim() + '"' : '';
          if (n.nodeType === 8) return '<!--' + n.nodeValue.trim() + '-->';
          if (n.nodeType !== 1) return '';
          const at = Array.from(n.attributes).map(a => a.name + '=' + a.value).sort().join(' ');
          return '<' + n.nodeName + ' ' + at + '>' + Array.from(n.childNodes).map(walk).join('') + '</>';
        };
        return walk(doc.documentElement);
      };
      const p = new DOMParser();
      const a = p.parseFromString(t, 'application/xml');
      if (a.getElementsByTagName('parsererror').length) return { valid: false };
      try {
        const pretty = xmlPretty(a.documentElement, 0, '  ', false);
        const b = p.parseFromString(pretty, 'application/xml');
        if (b.getElementsByTagName('parsererror').length) return { valid: true, same: false, why: 'reparse' };
        return { valid: true, same: canon(a) === canon(b) };
      } catch (e) { return { valid: true, same: false, why: String(e.message).slice(0, 80) }; }
    }, t);
    out.files.push(Object.assign({ kind: 'xml', file: f.src + '/' + f.rel }, r));
  }

  // 바이트 계산 — 실물 한국어 줄. JS u8len·cp949len 값을 싣고 자바가 getBytes 와 대조한다
  // 자바 Files.write 는 Windows 에서 CRLF — \r 이 줄에 붙으면 바이트가 1씩 는다(첫 판)
  const kor = fs.readFileSync(path.join(OUT, 'in-korean.txt'), 'utf8').split(/\r?\n/).filter(Boolean);
  const bytes = await dev.evaluate(ls => ls.map(s => [u8len(s), cp949len(s)]), kor);
  out.bytes = bytes;

  // 인코딩 판별·Hex(4-29, 설계 51) — CSV 마다 앞 4,096바이트와 첫 줄(첫 LF 앞) 원바이트를 순수 함수에. 원문 대신 판정 재료만 싣는다
  // (hex64 는 앞 64바이트 16진 — JsCorpusTest 가 HexFormat 과 대조, 문서엔 건수만)
  out.enc = [];
  for (const f of corpusFiles(CORPUS, r => r.endsWith('.csv')).filter(x => x.src === 'csv')) {
    const buf = fs.readFileSync(path.join(CORPUS, f.src, f.rel));
    const head = buf.subarray(0, 4096), lf = head.indexOf(0x0A);
    const line = lf < 0 ? head : head.subarray(0, lf);
    const r = await dev.evaluate((b, l) => {
      const bytes = Uint8Array.from(b), lb = Uint8Array.from(l), v = encVerdict(bytes), rt = hexToBytes(hexOf(bytes));
      let same = rt.length === bytes.length;
      for (let i = 0; same && i < rt.length; i++) same = rt[i] === bytes[i];
      return { enc: v.enc, hex64: hexOf(bytes, 64), dumpLines: hexDump(bytes).split('\n').length, roundTrip: same,
        badUtf8: (utf8Invalid(bytes, 1)[0] ?? -1), lineEucKr: new TextDecoder('euc-kr').decode(lb), lineUtf8: new TextDecoder('utf-8').decode(lb) };
    }, Array.from(head), Array.from(line));
    out.enc.push(Object.assign({ path: f.src + '/' + f.rel }, r));
  }

  // 카멜 — 실물 컬럼명 snake → camel → snake(순수본 toCamel·camelToSnake)
  const cols = fs.readFileSync(path.join(OUT, 'in-columns.txt'), 'utf8').split(/\r?\n/).filter(Boolean);
  out.camel = await dev.evaluate(cs => cs.map(c => camelToSnake(toCamel(c))), cols);

  // table_builder — JSP 안 순수 HTML 표(스크립틀릿·EL·태그립 없는 것) 불러오기 → 내보내기 → 불러오기 모델 같음
  const tb = await open('table_builder.html');
  let tables = 0;
  for (const f of corpusFiles(CORPUS, r => r.endsWith('.jsp'))) {
    if (tables >= 300) break;
    const t = read(f);
    const m = t.match(/<table\b[\s\S]*?<\/table>/i);
    if (!m || /<%|\$\{|<\w+:\w+/.test(m[0])) continue;
    tables++;
    const r = await tb.evaluate(src => {
      const model = () => JSON.stringify({ rows: G.rows, cols: G.cols, grid: G.grid, theadRows: G.theadRows, caption: G.caption });
      document.getElementById('m-src').value = src;
      if (!importHtml(src)) return { ok: false, why: 'import' };
      const first = model();
      refreshOutput();
      const html = document.getElementById('out').value;
      if (!importHtml(html)) return { ok: false, why: 'reimport' };
      return { ok: true, same: model() === first };
    }, m[0]);
    out.files.push(Object.assign({ kind: 'table', file: f.src + '/' + f.rel }, r));
  }

  await browser.close();
  fs.writeFileSync(path.join(OUT, 'summary.json'), JSON.stringify(out));
  const n = k => out.files.filter(x => x.kind === k).length;
  console.log('json ' + n('json') + ' · xml ' + n('xml') + ' · table ' + n('table') + ' · 바이트 ' + kor.length + ' · 카멜 ' + cols.length + ' · CSV 판정 ' + out.enc.length
    + ' · 페이지 오류 ' + errs.length);
})().catch(e => { console.error(e); process.exit(1); });
