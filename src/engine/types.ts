export type Kind = 'LZ' | 'QL'; // LZ = Li Zhuiyuan (bùa, 1 mục tiêu), QL = Qin Li (tia sét, cả cột)

export interface Companion { kind: Kind; level: number }

/** row 0 = xa nhất, row ROWS-1 = sát Khu Chiến Đấu. width=2 cho Oán Linh to chiếm 2 cột. */
export interface Ghost { hp: number; maxHp: number; row: number; col: number; width: number }

export interface Board {
  ghosts: Ghost[];
  field: (Companion | null)[][]; // [FIELD_ROWS][COLS], hàng 0 là hàng gần Oán Linh
  rest: (Companion | null)[];    // Khu Nghỉ Ngơi (chỉ các ô đã mở khoá)
  runes: { fire: number; ice: number };
  score: number;
  cost: number; // phạt tích luỹ khi dùng rune (chỉ để so sánh nước đi)
}

export type Main =
  | { t: 'pass' }
  | { t: 'place'; rest: number; r: number; c: number }
  | { t: 'move'; fr: number; fc: number; r: number; c: number }
  | { t: 'merge'; fr: number; fc: number; r: number; c: number }
  | { t: 'swap'; fr: number; fc: number; r: number; c: number };

export interface Action { main: Main; fireCol?: number; ice?: boolean }

export interface Suggestion {
  action: Action;
  survives: boolean;     // có đường sống trong tầm nhìn tìm kiếm không
  value: number;
  line: Action[];        // chuỗi nước đi dự kiến
}
