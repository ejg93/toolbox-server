/* JSP 포매터 — 탭 둘(4-12). 붙여넣기 탭은 순수본 그대로, 폴더 일괄 탭은 이 파일.
   서버가 파일을 읽고 쓰고, 정리·대조는 순수본의 formatJsp·compareDoc 가 한다(2.4).
   검사는 쓰지 않는다. 덮어쓰기는 체크한 것만 — 기본 체크는 바뀌고 위험 0 인 것. 쓰기는 서버가 원본을 먼저 백업한다.
   상세는 행을 누를 때 원본을 다시 읽는다 — 목록 상한이 2만이라 원본을 행에 쌓아 두지 않는다.
   ES5. innerHTML 에는 '' 만 — 값은 textContent. */
var DIR = { root: '', rows: [], stamp: null, sel: -1 };

function $id(id) { return document.getElementById(id); }
/* 1-58f — 폴더 줄 바로 아래 결과 칸(TB.result). state: run · ok · fail · stop(중지·일부 실패 — 노랑). o: { dir, backup } */
function setDirMsg(s, state, o) { TB.result('dirMsg', state || 'run', { summary: s, dir: o && o.dir, backup: o && o.backup }); }
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

/* 중지 — 검사·덮어쓰기 루프가 파일마다 본다. 브라우저가 돌리는 루프라 서버 작업이 없다(파일 사이에서 멈춘다) */
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
	DIR = { root: root, rows: [], stamp: null, sel: -1 };
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
				setDirMsg(stopped ? '검사 중지 — ' + i + '/' + files.length + '개까지 봤다(나머지는 안 봄) · 덮어쓸 대상 ' + countChecked() + '개'
					: '검사 ' + files.length + '개 · 덮어쓸 대상 ' + countChecked() + '개' + (l.truncated ? ' (목록 상한에서 끊김)' : ''),
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
				setDirMsg('검사 ' + i + '/' + files.length);
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
	if (r.done) return '덮어씀';
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
		tr.insertCell(-1).textContent = r.rel;
		var c2 = tr.insertCell(-1);
		c2.textContent = riskText(r);
		if (r.err) c2.className = 'bad';
		else if (r.risk !== 0) c2.className = 'warn';
		tr.onclick = function (e) {
			if (e.target && e.target.tagName === 'INPUT') return;
			dirSelect(i);
		};
	});
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
		+ (bad ? ' · 실패 ' + bad : '') + ' · 덮어쓸 대상 ' + n : '';
	$id('dirApply').disabled = n === 0;
}

function dirClearDetail() {
	$id('dTitle').textContent = '';
	$id('dRisk').innerHTML = '';
	$id('dOrig').value = '';
	$id('dOut').value = '';
	dirShowDetail('risk');
}

function dirSelect(i) {
	DIR.sel = i;
	dirRenderList();
	var r = DIR.rows[i];
	dirClearDetail();
	var title = r.rel;
	if (!r.err) {
		title += ' · ' + r.encoding + ' · ' + r.lineEnding + ' · 위험 ' + (r.risk === -1 ? '한도 초과' : r.risk) + ' · 불균형 ' + r.unbal + '종';
	}
	if (r.done) title += ' · 백업 ' + r.backup;
	$id('dTitle').textContent = title;
	var box = $id('dRisk');
	if (r.err) { box.appendChild(el('div', 'sum', r.err)); return; }
	if (r.done) {
		$id('dOut').value = r.out;
		box.appendChild(el('div', 'sum', '덮어썼다 — 원본은 백업에 있다'));
		return;
	}
	box.appendChild(el('div', 'sum', '원본 읽는 중…'));
	TB.api('/api/fs/read?path=' + encodeURIComponent(r.path)).then(function (t) {
		if (DIR.sel !== i) return;
		$id('dOrig').value = t.text;
		$id('dOut').value = r.out;
		dirRenderRisks(compareDoc(t.text, r.out), box);
		dirShowDetail('risk');
	}, function (e) {
		if (DIR.sel !== i) return;
		box.innerHTML = '';
		box.appendChild(el('div', 'sum', '읽기 실패: ' + e.message));
	});
}

/* renderCompare(순수본)와 같은 네 묶음·같은 글·같은 상한 — DOM 으로 그린다 */
function dirRenderRisks(r, box) {
	var cap = 40, i, g, it;
	var risk = r.struct.length + r.gapChg.length + r.preChg.length;
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
	function item(grp, cls, ln, code, why, src, out) {
		var d = el('div', 'it ' + cls + (src || out ? ' link' : ''));
		d.appendChild(el('span', 'ln', ln));
		d.appendChild(el('code', null, code));
		if (why) d.appendChild(el('span', 'why', why));
		if (src || out) d.onclick = function () { dirJump(src, out); };
		grp.appendChild(d);
	}
	function more(grp, n) { if (n > cap) grp.appendChild(el('div', 'why', '외 ' + (n - cap) + '건')); }
	if (r.struct.length) {
		g = group('red', '구조 변경 — 토큰이 없어지거나 늘었다. 이 결과는 쓰지 말 것');
		for (i = 0; i < r.struct.length && i < cap; i++) {
			it = r.struct[i];
			item(g, 'red', it.side + ' ' + it.line + '행', cut(it.raw, 140), null,
				it.side === '원본' ? it.line : 0, it.side === '결과' ? it.line : 0);
		}
		more(g, r.struct.length);
	}
	if (r.gapChg.length) {
		g = group('yel', '간격 변경 — 인라인 요소 사이 공백 유무가 바뀌어 화면 간격이 달라진다');
		for (i = 0; i < r.gapChg.length && i < cap; i++) {
			it = r.gapChg[i];
			item(g, 'yel', '원본 ' + it.line + '행 → 결과 ' + it.outLine + '행', cut(it.before, 60) + (it.added ? ' ⎵ ' : '') + cut(it.after, 60),
				it.added ? '없던 공백이 생김 — 두 요소가 붙어 보이던 자리가 벌어진다' : '있던 공백이 사라짐 — 벌어져 있던 자리가 붙는다',
				it.line, it.outLine);
		}
		more(g, r.gapChg.length);
	}
	if (r.preChg.length) {
		g = group('yel', 'pre / textarea 내부 들여쓰기 이동 — 내부 공백이 그대로 화면에 찍힌다');
		for (i = 0; i < r.preChg.length && i < cap; i++) {
			it = r.preChg[i];
			item(g, 'yel', '원본 ' + it.line + '행 → 결과 ' + it.outLine + '행', '<' + it.name + '> 블록 전체가 다른 깊이로 옮겨짐', null, it.line, it.outLine);
		}
		more(g, r.preChg.length);
	}
	if (r.unbalanced.length) {
		g = group('yel', '태그 불균형 — 원본에서 여닫음 개수가 안 맞는 태그. 들여쓰기 깊이를 믿을 수 없다');
		for (i = 0; i < r.unbalanced.length; i++) {
			it = r.unbalanced[i];
			item(g, 'yel', '', '<' + it.name + '> ' + (it.net > 0 ? '여는 태그가 ' + it.net + '개 많음' : '닫는 태그가 ' + (-it.net) + '개 많음'),
				'스크립틀릿으로 반쪽만 여닫은 레거시면 정상이다. 그 경우 이 문서의 깊이는 수동 확인할 것', 0, 0);
		}
	}
	if (!risk && !r.unbalanced.length && !r.tooBig) box.appendChild(el('div', 'clean', '화면 출력이 달라질 지점 없음 — 들여쓰기·줄바꿈만 바뀌었다'));
}

function dirJump(srcLine, outLine) {
	if (srcLine > 0) {
		dirShowDetail('orig');
		scrollTaToLine($id('dOrig'), srcLine);
	} else if (outLine > 0) {
		dirShowDetail('out');
		scrollTaToLine($id('dOut'), outLine);
	}
}

function dirShowDetail(which) {
	['risk', 'orig', 'out'].forEach(function (w) {
		var box = $id(w === 'risk' ? 'dRisk' : w === 'orig' ? 'dOrig' : 'dOut');
		box.style.display = w === which ? (w === 'risk' ? 'block' : '') : 'none';
		$id('dtab-' + w).className = w === which ? 't on' : 't';
	});
}

function dirApply() {
	var todo = DIR.rows.filter(function (r) { return r.checked && !r.err && !r.done; });
	if (!todo.length) return;
	if (!confirm(todo.length + '개 파일을 덮어쓴다. 원본은 out/<프로필>/<시각>/backup 에 백업한다. 계속?')) return;
	dirBusy(true);
	var i = 0, ok = 0, bad = 0;
	function next() {
		if (i >= todo.length || DIR_STOP) {
			var stopped = DIR_STOP && i < todo.length;
			dirBusy(false);
			dirRenderList();
			dirSummary();
			// R11 — 1줄 「덮어씀 n/m개」 · 2줄 폴더 · 3줄 백업. 중지·일부 실패는 노랑(R12)
			setDirMsg((stopped ? '덮어쓰기 중지 — ' : '덮어씀 ') + ok + '/' + todo.length + '개' + (stopped ? '만 썼다(나머지는 안 씀)' : '')
				+ (bad ? ' · 실패 ' + bad : ''), stopped ? 'stop' : bad ? (ok ? 'stop' : 'fail') : 'ok', { dir: DIR.root, backup: DIR.backupRoot });
			return;
		}
		var r = todo[i++];
		TB.api('/api/fs/write', { body: { path: r.path, root: DIR.root, text: r.out, encoding: r.encoding,
				lineEnding: r.lineEnding, stamp: DIR.stamp } }).then(function (res) {
			DIR.stamp = res.stamp; DIR.backupRoot = res.backupRoot;
			r.done = true;
			r.backup = res.backup;
			ok++;
			setDirMsg('덮어씀 ' + i + '/' + todo.length);
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
	['risk', 'orig', 'out'].forEach(function (w) {
		$id('dtab-' + w).addEventListener('click', function () { dirShowDetail(w); });
	});
	/* 끌어다 놓은 파일은 순수본이 붙여넣기 탭의 입력·비교 칸에 그린다 */
	window.addEventListener('drop', function (e) {
		var t = e.dataTransfer && e.dataTransfer.types;
		if (!t) return;
		for (var i = 0; i < t.length; i++) if (t[i] === 'Files') { showMode('paste'); return; }
	});
	window.addEventListener('load', loadRecentDirs);
})();
