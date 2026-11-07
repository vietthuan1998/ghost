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

/** Hành động -> JSON cho lớp phủ (vòng tròn, mũi tên, thẻ hướng dẫn từng bước). */
export function overlayFromSuggestion(s: Suggestion, board: Board): string {
  const a: Action = s.action;
  const m = a.main;
  const out: any = { kind: m.t };
  const steps: string[] = [];

  switch (m.t) {
    case 'place': {
      const comp = board.rest[m.rest];
      out.from = { zone: 'rest', a: m.rest };
      out.to = { zone: 'field', a: m.r, b: m.c };
      steps.push(`Kéo ${who(comp)} từ Khu Nghỉ Ngơi (thẻ ${m.rest + 1}) xuống ${cell(m.r, m.c)}`);
      break;
    }
    case 'move':
      out.from = { zone: 'field', a: m.fr, b: m.fc };
      out.to = { zone: 'field', a: m.r, b: m.c };
      steps.push(`Kéo ${who(board.field[m.fr]?.[m.fc])} từ ${cell(m.fr, m.fc)} sang ${cell(m.r, m.c)}`);
      break;
    case 'merge': {
      const src = board.field[m.fr]?.[m.fc];
      out.from = { zone: 'field', a: m.fr, b: m.fc };
      out.to = { zone: 'field', a: m.r, b: m.c };
      steps.push(`Gộp để lên cấp: kéo ${who(src)} (${cell(m.fr, m.fc)}) vào ${cell(m.r, m.c)}`);
      break;
    }
    case 'swap':
      out.from = { zone: 'field', a: m.fr, b: m.fc };
      out.to = { zone: 'field', a: m.r, b: m.c };
      steps.push(`Đổi chỗ ${cell(m.fr, m.fc)} ⇄ ${cell(m.r, m.c)}`);
      break;
    case 'pass':
      steps.push('Lượt này không cần di chuyển');
  }
  if (a.fireCol !== undefined) { out.fireCol = a.fireCol; steps.push(`Dùng Rune Ngọn Lửa lên cột ${a.fireCol + 1} (vùng đỏ)`); }
  if (a.ice) { out.ice = true; steps.push('Dùng Rune Băng Giá (vùng xanh)'); }
  if (!s.survives) steps.push('⚠ Khó thoát, cân nhắc dùng rune');

  out.steps = steps;
  out.text = steps.join(' + '); // dự phòng cho bản native cũ
  out.hint = 'Làm xong bấm 👻 để cập nhật gợi ý';
  return JSON.stringify(out);
}
