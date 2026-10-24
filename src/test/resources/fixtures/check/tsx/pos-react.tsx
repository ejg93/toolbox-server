export function List(p: { items: string[] }) {
  return (
    <ul>
      {p.items.map((it, index) => <li key={index}>{it}</li>)}
      <li><a href="/out" target="_blank">밖</a></li>
    </ul>
  );
}
