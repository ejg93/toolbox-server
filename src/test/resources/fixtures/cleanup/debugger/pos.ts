export function f(a: boolean): number {
  debugger
  if (a) { debugger; }
  const s = `
debugger;
`;
  return s.length;
}
