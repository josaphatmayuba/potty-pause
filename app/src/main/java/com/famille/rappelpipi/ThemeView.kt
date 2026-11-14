package com.famille.rappelpipi

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min
import kotlin.math.sin

/** Reglages d'un theme : fond, couleur de barre, barre affichee, phrase dite a voix haute. */
class ThemeInfo(val bg: String, val bc: String, val bar: Boolean, val say: String)

/**
 * Ecran de rappel a theme. Les scenes (IdeaScenesA, IdeaScenesB, BlocsAnim) dessinent dans un
 * repere 320 x 200 ; cette vue gere le cadrage, la barre de progression, les confettis,
 * les diamants et le coffre.
 */
class ThemeView(context: Context, val theme: String) : View(context) {

    companion object {
        val THEMES: Map<String, ThemeInfo> = linkedMapOf(
            "blocs" to ThemeInfo("#1A237E", "#4ADE4A", true, ""),
            "geant" to ThemeInfo("#FFB74D", "#7C3AED", true, "Oh là là, j'ai trop envie de faire pipi ! Et toi ? Allez, on y va !"),
            "bd" to ThemeInfo("#FFF7E0", "#F97316", true, "Il a envie… il court… il a réussi ! À toi de faire pareil !"),
            "sablier" to ThemeInfo("#FCE7D6", "#F59E0B", false, "Pendant que le sable coule, va faire pipi. Je t'attends !"),
            "feu" to ThemeInfo("#1E3A8A", "#22C55E", false, "Le feu est rouge, c'est l'heure du pipi ! Quand il sera vert, tu pourras rejouer."),
            "nuit" to ThemeInfo("#1E1B4B", "#A5B4FC", true, "La lune va bientôt dormir. Un petit pipi avant le dodo ?"),
            "ballon" to ThemeInfo("#FDF2F8", "#EF4444", false, "Le ballon gonfle, gonfle… Va faire pipi avant qu'il fasse pop !"),
            "carte" to ThemeInfo("#7C4A1E", "#FACC15", true, "Suis le chemin du trésor jusqu'aux toilettes !"),
            "porte" to ThemeInfo("#92400E", "#FDE047", true, "Toc toc ! Les toilettes t'attendent. Entre vite !"),
            "bouton" to ThemeInfo("#0EA5E9", "#FACC15", false, "C'est l'heure du pipi !"),
            "aqua" to ThemeInfo("#075985", "#22D3EE", true, "Les poissons se reposent. Toi, va faire pipi, puis reviens les voir !"),
            "dino" to ThemeInfo("#2E7D32", "#FACC15", true, "Oh oh, le petit dino a très envie ! Cours vite aux toilettes avec lui !"),
            "fusee" to ThemeInfo("#1B2466", "#4ADE80", false, "Trois, deux, un… décollage ! Va vite faire pipi, la fusée t'attend !"),
            "train" to ThemeInfo("#166534", "#F59E0B", false, "Tchou tchou ! Le petit train va à la gare des toilettes. Monte avec lui !"),
            "goutte" to ThemeInfo("#0369A1", "#7DD3FC", true, "Plouf ! La petite goutte est allée dans les toilettes. À toi maintenant !"),
            "photo" to ThemeInfo("#BE185D", "#FDE68A", true, "Regarde, c'est ton dessin ! C'est l'heure d'aller faire pipi.")
        )

        fun info(id: String): ThemeInfo = THEMES[id] ?: THEMES.getValue("blocs")
    }

    val state = SceneState()

    private val ti: ThemeInfo = Companion.info(theme)
    private val bgColor = Color.parseColor(ti.bg)
    private val barColor = Color.parseColor(ti.bc)
    private val p = Paint().apply { isAntiAlias = true }
    private val px = Paint().apply { isAntiAlias = false }
    private val rect = RectF()
    private val confColors = intArrayOf(
        Color.parseColor("#F87171"), Color.parseColor("#FACC15"), Color.parseColor("#4ADE80"),
        Color.parseColor("#60A5FA"), Color.parseColor("#C084FC"), Color.parseColor("#FB923C")
    )

    private var startMs = SystemClock.elapsedRealtime()
    private var celebrateStartMs = 0L

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { invalidate() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startMs = SystemClock.elapsedRealtime()
        anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    // Hauteur = largeur x 200/320 (au plus la place disponible) : en portrait, plus de grandes bandes
    // vides au-dessus et au-dessous, le message se place juste sous la scene.
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val wanted = (w * 200f / 320f).toInt()
        val avail = if (MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED) wanted else MeasureSpec.getSize(heightSpec)
        setMeasuredDimension(w, minOf(wanted, avail))
    }

    override fun onDraw(cv: Canvas) {
        val now = SystemClock.elapsedRealtime()
        val t = (now - startMs) / 1000f
        if (state.celebrate) {
            if (celebrateStartMs == 0L) celebrateStartMs = now
            state.celebrateT = (now - celebrateStartMs) / 1000f
        } else {
            celebrateStartMs = 0L
            state.celebrateT = 0f
        }

        cv.drawColor(bgColor)
        if (width <= 0 || height <= 0) return

        val k = min(width / 320f, height / 200f)
        cv.save()
        cv.translate((width - 320f * k) / 2f, (height - 200f * k) / 2f)
        cv.scale(k, k)
        cv.clipRect(0f, 0f, 320f, 200f)

        val handled = IdeaScenesA.draw(theme, cv, t, state) ||
            IdeaScenesB.draw(theme, cv, t, state) ||
            BlocsAnim.draw(theme, cv, t, state)
        if (!handled) drawFallback(cv)

        if (ti.bar && !state.celebrate) drawBar(cv)
        if (state.celebrate) drawConfetti(cv, state.celebrateT)
        drawDiamonds(cv)
        if (state.celebrate && state.chest) drawChest(cv)

        cv.restore()
    }

    private fun drawFallback(cv: Canvas) {
        p.color = bgColor
        cv.drawRect(0f, 0f, 320f, 200f, p)
        p.color = Color.argb(60, 255, 255, 255)
        cv.drawRect(0f, 170f, 320f, 200f, p)
    }

    private fun drawBar(cv: Canvas) {
        val x0 = 20f; val x1 = 170f; val y = 181f; val h = 10f
        p.color = Color.argb(140, 17, 24, 39)
        rect.set(x0, y, x1, y + h)
        cv.drawRoundRect(rect, h / 2f, h / 2f, p)
        val pr = state.progress.coerceIn(0f, 1f)
        if (pr > 0f) {
            p.color = barColor
            rect.set(x0, y, x0 + (x1 - x0) * pr, y + h)
            cv.drawRoundRect(rect, h / 2f, h / 2f, p)
        }
    }

    private fun drawConfetti(cv: Canvas, t: Float) {
        for (i in 0 until 46) {
            val x = (i * 47) % 320 + sin(t * 3f + i) * 4f
            val y = ((i * 29 + t * (40 + (i % 3) * 20)) % 220f) - 10f
            p.color = confColors[i % confColors.size]
            rect.set(x, y, x + 4f, y + 4f)
            cv.drawRoundRect(rect, 1f, 1f, p)
        }
    }

    /** Rectangle en unites de la grille 64 (1 unite = 5 px du repere 320). */
    private fun r(cv: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        px.color = color
        cv.drawRect(x * 5f, y * 5f, (x + w) * 5f, (y + h) * 5f, px)
    }

    private fun drawDiamonds(cv: Canvas) {
        val cyan = Color.parseColor("#22D3EE"); val cyanEdge = Color.parseColor("#0E7490")
        val grey = Color.parseColor("#9CA3AF"); val greyEdge = Color.parseColor("#6B7280")
        for (i in 0 until 5) {
            val x = 37f + i * 5.2f
            val y = 35f
            val on = i < state.diamonds || (state.celebrate && state.chest)
            val fill = if (on) cyan else grey
            val edge = if (on) cyanEdge else greyEdge
            r(cv, x + 0.6f, y - 0.6f, 2.8f, 1.6f, edge); r(cv, x - 0.6f, y + 0.6f, 5.2f, 3.2f, edge); r(cv, x + 0.6f, y + 3.2f, 2.8f, 1.6f, edge)
            r(cv, x + 1f, y, 2f, 1f, fill); r(cv, x, y + 1f, 4f, 2f, fill); r(cv, x + 1f, y + 3f, 2f, 1f, fill)
            if (on) r(cv, x + 0.6f, y + 1.1f, 1f, 1f, Color.WHITE)
        }
    }

    private fun drawChest(cv: Canvas) {
        val gold = Color.parseColor("#FFD54F")
        r(cv, 25.6f, 27.6f, 12.8f, 7.2f, Color.parseColor("#5B3A1E"))
        r(cv, 26f, 28f, 12f, 6.4f, Color.parseColor("#8B5A2B")); r(cv, 26f, 28f, 12f, 1.2f, Color.parseColor("#B07A3A"))
        r(cv, 31f, 28f, 2f, 3f, gold)
        r(cv, 26f, 24f, 12f, 3f, Color.parseColor("#6E4421"))
        r(cv, 27f, 25.6f, 10f, 2.4f, gold); r(cv, 29f, 24.6f, 6f, 1.4f, Color.WHITE)
    }
}
