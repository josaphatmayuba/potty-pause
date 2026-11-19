package com.famille.rappelpipi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Petit moteur 3D logiciel pour les personnages « Blocs » (type Minecraft) : 6 parties en pave
 * (tete, corps, 2 bras, 2 jambes) texturees avec une skin 64x64 (UV standard + calque externe),
 * perspective, rotation (yaw) de l'ensemble et des membres autour de leur pivot, faces triees
 * par profondeur (algorithme du peintre) avec elimination des faces arriere, eclairage par face.
 * Chaque face est dessinee par Matrix.setPolyToPoly sur la sous-region du Bitmap (pixels nets).
 * Aucune allocation pendant le dessin : tout est pre-alloue.
 */
object Player3D {
    const val STAND = 0
    const val CROSS = 1
    const val WALK0 = 2
    const val WALK1 = 3
    const val JUMP = 4
    const val HAPPY = 5

    private const val HEAD = 0
    private const val BODY = 1
    private const val RARM = 2
    private const val LARM = 3
    private const val RLEG = 4
    private const val LLEG = 5

    private const val CAM = 100f      // distance de la camera (unites de la skin ; le personnage fait 32)
    private const val PITCH = 0.13f   // camera un peu au-dessus
    private const val NLEV = 16

    // Dimensions (w, h, d), decalage du centre par rapport au pivot, pivot, UV base / calque, epaisseur du calque.
    private val PW = floatArrayOf(8f, 8f, 4f, 4f, 4f, 4f)
    private val PH = floatArrayOf(8f, 12f, 12f, 12f, 12f, 12f)
    private val PD = floatArrayOf(8f, 4f, 4f, 4f, 4f, 4f)
    private val PCY = floatArrayOf(4f, 0f, -6f, -6f, -6f, -6f)
    private val PIVX = floatArrayOf(0f, 0f, -6f, 6f, -2f, 2f)
    private val PIVY = floatArrayOf(24f, 18f, 24f, 24f, 12f, 12f)
    private val UB = intArrayOf(0, 16, 40, 32, 0, 16)
    private val VB = intArrayOf(0, 16, 16, 48, 16, 48)
    private val UO = intArrayOf(32, 16, 40, 48, 0, 0)
    private val VO = intArrayOf(0, 32, 32, 48, 32, 48)
    private val GROW = floatArrayOf(0.5f, 0.25f, 0.25f, 0.25f, 0.25f, 0.25f)

    // Faces : 0 +x, 1 -x, 2 dessus, 3 dessous, 4 face, 5 dos. Sommets (haut-gauche, haut-droit, bas-droit, bas-gauche)
    // vus de l'exterieur ; indice de sommet = (x>0 ?1:0) + (y>0 ?2:0) + (z>0 ?4:0).
    private val FV = intArrayOf(
        7, 3, 1, 5,
        2, 6, 4, 0,
        2, 3, 7, 6,
        0, 1, 5, 4,
        6, 7, 5, 4,
        3, 2, 0, 1
    )

    // Region (x0, y0, x1, y1) de chaque face de chaque pave (0..5 = base, 6..11 = calque) dans la skin.
    private val REG = IntArray(12 * 6 * 4)
    private val SRC = FloatArray(12 * 6 * 8)

    init {
        for (b in 0 until 12) {
            val p = b % 6
            val ov = b >= 6
            val u = if (ov) UO[p] else UB[p]
            val v = if (ov) VO[p] else VB[p]
            val w = PW[p].toInt(); val h = PH[p].toInt(); val d = PD[p].toInt()
            fun put(f: Int, x: Int, y: Int, rw: Int, rh: Int) {
                val o = (b * 6 + f) * 4
                REG[o] = x; REG[o + 1] = y; REG[o + 2] = x + rw; REG[o + 3] = y + rh
                val s = (b * 6 + f) * 8
                SRC[s] = x.toFloat(); SRC[s + 1] = y.toFloat()
                SRC[s + 2] = (x + rw).toFloat(); SRC[s + 3] = y.toFloat()
                SRC[s + 4] = (x + rw).toFloat(); SRC[s + 5] = (y + rh).toFloat()
                SRC[s + 6] = x.toFloat(); SRC[s + 7] = (y + rh).toFloat()
            }
            put(0, u + d + w, v + d, d, h)
            put(1, u, v + d, d, h)
            put(2, u + d, v, w, d)
            put(3, u + d + w, v, w, d)
            put(4, u + d, v + d, w, h)
            put(5, u + 2 * d + w, v + d, w, h)
        }
    }

    // ---- Skins (cache lazy par kind) ----
    private var appCtx: Context? = null
    private val skins = arrayOfNulls<Bitmap>(18)
    private val skinFail = BooleanArray(18)

    fun init(c: Context) { if (appCtx == null) appCtx = c.applicationContext }

    private fun skin(k: Int): Bitmap? {
        skins[k]?.let { return it }
        if (skinFail[k]) return null
        val c = appCtx ?: return null
        return try {
            val o = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bm = c.assets.open("skins/skin%02d.png".format(k)).use { BitmapFactory.decodeStream(it, null, o) }
            if (bm == null) skinFail[k] = true else skins[k] = bm
            bm
        } catch (e: Throwable) {
            skinFail[k] = true
            null
        }
    }

    // ---- Tampons reutilises ----
    private val paints = Array(NLEV) { i ->
        val v = (255f * (0.5f + 0.5f * i / (NLEV - 1))).toInt()
        Paint().apply {
            isAntiAlias = false; isFilterBitmap = false; isDither = false
            colorFilter = LightingColorFilter(Color.rgb(v, v, v), 0)
        }
    }
    private val mtx = Matrix()
    private val srcR = Rect()
    private val dstR = RectF()
    private val dstPts = FloatArray(8)
    private val srcPts = FloatArray(8)

    private val prx = FloatArray(6); private val pry = FloatArray(6); private val prz = FloatArray(6)
    private val pox = FloatArray(6); private val poy = FloatArray(6); private val poz = FloatArray(6)
    private val rView = FloatArray(9); private val rYaw = FloatArray(9); private val rPit = FloatArray(9)
    private val rPart = FloatArray(9); private val mm = FloatArray(9)
    private val vx = FloatArray(12 * 8); private val vy = FloatArray(12 * 8); private val vz = FloatArray(12 * 8)
    private val sx = FloatArray(12 * 8); private val sy = FloatArray(12 * 8)
    private val fn = FloatArray(12 * 6 * 3)
    private val fB = IntArray(72); private val fF = IntArray(72); private val fL = IntArray(72)
    private val fZ = FloatArray(72)

    // Lumiere fixe cote camera (haut, droite, devant).
    private const val LX = 0.352f; private const val LY = 0.755f; private const val LZ = 0.553f

    // Rotation d'Euler ordre XYZ (comme three.js), matrice 3x3 ligne par ligne.
    private fun rot(o: FloatArray, x: Float, y: Float, z: Float) {
        val a = cos(x); val b = sin(x); val c = cos(y); val d = sin(y); val e = cos(z); val f = sin(z)
        val ae = a * e; val af = a * f; val be = b * e; val bf = b * f
        o[0] = c * e; o[1] = -c * f; o[2] = d
        o[3] = af + be * d; o[4] = ae - bf * d; o[5] = -b * c
        o[6] = bf - ae * d; o[7] = be + af * d; o[8] = a * c
    }

    private fun mul(o: FloatArray, a: FloatArray, b: FloatArray) {
        for (r in 0 until 3) for (c in 0 until 3) {
            o[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
        }
    }

    private fun setPose(pose: Int, f: Boolean, swing: Float, t: Float) {
        for (i in 0 until 6) {
            prx[i] = 0f; pry[i] = 0f; prz[i] = 0f
            pox[i] = PIVX[i]; poy[i] = PIVY[i]; poz[i] = 0f
        }
        when (pose) {
            CROSS -> {
                // jambes croisees, mains devant, tete qui se balance
                prz[RLEG] = 0.32f; prz[LLEG] = -0.32f; poz[RLEG] = 1.5f; poz[LLEG] = -1.5f
                prx[RARM] = -1.15f; prx[LARM] = -1.15f; prz[RARM] = 0.45f; prz[LARM] = -0.45f
                prz[HEAD] = sin(t * 5f) * 0.2f; prx[HEAD] = 0.1f
                prz[BODY] = if (f) 0.05f else -0.05f
            }
            WALK0, WALK1 -> {
                val a = sin(t * 14f) * 0.8f
                prx[RLEG] = a; prx[LLEG] = -a; prx[RARM] = -a; prx[LARM] = a
                pry[HEAD] = sin(t * 7f) * 0.08f
                pry[BODY] = a * 0.08f
            }
            JUMP -> {
                prz[RARM] = -2.6f + sin(t * 12f) * 0.15f; prz[LARM] = 2.6f - sin(t * 12f) * 0.15f
                prx[RLEG] = -0.75f; prx[LLEG] = 0.45f; prz[RLEG] = -0.12f; prz[LLEG] = 0.12f
                prx[HEAD] = -0.12f
            }
            HAPPY -> {
                val w = sin(t * 9f)
                prz[RARM] = -2.55f + w * 0.25f; prz[LARM] = 2.55f - w * 0.25f
                prz[RLEG] = -0.12f; prz[LLEG] = 0.12f
                prz[HEAD] = w * 0.08f; prx[HEAD] = -0.1f
            }
            else -> {
                val b = sin(t * 1.6f) * 0.03f
                prz[RARM] = -0.06f - b; prz[LARM] = 0.06f + b
                pry[HEAD] = sin(t * 0.7f) * 0.12f
                if (swing > 0f) {
                    // coup de pioche : bras leves puis abattus
                    val a = 1.2f + 1.2f * (1f - swing.coerceIn(0f, 1f))
                    prx[RARM] = -a; prx[LARM] = -a + 0.25f
                    prz[RARM] = -0.12f; prz[LARM] = 0.28f
                    prx[BODY] = 0.05f
                }
            }
        }
    }

    private fun autoYaw(pose: Int, t: Float): Float = when (pose) {
        WALK0, WALK1 -> 0.95f
        JUMP -> 0.75f
        CROSS -> 0.45f + sin(t * 1.7f) * 0.3f
        HAPPY -> 0.2f + sin(t * 2.4f) * 0.45f
        else -> 0.5f + sin(t * 0.8f) * 0.15f
    }

    /**
     * Dessine le personnage dans le repere courant de [g] : (cx, fy) = milieu des pieds, u0 = echelle
     * (1 unite de skin = u0 pixels, le personnage mesure 32 unites). Renvoie false si la skin est indisponible.
     */
    fun draw(g: Canvas, kind: Int, cx: Float, fy: Float, u0: Float, pose: Int, f: Boolean, yawIn: Float, swing: Float, t: Float): Boolean {
        val k = kind.coerceIn(0, 17)
        val bm = skin(k) ?: return false
        if (u0 < 0.2f) return true
        setPose(pose, f, swing, t)
        val yaw = if (yawIn.isNaN()) autoYaw(pose, t) else yawIn
        rot(rYaw, 0f, yaw, 0f); rot(rPit, PITCH, 0f, 0f); mul(rView, rPit, rYaw)
        val nb = if (k < 16) 12 else 6   // JJ et Mikey : pas de calque externe (pixels parasites)
        val cy0 = fy - 16f * u0
        for (p in 0 until 6) {
            rot(rPart, prx[p], pry[p], prz[p])
            mul(mm, rView, rPart)
            val lx = pox[p]; val ly = poy[p] - 16f; val lz = poz[p]
            val px = rView[0] * lx + rView[1] * ly + rView[2] * lz
            val py = rView[3] * lx + rView[4] * ly + rView[5] * lz
            val pz = rView[6] * lx + rView[7] * ly + rView[8] * lz
            var bi = p
            while (bi < nb) {
                val gw = GROW[p] * (if (bi >= 6) 1f else 0f)
                val hx = PW[p] / 2f + gw; val hy = PH[p] / 2f + gw; val hz = PD[p] / 2f + gw
                for (v in 0 until 8) {
                    val ax = if (v and 1 != 0) hx else -hx
                    val ay = (if (v and 2 != 0) hy else -hy) + PCY[p]
                    val az = if (v and 4 != 0) hz else -hz
                    val o = bi * 8 + v
                    val X = mm[0] * ax + mm[1] * ay + mm[2] * az + px
                    val Y = mm[3] * ax + mm[4] * ay + mm[5] * az + py
                    val Z = mm[6] * ax + mm[7] * ay + mm[8] * az + pz
                    vx[o] = X; vy[o] = Y; vz[o] = Z
                    val s = u0 * CAM / (CAM - Z)
                    sx[o] = cx + X * s
                    sy[o] = cy0 - Y * s
                }
                // normales des 6 faces (colonnes de la matrice, signees)
                val n = bi * 18
                fn[n] = mm[0]; fn[n + 1] = mm[3]; fn[n + 2] = mm[6]
                fn[n + 3] = -mm[0]; fn[n + 4] = -mm[3]; fn[n + 5] = -mm[6]
                fn[n + 6] = mm[1]; fn[n + 7] = mm[4]; fn[n + 8] = mm[7]
                fn[n + 9] = -mm[1]; fn[n + 10] = -mm[4]; fn[n + 11] = -mm[7]
                fn[n + 12] = mm[2]; fn[n + 13] = mm[5]; fn[n + 14] = mm[8]
                fn[n + 15] = -mm[2]; fn[n + 16] = -mm[5]; fn[n + 17] = -mm[8]
                bi += 6
            }
        }
        // faces visibles (normale contre la camera), avec leur profondeur et leur luminosite
        var cnt = 0
        for (b in 0 until nb) for (fc in 0 until 6) {
            val n = (b * 6 + fc) * 3
            val nx = fn[n]; val ny = fn[n + 1]; val nz = fn[n + 2]
            val i0 = b * 8 + FV[fc * 4]; val i1 = b * 8 + FV[fc * 4 + 1]; val i2 = b * 8 + FV[fc * 4 + 2]; val i3 = b * 8 + FV[fc * 4 + 3]
            val fx = (vx[i0] + vx[i1] + vx[i2] + vx[i3]) * 0.25f
            val fyy = (vy[i0] + vy[i1] + vy[i2] + vy[i3]) * 0.25f
            val fz = (vz[i0] + vz[i1] + vz[i2] + vz[i3]) * 0.25f
            if (nx * fx + ny * fyy + nz * (fz - CAM) >= 0f) continue
            val dot = max(0f, nx * LX + ny * LY + nz * LZ)
            val m = (0.58f + 0.45f * dot).coerceIn(0.5f, 1f)
            var j = cnt++
            val z = fz + (if (b >= 6) 0.05f else 0f)
            // insertion triee par profondeur croissante (le plus loin d'abord)
            while (j > 0 && fZ[j - 1] > z) {
                fZ[j] = fZ[j - 1]; fB[j] = fB[j - 1]; fF[j] = fF[j - 1]; fL[j] = fL[j - 1]
                j--
            }
            fZ[j] = z; fB[j] = b; fF[j] = fc; fL[j] = ((m - 0.5f) / 0.5f * (NLEV - 1) + 0.5f).toInt().coerceIn(0, NLEV - 1)
        }
        for (i in 0 until cnt) {
            val b = fB[i]; val fc = fF[i]
            val so = (b * 6 + fc) * 8
            for (q in 0 until 8) srcPts[q] = SRC[so + q]
            for (q in 0 until 4) {
                val vi = b * 8 + FV[fc * 4 + q]
                dstPts[q * 2] = sx[vi]; dstPts[q * 2 + 1] = sy[vi]
            }
            if (!mtx.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) continue
            val ro = (b * 6 + fc) * 4
            srcR.set(REG[ro], REG[ro + 1], REG[ro + 2], REG[ro + 3])
            dstR.set(REG[ro].toFloat(), REG[ro + 1].toFloat(), REG[ro + 2].toFloat(), REG[ro + 3].toFloat())
            g.save()
            g.concat(mtx)
            g.drawBitmap(bm, srcR, dstR, paints[fL[i]])
            g.restore()
        }
        return true
    }
}
