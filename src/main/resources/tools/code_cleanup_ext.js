/*
 * 코드 정리 화면(5-34e, 설계 30). ES5 — HtmlUnit 스모크가 읽는다(var·function, 화살표·let 없음).
 * 서버·파일에서 온 값은 textContent 로만 넣는다(innerHTML 은 비우기만).
 * 미리보기(쓰기 없음) → 체크 → 결과 폴더에 쓰기(9-3d — 원본은 그대로). 쓰기는 서버가 파일을 다시 읽어 미리보기 때와 같을 때만 한다.
 */
(function () {
  'use strict';

  var STAGE = { 'delete': '삭제', marker: '표식', order: '선언 순서', eol: '줄바꿈' };
  var RULES = [];   // GET /api/cleanup/rules
  var ROWS = [];    // 미리보기 결과 줄 + checked·done·result
  var ROOT = null;  // 미리보기한 폴더 — 쓰기는 이 폴더의 파일을 고친 판
  var PREVIEW_RULES = []; // 미리보기한 규칙 — 쓰기는 이 규칙(뒤에 체크를 바꿔도 미리보기와 같게)
  var jobNow = null;
  var busy = false;
  var vcsSeq = 0;

  function $(id) { return document.getElementById(id); }

  /* 결과 칸은 이 한 자리(1-58 R1·R4) */
  function msg(state, summary, extra) {
    var o = extra || {};
    o.summary = summary;
    TB.result('res', state, o);
  }

  // ------------------------------------------------------------ 규칙

  function loadRules() {
    TB.api('/api/cleanup/rules').then(function (list) {
      RULES = list;
      var box = $('rules');
      box.innerHTML = '';
      list.forEach(function (r) {
        var lb = document.createElement('label');
        var cb = document.createElement('input');
        cb.type = 'checkbox';
        cb.id = 'r_' + r.id;
        cb.checked = true;
        lb.title = r.fix || '';
        if (!r.enabled) {
          cb.checked = false;
          cb.disabled = true;
          lb.title = '프로필에서 꺼짐';
        } else if (r.id === 'file.lineEnding' && !r.expected) {
          cb.checked = false;
          cb.disabled = true;
          lb.title = '프로필 project.lineEnding 없음';
        }
        lb.appendChild(cb);
        var id = document.createElement('span');
        id.textContent = r.id + (r.expected ? ' → ' + r.expected : '');
        lb.appendChild(id);
        var m = document.createElement('span');
        m.className = 'msg';
        m.textContent = r.message;
        lb.appendChild(m);
        box.appendChild(lb);
      });
    }, function (e) { msg('fail', e.message); });
  }

  function chosen() {
    var out = [];
    RULES.forEach(function (r) {
      var cb = $('r_' + r.id);
      if (cb && cb.checked && !cb.disabled) out.push(r.id);
    });
    return out;
  }

  // ------------------------------------------------------------ 폴더·변경분(코드 검사와 같은 꼴)

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
      if (v.available) {
        cb.disabled = false;
        info.textContent = v.kind + ' · 변경 ' + v.changed;
        $('changedLabel').title = v.kind + ' 작업 사본의 바뀐 파일만 고친다';
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

  // ------------------------------------------------------------ 작업

  function setBusy(b) {
    busy = b;
    $('preview').disabled = b;
    $('stop').disabled = !b;
    if (b) $('apply').disabled = true;
  }

  function poll(id, label, onDone) {
    TB.api('/api/jobs/' + id).then(function (j) {
      if (j.status === 'QUEUED' || j.status === 'RUNNING') {
        msg('run', label + ' ' + (j.progress || 0) + '% ' + (j.message || ''));
        setTimeout(function () { poll(id, label, onDone); }, 400);
        return;
      }
      jobNow = null;
      setBusy(false);
      if (j.status === 'CANCELLED') { onDone(null, true); sum(); return; }
      if (j.status !== 'DONE') { msg('fail', j.status + ' ' + (j.message || '')); sum(); return; }
      onDone(j.result, false);
    }, function (e) { jobNow = null; setBusy(false); msg('fail', e.message); sum(); });
  }

  function start(url, body, label, onDone) {
    setBusy(true);
    msg('run', label + ' 시작');
    TB.api(url, { body: body }).then(function (r) {
      jobNow = r.jobId;
      poll(r.jobId, label, onDone);
    }, function (e) { setBusy(false); msg('fail', e.message); sum(); });
  }

  /* 중지 — 미리보기는 결과 없이, 쓰기는 쓴 것은 그대로(4-20). 중지는 노랑(1-58 R12) */
  function stop() {
    if (!jobNow) return;
    $('stop').disabled = true;
    msg('run', '중지하는 중…');
    TB.api('/api/jobs/' + jobNow, { method: 'DELETE' }).then(null, function (e) { msg('fail', e.message); });
  }

  function preview() {
    var dir = $('dir').value.trim();
    if (!dir) { msg('fail', '폴더 경로를 넣는다'); return; }
    var rules = chosen();
    if (!rules.length) { msg('fail', '고칠 규칙을 하나 이상 고른다'); return; }
    // 앞 미리보기 줄은 지운다 — 새 미리보기가 중지·실패해도 옛 폴더 줄이 새 폴더에 덮어써지지 않게
    ROWS = [];
    render();
    ROOT = dir;
    PREVIEW_RULES = rules;
    var only = $('changed').checked && !$('changed').disabled;
    start('/api/cleanup/preview', { path: dir, rules: rules, changedOnly: only }, only ? '변경분 미리보기' : '미리보기', function (o, stopped) {
      if (stopped) { msg('stop', '미리보기 중지 — 결과 없음'); return; }
      ROWS = o.rows || [];
      ROWS.forEach(function (r) { r.checked = r.changed; });
      render();
      msg('ok', '파일 ' + o.files + ' · 바뀜 ' + o.changed + (o.skipped ? ' · 못 읽음 ' + o.skipped : '')
        + (o.truncated ? ' · 목록 상한에 걸림' : ''), { dir: dir });
    });
  }

  function apply() {
    var list = todo();
    if (!list.length) return;
    var files = list.map(function (r) { return { rel: r.rel, hash: r.hash, outHash: r.outHash }; });
    var body = { path: ROOT, rules: PREVIEW_RULES, files: files };
    var out = $('out').value.trim();
    if (out) body.outDir = out;
    start('/api/cleanup/apply', body, '쓰기', function (o, stopped) {
      if (stopped) { msg('stop', '쓰기 중지 — 쓴 것은 그대로(나머지는 안 씀)', { dir: ROOT }); return; }
      var by = {};
      (o.rows || []).forEach(function (w) { by[w.rel] = w; });
      list.forEach(function (r) {
        var w = by[r.rel];
        if (!w) return;
        r.done = true;
        r.checked = false;
        r.result = w.ok ? '썼다' : (w.reason || '실패');
      });
      render();
      var bad = o.failed + o.stale;
      msg(bad ? 'stop' : 'ok', '썼다 ' + o.written + '/' + list.length + '개' + (o.failed ? ' · 실패 ' + o.failed : '')
        + (o.stale ? ' · 낡음 ' + o.stale + '(다시 미리보기)' : ''), { dir: ROOT, out: o.outRoot });
    });
  }

  // ------------------------------------------------------------ 결과 표

  function stages(r) {
    var seen = {};
    var out = [];
    (r.steps || []).forEach(function (s) {
      if (seen[s.stage]) return;
      seen[s.stage] = 1;
      out.push(STAGE[s.stage] || s.stage);
    });
    return out.join(', ');
  }

  function skips(r) {
    return (r.skips || []).map(function (k) { return (STAGE[k.stage] || k.stage) + ' — ' + k.reason; }).join(' / ');
  }

  function todo() {
    return ROWS.filter(function (r) { return r.checked && r.changed && !r.done; });
  }

  function render() {
    var tb = $('rows').tBodies[0];
    tb.innerHTML = '';
    ROWS.forEach(function (r) {
      var tr = tb.insertRow();
      if (r.done) tr.className = 'done';
      var cb = document.createElement('input');
      cb.type = 'checkbox';
      cb.checked = !!r.checked;
      cb.disabled = !r.changed || !!r.done;
      cb.setAttribute('aria-label', r.rel);
      cb.onchange = function () { r.checked = cb.checked; sum(); };
      tr.insertCell().appendChild(cb);
      tr.insertCell().textContent = r.rel;
      var n = tr.insertCell();
      n.className = 'n';
      n.textContent = r.before + ' → ' + r.after;
      tr.insertCell().textContent = r.done ? (r.result || '') : stages(r);
      var s = tr.insertCell();
      s.className = 'skip';
      s.textContent = skips(r);
    });
    sum();
  }

  function sum() {
    var changed = 0;
    var skipped = 0;
    ROWS.forEach(function (r) {
      if (r.changed) changed++;
      if ((r.skips || []).length) skipped++;
    });
    var t = todo().length;
    $('sum').textContent = '파일 ' + ROWS.length + ' · 바뀜 ' + changed + ' · 건너뜀 있음 ' + skipped + ' · 쓸 대상 ' + t;
    $('apply').disabled = busy || t === 0;
    var able = ROWS.filter(function (r) { return r.changed && !r.done; });
    var on = able.filter(function (r) { return r.checked; }).length;
    $('all').checked = able.length > 0 && on === able.length;
    $('all').indeterminate = on > 0 && on < able.length;
  }

  function all() {
    var v = $('all').checked;
    ROWS.forEach(function (r) { if (r.changed && !r.done) r.checked = v; });
    render();
  }

  function init() {
    if (!window.TB) return;
    $('preview').onclick = preview;
    $('apply').onclick = apply;
    $('stop').onclick = stop;
    $('all').onchange = all;
    $('dir').onchange = vcsInfo;
    loadRules();
    loadRecentDirs();
    sum();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
