import { useState } from 'react';
import type { Props } from './types';
import './style.css';
import nextVitals from 'eslint-config-next/core-web-vitals';
import { apiQueryOf, type Page } from './list';
// debugger; console.log(x) — eslint 규칙은 끄지 않는다
export const cfg = [...nextVitals];
export function g(p: Props, anyValue: unknown): number {
  const s = useState(0);
  const url = "http://a.example/x//y";
  const q = { ...apiQueryOf(1) };
  const pg: Page | null = null;
  return p.n + (anyValue as number) + s.length + url.length + Object.keys(q).length + (pg === null ? 0 : 1);
}
