/*
 * dev_tools 백엔드본 탭(4-9) — 「폴더 비교」(4-2) · 「로그 SQL 복원」(4-8) · INSERT 탭 「스냅샷/접속에서」(4-7)
 * + 화면 표시기 「폴더 일괄」 주석 삭제(4-3).
 * dev_tools.html 이 커서 여기로 뺐다(4-9 실패 사다리). common.js 다음에 defer 로 읽는다.
 * ES5 만, 서버 값은 전부 textContent(0-31). 파일 내용·로그·SQL 은 서버에 남지 않는다(절대 규칙 3).
 */
(function () {
  'use strict';

  function $(id) { return document.getElementById(id); }
  function msg(id, s, err) { var m = $(id); m.textContent = s; m.className = 'ext-msg' + (err ? ' err' : ''); }
  function cell(tr, text, cls) {
    var td = document.createElement('td');
    td.textContent = text === null || text === undefined ? '' : String(text);
    if (cls) td.className = cls;
    tr.appendChild(td);
    return td;
  }
  function head(table, names) {
    var tr = document.createElement('tr');
    names.forEach(function (n) { var th = document.createElement('th'); th.textContent = n; tr.appendChild(th); });
    table.appendChild(tr);
  }
  function copy(text) {
    if (navigator.clipboard && navigator.clipboard.writeText) { navigator.clipboard.writeText(text); return; }
    var ta = document.createElement('textarea');
    ta.value = text; document.body.appendChild(ta); ta.select();
    try { document.execCommand('copy'); } catch (e) { /* 복사 실패 — 사용자가 직접 고른다 */ }
    document.body.removeChild(ta);
  }
  function joinPath(root, rel) {
    var sep = root.indexOf('\\') >= 0 ? '\\' : '/';
    return root.replace(/[\\\/]+$/, '') + sep + (sep === '\\' ? rel.replace(/\//g, '\\') : rel);
  }

  /* ---------------- 폴더 비교 ---------------- */
  var FD = { items: [], a: '', b: '' };
  var LABEL = { SAME: '같음', DIFF: '다름', ONLY_A: 'A 만', ONLY_B: 'B 만' };

  function fdRun() {
    var a = $('fd_a').value.trim(), b = $('fd_b').value.trim();
    if (!a || !b) { msg('fd_msg', '두 폴더를 넣을 것', true); return; }
    msg('fd_msg', '비교 중…');
    TB.api('/api/diff/folders', { body: { a: a, b: b, glob: $('fd_glob').value.trim() } }).then(function (r) {
      FD = { items: r.items, a: a, b: b };
      var n = { SAME: 0, DIFF: 0, ONLY_A: 0, ONLY_B: 0 };
      r.items.forEach(function (it) { n[it.status]++; });
      msg('fd_msg', '다름 ' + n.DIFF + ' · A 만 ' + n.ONLY_A + ' · B 만 ' + n.ONLY_B + ' · 같음 ' + n.SAME
        + (r.truncated ? ' (목록 상한에서 끊김)' : ''));
      fdList();
    }, function (e) { msg('fd_msg', '실패: ' + e.message, true); });
  }
  function fdList() {
    var box = $('fd_list'), hide = $('fd_hide_same').checked;
    var t = document.createElement('table');
    head(t, ['상태', '파일', 'A', 'B']);
    FD.items.forEach(function (it) {
      if (hide && it.status === 'SAME') return;
      var tr = document.createElement('tr');
      cell(tr, LABEL[it.status], 'st-' + it.status);
      cell(tr, it.rel);
      cell(tr, it.sizeA);
      cell(tr, it.sizeB);
      if (it.status === 'DIFF') {
        tr.className = 'link';
        tr.onclick = function () { fdFile(it.rel); };
      }
      t.appendChild(tr);
    });
    box.innerHTML = '';
    box.appendChild(t);
  }
  function fdFile(rel) {
    $('fd_file_label').textContent = '줄 비교 — ' + rel;
    var q = '?a=' + encodeURIComponent(joinPath(FD.a, rel)) + '&b=' + encodeURIComponent(joinPath(FD.b, rel));
    TB.api('/api/diff/file' + q).then(function (r) {
      var box = $('fd_file');
      box.innerHTML = '';
      if (r.tooBig) {
        var p = document.createElement('div'); p.className = 'card';
        p.textContent = '파일이 커서 줄 비교를 건너뛴다 — 편집기 비교 도구로 볼 것';
        box.appendChild(p);
        return;
      }
      var t = document.createElement('table');
      head(t, ['', 'A · ' + r.encodingA, '', 'B · ' + r.encodingB]);
      // 붙어 있는 - 묶음과 + 묶음을 한 줄씩 맞붙여 두 칸으로
      var i = 0, la = 0, lb = 0, ops = r.ops;
      while (i < ops.length) {
        if (ops[i].op === '=') {
          la++; lb++;
          row(t, la, ops[i].line, '', lb, ops[i].line, '');
          i++;
          continue;
        }
        var del = [], add = [];
        while (i < ops.length && ops[i].op === '-') { del.push(ops[i].line); i++; }
        while (i < ops.length && ops[i].op === '+') { add.push(ops[i].line); i++; }
        for (var k = 0; k < Math.max(del.length, add.length); k++) {
          var hasA = k < del.length, hasB = k < add.length;
          row(t, hasA ? ++la : '', hasA ? del[k] : '', hasA ? 'del' : '', hasB ? ++lb : '', hasB ? add[k] : '', hasB ? 'add' : '');
        }
      }
      box.appendChild(t);
    }, function (e) { $('fd_file').textContent = '실패: ' + e.message; });
  }
  function row(t, na, a, ca, nb, b, cb) {
    var tr = document.createElement('tr');
    cell(tr, na, 'ln'); cell(tr, a, ca); cell(tr, nb, 'ln'); cell(tr, b, cb);
    t.appendChild(tr);
  }

  /* ---------------- 로그 SQL 복원 ---------------- */
  var LS = [];
  function lsRun() {
    var text = $('ls_in').value;
    if (!text.trim()) { msg('ls_msg', '로그를 붙일 것', true); return; }
    TB.api('/api/text/logsql', { body: { text: text } }).then(function (r) {
      LS = r.items;
      var box = $('ls_out');
      box.innerHTML = '';
      var warned = 0;
      LS.forEach(function (it, idx) {
        var c = document.createElement('div'); c.className = 'card';
        var btn = document.createElement('button'); btn.className = 'btn-g'; btn.textContent = '복사 ' + (idx + 1);
        btn.onclick = function () { copy(it.restored); };
        c.appendChild(btn);
        var pre = document.createElement('pre'); pre.textContent = it.restored; c.appendChild(pre);
        if (it.warning) {
          warned++;
          var w = document.createElement('div'); w.className = 'w'; w.textContent = '⚠ ' + it.warning; c.appendChild(w);
        }
        box.appendChild(c);
      });
      msg('ls_msg', '문장 ' + LS.length + (warned ? ' · 원문 그대로 ' + warned : ''));
    }, function (e) { msg('ls_msg', '실패: ' + e.message, true); });
  }
  function lsCopyAll() {
    if (!LS.length) return;
    copy(LS.map(function (it) { return it.restored + ';'; }).join('\n\n'));
    msg('ls_msg', '전부 복사함 — 문장 ' + LS.length);
  }

  /* ---------------- INSERT 「스냅샷/접속에서」 ---------------- */
  function insLoad() {
    TB.api('/api/meta/snapshots').then(function (list) {
      var sel = $('ins_snap');
      (list || []).forEach(function (s) {
        var op = document.createElement('option');
        op.value = s.id;
        op.textContent = TB.snapLabel(s);
        sel.appendChild(op);
      });
    }, function () { /* 스냅샷이 없으면 붙여넣기 모드만 */ });
    TB.api('/api/conn').then(function (list) {
      var sel = $('ins_conn');
      (list || []).forEach(function (c) {
        var op = document.createElement('option');
        op.value = c.id;
        op.textContent = c.id + ' (' + c.dialect + ')';
        sel.appendChild(op);
      });
    }, function () {});
  }
  function insTables() {
    var id = $('ins_snap').value, dl = $('ins_tables');
    dl.innerHTML = '';
    if (!id) return;
    TB.api('/api/meta/snapshots/' + encodeURIComponent(id) + '/tables').then(function (list) {
      (list || []).forEach(function (t) {
        var op = document.createElement('option');
        op.value = typeof t === 'string' ? t : t.name;
        dl.appendChild(op);
      });
    }, function () {});
  }
  function insRun() {
    var snap = $('ins_snap').value, table = $('ins_table').value.trim();
    if (!snap || !table) { msg('ins_srv_msg', '스냅샷과 테이블을 고를 것', true); return; }
    var body = {
      snapshotId: +snap, table: table, dialect: $('ins_dialect').value,
      rows: +$('dummy_count').value || 5, upsert: $('chk_merge').checked, commit: $('chk_commit').checked
    };
    if ($('ins_conn').value) body.connId = $('ins_conn').value;
    if ($('ins_csv').checked) body.csv = $('create_input').value;
    msg('ins_srv_msg', '생성 중…');
    TB.api('/api/insert/generate', { body: body }).then(function (r) {
      $('dummy_out').value = r.sql;
      $('ins_warn').textContent = r.warnings.map(function (w) { return '⚠ ' + w; }).join('\n');
      msg('ins_srv_msg', '생성함' + (r.warnings.length ? ' · 경고 ' + r.warnings.length : ''));
    }, function (e) { msg('ins_srv_msg', '실패: ' + e.message, true); });
  }

  /* ---------------- 주석 삭제 폴더 일괄(4-3) ---------------- */
  // 언어(화면 td_lang 값) → 파일 glob. 주석 규칙은 화면의 cmtStrip 그대로(window.TB_CMT) — 서버에 파서 없음(2.3 결정 F2 ①)
  var GLOBS = {
    java: '*.java', js: '*.js,*.ts', jsx: '*.jsx,*.tsx', sql: '*.sql', mysql: '*.sql', xml: '*.xml,*.html',
    jsp: '*.jsp', mybatis: '*Mapper*.xml,*_SQL.xml', css: '*.css', less: '*.less,*.scss', props: '*.properties',
    sh: '*.sh', yaml: '*.yml,*.yaml', bat: '*.bat,*.cmd'
  };
  var CHUNK = 50;
  var SB = { root: '', lang: '', rows: [], stamp: null };

  function sbMsg(s) { $('td_batchMsg').textContent = s; }
  function sbOpts() { return { dropLine: true, squeeze: true, trimEnd: true, killHint: $('td_killHint').checked }; }

  function sbPreview() {
    var root = $('td_dir').value.trim(), lang = $('td_lang').value;
    if (!root) { sbMsg('폴더 경로를 넣을 것'); return; }
    if (!window.TB_CMT) { sbMsg('주석 삭제 규칙을 못 찾았다'); return; }
    SB = { root: root, lang: lang, rows: [], stamp: null };
    $('td_batchApply').disabled = true;
    $('td_batchOut').innerHTML = '';
    sbMsg('목록 읽는 중… (' + GLOBS[lang] + ')');
    TB.api('/api/fs/list?path=' + encodeURIComponent(root) + '&glob=' + encodeURIComponent(GLOBS[lang])).then(function (l) {
      var files = l.files, i = 0, opts = sbOpts();
      function next() {
        if (i >= files.length) {
          var n = SB.rows.filter(function (r) { return r.out !== undefined; }).length;
          sbRender();
          sbMsg('미리보기 ' + files.length + '개 · 바뀌는 것 ' + n + '개' + (l.truncated ? ' (목록 상한에서 끊김)' : '') + ' — ' + TB_CMT.label[lang]);
          $('td_batchApply').disabled = n === 0;
          return;
        }
        var f = files[i++];
        var path = joinPath(root, f.rel);
        TB.api('/api/fs/read?path=' + encodeURIComponent(path)).then(function (t) {
          var row = { rel: f.rel, path: path, encoding: t.encoding, lineEnding: t.lineEnding, before: t.text.length };
          try {
            var r = TB_CMT.strip(t.text, lang, opts);
            row.removed = r.removed;
            if (r.removed) { row.out = r.text; row.after = r.text.length; }
          } catch (e) { row.err = '삭제 실패: ' + e.message; }
          SB.rows.push(row);
          if (i % CHUNK === 0 || i === files.length) sbMsg('미리보기 ' + i + '/' + files.length);
          next();
        }, function (e) { SB.rows.push({ rel: f.rel, err: '읽기 실패: ' + e.message }); next(); });
      }
      next();
    }, function (e) { sbMsg('목록 실패: ' + e.message); });
  }

  function sbRender() {
    var t = document.createElement('table');
    t.id = 'td_batchTable';
    head(t, ['파일', '삭제', '전→후 자', '결과']);
    SB.rows.forEach(function (r) {
      if (!r.err && !r.removed) return; // 지울 것 없는 파일은 표에서 뺀다(수는 문구에)
      var tr = document.createElement('tr');
      cell(tr, r.rel);
      cell(tr, r.err ? '' : r.removed);
      cell(tr, r.err ? '' : r.before + '→' + r.after);
      cell(tr, r.err || r.result || '', r.err ? 'del' : '');
      t.appendChild(tr);
    });
    $('td_batchOut').innerHTML = '';
    $('td_batchOut').appendChild(t);
  }

  function sbApply() {
    var todo = SB.rows.filter(function (r) { return r.out !== undefined && !r.done && !r.err; });
    if (!todo.length) return;
    $('td_batchApply').disabled = true;
    var i = 0, ok = 0;
    function next() {
      if (i >= todo.length) {
        sbRender();
        sbMsg('적용 ' + ok + '/' + todo.length + '개 · 백업 stamp ' + (SB.stamp || '-'));
        return;
      }
      var r = todo[i++];
      TB.api('/api/fs/write', { body: { path: r.path, root: SB.root, text: r.out, encoding: r.encoding,
          lineEnding: r.lineEnding, stamp: SB.stamp } }).then(function (res) {
        SB.stamp = res.stamp; r.done = true; r.result = '백업 ' + res.backup; ok++;
        if (i % CHUNK === 0 || i === todo.length) sbMsg('적용 ' + i + '/' + todo.length);
        next();
      }, function (e) { r.err = '쓰기 실패: ' + e.message; next(); });
    }
    next();
  }

  function init() {
    if (!window.TB) return; // 순수본처럼 서버 없이 열린 경우 — 새 탭은 쓸 수 없다
    $('fd_run').onclick = fdRun;
    $('fd_hide_same').onchange = fdList;
    $('ls_run').onclick = lsRun;
    $('ls_copy_all').onclick = lsCopyAll;
    $('ins_snap').onchange = insTables;
    $('ins_srv_btn').onclick = insRun;
    $('td_batchPreview').onclick = sbPreview;
    $('td_batchApply').onclick = sbApply;
    insLoad();
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
