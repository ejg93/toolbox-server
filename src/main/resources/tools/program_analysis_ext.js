/*
 * 프로그램 분석 화면(6-5). ES5 — HtmlUnit 스모크가 읽는다(var·function, 화살표·let 없음).
 * 서버·파일에서 온 값은 textContent 로만 넣는다(innerHTML 은 비우기만).
 * 실행 직후와 이력 선택이 같은 길로 채운다 — GET /runs/{id}/crud(프로그램 목록·매트릭스 둘 다) + /unresolved.
 */
(function () {
  'use strict';

  var runId = null;
  var matrix = { tables: [], rows: [] };  // GET /api/analyze/runs/{id}/crud
  var unresolved = [];                     // GET /api/analyze/runs/{id}/unresolved
  var shown = [];                          // 프로그램 목록에서 거른 뒤
  var selected = null;

  function $(id) { return document.getElementById(id); }

  function msg(text, cls) {
    var el = $('msg');
    el.textContent = text;
    el.className = cls || '';
  }

  function low(s) { return String(s === null || s === undefined ? '' : s).toLowerCase(); }

  function progName(r) { return r.className + '.' + r.method; }

  function viewsText(r) {
    return (r.views || []).map(function (v) { return v.kind + ':' + v.name; }).join(', ');
  }

  // ------------------------------------------------------------ 탭

  var TABS = [['tabPrograms', 'panePrograms'], ['tabCrud', 'paneCrud'], ['tabUnresolved', 'paneUnresolved'], ['tabImpact', 'paneImpact'],
    ['tabConsistency', 'paneConsistency']];

  function showTab(tabId) {
    TABS.forEach(function (t) {
      var on = t[0] === tabId;
      $(t[0]).className = on ? 't on' : 't';
      $(t[1]).className = on ? 'pane on' : 'pane';
    });
  }

  // ------------------------------------------------------------ 실행·이력

  function run() {
    var p = $('dir').value.trim();
    if (!p) { msg('폴더 경로를 넣는다', 'err'); return; }
    $('run').disabled = true;
    msg('분석 시작');
    TB.api('/api/analyze/run', { body: { path: p } }).then(function (r) { poll(r.jobId); },
      function (e) { $('run').disabled = false; msg(e.message, 'err'); });
  }

  function poll(jobId) {
    TB.api('/api/jobs/' + jobId).then(function (j) {
      if (j.status === 'QUEUED' || j.status === 'RUNNING') {
        msg('분석 중 ' + (j.progress || 0) + '% ' + (j.message || ''));
        setTimeout(function () { poll(jobId); }, 400);
        return;
      }
      $('run').disabled = false;
      if (j.status !== 'DONE') { msg(j.status + ' ' + (j.message || ''), 'err'); return; }
      var o = j.result;
      var note = '파일 ' + o.files + (o.skipped ? ' · 못 읽음 ' + o.skipped : '') + (o.truncated ? ' · 목록 상한에 걸림' : '');
      loadRuns(o.runId);
      load(o.runId, note);
    }, function (e) { $('run').disabled = false; msg(e.message, 'err'); });
  }

  function when(s) {
    return String(s || '').replace('T', ' ').substring(0, 19);
  }

  function loadRuns(selectId) {
    TB.api('/api/analyze/runs').then(function (list) {
      var sel = $('runs');
      sel.innerHTML = '';
      var none = document.createElement('option');
      none.value = '';
      none.textContent = list.length ? '— ' + list.length + '건' : '—';
      sel.appendChild(none);
      list.forEach(function (r) {
        var op = document.createElement('option');
        op.value = String(r.id);
        op.textContent = '#' + r.id + ' · ' + when(r.startedAt) + ' · ' + r.path + ' · 프로그램 ' + r.programs;
        sel.appendChild(op);
      });
      sel.value = selectId === null || selectId === undefined ? '' : String(selectId);
    }, function () {});
  }

  function load(id, note) {
    msg('불러오는 중 #' + id);
    TB.api('/api/analyze/runs/' + id + '/crud').then(function (m) {
      return TB.api('/api/analyze/runs/' + id + '/unresolved').then(function (u) {
        runId = id;
        $('xlsx').disabled = false;
        matrix = m;
        unresolved = u;
        selected = null;
        $('detail').textContent = '행을 누르면 그 프로그램의 문장과 CRUD';
        kindOptions();
        renderPrograms();
        renderCrud();
        renderUnresolved();
        impactTables();
        msg((note ? note + ' · ' : '') + '프로그램 ' + m.rows.length + ' · 표 ' + m.tables.length + ' · 미해결 ' + u.length
          + ' · 이력 #' + id, 'ok');
      });
    }).then(null, function (e) { msg(e.message, 'err'); });
  }

  // ------------------------------------------------------------ 프로그램 목록

  function renderPrograms() {
    var q = low($('fP').value.trim());
    shown = matrix.rows.filter(function (r) {
      if (!q) return true;
      return (low(progName(r)) + ' ' + low(r.url) + ' ' + low(r.description)).indexOf(q) >= 0;
    });
    var t = TB.table($('programs'), ['클래스', '메서드', 'verb', 'URL', 'params', '종류', '뷰', '문장', '설명'],
      shown.map(function (r) {
        return [r.className, r.method, r.verb, r.url, r.params || '', r.kind, viewsText(r), (r.statements || []).length, r.description || ''];
      }));
    var trs = t.tBodies[0].rows;
    for (var i = 0; i < trs.length; i++) bindRow(trs[i], shown[i]);
    $('count').textContent = shown.length + ' / ' + matrix.rows.length;
  }

  function bindRow(tr, r) {
    tr.onclick = function () {
      if (selected) selected.className = '';
      selected = tr;
      tr.className = 'sel';
      detail(r);
    };
  }

  function detail(r) {
    var lines = [progName(r) + '  ' + r.verb + ' ' + r.url + (r.params ? ' ' + r.params : ''), r.file + ':' + r.line, '', '문장'];
    (r.statements || []).forEach(function (s) { lines.push('  ' + s.id + '  (' + s.resolution + ')'); });
    lines.push('', 'CRUD');
    Object.keys(r.crud || {}).sort().forEach(function (k) { lines.push('  ' + k + '  ' + r.crud[k]); });
    lines.push('', '뷰');
    (r.views || []).forEach(function (v) { lines.push('  ' + v.kind + ':' + v.name); });
    $('detail').textContent = lines.join('\n');
  }

  // ------------------------------------------------------------ CRUD 매트릭스

  function cell(tag, text, cls) {
    var el = document.createElement(tag);
    el.textContent = text;
    if (cls) el.className = cls;
    return el;
  }

  function renderCrud() {
    var ft = $('fTable').value.trim().toUpperCase();
    var fp = low($('fProg').value.trim());
    var all = $('allRows').checked;
    var cols = matrix.tables.filter(function (t) { return !ft || t.toUpperCase().indexOf(ft) >= 0; });
    var rows = matrix.rows.filter(function (r) {
      if (fp && (low(progName(r)) + ' ' + low(r.url)).indexOf(fp) < 0) return false;
      if (all) return true;
      for (var i = 0; i < cols.length; i++) if (r.crud && r.crud[cols[i]]) return true;
      return false;
    });
    var table = document.createElement('table');
    var thead = document.createElement('thead');
    var hr = document.createElement('tr');
    hr.appendChild(cell('th', '프로그램', 'head'));
    hr.appendChild(cell('th', 'URL'));
    cols.forEach(function (t) { hr.appendChild(cell('th', t)); });
    thead.appendChild(hr);
    table.appendChild(thead);
    var tbody = document.createElement('tbody');
    var frag = document.createDocumentFragment();
    rows.forEach(function (r) {
      var tr = document.createElement('tr');
      tr.appendChild(cell('td', progName(r), 'head'));
      tr.appendChild(cell('td', r.url + (r.params ? ' ' + r.params : '')));
      cols.forEach(function (t) {
        var v = r.crud ? r.crud[t] : null;
        tr.appendChild(cell('td', v || '', v ? 'c c-' + v.charAt(0) : 'c'));
      });
      frag.appendChild(tr);
    });
    tbody.appendChild(frag);
    table.appendChild(tbody);
    var box = $('crud');
    box.innerHTML = '';
    box.appendChild(table);
    $('crudCount').textContent = '프로그램 ' + rows.length + ' / ' + matrix.rows.length + ' · 표 ' + cols.length + ' / ' + matrix.tables.length;
  }

  // ------------------------------------------------------------ 미해결

  function kindOptions() {
    var sel = $('fKind');
    var keep = sel.value;
    var kinds = {};
    unresolved.forEach(function (u) { kinds[u.kind] = (kinds[u.kind] || 0) + 1; });
    sel.innerHTML = '';
    var allOp = document.createElement('option');
    allOp.value = '';
    allOp.textContent = '전체';
    sel.appendChild(allOp);
    Object.keys(kinds).sort().forEach(function (k) {
      var op = document.createElement('option');
      op.value = k;
      op.textContent = k + ' ' + kinds[k];
      sel.appendChild(op);
    });
    sel.value = kinds[keep] ? keep : '';
  }

  function renderUnresolved() {
    var k = $('fKind').value;
    var list = unresolved.filter(function (u) { return !k || u.kind === k; });
    TB.table($('unresolved'), ['종류', '파일', '줄', '식별자'], list.map(function (u) { return [u.kind, u.file, u.line, u.detail || '']; }));
    $('unCount').textContent = list.length + ' / ' + unresolved.length;
  }

  // ------------------------------------------------------------ xlsx(6-7)

  function xlsx() {
    if (runId === null) return;
    TB.api('/api/analyze/runs/' + runId + '/export', { body: { format: 'xlsx' } }).then(function (r) {
      msg('xlsx ' + TB.savedText(r.files.map(function (f) { return f.path; }), r.dir), 'ok');
    }, function (e) { msg(e.message, 'err'); });
  }

  // ------------------------------------------------------------ 영향도(6-6)

  function impactTables() {
    var dl = $('impTables');
    dl.innerHTML = '';
    matrix.tables.forEach(function (t) {
      var op = document.createElement('option');
      op.value = t;
      dl.appendChild(op);
    });
    $('impact').innerHTML = '';
    $('impJsps').textContent = '';
  }

  function impact() {
    var t = $('impTable').value.trim();
    var m = $('impMsg');
    m.className = 'count';
    if (runId === null) { m.textContent = '먼저 분석하거나 이력을 고른다'; m.className = 'err'; return; }
    if (!t) { m.textContent = '표 이름을 넣는다'; m.className = 'err'; return; }
    TB.api('/api/analyze/runs/' + runId + '/impact?table=' + encodeURIComponent(t)).then(function (im) {
      TB.table($('impact'), ['프로그램', 'verb', 'URL', 'CRUD', '뷰', 'JSP'], im.rows.map(function (x) {
        var r = x.program;
        return [progName(r), r.verb, r.url + (r.params ? ' ' + r.params : ''), (r.crud || {})[im.table] || '', viewsText(r), x.jsps.length];
      }));
      $('impJsps').textContent = im.jsps.length ? im.jsps.join('\n') : '이 표의 프로그램 URL 을 부르는 JSP 가 없다';
      m.textContent = im.table + ' — 프로그램 ' + im.rows.length + ' · JSP ' + im.jsps.length;
    }, function (e) { m.textContent = e.message; m.className = 'err'; });
  }

  // ------------------------------------------------------------ 정합성(6-12)

  function loadSnapshots() {
    TB.api('/api/meta/snapshots').then(function (list) {
      var sel = $('conSnap');
      sel.innerHTML = '';
      var none = document.createElement('option');
      none.value = '';
      none.textContent = '스냅샷 없이';
      sel.appendChild(none);
      (list || []).forEach(function (s) {
        var op = document.createElement('option');
        op.value = String(s.id);
        op.textContent = TB.snapLabel(s);
        sel.appendChild(op);
      });
    }, function () {});
  }

  function consistency() {
    var m = $('conMsg');
    m.className = 'count';
    if (runId === null) { m.textContent = '먼저 분석하거나 이력을 고른다'; m.className = 'err'; return; }
    var snap = $('conSnap').value;
    TB.api('/api/analyze/runs/' + runId + '/consistency' + (snap ? '?snapshotId=' + encodeURIComponent(snap) : '')).then(function (r) {
      if (snap) {
        TB.table($('conMissing'), ['표', '프로그램 수'], r.missingInDb.map(function (x) { return [x.table, x.programs]; }));
        TB.table($('conUnused'), ['스키마', '표', '종류'], r.unusedInCode.map(function (x) { return [x.schema, x.table, x.type || '']; }));
      } else {
        $('conMissing').innerHTML = '';
        $('conMissing').textContent = '스냅샷을 고르면 나온다';
        $('conUnused').innerHTML = '';
        $('conUnused').textContent = '스냅샷을 고르면 나온다';
      }
      TB.table($('conDead'), ['문장(ns.id)'], r.deadStatements.map(function (x) { return [x]; }));
      TB.table($('conOrphan'), ['JSP'], r.orphanJsps.map(function (x) { return [x]; }));
      m.textContent = (snap ? 'DB 에 없는 표 ' + r.missingInDb.length + ' · 안 쓰는 표 ' + r.unusedInCode.length + ' · ' : '')
        + '안 불리는 문장 ' + r.deadStatements.length + ' · 고아 JSP ' + r.orphanJsps.length;
    }, function (e) { m.textContent = e.message; m.className = 'err'; });
  }

  // ------------------------------------------------------------ 시작

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
    }, function () {});
  }

  function init() {
    if (!window.TB) return;
    $('run').onclick = run;
    $('runs').onchange = function () { if ($('runs').value) load(Number($('runs').value)); };
    TABS.forEach(function (t) { $(t[0]).onclick = function () { showTab(t[0]); }; });
    $('fP').oninput = renderPrograms;
    $('fTable').oninput = renderCrud;
    $('fProg').oninput = renderCrud;
    $('allRows').onchange = renderCrud;
    $('fKind').onchange = renderUnresolved;
    $('impRun').onclick = impact;
    $('xlsx').onclick = xlsx;
    $('conRun').onclick = consistency;
    loadRecentDirs();
    loadRuns(null);
    loadSnapshots();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
