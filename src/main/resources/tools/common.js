/*
 * 백엔드본 공통 — 도구 HTML 은 <script src="/tools/common.js" defer></script> 한 줄만 넣는다.
 * window.TB = { api, badge, table, sse, snapLabel, joinPath, savedText }. 로드되면 스스로 badge() 를 건다.
 * HtmlUnit(Rhino) 스모크가 읽도록 fetch·async·옵셔널 체이닝을 안 쓴다 — XHR + Promise.
 * 색은 도구 :root 토큰(--surface --border --text --muted --accent). special_chars 처럼 이름이 다른 도구(--card --ink --line --sub)와
 * 토큰이 없는 페이지를 위해 대체값을 이중으로 둔다(2026-09-27 리뷰 — 배지 배경이 투명해졌다).
 */
(function () {
  'use strict';

  function api(path, opts) {
    opts = opts || {};
    var method = opts.method || (opts.body === undefined ? 'GET' : 'POST');
    return new Promise(function (resolve, reject) {
      var xhr;
      try {
        xhr = new XMLHttpRequest();
        xhr.open(method, path, true);
        xhr.setRequestHeader('Accept', 'application/json');
        if (opts.body !== undefined) xhr.setRequestHeader('Content-Type', 'application/json');
      } catch (e) {
        reject({ status: 0, message: String(e && e.message ? e.message : e) });
        return;
      }
      xhr.onload = function () {
        var data = null;
        var text = xhr.responseText;
        if (text) {
          try { data = JSON.parse(text); } catch (e) { data = text; }
        }
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve(data);
        } else {
          var msg = data && data.message ? data.message : (data && data.title ? data.title : String(text || xhr.statusText));
          reject({ status: xhr.status, message: msg });
        }
      };
      xhr.onerror = function () { reject({ status: 0, message: '서버에 연결하지 못했다' }); };
      try {
        xhr.send(opts.body === undefined ? null : JSON.stringify(opts.body));
      } catch (e) {
        reject({ status: 0, message: String(e && e.message ? e.message : e) });
      }
    });
  }

  var BADGE_ID = 'tb-mode-badge';

  function injectStyle() {
    if (document.getElementById('tb-common-style')) return;
    var st = document.createElement('style');
    st.id = 'tb-common-style';
    st.textContent =
      '#' + BADGE_ID + '{position:fixed;bottom:6px;right:8px;z-index:9999;padding:3px 8px;font-size:11px;' +
      "font-family:'Consolas','D2Coding',monospace;background:var(--surface,var(--card,#252526));color:var(--text,var(--ink,#d4d4d4));" +
      'border:1px solid var(--border,var(--line,#3c3c3c));border-radius:3px;opacity:.9;pointer-events:none;letter-spacing:.5px}' +
      '#' + BADGE_ID + '.on{border-color:var(--accent,#0078d4)}' +
      '#' + BADGE_ID + '.off{color:var(--muted,var(--sub,#858585))}' +
      '#' + BADGE_ID + '.down{border-color:#f44747;color:#f44747;background:#3a1d1d;opacity:1}' +
      '.tb-table{border-collapse:collapse;font-size:12px;width:100%}' +
      '.tb-table th,.tb-table td{border:1px solid var(--border,var(--line,#3c3c3c));padding:3px 6px;text-align:left;white-space:nowrap}' +
      '.tb-table th{background:var(--surface,var(--card,#252526));color:var(--muted,var(--sub,#858585));position:sticky;top:0}' +
      '.tb-table td.tb-null{color:var(--muted,var(--sub,#858585))}';
    (document.head || document.documentElement).appendChild(st);
  }

  /* state: 'on'(서버 붙음) · 'down'(붙었다가 끊김, 빨강) · 'off'(순수 — 서버 없이 연 화면) */
  function setBadge(text, state) {
    injectStyle();
    var el = document.getElementById(BADGE_ID);
    if (!el) {
      el = document.createElement('div');
      el.id = BADGE_ID;
      document.body.appendChild(el);
    }
    el.textContent = text;
    el.className = state;
    return el;
  }

  function onText(profile) {
    return '백엔드 연결' + (profile ? ' · ' + profile : '');
  }

  /*
   * 실시간 — /api/alive(SSE)를 붙여 둔다. 서버가 죽으면 error 로 바로 빨강, 다시 뜨면 EventSource 가 스스로 붙어 alive 가 오면 원래대로.
   * alive 데이터는 활성 프로필 이름이라 다른 탭에서 바꾼 프로필도 따라온다. EventSource 가 없는 브라우저(HtmlUnit)는 처음 ping 만.
   * 숨은 탭은 닫는다(0-50)
   */
  var live = null;
  var backend = false; // ping 이 됐다 — 다시 보일 때 붙을지
  function watch() {
    if (live || typeof EventSource === 'undefined' || document.visibilityState === 'hidden') return;
    live = new EventSource('/api/alive');
    live.addEventListener('alive', function (ev) { setBadge(onText(ev.data), 'on'); });
    live.onerror = function () {
      setBadge('백엔드 끊김 — 서버가 응답하지 않는다', 'down');
      if (live.readyState === 2) { /* 브라우저가 다시 붙기를 그만뒀다 — 새로 연다 */
        live = null;
        setTimeout(watch, 3000);
      }
    };
  }

  /* 0-50 — 브라우저는 한 서버에 연결 6개까지만 연다. 숨은 탭이 배지 연결을 쥐고 있으면 보이는 탭의 중지·조회가 줄을 선다 */
  function onVisibility() {
    if (document.visibilityState === 'hidden') {
      if (live) { live.close(); live = null; }
    } else if (backend) {
      watch();
    }
  }

  function badge() {
    if (location.protocol === 'file:') {
      setBadge('순수', 'off');
      return Promise.resolve(null);
    }
    return api('/api/ping').then(function (p) {
      setBadge(onText(p && p.profile), 'on');
      backend = true;
      watch();
      return p;
    }, function () {
      setBadge('순수', 'off');
      return null;
    });
  }

  /* columns: ['이름', ...] 또는 [{key, label}], rows: 배열의 배열 또는 객체 배열 */
  function table(el, columns, rows) {
    injectStyle();
    var cols = (columns || []).map(function (c, i) {
      return typeof c === 'string' ? { key: i, label: c } : { key: c.key !== undefined ? c.key : i, label: c.label || String(c.key) };
    });
    var t = document.createElement('table');
    t.className = 'tb-table';
    var thead = document.createElement('thead');
    var hr = document.createElement('tr');
    cols.forEach(function (c) {
      var th = document.createElement('th');
      th.textContent = c.label;
      hr.appendChild(th);
    });
    thead.appendChild(hr);
    t.appendChild(thead);
    var tbody = document.createElement('tbody');
    (rows || []).forEach(function (r, ri) {
      var tr = document.createElement('tr');
      cols.forEach(function (c, ci) {
        var v = Array.isArray(r) ? r[typeof c.key === 'number' ? c.key : ci] : r[c.key];
        var td = document.createElement('td');
        if (v === null || v === undefined) {
          td.textContent = 'NULL';
          td.className = 'tb-null';
        } else {
          td.textContent = typeof v === 'object' ? JSON.stringify(v) : String(v);
        }
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
    t.appendChild(tbody);
    el.innerHTML = '';
    el.appendChild(t);
    return t;
  }

  /* onEvent(name, data). 반환값의 close() 로 닫는다. 끝 이벤트(done·failed·cancelled)를 받으면 스스로 닫는다 */
  function sse(url, onEvent) {
    if (typeof EventSource === 'undefined') {
      onEvent('failed', { message: '이 브라우저는 EventSource 를 모른다' });
      return { close: function () {} };
    }
    var es = new EventSource(url);
    var names = ['progress', 'log', 'result', 'done', 'failed', 'cancelled', 'message'];
    names.forEach(function (n) {
      es.addEventListener(n, function (ev) {
        var data = ev.data;
        try { data = JSON.parse(ev.data); } catch (e) { /* 문자열 그대로 */ }
        onEvent(n, data);
        if (n === 'done' || n === 'failed' || n === 'cancelled') es.close();
      });
    });
    es.onerror = function () {
      if (es.readyState === 2) onEvent('failed', { message: '연결이 끊겼다' });
    };
    return es;
  }

  /*
   * 스냅샷 select 라벨 한 자리(1-17) — 「#id 접속 시각 · 테이블 n — 메모」 + 걸렀으면 「 · 거름」, 벤더 SQL 이 물러섰으면 「 · 경고」.
   * 값은 option.textContent 로 넣는다(innerHTML 금지, 5장 #10)
   */
  function snapLabel(s) {
    var when = String(s.takenAt || '').replace('T', ' ').slice(0, 16);
    return '#' + s.id + ' ' + (s.connId || '') + (when ? ' ' + when : '') + ' · 테이블 ' + s.tableCount
      + (s.note ? ' — ' + s.note : '') + (s.filtered ? ' · 거름' : '') + (s.warningCount ? ' · 경고' : '');
  }

  /* 폴더 + 상대 이름 → 전체 경로. 구분자는 폴더 글을 따른다(윈도는 \) */
  function joinPath(dir, name) {
    var sep = String(dir).indexOf('\\') >= 0 ? '\\' : '/';
    return String(dir).replace(/[\\\/]+$/, '') + sep + String(name).split(/[\\\/]/).join(sep);
  }

  /* 저장 알림 한 꼴 — 파일 하나면 이름까지 전체 경로 「저장 C:\…\a.sql」, 여럿이면 폴더 「저장 n개 — C:\…\폴더」(dir 이 없으면 첫 파일의 폴더) */
  function savedText(paths, dir) {
    var list = (paths || []).filter(function (p) { return p; });
    if (!list.length) return '';
    if (list.length === 1) return '저장 ' + list[0];
    var folder = dir || String(list[0]).replace(/[\\\/][^\\\/]*$/, '');
    return '저장 ' + list.length + '개 — ' + folder;
  }

  /*
   * 복사(1-35) — 원천을 눈에 보이게 고른 뒤 클립보드에 넣고 알린다. src: 요소(textarea·input 은 value, 그 밖은 textContent)
   * 또는 { text, el, label }(el 이 없으면 고를 칸이 없어 글만 알린다). notify(text, ok) 는 화면의 msg·toast.
   * 성공 글은 어디서나 「복사됨」(+ 「: label」) 하나. execCommand·clipboard 는 이 파일에만 둔다(ToolsFolderTest)
   */
  function copy(src, notify) {
    var say = notify || function () {};
    var isEl = !!(src && src.nodeType);
    var el = isEl ? src : (src && src.el) || null;
    var text = isEl ? valueOf(src) : (src && src.text) || '';
    var label = !isEl && src && src.label ? ': ' + src.label : '';
    if (!text) { say('복사할 것이 없다', false); return Promise.resolve(false); }
    if (el) { selectEl(el); }
    var fail = el ? '복사 실패 — 선택된 글을 Ctrl+C' : '복사 실패 — 브라우저가 클립보드를 막았다';
    function done(ok) { say(ok ? '복사됨' + label : fail, ok); return ok; }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      return navigator.clipboard.writeText(text).then(function () { return done(true); }, function () { return done(legacyCopy(el, text)); });
    }
    return Promise.resolve(done(legacyCopy(el, text)));
  }

  function valueOf(el) {
    var t = el.tagName;
    return (t === 'TEXTAREA' || t === 'INPUT') ? el.value : el.textContent;
  }

  function selectEl(el) {
    var t = el.tagName;
    if (t === 'TEXTAREA' || t === 'INPUT') { el.focus(); el.select(); return; }
    var r = document.createRange();
    r.selectNodeContents(el);
    var s = window.getSelection();
    s.removeAllRanges();
    s.addRange(r);
  }

  /* 클립보드 API 가 없거나 막혔을 때. 고른 칸이 없으면 숨은 textarea 로 */
  function legacyCopy(el, text) {
    var tmp = null;
    if (!el) {
      tmp = document.createElement('textarea');
      tmp.value = text;
      tmp.style.position = 'fixed';
      tmp.style.opacity = '0';
      document.body.appendChild(tmp);
      tmp.select();
    }
    var ok = false;
    try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
    if (tmp) { document.body.removeChild(tmp); }
    return ok;
  }

  window.TB = { api: api, badge: badge, table: table, sse: sse, snapLabel: snapLabel, joinPath: joinPath, savedText: savedText, copy: copy };

  document.addEventListener('visibilitychange', onVisibility);
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () { badge(); });
  } else {
    badge();
  }
})();
