// ===== CÁC GIẢ ĐỊNH VỀ LUẬT — hãy đối chiếu với game thật rồi chỉnh tại đây =====
export const ROWS = 6;        // số hàng ô đá Oán Linh đi qua
export const COLS = 5;
export const FIELD_ROWS = 2;  // Khu Chiến Đấu của người chơi

export interface Rules {
  maxLevel: number;
  actionPoints: number;               // điểm mỗi hành động
  /** Tổng công kích theo cấp: gấp đôi mỗi lần nâng cấp (Lv1 = 1). */
  power: (level: number) => number;
  /** Số lá bùa của Li Zhuiyuan theo cấp (tối đa 4). Tổng sát thương chia đều cho các lá. */
  bolts: (level: number) => number;
  qlMultiplier: number;               // hệ số đánh giá Qin Li (đánh cả cột)
  firePenalty: number;                // ngại dùng rune lửa nếu không cần
  icePenalty: number;
}

export const defaultRules: Rules = {
  maxLevel: 10,
  actionPoints: 10,
  power: (l) => 2 ** (l - 1),
  bolts: (l) => Math.min(4, 2 ** (l - 1)),
  qlMultiplier: 1.5,
  firePenalty: 30,
  icePenalty: 20,
};
