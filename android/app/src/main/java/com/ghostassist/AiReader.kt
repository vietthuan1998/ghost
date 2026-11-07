package com.ghostassist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Khoá API + model, lưu trong SharedPreferences riêng của app (không bao giờ trả ngược về JS). */
object AiConfig {
    const val DEFAULT_MODEL = "claude-sonnet-5-5"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ghost_ai", Context.MODE_PRIVATE)
    fun key(ctx: Context): String = prefs(ctx).getString("key", "") ?: ""
    fun model(ctx: Context): String = prefs(ctx).getString("model", "")?.ifBlank { null } ?: DEFAULT_MODEL
    fun save(ctx: Context, key: String, model: String) {
        val e = prefs(ctx).edit()
        if (key.isNotBlank()) e.putString("key", key.trim())
        e.putString("model", model.trim())
        e.apply()
    }
    fun clearKey(ctx: Context) { prefs(ctx).edit().remove("key").apply() }
}

/**
 * Đọc bàn cờ bằng Claude (vision) thay cho OCR. Trả về JSON cùng định dạng với ScreenReader.parse
 * (thêm "source":"ai"), nên phần giải & lớp phủ phía sau không đổi. Gọi từ thread nền.
 */
class AiReader(private val ctx: Context) {
    class AiError(msg: String) : Exception(msg)

    fun read(bmp: Bitmap): JSONObject {
        val key = AiConfig.key(ctx)
        if (key.isBlank()) throw AiError("Chưa có khoá API. Nhập khoá trong app Ghost Assist (mục 4).")
        val body = buildRequest(AiConfig.model(ctx), encode(bmp))
        return normalize(extractJson(call(key, body)))
    }

    /** Thu nhỏ còn 900px rộng, che thanh trạng thái (thông báo, giờ...) để không gửi đi, nén JPEG. */
    private fun encode(bmp: Bitmap): String {
        val tw = 900
        val th = (bmp.height * tw.toFloat() / bmp.width).toInt()
        val s = Bitmap.createScaledBitmap(bmp, tw, th, true).copy(Bitmap.Config.ARGB_8888, true)
        Canvas(s).drawRect(0f, 0f, tw.toFloat(), th * 0.045f, Paint().apply { color = Color.BLACK })
        val os = ByteArrayOutputStream()
        s.compress(Bitmap.CompressFormat.JPEG, 82, os)
        return Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP)
    }

    private fun p(x: Float) = String.format(Locale.US, "%.1f%%", x * 100)

    private fun systemPrompt(): String = """
You read screenshots of a mobile grid-defence puzzle game (ghosts walk down a grid, the player's characters shoot them) and output the board state as JSON.
Reply with ONE JSON object only: no markdown, no commentary.

Geometry (fractions of the full image; x is a fraction of the width, y of the height):
- Every grid has 5 columns (0..4, left to right). Column c starts at x = ${p(Geo.GRID_LEFT)} + c * ${p(Geo.CELL_W)}.
- Enemy zone (stone-grey tiles): 6 rows, row 0 at the top. Row r starts at y = ${p(Geo.GHOST_TOP)} + r * ${p(Geo.ROW_H)}.
- Player field (sand-coloured tiles): 2 rows right below the enemy zone. Row r starts at y = ${p(Geo.FIELD_TOP)} + r * ${p(Geo.ROW_H)}.
- Rest area: 3 card slots at the bottom. Slot i starts at x = ${p(Geo.REST_LEFT)} + i * ${p(Geo.REST_PITCH)}, y = ${p(Geo.REST_TOP)}.
- Rune buttons, bottom right: fire (upper) centred at (${p(Geo.FIRE_BTN.x)}, ${p(Geo.FIRE_BTN.y)}), ice (lower) centred at (${p(Geo.ICE_BTN.x)}, ${p(Geo.ICE_BTN.y)}).

Rules:
1. Ghosts: every ghost has a number on a small plate at the BOTTOM of its tile; that number is its HP. Take row and col from the tile where the plate sits, NOT from where the head is (heads stick up into the tile above). A large ghost covers two columns: give its left column and width 2. Empty tiles have no ghost.
2. Player field: each character stands on a gold plate showing its level number. kind "LZ" = Li Zhuiyuan (young man, dark navy hair, glasses, bright yellow shirt). kind "QL" = Qin Li (young woman, brown hair buns, dark purple or brown robe). Ignore any semi-transparent coloured highlight, arrow or text bubble drawn over the screenshot; read the character underneath.
3. Rest area: a slot is either an empty grey silhouette, a locked slot (padlock) or a character card with its level in the top-right corner. Output null for empty or locked slots, else {"kind","level"} (blue-framed female portrait = QL, male with glasses = LZ).
4. Runes: each button is labelled like "3/1". Output the FIRST number for fire and ice.
5. If you cannot read a value, leave that item out (ghost) or null (character) rather than guessing.

Output format (example):
{"ghosts":[{"row":0,"col":1,"width":1,"hp":4}],
 "field":[[null,{"kind":"LZ","level":2},null,null,null],[null,null,null,null,null]],
 "rest":[{"kind":"QL","level":1},null,null],
 "runes":{"fire":3,"ice":3}}
""".trimIndent()

    private fun buildRequest(model: String, b64: String): String {
        val img = JSONObject().put("type", "image").put(
            "source", JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64)
        )
        val txt = JSONObject().put("type", "text").put("text", "Read this screenshot and return the JSON.")
        return JSONObject().put("model", model).put("max_tokens", 1500).put("system", systemPrompt())
            .put("messages", JSONArray().put(
                JSONObject().put("role", "user").put("content", JSONArray().put(img).put(txt))
            )).toString()
    }

    private fun call(key: String, body: String): String {
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 60000; c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            c.setRequestProperty("x-api-key", key)
            c.setRequestProperty("anthropic-version", "2023-06-01")
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val txt = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(txt).getJSONObject("error").getString("message") }
                    .getOrDefault(txt.take(200))
                throw AiError(when (code) {
                    401 -> "Khoá API không hợp lệ (401)."
                    404 -> "Không tìm thấy model \"${AiConfig.model(ctx)}\" (404)."
                    429 -> "Bị giới hạn tốc độ hoặc hết hạn mức (429): $msg"
                    else -> "Lỗi API $code: $msg"
                })
            }
            val content = JSONObject(txt).getJSONArray("content")
            val sb = StringBuilder()
            for (i in 0 until content.length()) {
                val b = content.getJSONObject(i)
                if (b.optString("type") == "text") sb.append(b.getString("text"))
            }
            return sb.toString()
        } finally { c.disconnect() }
    }

    private fun extractJson(t: String): JSONObject {
        val a = t.indexOf('{'); val b = t.lastIndexOf('}')
        if (a < 0 || b <= a) throw AiError("AI trả lời không đúng định dạng.")
        return try { JSONObject(t.substring(a, b + 1)) } catch (e: Exception) { throw AiError("AI trả về JSON lỗi.") }
    }

    /** Lọc giá trị ngoài phạm vi để một lần AI đọc sai không làm hỏng bộ giải. */
    private fun normalize(j: JSONObject): JSONObject {
        val ghosts = JSONArray()
        val ga = j.optJSONArray("ghosts") ?: JSONArray()
        for (i in 0 until ga.length()) {
            val g = ga.optJSONObject(i) ?: continue
            val row = g.optInt("row", -1); val col = g.optInt("col", -1)
            val width = g.optInt("width", 1).coerceIn(1, 2); val hp = g.optInt("hp", 0)
            if (row !in 0 until Geo.ROWS || col < 0 || col + width > Geo.COLS || hp < 1) continue
            ghosts.put(JSONObject().put("row", row).put("col", col).put("width", width).put("hp", hp))
        }
        fun comp(o: JSONObject?): Any {
            if (o == null) return JSONObject.NULL
            val kind = o.optString("kind").uppercase(Locale.US)
            val lv = o.optInt("level", 0)
            return if ((kind == "LZ" || kind == "QL") && lv >= 1) JSONObject().put("kind", kind).put("level", lv)
                   else JSONObject.NULL
        }
        val field = JSONArray()
        val fa = j.optJSONArray("field") ?: JSONArray()
        for (r in 0 until Geo.FIELD_ROWS) {
            val row = fa.optJSONArray(r) ?: JSONArray()
            val out = JSONArray()
            for (c in 0 until Geo.COLS) out.put(comp(row.optJSONObject(c)))
            field.put(out)
        }
        val rest = JSONArray()
        val ra = j.optJSONArray("rest") ?: JSONArray()
        for (i in 0 until Geo.REST_SLOTS) rest.put(comp(ra.optJSONObject(i)))
        val rn = j.optJSONObject("runes")
        val runes = JSONObject().put("fire", rn?.optInt("fire", 0) ?: 0).put("ice", rn?.optInt("ice", 0) ?: 0)
        return JSONObject().put("ghosts", ghosts).put("field", field).put("rest", rest)
            .put("runes", runes).put("source", "ai")
    }
}
