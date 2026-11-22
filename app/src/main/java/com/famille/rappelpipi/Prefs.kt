package com.famille.rappelpipi

import android.content.Context

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("rappel", Context.MODE_PRIVATE)

    fun intervalMin(c: Context) = p(c).getInt("interval", 45)
    fun lockMin(c: Context) = p(c).getInt("lock", 5)
    // Message brut enregistre ; "" = defaut localise (voir shownMessage).
    fun message(c: Context) = p(c).getString("message", "")!!

    // Langue : "fr", "en" ou "" (= langue du telephone).
    fun language(c: Context) = p(c).getString("language", "")!!
    fun setLanguage(c: Context, v: String) = p(c).edit().putString("language", v).apply()

    private fun stringIn(c: Context, lang: String, id: Int): String {
        val conf = android.content.res.Configuration(c.resources.configuration)
        conf.setLocale(java.util.Locale(lang))
        return c.createConfigurationContext(conf).getString(id)
    }

    // True si le texte est un des messages par defaut (FR ou EN) : on le stocke vide.
    fun isDefaultMessage(c: Context, text: String): Boolean {
        val t = text.trim()
        return t.isEmpty() || t == stringIn(c, "fr", R.string.default_message) || t == stringIn(c, "en", R.string.default_message)
    }

    // Message a afficher : valeur du parent, ou defaut dans la langue courante du contexte.
    fun shownMessage(c: Context): String {
        val m = message(c)
        return if (isDefaultMessage(c, m)) c.getString(R.string.default_message) else m
    }

    // Contexte avec la langue choisie (ou celui du telephone si "").
    fun localized(c: Context): Context {
        val lang = language(c)
        if (lang.isEmpty()) return c
        val conf = android.content.res.Configuration(c.resources.configuration)
        val loc = java.util.Locale(lang)
        java.util.Locale.setDefault(loc)
        conf.setLocale(loc)
        return c.createConfigurationContext(conf)
    }
    // ---- Code parent : jamais stocke en clair (PBKDF2 + sel). L'ancien code en clair est migre a la volee. ----
    // Nouveaux codes : 40 000 itérations (anciens hash conservés grâce à pinIter) ; la vérification se fait hors thread principal.
    private const val PIN_ITER = 40_000

    private fun pbkdf2(pin: String, salt: ByteArray, iter: Int): ByteArray {
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, iter, 256)
        return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun b64(b: ByteArray) = android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)
    private fun unb64(s: String) = android.util.Base64.decode(s, android.util.Base64.NO_WRAP)

    fun setPin(c: Context, pin: String) {
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        p(c).edit()
            .putString("pinSalt", b64(salt))
            .putString("pinHash", b64(pbkdf2(pin, salt, PIN_ITER)))
            .putInt("pinIter", PIN_ITER)
            .remove("pin")
            .apply()
    }

    private fun migratePin(c: Context) {
        val old = p(c).getString("pin", "") ?: ""
        if (old.isNotEmpty() && (p(c).getString("pinHash", "") ?: "").isEmpty()) setPin(c, old)
    }

    fun hasPin(c: Context): Boolean {
        migratePin(c)
        return (p(c).getString("pinHash", "") ?: "").isNotEmpty()
    }

    fun checkPin(c: Context, input: String): Boolean {
        migratePin(c)
        val h = p(c).getString("pinHash", "") ?: ""
        val s = p(c).getString("pinSalt", "") ?: ""
        if (h.isEmpty() || s.isEmpty() || input.isEmpty()) return false
        return try {
            java.security.MessageDigest.isEqual(unb64(h), pbkdf2(input, unb64(s), p(c).getInt("pinIter", PIN_ITER)))
        } catch (e: Throwable) {
            false
        }
    }

    // ---- Appairage avec le compte parent (Firestore) ----
    fun tabletUid(c: Context) = p(c).getString("tabletUid", "")!!
    fun paired(c: Context) = p(c).getBoolean("paired", false)
    fun pairCode(c: Context) = p(c).getString("pairCode", "")!!
    fun pairExpires(c: Context) = p(c).getLong("pairExpires", 0L)
    fun setPairInfo(c: Context, uid: String, paired: Boolean, code: String, expires: Long) =
        p(c).edit().putString("tabletUid", uid).putBoolean("paired", paired)
            .putString("pairCode", code).putLong("pairExpires", expires).apply()

    // Demande d'appairage d'un compte parent, en attente de confirmation sur la tablette (valable 5 min).
    fun pendingUid(c: Context) = p(c).getString("pendUid", "")!!
    fun pendingEmail(c: Context) = p(c).getString("pendEmail", "")!!
    fun pendingAt(c: Context) = p(c).getLong("pendAt", 0L)
    fun setPending(c: Context, uid: String, email: String, at: Long) =
        p(c).edit().putString("pendUid", uid).putString("pendEmail", email).putLong("pendAt", at).apply()
    fun clearPending(c: Context) = setPending(c, "", "", 0L)

    fun setMessage(c: Context, v: String) = p(c).edit().putString("message", v).apply()

    fun enabled(c: Context) = p(c).getBoolean("enabled", false)

    fun childName(c: Context) = p(c).getString("childName", "")!!
    fun diamonds(c: Context) = p(c).getInt("diamonds", 0)

    // +1 diamant ; a 5 le coffre s'ouvre : on renvoie 5 et le compteur repart a 0.
    fun addDiamond(c: Context): Int {
        val n = diamonds(c) + 1
        return if (n >= 5) { p(c).edit().putInt("diamonds", 0).apply(); 5 }
        else { p(c).edit().putInt("diamonds", n).apply(); n }
    }

    // pin vide = on garde le code actuel.
    fun save(c: Context, interval: Int, lock: Int, message: String, pin: String, name: String) {
        p(c).edit()
            .putInt("interval", interval)
            .putInt("lock", lock)
            .putString("message", message)
            .putString("childName", name)
            .apply()
        if (pin.isNotEmpty()) setPin(c, pin)
    }

    // Fin du blocage en heure murale : survit a un kill du service ou a un reboot (0 = pas de blocage).
    fun lockedUntilWall(c: Context) = p(c).getLong("lockedUntilWall", 0L)
    fun setLockedUntilWall(c: Context, v: Long) = p(c).edit().putLong("lockedUntilWall", v).apply()

    // Personnage : "hasard" (tous), "fam:mc", "fam:sp", "mc:0".."mc:17", "sp:1".."sp:12".
    // Ancienne valeur "0".."17" migree vers "mc:N".
    fun character(c: Context): String {
        val v = p(c).getString("character", "hasard")!!
        val n = v.toIntOrNull()
        if (n != null) {
            val m = "mc:" + n.coerceIn(0, 17)
            p(c).edit().putString("character", m).apply()
            return m
        }
        return v
    }
    fun setCharacter(c: Context, v: String) = p(c).edit().putString("character", v).apply()

    // Identifiant tire selon le choix ; jamais le meme deux fois de suite quand c'est aleatoire.
    fun nextCharacter(c: Context): String {
        val ch = character(c)
        if (ch.startsWith("mc:") || ch.startsWith("sp:")) return ch
        val pool = ArrayList<String>()
        if (ch != "fam:sp") for (i in 0..17) pool.add("mc:$i")
        if (ch != "fam:mc") for (i in 1..12) pool.add("sp:$i")
        val last = p(c).getString("charLast", "")!!
        var n = pool[kotlin.random.Random.nextInt(pool.size)]
        if (n == last && pool.size > 1) {
            val k = pool.indexOf(n)
            n = pool[(k + 1 + kotlin.random.Random.nextInt(pool.size - 1)) % pool.size]
        }
        p(c).edit().putString("charLast", n).apply()
        return n
    }

    // Heures de fonctionnement (minutes depuis minuit, heure locale).
    fun activeFrom(c: Context) = p(c).getInt("activeFrom", 7 * 60)
    fun activeTo(c: Context) = p(c).getInt("activeTo", 20 * 60)
    fun activeLimited(c: Context) = p(c).getBoolean("activeLimited", true)
    fun setActive(c: Context, from: Int, to: Int, limited: Boolean) =
        p(c).edit().putInt("activeFrom", from).putInt("activeTo", to).putBoolean("activeLimited", limited).apply()

    // True si l'heure actuelle est dans la plage (plage qui traverse minuit si debut > fin ; debut == fin = toujours).
    fun inActiveHours(c: Context): Boolean {
        if (!activeLimited(c)) return true
        val cal = java.util.Calendar.getInstance()
        val m = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val f = activeFrom(c); val t = activeTo(c)
        return when {
            f == t -> true
            f < t -> m >= f && m < t
            else -> m >= f || m < t
        }
    }

    fun setEnabled(c: Context, value: Boolean) = p(c).edit().putBoolean("enabled", value).apply()

    // Theme de l'ecran de rappel ("blocs", une idee, ou "hasard").
    fun theme(c: Context) = p(c).getString("theme", "blocs")!!
    // Animation du theme blocs : "hasard" ou "0".."5".
    fun blocsAnim(c: Context) = p(c).getString("blocsAnim", "hasard")!!
    // Image choisie pour le theme photo (URI persistante), ou null.
    fun imageUri(c: Context): String? = p(c).getString("imageUri", null)
    // Dernier theme affiche (pour ne pas tirer deux fois le meme au hasard).
    fun lastTheme(c: Context) = p(c).getString("lastTheme", "")!!

    // Verification auto des mises a jour : derniere verif, derniere version notifiee et quand.
    fun lastUpdateCheck(c: Context) = p(c).getLong("updCheck", 0L)
    fun setLastUpdateCheck(c: Context, v: Long) = p(c).edit().putLong("updCheck", v).apply()
    fun notifiedUpdate(c: Context) = p(c).getLong("updNotifiedVer", 0L)
    fun notifiedUpdateAt(c: Context) = p(c).getLong("updNotifiedAt", 0L)
    fun setNotifiedUpdate(c: Context, ver: Long, at: Long) =
        p(c).edit().putLong("updNotifiedVer", ver).putLong("updNotifiedAt", at).apply()

    // Config a distance : derniere revision appliquee et derniere commande "updateNow" traitee.
    fun setInterval(c: Context, v: Int) = p(c).edit().putInt("interval", v).apply()
    fun setLock(c: Context, v: Int) = p(c).edit().putInt("lock", v).apply()
    fun remoteRev(c: Context) = p(c).getLong("remoteRev", 0L)
    fun setRemoteRev(c: Context, v: Long) = p(c).edit().putLong("remoteRev", v).apply()
    fun remoteUpd(c: Context) = p(c).getLong("remoteUpd", 0L)
    fun setRemoteUpd(c: Context, v: Long) = p(c).edit().putLong("remoteUpd", v).apply()

    fun setTheme(c: Context, v: String) = p(c).edit().putString("theme", v).apply()
    fun setBlocsAnim(c: Context, v: String) = p(c).edit().putString("blocsAnim", v).apply()
    fun setImageUri(c: Context, v: String?) = p(c).edit().putString("imageUri", v).apply()
    fun setLastTheme(c: Context, v: String) = p(c).edit().putString("lastTheme", v).apply()

    // Role de cet appareil : "tablet" (enfant), "parent" ou "" (pas encore choisi).
    // Une installation existante (code parent defini, rappel demarre ou reglages deja enregistres) reste en "tablet".
    fun role(c: Context): String {
        val r = p(c).getString("role", "") ?: ""
        if (r.isNotEmpty()) return r
        if (hasPin(c) || enabled(c) || p(c).contains("interval") || p(c).contains("tabletUid")) {
            p(c).edit().putString("role", "tablet").apply()
            return "tablet"
        }
        return ""
    }
    fun setRole(c: Context, v: String) = p(c).edit().putString("role", v).apply()

    // Pause demandee par le parent a distance : le service reste vivant (synchro 15 min continue) mais n'affiche rien.
    fun paused(c: Context) = p(c).getBoolean("paused", false)
    fun setPaused(c: Context, v: Boolean) = p(c).edit().putBoolean("paused", v).apply()

    // Ecart (ms) entre l'heure du serveur et celle de la tablette ; sert aux expirations de codes et de demandes.
    fun clockOffset(c: Context) = p(c).getLong("clockOffset", 0L)
    fun setClockOffset(c: Context, v: Long) = p(c).edit().putLong("clockOffset", v).apply()
}
