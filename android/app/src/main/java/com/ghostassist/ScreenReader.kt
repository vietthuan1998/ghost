package com.ghostassist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Đọc bàn cờ từ ảnh chụp: OCR các con số (HP / cấp) rồi quy ra ô dựa trên Geo. Gọi từ thread nền. */
class ScreenReader(ctx: Context) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val prefs = ctx.getSharedPreferences("ghost_templates", Context.MODE_PRIVATE)

    private data class Num(val v: Int, val cx: Float, val cy: Float)
    private data class Loc(val row: Int, val col: Int, val width: Int)

    private fun clampRect(r: Rect, w: Int, h: Int) = Rect(
        r.left.coerceIn(0, w - 1), r.top.coerceIn(0, h - 1), r.right.coerceIn(1, w), r.bottom.coerceIn(1, h)
    )

    private fun readNumbers(bmp: Bitmap, rect0: Rect): List<Num> {
        val rect = clampRect(rect0, bmp.width, bmp.height)
        val crop = Bitmap.createBitmap(bmp, rect.left, rect.top, rect.width(), rect.height())
        val big = Bitmap.createScaledBitmap(crop, crop.width * 2, crop.height * 2, true)
        val res = Tasks.await(recognizer.process(InputImage.fromBitmap(big, 0)))
        val out = mutableListOf<Num>()
        for (b in res.textBlocks) for (l in b.lines) for (e in l.elements) {
            val t = e.text.trim()
            val bb = e.boundingBox ?: continue
            val v = when {
                t.matches(Regex("\\d{1,3}")) -> t.toInt()
                t == "I" || t == "l" || t == "|" -> 1
                else -> continue
            }
            out += Num(v, rect.left + bb.exactCenterX() / 2f, rect.top + bb.exactCenterY() / 2f)
        }
        return out
    }

    private fun locate(n: Num, w: Int, h: Int, top: Float, rows: Int, allowWide: Boolean): Loc? {
        val cellW = Geo.CELL_W * w
        val rowH = Geo.ROW_H * h
        val colPos = (n.cx - Geo.GRID_LEFT * w) / cellW - Geo.NUM_DX
        val r = colPos.roundToInt()
        val (col, width) =
            if (!allowWide || abs(colPos - r) <= 0.25f) r to 1 else floor(colPos).toInt() to 2
        val row = floor((n.cy - top * h) / rowH).toInt()
        if (row !in 0 until rows || col < 0 || col + width - 1 >= Geo.COLS) return null
        return Loc(row, col, width)
    }

    // ---- phân loại Li Zhuiyuan / Qin Li bằng ảnh mẫu thu nhỏ 8x8 ----
    private fun signature(bmp: Bitmap, rf: RectF): FloatArray {
        val r = clampRect(Rect(rf.left.toInt(), rf.top.toInt(), rf.right.toInt(), (rf.top + rf.height() * 0.65f).toInt()), bmp.width, bmp.height)
        val s = Bitmap.createScaledBitmap(Bitmap.createBitmap(bmp, r.left, r.top, r.width(), r.height()), 8, 8, true)
        val out = FloatArray(8 * 8 * 3)
        var i = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            val p = s.getPixel(x, y)
            out[i++] = ((p shr 16) and 255) / 255f; out[i++] = ((p shr 8) and 255) / 255f; out[i++] = (p and 255) / 255f
        }
        return out
    }

    private fun loadTpl(kind: String): FloatArray? =
        prefs.getString("tpl_$kind", null)?.let { s -> JSONArray(s).let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } } }

    fun learnTemplate(bmp: Bitmap, kind: String, zone: String, a: Int, b: Int) {
        val rf = if (zone == "rest") Geo.restRect(a, bmp.width, bmp.height) else Geo.fieldRect(a, b, bmp.width, bmp.height)
        val sig = signature(bmp, rf)
        prefs.edit().putString("tpl_$kind", JSONArray(sig.map { it.toDouble() }).toString()).apply()
    }

    private fun classify(bmp: Bitmap, rf: RectF): String {
        val lz = loadTpl("LZ"); val ql = loadTpl("QL")
        if (lz == null || ql == null) return "LZ" // chưa học Qin Li -> coi tất cả là Li Zhuiyuan
        val s = signature(bmp, rf)
        fun d(t: FloatArray) = s.indices.sumOf { abs(s[it] - t[it]).toDouble() }
        return if (d(lz) <= d(ql)) "LZ" else "QL"
    }

    private fun comp(kind: String, level: Int) = JSONObject().put("kind", kind).put("level", level)

    private fun parseCount(bmp: Bitmap, rf: RectF): Int {
        val r = Rect((rf.left * bmp.width).toInt(), (rf.top * bmp.height).toInt(), (rf.right * bmp.width).toInt(), (rf.bottom * bmp.height).toInt())
        val rr = clampRect(r, bmp.width, bmp.height)
        val crop = Bitmap.createScaledBitmap(Bitmap.createBitmap(bmp, rr.left, rr.top, rr.width(), rr.height()), rr.width() * 2, rr.height() * 2, true)
        val txt = Tasks.await(recognizer.process(InputImage.fromBitmap(crop, 0))).text
        // dạng "2/1": số đầu coi là số rune còn dùng được (có thể đảo nghĩa nếu game dùng khác)
        return Regex("(\\d+)\\s*/\\s*(\\d+)").find(txt)?.groupValues?.get(1)?.toInt() ?: 0
    }

    fun parse(bmp: Bitmap): JSONObject {
        val w = bmp.width; val h = bmp.height
        val left = ((Geo.GRID_LEFT - 0.02f) * w).toInt()
        val right = ((Geo.GRID_LEFT + Geo.COLS * Geo.CELL_W + 0.02f) * w).toInt()

        // Oán Linh
        val ghostArea = Rect(left, (Geo.GHOST_TOP * h).toInt(), right, ((Geo.GHOST_TOP + Geo.ROWS * Geo.ROW_H) * h).toInt())
        val ghosts = JSONArray()
        for (n in readNumbers(bmp, ghostArea)) {
            val loc = locate(n, w, h, Geo.GHOST_TOP, Geo.ROWS, true) ?: continue
            ghosts.put(JSONObject().put("row", loc.row).put("col", loc.col).put("width", loc.width).put("hp", n.v))
        }

        // Khu Chiến Đấu
        val fieldArea = Rect(left, (Geo.FIELD_TOP * h).toInt(), right, ((Geo.FIELD_TOP + Geo.FIELD_ROWS * Geo.ROW_H) * h).toInt())
        val field = Array(Geo.FIELD_ROWS) { arrayOfNulls<JSONObject>(Geo.COLS) }
        for (n in readNumbers(bmp, fieldArea)) {
            val loc = locate(n, w, h, Geo.FIELD_TOP, Geo.FIELD_ROWS, false) ?: continue
            field[loc.row][loc.col] = comp(classify(bmp, Geo.fieldRect(loc.row, loc.col, w, h)), n.v)
        }
        val fieldJson = JSONArray()
        for (r in field) fieldJson.put(JSONArray().also { a -> r.forEach { a.put(it ?: JSONObject.NULL) } })

        // Khu Nghỉ Ngơi
        val rest = JSONArray()
        for (i in 0 until Geo.REST_SLOTS) {
            val rf = Geo.restRect(i, w, h)
            val corner = Rect((rf.left + rf.width() * 0.55f).toInt(), rf.top.toInt(), rf.right.toInt(), (rf.top + rf.height() * 0.35f).toInt())
            val lv = readNumbers(bmp, corner).firstOrNull()?.v
            rest.put(if (lv == null) JSONObject.NULL else comp(classify(bmp, rf), lv))
        }

        val runes = JSONObject().put("fire", parseCount(bmp, Geo.FIRE_NUM)).put("ice", parseCount(bmp, Geo.ICE_NUM))
        return JSONObject().put("ghosts", ghosts).put("field", fieldJson).put("rest", rest).put("runes", runes)
    }
}
