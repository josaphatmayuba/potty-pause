package com.famille.rappelpipi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.security.SecureRandom
import java.util.Date
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * Telecommande : la tablette s'authentifie en anonyme et synchronise son document Firestore tablets/{uid}
 * (contrat : firebase/DATA_MODEL.md + firebase/firestore.rules, v2). Tout est optionnel et silencieux : pas de
 * reseau, pas de compte, regles refusees ou erreur = rien ne change et l'appli continue comme avant.
 * Appels BLOQUANTS : toujours depuis un thread de fond. Aucun PIN n'est jamais envoye au cloud.
 */
object RemoteConfig {
    private const val TABLETS = "tablets"
    private const val CODES = "pairingCodes"
    private const val CODE_TTL_MS = 10L * 60_000L
    private const val PENDING_TTL_MS = 5L * 60_000L
    private const val NET_TIMEOUT_S = 15L

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

    private fun <T> await(t: Task<T>): T = Tasks.await(t, NET_TIMEOUT_S, TimeUnit.SECONDS)

    /** Vrai si l'erreur vient des regles (permission refusee) et pas d'un probleme de reseau. */
    private fun denied(e: Throwable): Boolean {
        val x = if (e is ExecutionException) e.cause ?: e else e
        return x is FirebaseFirestoreException && x.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }

    private fun newCode(): String {
        val r = SecureRandom()
        return (0 until 8).joinToString("") { r.nextInt(10).toString() }
    }

    private fun db() = FirebaseFirestore.getInstance()

    /** Heure du serveur estimee : horloge locale + ecart mesure (les regles comparent a request.time, heure serveur). */
    fun nowMs(c: Context): Long = System.currentTimeMillis() + Prefs.clockOffset(c)

    /** Mesure l'ecart avec l'heure du serveur (en-tete Date d'une reponse HTTPS Google). Echec silencieux : ancien ecart conserve. */
    fun refreshClock(c: Context) {
        try {
            val cn = java.net.URL("https://firestore.googleapis.com/").openConnection() as java.net.HttpURLConnection
            cn.requestMethod = "HEAD"
            cn.connectTimeout = 5000; cn.readTimeout = 5000
            val t0 = System.currentTimeMillis()
            cn.responseCode   // toute reponse HTTP porte l'en-tete Date
            val t1 = System.currentTimeMillis()
            val d = cn.date
            cn.disconnect()
            if (d > 0L) Prefs.setClockOffset(c, d - (t0 + t1) / 2)
        } catch (e: Throwable) {
        }
    }

    /**
     * Avant de quitter le role tablette : supprime tablets/{uid} et son code (sans creer de session), avec un delai court
     * pour ne pas bloquer hors ligne. Appeler depuis un thread de fond.
     */
    fun deleteOwnDoc(c: Context) {
        try {
            val u = FirebaseAuth.getInstance().currentUser ?: return
            if (!u.isAnonymous) return
            val code = Prefs.pairCode(c)
            Tasks.await(db().collection(TABLETS).document(u.uid).delete(), 4, TimeUnit.SECONDS)
            if (code.isNotEmpty()) Tasks.await(db().collection(CODES).document(code).delete(), 4, TimeUnit.SECONDS)
        } catch (e: Throwable) {
        }
    }

    private fun uid(): String? {
        val auth = FirebaseAuth.getInstance()
        return (auth.currentUser ?: await(auth.signInAnonymously()).user)?.uid
    }

    private fun appVersion(c: Context): String =
        try { c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "" } catch (e: Throwable) { "" }

    private fun appCode(c: Context): Long =
        try { c.packageManager.getPackageInfo(c.packageName, 0).longVersionCode } catch (e: Throwable) { 0L }

    private fun alerts(c: Context): List<String> {
        val l = ArrayList<String>()
        try {
            if (!Settings.canDrawOverlays(c)) l.add("overlay_denied")
            if (!Prefs.enabled(c)) l.add("service_stopped")
            val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(c.packageName)) l.add("battery_optimized")
            if (Build.VERSION.SDK_INT >= 33 &&
                c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) l.add("notifications_denied")
        } catch (e: Throwable) {
        }
        return l
    }

    /** Champs d'etat envoyes par la tablette (lastSeen toujours serverTimestamp, exige par les regles). */
    private fun statusFields(c: Context): HashMap<String, Any?> = hashMapOf(
        "lastSeen" to FieldValue.serverTimestamp(),
        "appVersion" to appVersion(c).take(20),
        "appCode" to appCode(c),
        "androidApi" to Build.VERSION.SDK_INT.toLong(),
        "alerts" to alerts(c)
    )

    /**
     * Reserve un code a 8 chiffres dans pairingCodes/{code} (controle d'unicite : une collision avec un code encore
     * valide est refusee par les regles, on en tire un autre). Renvoie (code, expiration ms) ou null (reseau).
     */
    private fun claimCode(c: Context, uid: String): Pair<String, Long>? {
        for (i in 0 until 6) {
            val code = newCode()
            val exp = nowMs(c) + CODE_TTL_MS
            try {
                await(db().collection(CODES).document(code)
                    .set(hashMapOf("tabletId" to uid, "expires" to Timestamp(Date(exp)))))
                return Pair(code, exp)
            } catch (e: Throwable) {
                if (!denied(e)) return null
            }
        }
        return null
    }

    private fun deleteCode(code: String) {
        if (code.isEmpty()) return
        try { await(db().collection(CODES).document(code).delete()) } catch (e: Throwable) { }
    }

    private fun resetLocalRemote(c: Context) {
        Prefs.setRemoteRev(c, 0L)
        Prefs.setRemoteUpd(c, 0L)
        Prefs.clearPending(c)
    }

    /** Cree tablets/{uid} (absent) avec un code d'appairage neuf. */
    private fun createDoc(c: Context, uid: String, ref: DocumentReference): Boolean {
        val (code, exp) = claimCode(c, uid) ?: return false
        val f = statusFields(c)
        f["ownerUid"] = null
        f["createdAt"] = FieldValue.serverTimestamp()
        f["pairCode"] = code
        f["pairExpires"] = Timestamp(Date(exp))
        try { await(ref.set(f)) } catch (e: Throwable) { deleteCode(code); return false }
        resetLocalRemote(c)
        Prefs.setPairInfo(c, uid, false, code, exp)
        return true
    }

    /** Memorise une demande d'appairage valide (< 5 min) pour l'ecran des reglages, sinon l'efface. */
    private fun readPending(c: Context, snap: DocumentSnapshot) {
        val pu = snap.getString("pendingOwnerUid")
        val pe = snap.getString("pendingOwnerEmail")
        val pa = snap.getTimestamp("pendingAt")?.toDate()?.time ?: 0L
        if (!pu.isNullOrEmpty() && !pe.isNullOrEmpty() && pa > 0L && nowMs(c) - pa < PENDING_TTL_MS)
            Prefs.setPending(c, pu, pe, pa)
        else Prefs.clearPending(c)
    }

    private fun codeStillValid(c: Context, snap: DocumentSnapshot): Boolean {
        val exp = snap.getTimestamp("pairExpires")?.toDate()?.time ?: 0L
        return !snap.getString("pairCode").isNullOrEmpty() && exp > nowMs(c) + 60_000L
    }

    /**
     * Synchronisation periodique (15 min, ouverture des reglages). Renvoie true si une mise a jour de l'appli
     * est demandee (updateNow nouveau, variante direct seulement).
     */
    @Synchronized
    fun sync(c: Context): Boolean {
        return try {
            refreshClock(c)
            val uid = uid() ?: return false
            val ref = db().collection(TABLETS).document(uid)
            val snap = try { await(ref.get(Source.SERVER)) } catch (e: Throwable) { return false }
            if (!snap.exists()) { createDoc(c, uid, ref); return false }

            val owner = snap.getString("ownerUid")
            if (owner.isNullOrEmpty()) {
                // Non appairee : on garde un code valide, sinon on en reserve un nouveau.
                readPending(c, snap)
                val f = statusFields(c)
                var code = snap.getString("pairCode") ?: ""
                var exp = snap.getTimestamp("pairExpires")?.toDate()?.time ?: 0L
                if (!codeStillValid(c, snap)) {
                    val old = code
                    val n = claimCode(c, uid)
                    if (n != null) {
                        deleteCode(old)
                        code = n.first; exp = n.second
                        f["pairCode"] = code
                        f["pairExpires"] = Timestamp(Date(exp))
                    }
                }
                try { await(ref.update(f)) } catch (e: Throwable) { return false }
                Prefs.setPairInfo(c, uid, false, code, exp)
                return false
            }

            // Appairee : battement + reglages du parent proprietaire.
            Prefs.clearPending(c)
            val oldCode = Prefs.pairCode(c)
            if (oldCode.isNotEmpty()) deleteCode(oldCode)
            @Suppress("UNCHECKED_CAST")
            val s = snap.get("settings") as? Map<String, Any?>
            val f = statusFields(c)
            var upd = false
            if (s != null) {
                val rev = (s["rev"] as? Number)?.toLong() ?: 0L
                if (rev > Prefs.remoteRev(c)) {
                    applySettings(c, s)
                    Prefs.setRemoteRev(c, rev)
                    f["applied"] = hashMapOf("rev" to rev, "at" to FieldValue.serverTimestamp())
                }
                val u = (s["updateNow"] as? Number)?.toLong() ?: 0L
                if (Dist.IS_DIRECT && u > Prefs.remoteUpd(c)) { Prefs.setRemoteUpd(c, u); upd = true }
            }
            f["alerts"] = alerts(c)
            try { await(ref.update(f)) } catch (e: Throwable) { }
            Prefs.setPairInfo(c, uid, true, "", 0L)
            upd
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Passage leger quand l'ecran des reglages est ouvert (toutes les ~10 s) : detecte une demande d'appairage
     * ou un appairage termine, sans ecrire dans Firestore. Si le doc est absent ou le code perime : sync complete.
     */
    @Synchronized
    fun poll(c: Context) {
        try {
            val uid = uid() ?: return
            val ref = db().collection(TABLETS).document(uid)
            val snap = try { await(ref.get(Source.SERVER)) } catch (e: Throwable) { return }
            if (!snap.exists()) { sync(c); return }
            val owner = snap.getString("ownerUid")
            if (!owner.isNullOrEmpty()) {
                if (!Prefs.paired(c)) sync(c)
                return
            }
            if (!codeStillValid(c, snap)) { sync(c); return }
            readPending(c, snap)
            Prefs.setPairInfo(c, uid, false, snap.getString("pairCode") ?: "",
                snap.getTimestamp("pairExpires")?.toDate()?.time ?: 0L)
        } catch (e: Throwable) {
        }
    }

    /** La tablette confirme la demande (PIN local deja verifie par l'appelant). Vrai si le compte est maintenant proprietaire. */
    @Synchronized
    fun confirmPairing(c: Context): Boolean {
        return try {
            val uid = uid() ?: return false
            val pend = Prefs.pendingUid(c)
            if (pend.isEmpty() || nowMs(c) - Prefs.pendingAt(c) >= PENDING_TTL_MS) return false
            val ref = db().collection(TABLETS).document(uid)
            await(ref.update(hashMapOf<String, Any?>("ownerUid" to pend, "lastSeen" to FieldValue.serverTimestamp())))
            val code = Prefs.pairCode(c)
            Prefs.clearPending(c)
            Prefs.setPairInfo(c, uid, true, "", 0L)
            deleteCode(code)
            true
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Refus d'une demande, ou desappairage depuis la tablette (derriere le PIN) : supprime le doc tablets/{uid}
     * puis le recree avec un nouveau code. Les reglages du parent n'ont plus cours (rev local remis a zero).
     */
    @Synchronized
    fun resetPairing(c: Context): Boolean {
        return try {
            val uid = uid() ?: return false
            val ref = db().collection(TABLETS).document(uid)
            val old = Prefs.pairCode(c)
            await(ref.delete())
            deleteCode(old)
            resetLocalRemote(c)
            Prefs.setPairInfo(c, uid, false, "", 0L)
            createDoc(c, uid, ref)
            true
        } catch (e: Throwable) {
            false
        }
    }

    private fun applySettings(c: Context, s: Map<String, Any?>) {
        (s["interval"] as? Number)?.toInt()?.takeIf { it in 1..600 }?.let { Prefs.setInterval(c, it) }
        (s["lock"] as? Number)?.toInt()?.takeIf { it in 1..120 }?.let { Prefs.setLock(c, it) }
        (s["message"] as? String)?.takeIf { it.length <= 200 }?.let { Prefs.setMessage(c, if (Prefs.isDefaultMessage(c, it)) "" else it) }
        (s["theme"] as? String)?.takeIf { it in themes }?.let { Prefs.setTheme(c, it) }
        s["anim"]?.toString()?.takeIf { it in anims }?.let { Prefs.setBlocsAnim(c, it) }
        (s["character"] as? String)?.takeIf { validCharacter(it) }?.let { Prefs.setCharacter(c, it) }
        (s["language"] as? String)?.takeIf { it == "" || it == "fr" || it == "en" }?.let { Prefs.setLanguage(c, it) }
        // Pause / reprise : le service reste vivant en pause (synchro 15 min continue, aucun rappel affiche).
        (s["enabled"] as? Boolean)?.let { on ->
            if (!on) {
                if (Prefs.enabled(c) && !Prefs.paused(c)) {
                    Prefs.setPaused(c, true)
                    Dist.setIconHidden(c, false)   // jamais d'icone cachee pendant la pause
                }
            } else if (Prefs.paused(c)) {
                Prefs.setPaused(c, false)
            } else if (!Prefs.enabled(c) && Settings.canDrawOverlays(c) && Prefs.hasPin(c)) {
                // Premiere mise en route a distance : ne marche que si l'appli est au premier plan (limite Android).
                Prefs.setEnabled(c, true)
                try { c.startForegroundService(Intent(c, ReminderService::class.java)) } catch (e: Throwable) { }
            }
        }
        (s["iconHidden"] as? Boolean)?.let { hide ->
            // On ne cache l'icone que si le rappel tourne et n'est pas en pause (sinon plus aucun acces).
            if (!hide || (Prefs.enabled(c) && !Prefs.paused(c))) Dist.setIconHidden(c, hide)
        }
    }
}
