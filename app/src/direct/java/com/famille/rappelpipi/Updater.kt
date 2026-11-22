package com.famille.rappelpipi

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mise a jour : lit la derniere "release" GitHub (tag vN, N = versionCode), telecharge l'APK
 * et ouvre l'ecran d'installation Android (un seul appui du parent). Installation silencieuse impossible.
 */
object Updater {
    private const val API = "https://api.github.com/repos/josaphatmayuba/potty-pause/releases/latest"

    fun currentCode(a: android.content.Context): Long =
        a.packageManager.getPackageInfo(a.packageName, 0).longVersionCode

    /** Reseau bloquant (appeler hors thread principal) : (version distante, url APK) ou null si echec. */
    fun fetchLatest(): Pair<Long, String?>? = try {
        val c = URL(API).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        val json = JSONObject(c.inputStream.bufferedReader().readText())
        val remote = json.getString("tag_name").trim().removePrefix("v").toLongOrNull() ?: 0L
        val assets = json.getJSONArray("assets")
        var url: String? = null
        for (i in 0 until assets.length()) {
            val o = assets.getJSONObject(i)
            if (o.getString("name").endsWith(".apk")) url = o.getString("browser_download_url")
        }
        Pair(remote, url)
    } catch (e: Exception) {
        null
    }

    /** Sans Activity (reseau bloquant) : telecharge l'APK dans cacheDir/update/RappelPipi.apk. */
    fun downloadApk(c: android.content.Context, url: String): File {
        val dir = File(c.cacheDir, "update").apply { mkdirs() }
        val f = File(dir, "RappelPipi.apk")
        val part = File(dir, "RappelPipi.apk.part")
        var lastErr: Exception? = null
        for (attempt in 1..3) {
            try {
                part.delete()
                val cn = URL(url).openConnection() as HttpURLConnection
                cn.connectTimeout = 15000; cn.readTimeout = 30000
                val expected = cn.contentLengthLong
                val n = cn.inputStream.use { i -> part.outputStream().use { o -> i.copyTo(o) } }
                if (expected > 0 && n != expected) throw java.io.IOException("incomplet $n/$expected")
                // Le fichier doit etre un APK lisible de cette appli, plus recent que la version installee.
                val info = c.packageManager.getPackageArchiveInfo(part.absolutePath, 0)
                if (info == null || info.packageName != c.packageName || info.longVersionCode <= currentCode(c))
                    throw java.io.IOException("apk invalide")
                f.delete()
                if (!part.renameTo(f)) throw java.io.IOException("rename")
                return f
            } catch (e: Exception) {
                lastErr = e
            }
        }
        part.delete()
        throw lastErr ?: java.io.IOException("download")
    }

    /** Intent d'installation de l'APK telecharge (a mettre dans un PendingIntent). */
    fun installIntent(c: android.content.Context, f: File): Intent {
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.fileprovider", f)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** manual = true : affiche aussi "deja a jour" / les erreurs. */
    fun check(a: Activity, manual: Boolean) {
        Thread {
            try {
                val (remote, url) = fetchLatest() ?: throw java.io.IOException("fetch")
                a.runOnUiThread {
                    if (url != null && remote > currentCode(a)) askInstall(a, remote, url)
                    else if (manual) toast(a, a.getString(R.string.upd_up_to_date))
                }
            } catch (e: Exception) {
                if (manual) a.runOnUiThread { toast(a, a.getString(R.string.upd_failed)) }
            }
        }.start()
    }

    private fun toast(a: Activity, t: String) = Toast.makeText(a, t, Toast.LENGTH_LONG).show()

    private fun askInstall(a: Activity, v: Long, url: String) {
        if (a.isFinishing) return
        android.app.AlertDialog.Builder(a)
            .setTitle(a.getString(R.string.upd_new_version, v))
            .setMessage(a.getString(R.string.upd_ask))
            .setPositiveButton(a.getString(R.string.ok)) { _, _ -> download(a, url) }
            .setNegativeButton(a.getString(R.string.upd_later), null)
            .show()
    }

    private fun download(a: Activity, url: String) {
        if (!a.packageManager.canRequestPackageInstalls()) {
            toast(a, a.getString(R.string.upd_allow_installs))
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            return
        }
        toast(a, a.getString(R.string.upd_downloading))
        Thread {
            try {
                val f = downloadApk(a, url)
                a.runOnUiThread { a.startActivity(installIntent(a, f)) }
            } catch (e: Exception) {
                a.runOnUiThread { toast(a, a.getString(R.string.upd_download_failed)) }
            }
        }.start()
    }
}
