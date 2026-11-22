package com.famille.rappelpipi

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val pickImage = 42
    private var imageStatus: TextView? = null
    private var onImageChanged: (() -> Unit)? = null
    private var refreshAll: (() -> Unit)? = null

    private val themeIds = Choices.themeIds
    private val animIds = Choices.animIds

    private fun themeName(id: String): String = Choices.themeName(this, id)

    private fun animName(id: String): String = Choices.animName(this, id)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Prefs.localized(newBase))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun refreshImageStatus() {
        imageStatus?.text = getString(if (Prefs.imageUri(this) != null) R.string.image_chosen else R.string.image_none)
        onImageChanged?.invoke()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickImage || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Prefs.setImageUri(this, uri.toString())
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_image_fail), Toast.LENGTH_LONG).show()
        }
        refreshImageStatus()
    }

    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private var pairRefresh: (() -> Unit)? = null
    private var pendingDialog: AlertDialog? = null
    private val pairBusy = java.util.concurrent.atomic.AtomicBoolean(false)

    // Retour sur le thread principal seulement si l'ecran existe encore (evite les dialogues sur une activite detruite).
    private fun safeUi(block: () -> Unit) = runOnUiThread { if (!isFinishing && !isDestroyed) block() }

    // PBKDF2 est lent : verification du code parent hors thread principal.
    private fun verifyPin(input: String, ok: () -> Unit, bad: () -> Unit) {
        Thread {
            val r = try { Prefs.checkPin(this, input) } catch (e: Throwable) { false }
            safeUi { if (r) ok() else bad() }
        }.start()
    }

    private val pairPoll = object : Runnable {
        override fun run() {
            // Un seul passage a la fois : pas de cumul si le reseau est lent.
            if (!Prefs.paired(this@MainActivity) && pairBusy.compareAndSet(false, true)) {
                Thread {
                    try { RemoteConfig.poll(this@MainActivity) } catch (e: Throwable) { }
                    finally { pairBusy.set(false) }
                    safeUi { pairRefresh?.invoke(); showPendingIfAny() }
                }.start()
            }
            ui.postDelayed(this, 10_000L)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAll?.invoke()
        ui.removeCallbacks(pairPoll)
        if (pairRefresh != null) ui.postDelayed(pairPoll, 10_000L)   // detecte une demande d'appairage pendant que les reglages sont ouverts
    }

    override fun onPause() {
        ui.removeCallbacks(pairPoll)
        super.onPause()
    }

    // Demande le code parent local (jamais envoye au cloud) puis execute l'action ; pas de code defini = action directe.
    private fun askPinThen(message: String, action: () -> Unit) {
        if (!Prefs.hasPin(this)) { action(); return }
        val input = EditText(this).apply {
            hint = getString(R.string.pin_hint)
            minHeight = dp(48)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val d = AlertDialog.Builder(this)
            .setTitle(getString(R.string.pin_title))
            .setMessage(message)
            .setView(input)
            .setPositiveButton(getString(R.string.ok), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        d.show()
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { btn ->
            btn.isEnabled = false
            verifyPin(input.text.toString(),
                { d.dismiss(); action() },
                { btn.isEnabled = true; input.setText(""); input.error = getString(R.string.wrong_code) })
        }
    }

    // « Le compte <email> demande a gerer cette tablette » : on redemande le code parent meme si l'ecran est deja ouvert.
    private fun showPendingIfAny() {
        val email = Prefs.pendingEmail(this)
        if (email.isEmpty() || Prefs.paired(this) || isFinishing || isDestroyed) return
        if (RemoteConfig.nowMs(this) - Prefs.pendingAt(this) >= 5L * 60_000L) return
        if (pendingDialog?.isShowing == true) return
        val hasPin = Prefs.hasPin(this)
        val input = EditText(this).apply {
            hint = getString(R.string.pin_hint)
            minHeight = dp(48)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        fun decline() {
            Thread {
                try { RemoteConfig.resetPairing(this) } catch (e: Throwable) { }
                safeUi { pairRefresh?.invoke() }
            }.start()
        }
        val b = AlertDialog.Builder(this)
            .setTitle(getString(R.string.pend_title))
            .setMessage(getString(R.string.pend_msg, email) + if (hasPin) "" else "\n\n" + getString(R.string.pend_need_pin))
            .setCancelable(false)
            .setNegativeButton(getString(R.string.pend_refuse)) { _, _ -> decline() }
        if (hasPin) b.setView(input).setPositiveButton(getString(R.string.pend_confirm), null)
        val d = b.create()
        d.show()
        pendingDialog = d
        var tries = 0
        d.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener { btn ->
            btn.isEnabled = false
            verifyPin(input.text.toString(), {
                d.dismiss()
                Thread {
                    val ok = try { RemoteConfig.confirmPairing(this) } catch (e: Throwable) { false }
                    safeUi {
                        Toast.makeText(this, getString(if (ok) R.string.pend_done else R.string.pend_failed), Toast.LENGTH_LONG).show()
                        pairRefresh?.invoke()
                    }
                }.start()
            }, {
                btn.isEnabled = true
                tries++
                input.setText("")
                if (tries >= 3) { d.dismiss(); decline() }   // 3 codes faux = demande refusee
                else input.error = getString(R.string.wrong_code)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.SettingsTheme)
        super.onCreate(savedInstanceState)
        // Role : parent = on ouvre le mode parent ; pas encore choisi = ecran de choix (installations existantes = tablette).
        when (Prefs.role(this)) {
            "parent" -> { startActivity(Intent(this, ParentActivity::class.java)); finish(); return }
            "" -> { RoleChooser.show(this); return }
        }
        if (!Prefs.hasPin(this)) {
            buildUi()
        } else {
            askPin()
        }
    }

    private fun askPin() {
        val input = EditText(this).apply {
            hint = getString(R.string.pin_hint)
            minHeight = dp(48)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pin_title))
            .setView(input)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                verifyPin(input.text.toString(), { buildUi() }, { finish() })
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ -> finish() }
            .show()
    }

    private fun field(label: String, value: String, number: Boolean): EditText {
        return EditText(this).apply {
            setText(value)
            hint = label
            minHeight = dp(48)
            if (number) inputType = InputType.TYPE_CLASS_NUMBER
        }
    }

    private fun buildUi() {
        val kit = UiKit(this)
        val col = kit.column()
        val ctrl = UiKit.CONTROL_H
        fun card(title: String) = kit.card(title).also { kit.add(col, it, UiKit.S16) }

        val interval = kit.field(getString(R.string.lbl_interval), Prefs.intervalMin(this).toString(), true)
        val lock = kit.field(getString(R.string.lbl_lock), Prefs.lockMin(this).toString(), true)
        val message = kit.field(getString(R.string.lbl_message), Prefs.shownMessage(this), false, multi = true)
        val pin = kit.field("", "", true).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            typeface = Typeface.DEFAULT   // le mode mot de passe force la police monospace
        }
        val childName = kit.field(getString(R.string.lbl_child_name), Prefs.childName(this), false)

        col.addView(kit.title(getString(R.string.title_settings)))

        // 1. Rappel : etat, minutes, duree, heures
        val cRem = card(getString(R.string.sec_reminder))
        val statusRem = kit.status("")
        val statusBatt = kit.status("")
        kit.add(cRem, statusRem, UiKit.S12); kit.add(cRem, statusBatt, UiKit.S8)
        val startBtn = kit.button("", UiKit.Kind.PRIMARY)
        val stopBtn = kit.button(getString(R.string.btn_stop), UiKit.Kind.DANGER)
        val battBtn = kit.button(getString(R.string.btn_battery)).apply {
            setOnClickListener { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
        kit.add(cRem, battBtn, UiKit.S12, ctrl)
        kit.add(cRem, stopBtn, UiKit.S8, ctrl)

        fun refreshStatus() {
            val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
            val battOk = pm.isIgnoringBatteryOptimizations(packageName)
            val on = Prefs.enabled(this)
            val green = kit.c.ok; val orange = kit.c.warn
            val outside = on && !Prefs.inActiveHours(this)
            val paused = on && Prefs.paused(this)
            statusRem.text = getString(R.string.status_reminder,
                if (paused) getString(R.string.status_paused)
                else if (outside) getString(R.string.status_outside_hours)
                else getString(if (on) R.string.status_active else R.string.status_stopped))
            statusRem.setTextColor(if (on) green else orange)
            statusBatt.text = getString(R.string.status_battery, getString(if (battOk) R.string.battery_ok else R.string.battery_todo))
            statusBatt.setTextColor(if (battOk) green else orange)
            startBtn.text = getString(if (on) R.string.btn_save_restart else R.string.btn_start)
            stopBtn.isEnabled = on
            battBtn.visibility = if (battOk) View.GONE else View.VISIBLE
        }

        kit.add(cRem, kit.label(getString(R.string.lbl_interval)), UiKit.S24)
        kit.add(cRem, interval, UiKit.S4, ctrl)
        kit.add(cRem, kit.label(getString(R.string.lbl_lock)), UiKit.S12)
        kit.add(cRem, lock, UiKit.S4, ctrl)
        var fromMin = Prefs.activeFrom(this)
        var toMin = Prefs.activeTo(this)
        fun hm(m: Int) = "%02d:%02d".format(m / 60, m % 60)
        val limitChk = kit.checkbox(getString(R.string.chk_limit_hours), Prefs.activeLimited(this@MainActivity))
        val fromBtn = kit.button("")
        val toBtn = kit.button("")
        fun refreshHours() {
            fromBtn.text = getString(R.string.lbl_from) + " " + hm(fromMin)
            toBtn.text = getString(R.string.lbl_to) + " " + hm(toMin)
            fromBtn.isEnabled = limitChk.isChecked
            toBtn.isEnabled = limitChk.isChecked
        }
        fromBtn.setOnClickListener {
            android.app.TimePickerDialog(this, { _, h, m -> fromMin = h * 60 + m; refreshHours() }, fromMin / 60, fromMin % 60, true).show()
        }
        toBtn.setOnClickListener {
            android.app.TimePickerDialog(this, { _, h, m -> toMin = h * 60 + m; refreshHours() }, toMin / 60, toMin % 60, true).show()
        }
        limitChk.setOnCheckedChangeListener { _, _ -> refreshHours() }
        refreshHours()
        kit.add(cRem, limitChk, UiKit.S12, ctrl)
        kit.add(cRem, kit.row(fromBtn, toBtn), UiKit.S8, ctrl)
        kit.add(cRem, kit.help(getString(R.string.help_hours)), UiKit.S8)

        // 2. Apparence : theme, animation, personnage, image
        val cApp = card(getString(R.string.sec_appearance))
        val themeSpin = kit.spinner(themeIds.map { themeName(it) }, themeIds.indexOf(Prefs.theme(this@MainActivity)))
        val animSpin = kit.spinner(animIds.map { animName(it) }, animIds.indexOf(Prefs.blocsAnim(this@MainActivity)))
        // "" = separateur non selectionnable (on garde alors la valeur precedente).
        val charIds = Choices.charIds()
        val charLabels = Choices.charLabels(this)
        val charSpin = kit.spinner(charLabels, charIds.indexOf(Prefs.character(this@MainActivity)))
        val charLabel = kit.label(getString(R.string.lbl_character))
        val animLabel = kit.label(getString(R.string.lbl_anim))
        val photoWarn = kit.help(getString(R.string.warn_photo_no_image)).apply { setTextColor(kit.c.warn) }
        val imgStatus = kit.help("")
        imageStatus = imgStatus
        val removeBtn = kit.button(getString(R.string.btn_remove_image)).apply {
            setOnClickListener { Prefs.setImageUri(this@MainActivity, null); refreshImageStatus() }
        }
        fun refreshAppearance() {
            val t = themeIds[themeSpin.selectedItemPosition.coerceIn(0, themeIds.size - 1)]
            val showAnim = t == "blocs" || t == "hasard"
            animLabel.visibility = if (showAnim) View.VISIBLE else View.GONE
            animSpin.visibility = if (showAnim) View.VISIBLE else View.GONE
            charLabel.visibility = if (showAnim) View.VISIBLE else View.GONE
            charSpin.visibility = if (showAnim) View.VISIBLE else View.GONE
            val hasImg = Prefs.imageUri(this) != null
            photoWarn.visibility = if (t == "photo" && !hasImg) View.VISIBLE else View.GONE
            removeBtn.visibility = if (hasImg) View.VISIBLE else View.GONE
        }
        onImageChanged = { refreshAppearance() }
        themeSpin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = refreshAppearance()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        kit.add(cApp, kit.label(getString(R.string.lbl_theme)), UiKit.S12)
        kit.add(cApp, themeSpin, UiKit.S4, ctrl)
        kit.add(cApp, animLabel, UiKit.S12)
        kit.add(cApp, animSpin, UiKit.S4, ctrl)
        kit.add(cApp, charLabel, UiKit.S12)
        kit.add(cApp, charSpin, UiKit.S4, ctrl)
        kit.add(cApp, kit.button(getString(R.string.btn_pick_image)).apply {
            setOnClickListener {
                val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                runCatching { startActivityForResult(pick, pickImage) }
                    .onFailure { Toast.makeText(this@MainActivity, getString(R.string.toast_no_picker), Toast.LENGTH_LONG).show() }
            }
        }, UiKit.S16, ctrl)
        kit.add(cApp, removeBtn, UiKit.S8, ctrl)
        kit.add(cApp, imgStatus, UiKit.S8)
        kit.add(cApp, photoWarn, UiKit.S4)
        refreshImageStatus()

        // 3. Message et langue
        val cMsg = card(getString(R.string.sec_message_lang))
        kit.add(cMsg, kit.label(getString(R.string.lbl_child_name)), UiKit.S12)
        kit.add(cMsg, childName, UiKit.S4, ctrl)
        kit.add(cMsg, kit.help(getString(R.string.help_child_name)), UiKit.S8)
        kit.add(cMsg, kit.label(getString(R.string.lbl_message)), UiKit.S16)
        kit.add(cMsg, message, UiKit.S4)
        val langIds = arrayOf("", "fr", "en")
        val langSpin = kit.spinner(
            listOf(getString(R.string.lang_phone), getString(R.string.lang_fr), getString(R.string.lang_en)),
            langIds.indexOf(Prefs.language(this@MainActivity)))
        langSpin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val chosen = langIds[pos.coerceIn(0, langIds.size - 1)]
                if (chosen == Prefs.language(this@MainActivity)) return
                Prefs.setLanguage(this@MainActivity, chosen)
                if (Prefs.enabled(this@MainActivity)) {
                    // Le service relit la langue au demarrage.
                    stopService(Intent(this@MainActivity, ReminderService::class.java))
                    startForegroundService(Intent(this@MainActivity, ReminderService::class.java))
                }
                recreate()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        kit.add(cMsg, kit.label(getString(R.string.sec_language)), UiKit.S16)
        kit.add(cMsg, langSpin, UiKit.S4, ctrl)

        // 4. Telecommande : appairage de cette tablette avec le compte parent
        val cRemote = card(getString(R.string.sec_remote))
        val pairStatus = kit.status("")
        val clockWarn = kit.help(getString(R.string.pair_clock_off)).apply { setTextColor(kit.c.warn) }
        val pairBtn = kit.button(getString(R.string.btn_pair_refresh))
        val unpairBtn = kit.button(getString(R.string.btn_unpair), UiKit.Kind.DANGER)
        fun refreshPair() {
            val code = Prefs.pairCode(this)
            val live = code.length == 8 && Prefs.pairExpires(this) > RemoteConfig.nowMs(this)
            val paired = Prefs.paired(this)
            pairStatus.text = when {
                paired -> getString(R.string.pair_paired)
                live -> getString(R.string.pair_code_fmt, code.substring(0, 4) + " " + code.substring(4))
                else -> getString(R.string.pair_none)
            }
            pairStatus.textSize = if (live && !paired) UiKit.T_TITLE else UiKit.T_BODY
            clockWarn.visibility = if (Math.abs(Prefs.clockOffset(this)) > 120_000L) View.VISIBLE else View.GONE
            pairBtn.visibility = if (paired) View.GONE else View.VISIBLE
            unpairBtn.visibility = if (paired) View.VISIBLE else View.GONE
        }
        pairBtn.setOnClickListener {
            pairBtn.isEnabled = false
            Thread {
                try { RemoteConfig.sync(this) } catch (e: Throwable) { }
                safeUi { pairBtn.isEnabled = true; refreshPair(); showPendingIfAny() }
            }.start()
        }
        // Desappairage depuis la tablette : derriere le code parent.
        unpairBtn.setOnClickListener {
            askPinThen(getString(R.string.unpair_confirm)) {
                unpairBtn.isEnabled = false
                Thread {
                    try { RemoteConfig.resetPairing(this) } catch (e: Throwable) { }
                    safeUi { unpairBtn.isEnabled = true; refreshPair() }
                }.start()
            }
        }
        kit.add(cRemote, pairStatus, UiKit.S12)
        kit.add(cRemote, pairBtn, UiKit.S12, ctrl)
        kit.add(cRemote, unpairBtn, UiKit.S12, ctrl)
        kit.add(cRemote, kit.help(getString(R.string.help_pair)), UiKit.S8)
        kit.add(cRemote, clockWarn, UiKit.S8)
        pairRefresh = { refreshPair() }

        // 5. Securite : code parent, protection, icone
        val cSec = card(getString(R.string.sec_security))
        kit.add(cSec, kit.label(getString(R.string.lbl_pin)), UiKit.S12)
        kit.add(cSec, pin, UiKit.S4, ctrl)
        kit.add(cSec, kit.help(getString(R.string.pin_keep_hint)), UiKit.S8)
        // Changer de role (telephone du parent) : derriere le code parent.
        kit.add(cSec, kit.button(getString(R.string.btn_role_parent)).apply {
            setOnClickListener {
                askPinThen(getString(R.string.role_switch_confirm)) {
                    RoleChooser.switchTo(this@MainActivity, "parent")
                }
            }
        }, UiKit.S16, ctrl)
        kit.add(cSec, kit.help(getString(R.string.help_role_parent)), UiKit.S8)

        // 6. Mises a jour / a propos
        val cUpd = card(getString(if (Dist.IS_DIRECT) R.string.sec_update else R.string.sec_about))
        val refreshProt = Dist.addProtectionSection(this, cSec, cUpd, kit)
        val versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
        kit.add(cUpd, kit.help(getString(R.string.version_fmt, versionName)), UiKit.S12)
        Dist.checkOnOpen(this)
        // Un code d'appairage a jour s'affiche a l'ouverture (hors ligne : on garde l'ancien, sans erreur).
        Thread {
            try { RemoteConfig.sync(this) } catch (e: Throwable) { }
            safeUi { refreshPair(); showPendingIfAny() }
        }.start()

        // Demarrer / enregistrer
        startBtn.setOnClickListener {
            val i = interval.text.toString().toIntOrNull() ?: 0
            val l = lock.text.toString().toIntOrNull() ?: 0
            val p = pin.text.toString()
            interval.error = null; lock.error = null; pin.error = null
            var bad: EditText? = null
            if (i < 1) { interval.error = getString(R.string.err_minutes); bad = bad ?: interval }
            if (l < 1) { lock.error = getString(R.string.err_minutes); bad = bad ?: lock }
            // Champ vide = on garde le code actuel (le code n'est plus relisible : il est stocke chiffre).
            if (!(p.isEmpty() && Prefs.hasPin(this)) && p.length < 4) { pin.error = getString(R.string.err_pin); bad = bad ?: pin }
            if (bad != null) { bad.requestFocus(); return@setOnClickListener }
            val msgText = if (Prefs.isDefaultMessage(this@MainActivity, message.text.toString())) "" else message.text.toString()
            Prefs.save(this@MainActivity, i, l, msgText, p, childName.text.toString().trim())
            Prefs.setActive(this@MainActivity, fromMin, toMin, limitChk.isChecked)
            Prefs.setTheme(this@MainActivity, themeIds[themeSpin.selectedItemPosition.coerceIn(0, themeIds.size - 1)])
            Prefs.setBlocsAnim(this@MainActivity, animIds[animSpin.selectedItemPosition.coerceIn(0, animIds.size - 1)])
            charIds[charSpin.selectedItemPosition.coerceIn(0, charIds.size - 1)].let { if (it.isNotEmpty()) Prefs.setCharacter(this@MainActivity, it) }
            if (!Settings.canDrawOverlays(this@MainActivity)) {
                Toast.makeText(this@MainActivity, getString(R.string.toast_allow_overlay), Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                return@setOnClickListener
            }
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
            stopService(Intent(this@MainActivity, ReminderService::class.java))
            startForegroundService(Intent(this@MainActivity, ReminderService::class.java))
            Prefs.setEnabled(this@MainActivity, true)
            Prefs.setPaused(this@MainActivity, false)   // un demarrage local leve la pause distante
            refreshStatus()
        }
        stopBtn.setOnClickListener {
            stopService(Intent(this@MainActivity, ReminderService::class.java))
            Prefs.setEnabled(this@MainActivity, false)
            Prefs.setPaused(this@MainActivity, false)
            refreshStatus()
        }

        refreshAll = { refreshStatus(); refreshProt(); refreshPair(); refreshAppearance() }
        refreshAll?.invoke()
        ui.removeCallbacks(pairPoll)
        ui.postDelayed(pairPoll, 10_000L)

        kit.screen(this, col, startBtn)
    }
}
