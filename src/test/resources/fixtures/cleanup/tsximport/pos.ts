import A, { b, c as d } from 'x';
import { e } from 'y'
import {
  f,
  g
} from 'z';
import type { T } from 't';
import './a.css';
import { s } from 's';

export function run(): number {
  const o = { ...s };
  return A + d + g + Object.keys(o).length;
}
