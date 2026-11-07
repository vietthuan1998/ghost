package com.ghostassist

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Lớp phủ toàn màn hình, không nhận chạm: tô ô nguồn/đích, mũi tên và thẻ hướng dẫn từng bước.
 *
 * Toạ độ luôn tính theo KÍCH THƯỚC MÀN HÌNH THẬT (cùng hệ với ảnh chụp) và bù vị trí cửa sổ
 * (getLocationOnScreen), nên không bị lệch khi hệ thống đặt cửa sổ dưới thanh trạng thái / tai thỏ.
 */
class HighlightView(ctx: Context) : View(ctx) {
    private var sug: JSONObject? = null
    private var steps: List<String> = emptyList()
    private var hint: String? = null

    /** Kích thước màn hình thật; 0 = dùng kích thước của view. */
    var refW = 0
    var refH = 0
    private val loc = IntArray(2)

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val hintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB8C4D0.toInt() }
    private val badgeText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }

    fun showSuggestion(j: JSONObject) {
        sug = j
        val arr = j.optJSONArray("steps")
        steps = if (arr != null) List(arr.length()) { arr.getString(it) }
                else listOf(j.optString("text")).filter { it.isNotBlank() }
        hint = j.optString("hint").ifBlank { null }
        invalidate()
    }
    fun showMessage(m: String) { sug = null; steps = listOf(m); hint = null; invalidate() }
    fun clear() { sug = null; steps = emptyList(); hint = null; invalidate() }

    private fun rectOf(o: JSONObject, w: Int, h: Int): RectF {
        val a = o.getInt("a"); val b = o.optInt("b")
        return when (o.getString("zone")) {
            "rest" -> Geo.restRect(a, w, h)
            else -> Geo.fieldRect(a, b, w, h)
        }
    }

    private fun badge(c: Canvas, r: RectF, label: String, color: Int, tp: Float) {
        badgeText.textSize = tp
        val bw = badgeText.measureText(label) + tp
        val bh = tp * 1.6f
        val l = r.left + 6f; val t = r.top + 6f
        fill.color = color
        c.drawRoundRect(l, t, l + bw, t + bh, bh / 2, bh / 2, fill)
        c.drawText(label, l + bw / 2, t + bh / 2 + tp * 0.35f, badgeText)
    }

    private fun head(c: Canvas, tipX: Float, tipY: Float, ang: Float, size: Float, color: Int) {
        val p = Path()
        p.moveTo(tipX, tipY)
        p.lineTo(tipX + size * cos(ang + 2.6f), tipY + size * sin(ang + 2.6f))
        p.lineTo(tipX + size * cos(ang - 2.6f), tipY + size * sin(ang - 2.6f))
        p.close()
        fill.color = color
        c.drawPath(p, fill)
    }

    override fun onDraw(c: Canvas) {
        getLocationOnScreen(loc)
        val w = if (refW > 0) refW else width
        val h = if (refH > 0) refH else height
        c.save()
        c.translate(-loc[0].toFloat(), -loc[1].toFloat())

        val s = sug
        val u = w / 1000f // đơn vị tỉ lệ theo bề rộng màn hình
        if (s != null) {
            val gridBottom = (Geo.GHOST_TOP + Geo.ROWS * Geo.ROW_H) * h
            if (s.has("fireCol")) {
                val col = s.getInt("fireCol")
                fill.color = 0x55FF3B30
                c.drawRect((Geo.GRID_LEFT + col * Geo.CELL_W) * w, Geo.GHOST_TOP * h,
                    (Geo.GRID_LEFT + (col + 1) * Geo.CELL_W) * w, gridBottom, fill)
            }
            if (s.optBoolean("ice")) {
                fill.color = 0x4400AEEF
                c.drawRect(0f, Geo.GHOST_TOP * h, w.toFloat(), gridBottom, fill)
            }
            val f = s.optJSONObject("from"); val t = s.optJSONObject("to")
            val fr = f?.let { rectOf(it, w, h) }; val tr = t?.let { rectOf(it, w, h) }
            val src = 0xFFFFD60A.toInt(); val dst = 0xFF34C759.toInt()
            stroke.strokeWidth = 7f * u
            for ((r, col) in listOf(fr to src, tr to dst)) {
                if (r == null) continue
                val box = RectF(r).apply { inset(3f * u, 3f * u) }
                fill.color = (col and 0x00FFFFFF) or 0x38000000
                c.drawRoundRect(box, 18f * u, 18f * u, fill)
                stroke.color = col
                c.drawRoundRect(box, 18f * u, 18f * u, stroke)
            }
            if (fr != null && tr != null) {
                val kind = s.optString("kind")
                val ax = fr.centerX(); val ay = fr.centerY(); val bx = tr.centerX(); val by = tr.centerY()
                val d = hypot(bx - ax, by - ay)
                val ang = atan2(by - ay, bx - ax)
                val cut = min(min(fr.width(), fr.height()), min(tr.width(), tr.height())) * 0.38f
                val k = if (d > cut * 2.4f) cut else 0f
                val x1 = ax + cos(ang) * k; val y1 = ay + sin(ang) * k
                val x2 = bx - cos(ang) * k; val y2 = by - sin(ang) * k
                val hs = 46f * u
                // viền đen cho nổi trên nền đá, rồi nét trắng
                stroke.strokeWidth = 18f * u; stroke.color = 0xAA000000.toInt()
                c.drawLine(x1, y1, x2 - cos(ang) * hs * 0.6f, y2 - sin(ang) * hs * 0.6f, stroke)
                stroke.strokeWidth = 11f * u; stroke.color = Color.WHITE
                c.drawLine(x1, y1, x2 - cos(ang) * hs * 0.6f, y2 - sin(ang) * hs * 0.6f, stroke)
                head(c, x2, y2, ang, hs, Color.WHITE)
                if (kind == "swap") head(c, x1, y1, ang + Math.PI.toFloat(), hs, Color.WHITE)
            }
            // khoanh nút bùa cần bấm
            fun talisman(pt: PointF, label: String, col: Int) {
                val cx = pt.x * w; val cy = pt.y * h; val rr = Geo.BTN_R * w
                stroke.strokeWidth = 9f * u; stroke.color = col
                c.drawCircle(cx, cy, rr, stroke)
                badgeText.textSize = 26f * u
                val bw = badgeText.measureText(label) + 26f * u; val bh = 42f * u
                val l = cx - rr - bw - 8f * u
                fill.color = col
                c.drawRoundRect(l, cy - bh / 2, l + bw, cy + bh / 2, bh / 2, bh / 2, fill)
                c.drawText(label, l + bw / 2, cy + 9f * u, badgeText)
            }
            if (s.has("fireCol")) talisman(Geo.FIRE_BTN, "BÙA LỬA", 0xFFFF6B35.toInt())
            if (s.optBoolean("ice")) talisman(Geo.ICE_BTN, "BÙA BĂNG", 0xFF32C5FF.toInt())
            val tp = 26f * u
            if (fr != null) badge(c, fr, if (s.optString("kind") == "swap") "A" else "TỪ", 0xFFFFD60A.toInt(), tp)
            if (tr != null) badge(c, tr, when (s.optString("kind")) { "merge" -> "GỘP"; "swap" -> "B"; else -> "ĐẾN" }, 0xFF34C759.toInt(), tp)
        }

        sug?.optJSONArray("tags")?.let { arr ->
            val tsz = 22f * u
            badgeText.textSize = tsz
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val zone = o.getString("zone")
                val a = o.getInt("a"); val b = o.optInt("b")
                val rc = when (zone) {
                    "ghost" -> Geo.ghostRect(a, b, o.optInt("w", 1), w, h)
                    "rest" -> Geo.restRect(a, w, h)
                    else -> Geo.fieldRect(a, b, w, h)
                }
                val label = o.getString("text")
                val tw = badgeText.measureText(label) + tsz; val th = tsz * 1.5f
                val l = rc.left + 6f * u
                val t = if (zone == "field") rc.bottom - th - 6f * u else rc.top + 6f * u
                fill.color = when {
                    zone == "ghost" -> 0xDDFF8A80.toInt()
                    label.startsWith("QL") -> 0xDDD1A8FF.toInt()
                    else -> 0xDDFFE082.toInt()
                }
                c.drawRoundRect(l, t, l + tw, t + th, th / 2, th / 2, fill)
                c.drawText(label, l + tw / 2, t + th / 2 + tsz * 0.35f, badgeText)
            }
        }

        if (steps.isNotEmpty()) {
            text.textSize = 34f * u; hintPaint.textSize = 26f * u
            val pad = 16f * u
            val body = if (steps.size > 1) steps.mapIndexed { i, t -> "${i + 1}. $t" }.joinToString("\n") else steps[0]
            val tw = (w - 2 * (24f * u) - 2 * pad).toInt()
            val lay = StaticLayout.Builder.obtain(body, 0, body.length, text, tw)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            val hl = hint?.let {
                StaticLayout.Builder.obtain(it, 0, it.length, hintPaint, tw).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            }
            val cardH = lay.height + (hl?.height ?: 0) + (if (hl != null) 10f * u else 0f) + 2 * pad
            // đặt ngay trên sân Oán Linh: không che thanh điểm phía trên lẫn bàn cờ
            val bottom = Geo.GHOST_TOP * h - 0.022f * h
            val top = maxOf(bottom - cardH, 0.105f * h)
            val left = 24f * u
            fill.color = 0xEE0E151C.toInt()
            c.drawRoundRect(left, top, w - left, top + cardH, 24f * u, 24f * u, fill)
            c.save(); c.translate(left + pad, top + pad); lay.draw(c)
            if (hl != null) { c.translate(0f, lay.height + 10f * u); hl.draw(c) }
            c.restore()
        }
        c.restore()
    }
}
