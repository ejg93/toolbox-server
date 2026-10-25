export function Page(p: { go: () => void }) {
  return (
    <section>
      <img src="/logo.png" />
      <input type="text" name="q" />
      <div onClick={p.go}>열기</div>
      <a onClick={p.go}>이동</a>
      <iframe src="/map" />
      <input
        type="text"
        name="q2"
      />
    </section>
  );
}
