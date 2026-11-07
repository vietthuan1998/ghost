import { Action, Board, Companion, Suggestion } from './engine/types';
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
const who = (c?: Companion | null) => (c ? `${nameOf(c.kind)} Lv${c.level}` : 'đồng hành');

// "hàng trên" = hàng sát Oán Linh, "hàng dưới" = hàng sát Khu Nghỉ Ngơi; cột đếm từ trái sang phải.
// Tránh nói "hàng 1/hàng 2" vì dễ nhầm với hàng của sân Oán Linh phía trên.
const rowName = (r: number) => (FIELD_ROWS === 2 ? (r === 0 ? 'hàng trên' : 'hàng dưới') : `hàng ${r + 1}`);
const cell = (r: number, c: number) => `${rowName(r)}, cột ${c + 1}`;
// Cùng hàng thì chỉ cần nói cột; khác hàng mới nêu cả hàng.
const route = (fr: number, fc: number, r: number, c: number) =>
  fr === r ? `cột ${fc + 1} → cột ${c + 1} (${rowName(r)})` : `${cell(fr, fc)} → ${cell(r, c)}`;

/** Hành động -> JSON cho lớp phủ (vòng tròn, mũi tên, thẻ hướng dẫn từng bước). */
export function overlayFromSuggestion(s: Suggestion, board: Board, debug = false): string {
  const a: Action = s.action;
  const m = a.main;
  const out: any = { kind: m.t };
  const steps: string[] = [];

  switch (m.t) {
    case 'place': {
      const comp = board.rest[m.rest];
      out.from = { zone: 'rest', a: m.rest };
      out.to = { zone: 'field', a: m.r, b: m.c };
      steps.push(`Đặt ${who(comp)} từ thẻ ${m.rest + 1} → ${cell(m.r, m.c)}`);
      break;
    }
    case 'move':
      out.from = { zone: 'field', a: m.fr, b: m.fc };
      out.to = { zone: 'field', a: m.r, b: m.c };
      steps.push(`Dời ${who(board.field[m.fr]?.[m.fc])}: ${route(m.fr, m.fc, m.r, m.c)}`);
      break;
    case 'merge':
      out.from = { zone: 'field', a: m.fr, b: m.fc };
      out.to = { zone: 'field', a: m.r, b: m.c };
      steps.push(`Gộp ${who(board.field[m.fr]?.[m.fc])}: ${route(m.fr, m.fc, m.r, m.c)}`);
      break;
    case 'swap':
      out.from = { zone: 'field', a: m.fr, b: m.fc };
      out.to = { zone: 'field', a: m.r, b: m.c };
      steps.push(`Đổi chỗ: ${route(m.fr, m.fc, m.r, m.c)}`);
      break;
    case 'pass':
      steps.push('Lượt này không cần di chuyển');
  }
  // Hai bùa nằm ở góc dưới phải: lớp phủ khoanh đúng nút bùa cần bấm.
  if (a.fireCol !== undefined) { out.fireCol = a.fireCol; steps.push(`Bùa Lửa: chọn cột ${a.fireCol + 1}`); }
  if (a.ice) { out.ice = true; steps.push('Dùng Bùa Băng'); }
  if (!s.survives) steps.push('⚠ Khó thoát, cân nhắc dùng bùa');

  if (debug) {
    // nhãn "app đọc được gì" để đối chiếu ngay trên màn hình
    const tags: any[] = [];
    board.ghosts.forEach((g) => tags.push({ zone: 'ghost', a: g.row, b: g.col, w: g.width, text: `HP${g.hp}` }));
    board.field.forEach((row, r) => row.forEach((c, i) => c && tags.push({ zone: 'field', a: r, b: i, text: `${c.kind}${c.level}` })));
    board.rest.forEach((c, i) => c && tags.push({ zone: 'rest', a: i, text: `${c.kind}${c.level}` }));
    out.tags = tags;
  }
  out.steps = steps;
  out.text = steps.join(' + '); // dự phòng cho bản native cũ
  out.hint = 'Xong bấm 👻 để cập nhật';
  return JSON.stringify(out);
}
