package com.famille.rappelpipi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Theme "blocs" : 6 animations en pixel-art (danse, course, wagonnet, parkour, mineur, portail).
 * Port de la maquette JS (grille 960 x 600) : on dessine dans ce repere puis on reduit a 1/3
 * pour tomber dans le repere logique 320 x 200 fourni par ThemeView.
 * La barre de progression, les confettis et les diamants sont dessines par ThemeView.
 */
object BlocsAnim {
    private const val W = 960f
    private const val H = 600f
    private const val B = 48f

    // Couleurs de JJ (kind 16) et Mikey (kind 17) : choisies par defaut, modifiables ici facilement.
    private const val JJ_SKIN = "#FFC9A8"; private const val JJ_SKIN_D = "#E6A988"
    private const val JJ_HAIR = "#5A3328"; private const val JJ_HAIR_L = "#7B4A3A"
    private const val JJ_HOODIE = "#FDFDFD"; private const val JJ_HOODIE_D = "#E3E3E3"
    private const val JJ_PANTS = "#2A2A2E"; private const val JJ_PANTS_D = "#141416"; private const val JJ_SHOE = "#39FF14"
    private const val JJ_SHOE2 = "#FF00C8"; private const val JJ_EYE = "#6FD6FF"; private const val JJ_PUPIL = "#2A8FD0"
    private val JJ_RAINBOW = arrayOf("#E00000", "#F28A00", "#F5C400", "#3CCB2A", "#00B8D4", "#0A4FC0", "#7A10C0")
    private const val MK_SKIN = "#4DBA32"; private const val MK_SKIN_D = "#33881F"
    private const val MK_HAIR = "#1A1A1A"; private const val MK_HAIR_L = "#D9A826"
    private const val MK_HOODIE = "#2B2B2B"; private const val MK_HOODIE_D = "#171717"
    private const val MK_PANTS = "#5B6068"; private const val MK_PANTS_D = "#40444B"; private const val MK_SHOE = "#F2F2F2"
    private const val MK_SLEEVE = "#DC2F2F"; private const val MK_RED = "#D41414"; private const val MK_GOLD = "#E8B830"; private const val MK_BLACK = "#111111"

    private const val STAND = 0
    private const val CROSS = 1
    private const val WALK0 = 2
    private const val WALK1 = 3
    private const val JUMP = 4
    private const val HAPPY = 5

    private val p = Paint().apply { isAntiAlias = false; isFilterBitmap = false; style = Paint.Style.FILL }
    private val bp = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val cp = Paint().apply { isAntiAlias = false; isFilterBitmap = false; style = Paint.Style.FILL }
    private val op = Paint().apply {
        isAntiAlias = false; isFilterBitmap = false
        colorFilter = PorterDuffColorFilter(Color.parseColor("#141826"), PorterDuff.Mode.SRC_IN)
    }
    private val tp = Paint().apply { isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val src = Rect()
    private val dst = RectF()

    private var ga = 1f
    private var kind = 0
    private var sprunki = 0
    private var appCtx: android.content.Context? = null
    fun init(c: android.content.Context) { if (appCtx == null) appCtx = c.applicationContext }
    private val sprBmps = arrayOfNulls<Bitmap>(13)
    private val sprFail = BooleanArray(13)
    private val sprPaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
    private fun sprBitmap(n: Int): Bitmap? {
        sprBmps[n]?.let { return it }
        if (sprFail[n]) return null
        val ctx = appCtx ?: return null
        return try {
            val bm = ctx.assets.open("sprunki/sprk%02d.png".format(n)).use { android.graphics.BitmapFactory.decodeStream(it) }
            if (bm == null) sprFail[n] = true else sprBmps[n] = bm
            bm
        } catch (e: Exception) { sprFail[n] = true; null }
    }

    // Sprunki 2D : danse = balancement +-8 deg autour des pieds, rebond, squash and stretch.
    private fun drawSprunki(g: Canvas, cx: Float, fy: Float, u0: Float, pose: Int, f: Boolean): Boolean {
        val bm = sprBitmap(sprunki) ?: return false
        val h = 34f * u0
        val w = h * bm.width / bm.height
        val tt = max(lastT, 0f)
        val fast = if (pose == CROSS) 2f else 1f
        val ph = tt * 2f * PI.toFloat() * 1.6f * fast
        var rot = sin(ph) * 8f
        var hop = abs(sin(ph)) * h * (if (pose == CROSS) 0.05f else 0.04f)
        val sy = 1f + sin(ph * 2f) * 0.05f
        when (pose) {
            JUMP -> hop += h * 0.18f
            HAPPY -> { hop += abs(sin(ph * 0.5f)) * h * 0.3f; rot += sin(ph * 0.5f) * 14f }
        }
        val sx = 2f - sy
        g.save()
        g.translate(cx, fy - hop)
        g.rotate(rot)
        g.scale(if (f) -sx else sx, sy)
        dst.set(-w / 2f, -h, w / 2f, 0f)
        g.drawBitmap(bm, null, dst, sprPaint)
        g.restore()
        return true
    }
    private val colors = HashMap<String, Int>()
    private fun c(h: String): Int = colors.getOrPut(h) { Color.parseColor(h) }

    // Etat des animations qui avancent par etapes (parkour, mineur)
    private var lastT = -1f
    private var lastVariant = -1
    private var pkStage = 0
    private var pkT = -100f
    private var mnN = -1
    private var mnT = 0f

    fun draw(id: String, g: Canvas, t: Float, s: SceneState): Boolean {
        if (id != "blocs") return false
        if (t < lastT || s.variant != lastVariant) {
            pkStage = 0; pkT = -100f; mnN = -1; mnT = t
        }
        lastT = t; lastVariant = s.variant
        kind = s.kind.coerceIn(0, 17)
        sprunki = s.sprunki.coerceIn(0, 12)
        ga = 1f
        g.save()
        g.clipRect(0f, 0f, 320f, 200f)
        g.scale(1f / 3f, 1f / 3f)
        when (s.variant) {
            1 -> sCourse(g, t, s)
            2 -> sWagon(g, t, s)
            3 -> sParkour(g, t, s)
            4 -> sMine(g, t, s)
            5 -> sPortail(g, t, s)
            else -> sDanse(g, t, s)
        }
        ga = 1f
        g.restore()
        return true
    }

    /* ---------- Outils ---------- */
    private fun clamp(v: Float, a: Float, b: Float): Float = if (v < a) a else if (v > b) b else v
    private fun lerp(a: Float, b: Float, k: Float): Float = a + (b - a) * k
    private fun ease(k0: Float): Float { val k = clamp(k0, 0f, 1f); return k * k * (3f - 2f * k) }
    private fun easeOut(k0: Float): Float { val k = clamp(k0, 0f, 1f); return 1f - (1f - k) * (1f - k) }
    private fun hh(a: Float, b: Float): Float {
        val n = sin(a.toDouble() * 127.1 + b.toDouble() * 311.7) * 43758.5453
        return (n - floor(n)).toFloat()
    }
    private fun rnd(v: Float): Float = floor(v + 0.5f)
    private fun walkPose(c: Float, k: Float): Int = if (floor(c * k).toInt() % 2 == 0) WALK0 else WALK1
    private fun alphaOf(col: Int): Int = (Color.alpha(col) * ga).toInt().coerceIn(0, 255)

    private fun rect(g: Canvas, x: Float, y: Float, w: Float, h: Float, col: Int) {
        val x1 = rnd(x); val y1 = rnd(y); val x2 = rnd(x + w); val y2 = rnd(y + h)
        if (x2 <= x1 || y2 <= y1) return
        p.color = col
        if (ga < 1f) p.alpha = alphaOf(col)
        g.drawRect(x1, y1, x2, y2, p)
    }

    private fun mix(a: String, b: String, k: Float): Int {
        val ca = c(a); val cb = c(b)
        return Color.rgb(
            rnd(lerp(Color.red(ca).toFloat(), Color.red(cb).toFloat(), k)).toInt(),
            rnd(lerp(Color.green(ca).toFloat(), Color.green(cb).toFloat(), k)).toInt(),
            rnd(lerp(Color.blue(ca).toFloat(), Color.blue(cb).toFloat(), k)).toInt()
        )
    }

    private fun txt(g: Canvas, s: String, x: Float, y: Float, size: Float, fill: Int, stroke: Int? = null) {
        tp.textSize = size
        val fm = tp.fontMetrics
        val by = y - (fm.ascent + fm.descent) / 2f
        if (stroke != null) {
            tp.style = Paint.Style.STROKE
            tp.strokeWidth = max(4f, size / 4f)
            tp.strokeJoin = Paint.Join.MITER
            tp.color = stroke; tp.alpha = alphaOf(stroke)
            g.drawText(s, x, by, tp)
        }
        tp.style = Paint.Style.FILL
        tp.color = fill; tp.alpha = alphaOf(fill)
        g.drawText(s, x, by, tp)
    }

    /* ---------- Blocs textures (mis en cache, 6 variantes par type) ---------- */
    private val BT = mapOf(
        "grass" to arrayOf("#8B5A2B", "#6E4421", "#A56D3B"), "dirt" to arrayOf("#8B5A2B", "#6E4421", "#A56D3B"),
        "stone" to arrayOf("#8A8F98", "#6B7078", "#A9AEB6"), "gravel" to arrayOf("#9A9590", "#76716C", "#BDB8B2"),
        "plank" to arrayOf("#B8834A", "#8E6232", "#D19D60"), "log" to arrayOf("#7A5230", "#5B3A1E", "#97693E"),
        "leaf" to arrayOf("#3F9A3A", "#2C7429", "#62C55A"), "dia" to arrayOf("#8A8F98", "#6B7078", "#A9AEB6"),
        "roof" to arrayOf("#B4432F", "#8A2F20", "#D1604A")
    )
    private val DIA = HashMap<Int, Int>().apply {
        val l = intArrayOf(1, 1, 1, 2, 1, 2, 1, 2, 2, 2, 2, 2, 5, 2, 1, 6, 2, 2, 6, 3, 2, 5, 3, 2, 2, 5, 1, 3, 5, 2, 3, 6, 2, 2, 6, 2, 6, 6, 1)
        var i = 0
        while (i < l.size) { put(l[i] + l[i + 1] * 8, l[i + 2]); i += 3 }
    }
    private val blockCache = HashMap<String, Bitmap>()
    private val bkp = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }

    private fun paintBlock(g: Canvas, s: Float, type: String, seed: Float) {
        val pp = s / 8f
        val tt = BT[type] ?: BT.getValue("stone")
        val t1 = c(tt[1]); val t2 = c(tt[2])
        bkp.color = c(tt[0]); g.drawRect(0f, 0f, s, s, bkp)
        for (j in 0 until 8) for (i in 0 until 8) {
            val h = hh(i + seed * 8.13f, j + seed * 3.71f)
            val col: Int? = if (type == "grass" && (j < 2 || (j == 2 && h < 0.55f) || (j == 3 && h < 0.13f))) {
                if (j == 0 && h < 0.45f) c("#8BE063") else if (h < 0.3f) c("#4FA83A") else c("#62C04A")
            } else if (type == "plank" || type == "roof") {
                if (j % 4 == 3) t1 else if (i == (if (j < 4) 2 else 6)) t1 else if (h > 0.86f) t2 else if (h < 0.07f) t1 else null
            } else if (type == "log") {
                if (i % 3 == 0) t1 else if (h > 0.8f) t2 else null
            } else if (type == "dia" && DIA.containsKey(i + j * 8)) {
                if (DIA[i + j * 8] == 1) c("#E0FFFF") else c("#2DD4E8")
            } else if (type == "leaf") {
                if (h < 0.22f) t1 else if (h > 0.8f) t2 else null
            } else {
                if (h < 0.17f) t1 else if (h > 0.85f) t2 else null
            }
            if (col != null) { bkp.color = col; g.drawRect(i * pp, j * pp, (i + 1) * pp, (j + 1) * pp, bkp) }
        }
        // face claire en haut / a gauche, face sombre en bas / a droite
        bkp.color = Color.argb(41, 255, 255, 255); g.drawRect(0f, 0f, s, pp, bkp); g.drawRect(0f, 0f, pp, s, bkp)
        bkp.color = Color.argb(51, 0, 0, 0); g.drawRect(0f, s - pp, s, s, bkp); g.drawRect(s - pp, 0f, s, s, bkp)
    }

    private fun block(g: Canvas, x: Float, y: Float, s: Float, type: String, seed: Float) {
        val v = floor(hh(seed, 1.37f) * 6f).toInt()
        val key = type + v
        val bm = blockCache.getOrPut(key) {
            val b = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            paintBlock(Canvas(b), 48f, type, v * 17f + 3f)
            b
        }
        val x1 = rnd(x); val y1 = rnd(y)
        dst.set(x1, y1, x1 + s, y1 + s)
        bp.alpha = (255 * ga).toInt().coerceIn(0, 255)
        g.drawBitmap(bm, null, dst, bp)
    }

    private fun spr(g: Canvas, rows: Array<String>, x: Float, y: Float, pp: Float, pal: Map<Char, String>) {
        for (r in rows.indices) for (cc in rows[r].indices) {
            val k = rows[r][cc]
            if (k != '.') rect(g, x + cc * pp, y + r * pp, pp, pp, c(pal.getValue(k)))
        }
    }

    private val TOILET = arrayOf(
        "..EEEEEEEE..", "..EwwwwwYE..", "..EwwwwwsE..", "..EEEEEEEE..", "EEEEEEEEEEEE", "EwwwwwwwwwwE",
        "EwBBBBBBBBwE", "EwwwwwwwwwsE", ".EwwwwwwwsE.", "..EwwwwwsE..", "...EwwwsE...", "..EEEEEEEE.."
    )
    private val TPAL = mapOf('E' to "#5B6478", 'w' to "#F8FAFC", 's' to "#D3DAE6", 'B' to "#7DD3FC", 'Y' to "#FACC15")
    private fun toilet(g: Canvas, x: Float, y: Float, pp: Float) = spr(g, TOILET, x, y, pp, TPAL)
    private fun twinkle(g: Canvas, x: Float, y: Float, s: Float, col: Int) {
        rect(g, x - s, y - s * 3f, s * 2f, s * 6f, col); rect(g, x - s * 3f, y - s, s * 6f, s * 2f, col)
    }

    /* ---------- Decor commun ---------- */
    private fun sky(g: Canvas, top: String, bot: String, h: Float = H) {
        val n = 20
        val bh = ceil(h / n)
        for (i in 0 until n) rect(g, 0f, i * bh, W, bh + 1f, mix(top, bot, i / (n - 1f)))
    }

    private fun sun(g: Canvas, x: Float, y: Float) {
        ga = 0.12f; rect(g, x - 36f, y - 36f, 144f, 144f, c("#FFE7A3"))
        ga = 0.2f; rect(g, x - 18f, y - 18f, 108f, 108f, c("#FFE7A3")); ga = 1f
        rect(g, x, y, 72f, 72f, c("#FFD34D")); rect(g, x + 6f, y + 6f, 60f, 60f, c("#FFE680")); rect(g, x + 18f, y + 18f, 36f, 36f, c("#FFF6C2"))
    }

    private fun bcloud(g: Canvas, x: Float, y: Float, s: Float) {
        rect(g, x, y + 18f * s, 132f * s, 24f * s, Color.WHITE); rect(g, x + 24f * s, y, 66f * s, 18f * s, Color.WHITE)
        rect(g, x, y + 36f * s, 132f * s, 6f * s, c("#DCEBFA"))
    }

    private fun clouds(g: Canvas, off: Float, top: Float = 34f) {
        for (i in 0 until 5) {
            val x = ((i * 240f + 30f - off) % 1200f + 1200f) % 1200f - 160f
            bcloud(g, x, top + (i % 3) * 34f, if (i % 2 == 1) 1f else 0.8f)
        }
    }

    private fun hills(g: Canvas, off: Float, col: String, lite: String, base: Float, seedK: Float, step: Float = 24f) {
        val i0 = floor(off / B).toInt()
        for (i in i0 until i0 + 22) {
            val x = i * B - off
            val h = max(1f, rnd(3f + 2f * sin(i * 0.42f + seedK) + hh(i.toFloat(), seedK) * 1.5f)) * step
            rect(g, x, base - h, B, h, c(col)); rect(g, x, base - h, B, 6f, c(lite))
        }
    }

    private fun ground(g: Canvas, gy: Float, off: Float, skip: ((Int) -> Boolean)? = null) {
        val i0 = floor(off / B).toInt()
        for (i in i0 until i0 + 22) {
            if (skip != null && skip(i)) continue
            var r = 0
            while (gy + r * B < H) {
                block(g, i * B - off, gy + r * B, B, if (r == 0) "grass" else if (r < 2) "dirt" else "stone", i * 13f + r * 7f + 500f)
                r++
            }
        }
    }

    private fun tree(g: Canvas, x: Float, gy: Float, sd: Float) {
        block(g, x, gy - B, B, "log", sd); block(g, x, gy - 2f * B, B, "log", sd + 1f)
        for (i in -1..1) { block(g, x + i * B, gy - 3f * B, B, "leaf", sd + 2f + i); block(g, x + i * B, gy - 4f * B, B, "leaf", sd + 6f + i) }
        block(g, x, gy - 5f * B, B, "leaf", sd + 9f)
    }

    private fun flower(g: Canvas, x: Float, gy: Float, col: String) {
        rect(g, x + 3f, gy - 18f, 6f, 18f, c("#2F7A2C")); rect(g, x - 3f, gy - 12f, 6f, 6f, c("#3F9A3A"))
        rect(g, x, gy - 30f, 12f, 12f, c(col)); rect(g, x + 3f, gy - 27f, 6f, 6f, c("#FDE68A"))
    }

    private fun bubble(g: Canvas, cx: Float, cy: Float, s: String, size: Float, sc: Float, col: String = "#DC2626") {
        g.save(); g.translate(rnd(cx), rnd(cy)); g.scale(sc, sc)
        tp.textSize = size
        val w = rnd(tp.measureText(s)) + 36f
        val h = size + 28f
        val dk = c("#1F2937")
        rect(g, -w / 2f - 6f, -h / 2f - 6f, w + 12f, h + 12f, dk); rect(g, -w / 2f, -h / 2f, w, h, Color.WHITE); rect(g, -w / 2f, h / 2f - 6f, w, 6f, c("#DCE3EE"))
        rect(g, -w / 2f + 12f, h / 2f + 6f, 18f, 12f, dk); rect(g, -w / 2f + 18f, h / 2f, 6f, 12f, Color.WHITE); rect(g, -w / 2f + 2f, h / 2f + 18f, 12f, 10f, dk)
        txt(g, s, 2f, 4f, size, c(col))
        g.restore()
    }

    /** Texte "Bravo !" qui apparait avec un petit rebond (les confettis sont dessines par ThemeView). */
    private fun bravo(g: Canvas, d: Float) {
        if (d <= 0f) return
        val s = 1f + max(0f, 0.22f - d) * 2.5f
        g.save(); g.translate(480f, 104f); g.scale(s, s)
        txt(g, "Bravo !", 6f, 6f, 64f, c("#1F2937"))
        txt(g, "Bravo !", 0f, 0f, 64f, c("#FACC15"), c("#7C2D12"))
        g.restore()
    }

    private fun dust(g: Canvas, x: Float, y: Float, k: Float) {
        if (k < 0f || k > 1f) return
        ga = 1f - k
        val col = c("#D6D3D1")
        for (i in 0 until 3) {
            rect(g, x - 34f - k * 36f - i * 12f, y - 8f - i * 6f - k * 10f, 10f, 10f, col)
            rect(g, x + 24f + k * 36f + i * 12f, y - 8f - i * 6f - k * 10f, 10f, 10f, col)
        }
        ga = 1f
    }

    private fun warm(g: Canvas) { ga = 0.07f; rect(g, 0f, 0f, W, H, c("#FFB347")); ga = 1f }
    private fun shadow(g: Canvas, cx: Float, gy: Float, w: Float) = rect(g, cx - w / 2f, gy - 4f, w, 8f, Color.argb(46, 0, 0, 0))

    /* ---------- Personnages : 16 palettes (peau, peauD, haut, hautD, bas, basD, chaussure) ---------- */
    private val PAL = arrayOf(
        arrayOf("#F2B98F", "#D9946B", "#1FB5B5", "#14898A", "#3D52D5", "#2B3AA6", "#E5E7EB"),
        arrayOf("#6BC45A", "#4A9A3C", "#5BB84A", "#3F8A33", "#4A9A3C", "#3F8A33", "#4A9A3C"),
        arrayOf("#F8FAFC", "#CBD5E1", "#F8FAFC", "#CBD5E1", "#1F2937", "#111827", "#1F2937"),
        arrayOf("#C9A27A", "#A57F56", "#C9A27A", "#A57F56", "#A57F56", "#8A6743", "#A57F56"),
        arrayOf("#F9A8C0", "#E77FA0", "#F9A8C0", "#E77FA0", "#F48FB1", "#D9739A", "#F48FB1"),
        arrayOf("#F8FAFC", "#CBD5E1", "#F8FAFC", "#CBD5E1", "#6B7280", "#4B5563", "#6B7280"),
        arrayOf("#F8FAFC", "#CBD5E1", "#F8FAFC", "#CBD5E1", "#374151", "#1F2937", "#374151"),
        arrayOf("#FDE047", "#EAB308", "#FDE047", "#EAB308", "#F97316", "#C2410C", "#F97316"),
        arrayOf("#F97316", "#C2410C", "#F97316", "#C2410C", "#9A3412", "#7C2D12", "#9A3412"),
        arrayOf("#FBCFE8", "#F472B6", "#FBCFE8", "#F9A8D4", "#F9A8D4", "#F472B6", "#F9A8D4"),
        arrayOf("#FACC15", "#CA8A04", "#FACC15", "#CA8A04", "#1F2937", "#111827", "#1F2937"),
        arrayOf("#4ADE80", "#16A34A", "#4ADE80", "#16A34A", "#22C55E", "#15803D", "#22C55E"),
        arrayOf("#9CA3AF", "#6B7280", "#9CA3AF", "#6B7280", "#6B7280", "#4B5563", "#6B7280"),
        arrayOf("#86EFAC", "#4ADE80", "#86EFAC", "#4ADE80", "#4ADE80", "#22C55E", "#4ADE80"),
        arrayOf("#D6A77A", "#B07F54", "#8B5A2B", "#6E4421", "#8B5A2B", "#6E4421", "#8B5A2B"),
        arrayOf("#CBD5E1", "#94A3B8", "#94A3B8", "#64748B", "#64748B", "#475569", "#64748B"),
        arrayOf(JJ_SKIN, JJ_SKIN_D, JJ_HOODIE, JJ_HOODIE_D, JJ_PANTS, JJ_PANTS_D, JJ_SHOE),
        arrayOf(MK_SKIN, MK_SKIN_D, MK_HOODIE, MK_HOODIE_D, MK_PANTS, MK_PANTS_D, MK_SHOE)
    )

    // Tampon du personnage (contour sombre = silhouette decalee autour)
    private val charBuf: Bitmap by lazy { Bitmap.createBitmap(220, 330, Bitmap.Config.ARGB_8888) }
    private val charCanvas: Canvas by lazy { Canvas(charBuf) }
    private var cu = 1f

    private fun R(a: Float, b: Float, w: Float, h: Float, col: Int) {
        cp.color = col
        val x1 = rnd(a * cu); val y1 = rnd(b * cu); val x2 = rnd((a + w) * cu); val y2 = rnd((b + h) * cu)
        if (x2 > x1 && y2 > y1) charCanvas.drawRect(x1, y1, x2, y2, cp)
    }

    // yaw : NaN = automatique (3/4 face, tourne vers la marche) ; swing : coup de pioche 0..1 (0 = aucun).
    private fun hero(g: Canvas, cx: Float, fy: Float, u0: Float, pose: Int, f: Boolean = false, yaw: Float = Float.NaN, swing: Float = 0f) {
        if (sprunki > 0 && drawSprunki(g, cx, fy, u0, pose, f)) return
        if (Player3D.draw(g, kind, cx, fy, u0, pose, f, yaw, swing, lastT)) return
        // Secours 2D (skin introuvable)
        val u = max(1f, rnd(u0))
        val ox = 5f * u; val oy = 6f * u
        val x0 = rnd(cx - 7f * u) - ox; val y0 = rnd(fy - 31.5f * u) - oy
        val wq = min(220, (26f * u).toInt()); val hq = min(330, (39f * u).toInt())
        charBuf.eraseColor(Color.TRANSPARENT)
        cu = u
        drawChar(kind, 5f, 6f, pose, f)
        val o = max(1f, rnd(u / 3f))
        src.set(0, 0, wq, hq)
        dst.set(x0 - o, y0, x0 - o + wq, y0 + hq); g.drawBitmap(charBuf, src, dst, op)
        dst.set(x0 + o, y0, x0 + o + wq, y0 + hq); g.drawBitmap(charBuf, src, dst, op)
        dst.set(x0, y0 - o, x0 + wq, y0 - o + hq); g.drawBitmap(charBuf, src, dst, op)
        dst.set(x0, y0 + o, x0 + wq, y0 + o + hq); g.drawBitmap(charBuf, src, dst, op)
        bp.alpha = 255
        dst.set(x0, y0, x0 + wq, y0 + hq); g.drawBitmap(charBuf, src, dst, bp)
    }

    // Poses : STAND, CROSS (jambes croisees, envie), WALK0 / WALK1, JUMP, HAPPY (bras leves + grand sourire).
    private fun drawChar(k: Int, x: Float, y: Float, pose: Int, f: Boolean) {
        val pl = PAL[k]
        val skin = c(pl[0]); val skinD = c(pl[1]); val shirt = c(pl[2]); val shirtD = c(pl[3])
        val pants = c(pl[4]); val pantsD = c(pl[5]); val shoe = c(pl[6]); val dark = c("#1F2937")
        val hair = c("#5B3A1E"); val hairL = c("#7A5230")
        val s = if (f) 1f else 0f
        val arm = { a: Float, b: Float, w: Float, h: Float ->
            if (k == 16) { for (i in 0 until 7) R(a, b + h * i / 7f, w, h / 7f + 0.05f, c(JJ_RAINBOW[i])) }
            else R(a, b, w, h, if (k == 17) c(MK_SLEEVE) else shirt)
        }
        val sh = { a: Float, b: Float, w: Float, h: Float, i: Int ->
            if (k == 16) R(a, b, w, h, c(if (i == 0) JJ_SHOE else JJ_SHOE2))
            else if (k == 17) {
                R(a, b, w, h, Color.WHITE)
                for (cx in 0 until w.toInt()) for (cy in 0 until h.toInt()) if ((cx + cy + i) % 2 == 0) R(a + cx, b + cy, 1f, 1f, c(MK_BLACK))
            } else R(a, b, w, h, shoe)
        }
        val walk = pose == WALK0 || pose == WALK1
        val wd = if (pose == WALK0) 1 else -1
        // jambes
        if (pose == CROSS) {
            R(x + 7f, y + 22f, 4f, 3f, pantsD); R(x + 5.5f, y + 25f, 4f, 2.5f, pantsD); R(x + 4f, y + 27.5f, 4f, 2.5f, pantsD)
            R(x + 3f, y + 22f, 4f, 3f, pants); R(x + 4.5f, y + 25f, 4f, 2.5f, pants); R(x + 6f, y + 27.5f, 4f, 2.5f, pants)
            sh(x + 3f, y + 29.5f, 5f, 2f, 0); sh(x + 6f, y + 29.5f, 5f, 2f, 1)
        } else if (walk) {
            val la = if (wd > 0) 8f else 6.5f
            val lb = if (wd > 0) 6.5f else 8f
            R(x + 2f, y + 22f, 4f, la, pants); R(x + 2f, y + 22f, 1f, la, pantsD); R(x + 8f, y + 22f, 4f, lb, pants)
            sh(x + 1.5f, y + 21.5f + la, 5f, 2f, 0); sh(x + 7.5f, y + 21.5f + lb, 5f, 2f, 1)
        } else if (pose == JUMP) {
            R(x + 1.5f, y + 22f, 4f, 5.5f, pants); R(x + 1.5f, y + 22f, 1f, 5.5f, pantsD); R(x + 8.5f, y + 22f, 4f, 5.5f, pants)
            sh(x + 1f, y + 27f, 5f, 2f, 0); sh(x + 8f, y + 27f, 5f, 2f, 1)
        } else {
            val o = if (pose == HAPPY) 1f else 0f
            R(x + 3f - o, y + 22f, 4f, 8f, pants); R(x + 3f - o, y + 22f, 1f, 8f, pantsD); R(x + 7f + o, y + 22f, 4f, 8f, pants)
            sh(x + 2.5f - o, y + 29.5f, 5f, 2f, 0); sh(x + 6.5f + o, y + 29.5f, 5f, 2f, 1)
        }
        // corps
        R(x + 2f, y + 14f, 10f, 9f, shirt); R(x + 2f, y + 21f, 10f, 2f, shirtD)
        if (k == 16) R(x + 5f, y + 14f, 4f, 1.5f, skin)
        if (k == 17) { R(x + 3.5f, y + 14f, 7f, 1.5f, c(MK_RED)); R(x + 5f, y + 16f, 4f, 1f, c(MK_GOLD)); R(x + 6.5f, y + 16f, 1.5f, 4.5f, c(MK_GOLD)); R(x + 5f, y + 19f, 3f, 1.5f, c(MK_GOLD)) }
        // bras
        if (pose == CROSS) {
            arm(x - 1f + s * 0.5f, y + 14f, 3f, 6f); arm(x + 12f - s * 0.5f, y + 14f, 3f, 6f)
            arm(x + 1f, y + 18f, 3f, 3f); arm(x + 10f, y + 18f, 3f, 3f)
            R(x + 4f, y + 18.5f, 6f, 3f, skin); R(x + 4f, y + 20.5f, 6f, 0.6f, skinD)
        } else if (pose == JUMP || pose == HAPPY) {
            arm(x - 2.5f, y + 7f, 3f, 9f); arm(x + 13.5f, y + 7f, 3f, 9f)
            R(x - 2.5f, y + 5f, 3f, 2.5f, skin); R(x + 13.5f, y + 5f, 3f, 2.5f, skin)
        } else if (walk) {
            arm(x - 1f, y + 14f, 3f, if (wd > 0) 6f else 8f); R(x - 1f, y + (if (wd > 0) 20f else 22f), 3f, 2f, skin)
            arm(x + 12f, y + 14f, 3f, if (wd > 0) 8f else 6f); R(x + 12f, y + (if (wd > 0) 22f else 20f), 3f, 2f, skin)
        } else {
            arm(x - 1f, y + 14f, 3f, 8f); arm(x + 12f, y + 14f, 3f, 8f)
            R(x - 1f, y + 22f, 3f, 2f, skin); R(x + 12f, y + 22f, 3f, 2f, skin)
        }
        // tete
        R(x, y, 14f, 14f, skin); R(x, y + 12.5f, 14f, 1.5f, skinD)
        when (k) {
            0 -> {
                R(x - 0.5f, y - 0.5f, 15f, 5f, hair)
                R(x + 3f, y + 4.5f, 3f, 1.5f, hair); R(x + 9f, y + 4.5f, 2f, 1.5f, hair)
                R(x - 0.5f, y + 4f, 2f, 5f, hair); R(x + 12.5f, y + 4f, 2f, 5f, hair)
                R(x + 3f, y, 5f, 1f, hairL)
                R(x + 2f, y + 6f, 4f, 5f, Color.WHITE); R(x + 8f, y + 6f, 4f, 5f, Color.WHITE)
                R(x + 3.5f, y + 7.5f, 2.5f, 3f, c("#2563EB")); R(x + 9.5f, y + 7.5f, 2.5f, 3f, c("#2563EB"))
                R(x + 4.2f, y + 8.2f, 1.3f, 1.8f, dark); R(x + 10.2f, y + 8.2f, 1.3f, 1.8f, dark)
                R(x + 3.5f, y + 7.5f, 1f, 1f, Color.WHITE); R(x + 9.5f, y + 7.5f, 1f, 1f, Color.WHITE)
                R(x + 2f, y + 5f, 4f, 0.8f, hair); R(x + 8f, y + 5f, 4f, 0.8f, hair)
                R(x + 1f, y + 10.5f, 2.5f, 1.5f, c("#FF8FA3")); R(x + 10.5f, y + 10.5f, 2.5f, 1.5f, c("#FF8FA3"))
                R(x + 5.5f, y + 11f, 3f, 1.8f, c("#B4232C"))
            }
            1 -> {
                R(x + 1f, y + 1f, 3f, 3f, skinD); R(x + 9f, y + 2f, 3f, 3f, skinD); R(x + 5f, y + 11f, 4f, 2f, skinD)
                R(x + 2f, y + 5f, 4f, 4f, dark); R(x + 8f, y + 5f, 4f, 4f, dark)
                R(x + 3f, y + 5.8f, 1.3f, 1.3f, Color.WHITE); R(x + 9f, y + 5.8f, 1.3f, 1.3f, Color.WHITE)
                R(x + 5f, y + 9f, 4f, 1.5f, dark); R(x + 4f, y + 10.5f, 2f, 2.5f, dark); R(x + 8f, y + 10.5f, 2f, 2.5f, dark)
            }
            2 -> {
                R(x - 1f, y - 2f, 4f, 4f, dark); R(x + 11f, y - 2f, 4f, 4f, dark)
                R(x + 1.5f, y + 5f, 5f, 6f, dark); R(x + 7.5f, y + 5f, 5f, 6f, dark)
                R(x + 3f, y + 6.5f, 2.5f, 3f, Color.WHITE); R(x + 8.5f, y + 6.5f, 2.5f, 3f, Color.WHITE)
                R(x + 3.8f, y + 7.5f, 1.2f, 1.6f, dark); R(x + 9.3f, y + 7.5f, 1.2f, 1.6f, dark)
                R(x + 5.5f, y + 10.5f, 3f, 1.8f, dark)
                R(x + 1f, y + 11f, 2.2f, 1.4f, c("#FF8FA3")); R(x + 10.8f, y + 11f, 2.2f, 1.4f, c("#FF8FA3"))
            }
            3 -> {
                R(x - 2.5f, y + 1f, 3f, 8f, c("#7A5230")); R(x + 13.5f, y + 1f, 3f, 8f, c("#7A5230"))
                R(x + 2f, y + 6f, 4f, 5f, Color.WHITE); R(x + 8f, y + 6f, 4f, 5f, Color.WHITE)
                R(x + 3.5f, y + 7.5f, 2.5f, 3f, dark); R(x + 9.5f, y + 7.5f, 2.5f, 3f, dark)
                R(x + 3.5f, y + 7.5f, 1f, 1f, Color.WHITE); R(x + 9.5f, y + 7.5f, 1f, 1f, Color.WHITE)
                R(x + 4f, y + 9.5f, 6f, 4f, c("#E8D2B5")); R(x + 5.8f, y + 9.8f, 2.4f, 1.6f, dark)
                R(x + 6.5f, y + 12f, 1f, 1.2f, c("#B4232C"))
                R(x + 14f, y + 15f, 4f, 2.4f, c("#A57F56")); R(x + 17f, y + 13f, 2f, 3f, c("#A57F56"))
            }
            4 -> {
                R(x + 0.5f, y - 1.5f, 3f, 2.5f, skinD); R(x + 10.5f, y - 1.5f, 3f, 2.5f, skinD); R(x + 2.5f, y + 5f, 2.5f, 2.5f, dark); R(x + 9f, y + 5f, 2.5f, 2.5f, dark)
                R(x + 2.8f, y + 5.2f, 1f, 1f, Color.WHITE); R(x + 9.3f, y + 5.2f, 1f, 1f, Color.WHITE); R(x + 4f, y + 7.5f, 6f, 4f, c("#F06292")); R(x + 5f, y + 9f, 1.2f, 1.6f, c("#9D174D"))
                R(x + 7.8f, y + 9f, 1.2f, 1.6f, c("#9D174D")); R(x + 1f, y + 9.5f, 2f, 1.4f, c("#FF8FA3")); R(x + 11f, y + 9.5f, 2f, 1.4f, c("#FF8FA3")); R(x + 6f, y + 12f, 2f, 0.8f, dark)
            }
            5 -> {
                R(x - 1f, y - 1f, 4f, 3f, Color.WHITE); R(x + 3.5f, y - 1.5f, 5f, 2.5f, Color.WHITE); R(x + 9f, y - 1f, 5f, 3f, Color.WHITE); R(x - 1f, y + 2.5f, 2f, 5f, Color.WHITE)
                R(x + 13f, y + 2.5f, 2f, 5f, Color.WHITE); R(x + 2f, y + 3f, 10f, 10f, c("#E8D5C4")); R(x - 2f, y + 5f, 2.5f, 2f, c("#E8D5C4")); R(x + 13.5f, y + 5f, 2.5f, 2f, c("#E8D5C4"))
                R(x + 3.5f, y + 5.5f, 2.2f, 2.5f, dark); R(x + 8.3f, y + 5.5f, 2.2f, 2.5f, dark); R(x + 3.7f, y + 5.7f, 0.9f, 0.9f, Color.WHITE); R(x + 8.5f, y + 5.7f, 0.9f, 0.9f, Color.WHITE)
                R(x + 6f, y + 9f, 2f, 1.2f, c("#B07A5A")); R(x + 6.5f, y + 10.6f, 1f, 1f, c("#B07A5A")); R(x + 2.5f, y + 9f, 1.8f, 1.2f, c("#FF8FA3")); R(x + 9.7f, y + 9f, 1.8f, 1.2f, c("#FF8FA3"))
                R(x + 1f, y + 14f, 3f, 2f, Color.WHITE); R(x + 10f, y + 14f, 3f, 2f, Color.WHITE)
            }
            6 -> {
                R(x + 1f, y - 2f, 2f, 2f, c("#F1E3C4")); R(x + 11f, y - 2f, 2f, 2f, c("#F1E3C4")); R(x - 2f, y + 3f, 2.5f, 2f, dark); R(x + 13.5f, y + 3f, 2.5f, 2f, dark)
                R(x, y, 4f, 3f, dark); R(x + 10f, y + 1f, 4f, 3.5f, dark); R(x + 12f, y + 5f, 2f, 2f, dark); R(x + 3f, y + 5f, 2.5f, 3f, dark)
                R(x + 8.5f, y + 5f, 2.5f, 3f, dark); R(x + 3.3f, y + 5.3f, 1f, 1f, Color.WHITE); R(x + 8.8f, y + 5.3f, 1f, 1f, Color.WHITE); R(x + 3f, y + 9f, 8f, 4f, c("#F9A8C0"))
                R(x + 4.5f, y + 10.3f, 1.3f, 1.3f, c("#BE185D")); R(x + 8.2f, y + 10.3f, 1.3f, 1.3f, c("#BE185D")); R(x + 3f, y + 16f, 3f, 3f, dark); R(x + 8f, y + 18f, 3f, 2f, dark)
            }
            7 -> {
                R(x + 6f, y - 2f, 2f, 2f, skin); R(x + 5f, y - 1.2f, 1f, 1.2f, skin); R(x + 8f, y - 1.2f, 1f, 1.2f, skin); R(x + 3f, y + 5f, 2.5f, 3f, dark)
                R(x + 8.5f, y + 5f, 2.5f, 3f, dark); R(x + 3.3f, y + 5.3f, 1f, 1f, Color.WHITE); R(x + 8.8f, y + 5.3f, 1f, 1f, Color.WHITE); R(x + 5f, y + 8.5f, 4f, 2f, c("#F97316"))
                R(x + 5.5f, y + 10.5f, 3f, 1f, c("#EA580C")); R(x + 1f, y + 9f, 2f, 1.4f, c("#FF8FA3")); R(x + 11f, y + 9f, 2f, 1.4f, c("#FF8FA3")); R(x - 1f, y + 17f, 3f, 4f, skinD)
                R(x + 12f, y + 17f, 3f, 4f, skinD)
            }
            8 -> {
                R(x, y - 3f, 3.5f, 3f, skin); R(x + 0.5f, y - 4f, 2f, 1f, skin); R(x + 1f, y - 2.5f, 1.5f, 2f, dark); R(x + 10.5f, y - 3f, 3.5f, 3f, skin)
                R(x + 11.5f, y - 4f, 2f, 1f, skin); R(x + 11.5f, y - 2.5f, 1.5f, 2f, dark); R(x, y + 8f, 14f, 4.5f, c("#FFF7ED")); R(x, y + 7f, 3f, 1f, c("#FFF7ED"))
                R(x + 11f, y + 7f, 3f, 1f, c("#FFF7ED")); R(x + 3f, y + 5f, 2.5f, 2.5f, dark); R(x + 8.5f, y + 5f, 2.5f, 2.5f, dark); R(x + 3.3f, y + 5.2f, 0.9f, 0.9f, Color.WHITE)
                R(x + 8.8f, y + 5.2f, 0.9f, 0.9f, Color.WHITE); R(x + 6f, y + 8f, 2f, 1.5f, dark); R(x + 6.5f, y + 10f, 1f, 1f, c("#B4232C")); R(x + 14f, y + 15f, 3f, 3f, skin)
                R(x + 16.5f, y + 12f, 2.5f, 4f, skin); R(x + 16.5f, y + 11f, 2.5f, 1.5f, c("#FFF7ED"))
            }
            9 -> {
                R(x - 3f, y + 2f, 3f, 1.5f, c("#EC4899")); R(x - 3.5f, y + 5f, 3.5f, 1.5f, c("#EC4899")); R(x - 3f, y + 8f, 3f, 1.5f, c("#EC4899")); R(x + 14f, y + 2f, 3f, 1.5f, c("#EC4899"))
                R(x + 14f, y + 5f, 3.5f, 1.5f, c("#EC4899")); R(x + 14f, y + 8f, 3f, 1.5f, c("#EC4899")); R(x + 2.5f, y + 5.5f, 2.2f, 2.2f, dark); R(x + 9.3f, y + 5.5f, 2.2f, 2.2f, dark)
                R(x + 2.7f, y + 5.7f, 0.8f, 0.8f, Color.WHITE); R(x + 9.5f, y + 5.7f, 0.8f, 0.8f, Color.WHITE); R(x + 4f, y + 10f, 6f, 0.8f, c("#BE185D")); R(x + 3.5f, y + 9.3f, 1f, 1f, c("#BE185D"))
                R(x + 9.5f, y + 9.3f, 1f, 1f, c("#BE185D")); R(x + 1f, y + 8.5f, 2f, 1.4f, c("#FF8FA3")); R(x + 11f, y + 8.5f, 2f, 1.4f, c("#FF8FA3")); R(x + 5f, y + 16f, 4f, 4f, c("#F9A8D4"))
            }
            10 -> {
                R(x + 3f, y - 3f, 1f, 3f, dark); R(x + 2.5f, y - 3.8f, 2f, 1.2f, dark); R(x + 10f, y - 3f, 1f, 3f, dark); R(x + 9.5f, y - 3.8f, 2f, 1.2f, dark)
                R(x + 2f, y + 5f, 3f, 3.5f, dark); R(x + 9f, y + 5f, 3f, 3.5f, dark); R(x + 2.3f, y + 5.3f, 1f, 1f, Color.WHITE); R(x + 9.3f, y + 5.3f, 1f, 1f, Color.WHITE)
                R(x + 1f, y + 9.5f, 2f, 1.4f, c("#FF8FA3")); R(x + 11f, y + 9.5f, 2f, 1.4f, c("#FF8FA3")); R(x + 6f, y + 11f, 2f, 1f, c("#B4232C")); R(x + 2f, y + 16f, 10f, 1.5f, dark)
                R(x + 2f, y + 19f, 10f, 1.5f, dark); R(x - 3f, y + 11f, 3.5f, 3f, c("#E0F2FE")); R(x + 13.5f, y + 11f, 3.5f, 3f, c("#E0F2FE")); R(x - 3f, y + 11f, 3.5f, 0.8f, c("#BAE6FD"))
                R(x + 13.5f, y + 11f, 3.5f, 0.8f, c("#BAE6FD"))
            }
            11 -> {
                R(x + 0.5f, y - 3f, 5f, 5f, skin); R(x + 8.5f, y - 3f, 5f, 5f, skin); R(x + 1.5f, y - 2f, 3f, 3.5f, Color.WHITE); R(x + 9.5f, y - 2f, 3f, 3.5f, Color.WHITE)
                R(x + 2.5f, y - 1f, 2f, 2f, dark); R(x + 10.5f, y - 1f, 2f, 2f, dark); R(x + 2.7f, y - 0.8f, 0.7f, 0.7f, Color.WHITE); R(x + 10.7f, y - 0.8f, 0.7f, 0.7f, Color.WHITE)
                R(x + 5.5f, y + 5f, 1f, 1f, skinD); R(x + 7.5f, y + 5f, 1f, 1f, skinD); R(x + 2f, y + 9f, 10f, 0.9f, c("#166534")); R(x + 1.5f, y + 8f, 1f, 1f, c("#166534"))
                R(x + 11.5f, y + 8f, 1f, 1f, c("#166534")); R(x + 1f, y + 10f, 2f, 1.4f, c("#FF8FA3")); R(x + 11f, y + 10f, 2f, 1.4f, c("#FF8FA3")); R(x + 4f, y + 15f, 6f, 5f, c("#BBF7D0"))
            }
            12 -> {
                R(x, y - 2.5f, 3.5f, 2.5f, skin); R(x + 0.5f, y - 3.5f, 2f, 1f, skin); R(x + 1f, y - 2f, 1.5f, 1.5f, c("#F9A8D4")); R(x + 10.5f, y - 2.5f, 3.5f, 2.5f, skin)
                R(x + 11.5f, y - 3.5f, 2f, 1f, skin); R(x + 11.5f, y - 2f, 1.5f, 1.5f, c("#F9A8D4")); R(x + 5f, y, 1f, 2.5f, skinD); R(x + 8f, y, 1f, 2.5f, skinD)
                R(x + 6.5f, y, 1f, 1.5f, skinD); R(x + 2.5f, y + 5.5f, 3f, 3f, c("#84CC16")); R(x + 8.5f, y + 5.5f, 3f, 3f, c("#84CC16")); R(x + 3.5f, y + 5.5f, 1f, 3f, dark)
                R(x + 9.5f, y + 5.5f, 1f, 3f, dark); R(x + 6f, y + 9f, 2f, 1.2f, c("#F472B6")); R(x + 6.5f, y + 10.2f, 1f, 1f, dark); R(x - 2f, y + 9.5f, 4f, 0.5f, dark)
                R(x - 2f, y + 11f, 4f, 0.5f, dark); R(x + 12f, y + 9.5f, 4f, 0.5f, dark); R(x + 12f, y + 11f, 4f, 0.5f, dark); R(x + 14f, y + 17f, 3f, 2f, skin)
                R(x + 16f, y + 13f, 2f, 5f, skin); R(x + 16f, y + 13f, 2f, 1.5f, skinD)
            }
            13 -> {
                R(x + 2f, y + 2f, 10f, 10f, c("#4ADE80")); R(x + 1f, y + 1f, 3f, 1f, Color.WHITE); R(x + 1f, y + 2f, 1f, 2f, Color.WHITE); R(x + 11f, y + 10f, 1.5f, 1.5f, c("#BBF7D0"))
                R(x + 3.5f, y + 5f, 2.5f, 2.5f, dark); R(x + 8f, y + 5f, 2.5f, 2.5f, dark); R(x + 3.7f, y + 5.2f, 0.9f, 0.9f, Color.WHITE); R(x + 8.2f, y + 5.2f, 0.9f, 0.9f, Color.WHITE)
                R(x + 6f, y + 9.5f, 2f, 1f, dark); R(x + 2f, y + 8.5f, 1.8f, 1.2f, c("#FF8FA3")); R(x + 10.2f, y + 8.5f, 1.8f, 1.2f, c("#FF8FA3")); R(x + 2f, y + 13.5f, 1.5f, 1.5f, skinD)
                R(x + 10f, y + 13.5f, 1.5f, 2f, skinD)
            }
            14 -> {
                R(x, y - 0.5f, 14f, 3f, c("#5B3A1E")); R(x, y + 2f, 1.5f, 2.5f, c("#5B3A1E")); R(x + 12.5f, y + 2f, 1.5f, 2.5f, c("#5B3A1E")); R(x + 2f, y + 5f, 4f, 3f, Color.WHITE)
                R(x + 8f, y + 5f, 4f, 3f, Color.WHITE); R(x + 3.5f, y + 5.5f, 2f, 2f, c("#16A34A")); R(x + 8.5f, y + 5.5f, 2f, 2f, c("#16A34A")); R(x + 2f, y + 4f, 3.5f, 0.8f, c("#5B3A1E"))
                R(x + 8.5f, y + 4f, 3.5f, 0.8f, c("#5B3A1E")); R(x + 5.5f, y + 7f, 3f, 5f, skinD); R(x + 5.5f, y + 7f, 3f, 1f, c("#C4916A")); R(x + 1f, y + 9.5f, 2f, 1.4f, c("#FF8FA3"))
                R(x + 11f, y + 9.5f, 2f, 1.4f, c("#FF8FA3")); R(x + 2f, y + 18f, 10f, 1f, c("#FACC15"))
            }
            16 -> {
                val hc = c(JJ_HAIR); val hl = c(JJ_HAIR_L)
                R(x - 0.5f, y - 1f, 15f, 4.5f, hc); R(x - 0.5f, y + 2f, 2f, 5f, hc); R(x + 12.5f, y + 2f, 2f, 5f, hc)
                R(x + 2f, y + 3f, 4f, 1.6f, hc); R(x + 8f, y + 3f, 4f, 1.6f, hc); R(x + 6f, y + 3f, 2f, 0.8f, hc); R(x + 3f, y - 1f, 6f, 1f, hl)
                R(x + 2f, y + 5f, 4.5f, 4.5f, Color.WHITE); R(x + 7.5f, y + 5f, 4.5f, 4.5f, Color.WHITE)
                R(x + 3f, y + 5.5f, 3f, 4f, c(JJ_EYE)); R(x + 8.5f, y + 5.5f, 3f, 4f, c(JJ_EYE))
                R(x + 4f, y + 7f, 1.6f, 2f, c(JJ_PUPIL)); R(x + 9.5f, y + 7f, 1.6f, 2f, c(JJ_PUPIL))
                R(x + 3f, y + 5.5f, 1.2f, 1.2f, Color.WHITE); R(x + 8.5f, y + 5.5f, 1.2f, 1.2f, Color.WHITE)
                R(x + 1f, y + 9.5f, 2f, 1.4f, c("#FF9A9A")); R(x + 11f, y + 9.5f, 2f, 1.4f, c("#FF9A9A"))
                R(x + 5.5f, y + 11f, 3f, 1f, c("#C25A5A"))
            }
            17 -> {
                R(x - 0.5f, y - 1.5f, 15f, 4f, c(MK_BLACK)); R(x - 0.5f, y + 2.5f, 15f, 1.8f, c(MK_RED))
                R(x + 5f, y - 2.5f, 4f, 1.5f, c(MK_GOLD)); R(x + 6f, y - 3.5f, 2f, 1f, c(MK_GOLD))
                R(x + 2.5f, y + 5.5f, 2f, 3f, c(MK_BLACK)); R(x + 9.5f, y + 5.5f, 2f, 3f, c(MK_BLACK))
                R(x + 3f, y + 9.3f, 8f, 3f, c(MK_BLACK)); R(x + 4f, y + 9.8f, 6f, 2.4f, c(MK_RED)); R(x + 5f, y + 9.3f, 1.8f, 1.5f, Color.WHITE)
            }
            15 -> {
                R(x + 6.5f, y - 3f, 1f, 3f, c("#64748B")); R(x + 6f, y - 4f, 2f, 1.5f, c("#F87171")); R(x - 1f, y + 6f, 1.5f, 2f, c("#64748B")); R(x + 13.5f, y + 6f, 1.5f, 2f, c("#64748B"))
                R(x + 1.5f, y + 4.5f, 11f, 4.5f, c("#1E293B")); R(x + 3f, y + 5.5f, 3f, 2.5f, c("#22D3EE")); R(x + 8f, y + 5.5f, 3f, 2.5f, c("#22D3EE")); R(x + 3f, y + 5.5f, 1f, 1f, Color.WHITE)
                R(x + 8f, y + 5.5f, 1f, 1f, Color.WHITE); R(x + 4.5f, y + 10.5f, 5f, 1.5f, c("#64748B")); R(x + 5.5f, y + 10.5f, 0.6f, 1.5f, skin); R(x + 7f, y + 10.5f, 0.6f, 1.5f, skin)
                R(x + 8.5f, y + 10.5f, 0.6f, 1.5f, skin); R(x + 1f, y + 10f, 2f, 1.2f, c("#FF8FA3")); R(x + 11f, y + 10f, 2f, 1.2f, c("#FF8FA3")); R(x + 6f, y + 16f, 2f, 2f, c("#22D3EE"))
            }
        }
        if (pose == HAPPY) {
            R(x + 4f, y + 10.2f, 6f, 3f, c("#B4232C")); R(x + 4.6f, y + 10.2f, 4.8f, 1.1f, Color.WHITE)
        } else if (pose == CROSS) {
            R(x + 15.5f, y + 4f, 1.5f, 3f, c("#38BDF8")); R(x + 15.8f, y + 3f, 0.9f, 1f, c("#38BDF8"))
        }
    }

    /* ---------- Cabane WC en blocs ---------- */
    private fun cabin(g: Canvas, x: Float, gy: Float, open: Float) {
        for (j in 0 until 3) for (i in 0 until 3) if (!(i == 1 && j < 2)) block(g, x + i * B, gy - (j + 1) * B, B, "plank", 40f + i + j * 3)
        for (r in 0 until 4) block(g, x - 24f + r * B, gy - 4f * B, B, "roof", 60f + r)
        block(g, x + 24f, gy - 5f * B, B, "roof", 70f); block(g, x + 72f, gy - 5f * B, B, "roof", 71f)
        rect(g, x - 24f, gy - 4f * B + 42f, 192f, 6f, Color.argb(64, 0, 0, 0))
        rect(g, x + 48f, gy - 96f, 48f, 96f, c("#2A1E14")); rect(g, x + 48f, gy - 96f, 6f, 96f, c("#1A120C"))
        if (open > 0.3f) toilet(g, x + 54f, gy - 36f, 3f)
        val dw = rnd(48f * (1f - open * 0.85f))
        if (dw > 0f) {
            rect(g, x + 48f, gy - 96f, dw, 96f, c("#9A6A36"))
            for (k in 1 until 4) rect(g, x + 48f, gy - 96f + k * 24f, dw, 4f, c("#6E4421"))
            rect(g, x + 48f + dw - 4f, gy - 96f, 4f, 96f, c("#6E4421"))
            if (dw > 30f) rect(g, x + 48f + dw - 14f, gy - 52f, 6f, 6f, c("#FACC15"))
        }
        rect(g, x + 40f, gy - 140f, 64f, 36f, c("#0F172A")); rect(g, x + 44f, gy - 136f, 56f, 28f, c("#2563EB")); rect(g, x + 44f, gy - 136f, 56f, 4f, c("#60A5FA"))
        txt(g, "WC", x + 73f, gy - 120f, 16f, Color.WHITE)
    }

    // Arrivee a la cabane : marche, entre, porte fermee, « Ouf ! », ressort content + Bravo.
    private fun cabinSeq(g: Canvas, tc: Float, tA: Float, cabX: Float, gy: Float, u: Float, fromX: Float): Boolean {
        val d = tc - tA
        val door = cabX + 72f
        if (d < 0f) { cabin(g, cabX, gy, 0f); return false }
        if (d < 0.8f) {
            cabin(g, cabX, gy, clamp((d - 0.35f) / 0.3f, 0f, 1f))
            hero(g, lerp(fromX, door, ease(d / 0.8f)), gy - abs(sin(tc * 14f)) * 4f, u, walkPose(tc, 8f))
            return true
        }
        if (d < 1.15f) { cabin(g, cabX, gy, 1f - clamp((d - 0.8f) / 0.3f, 0f, 1f)); return true }
        if (d < 1.9f) { cabin(g, cabX, gy, 0f); bubble(g, door, gy - 300f, "Ouf !", 30f, 1f + sin(d * 12f) * 0.03f, "#1D4ED8"); return true }
        cabin(g, cabX, gy, clamp((d - 1.9f) / 0.25f, 0f, 1f))
        hero(g, door - 8f, gy - abs(sin((d - 1.9f) * 6f)) * 12f, u, HAPPY)
        bravo(g, d - 1.95f)
        return true
    }

    /* 0. La danse du pipi (boucle sur t, puis course a la toilette pendant la fete) */
    private fun sDanse(g: Canvas, t: Float, st: SceneState) {
        val tc = if (st.celebrate) 6.6f + st.celebrateT else t % 6.6f
        sky(g, "#5FB4F5", "#CDEBFF"); sun(g, 820f, 40f); clouds(g, t * 12f)
        hills(g, 0f, "#8FD08A", "#A9E0A2", 456f, 1f)
        ground(g, 456f, 0f)
        flower(g, 108f, 456f, "#F87171"); flower(g, 540f, 456f, "#FACC15"); flower(g, 600f, 456f, "#C084FC"); flower(g, 900f, 456f, "#F472B6")
        toilet(g, 768f, 384f, 6f)
        val sp = floor(t * 3f).toInt() % 2 == 1
        twinkle(g, 756f, 370f, 3f, if (sp) Color.WHITE else c("#FDE047")); twinkle(g, 852f, 356f, 2f, if (sp) c("#FDE047") else Color.WHITE)
        if (tc < 6.6f) {
            val bt = tc * 2.4f
            val hop = abs(sin(bt * PI.toFloat())) * 26f
            val cx = 300f + sin(bt * PI.toFloat() / 2f) * 16f
            shadow(g, cx, 458f, 90f - hop)
            hero(g, cx, 456f - hop, 7f, CROSS, floor(tc * 6f).toInt() % 2 == 1)
            val drop = c("#38BDF8"); val dropL = c("#E0F2FE")
            for (i in 0 until 4) {
                val k = (tc * 1.4f + i / 4f) % 1f
                val side = if (i % 2 == 1) 1f else -1f
                ga = 1f - k
                val dx = cx + side * (64f + k * 50f)
                val dy = 456f - hop - 200f - sin(k * PI.toFloat()) * 30f + k * 40f
                rect(g, dx, dy, 8f, 12f, drop); rect(g, dx + 2f, dy - 4f, 4f, 4f, drop); rect(g, dx, dy, 3f, 4f, dropL)
            }
            ga = 1f
            bubble(g, cx + 160f, 180f, if (tc < 2.2f) "!" else if (tc < 4.4f) "!!" else "!!!", 34f, 0.7f + 0.7f * (tc / 6.6f) + sin(tc * 14f) * 0.03f)
        } else if (tc < 7.6f) {
            val w = ease(tc - 6.6f)
            hero(g, lerp(300f, 700f, w), 456f - abs(sin(tc * 14f)) * 6f, 7f, walkPose(tc, 8f))
        } else {
            hero(g, 700f, 456f - abs(sin((tc - 7.6f) * 6f)) * 14f, 7f, HAPPY)
            bravo(g, tc - 7.6f)
        }
        warm(g)
    }

    /* 1. La course : le decor defile avec progress jusqu'a la cabane WC */
    private fun sCourse(g: Canvas, t: Float, st: SceneState) {
        val cel = st.celebrate
        val tc = 6f + st.celebrateT
        val cam = rnd(1100f * (if (cel) 1f else ease(st.progress)))
        sky(g, "#5FB4F5", "#D3EEFF"); sun(g, 830f, 36f)
        clouds(g, cam * 0.15f + t * 6f)
        hills(g, cam * 0.3f, "#A7DCA1", "#C2E9BC", 456f, 2f, 30f)
        hills(g, cam * 0.6f, "#76C46A", "#8FD484", 456f, 5f, 18f)
        val trees = floatArrayOf(520f, 980f, 1380f)
        for (i in trees.indices) { val tx = trees[i] - cam; if (tx > -150f && tx < W + 150f) tree(g, tx, 456f, 80f + i * 11f) }
        ground(g, 456f, cam)
        val fl = floatArrayOf(260f, 700f, 1180f, 1560f)
        val flc = arrayOf("#F87171", "#FACC15", "#4ADE80", "#60A5FA")
        for (j in fl.indices) flower(g, fl[j] - cam, 456f, flc[j])
        val cabX = 1740f - cam
        if (cel) {
            cabinSeq(g, tc, 6f, cabX, 456f, 6f, 300f)
        } else {
            cabin(g, cabX, 456f, 0f)
            val dcol = c("#D6D3D1")
            for (k in 0 until 3) {
                val a = (t * 3f + k / 3f) % 1f
                ga = 0.7f * (1f - a); rect(g, 300f - 50f - a * 60f, 440f - a * 20f, 12f, 12f, dcol)
            }
            ga = 1f
            hero(g, 300f, 456f - abs(sin(t * 18f)) * 8f, 6f, walkPose(t, 9f))
            bubble(g, 380f, 210f, "!", 24f, 0.9f)
        }
        warm(g)
    }

    /* 2. Le wagonnet : avance sur les rails avec progress */
    private fun bell(u: Float): Float = if (abs(u) < 1f) (cos(u * PI.toFloat()) + 1f) / 2f else 0f
    private fun bump(x: Float): Float = 36f * bell((x - 260f) / 80f) + 26f * bell((x - 470f) / 64f)
    private fun railY(x: Float): Float = 456f - rnd(bump(x) / 6f) * 6f

    private fun cart(g: Canvas, cx: Float, yb: Float, t: Float, moving: Boolean) {
        val x = rnd(cx - 60f)
        rect(g, x, yb - 66f, 120f, 52f, c("#374151")); rect(g, x + 6f, yb - 60f, 108f, 40f, c("#6B7280"))
        rect(g, x + 6f, yb - 60f, 108f, 6f, c("#9CA3AF")); rect(g, x + 6f, yb - 26f, 108f, 6f, c("#4B5563"))
        for (i in 0 until 4) rect(g, x + 18f + i * 26f, yb - 48f, 6f, 6f, c("#374151"))
        val r = moving && floor(t * 12f).toInt() % 2 == 1
        rect(g, x + 14f, yb - 18f, 24f, 18f, c("#111827")); rect(g, x + 82f, yb - 18f, 24f, 18f, c("#111827"))
        rect(g, x + 22f + (if (r) 0f else 4f), yb - 12f, 6f, 6f, c("#9CA3AF")); rect(g, x + 90f + (if (r) 4f else 0f), yb - 12f, 6f, 6f, c("#9CA3AF"))
    }

    private fun sWagon(g: Canvas, t: Float, st: SceneState) {
        sky(g, "#F7B267", "#FDE7C2"); sun(g, 120f, 60f); clouds(g, t * 10f, 50f)
        hills(g, 0f, "#C9A66B", "#DDBF86", 480f, 3f, 30f)
        ground(g, 480f, 0f)
        var x = 0f
        while (x < 712f) {
            val y = railY(x + 6f)
            if (y < 456f) { rect(g, x, y + 12f, 12f, 480f - y - 12f, c("#8A8F98")); rect(g, x, y + 12f, 12f, 4f, c("#A9AEB6")) }
            x += 12f
        }
        var sl = 0f
        while (sl < 712f) { rect(g, sl, railY(sl + 7f) + 6f, 14f, 10f, c("#7A5230")); sl += 24f }
        var r = 0f
        while (r < 712f) { val ry = railY(r + 6f); rect(g, r, ry, 12f, 6f, c("#8A93A3")); rect(g, r, ry, 12f, 2f, c("#D1D5DB")); r += 12f }
        rect(g, 700f, 430f, 12f, 26f, c("#B91C1C")); rect(g, 700f, 430f, 12f, 4f, c("#F87171"))
        if (!st.celebrate) {
            val cx = lerp(-80f, 600f, easeOut(st.progress))
            val yb = railY(cx)
            val moving = st.progress < 1f
            cabin(g, 740f, 480f, 0f)
            hero(g, cx, yb - 14f, 5f, if (bump(cx) > 10f) HAPPY else STAND, false, 0.9f)
            cart(g, cx, yb, t, moving)
            if (moving) for (i in 0 until 8) {
                val a = (t * 4f + i / 8f) % 1f
                val sxp = cx - 44f + (i % 2) * 68f - a * 70f
                val syp = yb - 4f - a * 36f + a * a * 60f
                ga = 1f - a; rect(g, sxp, syp, 6f, 6f, if (i % 3 != 0) c("#FDE047") else c("#FB923C")); ga = 1f
            }
        } else {
            val tc = 6.5f + st.celebrateT
            val yb0 = railY(600f)
            cart(g, 600f, yb0, t, false)
            if (tc < 7f) {
                cabin(g, 740f, 480f, 0f)
                val j = (tc - 6.5f) / 0.5f
                hero(g, lerp(600f, 680f, j), lerp(yb0 - 14f, 480f, j) - sin(PI.toFloat() * j) * 80f, 5f, JUMP)
            } else {
                cabinSeq(g, tc, 7f, 740f, 480f, 5f, 680f)
            }
        }
        warm(g)
    }

    /* 3. Le parkour : un saut de bloc en bloc a chaque palier de progress */
    private val PTS = arrayOf(floatArrayOf(110f, 504f), floatArrayOf(264f, 456f), floatArrayOf(360f, 408f), floatArrayOf(504f, 312f), floatArrayOf(680f, 264f))

    private fun sParkour(g: Canvas, t: Float, st: SceneState) {
        sky(g, "#4FA3F0", "#D6EEFF"); sun(g, 60f, 36f); clouds(g, t * 10f, 40f)
        hills(g, 0f, "#8FD08A", "#A9E0A2", 504f, 4f)
        ground(g, 504f, 0f)
        block(g, 240f, 456f, B, "grass", 901f)
        block(g, 336f, 456f, B, "dirt", 902f); block(g, 336f, 408f, B, "grass", 903f)
        val bob = rnd(sin(t * 2f) * 5f)
        rect(g, 486f, 500f, 36f, 6f, Color.argb(38, 0, 0, 0))
        block(g, 480f, 312f + bob, B, "grass", 904f)
        for (i in 0 until 6) for (j in 0 until 5) block(g, 624f + i * B, 504f - (j + 1) * B, B, if (j == 4) "grass" else if (j >= 3) "dirt" else "stone", 910f + i * 5 + j)
        toilet(g, 816f, 192f, 6f)
        val sp = floor(t * 3f).toInt() % 2 == 1
        twinkle(g, 806f, 180f, 3f, if (sp) Color.WHITE else c("#FDE047")); twinkle(g, 900f, 170f, 2f, if (sp) c("#FDE047") else Color.WHITE)
        val jt = 0.55f
        var cx: Float
        var fy: Float
        var pose: Int
        var landD = -1f
        if (!st.celebrate) {
            val target = min(4, floor(st.progress * 5f).toInt())
            if (pkStage < target && t - pkT >= jt + 0.35f) { pkStage++; pkT = t }
            val el = t - pkT
            if (pkStage > 0 && el < jt) {
                val a = PTS[pkStage - 1]; val b = PTS[pkStage]
                val ay = a[1] + (if (pkStage - 1 == 3) bob else 0f)
                val by = b[1] + (if (pkStage == 3) bob else 0f)
                val k = el / jt
                val hg = 60f + max(0f, ay - by) * 0.9f
                cx = lerp(a[0], b[0], k); fy = lerp(ay, by, k) - sin(PI.toFloat() * k) * hg; pose = JUMP
            } else {
                cx = PTS[pkStage][0]; fy = PTS[pkStage][1] + (if (pkStage == 3) bob else 0f); pose = CROSS
                if (pkStage > 0) landD = (el - jt) / 0.35f
            }
            hero(g, cx, fy, 5f, pose, floor(t * 6f).toInt() % 2 == 1)
            if (landD >= 0f) dust(g, cx, fy, landD)
            if (pkStage == 0) bubble(g, cx + 110f, 300f, "!", 24f, 0.9f)
        } else {
            val tc = 4.4f + st.celebrateT
            val endW = 5.2f
            if (tc < endW) { cx = lerp(680f, 760f, (tc - 4.4f) / 0.8f); fy = 264f; pose = walkPose(tc, 8f) }
            else { cx = 760f; fy = 264f - abs(sin(tc * 6f)) * 10f; pose = HAPPY }
            hero(g, cx, fy, 5f, pose)
            if (tc >= endW) bravo(g, tc - endW)
        }
        warm(g)
    }

    /* 4. Le mineur : creuse un bloc de plus a chaque palier de progress */
    private const val MT = 96f
    private const val ND = 9
    private const val DT = 0.62f
    private const val DCOL = 8
    private val CRACK = arrayOf(
        intArrayOf(3, 1), intArrayOf(4, 2), intArrayOf(4, 3), intArrayOf(5, 4), intArrayOf(3, 4), intArrayOf(2, 5),
        intArrayOf(5, 5), intArrayOf(6, 6), intArrayOf(2, 6), intArrayOf(1, 7), intArrayOf(6, 2), intArrayOf(7, 1)
    )

    private fun mineType(col: Int, row: Int): String {
        if (row == 0) return "grass"
        if (row < 3) return "dirt"
        if (col == DCOL && row == 4) return "dia"
        if (row >= 7 && hh(col.toFloat(), row.toFloat()) < 0.07f) return "dia"
        return if (hh(col.toFloat(), row + 40f) < 0.14f) "gravel" else "stone"
    }

    private fun isOpen(col: Int, row: Int, n: Int): Boolean {
        if (row in 3..5 && col <= 3) return true
        if (row in 2..5 && col >= 13) return true
        return row in 3..5 && col >= 4 && col < 4 + n
    }

    private fun pickaxe(g: Canvas, x: Float, y: Float, a: Float) {
        g.save(); g.translate(rnd(x), rnd(y)); g.rotate(a * 180f / PI.toFloat())
        rect(g, -6f, -4f, 60f, 8f, c("#7A5230")); rect(g, -6f, -4f, 60f, 3f, c("#97693E"))
        rect(g, 48f, -28f, 12f, 56f, c("#9CA3AF")); rect(g, 48f, -28f, 12f, 6f, c("#6B7280")); rect(g, 48f, 22f, 12f, 6f, c("#6B7280")); rect(g, 52f, -24f, 4f, 48f, c("#D1D5DB"))
        g.restore()
    }

    private fun sMine(g: Canvas, t: Float, st: SceneState) {
        sky(g, "#5FB4F5", "#BFE3FF", MT); sun(g, 850f, 12f); clouds(g, t * 10f, 10f)
        val cel = st.celebrate
        val n: Int
        if (cel) {
            n = ND
        } else {
            n = min(ND - 1, floor(st.progress * ND).toInt())
            if (n != mnN) { if (mnN >= 0 || n > 0) mnT = t; mnN = n }
        }
        val el = t - mnT
        for (col in 0 until 20) for (row in 0 until 11) {
            val x = col * B; val y = MT + row * B; val sd = col * 31f + row * 7f
            if (isOpen(col, row, n)) { block(g, x, y, B, "stone", sd); rect(g, x, y, B, B, Color.argb(158, 18, 10, 4)) }
            else block(g, x, y, B, mineType(col, row), sd)
        }
        // grotte de la toilette, eclairee par une torche
        val fl = floor(t * 8f).toInt() % 2 == 1
        ga = 0.16f; rect(g, 13f * B, MT + 2f * B, 7f * B, 4f * B, c("#FDBA74")); ga = 1f
        val tx = 14f * B + 20f; val ty = MT + 2f * B + 30f
        rect(g, tx, ty + 18f, 8f, 30f, c("#7A5230")); rect(g, tx - 2f, ty + 4f, 12f, 16f, if (fl) c("#FB923C") else c("#F59E0B")); rect(g, tx + 1f, ty + 8f, 6f, 8f, c("#FEF3C7"))
        toilet(g, 16f * B, MT + 6f * B - 72f, 6f)
        val fy0 = MT + 6f * B
        if (!cel) {
            // fissures du bloc en cours : grandissent avec progress dans le palier
            val kc = 4 + n
            val fr = clamp(st.progress * ND - n, 0f, 1f)
            val stc = min(CRACK.size, (floor(fr * 4f).toInt() + 1) * 3)
            val crackCol = Color.argb(191, 15, 10, 8)
            for (r2 in 3..5) for (q in 0 until stc) rect(g, kc * B + CRACK[q][0] * 6f, MT + r2 * B + CRACK[q][1] * 6f, 6f, 6f, crackCol)
            // eclats du bloc qui vient de casser
            if (n > 0 && el >= 0f && el < 0.45f) {
                val bc = 3 + n
                val cols = if (bc == DCOL) arrayOf("#22D3EE", "#8A8F98", "#E0FFFF") else arrayOf("#8A8F98", "#6B7078", "#A9AEB6")
                for (i in 0 until 16) {
                    val vx = (hh(i.toFloat(), bc.toFloat()) - 0.5f) * 380f
                    val vy = -120f - hh(i.toFloat(), bc + 9f) * 260f
                    ga = 1f - el / 0.45f
                    rect(g, bc * B + 24f + vx * el, MT + (3 + (i % 3)) * B + 24f + vy * el + 700f * el * el, 10f, 10f, c(cols[i % 3]))
                }
                ga = 1f
            }
            val tgt = (4 + n) * B - 30f
            val prev = (3 + n) * B - 30f
            val wt = 0.15f * DT
            val walking = n > 0 && el < wt
            val cx = if (walking) lerp(prev, tgt, el / wt) else tgt
            val sk = if (walking) 0f else max(0.001f, abs(sin(((el % DT) / DT) * PI.toFloat() * 3f)))
            hero(g, cx, fy0, 4f, if (walking) walkPose(t, 10f) else STAND, false, 1.05f, sk)
            if (!walking) {
                pickaxe(g, cx + 36f, fy0 - 84f + (1f - sk) * -22f, -1.9f + sk * 1.7f)
            }
        } else {
            val d = st.celebrateT
            if (d < 1f) {
                hero(g, lerp((3 + ND) * B - 30f, 15f * B + 10f, ease(d)), fy0, 4f, walkPose(d, 8f))
            } else {
                hero(g, 15f * B + 10f, fy0 - abs(sin(d * 6f)) * 8f, 4f, HAPPY)
                bravo(g, d - 1f)
            }
        }
    }

    /* 5. Le portail (boucle sur t, arrivee + Bravo pendant la fete) */
    private val PCOL = arrayOf("#8B5CF6", "#60A5FA", "#C4B5FD", "#3B82F6", "#A855F7")

    private fun oval(g: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, col: Int) {
        var yy = -ry
        while (yy <= ry) {
            val k = 1f - (yy * yy) / (ry * ry)
            if (k > 0f) {
                val hw = rnd(rx * sqrt(k) / 8f) * 8f
                if (hw > 0f) rect(g, cx - hw, cy + yy - 4f, hw * 2f, 8f, col)
            }
            yy += 8f
        }
    }

    private fun portal(g: Canvas, cx: Float, cy: Float, s: Float, t: Float) {
        if (s <= 0.02f) return
        val rx = 76f * s; val ry = 116f * s
        ga = 0.25f; oval(g, cx, cy, rx + 34f, ry + 34f, c("#C4B5FD")); ga = 1f
        oval(g, cx, cy, rx + 14f, ry + 14f, c("#4C1D95")); oval(g, cx, cy, rx, ry, c("#1E1B4B"))
        for (i in 0 until 18) {
            val a = i / 18f * PI.toFloat() * 2f
            rect(g, rnd((cx + cos(a) * (rx + 7f)) / 8f) * 8f - 4f, rnd((cy + sin(a) * (ry + 7f)) / 8f) * 8f - 4f, 8f, 8f, if (i % 2 == 1) c("#C4B5FD") else c("#7C3AED"))
        }
        for (j in 0 until 90) {
            val f = j / 90f
            val b = f * 14f + t * 5f
            rect(g, rnd((cx + cos(b) * rx * f * 0.92f) / 8f) * 8f - 4f, rnd((cy + sin(b) * ry * f * 0.92f) / 8f) * 8f - 4f, 8f, 8f, c(PCOL[j % 5]))
        }
        rect(g, cx - 8f, cy - 8f, 16f, 16f, c("#EDE9FE"))
        for (k in 0 until 6) {
            val a2 = t * 2f + k
            ga = 0.5f + 0.5f * sin(t * 6f + k)
            twinkle(g, cx + cos(a2) * rx * 1.35f, cy + sin(a2) * ry * 1.2f, 2f, c("#E9D5FF"))
        }
        ga = 1f
    }

    private fun sPortail(g: Canvas, t: Float, st: SceneState) {
        val cel = st.celebrate
        val tc = if (cel) 5.8f + st.celebrateT else t % 6.8f
        sky(g, "#5B6EE8", "#D9DEFF"); clouds(g, t * 8f, 40f)
        for (i in 0 until 12) { ga = 0.4f + 0.6f * abs(sin(t + i)); rect(g, ((i * 83) % 960).toFloat(), 20f + (i * 37) % 120, 4f, 4f, Color.WHITE) }
        ga = 1f
        hills(g, 0f, "#7F8CE0", "#98A3EA", 456f, 6f)
        ground(g, 456f, 0f) { col -> col in 9..11 }
        rect(g, 432f, 468f, 144f, H - 468f, c("#2563EB"))
        for (w in 0 until 18) rect(g, 432f + w * 8f, 462f + ((w + floor(t * 6f).toInt()) % 3) * 3f, 8f, 6f, c("#60A5FA"))
        rect(g, 432f, 520f, 144f, 6f, c("#1D4ED8"))
        toilet(g, 820f, 384f, 6f)
        val ax = 300f; val ay = 336f; val bx = 690f; val by = 356f
        val sA = if (tc < 1f) 0f else if (tc < 2.2f) ease((tc - 1f) / 1.2f) else if (tc < 5f) 1f else if (tc < 5.8f) 1f - ease((tc - 5f) / 0.8f) else 0f
        val sB = if (tc < 3.6f) 0f else if (tc < 4.4f) ease((tc - 3.6f) / 0.8f) else if (tc < 5.2f) 1f else if (tc < 6f) 1f - ease((tc - 5.2f) / 0.8f) else 0f
        portal(g, ax, ay, sA, t); portal(g, bx, by, sB * 0.85f, t)
        val u = 5f
        val mid = 15.75f * u
        if (tc < 2.2f) {
            hero(g, 130f, 456f, u, CROSS, floor(tc * 6f).toInt() % 2 == 1)
            bubble(g, 230f, 260f, if (tc < 1.1f) "!" else "?!", 24f, 0.9f)
        } else if (tc < 3.4f) {
            hero(g, lerp(130f, 230f, (tc - 2.2f) / 1.2f), 456f, u, walkPose(tc, 8f))
        } else if (tc < 4f) {
            val k = (tc - 3.4f) / 0.6f
            val us = u * (1f - k * 0.85f)
            hero(g, lerp(230f, ax, k), lerp(456f - mid, ay, k) - sin(PI.toFloat() * k) * 70f + 15.75f * us, us, JUMP)
        } else if (tc < 4.4f) {
            ga = 1f - (tc - 4f) / 0.4f; oval(g, ax, ay, 60f, 90f, c("#F5F3FF")); ga = 1f
        } else if (tc < 5f) {
            val k2 = (tc - 4.4f) / 0.6f
            val u2 = u * (0.15f + 0.85f * k2)
            hero(g, lerp(bx, 740f, k2), lerp(by, 456f - mid, k2) - sin(PI.toFloat() * k2) * 80f + 15.75f * u2, u2, JUMP)
        } else {
            hero(g, 740f, 456f - (if (tc > 5.8f) abs(sin(tc * 6f)) * 10f else 0f), u, if (tc > 5.3f) HAPPY else STAND)
        }
        if (cel && tc > 5.8f) bravo(g, tc - 5.8f)
    }
}
