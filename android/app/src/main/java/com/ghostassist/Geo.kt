package com.ghostassist

import android.graphics.PointF
import android.graphics.RectF

/**
 * Toạ độ chuẩn hoá (0..1), đo từ ảnh mẫu 942x2047. Nếu lệch trên máy bạn, chỉnh các hằng số ở đây.
 * Ô đá Oán Linh: 6 hàng x 5 cột. Khu Chiến Đấu: 2 hàng x 5 cột. Khu Nghỉ Ngơi: 3 thẻ.
 */
object Geo {
    const val ROWS = 6
    const val COLS = 5
    const val FIELD_ROWS = 2
    const val REST_SLOTS = 3

    const val GRID_LEFT = 110f / 942f
    const val CELL_W = 146.4f / 942f
    const val GHOST_TOP = 362f / 2047f
    const val FIELD_TOP = 1262f / 2047f
    const val ROW_H = 140.5f / 2047f
    /** Số HP/cấp nằm lệch phải tâm ô ~0.12 ô => 0.5 (nửa ô) + 0.12 */
    const val NUM_DX = 0.62f

    const val REST_LEFT = 218f / 942f
    const val REST_PITCH = 175f / 942f
    const val REST_W = 147f / 942f
    const val REST_TOP = 1820f / 2047f
    const val REST_H = 190f / 2047f

    /** Tâm 2 nút bùa (Lửa trên, Băng dưới) và bán kính, theo tỉ lệ chiều rộng màn hình. */
    val FIRE_BTN = PointF(0.894f, 0.8936f)
    val ICE_BTN = PointF(0.894f, 0.962f)
    const val BTN_R = 0.065f

    val FIRE_NUM = RectF(800f / 942f, 1835f / 2047f, 930f / 942f, 1905f / 2047f)
    val ICE_NUM = RectF(800f / 942f, 1965f / 2047f, 930f / 942f, 2035f / 2047f)

    fun ghostCenter(row: Int, col: Int, w: Int, h: Int) =
        PointF((GRID_LEFT + (col + 0.5f) * CELL_W) * w, (GHOST_TOP + (row + 0.5f) * ROW_H) * h)

    fun fieldCenter(row: Int, col: Int, w: Int, h: Int) =
        PointF((GRID_LEFT + (col + 0.5f) * CELL_W) * w, (FIELD_TOP + (row + 0.5f) * ROW_H) * h)

    fun restCenter(i: Int, w: Int, h: Int) =
        PointF((REST_LEFT + i * REST_PITCH + REST_W / 2) * w, (REST_TOP + REST_H / 2) * h)

    fun ghostRect(row: Int, col: Int, width: Int, w: Int, h: Int) = RectF(
        (GRID_LEFT + col * CELL_W) * w, (GHOST_TOP + row * ROW_H) * h,
        (GRID_LEFT + (col + width) * CELL_W) * w, (GHOST_TOP + (row + 1) * ROW_H) * h
    )

    fun fieldRect(row: Int, col: Int, w: Int, h: Int) = RectF(
        (GRID_LEFT + col * CELL_W) * w, (FIELD_TOP + row * ROW_H) * h,
        (GRID_LEFT + (col + 1) * CELL_W) * w, (FIELD_TOP + (row + 1) * ROW_H) * h
    )

    fun restRect(i: Int, w: Int, h: Int) = RectF(
        (REST_LEFT + i * REST_PITCH) * w, REST_TOP * h,
        (REST_LEFT + i * REST_PITCH + REST_W) * w, (REST_TOP + REST_H) * h
    )
}
