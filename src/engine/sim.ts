import { Action, Board, Ghost } from './types';
import { COLS, ROWS, Rules } from './rules';

export const cloneBoard = (b: Board): Board => ({
  ghosts: b.ghosts.map((g) => ({ ...g })),
  field: b.field.map((r) => r.map((c) => (c ? { ...c } : null))),
  rest: b.rest.map((c) => (c ? { ...c } : null)),
  runes: { ...b.runes },
  score: b.score,
  cost: b.cost,
});

export const covers = (g: Ghost, c: number) => g.col <= c && c < g.col + g.width;

/** Áp dụng 1 hành động rồi xử lý hết lượt: đồng hành tấn công -> Oán Linh tiến 1 ô. */
export function step(b: Board, a: Action, rules: Rules): { board: Board; over: boolean } | null {
  const nb = cloneBoard(b);
  const m = a.main;
  const F = nb.field;
  switch (m.t) {
    case 'pass': break;
    case 'place': {
      const c = nb.rest[m.rest];
      if (!c || F[m.r][m.c]) return null;
      F[m.r][m.c] = c; nb.rest[m.rest] = null; nb.score += rules.actionPoints; break;
    }
    case 'move': {
      const s = F[m.fr][m.fc];
      if (!s || F[m.r][m.c]) return null;
      F[m.r][m.c] = s; F[m.fr][m.fc] = null; nb.score += rules.actionPoints; break;
    }
    case 'merge': {
      const s = F[m.fr][m.fc], t = F[m.r][m.c];
      if (!s || !t || (m.fr === m.r && m.fc === m.c)) return null;
      if (s.kind !== t.kind || s.level !== t.level || t.level >= rules.maxLevel) return null;
      t.level += 1; F[m.fr][m.fc] = null; nb.score += rules.actionPoints; break;
    }
    case 'swap': {
      const s = F[m.fr][m.fc], t = F[m.r][m.c];
      if (!s || !t || (s.kind === t.kind && s.level === t.level)) return null;
      F[m.fr][m.fc] = t; F[m.r][m.c] = s; nb.score += rules.actionPoints; break;
    }
  }
  if (a.fireCol !== undefined) {
    if (nb.runes.fire <= 0) return null;
    nb.runes.fire -= 1; nb.cost += rules.firePenalty;
    nb.ghosts = nb.ghosts.filter((g) => !covers(g, a.fireCol!));
  }
  if (a.ice) {
    if (nb.runes.ice <= 0) return null;
    nb.runes.ice -= 1; nb.cost += rules.icePenalty;
  }

  // đồng hành tấn công
  const hit = (g: Ghost, d: number) => {
    g.hp -= d;
    if (g.hp <= 0) nb.score += g.maxHp;
  };
  for (let r = 0; r < F.length; r++) {
    for (let c = 0; c < COLS; c++) {
      const comp = F[r][c];
      if (!comp) continue;
      const alive = () => nb.ghosts.filter((g) => g.hp > 0 && covers(g, c)).sort((x, y) => y.row - x.row);
      const total = rules.power(comp.level);
      if (comp.kind === 'LZ') {
        const n = rules.bolts(comp.level);
        for (let i = 0; i < n; i++) {
          const t = alive()[0];
          if (!t) break;
          hit(t, total / n);
        }
      } else {
        for (const t of alive()) hit(t, total);
      }
    }
  }
  nb.ghosts = nb.ghosts.filter((g) => g.hp > 0);

  // Oán Linh tiến
  let over = false;
  if (!a.ice) {
    for (const g of nb.ghosts) {
      g.row += 1;
      if (g.row >= ROWS) over = true;
    }
  }
  return { board: nb, over };
}
