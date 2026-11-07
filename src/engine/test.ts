import { Board } from './types';
import { suggest } from './solver';
import { COLS, FIELD_ROWS } from './rules';

const emptyField = () => Array.from({ length: FIELD_ROWS }, () => new Array(COLS).fill(null));

// Ván ví dụ trong ảnh: 3 Oán Linh cột 2 (HP 2,1,1 ở hàng 0,1,2), 1 Oán Linh to HP4 chiếm cột 3-4 hàng 0
const f = emptyField();
f[0][1] = { kind: 'LZ', level: 1 };
f[1][0] = { kind: 'LZ', level: 1 };
const ex: Board = {
  ghosts: [
    { hp: 2, maxHp: 2, row: 0, col: 2, width: 1 },
    { hp: 1, maxHp: 1, row: 1, col: 2, width: 1 },
    { hp: 1, maxHp: 1, row: 2, col: 2, width: 1 },
    { hp: 4, maxHp: 4, row: 0, col: 3, width: 2 },
  ],
  field: f,
  rest: [{ kind: 'LZ', level: 1 }, null],
  runes: { fire: 2, ice: 2 },
  score: 97, cost: 0,
};
let t = Date.now();
const s1 = suggest(ex);
console.log('Ví dụ ảnh:', JSON.stringify(s1.action), 'sống:', s1.survives, `${Date.now() - t}ms`);
console.log(' dự kiến:', s1.line.map((a) => JSON.stringify(a)).join('\n         '));

// Khẩn cấp: Oán Linh HP9 sát đáy cột 0, không có đồng hành
const e: Board = {
  ghosts: [{ hp: 9, maxHp: 9, row: 5, col: 0, width: 1 }],
  field: emptyField(), rest: [{ kind: 'LZ', level: 1 }, null],
  runes: { fire: 1, ice: 1 }, score: 0, cost: 0,
};
t = Date.now();
const s2 = suggest(e);
console.log('Khẩn cấp:', JSON.stringify(s2.action), 'sống:', s2.survives, `${Date.now() - t}ms`);

// Hợp nhất: 2 LZ lv1 cùng loại, ghost xa -> kỳ vọng merge/đặt hợp lý
const f3 = emptyField(); f3[0][2] = { kind: 'LZ', level: 1 }; f3[1][2] = { kind: 'LZ', level: 1 };
const m: Board = { ghosts: [{ hp: 6, maxHp: 6, row: 2, col: 2, width: 1 }], field: f3, rest: [null, null], runes: { fire: 0, ice: 0 }, score: 0, cost: 0 };
t = Date.now();
const s3 = suggest(m);
console.log('Gộp:', JSON.stringify(s3.action), 'sống:', s3.survives, `${Date.now() - t}ms`);
