package com.ghostassist

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

class GhostAssistPackage : ReactPackage {
    override fun createNativeModules(rc: ReactApplicationContext): List<NativeModule> = listOf(GhostAssistModule(rc))
    override fun createViewManagers(rc: ReactApplicationContext): List<ViewManager<*, *>> = emptyList()
}
