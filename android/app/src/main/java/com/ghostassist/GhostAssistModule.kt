package com.ghostassist

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

class GhostAssistModule(private val rc: ReactApplicationContext) :
    ReactContextBaseJavaModule(rc), ActivityEventListener {

    companion object { @Volatile var instance: GhostAssistModule? = null; const val REQ = 4711 }

    private var pending: Promise? = null

    init { rc.addActivityEventListener(this); instance = this }

    override fun getName() = "GhostAssist"

    @ReactMethod fun hasOverlayPermission(p: Promise) = p.resolve(Settings.canDrawOverlays(rc))

    @ReactMethod fun openOverlaySettings() {
        rc.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${rc.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @ReactMethod fun startCapture(p: Promise) {
        val act = getCurrentActivity() ?: return p.reject("no_activity", "Không có Activity")
        pending = p
        val mpm = rc.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        act.startActivityForResult(mpm.createScreenCaptureIntent(), REQ)
    }

    @ReactMethod fun stop() { rc.stopService(Intent(rc, OverlayService::class.java)) }

    @ReactMethod fun showSuggestion(json: String) { OverlayService.instance?.showSuggestion(json) }

    @ReactMethod fun learnTemplate(kind: String, zone: String, a: Int, b: Int, p: Promise) {
        val s = OverlayService.instance ?: return p.reject("not_running", "Chưa bật dịch vụ")
        s.learn(kind, zone, a, b) { p.resolve(it) }
    }

    @ReactMethod fun setAiConfig(key: String, model: String) { AiConfig.save(rc, key, model) }

    @ReactMethod fun clearAiKey() { AiConfig.clearKey(rc) }

    /** Không trả khoá về JS, chỉ 4 ký tự cuối để người dùng nhận ra. */
    @ReactMethod fun getAiConfig(p: Promise) {
        val k = AiConfig.key(rc)
        val m = Arguments.createMap()
        m.putBoolean("hasKey", k.isNotBlank())
        m.putString("keyTail", if (k.length >= 4) k.takeLast(4) else "")
        m.putString("model", AiConfig.model(rc))
        p.resolve(m)
    }

    @ReactMethod fun addListener(name: String) {}
    @ReactMethod fun removeListeners(count: Int) {}

    fun emitBoard(json: String) {
        rc.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit("GhostBoard", json)
    }

    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQ) return
        if (resultCode == Activity.RESULT_OK && data != null) {
            ContextCompat.startForegroundService(rc, Intent(rc, OverlayService::class.java)
                .putExtra("code", resultCode).putExtra("data", data))
            pending?.resolve(true)
        } else pending?.resolve(false)
        pending = null
    }

    override fun onNewIntent(intent: Intent) {}
}
