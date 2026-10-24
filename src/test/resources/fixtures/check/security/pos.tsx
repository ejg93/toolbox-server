export function Html(p: { html: string }) {
  const f = new Function("a", p.html);
  return <div title={String(f)} dangerouslySetInnerHTML={{ __html: p.html }} />;
}
export function Store(p: { t: string }) {
  localStorage.setItem("accessToken", p.t);
  const k = import.meta.env.VITE_API_SECRET_KEY;
  return <a href="javascript:void(0)">{k}</a>;
}
