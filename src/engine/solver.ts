import { Action, Board, Main, Suggestion } from './types';
import { COLS, FIELD_ROWS, ROWS, Rules, defaultRules } from './rules';
import { covers, step } from './sim';

export function genActions(b: Board): Action[] {
  const mains: Main[] = [{ t: 'pass' }];
  const empties: [number, number][] = [];
  const occ: [number, number][] = [];
  for (let r = 0; r < FIELD_ROWS; r++)
    for (let c = 0; c < COLS; c++) (b.field[r][c] ? occ : empties).push([r, c]);

  b.rest.forEach((comp, i) => {
    if (comp) for (const [r, c] of empties) mains.push({ t: 'place', rest: i, r, c });
  });
  for (const [fr, fc] of occ) {
    for (const [r, c] of empties) mains.push({ t: 'move', fr, fc, r, c });
    for (const [r, c] of occ) {
      if (fr === r && fc === c) continue;
      const s = b.field[fr][fc]!, t = b.field[r][c]!;
      if (s.kind === t.kind && s.level === t.level) mains.push({ t: 'merge', fr, fc, r, c });
      else if (fr * COLS + fc < r * COLS + c) mains.push({ t: 'swap', fr, fc, r, c });
    }
  }

  // rune chỉ xét khi có Oán Linh đã gần
  const danger = b.ghosts.some((g) => g.row >= ROWS - 2);
  const fireCols = new Set<number | undefined>([undefined]);
  if (b.runes.fire > 0)
    for (const g of b.ghosts) if (g.row >= ROWS - 3) for (let c = g.col; c < g.col + g.width; c++) fireCols.add(c);
  const iceOpts = b.runes.ice > 0 && danger ? [false, true] : [false];

  const out: Action[] = [];
  for (const main of mains)
    for (const fc of fireCols)
      for (const ice of iceOpts) {
        if (fc !== undefined && ice) continue; // không gộp 2 rune trong 1 nước để giảm nhánh
        out.push({ main, ...(fc !== undefined ? { fireCol: fc } : {}), ...(ice ? { ice } : {}) });
      }
  return out;
}

function key(b: Board): string {
  return JSON.stringify([
    b.field.map((r) => r.map((c) => (c ? c.kind + c.level : '-'))),
    b.rest.map((c) => (c ? c.kind + c.level : '-')),
    b.ghosts.map((g) => [g.row, g.col, g.hp]).sort(),
    b.runes, Math.round(b.score), b.cost,
  ]);
}

/** Đánh giá tĩnh: điểm + sức mạnh - nguy cơ thiếu sát thương trên từng cột. */
export function evaluate(b: Board, rules: Rules): number {
  let power = 0;
  const lz = new Array(COLS).fill(0), ql = new Array(COLS).fill(0);
  for (const row of b.field)
    row.forEach((c, i) => {
      if (!c) return;
      const p = rules.power(c.level);
      power += p;
      if (c.kind === 'LZ') lz[i] += p; else ql[i] += p;
    });

  let threat = 0;
  for (let c = 0; c < COLS; c++) {
    const gs = b.ghosts.filter((g) => covers(g, c)).sort((x, y) => y.row - x.row);
    let need = 0;
    for (const g of gs) {
      const t = ROWS - g.row; // số lần bị bắn trước khi vào Khu Chiến Đấu
      need += Math.max(0, g.hp - ql[c] * t);
      const deficit = Math.max(0, need - lz[c] * t);
      threat += deficit * (1 + g.row);
    }
  }
  let emptyField = 0;
  for (const row of b.field) for (const c of row) if (!c) emptyField++;
  const emptyRest = b.rest.filter((c) => !c).length;

  return b.score + 1.5 * power + 2 * emptyField + 8 * emptyRest - 2 * threat - b.cost;
}

interface Node { board: Board; first: Action | null; line: Action[]; dead: boolean; turns: number; v: number }

export function suggest(
  start: Board,
  opts: { depth?: number; beam?: number; rules?: Rules } = {},
): Suggestion {
  const rules = opts.rules ?? defaultRules;
  const depth = opts.depth ?? 4;
  const beam = opts.beam ?? 80;

  let layer: Node[] = [{ board: start, first: null, line: [], dead: false, turns: 0, v: evaluate(start, rules) }];
  const finished: Node[] = [];

  for (let d = 0; d < depth; d++) {
    const next = new Map<string, Node>();
    for (const n of layer) {
      for (const a of genActions(n.board)) {
        const res = step(n.board, a, rules);
        if (!res) continue;
        const dead = res.over;
        const turns = n.turns + 1;
        const v = dead ? -1e6 + turns * 1000 : evaluate(res.board, rules);
        const node: Node = { board: res.board, first: n.first ?? a, line: [...n.line, a], dead, turns, v };
        if (dead) { finished.push(node); continue; }
        const k = key(res.board);
        const old = next.get(k);
        if (!old || old.v < v) next.set(k, node);
      }
    }
    layer = [...next.values()].sort((x, y) => y.v - x.v).slice(0, beam);
    if (!layer.length) break;
  }

  const all = [...layer, ...finished].sort((x, y) => y.v - x.v);
  const best = all[0];
  if (!best || !best.first) return { action: { main: { t: 'pass' } }, survives: true, value: 0, line: [] };
  return { action: best.first, survives: !best.dead, value: best.v, line: best.line };
}
