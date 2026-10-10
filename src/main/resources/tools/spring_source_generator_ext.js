/*
 * Table → Spring 소스 생성 화면(7-4, 이름은 7-14 — 옛 CRUD 생성기). ES5 — HtmlUnit 스모크가 읽는다(var·function, 화살표·let 없음).
 * 서버·파일에서 온 값은 textContent 로만 넣는다(innerHTML 은 비우기만).
 * 스냅샷 → 표 고르기 → 세트·패키지·출력 폴더(기본은 활성 프로필 generator) → 생성(job) → 결과 파일 목록·미리보기.
 */
(function () {
  'use strict';

  var tables = [];     // GET /api/meta/snapshots/{id}/tables
  var sel = {};        // 고른 표 — tables 의 번호 → true
  var visible = [];    // 지금 보이는 행 중 고를 수 있는 번호(뷰 빼고)
  var outDir = null;   // 마지막 실행의 출력 폴더 — 미리보기가 읽는다
  var selected = null;

  function $(id) { return document.getElementById(id); }

  /* 1-58e — 입력 줄 바로 아래 결과 칸(TB.result). 형식이 섞여 아이콘 없음(R5), 출력 폴더 하나(R6) */
  function msg(text, state, dir) {
    TB.result('msg', state === 'err' ? 'fail' : (state || 'run'), { summary: text, dir: dir });
  }

  function low(s) { return String(s === null || s === undefined ? '' : s).toLowerCase(); }

  function isView(t) { return low(t.type).indexOf('view') >= 0; }

  function option(sel, value, text) {
    var op = document.createElement('option');
    op.value = value;
    op.textContent = text;
    sel.appendChild(op);
  }

  // ------------------------------------------------------------ 처음 채우기

  function loadSnapshots() {
    TB.api('/api/meta/snapshots').then(function (list) {
      var sel = $('snap');
      sel.innerHTML = '';
      option(sel, '', list && list.length ? '— 고른다' : '— 스냅샷 없음');
      (list || []).forEach(function (s) {
        option(sel, String(s.id), TB.snapLabel(s));
      });
    }, function (e) { msg(e.message, 'err'); });
  }

  function loadSets() {
    return TB.api('/api/generate/templates').then(function (list) {
      var sel = $('set');
      sel.innerHTML = '';
      (list || []).forEach(function (s) {
        option(sel, s.name, s.error ? s.name + ' (오류)' : s.name);
      });
    }, function (e) { msg(e.message, 'err'); });
  }

  function loadDefaults() {
    TB.api('/api/profiles').then(function (p) {
      if (!p.active) return;
      return TB.api('/api/profiles/' + encodeURIComponent(p.active)).then(function (prof) {
        var g = prof.generator || {};
        if (g.basePackage && !$('pkg').value) $('pkg').value = g.basePackage;
        if (g.outDir && !$('out').value) $('out').value = g.outDir;
        if (g.templateSet) $('set').value = g.templateSet;
      });
    }).then(null, function () {});
  }

  // ------------------------------------------------------------ 표

  function loadTables() {
    var id = $('snap').value;
    tables = [];
    sel = {};
    if (!id) { renderTables(); return; }
    TB.api('/api/meta/snapshots/' + id + '/tables').then(function (list) {
      tables = list || [];
      renderTables();
    }, function (e) { msg(e.message, 'err'); });
  }

  function cell(tag, text) {
    var el = document.createElement(tag);
    el.textContent = text;
    return el;
  }

  function renderTables() {
    var q = low($('fT').value.trim());
    var box = $('tables');
    box.innerHTML = '';
    var table = document.createElement('table');
    var thead = document.createElement('thead');
    var hr = document.createElement('tr');
    /* 전체선택 — 찾기가 남긴 행 중 고를 수 있는 것(뷰 빼고)에만 */
    var all = document.createElement('input');
    all.type = 'checkbox';
    all.id = 'all';
    all.title = '전체 선택';
    all.onchange = function () {
      visible.forEach(function (i) { sel[i] = all.checked; });
      renderTables();
    };
    var th0 = cell('th', '');
    th0.appendChild(all);
    hr.appendChild(th0);
    ['표', '코멘트', '컬럼'].forEach(function (h) { hr.appendChild(cell('th', h)); });
    thead.appendChild(hr);
    table.appendChild(thead);
    var tbody = document.createElement('tbody');
    var shown = 0;
    visible = [];
    tables.forEach(function (t, i) {
      if (q && (low(t.name) + ' ' + low(t.comment)).indexOf(q) < 0) return;
      shown++;
      var tr = document.createElement('tr');
      var td = document.createElement('td');
      var cb = document.createElement('input');
      cb.type = 'checkbox';
      cb.id = 'tb_' + i;
      cb.title = t.name;
      cb.setAttribute('data-i', String(i));
      if (isView(t)) {
        cb.disabled = true;
        tr.className = 'view';
      } else {
        visible.push(i);
        cb.checked = !!sel[i];
        cb.onchange = function () { sel[i] = cb.checked; syncAll(); };
      }
      td.appendChild(cb);
      tr.appendChild(td);
      tr.appendChild(cell('td', (t.schema ? t.schema + '.' : '') + t.name + (isView(t) ? ' (뷰)' : '')));
      tr.appendChild(cell('td', t.comment || ''));
      tr.appendChild(cell('td', String(t.columnCount)));
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    box.appendChild(table);
    syncAll();
  }

  /* 머리 체크 상태(전부·일부·없음)와 건수 — 고른 것은 찾기로 숨어도 남는다 */
  function syncAll() {
    var all = $('all');
    var on = visible.filter(function (i) { return sel[i]; }).length;
    all.disabled = visible.length === 0;
    all.checked = visible.length > 0 && on === visible.length;
    all.indeterminate = on > 0 && on < visible.length;
    var shown = $('tables').querySelectorAll('tbody tr').length;
    $('tCount').textContent = shown + ' / ' + tables.length + ' · 고름 ' + checked().length;
  }

  function checked() {
    var out = [];
    tables.forEach(function (t, i) {
      if (sel[i] && !isView(t)) out.push({ schema: t.schema, name: t.name });
    });
    return out;
  }

  // ------------------------------------------------------------ 생성

  function run() {
    var snap = $('snap').value;
    var picked = checked();
    if (!snap) { msg('스냅샷을 고른다', 'err'); return; }
    if (!picked.length) { msg('표를 하나 이상 고른다', 'err'); return; }
    var body = { snapshotId: Number(snap), tables: picked, templateSet: $('set').value,
      basePackage: $('pkg').value.trim(), module: $('module').value.trim(), outDir: $('out').value.trim() };
    $('run').disabled = true;
    msg('생성 시작');
    TB.api('/api/generate', { body: body }).then(function (r) { poll(r.jobId); },
      function (e) { $('run').disabled = false; msg(e.message, 'err'); });
  }

  function poll(jobId) {
    TB.api('/api/jobs/' + jobId).then(function (j) {
      if (j.status === 'QUEUED' || j.status === 'RUNNING') {
        msg('생성 중 ' + (j.progress || 0) + '% ' + (j.message || ''));
        setTimeout(function () { poll(jobId); }, 400);
        return;
      }
      $('run').disabled = false;
      if (j.status !== 'DONE') { msg(j.status + ' ' + (j.message || ''), 'err'); return; }
      show(j.result);
    }, function (e) { $('run').disabled = false; msg(e.message, 'err'); });
  }

  function show(r) {
    outDir = r.outDir;
    selected = null;
    var t = TB.table($('files'), ['파일', '표', '상태'], r.files.map(function (f) { return [f.rel, f.table, f.status]; }));
    var trs = t.tBodies[0].rows;
    for (var i = 0; i < trs.length; i++) bindRow(trs[i], r.files[i]);
    $('warn').textContent = r.warnings.length ? '경고\n' + r.warnings.join('\n') : '';
    msg('새 파일 ' + r.created + ' · 옆에 .gen ' + r.sidecar, 'ok', r.outDir);
  }

  function bindRow(tr, f) {
    tr.onclick = function () {
      if (selected) selected.className = '';
      selected = tr;
      tr.className = 'sel';
      var sep = outDir.indexOf('\\') >= 0 ? '\\' : '/';
      TB.api('/api/fs/read?path=' + encodeURIComponent(outDir + sep + f.rel.split('/').join(sep))).then(function (x) {
        $('preview').textContent = x.text;
      }, function (e) { $('preview').textContent = e.message; });
    };
  }

  function init() {
    if (!window.TB) return;
    $('snap').onchange = loadTables;
    $('fT').oninput = renderTables;
    $('run').onclick = run;
    loadSnapshots();
    loadSets().then(loadDefaults);
    renderTables();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
