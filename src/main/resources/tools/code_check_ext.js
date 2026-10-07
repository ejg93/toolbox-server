/*
 * 코드 검사 화면(5-5). ES5 — HtmlUnit 스모크가 읽는다(var·function, 화살표·let 없음).
 * 서버·파일에서 온 값은 textContent 로만 넣는다(innerHTML 은 비우기만).
 * 규칙 체크 상태는 PUT /api/profiles/{활성}/codecheck 로 프로필에 — 프로필이 원본.
 */
(function () {
  'use strict';

  var GROUPS = [
    ['common', 'ㄱ 공통 잔재'], ['java', 'ㄴ Java 구조'], ['mybatis', 'ㄷ MyBatis XML'], ['jsp', 'ㄹ JSP'],
    ['tsx', 'ㅁ TSX·JS'], ['file', 'ㅂ 파일'], ['security', 'ㅇ 보안약점'], ['a11y', 'ㅈ 웹 접근성·표준']
  ];
  var rules = [];      // GET /api/check/rules
  var fileRule = {};   // 5-23 — 파일 단위 규칙 id. 줄 칸에 「파일」
  var findings = [];   // 마지막 실행 결과(발췌 포함, 메모리만)
  var shown = [];      // 거른 뒤
  var runId = null;
  var root = null;     // 폴더 검사 경로 — 미리보기가 다시 읽는다
  var pastedText = null;

  function $(id) { return document.getElementById(id); }

  function msg(id, text, cls) {
    var el = $(id);
    el.textContent = text;
    el.className = cls || '';
  }

  function groupLabel(g) {
    for (var i = 0; i < GROUPS.length; i++) if (GROUPS[i][0] === g) return GROUPS[i][1];
    return g;
  }

  // ------------------------------------------------------------ 규칙 패널

  function loadRules() {
    return TB.api('/api/check/rules').then(function (list) {
      rules = list;
      fileRule = {};
      list.forEach(function (r) { if (r.fileLevel) fileRule[r.id] = true; });
      renderRules();
      var sel = $('fGroup');
      GROUPS.forEach(function (g) {
        var op = document.createElement('option');
        op.value = g[0];
        op.textContent = g[1];
        sel.appendChild(op);
      });
    }, function (e) { msg('ruleMsg', '규칙을 못 읽었다: ' + e.message, 'err'); });
  }

  function renderRules() {
    var box = $('rules');
    box.innerHTML = '';
    GROUPS.forEach(function (g) {
      var mine = rules.filter(function (r) { return r.group === g[0]; });
      if (!mine.length) return;
      var wrap = document.createElement('div');
      wrap.className = 'grp';
      var head = document.createElement('div');
      head.className = 'ghead';
      var cb = document.createElement('input');
      cb.type = 'checkbox';
      cb.id = 'g_' + g[0];
      cb.checked = mine[0].groupEnabled !== false;
      cb.onclick = function (ev) { ev.stopPropagation(); };
      var name = document.createElement('span');
      name.textContent = g[1];
      var n = document.createElement('span');
      n.className = 'n';
      n.textContent = '규칙 ' + mine.length;
      head.appendChild(cb);
      head.appendChild(name);
      head.appendChild(n);
      head.onclick = function () { wrap.className = wrap.className === 'grp' ? 'grp open' : 'grp'; };
      wrap.appendChild(head);
      var list = document.createElement('div');
      list.className = 'rlist';
      mine.forEach(function (r) {
        var row = document.createElement('div');
        row.className = 'rule';
        var rc = document.createElement('input');
        rc.type = 'checkbox';
        rc.id = 'r_' + r.id;
        rc.checked = r.ruleEnabled !== false;
        var id = document.createElement('span');
        id.className = 'id';
        id.textContent = r.id;
        var sev = document.createElement('span');
        sev.className = 'sev';
        sev.textContent = r.severity;
        row.appendChild(rc);
        row.appendChild(id);
        row.appendChild(sev);
        if (r.kind === 'regex' && r.regex) {
          var rx = document.createElement('input');
          rx.type = 'text';
          rx.id = 'x_' + r.id;
          rx.value = r.regex;
          rx.spellcheck = false;
          row.appendChild(rx);
        } else {
          var m = document.createElement('span');
          m.className = 'msg';
          m.textContent = r.message || '';
          row.appendChild(m);
        }
        row.title = r.message || '';
        list.appendChild(row);
      });
      wrap.appendChild(list);
      box.appendChild(wrap);
    });
  }

  /* 화면 상태 → {groups, rules}. 규칙은 처음 받은 상태와 다른 것만 */
  function choice() {
    var groups = {};
    var over = {};
    GROUPS.forEach(function (g) {
      var cb = $('g_' + g[0]);
      if (cb) groups[g[0]] = cb.checked;
    });
    rules.forEach(function (r) {
      var rc = $('r_' + r.id);
      if (!rc) return;
      var rx = $('x_' + r.id);
      var changedRx = rx && rx.value !== r.regex;
      var wasOn = r.ruleEnabled !== false;
      if (changedRx) {
        over[r.id] = { enabled: rc.checked, regex: rx.value };
      } else if (rc.checked !== wasOn) {
        over[r.id] = rc.checked;
      }
    });
    return { groups: groups, rules: over };
  }

  function saveRules() {
    var c = choice();
    TB.api('/api/profiles').then(function (p) {
      if (!p.active) { msg('ruleMsg', '활성 프로필이 없다', 'err'); return; }
      return TB.api('/api/profiles/' + encodeURIComponent(p.active) + '/codecheck', { method: 'PUT', body: c }).then(function (r) {
        msg('ruleMsg', TB.savedText([r.path]), 'ok');
        return loadRulesKeepOpen();
      });
    }).then(null, function (e) { msg('ruleMsg', '저장 실패: ' + e.message, 'err'); });
  }

  function loadRulesKeepOpen() {
    return TB.api('/api/check/rules').then(function (list) { rules = list; renderRules(); });
  }

  // ------------------------------------------------------------ 실행

  function start(body, label) {
    var c = choice();
    body.groups = c.groups;
    body.ruleOverrides = c.rules;
    $('runDir').disabled = true;
    $('runText').disabled = true;
    msg('msg', label + ' 시작');
    TB.api('/api/check/run', { body: body }).then(function (r) {
      jobNow = r.jobId;
      $('runStop').disabled = false;
      poll(r.jobId);
    }, function (e) { done(); msg('msg', e.message, 'err'); });
  }

  function done() {
    jobNow = null;
    $('runDir').disabled = false;
    $('runText').disabled = false;
    $('runStop').disabled = true;
  }

  /* 5-21 — 검사 중지. 취소된 검사는 이력에 안 남는다(서버가 저장 전에 끊는다) */
  var jobNow = null;
  function stop() {
    if (!jobNow) return;
    $('runStop').disabled = true;
    msg('msg', '중지하는 중…');
    TB.api('/api/jobs/' + jobNow, { method: 'DELETE' }).then(null, function (e) { msg('msg', e.message, 'err'); });
  }

  function poll(jobId) {
    TB.api('/api/jobs/' + jobId).then(function (j) {
      if (j.status === 'QUEUED' || j.status === 'RUNNING') {
        msg('msg', '검사 중 ' + (j.progress || 0) + '% ' + (j.message || ''));
        setTimeout(function () { poll(jobId); }, 400);
        return;
      }
      done();
      if (j.status === 'CANCELLED') { msg('msg', '중지함 — 이력에 남기지 않았다', 'err'); return; }
      if (j.status !== 'DONE') { msg('msg', j.status + ' ' + (j.message || ''), 'err'); return; }
      var o = j.result;
      runId = o.runId;
      findings = o.findings || [];
      msg('msg', '파일 ' + o.files + ' · 결과 ' + findings.length + (o.skipped ? ' · 못 읽음 ' + o.skipped : '')
        + (o.parseErrors ? ' · 구문 못 읽음 ' + o.parseErrors : '') + (o.truncated ? ' · 목록 상한에 걸림' : '') + ' · 이력 #' + runId, 'ok');
      $('copy').disabled = false;
      $('xlsx').disabled = false;
      $('cmp').disabled = root === null;
      render();
    }, function (e) { done(); msg('msg', e.message, 'err'); });
  }

  function runDir() {
    var p = $('dir').value.trim();
    if (!p) { msg('msg', '폴더 경로를 넣는다', 'err'); return; }
    root = p;
    pastedText = null;
    var only = $('changed').checked && !$('changed').disabled;
    start({ path: p, changedOnly: only }, only ? '변경분 검사' : '폴더 검사');
  }

  /* 5-8 배포 목록 — 탭 전환, 두 지점 사이 바뀐 파일, xlsx */
  var vcsKind = 'none';

  function showTab(onDeploy) {
    $('paneCheck').style.display = onDeploy ? 'none' : 'flex';
    $('paneDeploy').style.display = onDeploy ? 'flex' : 'none';
    $('tabCheck').className = onDeploy ? 't' : 't on';
    $('tabDeploy').className = onDeploy ? 't on' : 't';
  }

  function depOptions(v) {
    var dl = $('depRecent');
    dl.innerHTML = '';
    (v.recent || []).forEach(function (r) {
      var op = document.createElement('option');
      op.value = r.id;
      op.textContent = r.date + ' ' + r.subject;
      dl.appendChild(op);
    });
    var svn = v.kind === 'svn';
    $('depFrom').placeholder = svn ? '리비전 번호 (예: 1200)' : '커밋·태그 (예: v1.0)';
    $('depNote').textContent = svn
      ? 'svn 은 리비전 구간을 저장소 서버에 묻는다 — 서버에 닿는 PC 에서만 된다'
      : '폴더 칸의 작업 사본에서 두 지점 사이에 바뀐 파일(추가·수정·삭제)을 배포 요청 목록으로 낸다';
  }

  function deploy(withXlsx) {
    var p = $('dir').value.trim();
    var from = $('depFrom').value.trim();
    var to = $('depTo').value.trim();
    if (!p || !from || !to) { msg('depMsg', '폴더·부터·까지를 넣는다', 'err'); return; }
    msg('depMsg', '조회 중');
    TB.api('/api/check/deploy-list', { body: { path: p, from: from, to: to, xlsx: withXlsx } }).then(function (r) {
      TB.table($('depResult'), ['순번', '파일', '상태', '확장자', '크기'], r.rows.map(function (x, i) {
        return [i + 1, x.file, { A: '추가', M: '수정', D: '삭제' }[x.status], x.ext, x.size];
      }));
      $('depCount').textContent = '추가 ' + r.counts.A + ' · 수정 ' + r.counts.M + ' · 삭제 ' + r.counts.D;
      msg('depMsg', r.xlsxPath ? 'xlsx — ' + r.xlsxPath : r.kind + ' ' + r.from + ' → ' + r.to, 'ok');
    }, function (e) { msg('depMsg', e.message, 'err'); });
  }

  /* 5-6b 폴더의 형상 관리 상태 — git·svn 작업 사본이고 명령이 있으면 「변경분만」 을 켠다 */
  var vcsSeq = 0;
  function vcsInfo() {
    var p = $('dir').value.trim();
    var cb = $('changed');
    var info = $('vcsInfo');
    var seq = ++vcsSeq;
    cb.disabled = true;
    info.textContent = '';
    if (!p) return;
    TB.api('/api/check/vcs?path=' + encodeURIComponent(p)).then(function (v) {
      if (seq !== vcsSeq) return;
      vcsKind = v.kind;
      depOptions(v);
      if (v.available) {
        cb.disabled = false;
        info.textContent = v.kind + ' · 변경 ' + v.changed;
        $('changedLabel').title = v.kind + ' 작업 사본의 바뀐 파일만 검사한다';
      } else {
        cb.checked = false;
        info.textContent = v.reason || '';
        $('changedLabel').title = v.reason || '';
      }
    }, function (e) {
      if (seq !== vcsSeq) return;
      cb.checked = false;
      info.textContent = e.message;
    });
  }

  function runText() {
    var t = $('paste').value;
    if (!t.trim()) { msg('msg', '붙여 넣은 글이 없다', 'err'); return; }
    root = null;
    pastedText = t;
    start({ text: t, lang: $('lang').value || null }, '붙여넣기 검사');
  }

  // ------------------------------------------------------------ 결과 표

  function render() {
    var sev = $('fSev').value;
    var grp = $('fGroup').value;
    var q = $('fText').value.toLowerCase();
    shown = findings.filter(function (f) {
      return (!sev || f.severity === sev) && (!grp || f.group === grp)
        && (!q || (f.file + ' ' + f.rule).toLowerCase().indexOf(q) >= 0);
    });
    var t = TB.table($('result'), ['파일', '줄', '묶음', '규칙', '등급', '원문'], shown.map(function (f) {
      return [f.file, lineText(f), groupLabel(f.group), f.rule, f.severity, f.excerpt];
    }));
    var trs = t.tBodies[0].rows;
    for (var i = 0; i < trs.length; i++) {
      (function (tr, f) {
        tr.onclick = function () {
          for (var k = 0; k < trs.length; k++) trs[k].className = '';
          tr.className = 'sel';
          preview(f);
        };
      })(trs[i], shown[i]);
    }
    $('count').textContent = shown.length + '/' + findings.length + '건';
  }

  function lineText(f) {
    return fileRule[f.rule] ? '파일' : f.line;
  }

  function around(text, line) {
    var lines = text.split(/\r?\n/);
    var from = Math.max(1, line - 5);
    var to = Math.min(lines.length, line + 5);
    var out = [];
    for (var n = from; n <= to; n++) {
      var no = String(n);
      while (no.length < 5) no = ' ' + no;
      out.push((n === line ? '>' : ' ') + no + '  ' + lines[n - 1]);
    }
    return out.join('\n');
  }

  function preview(f) {
    var box = $('preview');
    if (pastedText !== null) { box.textContent = around(pastedText, f.line); return; }
    var sep = root.charAt(root.length - 1) === '/' || root.charAt(root.length - 1) === '\\' ? '' : '/';
    box.textContent = '읽는 중';
    TB.api('/api/fs/read?path=' + encodeURIComponent(root + sep + f.file)).then(function (r) {
      box.textContent = f.file + ':' + f.line + '\n\n' + around(r.text, f.line);
    }, function (e) { box.textContent = '못 읽었다: ' + e.message; });
  }

  function copy() {
    var lines = ['파일\t줄\t묶음\t규칙\t등급\t원문'];
    shown.forEach(function (f) { lines.push([f.file, lineText(f), f.group, f.rule, f.severity, f.excerpt].join('\t')); });
    TB.copy({ text: lines.join('\n'), label: shown.length + '행' }, function (t, ok) { msg('msg', t, ok ? 'ok' : 'err'); });
  }

  function xlsx() {
    if (runId === null) return;
    TB.api('/api/check/runs/' + runId + '/export', { body: {} }).then(function (r) {
      msg('msg', 'xlsx ' + r.rows + '행 · ' + TB.savedText([r.path]), 'ok');
    }, function (e) { msg('msg', e.message, 'err'); });
  }

  function compare() {
    if (runId === null) return;
    TB.api('/api/check/runs/' + runId + '/compare').then(function (c) {
      msg('msg', '이력 #' + c.prevId + ' 대비 — 새로 ' + c.added.length + ' · 사라짐 ' + c.removed.length + ' · 그대로 ' + c.same, 'ok');
    }, function (e) { msg('msg', e.message, 'err'); });
  }

  function loadRecentDirs() {
    TB.api('/api/fs/defaults').then(function (d) {
      var dl = $('dirRecent');
      var seen = {};
      dl.innerHTML = '';
      [d.projectRoot].concat(d.recent || []).forEach(function (p) {
        if (!p || seen[p]) return;
        seen[p] = 1;
        var op = document.createElement('option');
        op.value = p;
        dl.appendChild(op);
      });
      if (!$('dir').value && d.projectRoot) $('dir').value = d.projectRoot;
      vcsInfo();
    }, function () {});
  }

  function init() {
    if (!window.TB) return;
    $('runDir').onclick = runDir;
    $('runStop').onclick = stop;
    $('runText').onclick = runText;
    $('dir').onchange = vcsInfo;
    $('tabCheck').onclick = function () { showTab(false); };
    $('tabDeploy').onclick = function () { showTab(true); };
    $('depRun').onclick = function () { deploy(false); };
    $('depXlsx').onclick = function () { deploy(true); };
    $('saveRules').onclick = saveRules;
    $('copy').onclick = copy;
    $('xlsx').onclick = xlsx;
    $('cmp').onclick = compare;
    $('fSev').onchange = render;
    $('fGroup').onchange = render;
    $('fText').oninput = render;
    loadRules();
    loadRecentDirs();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
