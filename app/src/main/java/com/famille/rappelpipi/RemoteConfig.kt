package com.famille.rappelpipi

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Config a distance : lit config.json sur GitHub. Tout est optionnel et silencieux :
 * pas de reseau, fichier absent ou JSON casse = rien ne change. Chaque valeur est validee seule.
 */
object RemoteConfig {
    private const val URL = "https://raw.githubusercontent.com/josaphatmayuba/potty-pause/main/config.json"

    private val themes = setOf(
        "blocs", "geant", "bd", "sablier", "feu", "nuit", "ballon", "carte", "porte",
        "bouton", "aqua", "dino", "fusee", "train", "goutte", "photo", "hasard"
    )
    private val anims = setOf("hasard", "0", "1", "2", "3", "4", "5", "6")

    private fun validCharacter(v: String): Boolean {
        if (v == "hasard" || v == "fam:mc" || v == "fam:sp") return true
        if (v.startsWith("mc:")) return v.substring(3).toIntOrNull()?.let { it in 0..17 } == true
        if (v.startsWith("sp:")) return v.substring(3).toIntOrNull()?.let { it in 1..12 } == true
        return false
    }

    /** Reseau bloquant (hors thread principal) : le JSON ou null en cas d'echec. */
    private fun fetch(): JSONObject? = try {
        val c = URL("$URL?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        if (c.responseCode != 200) null
        else JSONObject(c.inputStream.bufferedReader().readText())
    } catch (e: Throwable) {
        null
    }

    /** Lit et applique la config. Renvoie true si une mise a jour de l'appli est demandee (updateNow nouveau). */
    fun checkAndApply(c: Context): Boolean {
        return try {
            val j = fetch() ?: return false
            applySettings(c, j)
            val upd = j.optLong("updateNow", 0L)
            if (upd > Prefs.remoteUpd(c)) {
                Prefs.setRemoteUpd(c, upd)
                true
            } else false
        } catch (e: Throwable) {
            false
        }
    }

    private fun applySettings(c: Context, j: JSONObject) {
        val rev = j.optLong("rev", 0L)
        if (rev <= Prefs.remoteRev(c)) return
        if (j.has("interval")) j.optInt("interval", -1).takeIf { it in 1..600 }?.let { Prefs.setInterval(c, it) }
        if (j.has("lock")) j.optInt("lock", -1).takeIf { it in 1..120 }?.let { Prefs.setLock(c, it) }
        if (j.has("theme")) j.optString("theme", "").takeIf { it in themes }?.let { Prefs.setTheme(c, it) }
        if (j.has("anim")) j.opt("anim")?.toString()?.takeIf { it in anims }?.let { Prefs.setBlocsAnim(c, it) }
        if (j.has("character")) j.optString("character", "").takeIf { validCharacter(it) }?.let { Prefs.setCharacter(c, it) }
        if (j.has("iconHidden")) {
            val hide = j.optBoolean("iconHidden", false)
            // On ne cache l'icone que si le rappel est demarre (sinon plus aucun acces).
            if (!hide || Prefs.enabled(c)) setIconHidden(c, hide)
        }
        Prefs.setRemoteRev(c, rev)
    }

    fun setIconHidden(c: Context, hidden: Boolean) {
        try {
            c.packageManager.setComponentEnabledSetting(
                ComponentName(c, "${c.packageName}.LauncherAlias"),
                if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Throwable) {
        }
    }
}
