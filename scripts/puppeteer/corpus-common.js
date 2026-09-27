// 실물 표본 스크립트 공통(V-2~) — 표본 훑기·읽기. 표본에 EUC-KR 파일이 섞여 있다(V-2 드러남).
const fs = require('fs');
const path = require('path');

// egov-prev 는 egov 의 이전 판(폴더 비교용) — 같은 파일을 두 번 재지 않는다
const SKIP = new Set(['egov-prev', '.manifest']);

function walk(dir, base, out) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, base, out);
    else out.push(path.relative(base, p).split(path.sep).join('/'));
  }
}

/** 표본의 파일 — [{src, rel}], 출처·상대 경로 정렬. keep(rel) 이 참인 것만 */
function corpusFiles(corpus, keep) {
  const files = [];
  for (const src of fs.readdirSync(corpus).sort()) {
    if (SKIP.has(src) || !fs.statSync(path.join(corpus, src)).isDirectory()) continue;
    const rels = [];
    walk(path.join(corpus, src), path.join(corpus, src), rels);
    rels.sort().forEach(r => { if (keep(r)) files.push({ src, rel: r }); });
  }
  return files;
}

/** UTF-8 엄격 → EUC-KR(자바 Csv.decode·LocalFiles.read 와 같은 규칙). BOM 은 뗀다 */
function decode(buf) {
  try { return { text: new TextDecoder('utf-8', { fatal: true }).decode(buf), enc: 'utf-8' }; }
  catch (e) { return { text: new TextDecoder('euc-kr').decode(buf), enc: 'euc-kr' }; }
}

module.exports = { SKIP, walk, corpusFiles, decode };
