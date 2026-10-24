/* 표 편집기 — 엑셀 클립보드 표 붙여넣기(4-14). 엑셀은 TSV 와 함께 text/html(<table> + colspan·rowspan,
   정렬은 <style> 의 .xlNN 클래스 규칙, 옆 칸으로 넘친 글은 mso-ignore:colspan)도 싣는다.
   글·셀 안 줄바꿈·병합·정렬만 남기고 색·글꼴·크기·열 너비·class 는 버린다.
   병합이 있으면 순수본 importHtml 로 표 전체를 바꾸고, 없으면 순수본 pasteGrid(TSV) 뒤에 정렬만 덧입힌다.
   ES5. innerHTML 대입 없음 — 셀 글은 직접 이스케이프한다. */

var TB_PASTE_ALIGNS = null;

function tbEsc(s) {
	return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function tbNormAlign(a) {
	a = String(a || '').toLowerCase().trim();
	if (a === 'justify') return 'left';
	return a === 'left' || a === 'center' || a === 'right' ? a : '';
}

/* <style> 글의 「.클래스 { … text-align: X … }」 → { 클래스: X } */
function tbClassAligns(doc) {
	var map = {};
	Array.prototype.forEach.call(doc.querySelectorAll('style'), function (st) {
		var re = /\.([\w-]+)\s*\{([^}]*)\}/g, m;
		var css = st.textContent || '';
		while ((m = re.exec(css))) {
			var a = /text-align\s*:\s*([a-z-]+)/i.exec(m[2]);
			if (a) map[m[1]] = tbNormAlign(a[1]);
		}
	});
	return map;
}

/* 셀 글 — 텍스트는 이스케이프, <br> 은 <br>, 그 밖 태그는 벗기고 안쪽만. 공백은 화면처럼 하나로.
   script·style·noscript·template 은 안쪽 글째 버린다(4-18 — 화면에 안 보이는 글이 셀 글자로 붙지 않게) */
var TB_DROP = { script: 1, style: 1, noscript: 1, template: 1 };
function tbCellText(node) {
	var out = '';
	Array.prototype.forEach.call(node.childNodes, function (ch) {
		if (ch.nodeType === 3) out += tbEsc(String(ch.nodeValue).replace(/\s+/g, ' '));
		else if (ch.nodeType === 1) {
			var tag = ch.tagName.toLowerCase();
			if (TB_DROP[tag]) return;
			out += tag === 'br' ? '<br>' : tbCellText(ch);
		}
	});
	return out;
}

function tbStrip(text) {
	return text.replace(/^(?: |<br>)+|(?: |<br>)+$/g, '').replace(/ ?<br> ?/g, '<br>');
}

/** 클립보드 text/html → { merged, html, aligns } | null(표 없음) */
function tbClipboardTable(html) {
	var doc = new DOMParser().parseFromString(String(html), 'text/html');
	var tb = doc.querySelector('table');
	if (!tb) return null;
	var classAlign = tbClassAligns(doc);
	var trs = Array.prototype.filter.call(tb.querySelectorAll('tr'), function (tr) { return tr.closest('table') === tb; });
	if (!trs.length) return null;
	var cells = [], merged = false;   // cells[r] = [{ t, cs, rs, text, align }]
	trs.forEach(function (tr, r) {
		var row = [];
		Array.prototype.forEach.call(tr.children, function (el) {
			var t = el.tagName.toLowerCase();
			if (t !== 'td' && t !== 'th') return;
			var cs = Math.max(1, parseInt(el.getAttribute('colspan'), 10) || 1);
			var rs = Math.max(1, parseInt(el.getAttribute('rowspan'), 10) || 1);
			rs = Math.min(rs, trs.length - r);
			var style = el.getAttribute('style') || '';
			var align = tbNormAlign(el.style && el.style.textAlign) || tbNormAlign(el.getAttribute('align'));
			if (!align) {
				String(el.className || '').split(/\s+/).forEach(function (c) { if (classAlign[c]) align = classAlign[c]; });
			}
			var text = tbStrip(tbCellText(el));
			if (cs > 1 && /mso-ignore\s*:\s*colspan/i.test(style)) {
				// 글이 옆 칸으로 넘친 것 — 병합이 아니다. 낱칸으로 편다(첫 칸에 글, 나머지 빈칸)
				row.push({ t: t, cs: 1, rs: rs, text: text, align: align });
				for (var k = 1; k < cs; k++) row.push({ t: t, cs: 1, rs: rs, text: '', align: '' });
				if (rs > 1) merged = true;
				return;
			}
			if (cs > 1 || rs > 1) merged = true;
			row.push({ t: t, cs: cs, rs: rs, text: text, align: align });
		});
		cells.push(row);
	});
	// 병합을 편 격자 — 덮인 칸은 주인 칸의 정렬
	var aligns = [], covered = [], r, c;
	for (r = 0; r < cells.length; r++) { aligns.push([]); covered.push([]); }
	var h = ['<table>'];
	for (r = 0; r < cells.length; r++) {
		c = 0;
		h.push('<tr>');
		cells[r].forEach(function (x) {
			while (covered[r][c]) c++;
			for (var a = 0; a < x.rs; a++) for (var b = 0; b < x.cs; b++) {
				covered[r + a][c + b] = true;
				aligns[r + a][c + b] = x.align;
			}
			h.push('<' + x.t + (x.cs > 1 ? ' colspan="' + x.cs + '"' : '') + (x.rs > 1 ? ' rowspan="' + x.rs + '"' : '')
				+ (x.align ? ' style="text-align:' + x.align + '"' : '') + '>' + x.text + '</' + x.t + '>');
			c += x.cs;
		});
		h.push('</tr>');
	}
	h.push('</table>');
	for (r = 0; r < aligns.length; r++) for (c = 0; c < aligns[r].length; c++) if (aligns[r][c] == null) aligns[r][c] = '';
	return { merged: merged, html: h.join(''), aligns: aligns };
}

/* pasteGrid 바로 뒤 — 붙인 자리부터 정렬 격자대로. 되돌리기는 pasteGrid 의 snapshot() 한 번으로 글·정렬이 같이 돌아간다 */
function tbApplyPastedAligns() {
	var p = TB_PASTE_ALIGNS;
	TB_PASTE_ALIGNS = null;
	if (!p) return;
	for (var i = 0; i < p.aligns.length; i++) {
		for (var j = 0; j < p.aligns[i].length; j++) {
			var R = p.r + i, C = p.c + j;
			if (R >= G.rows || C >= G.cols) continue;
			var m = masterOf(R, C), cell = G.grid[m.r][m.c];
			if (p.aligns[i][j]) cell.align = p.aligns[i][j];
			else delete cell.align;
		}
	}
	render();
}

/* JSX 출력(4-27, 설계 35) — 공통 class 모드의 HTML(순수본 genHtml('cls'))을 DOMParser 로 읽어 JSX 로 다시 쓴다.
   class → className · colspan·rowspan → colSpan={n}·rowSpan={n} · style="a-b:c" → style={{ aB: 'c' }} · 빈 요소는 <br /> ·
   글자의 < > { } 는 엔터티로. summary(HTML5 폐지)·on* 속성은 뺀다. ES5. innerHTML 대입 없음 */

var TB_JSX_BLOCK = { table: 1, colgroup: 1, thead: 1, tbody: 1, tfoot: 1, tr: 1 };
var TB_JSX_VOID = { br: 1, col: 1, img: 1, hr: 1, input: 1, wbr: 1 };
var TB_JSX_NAME = { 'class': 'className', 'colspan': 'colSpan', 'rowspan': 'rowSpan', 'for': 'htmlFor', 'tabindex': 'tabIndex' };

function tbJsxText(t) {
	return String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
		.replace(/\{/g, '&#123;').replace(/\}/g, '&#125;').replace(/\u00a0/g, '&nbsp;');
}

function tbJsxStyle(css) {
	var parts = [];
	String(css).split(';').forEach(function (d) {
		var i = d.indexOf(':');
		if (i < 0) return;
		var k = d.slice(0, i).trim().toLowerCase(), v = d.slice(i + 1).trim();
		if (!k || !v) return;
		k = k.replace(/-([a-z])/g, function (m, c) { return c.toUpperCase(); });
		parts.push(k + ": '" + v.replace(/\\/g, '\\\\').replace(/'/g, "\\'") + "'");
	});
	return parts.join(', ');
}

function tbJsxAttrs(el) {
	var out = '';
	for (var i = 0; i < el.attributes.length; i++) {
		var a = el.attributes[i], n = a.name.toLowerCase(), v = a.value;
		if (n === 'summary' || n.indexOf('on') === 0) continue;
		if (n === 'style') {
			var st = tbJsxStyle(v);
			if (st) out += ' style={{ ' + st + ' }}';
			continue;
		}
		if ((n === 'colspan' || n === 'rowspan') && /^\d+$/.test(v)) {
			out += ' ' + TB_JSX_NAME[n] + '={' + v + '}';
			continue;
		}
		out += ' ' + (TB_JSX_NAME[n] || n) + '="' + v.replace(/&/g, '&amp;').replace(/"/g, '&quot;') + '"';
	}
	return out;
}

function tbJsxInline(node) {
	var s = '';
	for (var i = 0; i < node.childNodes.length; i++) {
		var c = node.childNodes[i];
		if (c.nodeType === 3) s += tbJsxText(c.nodeValue);
		else if (c.nodeType === 1) {
			var t = c.tagName.toLowerCase();
			s += TB_JSX_VOID[t] ? '<' + t + tbJsxAttrs(c) + ' />' : '<' + t + tbJsxAttrs(c) + '>' + tbJsxInline(c) + '</' + t + '>';
		}
	}
	return s;
}

function tbJsxBlock(el, depth, L) {
	var pad = new Array(depth + 1).join('\t'), t = el.tagName.toLowerCase();
	if (TB_JSX_VOID[t]) { L.push(pad + '<' + t + tbJsxAttrs(el) + ' />'); return; }
	if (!TB_JSX_BLOCK[t]) { L.push(pad + '<' + t + tbJsxAttrs(el) + '>' + tbJsxInline(el) + '</' + t + '>'); return; }
	L.push(pad + '<' + t + tbJsxAttrs(el) + '>');
	for (var i = 0; i < el.children.length; i++) tbJsxBlock(el.children[i], depth + 1, L);
	L.push(pad + '</' + t + '>');
}

function tbGenJsx() {
	var doc = new DOMParser().parseFromString(genHtml('cls'), 'text/html');
	var tb = doc.querySelector('table');
	if (!tb) return '';
	var L = [];
	tbJsxBlock(tb, 0, L);
	return L.join('\n');
}
