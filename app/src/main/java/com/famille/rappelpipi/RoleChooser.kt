package com.famille.rappelpipi

import android.app.Activity
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

/** Choix du role de l'appareil au premier lancement + changement de role. */
object RoleChooser {
    fun show(a: Activity) {
        val kit = UiKit(a)
        val col = kit.column()
        col.addView(kit.title(a.getString(R.string.app_name)))
        val card = kit.card(a.getString(R.string.role_title))
        kit.add(col, card, UiKit.S16)
        kit.add(card, kit.help(a.getString(R.string.role_help)), UiKit.S8)
        kit.add(card, kit.button(a.getString(R.string.role_tablet), UiKit.Kind.PRIMARY).apply {
            setOnClickListener {
                Prefs.setRole(a, "tablet")
                a.recreate()
            }
        }, UiKit.S16, UiKit.CONTROL_H)
        kit.add(card, kit.button(a.getString(R.string.role_parent)).apply {
            setOnClickListener { switchTo(a, "parent") }
        }, UiKit.S12, UiKit.CONTROL_H)
        kit.screen(a, col, null)
    }

    private val switching = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Change le role puis ouvre l'ecran correspondant. Quitter le role tablette : on remet l'icone visible, on arrete le
     * rappel, puis (en arriere-plan, delai court, sans bloquer hors ligne) on supprime le document de la tablette
     * avant de fermer sa session.
     */
    fun switchTo(a: Activity, role: String) {
        if (!switching.compareAndSet(false, true)) return
        val app = a.applicationContext
        if (role == "parent") {
            try { Dist.setIconHidden(a, false) } catch (e: Throwable) { }   // sinon plus aucun acces a l'appli (vide en play)
            a.stopService(Intent(a, ReminderService::class.java))
            Prefs.setEnabled(a, false)
            Prefs.setPaused(a, false)
        }
        Thread {
            if (role == "parent") {
                RemoteConfig.deleteOwnDoc(app)   // avant signOut : la session anonyme est necessaire pour supprimer
                Prefs.setPairInfo(app, "", false, "", 0L)
                Prefs.setRemoteRev(app, 0L)
                Prefs.setRemoteUpd(app, 0L)
                Prefs.clearPending(app)
            }
            // Session propre : anonyme de la tablette ou Google du parent.
            try { FirebaseAuth.getInstance().signOut() } catch (e: Throwable) { }
            Prefs.setRole(app, role)
            a.runOnUiThread {
                switching.set(false)
                if (!a.isFinishing && !a.isDestroyed) {
                    a.startActivity(Intent(a, if (role == "parent") ParentActivity::class.java else MainActivity::class.java))
                    a.finish()
                }
            }
        }.start()
    }
}
