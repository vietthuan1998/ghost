import { Action, Board, Suggestion } from './engine/types';
import { COLS, FIELD_ROWS } from './engine/rules';

/** JSON từ native (ScreenReader.parse) -> Board cho engine. */
export function boardFromNative(json: string, restUnlocked: number): Board {
  const j = JSON.parse(json);
  const ghosts = (j.ghosts as any[]).map((g) => ({
    hp: g.hp, maxHp: g.hp, row: g.row, col: g.col, width: g.width ?? 1,
  }));
  const field = Array.from({ length: FIELD_ROWS }, (_, r) =>
    Array.from({ length: COLS }, (_, c) => j.field?.[r]?.[c] ?? null));
  const rest = (j.rest as any[]).slice(0, restUnlocked).map((c) => c ?? null);
  return { ghosts, field, rest, runes: j.runes ?? { fire: 0, ice: 0 }, score: 0, cost: 0 };
}

const nameOf = (k: string) => (k === 'LZ' ? 'Li Zhuiyuan' : 'Qin Li');

/** Hành động -> JSON cho lớp phủ + câu hướng dẫn tiếng Việt. */
export function overlayFromSuggestion(s: Suggestion, board: Board): string {
  const a: Action = s.action;
  const m = a.main;
  const cell = (r: number, c: number) => `hàng ${r + 1}, cột ${c + 1}`;
  const out: any = {};
  const parts: string[] = [];

  switch (m.t) {
    case 'place': {
      const comp = board.rest[m.rest]!;
      out.from = { zone: 'rest', a: m.rest };
      out.to = { zone: 'field', a: m.r, b: m.c };
      parts.push(`Đặt ${nameOf(comp.kind)} Lv${comp.level} từ Khu Nghỉ Ngơi #${m.rest + 1} → ${cell(m.r, m.c)}`);
      break;
    }
    case 'move':
      out.from = { zone: 'field', a: m.fr, b: m.fc };
      out.to = { zone: 'field', a: m.r, b: m.c };
      parts.push(`Di chuyển ${cell(m.fr, m.fc)} → ${cell(m.r, m.c)}`);
      break;
    case 'merge':
      out.from = { zone: 'field', a: m.fr, b: m.fc };
      out.to = { zone: 'field', a: m.r, b: m.c };
      parts.push(`Nâng cấp: kéo ${cell(m.fr, m.fc)} vào ${cell(m.r, m.c)}`);
      break;
    case 'swap':
      out.from = { zone: 'field', a: m.fr, b: m.fc };
      out.to = { zone: 'field', a: m.r, b: m.c };
      parts.push(`Đổi chỗ ${cell(m.fr, m.fc)} ↔ ${cell(m.r, m.c)}`);
      break;
    case 'pass':
      parts.push('Không cần hành động lượt này');
  }
  if (a.fireCol !== undefined) { out.fireCol = a.fireCol; parts.push(`Dùng Rune Ngọn Lửa cột ${a.fireCol + 1}`); }
  if (a.ice) { out.ice = true; parts.push('Dùng Rune Băng Giá'); }
  if (!s.survives) parts.push('⚠ Khó thoát, nên cân nhắc rune');
  out.text = parts.join(' + ');
  return JSON.stringify(out);
}
