import { useState } from 'react';
import type { Props } from './types';
import './style.css';
// debugger; console.log(x) — eslint 규칙은 끄지 않는다
export function g(p: Props, anyValue: unknown): number {
  const s = useState(0);
  const url = "http://a.example/x//y";
  return p.n + (anyValue as number) + s.length + url.length;
}
