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
export const g = (b: boolean) =>
  console.log("arrow body");
export function h(): string {
  console.log(<p>don't</p>);
  return "x";
}
