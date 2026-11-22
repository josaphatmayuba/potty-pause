package com.famille.rappelpipi

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Variante "direct" (APK GitHub) : mise a jour par APK, icone cachable, protection contre la desinstallation.
 * La variante "play" a le meme objet avec des methodes vides (Google Play interdit ces mecanismes).
 */
object Dist {
    const val IS_DIRECT = true
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val ALIAS = "com.famille.rappelpipi.LauncherAlias"

    fun setIconHidden(c: Context, hidden: Boolean) {
        try {
            c.packageManager.setComponentEnabledSetting(
                ComponentName(c.packageName, ALIAS),
                if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Throwable) {
        }
    }

    /** Sections « protection / icone » (protCol) et « mise a jour » (updCol) des reglages ; renvoie le rafraichissement. */
    fun addProtectionSection(a: Activity, protCol: LinearLayout, updCol: LinearLayout, ui: UiKit): () -> Unit {
        val dpm = a.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(a, AdminReceiver::class.java)
        val alias = ComponentName(a.packageName, ALIAS)
        val protStatus = ui.status("")
        val adminBtn = ui.button("")
        val iconStatus = ui.status("")
        val iconBtn = ui.button("")
        fun iconHidden() = a.packageManager.getComponentEnabledSetting(alias) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        fun refreshProt() {
            val on = dpm.isAdminActive(admin)
            val hidden = iconHidden()
            adminBtn.text = a.getString(if (on) R.string.btn_protection_off else R.string.btn_protection_on)
            iconBtn.text = a.getString(if (hidden) R.string.btn_icon_show else R.string.btn_icon_hide)
            protStatus.text = a.getString(R.string.protection_status, a.getString(if (on) R.string.on else R.string.off))
            iconStatus.text = a.getString(R.string.icon_status, a.getString(if (hidden) R.string.icon_hidden else R.string.icon_visible))
        }
        adminBtn.setOnClickListener {
            if (dpm.isAdminActive(admin)) {
                dpm.removeActiveAdmin(admin)
            } else {
                a.startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, a.getString(R.string.admin_explanation))
                })
            }
            adminBtn.postDelayed({ refreshProt() }, 600)
        }
        iconBtn.setOnClickListener {
            if (!iconHidden() && (!Prefs.enabled(a) || Prefs.paused(a))) {
                Toast.makeText(a, a.getString(R.string.toast_start_first), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            setIconHidden(a, !iconHidden())
            refreshProt()
        }
        refreshProt()
        ui.add(protCol, protStatus, UiKit.S16); ui.add(protCol, adminBtn, UiKit.S8, UiKit.CONTROL_H)
        ui.add(protCol, iconStatus, UiKit.S16); ui.add(protCol, iconBtn, UiKit.S8, UiKit.CONTROL_H)
        ui.add(protCol, ui.help(a.getString(R.string.help_icon)), UiKit.S8)
        ui.add(updCol, ui.button(a.getString(R.string.btn_check_update)).apply {
            setOnClickListener { Updater.check(a, true) }
        }, UiKit.S12, UiKit.CONTROL_H)
        return { refreshProt() }
    }

    /** A l'ouverture des reglages : propose la mise a jour s'il y en a une. */
    fun checkOnOpen(a: Activity) = Updater.check(a, false)

    /** Verif quotidienne (thread de fond) : notification « nouvelle version disponible ». */
    fun dailyUpdateCheck(c: Context) {
        val now = System.currentTimeMillis()
        val last = Prefs.lastUpdateCheck(c)
        if (!(now - last >= DAY_MS || last > now)) return
        Prefs.setLastUpdateCheck(c, now)
        Thread {
            val (remote, url) = Updater.fetchLatest() ?: return@Thread
            if (url == null || remote <= Updater.currentCode(c)) return@Thread
            val at = Prefs.notifiedUpdateAt(c)
            val t = System.currentTimeMillis()
            if (Prefs.notifiedUpdate(c) == remote && t - at in 0 until DAY_MS) return@Thread
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("update", c.getString(R.string.upd_channel), NotificationManager.IMPORTANCE_DEFAULT))
            nm.notify(3, Notification.Builder(c, "update")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(c.getString(R.string.upd_notif_title))
                .setContentText(c.getString(R.string.upd_notif_text))
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(
                    c, 1, Intent(c, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                .build())
            Prefs.setNotifiedUpdate(c, remote, t)
        }.start()
    }

    /** Mise a jour demandee a distance (thread de fond) : telecharge puis notifie « touche pour installer ». */
    fun remoteUpdate(c: Context) {
        val (remote, url) = Updater.fetchLatest() ?: return
        if (url == null || remote <= Updater.currentCode(c)) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("update", c.getString(R.string.upd_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val b = Notification.Builder(c, "update").setAutoCancel(true)
        if (!c.packageManager.canRequestPackageInstalls()) {
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(c.getString(R.string.upd_perm_title))
                .setContentText(c.getString(R.string.upd_perm_text))
                .setContentIntent(PendingIntent.getActivity(c, 2, i, flags))
        } else {
            val f = Updater.downloadApk(c, url)
            b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(c.getString(R.string.upd_ready_title))
                .setContentText(c.getString(R.string.upd_ready_text))
                .setContentIntent(PendingIntent.getActivity(c, 3, Updater.installIntent(c, f), flags))
        }
        nm.notify(4, b.build())
    }
}
