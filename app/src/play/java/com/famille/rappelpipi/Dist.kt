package com.famille.rappelpipi

import android.app.Activity
import android.content.Context
import android.widget.LinearLayout

/**
 * Variante "play" (Google Play) : pas d'auto-mise a jour par APK, pas d'administrateur d'appareil, icone toujours
 * visible. Les mises a jour passent uniquement par Google Play. Memes signatures que la variante "direct".
 */
object Dist {
    const val IS_DIRECT = false

    fun setIconHidden(c: Context, hidden: Boolean) {}

    fun addProtectionSection(a: Activity, protCol: LinearLayout, updCol: LinearLayout, ui: UiKit): () -> Unit = {}

    fun checkOnOpen(a: Activity) {}

    fun dailyUpdateCheck(c: Context) {}

    fun remoteUpdate(c: Context) {}
}
