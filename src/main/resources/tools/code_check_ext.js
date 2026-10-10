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

  function $(id) { return document.getElementById(id); }

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
      ['dirGroup', 'pasteGroup'].forEach(function (id) {
        var sel = $(id);
        GROUPS.forEach(function (g) {
          var op = document.createElement('option');
          op.value = g[0];
          op.textContent = g[1];
          sel.appendChild(op);
        });
      });
    }, function (e) { TB.result('ruleRes', 'fail', { summary: '규칙을 못 읽었다: ' + e.message }); });
  }

  /*
   * 규칙 칸(5-24f) — 묶음 머리 체크는 그 묶음 규칙의 전체 선택(머리 체크 꼴, 3-13·4-19 와 같다). 일부만 켜졌으면 중간 상태,
   * 맨 위 「전체」 는 모든 묶음. 묶음이 꺼진 프로필이면 그 규칙은 꺼진 채 보인다(묶음 끔이 규칙 켬보다 앞선다)
   */
  function syncGroup(g) {
    var cb = $('g_' + g);
    if (!cb) return;
    var mine = rules.filter(function (r) { return r.group === g; });
    var on = 0;
    mine.forEach(function (r) { if ($('r_' + r.id).checked) on++; });
    cb.checked = mine.length > 0 && on === mine.length;
    cb.indeterminate = on > 0 && on < mine.length;
    $('n_' + g).textContent = '규칙 ' + on + '/' + mine.length;
  }

  function syncAll() {
    var all = $('rulesAll');
    if (!all) return;
    var on = 0;
    rules.forEach(function (r) { var rc = $('r_' + r.id); if (rc && rc.checked) on++; });
    all.checked = rules.length > 0 && on === rules.length;
    all.indeterminate = on > 0 && on < rules.length;
  }

  function setGroup(g, on) {
    rules.forEach(function (r) { if (r.group === g) $('r_' + r.id).checked = on; });
    syncGroup(g);
  }

  function renderRules() {
    var box = $('rules');
    box.innerHTML = '';
    var top = document.createElement('label');
    top.className = 'ghead all';
    var allCb = document.createElement('input');
    allCb.type = 'checkbox';
    allCb.id = 'rulesAll';
    allCb.onchange = function () {
      GROUPS.forEach(function (g) { if ($('g_' + g[0])) setGroup(g[0], allCb.checked); });
      syncAll();
    };
    top.appendChild(allCb);
    top.appendChild(document.createTextNode(' 전체'));
    box.appendChild(top);
    GROUPS.forEach(function (g) {
      var mine = rules.filter(function (r) { return r.group === g[0]; });
      if (!mine.length) return;
      var groupOn = mine[0].groupEnabled !== false;
      var wrap = document.createElement('div');
      wrap.className = 'grp';
      var head = document.createElement('div');
      head.className = 'ghead';
      var cb = document.createElement('input');
      cb.type = 'checkbox';
      cb.id = 'g_' + g[0];
      cb.onclick = function (ev) { ev.stopPropagation(); };
      cb.onchange = function () { setGroup(g[0], cb.checked); syncAll(); };
      var name = document.createElement('span');
      name.textContent = g[1];
      var n = document.createElement('span');
      n.className = 'n';
      n.id = 'n_' + g[0];
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
        rc.checked = groupOn && r.ruleEnabled !== false;
        rc.onchange = function () { syncGroup(g[0]); syncAll(); };
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
      syncGroup(g[0]);
    });
    syncAll();
  }

  /*
   * 화면 상태 → {groups, rules}. 묶음은 규칙이 하나라도 켜졌으면 켬. 꺼진 묶음의 규칙은 안 보낸다(묶음 끔이 덮는다),
   * 켜진 묶음의 규칙은 처음 받은 규칙 켬(묶음과 따로)과 다른 것만
   */
  function choice() {
    var groups = {};
    var over = {};
    GROUPS.forEach(function (g) {
      var mine = rules.filter(function (r) { return r.group === g[0]; });
      if (!mine.length || !$('g_' + g[0])) return;
      groups[g[0]] = mine.some(function (r) { return $('r_' + r.id).checked; });
    });
    rules.forEach(function (r) {
      var rc = $('r_' + r.id);
      if (!rc || !groups[r.group]) return;
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

  /* 5-24d — 저장 대상 프로필 파일을 버튼에 박는다. 머리줄 고르기·다른 탭이 바꾸면(tb:profile) 따라 바뀐다 */
  var activeProfile = null;
  function saveTarget(name) {
    activeProfile = name || null;
    $('saveTarget').textContent = activeProfile || '(활성 프로필 없음)';
    $('saveRules').disabled = !activeProfile;
  }

  /* 규칙 켬·끔을 활성 프로필 YAML 의 codecheck.groups·rules 에 — 덮어쓰기라 백업(1-58g)과 함께 결과 칸 세 줄(R11) */
  function saveRules() {
    if (!activeProfile) { TB.result('ruleRes', 'fail', { summary: '활성 프로필이 없다 — 머리줄에서 고른다' }); return; }
    if (!rules.length) { TB.result('ruleRes', 'fail', { summary: '규칙을 아직 못 읽었다' }); return; }
    var c = choice();
    TB.result('ruleRes', 'run', { summary: '저장 중…' });
    TB.api('/api/profiles/' + encodeURIComponent(activeProfile) + '/codecheck', { method: 'PUT', body: c }).then(function (r) {
      TB.result('ruleRes', 'ok', { summary: '기본 규칙 저장 — 묶음 ' + Object.keys(c.groups).length + ' · 규칙 ' + Object.keys(c.rules).length,
        path: r.path, backup: r.backup });
      return loadRulesKeepOpen();
    }).then(null, function (e) { TB.result('ruleRes', 'fail', { summary: '저장 실패: ' + e.message }); });
  }

  function loadRulesKeepOpen() {
    return TB.api('/api/check/rules').then(function (list) { rules = list; renderRules(); });
  }

  // ------------------------------------------------------------ 실행 — 탭마다 자기 결과 묶음(5-24)

  /* 결과 묶음 하나 — 폴더 검사(dir)·붙여넣기 검사(paste). id 는 접두 + 이름 */
  function pane(prefix) {
    return {
      prefix: prefix, findings: [], shown: [], runId: null, root: null, pastedText: null,
      id: function (name) { return prefix + name; }
    };
  }
  var panes = { dir: pane('dir'), paste: pane('paste') };
  var running = null; // 지금 도는 pane — 작업은 한 번에 하나

  function res(p, state, summary, path) { TB.result(p.id('Res'), state, { summary: summary, path: path }); }

  function start(p, body, label) {
    var c = choice();
    body.groups = c.groups;
    body.ruleOverrides = c.rules;
    $('runDir').disabled = true;
    $('runText').disabled = true;
    running = p;
    res(p, 'run', label + ' 시작');
    TB.api('/api/check/run', { body: body }).then(function (r) {
      jobNow = r.jobId;
      $(p.id('Stop')).disabled = false;
      poll(p, r.jobId);
    }, function (e) { done(); res(p, 'fail', e.message); });
  }

  function done() {
    jobNow = null;
    $('runDir').disabled = false;
    $('runText').disabled = false;
    if (running) $(running.id('Stop')).disabled = true;
    running = null;
  }

  /* 5-21 — 검사 중지. 취소된 검사는 이력에 안 남는다(서버가 저장 전에 끊는다). 중지는 오류가 아니라 노랑(1-58 R12) */
  var jobNow = null;
  function stop() {
    if (!jobNow || !running) return;
    var p = running;
    $(p.id('Stop')).disabled = true;
    res(p, 'run', '중지하는 중…');
    TB.api('/api/jobs/' + jobNow, { method: 'DELETE' }).then(null, function (e) { $(p.id('Stop')).disabled = false; res(p, 'fail', e.message); });
  }

  function poll(p, jobId) {
    TB.api('/api/jobs/' + jobId).then(function (j) {
      if (j.status === 'QUEUED' || j.status === 'RUNNING') {
        res(p, 'run', '검사 중 ' + (j.progress || 0) + '% ' + (j.message || ''));
        setTimeout(function () { poll(p, jobId); }, 400);
        return;
      }
      done();
      if (j.status === 'CANCELLED') { res(p, 'stop', '중지함 — 이력에 남기지 않았다'); return; }
      if (j.status !== 'DONE') { res(p, 'fail', j.status + ' ' + (j.message || '')); return; }
      var o = j.result;
      p.runId = o.runId;
      p.findings = o.findings || [];
      res(p, 'ok', '파일 ' + o.files + ' · 결과 ' + p.findings.length + (o.skipped ? ' · 못 읽음 ' + o.skipped : '')
        + (o.parseErrors ? ' · 구문 못 읽음 ' + o.parseErrors : '') + (o.truncated ? ' · 목록 상한에 걸림' : '') + ' · 이력 #' + p.runId);
      $(p.id('Copy')).disabled = false;
      $(p.id('Xlsx')).disabled = false;
      render(p);
      afterRun(p);
    }, function (e) { done(); res(p, 'fail', e.message); });
  }

  /* 검사가 끝나면 이력 목록을 다시 받고 새 실행을 고른다 */
  function afterRun(p) { loadRuns(p === panes.dir ? p.runId : ($('runs').value || null)); } // 붙여넣기는 폴더 탭 고르기를 안 바꾼다

  // ------------------------------------------------------------ 이력 고르기(5-24c)

  function when(t) { return String(t || '').replace('T', ' ').slice(5, 16); } // MM-DD HH:mm
  function tail(path) {
    var s = String(path || '').replace(/[\\\/]+$/, '');
    var i = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
    return i >= 0 ? s.substring(i + 1) : s;
  }

  /* GET /api/check/runs — 라벨 「#id MM-DD HH:mm · 경로 끝 · n건(· 변경분)」. 첫 칸은 지금 화면의 검사 */
  function loadRuns(selectId) {
    return TB.api('/api/check/runs').then(function (list) {
      var sel = $('runs');
      sel.innerHTML = '';
      var first = document.createElement('option');
      first.value = '';
      first.textContent = '(이번 검사)';
      sel.appendChild(first);
      list.forEach(function (r) {
        var op = document.createElement('option');
        op.value = String(r.id);
        op.textContent = '#' + r.id + ' ' + when(r.startedAt) + ' · ' + tail(r.path) + ' · ' + r.findings + '건' + (r.changedOnly ? ' · 변경분' : '');
        sel.appendChild(op);
      });
      sel.value = selectId === null || selectId === undefined ? '' : String(selectId);
    }, function () {});
  }

  /* 지난 검사 열기 — 발췌는 안 남기니(규칙 3) 원문 칸은 비고, 미리보기는 지금 파일을 다시 읽는다 */
  function openRun(id) {
    var p = panes.dir;
    if (running) { $('runs').value = p.runId === null ? '' : String(p.runId); res(running, 'run', '검사가 끝난 뒤 이력을 고른다'); return; }
    res(p, 'run', '이력 #' + id + ' 읽는 중…');
    TB.api('/api/check/runs/' + id).then(function (o) {
      var run = o.run;
      p.findings = o.findings || [];
      p.runId = run.id;
      p.root = run.path && run.path !== '(붙여넣기)' ? run.path : null;
      p.pastedText = null;
      if (p.root) $('dir').value = p.root;
      $(p.id('Copy')).disabled = false;
      $(p.id('Xlsx')).disabled = false;
      render(p);
      $(p.id('Preview')).textContent = '행을 누르면 그 줄 앞뒤 5줄';
      TB.result(p.id('Res'), 'ok', { summary: '이력 #' + run.id + ' · ' + when(run.startedAt) + ' · ' + p.findings.length
        + (p.root ? '건 — 미리보기는 지금 파일(검사 뒤 바뀌었으면 줄이 어긋난다)' : '건 — 붙여 넣은 글은 이력에 없어 미리보기가 없다'), path: p.root });
    }, function (e) { res(p, 'fail', e.message); });
  }

  function runDir() {
    var p = panes.dir;
    var dir = $('dir').value.trim();
    if (!dir) { res(p, 'fail', '폴더 경로를 넣는다'); return; }
    p.root = dir;
    p.pastedText = null;
    var only = $('changed').checked && !$('changed').disabled;
    start(p, { path: dir, changedOnly: only }, only ? '변경분 검사' : '폴더 검사');
  }

  function runText() {
    var p = panes.paste;
    var t = $('paste').value;
    if (!t.trim()) { res(p, 'fail', '붙여 넣은 글이 없다'); return; }
    p.root = null;
    p.pastedText = t;
    start(p, { text: t, lang: $('lang').value || null }, '붙여넣기 검사');
  }

  /* 5-8 배포 목록 · 5-24 탭 셋 — 규칙 칸은 두 검사 탭에서만 */
  var vcsKind = 'none';
  var DEP_NOTE = ''; // 화면 글(쓰임) — svn 이면 한 마디 덧붙인다

  function showTab(name) {
    var tabs = { dir: ['tabDir', 'paneDir'], paste: ['tabPaste', 'panePaste'], deploy: ['tabDeploy', 'paneDeploy'] };
    for (var k in tabs) {
      if (!Object.prototype.hasOwnProperty.call(tabs, k)) continue;
      $(tabs[k][0]).className = k === name ? 't on' : 't';
      if (k === name) $(tabs[k][1]).removeAttribute('hidden'); else $(tabs[k][1]).setAttribute('hidden', '');
    }
    if (name === 'deploy') $('rulesCol').setAttribute('hidden', ''); else $('rulesCol').removeAttribute('hidden');
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
    $('depNote').textContent = DEP_NOTE + (svn ? ' — svn 은 리비전 구간을 저장소 서버에 묻는다(서버에 닿는 PC 에서만 된다)' : '');
  }

  function deploy(withXlsx) {
    var p = $('dir').value.trim();
    var from = $('depFrom').value.trim();
    var to = $('depTo').value.trim();
    if (!p || !from || !to) { TB.result('depRes', 'fail', { summary: '폴더(폴더 검사 탭)·부터·까지를 넣는다' }); return; }
    TB.result('depRes', 'run', { summary: '조회 중…' });
    TB.api('/api/check/deploy-list', { body: { path: p, from: from, to: to, xlsx: withXlsx } }).then(function (r) {
      TB.table($('depResult'), ['순번', '파일', '상태', '확장자', '크기'], r.rows.map(function (x, i) {
        return [i + 1, x.file, { A: '추가', M: '수정', D: '삭제' }[x.status], x.ext, x.size];
      }));
      $('depCount').textContent = '추가 ' + r.counts.A + ' · 수정 ' + r.counts.M + ' · 삭제 ' + r.counts.D;
      TB.result('depRes', 'ok', { summary: (r.xlsxPath ? '배포 목록 — ' : '') + r.kind + ' ' + r.from + ' → ' + r.to
        + ' · 추가 ' + r.counts.A + ' · 수정 ' + r.counts.M + ' · 삭제 ' + r.counts.D, path: r.xlsxPath || null });
    }, function (e) { TB.result('depRes', 'fail', { summary: e.message }); });
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

  // ------------------------------------------------------------ 결과 표

  function render(p) {
    var sev = $(p.id('Sev')).value;
    var grp = $(p.id('Group')).value;
    var q = $(p.id('Text')).value.toLowerCase();
    p.shown = p.findings.filter(function (f) {
      return (!sev || f.severity === sev) && (!grp || f.group === grp)
        && (!q || (f.file + ' ' + f.rule).toLowerCase().indexOf(q) >= 0);
    });
    var t = TB.table($(p.id('Result')), ['파일', '줄', '묶음', '규칙', '등급', '원문'], p.shown.map(function (f) {
      return [f.file, lineText(f), groupLabel(f.group), f.rule, f.severity, f.excerpt];
    }));
    var trs = t.tBodies[0].rows;
    for (var i = 0; i < trs.length; i++) {
      (function (tr, f) {
        tr.onclick = function () {
          for (var k = 0; k < trs.length; k++) trs[k].className = '';
          tr.className = 'sel';
          preview(p, f);
        };
      })(trs[i], p.shown[i]);
    }
    $(p.id('Count')).textContent = p.shown.length + '/' + p.findings.length + '건';
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

  function preview(p, f) {
    var box = $(p.id('Preview'));
    if (p.pastedText !== null) { box.textContent = around(p.pastedText, f.line); return; }
    if (!p.root) { box.textContent = '붙여 넣은 글은 이력에 없어 미리보기가 없다'; return; }
    var sep = p.root.charAt(p.root.length - 1) === '/' || p.root.charAt(p.root.length - 1) === '\\' ? '' : '/';
    box.textContent = '읽는 중';
    TB.api('/api/fs/read?path=' + encodeURIComponent(p.root + sep + f.file)).then(function (r) {
      box.textContent = f.file + ':' + f.line + '\n\n' + around(r.text, f.line);
    }, function (e) { box.textContent = '못 읽었다: ' + e.message; });
  }

  function copy(p) {
    var lines = ['파일\t줄\t묶음\t규칙\t등급\t원문'];
    p.shown.forEach(function (f) { lines.push([f.file, lineText(f), f.group, f.rule, f.severity, f.excerpt].join('\t')); });
    TB.copy({ text: lines.join('\n'), label: p.shown.length + '행' }, function (t, ok) { res(p, ok ? 'ok' : 'fail', t); });
  }

  function xlsx(p) {
    if (p.runId === null) return;
    res(p, 'run', '코드 검사 결과 엑셀 만드는 중…');
    TB.api('/api/check/runs/' + p.runId + '/export', { body: {} }).then(function (r) {
      res(p, 'ok', '코드 검사 결과 ' + r.rows + '행', r.path);
    }, function (e) { res(p, 'fail', e.message); });
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

  function bindPane(p) {
    $(p.id('Stop')).onclick = stop;
    $(p.id('Copy')).onclick = function () { copy(p); };
    $(p.id('Xlsx')).onclick = function () { xlsx(p); };
    $(p.id('Sev')).onchange = function () { render(p); };
    $(p.id('Group')).onchange = function () { render(p); };
    $(p.id('Text')).oninput = function () { render(p); };
  }

  function init() {
    if (!window.TB) return;
    $('runDir').onclick = runDir;
    $('runText').onclick = runText;
    $('dir').onchange = vcsInfo;
    $('tabDir').onclick = function () { showTab('dir'); };
    $('tabPaste').onclick = function () { showTab('paste'); };
    $('tabDeploy').onclick = function () { showTab('deploy'); };
    $('depRun').onclick = function () { deploy(false); };
    $('depXlsx').onclick = function () { deploy(true); };
    $('saveRules').onclick = saveRules;
    $('runs').onchange = function () { if ($('runs').value) openRun(Number($('runs').value)); };
    bindPane(panes.dir);
    bindPane(panes.paste);
    DEP_NOTE = $('depNote').textContent;
    TB.profiles().then(function (p) { saveTarget(p.active); }, function () { saveTarget(null); });
    document.addEventListener('tb:profile', function (ev) { saveTarget(ev.detail); });
    loadRules();
    loadRecentDirs();
    loadRuns(null);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
