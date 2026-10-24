/* JSP 포매터 — 탭 둘(4-12). 붙여넣기 탭은 순수본 그대로, 폴더 일괄 탭은 이 파일.
 * 서버가 파일을 읽고 쓰고, 정리·대조는 순수본의 formatJsp·compareDoc 가 한다(2.4).
 * 검사는 쓰지 않는다. 쓰기는 체크한 것만 — 기본 체크는 바뀌고 위험 0 인 것. 원본은 그대로 두고 결과 폴더에 같은 상대 경로로 쓴다(9-3d).
 * 상세는 행을 누를 때 원본을 다시 읽는다 — 목록 상한이 2만이라 원본을 행에 쌓아 두지 않는다.
 * ES5. innerHTML 에는 '' 만 — 값은 textContent. */
var DIR = { root: '', rows: [], stamp: null, outRoot: null, sel: -1 };

function $id(id) { return document.getElementById(id); }
/* 1-58f — 폴더 줄 바로 아래 결과 칸(TB.result). state: run · ok · fail · stop(중지·일부 실패 — 노랑). o: { dir, out, pct } */
function setDirMsg(s, state, o) { TB.result('dirMsg', state || 'run', { summary: s, dir: o && o.dir, out: o && o.out, pct: o && o.pct }); }
function joinPath(root, rel) {
	var sep = root.indexOf('\\') >= 0 ? '\\' : '/';
	var r = root.replace(/[\\\/]+$/, '');
	return r + sep + (sep === '\\' ? rel.replace(/\//g, '\\') : rel);
}
function el(tag, cls, text) {
	var e = document.createElement(tag);
	if (cls) e.className = cls;
	if (text != null) e.textContent = text;
	return e;
}

function showMode(which) {
	var paste = which === 'paste';
	$id('pane-paste').style.display = paste ? 'flex' : 'none';
	$id('pane-dir').style.display = paste ? 'none' : 'flex';
	$id('tab-paste').className = paste ? 't on' : 't';
	$id('tab-dir').className = paste ? 't' : 't on';
}

/* 중지 — 검사·쓰기 루프가 파일마다 본다. 브라우저가 돌리는 루프라 서버 작업이 없다(파일 사이에서 멈춘다) */
var DIR_STOP = false;
function dirBusy(on) {
	DIR_STOP = false;
	$id('dirStop').disabled = !on;
	$id('dirPreview').disabled = on;
	if (on) $id('dirApply').disabled = true;
}
function dirStop() {
	DIR_STOP = true;
	$id('dirStop').disabled = true;
	setDirMsg('중지하는 중…');
}

function dirPreview() {
	var root = $id('dir').value.trim();
	if (!root) { toast('폴더 경로를 넣을 것'); return; }
	var o = opts();
	DIR = { root: root, rows: [], stamp: null, outRoot: null, sel: -1 }; // 새 검사는 새 결과 폴더(stamp)
	dirBusy(true);
	dirClearDetail();
	dirRenderList();
	dirSummary();
	setDirMsg('목록 읽는 중…');
	TB.api('/api/fs/list?path=' + encodeURIComponent(root) + '&glob=' + encodeURIComponent('*.jsp')).then(function (l) {
		var files = l.files, i = 0;
		function next() {
			if (i >= files.length || DIR_STOP) {
				var stopped = DIR_STOP && i < files.length;
				dirBusy(false);
				dirRenderList();
				dirSummary();
				setDirMsg(stopped ? '검사 중지 — ' + i + '/' + files.length + '개까지 봤다(나머지는 안 봄) · 쓸 대상 ' + countChecked() + '개'
					: '검사 ' + files.length + '개 · 쓸 대상 ' + countChecked() + '개' + (l.truncated ? ' (목록 상한에서 끊김)' : ''),
					stopped || l.truncated ? 'stop' : 'ok');
				return;
			}
			var f = files[i++];
			var path = joinPath(root, f.rel);
			TB.api('/api/fs/read?path=' + encodeURIComponent(path)).then(function (t) {
				var row = { rel: f.rel, path: path, encoding: t.encoding, lineEnding: t.lineEnding };
				try {
					var outText = formatJsp(t.text, o);
					var r = compareDoc(t.text, outText);
					row.risk = r.tooBig ? -1 : r.struct.length + r.gapChg.length + r.preChg.length;
					row.unbal = r.unbalanced.length;
					row.changed = outText !== t.text;
					row.out = outText;
					row.checked = row.changed && row.risk === 0;
				} catch (e) { row.err = '정리 실패: ' + e.message; }
				DIR.rows.push(row);
				setDirMsg('검사 ' + i + '/' + files.length, 'run', { pct: i / files.length * 100 });
				next();
			}, function (e) { DIR.rows.push({ rel: f.rel, err: '읽기 실패: ' + e.message }); next(); });
		}
		next();
	}, function (e) { dirBusy(false); setDirMsg('목록 실패: ' + e.message, 'fail'); });
}

function countChecked() {
	var n = 0;
	DIR.rows.forEach(function (r) { if (r.checked && !r.err && !r.done) n++; });
	return n;
}

function riskText(r) {
	if (r.err) return '실패';
	if (r.done) return '썼다';
	if (!r.changed) return '바뀜 없음';
	return r.risk === -1 ? '한도 초과' : String(r.risk);
}

function dirRenderList() {
	var t = $id('dirTable');
	t.innerHTML = '';
	var hr = t.insertRow(-1);
	var only = $id('dirOnlyRisk').checked;
	/* 보이는 행 중 고를 수 있는 것 — 전체선택은 거르기(위험 있는 것만)가 남긴 행에만 */
	var pick = DIR.rows.filter(function (r) { return !(only && !r.err && r.risk === 0) && !r.err && r.changed && !r.done; });
	var on = pick.filter(function (r) { return r.checked; }).length;
	var all = document.createElement('input');
	all.type = 'checkbox';
	all.id = 'dirAll';
	all.title = '전체 선택';
	all.disabled = pick.length === 0;
	all.checked = pick.length > 0 && on === pick.length;
	all.indeterminate = on > 0 && on < pick.length;
	all.onchange = function () {
		pick.forEach(function (r) { r.checked = all.checked; });
		dirRenderList();
		dirSummary();
	};
	var th0 = el('th', null, '');
	th0.appendChild(all);
	hr.appendChild(th0);
	['파일', '위험'].forEach(function (s) { hr.appendChild(el('th', null, s)); });
	DIR.rows.forEach(function (r, i) {
		if (only && !r.err && r.risk === 0) return;
		var tr = t.insertRow(-1);
		if (i === DIR.sel) tr.className = 'on';
		var c0 = tr.insertCell(-1);
		if (!r.err && r.changed && !r.done) {
			var cb = document.createElement('input');
			cb.type = 'checkbox';
			cb.checked = !!r.checked;
			cb.onchange = function () { r.checked = cb.checked; dirRenderList(); dirSummary(); };
			c0.appendChild(cb);
		}
		var c1 = tr.insertCell(-1), slash = r.rel.lastIndexOf('/') + 1;
		if (slash) c1.appendChild(el('span', 'dp', r.rel.slice(0, slash)));
		c1.appendChild(document.createTextNode(r.rel.slice(slash)));
		var c2 = tr.insertCell(-1);
		c2.textContent = riskText(r);
		if (r.err) c2.className = 'bad';
		else if (r.risk !== 0) c2.className = 'warn';
		tr.onclick = function (e) {
			if (e.target && e.target.tagName === 'INPUT') return;
			dirSelect(i);
		};
	});
	$id('dnFiles').textContent = DIR.rows.length || '';
	dirNavState();
}

/* ◀ ▶ 가 가는 파일 — 「위험 있는 것만」 이 남기는 행과 같다 */
function dirFlagged(r) { return !!r.err || r.risk !== 0; }

function dirNavState() {
	var i = DIR.sel, r = DIR.rows[i];
	var hit = DIR.rows.map(function (x, k) { return dirFlagged(x) ? k : -1; }).filter(function (k) { return k >= 0; });
	$id('dPrev').disabled = !hit.some(function (k) { return k < i; });
	$id('dNext').disabled = !hit.some(function (k) { return k > i; });
	var cb = $id('dCheck');
	cb.disabled = !r || !!r.err || !r.changed || !!r.done;
	cb.checked = !!(r && r.checked && !cb.disabled);
	var at = hit.indexOf(i);
	$id('dPos').textContent = r ? (at >= 0 ? '위험 있는 파일 ' + (at + 1) + '/' + hit.length + ' · ' : '') + '전체 ' + (i + 1) + '/' + DIR.rows.length : '';
}

function dirStep(d) {
	for (var k = DIR.sel + d; k >= 0 && k < DIR.rows.length; k += d) if (dirFlagged(DIR.rows[k])) { dirSelect(k); return; }
}

function dirSummary() {
	var risk = 0, same = 0, bad = 0;
	DIR.rows.forEach(function (r) {
		if (r.err) bad++;
		else if (!r.changed) same++;
		else if (r.risk !== 0) risk++;
	});
	var n = countChecked();
	$id('dirSum').textContent = DIR.rows.length ? DIR.rows.length + '개 · 위험 있음 ' + risk + ' · 바뀜 없음 ' + same
		+ (bad ? ' · 실패 ' + bad : '') + ' · 쓸 대상 ' + n : '';
	$id('dirApply').disabled = n === 0;
}

function dirClearDetail() {
	$id('dTitle').textContent = '';
	$id('dRisk').innerHTML = '';
	$id('dnRisk').textContent = '';
	$id('dnRisk').className = 'n';
	DIR.items = [];
	dirCodeView('dOrig', '', 'src');
	dirCodeView('dOut', '', 'out');
	$id('dOrigCap').textContent = '원본';
	$id('dOutCap').textContent = '정리본';
	dirShowSide('files');
}

function dirSelect(i) {
	DIR.sel = i;
	dirRenderList();
	var r = DIR.rows[i];
	dirClearDetail();
	var t = $id('dTitle'), slash = r.rel.lastIndexOf('/') + 1, meta = '';
	if (slash) t.appendChild(el('span', 'dp', r.rel.slice(0, slash)));
	t.appendChild(el('b', null, r.rel.slice(slash)));
	if (!r.err) meta = ' · ' + r.encoding + ' · ' + r.lineEnding + ' · 위험 ' + (r.risk === -1 ? '한도 초과' : r.risk) + ' · 불균형 ' + r.unbal + '종';
	if (r.done) meta += ' · 결과 ' + r.written;
	if (meta) t.appendChild(el('span', 'dp', meta));
	t.title = t.textContent;
	dirShowSide('risk');
	var box = $id('dRisk');
	if (r.err) { box.appendChild(el('div', 'sum', r.err)); return; }
	if (r.done) {
		dirCodeView('dOut', r.out, 'out');
		box.appendChild(el('div', 'sum', '결과 폴더에 썼다 — 원본은 그대로'));
		return;
	}
	box.appendChild(el('div', 'sum', '원본 읽는 중…'));
	TB.api('/api/fs/read?path=' + encodeURIComponent(r.path)).then(function (t) {
		if (DIR.sel !== i) return;
		var cmp = compareDoc(t.text, r.out);
		DIR.items = dirRiskItems(cmp);
		dirCodeView('dOrig', t.text, 'src');
		dirCodeView('dOut', r.out, 'out');
		dirRenderRisks(cmp, box);
		var first = -1;
		DIR.items.some(function (x, j) { if (x.src || x.out) { first = j; return true; } return false; });
		if (first >= 0) dirJump(first);
	}, function (e) {
		if (DIR.sel !== i) return;
		box.innerHTML = '';
		box.appendChild(el('div', 'sum', '읽기 실패: ' + e.message));
	});
}

/* 한 파일의 지적을 한 줄 목록으로 — 위험 지점 목록·거터 배지·줄 안 자리·세로 눈금이 같은 번호(j)를 본다 */
function dirRiskItems(r) {
	var out = [];
	if (r.tooBig) return out;
	r.struct.forEach(function (it) {
		out.push({ grp: 'struct', cls: 'red', ln: it.side + ' ' + it.line + '행', code: cut(it.raw, 140),
			src: it.side === '원본' ? it.line : 0, out: it.side === '결과' ? it.line : 0 });
	});
	r.gapChg.forEach(function (it) {
		out.push({ grp: 'gap', cls: 'yel', ln: '원본 ' + it.line + '행 → 결과 ' + it.outLine + '행',
			code: cut(it.before, 60) + (it.added ? ' ⎵ ' : '') + cut(it.after, 60),
			why: it.added ? '없던 공백이 생김 — 두 요소가 붙어 보이던 자리가 벌어진다' : '있던 공백이 사라짐 — 벌어져 있던 자리가 붙는다',
			src: it.line, out: it.outLine, gap: it });
	});
	r.preChg.forEach(function (it) {
		out.push({ grp: 'pre', cls: 'yel', ln: '원본 ' + it.line + '행 → 결과 ' + it.outLine + '행',
			code: '<' + it.name + '> 블록 전체가 다른 깊이로 옮겨짐', src: it.line, out: it.outLine });
	});
	return out;
}

/* renderCompare(순수본)와 같은 네 묶음·같은 글·같은 상한 — DOM 으로 그린다. 항목 번호는 DIR.items 의 j */
function dirRenderRisks(r, box) {
	var cap = 40, i, g, it, j = 0;
	var risk = r.struct.length + r.gapChg.length + r.preChg.length;
	var nr = $id('dnRisk');
	nr.textContent = risk + r.unbalanced.length || '';
	nr.className = risk ? 'n y' : 'n';
	box.innerHTML = '';
	if (r.tooBig) {
		box.appendChild(el('div', 'sum', '토큰 ' + r.tokens + '개 — 정렬 한도를 넘어 전후 대조는 건너뛴다. '
			+ '태그 불균형만 검사했다. 전후 대조가 필요하면 파일을 나눠서 정리할 것.'));
	} else {
		box.appendChild(el('div', 'sum', '토큰 ' + r.tokens + '개 중 ' + r.matched + '개 일치 · 구조 변경 ' + r.struct.length
			+ ' · 간격 변경 ' + r.gapChg.length + ' · pre/textarea 들여쓰기 이동 ' + r.preChg.length
			+ ' · 태그 불균형 ' + r.unbalanced.length + '종' + (risk ? ' · 항목 클릭 = 그 줄로 이동' : '')));
	}
	function group(cls, title) {
		var d = el('div', 'grp');
		d.appendChild(el('h2', cls, title));
		box.appendChild(d);
		return d;
	}
	/* x = DIR.items[j] — 줄이 있으면 누르면 그 줄로 */
	function item(grp, x, k) {
		var d = el('div', 'it ' + x.cls + (x.src || x.out ? ' link' : ''));
		d.setAttribute('data-j', k);
		d.appendChild(el('span', 'ln', x.ln));
		d.appendChild(el('code', null, x.code));
		if (x.why) d.appendChild(el('span', 'why', x.why));
		if (x.src || x.out) d.onclick = function () { dirJump(k); };
		grp.appendChild(d);
	}
	function more(grp, n) { if (n > cap) grp.appendChild(el('div', 'why', '외 ' + (n - cap) + '건 — 원본·정리본의 줄 배지·세로 눈금에는 전부 있다')); }
	[['struct', 'red', '구조 변경 — 토큰이 없어지거나 늘었다. 이 결과는 쓰지 말 것', r.struct.length],
		['gap', 'yel', '간격 변경 — 인라인 요소 사이 공백 유무가 바뀌어 화면 간격이 달라진다', r.gapChg.length],
		['pre', 'yel', 'pre / textarea 내부 들여쓰기 이동 — 내부 공백이 그대로 화면에 찍힌다', r.preChg.length]].forEach(function (s) {
		if (!s[3]) return;
		g = group(s[1], s[2]);
		for (i = 0; i < s[3]; i++, j++) if (i < cap) item(g, DIR.items[j], j);
		more(g, s[3]);
	});
	if (r.unbalanced.length) {
		g = group('yel', '태그 불균형 — 원본에서 여닫음 개수가 안 맞는 태그. 들여쓰기 깊이를 믿을 수 없다');
		for (i = 0; i < r.unbalanced.length; i++) {
			it = r.unbalanced[i];
			item(g, { cls: 'yel', ln: '', code: '<' + it.name + '> ' + (it.net > 0 ? '여는 태그가 ' + it.net + '개 많음' : '닫는 태그가 ' + (-it.net) + '개 많음'),
				why: '스크립틀릿으로 반쪽만 여닫은 레거시면 정상이다. 그 경우 이 문서의 깊이는 수동 확인할 것' }, -1);
		}
	}
	if (!risk && !r.unbalanced.length && !r.tooBig) box.appendChild(el('div', 'clean', '화면 출력이 달라질 지점 없음 — 들여쓰기·줄바꿈만 바뀌었다'));
}

/*
 * 코드 보기 — textarea 대신 줄마다 거터(번호 · 지적 건수 배지) + 본문. 줄을 접지 않아 번호와 줄이 1:1.
 * side: src(원본 — 항목의 src 줄) · out(정리본 — out 줄). 간격 변경은 줄 안 경계에 세로선(.gm). 오른쪽 세로 눈금은 파일 전체의 지적 위치
 */
function dirCodeView(id, text, side) {
	var box = $id(id), items = DIR.items || [];
	box.innerHTML = '';
	if (!text) return;
	var lines = text.replace(/\r\n?/g, '\n').split('\n');
	if (lines.length > 1 && lines[lines.length - 1] === '') lines.pop();
	var cnt = {}, red = {}, marks = {}, cursor = {};
	items.forEach(function (x, j) {
		var ln = side === 'src' ? x.src : x.out;
		if (!ln) return;
		cnt[ln] = (cnt[ln] || 0) + 1;
		if (x.cls === 'red') red[ln] = 1;
		if (!x.gap) return;
		/* 같은 줄의 간격 변경을 차례로 — 원본은 앞 토큰 끝, 정리본은 뒤 토큰 앞이 경계 */
		var t = lines[ln - 1] || '', from = cursor[ln] || 0, pos;
		if (side === 'src') { pos = t.indexOf(x.gap.before, from); if (pos >= 0) pos += x.gap.before.length; }
		else pos = t.indexOf(x.gap.after, from);
		if (pos < 0) return;
		cursor[ln] = side === 'src' ? pos : pos + 1;
		(marks[ln] = marks[ln] || []).push({ pos: pos, j: j });
	});
	var scroll = el('div', 'scroll'), wrap = el('div', 'lines'), ruler = el('div', 'ruler');
	lines.forEach(function (t, i) {
		var ln = i + 1, row = el('div', 'row' + (cnt[ln] ? ' risky' + (red[ln] ? ' red' : '') : ''));
		row.setAttribute('data-ln', ln);
		var g = el('span', 'g', String(ln));
		if (cnt[ln]) g.setAttribute('data-n', cnt[ln]);
		var c = el('span', 'c'), last = 0;
		(marks[ln] || []).sort(function (a, b) { return a.pos - b.pos; }).forEach(function (m) {
			if (m.pos > last) c.appendChild(document.createTextNode(t.slice(last, m.pos)));
			var gm = el('span', 'gm');
			gm.setAttribute('data-j', m.j);
			c.appendChild(gm);
			last = m.pos;
		});
		c.appendChild(document.createTextNode(t.slice(last) || (last ? '' : ' ')));
		row.appendChild(g);
		row.appendChild(c);
		wrap.appendChild(row);
	});
	scroll.appendChild(wrap);
	Object.keys(cnt).forEach(function (ln) {
		var mk = el('i', red[ln] ? 'red' : null);
		mk.style.top = ((ln - 1) / lines.length * 100) + '%';
		mk.setAttribute('data-ln', ln);
		ruler.appendChild(mk);
	});
	var vp = el('div', 'vp');
	ruler.appendChild(vp);
	function vpMove() {
		if (!scroll.scrollHeight) return;
		vp.style.top = (scroll.scrollTop / scroll.scrollHeight * 100) + '%';
		vp.style.height = (scroll.clientHeight / scroll.scrollHeight * 100) + '%';
	}
	scroll.addEventListener('scroll', vpMove);
	ruler.onclick = function (e) {
		var b = ruler.getBoundingClientRect();
		if (b.height) dirGoLine(box, Math.max(1, Math.round((e.clientY - b.top) / b.height * lines.length)), -1);
	};
	box.appendChild(scroll);
	box.appendChild(ruler);
	setTimeout(vpMove, 0);
	$id(id + 'Cap').textContent = (side === 'src' ? '원본 ' : '정리본 ') + lines.length + '행';
}

/* 그 줄을 가운데로 · 파랑 바탕 + 깜빡임 · 간격 변경이면 줄 안 경계까지 가로로 */
function dirGoLine(box, ln, j) {
	var scroll = box.querySelector('.scroll');
	if (!scroll) return;
	[].forEach.call(box.querySelectorAll('.row.hit'), function (x) { x.className = x.className.replace(/\s*\b(hit|flash)\b/g, ''); });
	[].forEach.call(box.querySelectorAll('.gm.cur, .ruler i.cur'), function (x) { x.className = x.className.replace(/\s*\bcur\b/g, ''); });
	var row = box.querySelector('.row[data-ln="' + ln + '"]');
	if (!row) return;
	row.className += ' hit';
	void row.offsetWidth;
	row.className += ' flash';
	scroll.scrollTop = row.offsetTop - scroll.clientHeight / 2 + 9;
	var gm = j >= 0 ? row.querySelector('.gm[data-j="' + j + '"]') : null;
	if (gm) {
		gm.className += ' cur';
		var x = gm.getBoundingClientRect().left - scroll.getBoundingClientRect().left + scroll.scrollLeft;
		if (x < scroll.scrollLeft + 90 || x > scroll.scrollLeft + scroll.clientWidth - 30) scroll.scrollLeft = Math.max(0, x - 58 - scroll.clientWidth / 3);
	} else scroll.scrollLeft = 0;
	var mk = box.querySelector('.ruler i[data-ln="' + ln + '"]');
	if (mk) mk.className += ' cur';
}

/* 위험 지점 j — 원본·정리본이 함께 그 줄로 */
function dirJump(j) {
	var x = DIR.items[j];
	if (!x) return;
	[].forEach.call($id('dRisk').querySelectorAll('.it'), function (d) {
		d.className = d.className.replace(/\s*\bcur\b/g, '') + (d.getAttribute('data-j') === String(j) ? ' cur' : '');
	});
	if (x.src) dirGoLine($id('dOrig'), x.src, j);
	if (x.out) dirGoLine($id('dOut'), x.out, j);
}

function dirShowSide(which) {
	$id('dirList').style.display = which === 'files' ? '' : 'none';
	$id('dRisk').style.display = which === 'risk' ? 'block' : 'none';
	$id('dstab-files').className = which === 'files' ? 't on' : 't';
	$id('dstab-risk').className = which === 'risk' ? 't on' : 't';
	if (which === 'files') {
		var on = $id('dirTable').querySelector('tr.on');
		if (on && on.scrollIntoView) on.scrollIntoView({ block: 'nearest' });
	}
}

function dirApply() {
	var todo = DIR.rows.filter(function (r) { return r.checked && !r.err && !r.done; });
	if (!todo.length) return;
	// 마무리 리뷰 — 결과 폴더 칸을 바꿨으면 새 폴더로(앞 쓰기의 폴더를 이어 쓰지 않는다)
	var want = $id('dirOut').value.trim();
	if (DIR.outFor !== want) { DIR.outRoot = null; DIR.stamp = null; DIR.outFor = want; }
	dirBusy(true);
	var i = 0, ok = 0, bad = 0;
	function next() {
		if (i >= todo.length || DIR_STOP) {
			var stopped = DIR_STOP && i < todo.length;
			dirBusy(false);
			dirRenderList();
			dirSummary();
			// R11·9-3d — 1줄 「썼다 n/m개」 · 2줄 원본 폴더 · 3줄 결과 폴더. 중지·일부 실패는 노랑(R12)
			setDirMsg((stopped ? '쓰기 중지 — ' : '썼다 ') + ok + '/' + todo.length + '개' + (stopped ? '만 썼다(나머지는 안 씀)' : '')
				+ (bad ? ' · 실패 ' + bad : ''), stopped ? 'stop' : bad ? (ok ? 'stop' : 'fail') : 'ok', { dir: DIR.root, out: DIR.outRoot });
			return;
		}
		var r = todo[i++];
		TB.api('/api/fs/result', { body: { root: DIR.root, rel: r.rel, text: r.out, encoding: r.encoding,
				lineEnding: r.lineEnding, stamp: DIR.stamp, outDir: DIR.outRoot || want } }).then(function (res) {
			DIR.stamp = res.stamp; DIR.outRoot = res.outRoot;
			r.done = true;
			r.written = res.path;
			ok++;
			setDirMsg('썼다 ' + i + '/' + todo.length, 'run', { pct: i / todo.length * 100 });
			next();
		}, function (e) { r.err = '쓰기 실패: ' + e.message; bad++; next(); });
	}
	next();
}

/* 경로 칸 기본값(5-5) — 비어 있으면 프로필 프로젝트 루트, 고를 거리는 루트 + 최근 목록 */
function loadRecentDirs() {
	if (!window.TB) return;
	TB.api('/api/fs/defaults').then(function (d) {
		var dl = $id('dirRecent'), seen = {};
		dl.innerHTML = '';
		[d.projectRoot].concat(d.recent).forEach(function (p) {
			if (!p || seen[p]) return;
			seen[p] = 1;
			var op = document.createElement('option'); op.value = p; dl.appendChild(op);
		});
		var inp = $id('dir');
		if (!inp.value && d.projectRoot) inp.value = d.projectRoot;
	}, function () {});
}

(function () {
	$id('tab-paste').addEventListener('click', function () { showMode('paste'); });
	$id('tab-dir').addEventListener('click', function () { showMode('dir'); });
	$id('dirPreview').addEventListener('click', dirPreview);
	$id('dirApply').addEventListener('click', dirApply);
	$id('dirStop').addEventListener('click', dirStop);
	$id('dirOnlyRisk').addEventListener('change', dirRenderList);
	$id('dstab-files').addEventListener('click', function () { dirShowSide('files'); });
	$id('dstab-risk').addEventListener('click', function () { dirShowSide('risk'); });
	$id('dPrev').addEventListener('click', function () { dirStep(-1); });
	$id('dNext').addEventListener('click', function () { dirStep(1); });
	$id('dCheck').addEventListener('change', function () {
		var r = DIR.rows[DIR.sel];
		if (!r) return;
		r.checked = this.checked;
		dirRenderList();
		dirSummary();
	});
	/* 끌어다 놓은 파일은 순수본이 붙여넣기 탭의 입력·비교 칸에 그린다 */
	window.addEventListener('drop', function (e) {
		var t = e.dataTransfer && e.dataTransfer.types;
		if (!t) return;
		for (var i = 0; i < t.length; i++) if (t[i] === 'Files') { showMode('paste'); return; }
	});
	window.addEventListener('load', loadRecentDirs);
})();
