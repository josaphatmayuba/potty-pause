package com.famille.rappelpipi

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/** Une tablette appairee au compte parent (document tablets/{id}, voir firebase/DATA_MODEL.md). */
class TabletInfo(
    val id: String,
    val name: String,
    val lastSeenMs: Long,
    val alerts: List<String>,
    val appVersion: String,
    val settings: Map<String, Any?>,
    val appliedRev: Long,
    val appliedAtMs: Long
) {
    val rev: Long get() = (settings["rev"] as? Number)?.toLong() ?: 0L
}

/** Resultat d'une demande d'appairage. */
enum class PairResult { REQUESTED, INVALID_CODE, DENIED, NETWORK }

/**
 * Acces Firestore du mode parent. Appels BLOQUANTS (toujours depuis un thread de fond). Le contrat est dans
 * firebase/firestore.rules : parent = connexion Google ; le parent ne devient proprietaire qu'apres confirmation
 * de la tablette (PIN local) ; settings reecrits en map complete (rev + 1, updatedAt = serverTimestamp).
 */
object ParentRepo {
    private const val NET_TIMEOUT_S = 15L
    private const val ONLINE_MS = 40L * 60_000L

    fun isOnline(t: TabletInfo, now: Long = System.currentTimeMillis()) = t.lastSeenMs > 0 && now - t.lastSeenMs < ONLINE_MS

    private fun <T> await(t: Task<T>): T = Tasks.await(t, NET_TIMEOUT_S, TimeUnit.SECONDS)

    private fun root(e: Throwable): Throwable = if (e is ExecutionException) e.cause ?: e else e

    fun isDenied(e: Throwable): Boolean {
        val x = root(e)
        return x is FirebaseFirestoreException && x.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }

    private fun db() = FirebaseFirestore.getInstance()
    private fun auth() = FirebaseAuth.getInstance()

    /** Parent connecte avec Google (pas la session anonyme d'une tablette). */
    fun currentParent(): FirebaseUser? {
        val u = auth().currentUser ?: return null
        return if (!u.isAnonymous && u.providerData.any { it.providerId == "google.com" }) u else null
    }

    fun signInGoogle(idToken: String): FirebaseUser {
        val r = await(auth().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)))
        return r.user ?: throw IllegalStateException("no user")
    }

    /** Cree users/{uid} s'il est absent ; sinon met a jour e-mail et langue (createdAt n'est jamais reecrit). */
    fun ensureUser(c: Context, u: FirebaseUser) {
        val ref = db().collection("users").document(u.uid)
        val locale = java.util.Locale.getDefault().language.take(10)
        val snap = await(ref.get())
        val f = hashMapOf<String, Any?>("email" to (u.email ?: "").take(254), "locale" to locale)
        if (!snap.exists()) {
            f["createdAt"] = FieldValue.serverTimestamp()
            await(ref.set(f))
        } else {
            await(ref.set(f, com.google.firebase.firestore.SetOptions.merge()))
        }
    }

    fun signOut() {
        try { auth().signOut() } catch (e: Throwable) { }
    }

    private fun toTablet(d: DocumentSnapshot): TabletInfo {
        @Suppress("UNCHECKED_CAST")
        val settings = (d.get("settings") as? Map<String, Any?>) ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val applied = d.get("applied") as? Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val alerts = (d.get("alerts") as? List<Any?>)?.mapNotNull { it as? String } ?: emptyList()
        return TabletInfo(
            id = d.id,
            name = d.getString("name") ?: "",
            lastSeenMs = d.getTimestamp("lastSeen")?.toDate()?.time ?: 0L,
            alerts = alerts,
            appVersion = d.getString("appVersion") ?: "",
            settings = settings,
            appliedRev = (applied?.get("rev") as? Number)?.toLong() ?: 0L,
            appliedAtMs = (applied?.get("at") as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
        )
    }

    /** Tablettes du parent : where ownerUid == uid, limit 20 (les deux sont obligatoires pour les regles). */
    fun listTablets(uid: String): List<TabletInfo> {
        val q = await(db().collection("tablets").whereEqualTo("ownerUid", uid).limit(20).get())
        return q.documents.map { toTablet(it) }.sortedBy { it.name.lowercase() }
    }

    fun getTablet(id: String): TabletInfo? {
        val d = await(db().collection("tablets").document(id).get())
        return if (d.exists()) toTablet(d) else null
    }

    /** Retrouve la tablette d'un code d'appairage : null = code invalide ou expire (permission-denied compris). */
    fun findTabletByCode(code: String): String? {
        return try {
            val d = await(db().collection("pairingCodes").document(code).get())
            if (d.exists()) d.getString("tabletId") else null
        } catch (e: Throwable) {
            if (isDenied(e)) null else throw e
        }
    }

    /** Premier temps de l'appairage : le parent DEMANDE, la tablette confirme ensuite avec son PIN. */
    fun requestPairing(u: FirebaseUser, tabletId: String, code: String, name: String): PairResult {
        return try {
            val f = hashMapOf<String, Any?>(
                "pendingOwnerUid" to u.uid,
                "pendingOwnerEmail" to (u.email ?: ""),
                "pendingAt" to FieldValue.serverTimestamp(),
                "claimCode" to code,
                "name" to name
            )
            await(db().collection("tablets").document(tabletId).update(f))
            PairResult.REQUESTED
        } catch (e: Throwable) {
            if (isDenied(e)) PairResult.DENIED else PairResult.NETWORK
        }
    }

    /** True des que la tablette a confirme : le parent peut alors lire le document (avant, permission refusee). */
    fun isPaired(uid: String, tabletId: String): Boolean {
        return try {
            val d = await(db().collection("tablets").document(tabletId).get())
            d.exists() && d.getString("ownerUid") == uid
        } catch (e: Throwable) {
            if (isDenied(e)) false else throw e
        }
    }

    /**
     * Ecrit nom + reglages en map complete : rev = ancienne rev + 1, updatedAt = serverTimestamp. Seules les cles
     * connues sont envoyees ; iconHidden et updateNow existants sont conserves. Le reste doit deja etre valide.
     */
    fun saveSettings(t: TabletInfo, name: String, values: Map<String, Any?>) {
        val s = HashMap<String, Any?>()
        t.settings["iconHidden"]?.let { s["iconHidden"] = it }
        t.settings["updateNow"]?.let { s["updateNow"] = it }
        for ((k, v) in values) if (v != null) s[k] = v
        s["rev"] = t.rev + 1
        s["updatedAt"] = FieldValue.serverTimestamp()
        val f = hashMapOf<String, Any?>("settings" to s, "name" to name)
        await(db().collection("tablets").document(t.id).update(f))
    }

    fun unpair(tabletId: String) {
        await(db().collection("tablets").document(tabletId).delete())
    }

    enum class DeleteResult { DONE, NEEDS_REAUTH }

    /** Supprime les tablettes, users/{uid}, puis le compte. NEEDS_REAUTH : reconnecter avec Google puis refaire. */
    fun deleteAccount(u: FirebaseUser): DeleteResult {
        // Boucle tant qu'il reste des tablettes (liste limitee a 20).
        for (i in 0 until 10) {
            val l = await(db().collection("tablets").whereEqualTo("ownerUid", u.uid).limit(20).get())
            if (l.isEmpty) break
            for (d in l.documents) await(d.reference.delete())
        }
        try { await(db().collection("users").document(u.uid).delete()) } catch (e: Throwable) { if (!isDenied(e)) throw e }
        return try {
            await(u.delete())
            DeleteResult.DONE
        } catch (e: Throwable) {
            if (root(e) is FirebaseAuthRecentLoginRequiredException) DeleteResult.NEEDS_REAUTH else throw e
        }
    }

    fun reauthenticate(u: FirebaseUser, idToken: String) {
        await(u.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)))
    }
}
