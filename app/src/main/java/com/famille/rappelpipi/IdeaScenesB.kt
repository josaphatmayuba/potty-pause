package com.famille.rappelpipi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Scenes de theme (lot B) portees depuis rappel-pipi-idees.html :
 * bouton, aqua, dino, fusee, train, goutte, photo. Repere logique 320 x 200
 * (le canvas est deja mis a l'echelle par l'appelant).
 */
object IdeaScenesB {

    fun draw(id: String, g: Canvas, t: Float, s: SceneState): Boolean {
        resetState()
        when (id) {
            "bouton" -> sceneBouton(g, t)
            "aqua" -> sceneAqua(g, t)
            "dino" -> sceneDino(g, t, s)
            "fusee" -> sceneFusee(g, t, s)
            "train" -> sceneTrain(g, t, s)
            "goutte" -> sceneGoutte(g, t, s)
            "photo" -> scenePhoto(g, t, s)
            else -> return false
        }
        return true
    }

    // ---------------------------------------------------------------- etat de dessin

    private const val PI_F = 3.1415927f
    private const val HOLD = 0
    private const val RUN = 1
    private const val HAPPY = 2

    private val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fm = Paint.FontMetrics()
    private val path = Path()
    private val rect = RectF()
    private val dash = DashPathEffect(floatArrayOf(4f, 4f), 0f)
    private val colorCache = HashMap<String, Int>()
    private val gradCache = HashMap<String, LinearGradient>()

    private var ga = 1f
    private var lineW = 1f
    private var strokeCol = Color.BLACK
    private var cap = Paint.Cap.BUTT

    private val stW = FloatArray(16)
    private val stA = FloatArray(16)
    private val stC = IntArray(16)
    private val stCap = arrayOfNulls<Paint.Cap>(16)
    private var stN = 0

    private fun resetState() {
        ga = 1f; lineW = 1f; strokeCol = Color.BLACK; cap = Paint.Cap.BUTT; stN = 0
        sp.pathEffect = null; fp.shader = null
    }

    private fun gsave(g: Canvas) {
        g.save()
        if (stN < 16) { stW[stN] = lineW; stA[stN] = ga; stC[stN] = strokeCol; stCap[stN] = cap }
        stN++
    }

    private fun grestore(g: Canvas) {
        g.restore()
        stN--
        if (stN in 0..15) { lineW = stW[stN]; ga = stA[stN]; strokeCol = stC[stN]; cap = stCap[stN] ?: Paint.Cap.BUTT }
        if (stN < 0) stN = 0
    }

    private fun cc(s: String): Int = colorCache.getOrPut(s) {
        if (s.startsWith("rgba")) {
            val p = s.substring(5, s.length - 1).split(',')
            Color.argb(
                (p[3].trim().toFloat() * 255f).roundToInt().coerceIn(0, 255),
                p[0].trim().toInt(), p[1].trim().toInt(), p[2].trim().toInt()
            )
        } else if (s.length == 4) {
            Color.parseColor("#" + s[1] + s[1] + s[2] + s[2] + s[3] + s[3])
        } else {
            Color.parseColor(s)
        }
    }

    private fun alphaOf(col: Int): Int = (Color.alpha(col) * clamp(ga, 0f, 1f)).toInt().coerceIn(0, 255)

    private fun applyFill(col: Int) { fp.color = col; fp.alpha = alphaOf(col) }

    private fun applyStroke() {
        sp.color = strokeCol; sp.alpha = alphaOf(strokeCol)
        sp.strokeWidth = lineW; sp.strokeCap = cap
    }

    // ---------------------------------------------------------------- helpers

    private fun clamp(v: Float, a: Float, b: Float): Float = if (v < a) a else if (v > b) b else v
    private fun lerp(a: Float, b: Float, k: Float): Float = a + (b - a) * k
    private fun ease(k: Float): Float = k * k * (3f - 2f * k)

    private fun fillRect(g: Canvas, x: Float, y: Float, w: Float, h: Float, fill: String) {
        applyFill(cc(fill)); g.drawRect(x, y, x + w, y + h, fp)
    }

    private fun rr(g: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, fill: String?, stroke: String? = null) {
        rect.set(x, y, x + w, y + h)
        val rad = max(0f, min(r, min(abs(w), abs(h)) / 2f))
        if (fill != null) { applyFill(cc(fill)); g.drawRoundRect(rect, rad, rad, fp) }
        if (stroke != null) { strokeCol = cc(stroke); applyStroke(); g.drawRoundRect(rect, rad, rad, sp) }
    }

    private fun circ(g: Canvas, x: Float, y: Float, r: Float, fill: String?, stroke: String? = null) {
        val rad = max(r, 0f)
        if (fill != null) { applyFill(cc(fill)); g.drawCircle(x, y, rad, fp) }
        if (stroke != null) { strokeCol = cc(stroke); applyStroke(); g.drawCircle(x, y, rad, sp) }
    }

    private fun ell(g: Canvas, x: Float, y: Float, rx: Float, ry: Float, fill: String?, stroke: String? = null) {
        val a = max(rx, 0f); val b = max(ry, 0f)
        rect.set(x - a, y - b, x + a, y + b)
        if (fill != null) { applyFill(cc(fill)); g.drawOval(rect, fp) }
        if (stroke != null) { strokeCol = cc(stroke); applyStroke(); g.drawOval(rect, sp) }
    }

    private fun tri(g: Canvas, a: Float, b: Float, c: Float, d: Float, e: Float, f: Float, fill: String) {
        path.reset(); path.moveTo(a, b); path.lineTo(c, d); path.lineTo(e, f); path.close()
        applyFill(cc(fill)); g.drawPath(path, fp)
    }

    private fun line(g: Canvas, a: Float, b: Float, c: Float, d: Float) {
        applyStroke(); g.drawLine(a, b, c, d, sp)
    }

    /** Arc trace (angles en radians, sens horaire comme le canvas JS). */
    private fun arcS(g: Canvas, x: Float, y: Float, r: Float, a0: Float, a1: Float) {
        rect.set(x - r, y - r, x + r, y + r)
        applyStroke()
        g.drawArc(rect, a0 * 180f / PI_F, (a1 - a0) * 180f / PI_F, false, sp)
    }

    private fun fillPath(g: Canvas, fill: String) { applyFill(cc(fill)); g.drawPath(path, fp) }
    private fun strokePath(g: Canvas) { applyStroke(); g.drawPath(path, sp) }

    private fun txt(g: Canvas, s: String, x: Float, y: Float, size: Float, fill: String, stroke: String? = null) {
        tp.textSize = size
        tp.getFontMetrics(fm)
        val by = y - (fm.ascent + fm.descent) / 2f
        if (stroke != null) {
            val c = cc(stroke)
            tp.style = Paint.Style.STROKE; tp.strokeWidth = size / 5f; tp.strokeJoin = Paint.Join.ROUND
            tp.color = c; tp.alpha = alphaOf(c)
            g.drawText(s, x, by, tp)
        }
        val f = cc(fill)
        tp.style = Paint.Style.FILL; tp.color = f; tp.alpha = alphaOf(f)
        g.drawText(s, x, by, tp)
    }

    private fun bubble(g: Canvas, x: Float, y: Float, s: String, size: Float) {
        tp.textSize = size
        val w = tp.measureText(s) + 18f
        val h = size + 10f
        lineW = 2f
        tri(g, x - 8f, y + h / 2f - 1f, x + 2f, y + h / 2f - 1f, x - 10f, y + h / 2f + 10f, "#374151")
        rr(g, x - w / 2f, y - h / 2f, w, h, 9f, "#fff", "#374151")
        tri(g, x - 6f, y + h / 2f - 2f, x, y + h / 2f - 2f, x - 8f, y + h / 2f + 6f, "#fff")
        txt(g, s, x, y + 1f, size, "#1F2937")
    }

    private fun sky(g: Canvas, top: String, bottom: String) {
        val key = "$top|$bottom"
        val gr = gradCache.getOrPut(key) {
            LinearGradient(0f, 0f, 0f, 200f, cc(top), cc(bottom), Shader.TileMode.CLAMP)
        }
        fp.color = Color.WHITE; fp.alpha = 255
        fp.shader = gr
        g.drawRect(0f, 0f, 320f, 200f, fp)
        fp.shader = null
    }

    private fun sparkle(g: Canvas, x: Float, y: Float, r: Float, c: String) {
        tri(g, x - r, y, x + r, y, x, y - r * 2.2f, c); tri(g, x - r, y, x + r, y, x, y + r * 2.2f, c)
        tri(g, x, y - r, x, y + r, x - r * 2.2f, y, c); tri(g, x, y - r, x, y + r, x + r * 2.2f, y, c)
    }

    private fun cloud(g: Canvas, x: Float, y: Float, s: Float) {
        circ(g, x, y, 9f * s, "#fff"); circ(g, x + 11f * s, y - 4f * s, 11f * s, "#fff")
        circ(g, x + 23f * s, y, 9f * s, "#fff"); rr(g, x - 2f * s, y, 28f * s, 9f * s, 4f * s, "#fff")
    }

    /** Toilette vue de face : cx = centre, by = sol, s = echelle. */
    private fun toilet(g: Canvas, cx: Float, by: Float, s: Float, noShadow: Boolean = false) {
        gsave(g); g.translate(cx, by); g.scale(s, s); lineW = 2f
        if (!noShadow) ell(g, 0f, 0f, 30f, 4.5f, "rgba(15,23,42,.18)")
        val w = "#F8FAFC"; val e = "#7B8598"
        rr(g, -16f, -64f, 32f, 22f, 5f, w, e); circ(g, 0f, -58f, 2.6f, "#FACC15")
        rr(g, -11f, -14f, 22f, 14f, 3f, w, e)
        path.reset(); path.moveTo(-24f, -37f); path.quadTo(-22f, -16f, -10f, -12f); path.lineTo(10f, -12f)
        path.quadTo(22f, -16f, 24f, -37f); path.close()
        fillPath(g, w); strokeCol = cc(e); strokePath(g)
        ell(g, 0f, -38f, 25f, 7f, w, e); ell(g, 0f, -38f, 18f, 4f, "#7DD3FC")
        grestore(g)
    }

    /** Petit personnage rond (« Bou ») : mode HOLD / RUN / HAPPY. */
    @Suppress("unused")
    private fun blob(g: Canvas, x: Float, y: Float, mode: Int, t: Float, col: String, dk: String) {
        ell(g, x, y + 1f, 17f, 3.6f, "rgba(15,23,42,.18)")
        cap = Paint.Cap.ROUND; strokeCol = cc(dk); lineW = 5f
        when (mode) {
            HOLD -> { line(g, x - 6f, y - 14f, x + 4f, y - 2f); line(g, x + 6f, y - 14f, x - 4f, y - 2f) }
            RUN -> {
                val sw = sin(t * 22f) * 6f
                line(g, x - 5f, y - 14f, x - 5f + sw, y - 2f); line(g, x + 5f, y - 14f, x + 5f - sw, y - 2f)
            }
            else -> { line(g, x - 5f, y - 14f, x - 7f, y - 2f); line(g, x + 5f, y - 14f, x + 7f, y - 2f) }
        }
        lineW = 2f; line(g, x, y - 46f, x + 2f, y - 53f); circ(g, x + 2f, y - 54f, 3f, "#FACC15")
        ell(g, x, y - 30f, 17f, 18f, col); ell(g, x, y - 23f, 10f, 8f, "rgba(255,255,255,.35)")
        strokeCol = cc(dk); lineW = 4f
        when (mode) {
            HOLD -> { line(g, x - 14f, y - 28f, x - 4f, y - 18f); line(g, x + 14f, y - 28f, x + 4f, y - 18f) }
            HAPPY -> { line(g, x - 15f, y - 32f, x - 24f, y - 44f); line(g, x + 15f, y - 32f, x + 24f, y - 44f) }
            else -> {
                val a = sin(t * 22f) * 4f
                line(g, x + 15f, y - 30f, x + 22f, y - 24f + a); line(g, x - 15f, y - 30f, x - 22f, y - 36f - a)
            }
        }
        if (mode == HAPPY) {
            strokeCol = cc("#1F2937"); lineW = 1.8f
            arcS(g, x - 6f, y - 34f, 3f, PI_F * 1.1f, PI_F * 1.9f)
            arcS(g, x + 6f, y - 34f, 3f, PI_F * 1.1f, PI_F * 1.9f)
            lineW = 2.2f; strokeCol = cc("#7A1F1F"); arcS(g, x, y - 27f, 5f, PI_F * 0.15f, PI_F * 0.85f)
        } else {
            circ(g, x - 6f, y - 35f, 4.6f, "#fff"); circ(g, x + 6f, y - 35f, 4.6f, "#fff")
            circ(g, x - 5.4f, y - 34.6f, 2.2f, "#1F2937"); circ(g, x + 6.6f, y - 34.6f, 2.2f, "#1F2937")
            if (mode == HOLD) {
                strokeCol = cc("#1F2937"); lineW = 1.6f
                line(g, x - 10f, y - 42f, x - 3f, y - 40f); line(g, x + 10f, y - 42f, x + 3f, y - 40f)
                ell(g, x, y - 25f, 2.4f, 2.8f, "#7A1F1F")
            } else {
                lineW = 2f; strokeCol = cc("#7A1F1F"); arcS(g, x, y - 27f, 3.5f, PI_F * 0.15f, PI_F * 0.85f)
            }
        }
        circ(g, x - 11f, y - 28f, 2.4f, "#FF9FB2"); circ(g, x + 11f, y - 28f, 2.4f, "#FF9FB2")
        if (mode == HOLD) {
            val d = (t * 1.5f) % 1f
            ga = 1f - d
            circ(g, x + 20f, y - 44f + d * 10f, 2.2f, "#38BDF8")
            tri(g, x + 18.2f, y - 44.5f + d * 10f, x + 21.8f, y - 44.5f + d * 10f, x + 20f, y - 49f + d * 10f, "#38BDF8")
            ga = 1f
        }
    }

    private fun fish(g: Canvas, x: Float, y: Float, dir: Float, col: String) {
        gsave(g); g.translate(x, y); g.scale(dir, 1f)
        tri(g, -12f, 0f, -24f, -9f, -24f, 9f, col); ell(g, 0f, 0f, 16f, 10f, col)
        circ(g, 8f, -2f, 3f, "#fff"); circ(g, 9f, -2f, 1.5f, "#0F172A")
        strokeCol = cc("rgba(255,255,255,.6)"); lineW = 1.5f; line(g, -2f, -7f, -2f, 7f)
        grestore(g)
    }

    private fun dino(g: Canvas, x: Float, y: Float, mode: Int, t: Float) {
        val gr = "#43B649"; val gd = "#2E8B3A"; val be = "#C8F5A8"; val o = "#FF9F43"
        ell(g, x - 4f, 171f, 26f, 4f, "rgba(15,23,42,.16)")
        cap = Paint.Cap.ROUND
        path.reset(); path.moveTo(x - 10f, y - 30f)
        path.quadTo(x - 30f, y - 24f, x - 36f, y - 12f + sin(t * 8f) * 2f)
        path.quadTo(x - 22f, y - 14f, x - 8f, y - 14f); path.close()
        fillPath(g, gr)
        tri(g, x - 15f, y - 28f, x - 18f, y - 40f, x - 7f, y - 34f, o)
        tri(g, x - 8f, y - 35f, x - 5f, y - 46f, x + 2f, y - 37f, o)
        tri(g, x + 4f, y - 50f, x + 8f, y - 59f, x + 12f, y - 51f, o)
        strokeCol = cc(gd); lineW = 6f
        when (mode) {
            HOLD -> { line(g, x - 6f, y - 13f, x + 4f, y - 2f); line(g, x + 6f, y - 13f, x - 4f, y - 2f) }
            RUN -> {
                val sw = sin(t * 22f) * 6f
                line(g, x - 5f, y - 13f, x - 5f + sw, y - 2f); line(g, x + 5f, y - 13f, x + 5f - sw, y - 2f)
            }
            else -> { line(g, x - 5f, y - 13f, x - 7f, y - 2f); line(g, x + 5f, y - 13f, x + 7f, y - 2f) }
        }
        ell(g, x, y - 25f, 15f, 14f, gr); ell(g, x + 3f, y - 22f, 9f, 9f, be)
        strokeCol = cc(gr); lineW = 4f
        when (mode) {
            HOLD -> { line(g, x + 8f, y - 28f, x + 1f, y - 18f); line(g, x + 12f, y - 27f, x + 4f, y - 17f) }
            HAPPY -> { line(g, x + 9f, y - 28f, x + 18f, y - 36f); line(g, x - 6f, y - 30f, x - 14f, y - 38f) }
            else -> { val a = sin(t * 22f) * 4f; line(g, x + 9f, y - 28f, x + 16f, y - 24f + a) }
        }
        circ(g, x + 12f, y - 43f, 11f, gr); ell(g, x + 19f, y - 40f, 8f, 6.5f, gr)
        circ(g, x + 24f, y - 42f, 1f, gd)
        if (mode == HAPPY) {
            strokeCol = cc("#1F2937"); lineW = 1.8f; arcS(g, x + 14f, y - 46f, 3f, PI_F * 1.1f, PI_F * 1.9f)
            lineW = 2f; strokeCol = cc("#7A1F1F"); arcS(g, x + 19f, y - 38f, 4f, PI_F * 0.15f, PI_F * 0.85f)
        } else {
            circ(g, x + 14f, y - 46f, 4f, "#fff"); circ(g, x + 15f, y - 46f, 2.1f, "#1F2937")
            circ(g, x + 15.6f, y - 46.8f, 0.7f, "#fff")
            if (mode == HOLD) ell(g, x + 20f, y - 36.5f, 2f, 2.4f, "#7A1F1F")
            else { lineW = 2f; strokeCol = cc("#7A1F1F"); arcS(g, x + 19f, y - 38f, 4f, PI_F * 0.15f, PI_F * 0.85f) }
        }
        circ(g, x + 9f, y - 38f, 2.6f, "#FF9FB2")
        if (mode == HOLD) {
            val d = (t * 1.5f) % 1f
            ga = 1f - d
            circ(g, x + 27f, y - 52f + d * 10f, 2.4f, "#38BDF8")
            tri(g, x + 25f, y - 52f + d * 10f, x + 29f, y - 52f + d * 10f, x + 27f, y - 57f + d * 10f, "#38BDF8")
            ga = 1f
        }
    }

    private fun wheel(g: Canvas, x: Float, y: Float, r: Float, ang: Float, c: String) {
        lineW = 2f; circ(g, x, y, r, "#1F2937"); circ(g, x, y, r - 2.4f, c)
        strokeCol = cc("#1F2937"); lineW = 1.6f
        line(g, x + cos(ang) * (r - 2f), y + sin(ang) * (r - 2f), x - cos(ang) * (r - 2f), y - sin(ang) * (r - 2f))
        circ(g, x, y, 1.6f, "#1F2937")
    }

    private fun drop(g: Canvas, x: Float, y: Float, r: Float, sx: Float, sy: Float) {
        gsave(g); g.translate(x, y); g.scale(sx, sy)
        val cy = -r
        path.reset(); path.moveTo(0f, cy - 1.15f * r)
        path.cubicTo(r * 0.35f, cy - 0.6f * r, r, cy - 0.55f * r, r, cy)
        rect.set(-r, cy - r, r, cy + r)
        path.arcTo(rect, 0f, 180f, false)
        path.cubicTo(-r, cy - 0.55f * r, -r * 0.35f, cy - 0.6f * r, 0f, cy - 1.15f * r)
        path.close()
        fillPath(g, "#4FC3F7"); lineW = 2f; strokeCol = cc("#0288D1"); strokePath(g)
        ell(g, -r * 0.45f, cy - r * 0.35f, r * 0.18f, r * 0.32f, "rgba(255,255,255,.85)")
        val k = r / 13f
        circ(g, -4.2f * k, cy - k, 2.3f * k, "#0F172A"); circ(g, 4.2f * k, cy - k, 2.3f * k, "#0F172A")
        circ(g, -3.5f * k, cy - 1.8f * k, 0.8f * k, "#fff"); circ(g, 4.9f * k, cy - 1.8f * k, 0.8f * k, "#fff")
        circ(g, -7.5f * k, cy + 3.5f * k, 2f * k, "#FF9FB2"); circ(g, 7.5f * k, cy + 3.5f * k, 2f * k, "#FF9FB2")
        strokeCol = cc("#0F172A"); lineW = 1.6f * k
        arcS(g, 0f, cy + 2.5f * k, 4f * k, PI_F * 0.15f, PI_F * 0.85f)
        grestore(g)
    }

    // ---------------------------------------------------------------- scenes

    /* Le gros bouton */
    private fun sceneBouton(g: Canvas, t: Float) {
        sky(g, "#38BDF8", "#0EA5E9")
        lineW = 3f; strokeCol = Color.WHITE
        for (k in 0 until 3) {
            val a = (t * 0.6f + k / 3f) % 1f
            ga = 1f - a
            circ(g, 160f, 100f, 56f + a * 70f, null, "#fff")
        }
        ga = 1f
        val sc = 1f + sin(t * 3f) * 0.05f
        circ(g, 160f, 104f, 56f * sc, "rgba(3,105,161,.35)")
        circ(g, 160f, 100f, 56f * sc, "#fff"); circ(g, 160f, 100f, 48f * sc, "#FACC15")
        toilet(g, 160f, 100f + 32f * sc, 0.95f * sc, true)
    }

    /* Aquarium */
    private val aquaSx = floatArrayOf(24f, 46f, 270f, 296f)
    private val aquaSp = floatArrayOf(22f, 14f, 18f)
    private val aquaY = floatArrayOf(60f, 104f, 140f)
    private val aquaCol = arrayOf("#FB923C", "#FACC15", "#F472B6")
    private val aquaDir = floatArrayOf(1f, -1f, 1f)

    private fun sceneAqua(g: Canvas, t: Float) {
        sky(g, "#22D3EE", "#075985")
        ga = 0.12f
        for (r in 0 until 4) {
            tri(g, 40f + r * 80f, 0f, 70f + r * 80f, 0f, 20f + r * 80f + sin(t * 0.5f + r) * 10f, 180f, "#fff")
        }
        ga = 1f
        ell(g, 160f, 200f, 200f, 26f, "#FCD34D"); circ(g, 70f, 182f, 5f, "#F472B6"); circ(g, 230f, 186f, 4f, "#FB923C")
        strokeCol = cc("#22C55E"); lineW = 6f; cap = Paint.Cap.ROUND
        for (i in aquaSx.indices) {
            val x = aquaSx[i]
            path.reset(); path.moveTo(x, 190f)
            path.quadTo(x + sin(t + i) * 10f, 150f, x + sin(t * 0.8f + i) * 6f, 110f + i * 8f)
            strokePath(g)
        }
        for (f in 0 until 3) {
            val pos = (t * aquaSp[f] + f * 120f) % 380f - 30f
            val xx = if (aquaDir[f] > 0f) pos else 320f - pos
            fish(g, xx, aquaY[f] + sin(t * 1.3f + f) * 6f, aquaDir[f], aquaCol[f])
        }
        lineW = 1.5f
        for (b in 0 until 14) {
            val by = 196f - ((t * 22f + b * 37f) % 210f)
            circ(g, 30f + b * 20f + sin(t * 2f + b) * 3f, by, 2f + (b % 3), "rgba(255,255,255,.2)", "rgba(255,255,255,.8)")
        }
    }

    /* Le dino qui danse : danse pendant le blocage, court puis content pendant la fete. */
    private fun sceneDino(g: Canvas, t: Float, s: SceneState) {
        sky(g, "#9ED8FF", "#E3F5FF")
        circ(g, 286f, 30f, 14f, "#FFE066"); circ(g, 286f, 30f, 9f, "#FFF3A8")
        cloud(g, 30f + (t * 4f) % 40f, 30f, 1f); cloud(g, 150f, 22f, 0.8f)
        fillRect(g, 0f, 170f, 320f, 30f, "#7ED957"); fillRect(g, 0f, 170f, 320f, 4f, "#5DBB43")
        for (i in 0 until 12) circ(g, 12f + i * 27f, 186f + (i % 2) * 6f, 2f, if (i % 3 != 0) "#FFFFFF" else "#FFD54F")
        toilet(g, 262f, 172f, 1.05f)
        val x: Float
        val y: Float
        val mode: Int
        val c: Float
        if (!s.celebrate) {
            c = t % 3.2f
            mode = HOLD; x = 70f + sin(c * 9f) * 3f; y = 170f - abs(sin(c * 7f)) * 9f
        } else if (s.celebrateT < 1.4f) {
            c = 3.2f + s.celebrateT
            mode = RUN; x = 70f + ease(clamp((c - 3.2f) / 1.4f, 0f, 1f)) * 135f; y = 170f - abs(sin(c * 18f)) * 4f
        } else {
            c = 4.6f
            mode = HAPPY; x = 205f; y = 170f
        }
        if (mode == RUN) for (k in 0 until 3) circ(g, x - 22f - k * 9f, 166f - k, 4f - k, "rgba(160,140,110,.45)")
        dino(g, x, y, mode, t)
        if (mode == HOLD) bubble(g, x + 14f, y - 76f, if (t % 0.6f < 0.3f) "!" else "!!", 16f)
        if (mode == HAPPY) {
            bubble(g, x + 16f, y - 78f, "Ouf !", 18f)
            val k = (t * 3f) % 1f
            sparkle(g, 232f, 104f, 2f + k * 2f, "#FACC15"); sparkle(g, 295f, 120f, 3f - k * 1.5f, "#FACC15")
        }
    }

    /* La fusee-toilette : barre = progress ; 3-2-1 puis decollage pendant la fete. */
    private fun sceneFusee(g: Canvas, t: Float, s: SceneState) {
        sky(g, "#1B2466", "#4A3596")
        for (i in 0 until 34) {
            ga = 0.4f + 0.6f * abs(sin(t * 1.5f + i))
            circ(g, ((i * 73) % 320).toFloat(), ((i * 37) % 150).toFloat(), if (i % 4 != 0) 1f else 1.7f, "#fff")
        }
        ga = 1f
        circ(g, 40f, 34f, 13f, "#F1E9C9"); circ(g, 36f, 30f, 3f, "#DCD2AE"); circ(g, 45f, 39f, 2.2f, "#DCD2AE")
        fillRect(g, 0f, 172f, 320f, 28f, "#374151"); rr(g, 168f, 166f, 64f, 8f, 3f, "#6B7280")
        val c = if (s.celebrate) min(4f + s.celebrateT, 8f) else clamp(s.progress, 0f, 1f) * 3.99f
        val p = if (s.celebrate) 1f else clamp(s.progress, 0f, 1f)
        val up = if (c > 5.5f) (c - 5.5f) / 2.5f else 0f
        val lift = up * up * 270f
        val dx = if (c > 4f && c < 5.5f) sin(c * 70f) * 1.2f else 0f
        val cx = 200f
        val by = 166f
        if (c > 5.3f) {
            val sm = clamp((c - 5.3f) * 1.2f, 0f, 1f)
            ga = 1f - sm * 0.6f
            for (k in 0 until 5) {
                circ(g, cx - 24f - k * 10f * sm, 166f - k * 3f, 7f + sm * 8f, "#D1D5DB")
                circ(g, cx + 24f + k * 10f * sm, 166f - k * 3f, 7f + sm * 8f, "#D1D5DB")
            }
            ga = 1f
        }
        ell(g, cx, 168f, 34f * max(0f, 1f - up * 1.6f), 4f, "rgba(0,0,0,.3)")
        gsave(g); g.translate(dx, -lift)
        if (c > 5.2f) {
            val f = abs(sin(t * 30f)) * 8f
            tri(g, cx - 11f, by - 1f, cx + 11f, by - 1f, cx, by + 22f + f, "#FB923C")
            tri(g, cx - 6f, by - 1f, cx + 6f, by - 1f, cx, by + 12f + f * 0.6f, "#FDE047")
        }
        tri(g, cx - 21f, by - 34f, cx - 36f, by - 2f, cx - 12f, by - 14f, "#EF4444")
        tri(g, cx + 21f, by - 34f, cx + 36f, by - 2f, cx + 12f, by - 14f, "#EF4444")
        toilet(g, cx, by, 1f, true)
        tri(g, cx - 16f, by - 63f, cx + 16f, by - 63f, cx, by - 90f, "#EF4444")
        lineW = 2f; circ(g, cx, by - 72f, 4f, "#7DD3FC", "#fff")
        grestore(g)
        lineW = 2f; rr(g, 22f, 150f, 120f, 14f, 7f, "#0F1440", "#fff")
        if (p > 0.02f) rr(g, 24f, 152f, 116f * p, 10f, 5f, if (p >= 1f) "#FACC15" else "#4ADE80")
        val bx = 82f
        val bty = 92f
        if (c < 4f) {
            circ(g, bx, bty, 32f, "#2E3A8C"); ga = 0.35f; txt(g, "3", bx, bty + 3f, 44f, "#fff"); ga = 1f
        } else if (c < 5.5f) {
            val n = 3 - floor((c - 4f) / 0.5f).toInt()
            val q = ((c - 4f) % 0.5f) / 0.5f
            circ(g, bx, bty, 32f + (1f - q) * 4f, "#FACC15")
            txt(g, n.toString(), bx, bty + 3f, 46f + (1f - q) * 8f, "#1F2937")
        } else {
            val rs = 6f + ((t * 2f) % 1f) * 4f
            sparkle(g, bx, bty, rs, "#FACC15"); sparkle(g, bx - 30f, bty + 20f, rs * 0.5f, "#fff")
            sparkle(g, bx + 28f, bty - 22f, rs * 0.6f, "#fff")
        }
    }

    /* Le petit train : position = progress, entre en gare pendant la fete. */
    private val trainCols = arrayOf("#3B82F6", "#FACC15")

    private fun sceneTrain(g: Canvas, t: Float, s: SceneState) {
        sky(g, "#BDE8FF", "#FFF1D6")
        ell(g, 60f, 168f, 90f, 40f, "#A7E08F"); ell(g, 190f, 172f, 110f, 34f, "#93D67A")
        cloud(g, 120f, 34f, 0.9f); cloud(g, 220f - (t * 3f) % 30f, 20f, 0.7f)
        fillRect(g, 0f, 160f, 320f, 40f, "#7ED957")
        fillRect(g, 0f, 158f, 320f, 7f, "#C9B79C")
        var sx = 2f
        while (sx < 320f) { rr(g, sx, 160f, 7f, 5f, 1f, "#8B5A2B"); sx += 13f }
        strokeCol = cc("#6B7280"); lineW = 2.5f; line(g, 0f, 158.5f, 320f, 158.5f)
        rr(g, 240f, 150f, 80f, 8f, 2f, "#9CA3AF")
        lineW = 2f; rr(g, 252f, 96f, 62f, 54f, 4f, "#FDE68A", "#B45309")
        tri(g, 246f, 98f, 320f, 98f, 283f, 70f, "#EF4444")
        rr(g, 262f, 102f, 42f, 20f, 4f, "#1D4ED8"); txt(g, "WC", 283f, 113f, 15f, "#fff")
        rr(g, 270f, 126f, 26f, 24f, 3f, "#B45309")
        toilet(g, 283f, 150f, 0.34f, true)
        val p = if (s.celebrate) lerp(0.9f, 1f, ease(clamp(s.celebrateT / 1.5f, 0f, 1f)))
        else clamp(s.progress, 0f, 1f) * 0.9f
        val bar = if (s.celebrate) 1f else clamp(s.progress, 0f, 1f)
        val front = lerp(-10f, 245f, p)
        val ang = front / 6f
        val lx = front - 54f
        for (w in 0 until 2) {
            val wx = lx - 46f * (w + 1)
            fillRect(g, wx + 38f, 140f, 10f, 3f, "#374151")
            rr(g, wx, 120f, 40f, 28f, 4f, trainCols[w]); rr(g, wx, 120f, 40f, 5f, 2f, if (w != 0) "#EAB308" else "#2563EB")
            rr(g, wx + 6f, 127f, 11f, 10f, 2f, "#E0F2FE"); rr(g, wx + 23f, 127f, 11f, 10f, 2f, "#E0F2FE")
            wheel(g, wx + 10f, 151f, 6f, ang, "#F87171"); wheel(g, wx + 30f, 151f, 6f, ang, "#F87171")
        }
        if (p < 1f) for (k in 0 until 4) {
            val a = (t * 1.4f + k / 4f) % 1f
            ga = 0.8f * (1f - a)
            circ(g, lx + 44f - a * 28f, 104f - a * 50f, 4f + a * 9f, "#fff")
            ga = 1f
        }
        rr(g, lx + 38f, 104f, 10f, 18f, 2f, "#374151"); rr(g, lx + 36f, 102f, 14f, 4f, 2f, "#111827")
        rr(g, lx + 18f, 122f, 36f, 26f, 7f, "#E53935"); circ(g, lx + 52f, 134f, 3f, "#FDE047")
        rr(g, lx, 106f, 24f, 42f, 3f, "#E53935"); rr(g, lx - 3f, 102f, 30f, 6f, 2f, "#B71C1C")
        rr(g, lx + 4f, 112f, 15f, 13f, 2f, "#BFE9FF")
        fillRect(g, lx + 54f, 142f, 5f, 5f, "#374151")
        wheel(g, lx + 11f, 150f, 7.5f, ang, "#FACC15"); wheel(g, lx + 40f, 151f, 6.5f, ang, "#FACC15")
        if (s.celebrate && s.celebrateT >= 1.5f) bubble(g, lx + 22f, 80f, "Tchou !", 16f)
        rr(g, 20f, 178f, 280f, 10f, 5f, "rgba(255,255,255,.7)")
        if (bar > 0.02f) rr(g, 20f, 178f, 280f * bar, 10f, 5f, "#F59E0B")
    }

    /* Plouf la goutte : rebondit pendant le blocage, plonge « Plouf » pendant la fete. */
    private fun sceneGoutte(g: Canvas, t: Float, s: SceneState) {
        fillRect(g, 0f, 0f, 320f, 200f, "#E0F2FE")
        strokeCol = cc("#BAE6FD"); lineW = 1.5f
        var i = 20f
        while (i < 320f) { line(g, i, 0f, i, 172f); i += 22f }
        var j = 20f
        while (j < 172f) { line(g, 0f, j, 320f, j); j += 22f }
        fillRect(g, 0f, 172f, 320f, 28f, "#CBD5E1"); fillRect(g, 0f, 172f, 320f, 3f, "#94A3B8")
        val cx = 255f
        val by = 172f
        val sc = 1.15f
        val wy = by - 38f * sc
        toilet(g, cx, by, sc)
        val c = if (s.celebrate) 3f + s.celebrateT else t % 3f
        val ground = 172f
        if (c < 3f) {
            val k = c / 3f
            val x = 40f + k * 140f
            val b = abs(sin(PI_F * 3f * k))
            val y = ground - b * 46f
            val sq = if (b < 0.15f) 1f + (0.15f - b) * 2.2f else 1f
            ell(g, x, ground + 3f, 12f * (1f - b * 0.5f), 3f, "rgba(15,23,42,.15)")
            drop(g, x, y, 13f, sq, 1f / sq)
        } else if (c < 3.9f) {
            val m = (c - 3f) / 0.9f
            val x2 = lerp(180f, cx, m)
            val y2 = lerp(ground, wy + 4f, m) - sin(PI_F * m) * 85f
            val k = if (m > 0.8f) 1f - (m - 0.8f) * 4f else 1f
            drop(g, x2, y2, 13f * max(k, 0.2f), 1f, 1f)
        } else {
            val e = c - 3.9f
            val a = clamp(1f - e, 0f, 1f)
            if (a > 0f) {
                lineW = 2f; ga = a
                ell(g, cx, wy, 10f + e * 20f, 2.5f + e * 4f, null, "#0288D1")
                for (n in 0 until 10) {
                    val an = -PI_F * (0.1f + 0.8f * n / 9f)
                    val v = 70f + (n % 3) * 18f
                    circ(g, cx + cos(an) * v * e, wy + sin(an) * v * e + 120f * e * e, 3.4f, "#4FC3F7")
                }
                ga = 1f
            }
            txt(g, "Plouf !", cx - 40f, 52f, 30f * (1f + max(0f, 0.25f - e) * 2f), "#0284C7", "#fff")
        }
    }

    /* Cadre photo : l'image du parent si elle existe, sinon le cadre « ta photo ici ». */
    private fun scenePhoto(g: Canvas, t: Float, s: SceneState) {
        val img: Bitmap? = s.image
        if (img != null && img.width > 0 && img.height > 0) {
            fillRect(g, 0f, 0f, 320f, 200f, "#10131F")
            val k = min(320f / img.width, 200f / img.height)
            val w = img.width * k
            val h = img.height * k
            rect.set((320f - w) / 2f, (200f - h) / 2f, (320f + w) / 2f, (200f + h) / 2f)
            g.drawBitmap(img, null, rect, bp)
            return
        }
        sky(g, "#FFE3EC", "#FFF6D6")
        lineW = 2f; rr(g, 86f, 18f, 148f, 150f, 10f, "#F59E0B", "#B45309"); rr(g, 98f, 30f, 124f, 126f, 4f, "#FFFDF5")
        for (d in 0 until 8) circ(g, 92f + d * 19.4f, 24f, 2.5f, "#FDE68A")
        cap = Paint.Cap.ROUND; lineW = 3f
        circ(g, 196f, 58f, 11f, "#FACC15"); strokeCol = cc("#FACC15")
        for (i in 0 until 8) {
            val a = i * PI_F / 4f + t * 0.3f
            line(g, 196f + cos(a) * 15f, 58f + sin(a) * 15f, 196f + cos(a) * 21f, 58f + sin(a) * 21f)
        }
        strokeCol = cc("#4ADE80"); line(g, 104f, 140f, 216f, 138f)
        rr(g, 120f, 104f, 44f, 34f, 2f, "#F87171"); tri(g, 114f, 106f, 170f, 106f, 142f, 80f, "#8B5CF6")
        rr(g, 136f, 118f, 12f, 20f, 2f, "#60A5FA"); strokeCol = cc("#3B82F6"); line(g, 186f, 138f, 186f, 120f)
        circ(g, 186f, 116f, 6f, "#F472B6")
        val k = (t * 1.2f) % 1f
        sparkle(g, 60f, 48f, 4f + k * 3f, "#FACC15"); sparkle(g, 264f, 112f, 7f - k * 3f, "#FACC15")
        sparkle(g, 258f, 38f, 3f + (1f - k) * 2f, "#F472B6"); sparkle(g, 56f, 132f, 3f + k * 2f, "#60A5FA")
        sp.pathEffect = dash; lineW = 1.5f; rr(g, 98f, 30f, 124f, 126f, 4f, null, "#D97706"); sp.pathEffect = null
        rr(g, 116f, 176f, 88f, 18f, 9f, "#BE185D"); txt(g, "ta photo ici", 160f, 185.5f, 11f, "#fff")
    }
}
