package com.famille.rappelpipi

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import com.google.firebase.auth.FirebaseUser

/**
 * Mode parent : connexion Google, liste des tablettes appairees, ajout par code (appairage en deux temps),
 * fiche par tablette (reglages envoyes a distance) et gestion du compte. Meme style que les reglages (UiKit).
 * Lecture ponctuelle de Firestore (pas d'ecouteur permanent) ; tout echec reseau est affiche sans planter.
 */
class ParentActivity : Activity() {

    private enum class Screen { SIGNIN, HOME, ADD, WAIT, TABLET, BUSY }

    private lateinit var kit: UiKit
    private var user: FirebaseUser? = null
    private var screen = Screen.BUSY
    private var session = 0                       // invalide les reponses asynchrones d'un ecran quitte
    private val ui = Handler(Looper.getMainLooper())

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Prefs.localized(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.SettingsTheme)
        super.onCreate(savedInstanceState)
        kit = UiKit(this)
        val u = runCatching { ParentRepo.currentParent() }.getOrNull()
        if (u == null) showSignIn(null) else { user = u; showHome() }
    }

    override fun onDestroy() {
        session++
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (screen) {
            Screen.ADD, Screen.WAIT, Screen.TABLET -> { session++; if (user != null) showHome() else showSignIn(null) }
            Screen.BUSY -> {}
            else -> super.onBackPressed()
        }
    }

    // ---------- Outils ----------
    private fun bg(block: () -> Unit) { Thread { block() }.start() }
    private fun onUi(s: Int, block: () -> Unit) {
        runOnUiThread { if (!isFinishing && !isDestroyed && s == session) block() }
    }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
    private fun dp(v: Int) = kit.dp(v)

    private fun header(col: android.widget.LinearLayout, titleText: String) {
        col.addView(kit.title(titleText))
    }

    private fun errorText(msg: String) = kit.help(msg).apply {
        setTextColor(kit.c.danger)
        contentDescription = msg
    }

    private fun showBusy(msg: String) {
        screen = Screen.BUSY
        val col = kit.column()
        header(col, getString(R.string.par_title))
        val card = kit.card(null)
        kit.add(col, card, UiKit.S16)
        val bar = ProgressBar(this).apply { isIndeterminate = true; contentDescription = msg }
        kit.add(card, bar, UiKit.S8, UiKit.CONTROL_H)
        kit.add(card, kit.body(msg), UiKit.S12)
        kit.screen(this, col, null)
    }

    private fun confirm(title: String, msg: String, ok: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(msg)
            .setPositiveButton(ok) { _, _ -> action() }
            .setNegativeButton(getString(R.string.cancel), null).show()
    }

    // ---------- Connexion ----------
    private fun showSignIn(error: String?) {
        screen = Screen.SIGNIN
        session++
        val col = kit.column()
        header(col, getString(R.string.app_name))
        val card = kit.card(getString(R.string.par_signin_title))
        kit.add(col, card, UiKit.S16)
        kit.add(card, kit.help(getString(R.string.par_signin_help)), UiKit.S8)
        if (error != null) kit.add(card, errorText(error), UiKit.S12)
        kit.add(card, kit.button(getString(R.string.par_signin_google), UiKit.Kind.PRIMARY).apply {
            setOnClickListener { signIn() }
        }, UiKit.S16, UiKit.CONTROL_H)
        kit.add(card, kit.button(getString(R.string.par_use_as_tablet)).apply {
            setOnClickListener {
                confirm(getString(R.string.par_use_as_tablet), getString(R.string.par_use_as_tablet_confirm), getString(R.string.ok)) {
                    RoleChooser.switchTo(this@ParentActivity, "tablet")
                }
            }
        }, UiKit.S12, UiKit.CONTROL_H)
        kit.screen(this, col, null)
    }

    private fun signIn() {
        showBusy(getString(R.string.par_signing_in))
        val s = session
        GoogleSignIn.getIdToken(this, { token ->
            bg {
                try {
                    val u = ParentRepo.signInGoogle(token)
                    try { ParentRepo.ensureUser(this, u) } catch (e: Throwable) { }
                    onUi(s) { user = u; showHome() }
                } catch (e: Throwable) {
                    onUi(s) { showSignIn(getString(R.string.par_err_signin)) }
                }
            }
        }, { err ->
            onUi(s) {
                when (err) {
                    GoogleSignIn.Err.CANCELLED -> showSignIn(null)
                    GoogleSignIn.Err.NO_ACCOUNT -> showSignIn(getString(R.string.par_err_no_account))
                    GoogleSignIn.Err.NOT_CONFIGURED -> showSignIn(getString(R.string.par_err_not_configured))
                    GoogleSignIn.Err.OTHER -> showSignIn(getString(R.string.par_err_signin))
                }
            }
        })
    }

    // ---------- Accueil : liste des tablettes ----------
    private fun showHome() {
        val u = user ?: return showSignIn(null)
        session++
        val s = session
        showBusy(getString(R.string.par_loading))
        bg {
            val r: List<TabletInfo>? = try { ParentRepo.listTablets(u.uid) } catch (e: Throwable) { null }
            onUi(s) { renderHome(u, r) }
        }
    }

    private fun lastSeenText(t: TabletInfo): String {
        if (t.lastSeenMs <= 0) return getString(R.string.par_never_seen)
        val rel = DateUtils.getRelativeTimeSpanString(t.lastSeenMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
        return if (ParentRepo.isOnline(t)) getString(R.string.par_online, rel) else getString(R.string.par_offline, rel)
    }

    private fun alertText(a: String): String {
        val rid = resources.getIdentifier("par_alert_$a", "string", packageName)
        return if (rid != 0) getString(rid) else a
    }

    private fun renderHome(u: FirebaseUser, list: List<TabletInfo>?) {
        screen = Screen.HOME
        val col = kit.column()
        header(col, getString(R.string.par_title))
        if (list == null) {
            val c = kit.card(getString(R.string.par_err_load_title))
            kit.add(col, c, UiKit.S16)
            kit.add(c, errorText(getString(R.string.par_err_network)), UiKit.S8)
            kit.add(c, kit.button(getString(R.string.par_retry)).apply { setOnClickListener { showHome() } }, UiKit.S12, UiKit.CONTROL_H)
        } else if (list.isEmpty()) {
            val c = kit.card(getString(R.string.par_empty_title))
            kit.add(col, c, UiKit.S16)
            kit.add(c, kit.help(getString(R.string.par_empty_help)), UiKit.S8)
        } else {
            for (t in list) {
                val name = t.name.ifBlank { getString(R.string.par_unnamed) }
                val c = kit.card(name)
                c.contentDescription = name + ", " + lastSeenText(t)
                kit.add(col, c, UiKit.S16)
                val online = ParentRepo.isOnline(t)
                kit.add(c, kit.status(lastSeenText(t)).apply { setTextColor(if (online) kit.c.ok else kit.c.warn) }, UiKit.S8)
                for (a in t.alerts) kit.add(c, kit.help("• " + alertText(a)).apply { setTextColor(kit.c.warn) }, UiKit.S4)
                kit.add(c, kit.button(getString(R.string.par_manage)).apply {
                    contentDescription = getString(R.string.par_manage) + " " + name
                    setOnClickListener { showTablet(t) }
                }, UiKit.S12, UiKit.CONTROL_H)
            }
        }
        // Compte
        val acc = kit.card(getString(R.string.par_account))
        kit.add(col, acc, UiKit.S16)
        kit.add(acc, kit.help(getString(R.string.par_signed_as, u.email ?: "")), UiKit.S8)
        kit.add(acc, kit.button(getString(R.string.par_refresh)).apply { setOnClickListener { showHome() } }, UiKit.S12, UiKit.CONTROL_H)
        kit.add(acc, kit.button(getString(R.string.par_signout)).apply { setOnClickListener { signOut() } }, UiKit.S8, UiKit.CONTROL_H)
        kit.add(acc, kit.button(getString(R.string.par_use_as_tablet)).apply {
            setOnClickListener {
                confirm(getString(R.string.par_use_as_tablet), getString(R.string.par_use_as_tablet_confirm), getString(R.string.ok)) {
                    RoleChooser.switchTo(this@ParentActivity, "tablet")
                }
            }
        }, UiKit.S8, UiKit.CONTROL_H)
        kit.add(acc, kit.button(getString(R.string.par_delete_account), UiKit.Kind.DANGER).apply {
            setOnClickListener { askDeleteAccount(u) }
        }, UiKit.S16, UiKit.CONTROL_H)
        kit.add(acc, kit.help(getString(R.string.par_delete_help)), UiKit.S8)
        kit.screen(this, col, kit.button(getString(R.string.par_add), UiKit.Kind.PRIMARY).apply {
            setOnClickListener { showAdd(null, "", "") }
        })
    }

    private fun signOut() {
        session++
        ParentRepo.signOut()
        GoogleSignIn.clear(this)
        user = null
        showSignIn(null)
    }

    // ---------- Ajouter une tablette ----------
    private fun showAdd(error: String?, code: String, name: String) {
        screen = Screen.ADD
        session++
        val col = kit.column()
        header(col, getString(R.string.par_add))
        val card = kit.card(getString(R.string.par_add_title))
        kit.add(col, card, UiKit.S16)
        kit.add(card, kit.help(getString(R.string.par_add_help)), UiKit.S8)
        kit.add(card, kit.label(getString(R.string.par_code_label)), UiKit.S16)
        val codeF = kit.field(getString(R.string.par_code_hint), code, true).apply {
            filters = arrayOf(android.text.InputFilter.LengthFilter(8))
            contentDescription = getString(R.string.par_code_label)
        }
        kit.add(card, codeF, UiKit.S4, UiKit.CONTROL_H)
        kit.add(card, kit.label(getString(R.string.par_name_label)), UiKit.S12)
        val nameF = kit.field(getString(R.string.par_name_hint), name, false).apply {
            filters = arrayOf(android.text.InputFilter.LengthFilter(40))
            contentDescription = getString(R.string.par_name_label)
        }
        kit.add(card, nameF, UiKit.S4, UiKit.CONTROL_H)
        kit.add(card, kit.help(getString(R.string.par_name_help)), UiKit.S8)
        if (error != null) kit.add(card, errorText(error), UiKit.S12)
        kit.add(card, kit.button(getString(R.string.cancel)).apply { setOnClickListener { showHome() } }, UiKit.S16, UiKit.CONTROL_H)
        val go = kit.button(getString(R.string.par_validate), UiKit.Kind.PRIMARY).apply {
            setOnClickListener {
                val c = codeF.text.toString().trim()
                val n = nameF.text.toString().trim()
                codeF.error = null; nameF.error = null
                if (c.length != 8 || !c.all { it.isDigit() }) { codeF.error = getString(R.string.par_err_code_format); codeF.requestFocus(); return@setOnClickListener }
                if (n.isEmpty() || n.length > 40) { nameF.error = getString(R.string.par_err_name); nameF.requestFocus(); return@setOnClickListener }
                startPairing(c, n)
            }
        }
        kit.screen(this, col, go)
    }

    private fun startPairing(code: String, name: String) {
        val u = user ?: return showSignIn(null)
        showBusy(getString(R.string.par_checking))
        val s = session
        bg {
            try {
                val tabletId = ParentRepo.findTabletByCode(code)
                if (tabletId == null) {
                    onUi(s) { showAdd(getString(R.string.par_err_code_invalid), code, name) }
                    return@bg
                }
                val r = ParentRepo.requestPairing(u, tabletId, code, name)
                onUi(s) {
                    when (r) {
                        PairResult.REQUESTED -> showWaitConfirm(tabletId, name)
                        PairResult.DENIED -> showAdd(getString(R.string.par_err_pair_denied), code, name)
                        else -> showAdd(getString(R.string.par_err_network), code, name)
                    }
                }
            } catch (e: Throwable) {
                onUi(s) { showAdd(getString(R.string.par_err_network), code, name) }
            }
        }
    }

    // Attente de la confirmation sur la tablette (PIN local) : relecture toutes les 3 s pendant 2 minutes.
    private fun showWaitConfirm(tabletId: String, name: String) {
        val u = user ?: return showSignIn(null)
        screen = Screen.WAIT
        session++
        val s = session
        val col = kit.column()
        header(col, getString(R.string.par_add))
        val card = kit.card(getString(R.string.par_wait_title))
        kit.add(col, card, UiKit.S16)
        kit.add(card, kit.body(getString(R.string.par_wait_msg)), UiKit.S12)
        kit.add(card, kit.help(getString(R.string.par_wait_help, u.email ?: "")), UiKit.S8)
        kit.add(card, ProgressBar(this).apply { isIndeterminate = true; contentDescription = getString(R.string.par_wait_title) },
            UiKit.S16, UiKit.CONTROL_H)
        kit.add(card, kit.button(getString(R.string.cancel)).apply {
            setOnClickListener { session++; showHome() }
        }, UiKit.S16, UiKit.CONTROL_H)
        kit.screen(this, col, null)

        val start = System.currentTimeMillis()
        val poll = object : Runnable {
            override fun run() {
                bg {
                    val paired = try { ParentRepo.isPaired(u.uid, tabletId) } catch (e: Throwable) { false }
                    onUi(s) {
                        when {
                            paired -> { toast(getString(R.string.par_paired_ok, name)); showHome() }
                            System.currentTimeMillis() - start > 2L * 60_000L -> showAdd(getString(R.string.par_err_not_confirmed), "", name)
                            else -> ui.postDelayed(this, 3000L)
                        }
                    }
                }
            }
        }
        ui.postDelayed(poll, 3000L)
    }

    // ---------- Fiche tablette ----------
    private fun showTablet(t0: TabletInfo) {
        screen = Screen.TABLET
        session++
        val t = t0
        val s = t.settings
        val dirty = HashSet<String>()
        val col = kit.column()
        header(col, t.name.ifBlank { getString(R.string.par_unnamed) })

        // Etat
        val st = kit.card(getString(R.string.par_state))
        kit.add(col, st, UiKit.S16)
        val online = ParentRepo.isOnline(t)
        kit.add(st, kit.status(lastSeenText(t)).apply { setTextColor(if (online) kit.c.ok else kit.c.warn) }, UiKit.S8)
        if (t.appVersion.isNotEmpty()) kit.add(st, kit.help(getString(R.string.par_app_version, t.appVersion)), UiKit.S4)
        for (a in t.alerts) kit.add(st, kit.help("• " + alertText(a)).apply { setTextColor(kit.c.warn) }, UiKit.S4)
        val applied = if (t.appliedRev > 0)
            getString(R.string.par_applied, t.appliedRev,
                if (t.appliedAtMs > 0) DateUtils.formatDateTime(this, t.appliedAtMs,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH) else "")
        else getString(R.string.par_applied_none)
        kit.add(st, kit.help(applied), UiKit.S8)
        if (t.rev > t.appliedRev) kit.add(st, kit.help(getString(R.string.par_pending_sync)), UiKit.S4)

        fun watch(key: String, e: EditText) {
            e.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun afterTextChanged(a: Editable?) { dirty.add(key) }
            })
        }

        // Nom
        val nameCard = kit.card(getString(R.string.par_name_label))
        kit.add(col, nameCard, UiKit.S16)
        val nameF = kit.field(getString(R.string.par_name_hint), t.name, false).apply {
            filters = arrayOf(android.text.InputFilter.LengthFilter(40))
        }
        kit.add(nameCard, nameF, UiKit.S12, UiKit.CONTROL_H)

        // Rappel
        val rem = kit.card(getString(R.string.sec_reminder))
        kit.add(col, rem, UiKit.S16)
        val interval = kit.field(getString(R.string.lbl_interval), ((s["interval"] as? Number)?.toInt() ?: 45).toString(), true)
        val lock = kit.field(getString(R.string.lbl_lock), ((s["lock"] as? Number)?.toInt() ?: 5).toString(), true)
        watch("interval", interval); watch("lock", lock)
        kit.add(rem, kit.label(getString(R.string.lbl_interval)), UiKit.S12)
        kit.add(rem, interval, UiKit.S4, UiKit.CONTROL_H)
        kit.add(rem, kit.label(getString(R.string.lbl_lock)), UiKit.S12)
        kit.add(rem, lock, UiKit.S4, UiKit.CONTROL_H)
        val enabled = kit.checkbox(getString(R.string.par_enabled), (s["enabled"] as? Boolean) ?: true)
        enabled.setOnCheckedChangeListener { _, _ -> dirty.add("enabled") }
        kit.add(rem, enabled, UiKit.S12, UiKit.CONTROL_H)
        kit.add(rem, kit.help(getString(R.string.par_enabled_help)), UiKit.S4)

        // Apparence
        val app = kit.card(getString(R.string.sec_appearance))
        kit.add(col, app, UiKit.S16)
        val themeIds = Choices.themeIds
        val animIds = Choices.animIds
        val charIds = Choices.charIds()
        val themeSpin = kit.spinner(themeIds.map { Choices.themeName(this, it) }, themeIds.indexOf((s["theme"] as? String) ?: "blocs"))
        val animSpin = kit.spinner(animIds.map { Choices.animName(this, it) }, animIds.indexOf((s["anim"] as? String) ?: "hasard"))
        val charSpin = kit.spinner(Choices.charLabels(this), charIds.indexOf((s["character"] as? String) ?: "hasard"))
        val animLabel = kit.label(getString(R.string.lbl_anim))
        val charLabel = kit.label(getString(R.string.lbl_character))
        fun refreshAppearance() {
            val th = themeIds[themeSpin.selectedItemPosition.coerceIn(0, themeIds.size - 1)]
            val show = if (th == "blocs" || th == "hasard") View.VISIBLE else View.GONE
            animLabel.visibility = show; animSpin.visibility = show; charLabel.visibility = show; charSpin.visibility = show
        }
        fun spinWatch(key: String, sp: android.widget.Spinner, after: (() -> Unit)? = null) {
            var first = true
            sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (first) first = false else dirty.add(key)
                    after?.invoke()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        spinWatch("theme", themeSpin) { refreshAppearance() }
        spinWatch("anim", animSpin); spinWatch("character", charSpin)
        kit.add(app, kit.label(getString(R.string.lbl_theme)), UiKit.S12)
        kit.add(app, themeSpin, UiKit.S4, UiKit.CONTROL_H)
        kit.add(app, animLabel, UiKit.S12)
        kit.add(app, animSpin, UiKit.S4, UiKit.CONTROL_H)
        kit.add(app, charLabel, UiKit.S12)
        kit.add(app, charSpin, UiKit.S4, UiKit.CONTROL_H)
        refreshAppearance()

        // Message et langue
        val msg = kit.card(getString(R.string.sec_message_lang))
        kit.add(col, msg, UiKit.S16)
        val message = kit.field(getString(R.string.lbl_message), (s["message"] as? String) ?: "", false, multi = true).apply {
            filters = arrayOf(android.text.InputFilter.LengthFilter(200))
        }
        watch("message", message)
        kit.add(msg, kit.label(getString(R.string.lbl_message)), UiKit.S12)
        kit.add(msg, message, UiKit.S4)
        val langIds = Choices.langIds
        val langSpin = kit.spinner(Choices.langLabels(this), langIds.indexOf((s["language"] as? String) ?: ""))
        spinWatch("language", langSpin)
        kit.add(msg, kit.label(getString(R.string.sec_language)), UiKit.S16)
        kit.add(msg, langSpin, UiKit.S4, UiKit.CONTROL_H)

        // Dissocier
        val dang = kit.card(getString(R.string.par_unpair_title))
        kit.add(col, dang, UiKit.S16)
        kit.add(dang, kit.help(getString(R.string.par_unpair_help)), UiKit.S8)
        kit.add(dang, kit.button(getString(R.string.btn_unpair), UiKit.Kind.DANGER).apply {
            setOnClickListener {
                confirm(getString(R.string.btn_unpair), getString(R.string.par_unpair_confirm), getString(R.string.ok)) { unpair(t) }
            }
        }, UiKit.S12, UiKit.CONTROL_H)

        val save = kit.button(getString(R.string.par_save), UiKit.Kind.PRIMARY).apply {
            setOnClickListener {
                val name = nameF.text.toString().trim()
                val i = interval.text.toString().toIntOrNull() ?: 0
                val l = lock.text.toString().toIntOrNull() ?: 0
                nameF.error = null; interval.error = null; lock.error = null
                var bad: EditText? = null
                if (name.isEmpty() || name.length > 40) { nameF.error = getString(R.string.par_err_name); bad = bad ?: nameF }
                if (i !in 1..600) { interval.error = getString(R.string.par_err_range, 1, 600); bad = bad ?: interval }
                if (l !in 1..120) { lock.error = getString(R.string.par_err_range, 1, 120); bad = bad ?: lock }
                if (bad != null) { bad.requestFocus(); return@setOnClickListener }
                // Seuls les champs deja connus de la tablette ou modifies ici sont envoyes (les autres gardent leur valeur locale).
                val v = HashMap<String, Any?>()
                fun want(k: String) = dirty.contains(k) || s.containsKey(k)
                if (want("interval")) v["interval"] = i.toLong()
                if (want("lock")) v["lock"] = l.toLong()
                if (want("enabled")) v["enabled"] = enabled.isChecked
                if (want("theme")) v["theme"] = themeIds[themeSpin.selectedItemPosition.coerceIn(0, themeIds.size - 1)]
                if (want("anim")) v["anim"] = animIds[animSpin.selectedItemPosition.coerceIn(0, animIds.size - 1)]
                val ch = charIds[charSpin.selectedItemPosition.coerceIn(0, charIds.size - 1)]
                if (want("character") && ch.isNotEmpty()) v["character"] = ch
                if (want("message")) v["message"] = message.text.toString().trim().take(200)
                if (want("language")) v["language"] = langIds[langSpin.selectedItemPosition.coerceIn(0, langIds.size - 1)]
                save(t, name, v)
            }
        }
        kit.screen(this, col, save)
    }

    private fun save(t: TabletInfo, name: String, values: Map<String, Any?>) {
        showBusy(getString(R.string.par_saving))
        val s = session
        bg {
            try {
                ParentRepo.saveSettings(t, name, values)
                val fresh = try { ParentRepo.getTablet(t.id) } catch (e: Throwable) { null }
                onUi(s) {
                    toast(getString(R.string.par_saved))
                    if (fresh != null) showTablet(fresh) else showHome()
                }
            } catch (e: Throwable) {
                val denied = ParentRepo.isDenied(e)
                onUi(s) {
                    toast(getString(if (denied) R.string.par_err_save_denied else R.string.par_err_network))
                    showTablet(t)
                }
            }
        }
    }

    private fun unpair(t: TabletInfo) {
        showBusy(getString(R.string.par_saving))
        val s = session
        bg {
            try {
                ParentRepo.unpair(t.id)
                onUi(s) { toast(getString(R.string.par_unpaired)); showHome() }
            } catch (e: Throwable) {
                onUi(s) { toast(getString(R.string.par_err_network)); showTablet(t) }
            }
        }
    }

    // ---------- Suppression du compte et des donnees ----------
    private fun askDeleteAccount(u: FirebaseUser) {
        val word = getString(R.string.par_delete_word)
        val input = EditText(this).apply {
            hint = word
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            minHeight = dp(UiKit.CONTROL_H)
        }
        val d = AlertDialog.Builder(this)
            .setTitle(getString(R.string.par_delete_account))
            .setMessage(getString(R.string.par_delete_confirm, word))
            .setView(input)
            .setPositiveButton(getString(R.string.par_delete_do), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        d.show()
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (input.text.toString().trim().equals(word, ignoreCase = true)) { d.dismiss(); deleteAccount(u) }
            else input.error = getString(R.string.par_delete_mismatch)
        }
    }

    private fun deleteAccount(u: FirebaseUser) {
        showBusy(getString(R.string.par_deleting))
        val s = session
        bg {
            try {
                when (ParentRepo.deleteAccount(u)) {
                    ParentRepo.DeleteResult.DONE -> onUi(s) { afterDelete() }
                    ParentRepo.DeleteResult.NEEDS_REAUTH -> onUi(s) {
                        toast(getString(R.string.par_reauth))
                        GoogleSignIn.getIdToken(this, { token ->
                            bg {
                                try {
                                    ParentRepo.reauthenticate(u, token)
                                    val r = ParentRepo.deleteAccount(u)
                                    onUi(s) { if (r == ParentRepo.DeleteResult.DONE) afterDelete() else renderHome(u, null) }
                                } catch (e: Throwable) {
                                    onUi(s) { toast(getString(R.string.par_err_delete)); showHome() }
                                }
                            }
                        }, { onUi(s) { toast(getString(R.string.par_err_delete)); showHome() } })
                    }
                }
            } catch (e: Throwable) {
                onUi(s) { toast(getString(R.string.par_err_delete)); showHome() }
            }
        }
    }

    private fun afterDelete() {
        ParentRepo.signOut()
        GoogleSignIn.clear(this)
        user = null
        toast(getString(R.string.par_deleted))
        showSignIn(null)
    }
}
