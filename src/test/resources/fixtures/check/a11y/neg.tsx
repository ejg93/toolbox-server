export function Page(p: { go: () => void }) {
  return (
    <section>
      <img src="/logo.png" alt="회사 로고" />
      <label htmlFor="q">검색</label>
      <input type="text" id="q" name="q" />
      <input type="hidden" name="t" />
      <button type="button" onClick={p.go}>열기</button>
      <div role="button" tabIndex={0} onClick={p.go} onKeyDown={p.go}>닫기</div>
      <a href="/help">도움말</a>
      <iframe src="/map" title="지도" />
      <img
        src="/multi.png"
      />
    </section>
  );
}
