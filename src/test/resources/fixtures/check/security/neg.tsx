export function Text(p: { text: string }) {
  return <div ref={(el) => { if (el) el.textContent = p.text; }} />;
}
