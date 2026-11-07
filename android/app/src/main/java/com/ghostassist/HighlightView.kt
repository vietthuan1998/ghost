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
import kotlin.math.sin

/** Lớp phủ toàn màn hình, không nhận chạm: vẽ vòng tròn nguồn/đích, mũi tên và dòng chữ gợi ý. */
class HighlightView(ctx: Context) : View(ctx) {
    private var sug: JSONObject? = null
    private var message: String? = null

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 44f }

    fun showSuggestion(j: JSONObject) { sug = j; message = j.optString("text"); invalidate() }
    fun showMessage(m: String) { sug = null; message = m; invalidate() }
    fun clear() { sug = null; message = null; invalidate() }

    private fun pt(o: JSONObject, w: Int, h: Int): PointF {
        val a = o.getInt("a"); val b = o.optInt("b")
        return when (o.getString("zone")) {
            "rest" -> Geo.restCenter(a, w, h)
            "ghost" -> Geo.ghostCenter(a, b, w, h)
            else -> Geo.fieldCenter(a, b, w, h)
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width; val h = height
        val s = sug
        val r = w * 0.07f
        if (s != null) {
            if (s.has("fireCol")) {
                val col = s.getInt("fireCol")
                fill.color = 0x55FF3B30
                c.drawRect(
                    (Geo.GRID_LEFT + col * Geo.CELL_W) * w, Geo.GHOST_TOP * h,
                    (Geo.GRID_LEFT + (col + 1) * Geo.CELL_W) * w, (Geo.GHOST_TOP + Geo.ROWS * Geo.ROW_H) * h, fill
                )
            }
            if (s.optBoolean("ice")) {
                fill.color = 0x4400AEEF
                c.drawRect(0f, Geo.GHOST_TOP * h, w.toFloat(), (Geo.GHOST_TOP + Geo.ROWS * Geo.ROW_H) * h, fill)
            }
            val f = s.optJSONObject("from"); val t = s.optJSONObject("to")
            if (f != null) { val p = pt(f, w, h); stroke.color = Color.YELLOW; c.drawCircle(p.x, p.y, r, stroke) }
            if (t != null) { val p = pt(t, w, h); stroke.color = Color.GREEN; c.drawCircle(p.x, p.y, r, stroke) }
            if (f != null && t != null) {
                val a = pt(f, w, h); val b = pt(t, w, h)
                stroke.color = Color.WHITE
                c.drawLine(a.x, a.y, b.x, b.y, stroke)
                val ang = atan2(b.y - a.y, b.x - a.x)
                for (d in listOf(2.6f, -2.6f))
                    c.drawLine(b.x, b.y, b.x + 40 * cos(ang + d), b.y + 40 * sin(ang + d), stroke)
            }
        }
        message?.let {
            val layout = StaticLayout.Builder.obtain(it, 0, it.length, text, w - 80).setAlignment(Layout.Alignment.ALIGN_CENTER).build()
            fill.color = 0xCC000000.toInt()
            val top = 0.075f * h
            c.drawRoundRect(24f, top - 20, w - 24f, top + layout.height + 20, 24f, 24f, fill)
            c.save(); c.translate(40f, top); layout.draw(c); c.restore()
        }
    }
}
