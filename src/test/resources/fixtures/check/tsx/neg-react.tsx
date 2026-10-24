export function List(p: { items: { id: string; name: string }[] }) {
  return (
    <ul>
      {p.items.map((it) => <li key={it.id}>{it.name}</li>)}
      <li><a href="/out" target="_blank" rel="noopener noreferrer">밖</a></li>
      <li><a href="/in" target="_self">안</a></li>
    </ul>
  );
}
