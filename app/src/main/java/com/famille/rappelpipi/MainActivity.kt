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

    private val themeIds = arrayOf(
        "blocs", "geant", "bd", "sablier", "feu", "nuit", "ballon", "carte", "porte",
        "bouton", "aqua", "dino", "fusee", "train", "goutte", "photo", "hasard"
    )
    private val animIds = arrayOf("hasard", "0", "1", "2", "3", "4", "5")

    private fun themeName(id: String): String {
        val rid = resources.getIdentifier("theme_$id", "string", packageName)
        return if (rid != 0) getString(rid) else id
    }

    private fun animName(id: String): String {
        val rid = resources.getIdentifier("anim_$id", "string", packageName)
        return if (rid != 0) getString(rid) else id
    }

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

    override fun onResume() {
        super.onResume()
        refreshAll?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pin = Prefs.pin(this)
        if (pin.isEmpty()) {
            buildUi()
        } else {
            askPin(pin)
        }
    }

    private fun askPin(pin: String) {
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
                if (input.text.toString() == pin) buildUi() else finish()
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
        val tablet = resources.configuration.smallestScreenWidthDp >= 600
        val pad = dp(if (tablet) 24 else 16)
        val maxW = minOf(dp(560), resources.displayMetrics.widthPixels)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val grey = Color.parseColor("#5F6368")

        fun section(t: String) = TextView(this).apply {
            text = t; textSize = 18f; setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(24), 0, dp(4))
        }
        fun label(t: String) = TextView(this).apply { text = t; setPadding(0, dp(12), 0, 0) }
        fun help(t: String) = TextView(this).apply { text = t; textSize = 13f; setTextColor(grey) }
        fun btn(t: String) = Button(this).apply { text = t; minHeight = dp(48) }

        val interval = field(getString(R.string.lbl_interval), Prefs.intervalMin(this).toString(), true)
        val lock = field(getString(R.string.lbl_lock), Prefs.lockMin(this).toString(), true)
        val message = field(getString(R.string.lbl_message), Prefs.shownMessage(this), false).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val pin = field(getString(R.string.lbl_pin), Prefs.pin(this), true).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val childName = field(getString(R.string.lbl_child_name), Prefs.childName(this), false)

        col.addView(TextView(this).apply { text = getString(R.string.title_settings); textSize = 26f; setTypeface(null, Typeface.BOLD) })

        // 1. Etat
        col.addView(section(getString(R.string.sec_status)))
        val statusRem = TextView(this).apply { textSize = 16f; setTypeface(null, Typeface.BOLD) }
        val statusBatt = TextView(this).apply { textSize = 16f; setTypeface(null, Typeface.BOLD) }
        col.addView(statusRem); col.addView(statusBatt)
        val startBtn = btn("")
        val stopBtn = btn(getString(R.string.btn_stop))
        val battBtn = btn(getString(R.string.btn_battery)).apply {
            setOnClickListener { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
        col.addView(startBtn); col.addView(stopBtn); col.addView(battBtn)

        fun refreshStatus() {
            val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
            val battOk = pm.isIgnoringBatteryOptimizations(packageName)
            val on = Prefs.enabled(this)
            val green = Color.parseColor("#2E7D32"); val orange = Color.parseColor("#E65100")
            statusRem.text = getString(R.string.status_reminder, getString(if (on) R.string.status_active else R.string.status_stopped))
            statusRem.setTextColor(if (on) green else orange)
            statusBatt.text = getString(R.string.status_battery, getString(if (battOk) R.string.battery_ok else R.string.battery_todo))
            statusBatt.setTextColor(if (battOk) green else orange)
            startBtn.text = getString(if (on) R.string.btn_save_restart else R.string.btn_start)
            stopBtn.isEnabled = on
            battBtn.visibility = if (battOk) View.GONE else View.VISIBLE
        }

        // 2. Temps d'ecran
        col.addView(section(getString(R.string.sec_screen_time)))
        col.addView(label(getString(R.string.lbl_interval))); col.addView(interval)
        col.addView(label(getString(R.string.lbl_lock))); col.addView(lock)

        // 3. Message pour l'enfant
        col.addView(section(getString(R.string.sec_message)))
        col.addView(label(getString(R.string.lbl_child_name))); col.addView(childName)
        col.addView(help(getString(R.string.help_child_name)))
        col.addView(label(getString(R.string.lbl_message))); col.addView(message)

        // 4. Apparence
        col.addView(section(getString(R.string.sec_appearance)))
        val themeSpin = Spinner(this).apply {
            minimumHeight = dp(48)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, themeIds.map { themeName(it) })
            setSelection(themeIds.indexOf(Prefs.theme(this@MainActivity)).coerceAtLeast(0))
        }
        val animSpin = Spinner(this).apply {
            minimumHeight = dp(48)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, animIds.map { animName(it) })
            setSelection(animIds.indexOf(Prefs.blocsAnim(this@MainActivity)).coerceAtLeast(0))
        }
        val animLabel = label(getString(R.string.lbl_anim))
        val photoWarn = TextView(this).apply {
            text = getString(R.string.warn_photo_no_image)
            textSize = 13f; setTextColor(Color.parseColor("#E65100")); setPadding(0, dp(8), 0, 0)
        }
        val imgStatus = TextView(this).apply { setPadding(0, dp(4), 0, 0) }
        imageStatus = imgStatus
        val removeBtn = btn(getString(R.string.btn_remove_image)).apply {
            setOnClickListener { Prefs.setImageUri(this@MainActivity, null); refreshImageStatus() }
        }
        fun refreshAppearance() {
            val t = themeIds[themeSpin.selectedItemPosition.coerceIn(0, themeIds.size - 1)]
            val showAnim = t == "blocs" || t == "hasard"
            animLabel.visibility = if (showAnim) View.VISIBLE else View.GONE
            animSpin.visibility = if (showAnim) View.VISIBLE else View.GONE
            val hasImg = Prefs.imageUri(this) != null
            photoWarn.visibility = if (t == "photo" && !hasImg) View.VISIBLE else View.GONE
            removeBtn.visibility = if (hasImg) View.VISIBLE else View.GONE
        }
        onImageChanged = { refreshAppearance() }
        themeSpin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = refreshAppearance()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        col.addView(label(getString(R.string.lbl_theme))); col.addView(themeSpin)
        col.addView(animLabel); col.addView(animSpin)
        col.addView(btn(getString(R.string.btn_pick_image)).apply {
            setOnClickListener {
                val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                runCatching { startActivityForResult(pick, pickImage) }
                    .onFailure { Toast.makeText(this@MainActivity, getString(R.string.toast_no_picker), Toast.LENGTH_LONG).show() }
            }
        })
        col.addView(removeBtn)
        col.addView(imgStatus)
        col.addView(photoWarn)
        refreshImageStatus()

        // 5. Langue
        col.addView(section(getString(R.string.sec_language)))
        val langIds = arrayOf("", "fr", "en")
        val langSpin = Spinner(this).apply {
            minimumHeight = dp(48)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf(getString(R.string.lang_phone), getString(R.string.lang_fr), getString(R.string.lang_en)))
            setSelection(langIds.indexOf(Prefs.language(this@MainActivity)).coerceAtLeast(0))
        }
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
        col.addView(langSpin)

        // 6. Securite
        col.addView(section(getString(R.string.sec_security)))
        col.addView(label(getString(R.string.lbl_pin))); col.addView(pin)

        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val admin = android.content.ComponentName(this, AdminReceiver::class.java)
        val alias = android.content.ComponentName(this, "$packageName.LauncherAlias")
        val protStatus = TextView(this).apply { setPadding(0, dp(16), 0, 0) }
        val adminBtn = btn("")
        val iconStatus = TextView(this).apply { setPadding(0, dp(16), 0, 0) }
        val iconBtn = btn("")
        fun iconHidden() = packageManager.getComponentEnabledSetting(alias) ==
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        fun refreshProt() {
            val on = dpm.isAdminActive(admin)
            val hidden = iconHidden()
            adminBtn.text = getString(if (on) R.string.btn_protection_off else R.string.btn_protection_on)
            iconBtn.text = getString(if (hidden) R.string.btn_icon_show else R.string.btn_icon_hide)
            protStatus.text = getString(R.string.protection_status, getString(if (on) R.string.on else R.string.off))
            iconStatus.text = getString(R.string.icon_status, getString(if (hidden) R.string.icon_hidden else R.string.icon_visible))
        }
        adminBtn.setOnClickListener {
            if (dpm.isAdminActive(admin)) {
                dpm.removeActiveAdmin(admin)
            } else {
                startActivity(Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explanation))
                })
            }
            adminBtn.postDelayed({ refreshProt() }, 600)
        }
        iconBtn.setOnClickListener {
            if (!iconHidden() && !Prefs.enabled(this)) {
                Toast.makeText(this, getString(R.string.toast_start_first), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            packageManager.setComponentEnabledSetting(
                alias,
                if (iconHidden()) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
            refreshProt()
        }
        col.addView(protStatus); col.addView(adminBtn)
        col.addView(iconStatus); col.addView(iconBtn)
        col.addView(help(getString(R.string.help_icon)))
        col.addView(btn(getString(R.string.btn_check_update)).apply {
            setOnClickListener { Updater.check(this@MainActivity, true) }
        })
        val versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
        col.addView(help(getString(R.string.version_fmt, versionName)).apply { setPadding(0, dp(8), 0, 0) })
        Updater.check(this, false)

        // Demarrer / enregistrer
        startBtn.setOnClickListener {
            val i = interval.text.toString().toIntOrNull() ?: 0
            val l = lock.text.toString().toIntOrNull() ?: 0
            val p = pin.text.toString()
            interval.error = null; lock.error = null; pin.error = null
            var bad: EditText? = null
            if (i < 1) { interval.error = getString(R.string.err_minutes); bad = bad ?: interval }
            if (l < 1) { lock.error = getString(R.string.err_minutes); bad = bad ?: lock }
            if (p.length < 4) { pin.error = getString(R.string.err_pin); bad = bad ?: pin }
            if (bad != null) { bad.requestFocus(); return@setOnClickListener }
            val msgText = if (Prefs.isDefaultMessage(this@MainActivity, message.text.toString())) "" else message.text.toString()
            Prefs.save(this@MainActivity, i, l, msgText, p, childName.text.toString().trim())
            Prefs.setTheme(this@MainActivity, themeIds[themeSpin.selectedItemPosition.coerceIn(0, themeIds.size - 1)])
            Prefs.setBlocsAnim(this@MainActivity, animIds[animSpin.selectedItemPosition.coerceIn(0, animIds.size - 1)])
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
            refreshStatus()
        }
        stopBtn.setOnClickListener {
            stopService(Intent(this@MainActivity, ReminderService::class.java))
            Prefs.setEnabled(this@MainActivity, false)
            refreshStatus()
        }

        refreshAll = { refreshStatus(); refreshProt(); refreshAppearance() }
        refreshAll?.invoke()

        val frame = FrameLayout(this).apply {
            addView(col, FrameLayout.LayoutParams(maxW, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL))
        }
        setContentView(ScrollView(this).apply { addView(frame) })
    }
}
