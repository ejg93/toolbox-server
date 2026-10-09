/*
 * 프로그램 분석 화면(6-5). ES5 — HtmlUnit 스모크가 읽는다(var·function, 화살표·let 없음).
 * 서버·파일에서 온 값은 textContent 로만 넣는다(innerHTML 은 비우기만).
 * 실행 직후와 이력 선택이 같은 길로 채운다 — GET /runs/{id}/crud(프로그램 목록·매트릭스 둘 다) + /unresolved.
 */
(function () {
  'use strict';

  var runId = null;
  var matrix = { tables: [], rows: [], longRows: [], moduleMatrix: { modules: [], rows: [] } };  // GET /api/analyze/runs/{id}/crud
  var crudMode = 'module';                 // 6-21 — module(모듈 매트릭스) · list(세로 목록)
  var unresolved = [];                     // GET /api/analyze/runs/{id}/unresolved
  var kinds = {};                          // GET /api/analyze/unresolved-kinds — 코드 → {kind, name, meaning, fix}, 넣은 순서 = 칩 순서
  var kindSel = '';                        // 고른 칩(빈 글 = 전체)
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

  // 6-18 — 화면에 보이는 이름. 저장·API 값(view·literal·C/R/U/D)은 그대로 두고 여기서만 바꾼다
  var CRUD_WORDS = { C: 'Create', R: 'Read', U: 'Update', D: 'Delete' };
  var HOW = { literal: '문자열 그대로', mapper: 'Mapper 인터페이스', 'var': '변수 추적', prefix: '앞부분 일치 — 후보 여럿',
    jpa: 'JPA 추정', qdsl: 'QueryDSL 추정' };

  // 순서는 데이터가 아니라 이 배열이 정한다 — C→R→U→D(PR #47 리뷰)
  var CRUD_ORDER = ['C', 'R', 'U', 'D'];

  function crudWords(s) {
    var t = String(s || '');
    return CRUD_ORDER.filter(function (c) { return t.indexOf(c) >= 0; })
      .map(function (c) { return CRUD_WORDS[c]; }).join(' · ');
  }

  function viewLabel(v) { return (v.kind === 'view' ? 'jsp' : v.kind) + ': ' + v.name; }

  function kindLabel(k) { return k === 'view' ? 'page' : k; }

  function viewsText(r) {
    return (r.views || []).map(viewLabel).join(', ');
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
        $('crudDetail').textContent = '모듈 칸을 누르면 그 모듈·표의 프로그램 목록';
        renderChips();
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
    var t = TB.table($('programs'), ['클래스', '메서드', 'verb', 'URL', 'params', '종류', 'view', '문장', '설명'],
      shown.map(function (r) {
        return [r.className, r.method, r.verb, r.url, r.params || '', kindLabel(r.kind), viewsText(r), (r.statements || []).length, r.description || ''];
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
    (r.statements || []).forEach(function (s) { lines.push('  ' + s.id + '  (' + (HOW[s.resolution] || s.resolution) + ')'); });
    lines.push('', 'CRUD');
    Object.keys(r.crud || {}).sort().forEach(function (k) { lines.push('  ' + k + '  ' + crudWords(r.crud[k])); });
    lines.push('', 'view');
    (r.views || []).forEach(function (v) { lines.push('  ' + viewLabel(v)); });
    $('detail').textContent = lines.join('\n');
  }

  // ------------------------------------------------------------ CRUD 매트릭스

  function cell(tag, text, cls) {
    var el = document.createElement(tag);
    el.textContent = text;
    if (cls) el.className = cls;
    return el;
  }

  // 6-21 — 넓은 격자(열 = 표 전부)를 걷었다. 모듈 매트릭스(기본)와 세로 목록. 합집합은 서버(CrudViews)가 센다 — 여기서는 안 센다
  function renderCrud() {
    var module = crudMode === 'module';
    $('crudModeModule').className = module ? 't on' : 't';
    $('crudModeList').className = module ? 't' : 't on';
    $('fProg').disabled = module;
    $('fProg').placeholder = module ? '세로 목록에서' : '클래스·메서드·URL';
    var box = $('crud');
    box.innerHTML = '';
    var legend = document.createElement('div');
    legend.className = 'count';
    legend.id = 'crudLegend';
    legend.textContent = 'C=Create · R=Read · U=Update · D=Delete';
    box.appendChild(legend);
    box.appendChild(module ? moduleTable() : listTable());
  }

  function moduleTable() {
    var ft = $('fTable').value.trim().toUpperCase();
    var mm = matrix.moduleMatrix || { modules: [], rows: [] };
    var rows = mm.rows.filter(function (r) { return !ft || r.table.toUpperCase().indexOf(ft) >= 0; });
    var table = document.createElement('table');
    var thead = document.createElement('thead');
    var hr = document.createElement('tr');
    hr.appendChild(cell('th', '표', 'head'));
    mm.modules.forEach(function (m) { hr.appendChild(cell('th', m)); });
    thead.appendChild(hr);
    table.appendChild(thead);
    var tbody = document.createElement('tbody');
    rows.forEach(function (r) {
      var tr = document.createElement('tr');
      tr.appendChild(cell('td', r.table, 'head'));
      mm.modules.forEach(function (m) {
        var v = r.cells[m];
        var td = cell('td', v || '', v ? 'c c-' + v.charAt(0) : 'c');
        if (v) td.onclick = function () { crudDetail(m, r.table); };
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    $('crudCount').textContent = '표 ' + rows.length + ' / ' + mm.rows.length + ' · 모듈 ' + mm.modules.length;
    return table;
  }

  function listTable() {
    var ft = $('fTable').value.trim().toUpperCase();
    var fp = low($('fProg').value.trim());
    var all = matrix.longRows || [];
    var rows = all.filter(function (p) {
      if (ft && p.table.toUpperCase().indexOf(ft) < 0) return false;
      return !fp || (low(p.program) + ' ' + low(p.url)).indexOf(fp) >= 0;
    });
    var table = document.createElement('table');
    var thead = document.createElement('thead');
    var hr = document.createElement('tr');
    ['프로그램', 'URL', '모듈', '표', 'CRUD'].forEach(function (h) { hr.appendChild(cell('th', h)); });
    thead.appendChild(hr);
    table.appendChild(thead);
    var tbody = document.createElement('tbody');
    var frag = document.createDocumentFragment();
    rows.forEach(function (p) {
      var tr = document.createElement('tr');
      tr.appendChild(cell('td', p.program));
      tr.appendChild(cell('td', p.url));
      tr.appendChild(cell('td', p.module));
      tr.appendChild(cell('td', p.table));
      tr.appendChild(cell('td', p.crud, 'v v-' + p.crud.charAt(0)));
      frag.appendChild(tr);
    });
    tbody.appendChild(frag);
    table.appendChild(tbody);
    $('crudCount').textContent = '쌍 ' + rows.length + ' / ' + all.length;
    return table;
  }

  function crudDetail(module, table) {
    var ps = (matrix.longRows || []).filter(function (p) { return p.module === module && p.table === table; });
    var lines = [module + ' · ' + table + ' — 프로그램 ' + ps.length, ''];
    ps.forEach(function (p) { lines.push('  ' + p.program + '  ' + p.url + '  ' + crudWords(p.crud)); });
    $('crudDetail').textContent = lines.join('\n');
  }

  // ------------------------------------------------------------ 미해결

  function kindName(k) { return kinds[k] ? kinds[k].name : k; }

  // 6-22 — 종류 칩. 순서는 KINDS(API 순서), 이 실행에 있는 것만. title 은 영문 코드. 뜻·푸는 법 글은 서버 한 곳(Unresolved.KINDS)
  function renderChips() {
    var count = {};
    unresolved.forEach(function (u) { count[u.kind] = (count[u.kind] || 0) + 1; });
    if (!count[kindSel]) kindSel = '';
    var order = Object.keys(kinds).filter(function (k) { return count[k]; })
      .concat(Object.keys(count).filter(function (k) { return !kinds[k]; }).sort());
    var box = $('kindChips');
    box.innerHTML = '';
    [['', '전체', unresolved.length]].concat(order.map(function (k) { return [k, kindName(k), count[k]]; })).forEach(function (c) {
      var el = document.createElement('span');
      el.className = c[0] === kindSel ? 't on' : 't';
      el.textContent = c[1] + ' ' + c[2];
      el.title = c[0] || '전체';
      el.onclick = function () { kindSel = c[0]; renderChips(); renderUnresolved(); };
      box.appendChild(el);
    });
    var k = kinds[kindSel];
    $('kindHelp').textContent = kindSel ? kindName(kindSel) + ' — ' + (k ? k.meaning + '. 푸는 법: ' + k.fix : '뜻 없음')
      : '종류 칩을 누르면 뜻과 푸는 법';
  }

  function renderUnresolved() {
    var list = unresolved.filter(function (u) { return !kindSel || u.kind === kindSel; });
    var t = TB.table($('unresolved'), ['종류', '파일', '줄', '식별자'], list.map(function (u) { return [kindName(u.kind), u.file, u.line, u.detail || '']; }));
    var trs = t.tBodies[0].rows;
    for (var i = 0; i < trs.length; i++) trs[i].cells[0].title = list[i].kind;
    $('unCount').textContent = list.length + ' / ' + unresolved.length;
  }

  // ------------------------------------------------------------ xlsx(6-7)

  function xlsx() {
    if (runId === null) return;
    // 6-24 — 한 파일. 정합성 탭에서 스냅샷을 골랐으면 정합성 시트도
    var body = { format: 'xlsx' };
    if ($('conSnap').value) body.snapshotId = Number($('conSnap').value);
    TB.api('/api/analyze/runs/' + runId + '/export', { body: body }).then(function (r) {
      msg('xlsx ' + TB.savedText([r.files[0].path], r.dir) + ' · 시트 ' + r.sheets.map(function (s) { return s.name + ' ' + s.rows; }).join(' · '), 'ok');
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
      TB.table($('impact'), ['프로그램', 'verb', 'URL', 'CRUD', 'view', 'JSP'], im.rows.map(function (x) {
        var r = x.program;
        return [progName(r), r.verb, r.url + (r.params ? ' ' + r.params : ''), crudWords((r.crud || {})[im.table]), viewsText(r), x.jsps.length];
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
        // 6-23 — 스냅샷 범위에 걸려 빠진 표는 「범위 밖 — 규칙」. 사유 글은 서버(Scope.reason)
        TB.table($('conMissing'), ['표', '프로그램 수', '사유'], r.missingInDb.map(function (x) { return [x.table, x.programs, x.reason]; }));
        $('conScope').textContent = '스냅샷 범위: ' + (r.scopeSummary === null || r.scopeSummary === undefined ? '기록 없음(옛 스냅샷)' : r.scopeSummary);
        TB.table($('conUnused'), ['스키마', '표', '종류'], r.unusedInCode.map(function (x) { return [x.schema, x.table, x.type || '']; }));
      } else {
        $('conMissing').innerHTML = '';
        $('conMissing').textContent = '스냅샷을 고르면 나온다';
        $('conUnused').innerHTML = '';
        $('conUnused').textContent = '스냅샷을 고르면 나온다';
        $('conScope').textContent = '';
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
    $('crudModeModule').onclick = function () { crudMode = 'module'; renderCrud(); };
    $('crudModeList').onclick = function () { crudMode = 'list'; renderCrud(); };
    $('impRun').onclick = impact;
    $('xlsx').onclick = xlsx;
    $('conRun').onclick = consistency;
    TB.api('/api/analyze/unresolved-kinds').then(function (l) {
      kinds = {};
      l.forEach(function (k) { kinds[k.kind] = k; });
      if (unresolved.length) { renderChips(); renderUnresolved(); }
    }, function () {});
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
