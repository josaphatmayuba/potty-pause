package com.famille.rappelpipi

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
import kotlin.math.sin

/**
 * Scenes de theme (lot A) : geant, bd, sablier, feu, nuit, ballon, carte, porte.
 * Portage du prototype JS (rappel-pipi-idees.html) dans le repere logique 320 x 200.
 */
object IdeaScenesA {

    private const val PI_F = 3.1415927f
    private const val RAD2DEG = 57.29578f
    private const val HOLD = 0
    private const val RUN = 1
    private const val HAPPY = 2

    // ---- Couleurs ----
    private fun op(rgb: Int): Int = rgb or 0xFF000000.toInt()
    private fun ca(a: Float, rgb: Int): Int = ((a * 255f + 0.5f).toInt() shl 24) or (rgb and 0xFFFFFF)

    private val WHITE = op(0xFFFFFF)
    private val INK = op(0x1F2937)
    private val SHADOW = ca(0.18f, 0x0F172A)
    private val YEL = op(0xFACC15)
    private val CONF = intArrayOf(op(0xF87171), op(0xFACC15), op(0x4ADE80), op(0x60A5FA), op(0xC084FC), op(0xFB923C))

    // ---- Etat de dessin (equivalent du contexte 2D JS) ----
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val fm = Paint.FontMetrics()
    private val path = Path()
    private val hg = Path()
    private val rect = RectF()
    private val dash = DashPathEffect(floatArrayOf(6f, 6f), 0f)
    private val grads = HashMap<Long, LinearGradient>()

    private var ga = 1f
    private var lw = 1f
    private var strokeCol = op(0x000000)
    private var cap = Paint.Cap.BUTT

    private val stLw = FloatArray(16)
    private val stCol = IntArray(16)
    private val stGa = FloatArray(16)
    private val stCap = arrayOfNulls<Paint.Cap>(16)
    private var sd = 0

    fun draw(id: String, g: Canvas, t: Float, s: SceneState): Boolean {
        ga = 1f; lw = 1f; strokeCol = op(0x000000); cap = Paint.Cap.BUTT; sd = 0
        sp.pathEffect = null
        when (id) {
            "geant" -> sceneGeant(g, t, s)
            "bd" -> sceneBD(g, t, s)
            "sablier" -> sceneSablier(g, t, s)
            "feu" -> sceneFeu(g, t, s)
            "nuit" -> sceneNuit(g, t)
            "ballon" -> sceneBallon(g, t, s)
            "carte" -> sceneCarte(g, t, s)
            "porte" -> scenePorte(g, t, s)
            else -> return false
        }
        return true
    }

    // ---- Helpers ----
    private fun clamp(v: Float, a: Float, b: Float): Float = if (v < a) a else if (v > b) b else v
    private fun lerp(a: Float, b: Float, k: Float): Float = a + (b - a) * k
    private fun ease(k: Float): Float = k * k * (3f - 2f * k)
    private fun bez(k: Float, a: Float, b: Float, c: Float, d: Float): Float {
        val u = 1f - k
        return u * u * u * a + 3f * u * u * k * b + 3f * u * k * k * c + k * k * k * d
    }

    private fun sv(g: Canvas) {
        g.save()
        stLw[sd] = lw; stCol[sd] = strokeCol; stGa[sd] = ga; stCap[sd] = cap; sd++
    }

    private fun rs(g: Canvas) {
        g.restore()
        sd--
        lw = stLw[sd]; strokeCol = stCol[sd]; ga = stGa[sd]; cap = stCap[sd] ?: Paint.Cap.BUTT
    }

    private fun alphaOf(c: Int): Int = (Color.alpha(c) * clamp(ga, 0f, 1f)).toInt()

    private fun fillP(c: Int): Paint {
        fp.shader = null
        fp.color = c
        fp.alpha = alphaOf(c)
        return fp
    }

    private fun strokeP(): Paint {
        sp.color = strokeCol
        sp.alpha = alphaOf(strokeCol)
        sp.strokeWidth = lw
        sp.strokeCap = cap
        sp.strokeJoin = Paint.Join.MITER
        return sp
    }

    private fun fr(g: Canvas, x: Float, y: Float, w: Float, h: Float, c: Int) {
        g.drawRect(x, y, x + w, y + h, fillP(c))
    }

    private fun rr(g: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, fill: Int = 0, stroke: Int = 0) {
        rect.set(x, y, x + w, y + h)
        if (fill != 0) g.drawRoundRect(rect, r, r, fillP(fill))
        if (stroke != 0) { strokeCol = stroke; g.drawRoundRect(rect, r, r, strokeP()) }
    }

    private fun circ(g: Canvas, x: Float, y: Float, r: Float, fill: Int = 0, stroke: Int = 0) {
        val rad = max(r, 0f)
        if (fill != 0) g.drawCircle(x, y, rad, fillP(fill))
        if (stroke != 0) { strokeCol = stroke; g.drawCircle(x, y, rad, strokeP()) }
    }

    private fun ell(g: Canvas, x: Float, y: Float, rx: Float, ry: Float, fill: Int = 0, stroke: Int = 0) {
        val a = max(rx, 0f); val b = max(ry, 0f)
        rect.set(x - a, y - b, x + a, y + b)
        if (fill != 0) g.drawOval(rect, fillP(fill))
        if (stroke != 0) { strokeCol = stroke; g.drawOval(rect, strokeP()) }
    }

    private fun tri(g: Canvas, a: Float, b: Float, c: Float, d: Float, e: Float, f: Float, fill: Int) {
        path.reset(); path.moveTo(a, b); path.lineTo(c, d); path.lineTo(e, f); path.close()
        g.drawPath(path, fillP(fill))
    }

    private fun line(g: Canvas, a: Float, b: Float, c: Float, d: Float) {
        g.drawLine(a, b, c, d, strokeP())
    }

    /** Arc trace (equivalent de arc(x, y, r, a0, a1) + stroke en JS, sens horaire). */
    private fun arcS(g: Canvas, x: Float, y: Float, r: Float, a0: Float, a1: Float) {
        rect.set(x - r, y - r, x + r, y + r)
        g.drawArc(rect, a0 * RAD2DEG, (a1 - a0) * RAD2DEG, false, strokeP())
    }

    private fun txt(g: Canvas, s: String, x: Float, y: Float, size: Float, fill: Int, stroke: Int = 0) {
        tp.textSize = size
        tp.getFontMetrics(fm)
        val by = y - (fm.ascent + fm.descent) / 2f
        if (stroke != 0) {
            tp.style = Paint.Style.STROKE
            tp.strokeWidth = size / 5f
            tp.strokeJoin = Paint.Join.ROUND
            tp.color = stroke
            tp.alpha = alphaOf(stroke)
            g.drawText(s, x, by, tp)
        }
        tp.style = Paint.Style.FILL
        tp.color = fill
        tp.alpha = alphaOf(fill)
        g.drawText(s, x, by, tp)
    }

    private fun bubble(g: Canvas, x: Float, y: Float, s: String, size: Float) {
        tp.textSize = size
        val w = tp.measureText(s) + 18f
        val h = size + 10f
        lw = 2f
        val edge = op(0x374151)
        tri(g, x - 8f, y + h / 2f - 1f, x + 2f, y + h / 2f - 1f, x - 10f, y + h / 2f + 10f, edge)
        rr(g, x - w / 2f, y - h / 2f, w, h, 9f, WHITE, edge)
        tri(g, x - 6f, y + h / 2f - 2f, x, y + h / 2f - 2f, x - 8f, y + h / 2f + 6f, WHITE)
        txt(g, s, x, y + 1f, size, INK)
    }

    private fun sky(g: Canvas, top: Int, bottom: Int) {
        val key = (top.toLong() shl 32) or (bottom.toLong() and 0xFFFFFFFFL)
        val gr = grads.getOrPut(key) { LinearGradient(0f, 0f, 0f, 200f, top, bottom, Shader.TileMode.CLAMP) }
        fp.color = op(0x000000)
        fp.alpha = (255f * clamp(ga, 0f, 1f)).toInt()
        fp.shader = gr
        g.drawRect(0f, 0f, 320f, 200f, fp)
        fp.shader = null
    }

    private fun sparkle(g: Canvas, x: Float, y: Float, r: Float, c: Int) {
        tri(g, x - r, y, x + r, y, x, y - r * 2.2f, c); tri(g, x - r, y, x + r, y, x, y + r * 2.2f, c)
        tri(g, x, y - r, x, y + r, x - r * 2.2f, y, c); tri(g, x, y - r, x, y + r, x + r * 2.2f, y, c)
    }

    private fun cloud(g: Canvas, x: Float, y: Float, s: Float) {
        circ(g, x, y, 9f * s, WHITE); circ(g, x + 11f * s, y - 4f * s, 11f * s, WHITE)
        circ(g, x + 23f * s, y, 9f * s, WHITE); rr(g, x - 2f * s, y, 28f * s, 9f * s, 4f * s, WHITE)
    }

    /** Toilette vue de face : cx = centre, by = sol, s = echelle. */
    private fun toilet(g: Canvas, cx: Float, by: Float, s: Float, noShadow: Boolean = false) {
        sv(g); g.translate(cx, by); g.scale(s, s); lw = 2f
        if (!noShadow) ell(g, 0f, 0f, 30f, 4.5f, SHADOW)
        val w = op(0xF8FAFC); val e = op(0x7B8598)
        rr(g, -16f, -64f, 32f, 22f, 5f, w, e); circ(g, 0f, -58f, 2.6f, YEL)
        rr(g, -11f, -14f, 22f, 14f, 3f, w, e)
        path.reset(); path.moveTo(-24f, -37f); path.quadTo(-22f, -16f, -10f, -12f); path.lineTo(10f, -12f)
        path.quadTo(22f, -16f, 24f, -37f); path.close()
        g.drawPath(path, fillP(w)); strokeCol = e; g.drawPath(path, strokeP())
        ell(g, 0f, -38f, 25f, 7f, w, e); ell(g, 0f, -38f, 18f, 4f, op(0x7DD3FC))
        rs(g)
    }

    /** Petit personnage rond (Bou) : mode HOLD / RUN / HAPPY. */
    private fun blob(g: Canvas, x: Float, y: Float, mode: Int, t: Float, col: Int, dk: Int) {
        ell(g, x, y + 1f, 17f, 3.6f, SHADOW)
        cap = Paint.Cap.ROUND; strokeCol = dk; lw = 5f
        when (mode) {
            HOLD -> { line(g, x - 6f, y - 14f, x + 4f, y - 2f); line(g, x + 6f, y - 14f, x - 4f, y - 2f) }
            RUN -> {
                val sw = sin(t * 22f) * 6f
                line(g, x - 5f, y - 14f, x - 5f + sw, y - 2f); line(g, x + 5f, y - 14f, x + 5f - sw, y - 2f)
            }
            else -> { line(g, x - 5f, y - 14f, x - 7f, y - 2f); line(g, x + 5f, y - 14f, x + 7f, y - 2f) }
        }
        lw = 2f; line(g, x, y - 46f, x + 2f, y - 53f); circ(g, x + 2f, y - 54f, 3f, YEL)
        ell(g, x, y - 30f, 17f, 18f, col); ell(g, x, y - 23f, 10f, 8f, ca(0.35f, 0xFFFFFF))
        strokeCol = dk; lw = 4f
        when (mode) {
            HOLD -> { line(g, x - 14f, y - 28f, x - 4f, y - 18f); line(g, x + 14f, y - 28f, x + 4f, y - 18f) }
            HAPPY -> { line(g, x - 15f, y - 32f, x - 24f, y - 44f); line(g, x + 15f, y - 32f, x + 24f, y - 44f) }
            else -> {
                val a = sin(t * 22f) * 4f
                line(g, x + 15f, y - 30f, x + 22f, y - 24f + a); line(g, x - 15f, y - 30f, x - 22f, y - 36f - a)
            }
        }
        val mouth = op(0x7A1F1F)
        if (mode == HAPPY) {
            strokeCol = INK; lw = 1.8f
            arcS(g, x - 6f, y - 34f, 3f, PI_F * 1.1f, PI_F * 1.9f)
            arcS(g, x + 6f, y - 34f, 3f, PI_F * 1.1f, PI_F * 1.9f)
            lw = 2.2f; strokeCol = mouth; arcS(g, x, y - 27f, 5f, PI_F * 0.15f, PI_F * 0.85f)
        } else {
            circ(g, x - 6f, y - 35f, 4.6f, WHITE); circ(g, x + 6f, y - 35f, 4.6f, WHITE)
            circ(g, x - 5.4f, y - 34.6f, 2.2f, INK); circ(g, x + 6.6f, y - 34.6f, 2.2f, INK)
            if (mode == HOLD) {
                strokeCol = INK; lw = 1.6f
                line(g, x - 10f, y - 42f, x - 3f, y - 40f); line(g, x + 10f, y - 42f, x + 3f, y - 40f)
                ell(g, x, y - 25f, 2.4f, 2.8f, mouth)
            } else {
                lw = 2f; strokeCol = mouth; arcS(g, x, y - 27f, 3.5f, PI_F * 0.15f, PI_F * 0.85f)
            }
        }
        val cheek = op(0xFF9FB2)
        circ(g, x - 11f, y - 28f, 2.4f, cheek); circ(g, x + 11f, y - 28f, 2.4f, cheek)
        if (mode == HOLD) {
            val d = (t * 1.5f) % 1f
            val drop = op(0x38BDF8)
            val old = ga
            ga = 1f - d
            circ(g, x + 20f, y - 44f + d * 10f, 2.2f, drop)
            tri(g, x + 18.2f, y - 44.5f + d * 10f, x + 21.8f, y - 44.5f + d * 10f, x + 20f, y - 49f + d * 10f, drop)
            ga = old
        }
    }

    private fun blobAt(g: Canvas, x: Float, y: Float, sc: Float, mode: Int, t: Float, col: Int, dk: Int) {
        sv(g); g.translate(x, y); g.scale(sc, sc); blob(g, 0f, 0f, mode, t, col, dk); rs(g)
    }

    // ---- 1. Personnage geant ----
    private fun sceneGeant(g: Canvas, t: Float, s: SceneState) {
        sky(g, op(0xFFE082), op(0xFFB74D))
        ga = 0.25f
        for (i in 0 until 10) {
            val a = i * 0.63f + t * 0.2f
            tri(g, 150f, 110f, 150f + cos(a) * 260f, 110f + sin(a) * 260f,
                150f + cos(a + 0.3f) * 260f, 110f + sin(a + 0.3f) * 260f, WHITE)
        }
        ga = 1f
        val happy = s.celebrate
        val x = 150f + if (happy) 0f else sin(t * 9f) * 4f
        val y = 196f - abs(sin(t * 7f)) * 6f
        blobAt(g, x, y, 3.2f, if (happy) HAPPY else HOLD, t, op(0xA78BFA), op(0x5B21B6))
        bubble(g, 262f, 42f + sin(t * 4f) * 2f, if (happy) "Bravo !" else "Pipi !", 24f)
    }

    // ---- 2. Bande dessinee en 3 cases ----
    private val bdBg = intArrayOf(op(0xE0F2FE), op(0xFCE7F3), op(0xDCFCE7))

    private fun sceneBD(g: Canvas, t: Float, s: SceneState) {
        fr(g, 0f, 0f, 320f, 200f, op(0xFFF7E0))
        val act = if (s.celebrate) 2 else floor((t % 6f) / 2f).toInt()
        val col = op(0xFB923C); val dk = op(0x9A3412)
        for (i in 0 until 3) {
            val x = 8f + i * 104f; val y = 10f; val w = 96f; val h = 180f
            sv(g)
            rect.set(x, y, x + w, y + h)
            hg.reset(); hg.addRoundRect(rect, 8f, 8f, Path.Direction.CW)
            g.drawPath(hg, fillP(bdBg[i]))
            g.clipPath(hg)
            fr(g, x, 160f, w, 30f, ca(0.08f, 0x000000))
            when (i) {
                0 -> {
                    blobAt(g, x + 48f + sin(t * 9f) * 2f, 160f, 1.35f, HOLD, t, col, dk)
                    bubble(g, x + 70f, 44f, "!", 18f)
                }
                1 -> {
                    toilet(g, x + 78f, 160f, 0.55f)
                    strokeCol = op(0x94A3B8); lw = 2f
                    line(g, x + 6f, 120f, x + 20f, 120f); line(g, x + 4f, 132f, x + 18f, 132f)
                    blobAt(g, x + 40f, 160f - abs(sin(t * 14f)) * 4f, 1.35f, RUN, t, col, dk)
                }
                else -> {
                    toilet(g, x + 70f, 160f, 0.55f)
                    blobAt(g, x + 34f, 160f - abs(sin(t * 6f)) * 8f, 1.35f, HAPPY, t, col, dk)
                    val k = (t * 2f) % 1f
                    sparkle(g, x + 20f, 40f, 3f + k * 2f, YEL); sparkle(g, x + 74f, 54f, 5f - k * 2f, YEL)
                }
            }
            if (i != act) fr(g, x, y, w, h, ca(0.55f, 0xFFFFFF))
            rs(g)
            lw = if (i == act) 4f else 2.5f
            rr(g, x, y, w, h, 8f, 0, INK)
            circ(g, x + 13f, y + 13f, 9f, if (i == act) INK else op(0x9CA3AF))
            txt(g, (i + 1).toString(), x + 13f, y + 14f, 12f, WHITE)
        }
    }

    // ---- 3. Grand sablier (niveau = progress) ----
    private fun sceneSablier(g: Canvas, t: Float, s: SceneState) {
        sky(g, op(0xFDF2E9), op(0xFCE7D6))
        val p = if (s.celebrate) 1f else clamp(s.progress, 0f, 1f)
        val sand = op(0xF59E0B)
        lw = 2f
        hg.reset()
        hg.moveTo(125f, 38f); hg.lineTo(195f, 38f); hg.quadTo(195f, 80f, 162f, 100f); hg.quadTo(195f, 120f, 195f, 162f)
        hg.lineTo(125f, 162f); hg.quadTo(125f, 120f, 158f, 100f); hg.quadTo(125f, 80f, 125f, 38f); hg.close()
        g.drawPath(hg, fillP(ca(0.7f, 0xFFFFFF)))
        sv(g); g.clipPath(hg)
        fr(g, 120f, 38f + 62f * p, 80f, 62f - 62f * p + 1f, sand)
        val hb = 56f * p
        fr(g, 120f, 162f - hb, 80f, hb, sand)
        ell(g, 160f, 162f - hb, 18f * (1f - p * 0.4f), 6f * p, sand)
        if (p < 0.99f) {
            fr(g, 159f, 99f, 2f, 63f - hb, sand)
            for (k in 0 until 3) circ(g, 160f + (k - 1) * 1.5f, 100f + ((t * 80f + k * 20f) % (60f - hb)), 1f, op(0xB45309))
        }
        rs(g)
        strokeCol = op(0x94A3B8); g.drawPath(hg, strokeP())
        val wood = op(0xB45309); val dark = op(0x92400E)
        rr(g, 110f, 26f, 100f, 12f, 5f, wood); rr(g, 110f, 162f, 100f, 12f, 5f, wood)
        rr(g, 114f, 38f, 6f, 124f, 3f, dark); rr(g, 200f, 38f, 6f, 124f, 3f, dark)
        toilet(g, 262f, 176f, 0.55f)
    }

    // ---- 4. Feu tricolore (rouge pendant le blocage, vert a la fete) ----
    private val feuOn = intArrayOf(op(0xEF4444), op(0xF59E0B), op(0x22C55E))

    private fun sceneFeu(g: Canvas, t: Float, s: SceneState) {
        sky(g, op(0x93C5FD), op(0xDBEAFE))
        fr(g, 0f, 168f, 320f, 32f, op(0x9CA3AF)); fr(g, 0f, 168f, 320f, 3f, op(0x6B7280))
        val st: Int; val x: Float; val mode: Int
        if (!s.celebrate) {
            st = if (s.progress >= 0.9f) 1 else 0
            mode = HOLD; x = 150f + sin(t * 9f) * 2f
        } else {
            st = 2
            val c = s.celebrateT
            if (c < 1.6f) { mode = RUN; x = 150f + ease(clamp(c / 1.6f, 0f, 1f)) * 80f }
            else { mode = HAPPY; x = 230f }
        }
        rr(g, 60f, 150f, 8f, 20f, 2f, op(0x374151))
        rr(g, 36f, 12f, 56f, 140f, 14f, INK)
        for (i in 0 until 3) {
            val y = 40f + i * 44f
            if (i == st) { ga = 0.35f + 0.1f * sin(t * 5f); circ(g, 64f, y, 27f, feuOn[i]); ga = 1f }
            circ(g, 64f, y, 17f, if (i == st) feuOn[i] else op(0x4B5563))
            if (i == st) circ(g, 58f, y - 6f, 4f, ca(0.6f, 0xFFFFFF))
        }
        toilet(g, 272f, 170f, 1f)
        blobAt(g, x, 170f, 1.4f, mode, t, op(0x34D399), op(0x065F46))
    }

    // ---- 5. Nuit calme ----
    private fun sceneNuit(g: Canvas, t: Float) {
        sky(g, op(0x1E1B4B), op(0x4338CA))
        val star = op(0xFEF9C3)
        for (i in 0 until 30) {
            ga = 0.35f + 0.5f * abs(sin(t * 0.6f + i * 1.7f))
            sparkle(g, ((i * 71 + 13) % 320).toFloat(), ((i * 43) % 130).toFloat(), if (i % 5 != 0) 0.8f else 1.6f, star)
        }
        val moon = op(0xFDE68A); val spot = op(0xFCD34D); val brown = op(0x92400E)
        ga = 0.25f; circ(g, 110f, 78f, 56f, moon); ga = 1f
        circ(g, 110f, 78f, 42f, moon); circ(g, 92f, 60f, 6f, spot); circ(g, 132f, 92f, 4f, spot); circ(g, 126f, 56f, 3f, spot)
        strokeCol = brown; lw = 2.2f; cap = Paint.Cap.ROUND
        arcS(g, 97f, 74f, 5f, PI_F * 0.1f, PI_F * 0.9f)
        arcS(g, 123f, 74f, 5f, PI_F * 0.1f, PI_F * 0.9f)
        val yawn = max(0f, sin(t * 0.8f))
        ell(g, 110f, 96f, 5f + yawn * 3f, 2f + yawn * 8f, brown)
        val blush = ca(0.5f, 0xFB7185)
        circ(g, 89f, 88f, 4f, blush); circ(g, 131f, 88f, 4f, blush)
        for (z in 0 until 3) {
            val a = (t * 0.35f + z / 3f) % 1f
            ga = 1f - a
            txt(g, "z", 160f + a * 30f, 64f - a * 40f, 10f + a * 10f, op(0xE0E7FF))
        }
        ga = 1f
        ell(g, 70f, 210f, 140f, 46f, op(0x312E81)); ell(g, 260f, 214f, 130f, 50f, op(0x3730A3))
        rr(g, 226f, 132f, 50f, 40f, 3f, op(0x4C1D95)); tri(g, 220f, 134f, 282f, 134f, 251f, 110f, op(0x5B21B6))
        ga = 0.75f + 0.25f * sin(t * 1.2f)
        rr(g, 236f, 142f, 12f, 12f, 2f, op(0xFDE047)); rr(g, 254f, 142f, 12f, 12f, 2f, op(0xFDE047))
        ga = 1f
    }

    // ---- 6. Ballon qui se gonfle (taille = progress, eclate a la fete) ----
    private fun sceneBallon(g: Canvas, t: Float, s: SceneState) {
        sky(g, op(0xFBCFE8), op(0xFDF2F8))
        cloud(g, 30f, 40f, 0.9f); cloud(g, 250f, 30f, 0.8f)
        if (!s.celebrate) {
            val p = clamp(s.progress, 0f, 1f)
            val r = 14f + p * 56f
            val sh = if (p > 0.85f) sin(t * 50f) * 1.5f else 0f
            val cx = 160f + sin(t * 2f) * 3f * p + sh
            val cy = 160f - r - 6f
            strokeCol = op(0x9D174D); lw = 1.5f
            path.reset(); path.moveTo(cx, cy + r + 4f); path.quadTo(cx - 8f, 176f, 160f, 196f)
            g.drawPath(path, strokeP())
            tri(g, cx - 4f, cy + r + 5f, cx + 4f, cy + r + 5f, cx, cy + r - 2f, op(0xB91C1C))
            ell(g, cx, cy, r * 0.92f, r, op(0xEF4444))
            ell(g, cx - r * 0.38f, cy - r * 0.42f, r * 0.14f, r * 0.26f, ca(0.6f, 0xFFFFFF))
            toilet(g, cx, cy + r * 0.5f, r / 95f, true)
        } else {
            val k = s.celebrateT
            for (i in 0 until 44) {
                val a = i * 2.4f
                val v = 60f + (i % 5) * 25f
                rr(g, 160f + cos(a) * v * k, 100f + sin(a) * v * k + 50f * k * k, 4f, 4f, 1f, CONF[i % CONF.size])
            }
            txt(g, "POP !", 160f, 92f, 38f * (1f + max(0f, 0.3f - k) * 2f), op(0xDB2777), WHITE)
        }
    }

    // ---- 7. Carte au tresor (position = progress) ----
    private val carteP = floatArrayOf(52f, 150f, 100f, 30f, 200f, 200f, 264f, 74f)

    private fun sceneCarte(g: Canvas, t: Float, s: SceneState) {
        fr(g, 0f, 0f, 320f, 200f, op(0xE8C98E))
        lw = 2f; rr(g, 8f, 8f, 304f, 184f, 12f, op(0xF7E7C1), op(0xC08A3E))
        circ(g, 150f, 46f, 9f, op(0x86EFAC)); circ(g, 162f, 42f, 7f, op(0x4ADE80))
        circ(g, 214f, 164f, 8f, op(0x86EFAC)); circ(g, 92f, 172f, 6f, op(0x4ADE80))
        ell(g, 236f, 54f, 18f, 8f, op(0x93C5FD))
        val p = carteP
        val brown = op(0x8B5A2B)
        strokeCol = brown; lw = 3f
        path.reset(); path.moveTo(p[0], p[1]); path.cubicTo(p[2], p[3], p[4], p[5], p[6], p[7])
        val sk = strokeP(); sk.pathEffect = dash
        g.drawPath(path, sk)
        sk.pathEffect = null
        rr(g, 26f, 140f, 8f, 28f, 2f, brown); rr(g, 26f, 150f, 52f, 14f, 3f, brown)
        rr(g, 37f, 144f, 12f, 7f, 3f, WHITE); rr(g, 50f, 145f, 28f, 10f, 3f, op(0x60A5FA))
        toilet(g, 268f, 132f, 0.7f, true)
        strokeCol = op(0xDC2626); lw = 5f; cap = Paint.Cap.ROUND
        line(g, 256f, 62f, 272f, 78f); line(g, 272f, 62f, 256f, 78f)
        lw = 1.5f; circ(g, 292f, 174f, 11f, op(0xFDF2DC), brown)
        tri(g, 289f, 174f, 295f, 174f, 292f, 164f, op(0xDC2626)); tri(g, 289f, 174f, 295f, 174f, 292f, 184f, op(0x6B7280))
        txt(g, "N", 292f, 157f, 8f, brown)
        val k = if (s.celebrate) 1f else clamp(s.progress, 0f, 1f)
        val mx = bez(k, p[0], p[2], p[4], p[6])
        val my = bez(k, p[1], p[3], p[5], p[7])
        val mode = if (!s.celebrate && k < 0.98f) RUN else HAPPY
        blobAt(g, mx, my + 8f, 0.7f, mode, t, op(0x60A5FA), op(0x1E3A8A))
    }

    // ---- 8. Porte qui s'ouvre (a la fete ou progress > 0.9) ----
    private fun scenePorte(g: Canvas, t: Float, s: SceneState) {
        fr(g, 0f, 0f, 320f, 200f, op(0xFEF3C7))
        var sx = 0f
        while (sx < 320f) { fr(g, sx, 0f, 7f, 170f, op(0xFDE68A)); sx += 16f }
        fr(g, 0f, 170f, 320f, 30f, op(0xD6A86B))
        val progOpen = if (s.progress > 0.9f) ease(clamp((s.progress - 0.9f) / 0.1f, 0f, 1f)) else 0f
        val celebOpen = if (s.celebrate) ease(clamp(s.celebrateT / 2f, 0f, 1f)) else 0f
        val open = max(progOpen, celebOpen)
        val c = t % 3f
        rr(g, 108f, 28f, 104f, 144f, 4f, op(0x92400E))
        fr(g, 116f, 36f, 88f, 136f, op(0xFFF9C4))
        if (open > 0f) {
            val light = op(0xFDE047)
            ga = open * 0.35f
            tri(g, 116f, 172f, 204f, 172f, 250f, 200f, light); tri(g, 116f, 172f, 204f, 172f, 70f, 200f, light)
            ga = 1f
            toilet(g, 168f, 170f, 0.95f)
            val k = (t * 2f) % 1f
            sparkle(g, 136f, 70f, (2f + k * 2f) * open, YEL); sparkle(g, 190f, 96f, (3f - k) * open, YEL)
        }
        val knock = open <= 0f && (c % 1f) < 0.3f
        val dx = if (knock) sin(t * 60f) * 1.5f else 0f
        val w = 88f * (1f - open * 0.85f)
        rr(g, 116f + dx, 36f, w, 136f, 2f, op(0x3B82F6))
        if (w > 40f) {
            lw = 2f
            val frame = op(0x2563EB)
            rr(g, 124f + dx, 86f, w - 16f, 34f, 3f, 0, frame); rr(g, 124f + dx, 128f, w - 16f, 36f, 3f, 0, frame)
            rr(g, 116f + dx + w / 2f - 15f, 48f, 30f, 18f, 4f, WHITE)
            txt(g, "WC", 116f + dx + w / 2f, 58f, 12f, op(0x1D4ED8))
            circ(g, 116f + dx + w - 10f, 110f, 4.5f, YEL)
        }
        val mode = if (open > 0.9f || s.celebrate) HAPPY else HOLD
        blobAt(g, 62f, 170f, 1.3f, mode, t, op(0xF472B6), op(0x9D174D))
        if (open <= 0f) bubble(g, if (floor(c).toInt() % 2 == 1) 252f else 262f, 50f, "Toc toc !", 16f)
    }
}
