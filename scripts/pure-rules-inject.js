// 순수본 코드 검사(portfolio frontend/public/toolbox/tools/code_check.html)의 `var RULES = [ … ];` 블록을
// toolbox-server golden/check/pure-rules.json 으로 바꾼다(5-16 — 5-13 때 임시 스크립트로 한 일을 저장소로).
// 규칙은 손으로 고치지 않는다(docs/pure-code-check.md 2장). 블록 밖은 건드리지 않고, 파일의 줄바꿈(CRLF·LF)을 지킨다.
//
//   node scripts/pure-rules-inject.js [portfolio 경로]      기본 C:/workspace/portfolio
//
// 다음 순서로 쓴다: 골든 갱신 → 이것 → portfolio 가지 `toolbox/rules-2` 에 커밋 → bash scripts/sync-pure.sh
const fs = require("fs");
const path = require("path");

const portfolio = process.argv[2] || "C:/workspace/portfolio";
const golden = path.join(__dirname, "..", "src", "test", "resources", "golden", "check", "pure-rules.json");
const target = path.join(portfolio, "frontend", "public", "toolbox", "tools", "code_check.html");

const rules = JSON.parse(fs.readFileSync(golden, "utf8"));
const text = fs.readFileSync(target, "utf8");
const nl = text.includes("\r\n") ? "\r\n" : "\n";
const lines = text.split(nl);
const a = lines.findIndex((l) => l === "var RULES = [");
const b = lines.findIndex((l, i) => i > a && l === "];");
if (a < 0 || b < 0) {
  console.error("var RULES 블록을 못 찾았다: " + target);
  process.exit(1);
}
const block = rules.map((r, i) => "\t" + JSON.stringify(r) + (i < rules.length - 1 ? "," : ""));
fs.writeFileSync(target, lines.slice(0, a + 1).concat(block, lines.slice(b)).join(nl));

// 다시 읽어 골든과 같은지, 브라우저 RegExp 로 전부 컴파일되는지
const again = fs.readFileSync(target, "utf8");
const s = again.indexOf("var RULES = [") + "var RULES = ".length;
const parsed = JSON.parse(again.slice(s, again.indexOf("];", s) + 1));
if (JSON.stringify(parsed) !== JSON.stringify(rules)) {
  console.error("다시 읽은 RULES 가 골든과 다르다");
  process.exit(1);
}
for (const r of parsed) new RegExp(r.regex, r.flags.join(""));
console.log("rules " + parsed.length + " — " + target);
