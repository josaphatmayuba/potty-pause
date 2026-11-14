package com.famille.rappelpipi

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import kotlin.random.Random
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Compte le temps d'ecran allume. Quand il atteint l'intervalle, affiche un ecran
 * plein ecran avec le message et un minuteur. L'ecran se libere seul a la fin du
 * minuteur, ou avec le code parent. Eteindre l'ecran ne raccourcit pas le blocage
 * (le minuteur utilise elapsedRealtime, qui continue en veille).
 */
class ReminderService : Service() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Prefs.localized(base))
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var keyguard: KeyguardManager
    private var failUntil = 0L
    private var fails = 0
    private var overlay: View? = null
    private var countdown: TextView? = null
    private var scene: ThemeView? = null
    private var currentTheme = "blocs"
    private var lockTotal = 1L
    private var lastSpoken = 0L
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var celebrating = false
    private var celebrateEnd: Runnable? = null
    private var currentKind = 0

    // Voix pour un enfant qui ne sait pas encore lire. Repetee toutes les 40 s pendant le blocage.
    private fun sayFor(id: String): String {
        val rid = resources.getIdentifier("say_$id", "string", packageName)
        return if (rid != 0) getString(rid) else ""
    }

    private fun speak() {
        if (ttsReady && currentTheme != "blocs") {
            val name = Prefs.childName(this)
            val phrase = (if (name.isNotEmpty()) "$name, " else "") + sayFor(currentTheme)
            tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "pipi")
        } else if (ttsReady) {
            val name = Prefs.childName(this)
            val who = resources.getStringArray(R.array.blocs_who)[currentKind.coerceIn(0, 15)]
            val phrase = (if (name.isNotEmpty()) getString(R.string.say_blocs_name, name) else getString(R.string.say_blocs_noname)) +
                (if (who.isNotEmpty()) getString(R.string.say_blocs_who, who) else "") + getString(R.string.say_blocs_end)
            tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "pipi")
        }
        lastSpoken = SystemClock.elapsedRealtime()
    }

    private var usedMs = 0L
    private var onSince = 0L
    private var offSince = 0L
    private var screenOn = true
    private var lockedUntil = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val now = SystemClock.elapsedRealtime()
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (screenOn) usedMs += now - onSince
                    screenOn = false
                    offSince = now
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (!screenOn) {
                        // Une vraie pause (ecran eteint >= duree de blocage) remet le compteur a zero.
                        if (overlay == null && now - offSince >= Prefs.lockMin(c) * 60_000L) usedMs = 0
                        screenOn = true
                        onSince = now
                    }
                }
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            if (!Settings.canDrawOverlays(this@ReminderService)) {
                // Permission retiree : on le dit au parent au lieu de s'arreter en silence.
                Prefs.setEnabled(this@ReminderService, false)
                getSystemService(NotificationManager::class.java).notify(
                    2,
                    Notification.Builder(this@ReminderService, "rappel")
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle(getString(R.string.notif_stopped))
                        .build()
                )
                stopSelf()
                return
            }
            if (celebrating) {
                // Fete en cours : on attend la fin des confettis.
            } else if (overlay != null) {
                val left = lockedUntil - now
                if (left <= 0) {
                    hideOverlay(now)
                } else {
                    countdown?.text = format(left)
                    scene?.state?.progress = (1f - left.toFloat() / lockTotal).coerceIn(0f, 1f)
                    if (now - lastSpoken >= 40_000L) speak()
                }
            } else if (screenOn) {
                // Le temps passe sur l'ecran de verrouillage n'est pas compte.
                if (keyguard.isKeyguardLocked) {
                    onSince = now
                } else {
                    val used = usedMs + (now - onSince)
                    if (used >= Prefs.intervalMin(this@ReminderService) * 60_000L) {
                        showOverlay(now, Prefs.lockMin(this@ReminderService) * 60_000L)
                    }
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        screenOn = pm.isInteractive
        onSince = SystemClock.elapsedRealtime()
        offSince = onSince
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val l = Prefs.language(this@ReminderService)
                val loc = if (l.isNotEmpty()) Locale(l) else Locale.getDefault()
                val r = tts?.setLanguage(loc)
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts?.language = Locale.ENGLISH
                ttsReady = true
            }
        }
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
        // Service relance (kill, reboot) pendant un blocage : on le reprend la ou il en etait.
        val remaining = Prefs.lockedUntilWall(this) - System.currentTimeMillis()
        if (remaining > 0 && Settings.canDrawOverlays(this)) showOverlay(onSince, remaining)
        else Prefs.setLockedUntilWall(this, 0L)
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("rappel", getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val notif = Notification.Builder(this, "rappel")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.notif_active))
            .setContentIntent(android.app.PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notif)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        celebrateEnd?.let { handler.removeCallbacks(it) }
        runCatching { unregisterReceiver(screenReceiver) }
        tts?.stop(); tts?.shutdown(); tts = null
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun format(ms: Long): String {
        val s = (ms + 999) / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun showOverlay(now: Long, durationMs: Long) {
        lockedUntil = now + durationMs
        celebrating = false
        lockTotal = Prefs.lockMin(this) * 60_000L
        Prefs.setLockedUntilWall(this, System.currentTimeMillis() + durationMs)

        val msg = TextView(this).apply {
            text = Prefs.shownMessage(this@ReminderService)
            textSize = 34f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val timer = TextView(this).apply {
            textSize = 48f
            setTextColor(Color.parseColor("#FFD54F"))
            gravity = Gravity.CENTER
            text = format(lockedUntil - now)
        }
        countdown = timer

        val pinField = EditText(this).apply {
            hint = getString(R.string.pin_hint)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            visibility = View.GONE
        }
        val skipBtn = Button(this).apply {
            text = getString(R.string.btn_skip)
            visibility = View.GONE
            setOnClickListener { hideOverlay(SystemClock.elapsedRealtime()) }
        }
        val rewardBtn = Button(this).apply {
            text = getString(R.string.btn_reward)
            visibility = View.GONE
            setOnClickListener {
                // Double clic : un seul diamant.
                if (celebrating) return@setOnClickListener
                isEnabled = false
                val name = Prefs.childName(this@ReminderService)
                val d = Prefs.addDiamond(this@ReminderService)
                // Diamant donne = blocage fini, meme si le service redemarre pendant la fete.
                Prefs.setLockedUntilWall(this@ReminderService, 0L)
                celebrating = true
                scene?.state?.apply { celebrate = true; chest = d >= 5; diamonds = d }
                val bravo = if (name.isNotEmpty()) getString(R.string.bravo_name, name) else getString(R.string.bravo)
                msg.text = bravo + (if (d >= 5) getString(R.string.chest_open) else getString(R.string.plus_diamond))
                timer.text = ""
                visibility = View.GONE
                skipBtn.visibility = View.GONE
                if (ttsReady) {
                    tts?.speak(
                        bravo + " " + (if (d >= 5) getString(R.string.say_chest) else getString(R.string.say_diamond)),
                        TextToSpeech.QUEUE_FLUSH, null, "bravo"
                    )
                }
                val ov = overlay
                val end = Runnable {
                    celebrating = false
                    if (ov != null && overlay === ov) hideOverlay(SystemClock.elapsedRealtime())
                }
                celebrateEnd = end
                handler.postDelayed(end, 7000L)
            }
        }
        val okBtn = Button(this).apply {
            text = getString(R.string.ok)
            visibility = View.GONE
            setOnClickListener {
                val t = SystemClock.elapsedRealtime()
                val pin = Prefs.pin(this@ReminderService)
                if (t < failUntil) {
                    pinField.setText("")
                    pinField.hint = getString(R.string.retry_in, ((failUntil - t) / 1000 + 1).toInt())
                } else if (pin.isNotEmpty() && pinField.text.toString() == pin) {
                    fails = 0
                    pinField.visibility = View.GONE
                    visibility = View.GONE
                    rewardBtn.visibility = View.VISIBLE
                    skipBtn.visibility = View.VISIBLE
                } else {
                    pinField.setText("")
                    fails++
                    if (fails >= 3) { failUntil = t + 30_000L; fails = 0 }
                    pinField.hint = getString(R.string.wrong_code)
                }
            }
        }
        val parentBtn = Button(this).apply {
            text = getString(R.string.parent)
            alpha = 0.5f
            setOnClickListener {
                pinField.visibility = View.VISIBLE
                okBtn.visibility = View.VISIBLE
                visibility = View.GONE
                pinField.requestFocus()
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1A237E"))
            setPadding(dp(8), dp(24), dp(8), dp(24))
            isClickable = true // absorbe les touches
            val sc = buildScene()
            scene = sc
            sc.state.diamonds = Prefs.diamonds(this@ReminderService)
            addView(sc, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(msg)
            addView(timer)
            addView(parentBtn)
            addView(pinField)
            addView(okBtn)
            addView(rewardBtn)
            addView(skipBtn)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            // Suit la rotation choisie par l'utilisateur (portrait ou paysage), meme si l'ecran
            // d'accueil en dessous est bloque dans un seul sens.
            screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        runCatching { wm.addView(root, params) }.onSuccess { overlay = root; speak() }
    }

    // Theme choisi par le parent, ou tire au hasard (jamais deux fois de suite ; photo seulement si une image existe).
    private fun pickTheme(): String {
        val wanted = Prefs.theme(this)
        if (wanted != "hasard") return wanted
        val hasImage = Prefs.imageUri(this) != null
        val last = Prefs.lastTheme(this)
        val all = ThemeView.THEMES.keys.filter { it != "photo" || hasImage }
        val pool = all.filter { it != last }.ifEmpty { all }
        return pool[Random.nextInt(pool.size)]
    }

    // Image du parent reduite (cote max ~1600 px) ; null si illisible.
    private fun loadImage(uriStr: String?): Bitmap? {
        if (uriStr == null) return null
        return try {
            val uri = Uri.parse(uriStr)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Throwable) {
            null
        }
    }

    private fun buildScene(): ThemeView {
        var theme = pickTheme()
        var image: Bitmap? = null
        if (theme == "photo") {
            image = loadImage(Prefs.imageUri(this))
            if (image == null) theme = "blocs"
        }
        if (!ThemeView.THEMES.containsKey(theme)) theme = "blocs"
        Prefs.setLastTheme(this, theme)
        currentTheme = theme
        val sc = ThemeView(this, theme)
        sc.state.image = image
        if (theme == "blocs") {
            sc.state.kind = Prefs.nextCharacter(this)
            currentKind = sc.state.kind
            val a = Prefs.blocsAnim(this)
            sc.state.variant = if (a == "hasard") Random.nextInt(6) else (a.toIntOrNull() ?: 0).coerceIn(0, 5)
        }
        return sc
    }

    private fun hideOverlay(now: Long) {
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
        countdown = null
        scene = null
        celebrating = false
        celebrateEnd?.let { handler.removeCallbacks(it) }
        celebrateEnd = null
        tts?.stop()
        Prefs.setLockedUntilWall(this, 0L)
        usedMs = 0
        onSince = now
    }
}
