export function Html(p: { html: string }) {
  const f = new Function("a", p.html);
  return <div title={String(f)} dangerouslySetInnerHTML={{ __html: p.html }} />;
}
