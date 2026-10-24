export function f(a: number): number {
  console.log("start", a);
  console.debug(
    "multi",
    a,
  );
  console.error("kept", a);
  const s = `x
  console.log("in template")
  `;
  if (a > 1) console.info("inline");
  if (a > 2)
    console.log("braceless");
  console.trace("paren in string )", a); // 끝 주석
  return a + s.length;
}
