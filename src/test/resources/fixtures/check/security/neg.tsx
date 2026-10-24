export function Text(p: { text: string }) {
  return <div ref={(el) => { if (el) el.textContent = p.text; }} />;
}
export function Safe(p: { t: string }) {
  sessionStorage.setItem("lastTab", p.t);
  const u = import.meta.env.VITE_API_URL;
  return <a href="/help">{u}</a>;
}
