export function Page(p: { go: () => void; name: string; shared: object; type: string }) {
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
      <label className="row">
        <input type="radio" name="r" />
        다시 판매
      </label>
      <label><input type="checkbox" name="c" /> 공개</label>
      <input {...p.shared} type={p.type} />
      <label htmlFor={p.name}>이름</label>
      <input id={p.name} />
      <input type={"hidden"} name="h" />
    </section>
  );
}
