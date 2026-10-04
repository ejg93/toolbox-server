/*
 * 백엔드본 공통 — 도구 HTML 은 <script src="/tools/common.js" defer></script> 한 줄만 넣는다.
 * window.TB = { api, badge, table, sse, snapLabel }. 로드되면 스스로 badge() 를 건다.
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
      '#' + BADGE_ID + '{position:fixed;top:6px;right:8px;z-index:9999;padding:3px 8px;font-size:11px;' +
      "font-family:'Consolas','D2Coding',monospace;background:var(--surface,var(--card,#252526));color:var(--text,var(--ink,#d4d4d4));" +
      'border:1px solid var(--border,var(--line,#3c3c3c));border-radius:3px;opacity:.9;pointer-events:none;letter-spacing:.5px}' +
      '#' + BADGE_ID + '.on{border-color:var(--accent,#0078d4)}' +
      '#' + BADGE_ID + '.off{color:var(--muted,var(--sub,#858585))}' +
      '.tb-table{border-collapse:collapse;font-size:12px;width:100%}' +
      '.tb-table th,.tb-table td{border:1px solid var(--border,var(--line,#3c3c3c));padding:3px 6px;text-align:left;white-space:nowrap}' +
      '.tb-table th{background:var(--surface,var(--card,#252526));color:var(--muted,var(--sub,#858585));position:sticky;top:0}' +
      '.tb-table td.tb-null{color:var(--muted,var(--sub,#858585))}';
    (document.head || document.documentElement).appendChild(st);
  }

  function setBadge(text, on) {
    injectStyle();
    var el = document.getElementById(BADGE_ID);
    if (!el) {
      el = document.createElement('div');
      el.id = BADGE_ID;
      document.body.appendChild(el);
    }
    el.textContent = text;
    el.className = on ? 'on' : 'off';
    return el;
  }

  function badge() {
    if (location.protocol === 'file:') {
      setBadge('순수', false);
      return Promise.resolve(null);
    }
    return api('/api/ping').then(function (p) {
      var text = '백엔드 연결' + (p && p.profile ? ' · ' + p.profile : '');
      setBadge(text, true);
      return p;
    }, function () {
      setBadge('순수', false);
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

  window.TB = { api: api, badge: badge, table: table, sse: sse, snapLabel: snapLabel };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () { badge(); });
  } else {
    badge();
  }
})();
