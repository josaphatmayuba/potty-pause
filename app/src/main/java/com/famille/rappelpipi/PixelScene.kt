package com.famille.rappelpipi

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

/**
 * Scene en pixels dessinee par code (aucune image), style "bebe mob" tres mignon :
 * grosse tete, grands yeux, joues roses. Il se tortille (envie de pipi), une fleche
 * rebondit vers une toilette qui scintille. Une barre de blocs verts se remplit pendant
 * le blocage (pas besoin de savoir lire). Grille logique 64 x 40.
 */
class PixelScene(context: Context) : View(context) {
    private val p = Paint().apply { isAntiAlias = false }
    private val text = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    /** Mode fete (apres "Bravo") : confettis, grand sourire, coffre si 5 diamants. */
    var celebrate = false
    var chest = false

    /** Diamants gagnes (0..5), affiches en bas a droite, dans la terre. */
    var diamonds = 0

    /** Personnage affiche (change a chaque rappel). */
    var kind = 0

    /** 0..1 : avancement du blocage. */
    var progress = 0f
        set(v) { field = v.coerceIn(0f, 1f) }

    private var phase = 0f
    private var u = 1f
    private var ox = 0f
    private var oy = 0f
    private lateinit var canvas: Canvas

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 600
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    private fun r(x: Float, y: Float, w: Float, h: Float, color: Int) {
        p.color = color
        canvas.drawRect(ox + x * u, oy + y * u, ox + (x + w) * u, oy + (y + h) * u, p)
    }

    private fun c(hex: String) = Color.parseColor(hex)

    override fun onDraw(cv: Canvas) {
        canvas = cv
        val gw = 64f
        val gh = 40f
        u = min(width / gw, height / gh)
        ox = (width - gw * u) / 2
        oy = (height - gh * u) / 2

        val s = if (phase < 0.5f) 0f else 1f

        // Ciel + soleil + nuages
        r(0f, 0f, gw, 34f, c("#8ED1FC"))
        r(0f, 20f, gw, 14f, c("#B6E3FD"))
        r(52f, 3f, 7f, 7f, c("#FFE066")); r(53f, 4f, 5f, 5f, c("#FFF3A8"))
        r(2f, 5f, 10f, 3f, Color.WHITE); r(4f, 3f, 6f, 2f, Color.WHITE)
        r(30f, 2f, 9f, 3f, Color.WHITE); r(32f, 0.5f, 5f, 2f, Color.WHITE)

        // Herbe + terre
        r(0f, 34f, gw, 1.6f, c("#6FD36F"))
        r(0f, 35.6f, gw, 4.4f, c("#8B5A2B"))
        for (i in 0 until 16) r(1f + i * 4f + (i % 2) * 1.5f, 37f + (i % 3), 1.2f, 1.2f, c("#6E4421"))

        drawKid(8f + s * 0.8f, 4f, s)
        if (!celebrate) { drawArrow(s); drawBubble(s) }
        drawToilet(44f, 12f, s)
        drawDiamonds()
        if (celebrate) {
            if (chest) drawChest()
            drawConfetti()
        } else {
            drawProgress()
        }
    }

    private fun drawKid(x: Float, y: Float, s: Float) {
        // kind : 0 enfant, 1 petit monstre vert, 2 bebe panda, 3 chiot, 4 cochon, 5 mouton, 6 vache, 7 poussin,
        // 8 renard, 9 axolotl, 10 abeille, 11 grenouille, 12 chat, 13 cube de gelee, 14 villageois, 15 petit robot
        val skin = when (kind) { 1 -> c("#6BC45A"); 2 -> c("#F8FAFC"); 3 -> c("#C9A27A"); 4 -> c("#F9A8C0"); 5 -> c("#F8FAFC"); 6 -> c("#F8FAFC"); 7 -> c("#FDE047"); 8 -> c("#F97316"); 9 -> c("#FBCFE8"); 10 -> c("#FACC15"); 11 -> c("#4ADE80"); 12 -> c("#9CA3AF"); 13 -> c("#86EFAC"); 14 -> c("#D6A77A"); 15 -> c("#CBD5E1"); else -> c("#F2B98F") }
        val skinD = when (kind) { 1 -> c("#4A9A3C"); 2 -> c("#CBD5E1"); 3 -> c("#A57F56"); 4 -> c("#E77FA0"); 5 -> c("#CBD5E1"); 6 -> c("#CBD5E1"); 7 -> c("#EAB308"); 8 -> c("#C2410C"); 9 -> c("#F472B6"); 10 -> c("#CA8A04"); 11 -> c("#16A34A"); 12 -> c("#6B7280"); 13 -> c("#4ADE80"); 14 -> c("#B07F54"); 15 -> c("#94A3B8"); else -> c("#D9946B") }
        val hair = c("#5B3A1E"); val hairL = c("#7A5230")
        val shirt = when (kind) { 1 -> c("#5BB84A"); 2 -> c("#F8FAFC"); 3 -> c("#C9A27A"); 4 -> c("#F9A8C0"); 5 -> c("#F8FAFC"); 6 -> c("#F8FAFC"); 7 -> c("#FDE047"); 8 -> c("#F97316"); 9 -> c("#FBCFE8"); 10 -> c("#FACC15"); 11 -> c("#4ADE80"); 12 -> c("#9CA3AF"); 13 -> c("#86EFAC"); 14 -> c("#8B5A2B"); 15 -> c("#94A3B8"); else -> c("#1FB5B5") }
        val shirtD = when (kind) { 1 -> c("#3F8A33"); 2 -> c("#CBD5E1"); 3 -> c("#A57F56"); 4 -> c("#E77FA0"); 5 -> c("#CBD5E1"); 6 -> c("#CBD5E1"); 7 -> c("#EAB308"); 8 -> c("#C2410C"); 9 -> c("#F9A8D4"); 10 -> c("#CA8A04"); 11 -> c("#16A34A"); 12 -> c("#6B7280"); 13 -> c("#4ADE80"); 14 -> c("#6E4421"); 15 -> c("#64748B"); else -> c("#14898A") }
        val pants = when (kind) { 1 -> c("#4A9A3C"); 2 -> c("#1F2937"); 3 -> c("#A57F56"); 4 -> c("#F48FB1"); 5 -> c("#6B7280"); 6 -> c("#374151"); 7 -> c("#F97316"); 8 -> c("#9A3412"); 9 -> c("#F9A8D4"); 10 -> c("#1F2937"); 11 -> c("#22C55E"); 12 -> c("#6B7280"); 13 -> c("#4ADE80"); 14 -> c("#8B5A2B"); 15 -> c("#64748B"); else -> c("#3D52D5") }
        val pantsD = when (kind) { 1 -> c("#3F8A33"); 2 -> c("#111827"); 3 -> c("#8A6743"); 4 -> c("#D9739A"); 5 -> c("#4B5563"); 6 -> c("#1F2937"); 7 -> c("#C2410C"); 8 -> c("#7C2D12"); 9 -> c("#F472B6"); 10 -> c("#111827"); 11 -> c("#15803D"); 12 -> c("#4B5563"); 13 -> c("#22C55E"); 14 -> c("#6E4421"); 15 -> c("#475569"); else -> c("#2B3AA6") }
        val shoe = if (kind == 0) c("#E5E7EB") else pants; val dark = c("#1F2937")

        // Jambes (serrees, decalage = il se retient)
        r(x + 3f + s * 0.6f, y + 22f, 4f, 8f, pants); r(x + 3f + s * 0.6f, y + 22f, 1f, 8f, pantsD)
        r(x + 7f - s * 0.6f, y + 22f, 4f, 8f, pants)
        r(x + 2.5f + s * 0.6f, y + 29.5f, 5f, 2f, shoe)
        r(x + 6.5f - s * 0.6f, y + 29.5f, 5f, 2f, shoe)
        // Corps + bras
        r(x + 2f, y + 14f, 10f, 9f, shirt); r(x + 2f, y + 21f, 10f, 2f, shirtD)
        r(x - 1f, y + 14f, 3f, 8f, shirt); r(x + 12f, y + 14f, 3f, 8f, shirt)
        // Mains jointes devant (il se tient)
        r(x + 4f, y + 20.5f, 6f, 3f, skin); r(x + 4f, y + 22.5f, 6f, 0.6f, skinD)
        // Grosse tete
        r(x, y, 14f, 14f, skin); r(x, y + 12.5f, 14f, 1.5f, skinD)
        if (kind == 0) {
            r(x - 0.5f, y - 0.5f, 15f, 5f, hair)
            r(x + 3f, y + 4.5f, 3f, 1.5f, hair); r(x + 9f, y + 4.5f, 2f, 1.5f, hair)
            r(x - 0.5f, y + 4f, 2f, 5f, hair); r(x + 12.5f, y + 4f, 2f, 5f, hair)
            r(x + 3f, y, 5f, 1f, hairL)
            // Grands yeux brillants
            r(x + 2f, y + 6f, 4f, 5f, Color.WHITE); r(x + 8f, y + 6f, 4f, 5f, Color.WHITE)
            r(x + 3.5f, y + 7.5f, 2.5f, 3f, c("#2563EB")); r(x + 9.5f, y + 7.5f, 2.5f, 3f, c("#2563EB"))
            r(x + 4.2f, y + 8.2f, 1.3f, 1.8f, dark); r(x + 10.2f, y + 8.2f, 1.3f, 1.8f, dark)
            r(x + 3.5f, y + 7.5f, 1f, 1f, Color.WHITE); r(x + 9.5f, y + 7.5f, 1f, 1f, Color.WHITE)
            // Sourcils inquiets, joues roses, petite bouche
            r(x + 2f, y + 5f, 4f, 0.8f, hair); r(x + 8f, y + 5f, 4f, 0.8f, hair)
            r(x + 1f, y + 10.5f, 2.5f, 1.5f, c("#FF8FA3")); r(x + 10.5f, y + 10.5f, 2.5f, 1.5f, c("#FF8FA3"))
            r(x + 5.5f, y + 11f, 3f, 1.8f, c("#B4232C"))
        } else if (kind == 1) {
            // petit monstre vert : taches + visage carre
            r(x + 1f, y + 1f, 3f, 3f, skinD); r(x + 9f, y + 2f, 3f, 3f, skinD); r(x + 5f, y + 11f, 4f, 2f, skinD)
            r(x + 2f, y + 5f, 4f, 4f, dark); r(x + 8f, y + 5f, 4f, 4f, dark)
            r(x + 3f, y + 5.8f, 1.3f, 1.3f, Color.WHITE); r(x + 9f, y + 5.8f, 1.3f, 1.3f, Color.WHITE)
            r(x + 5f, y + 9f, 4f, 1.5f, dark); r(x + 4f, y + 10.5f, 2f, 2.5f, dark); r(x + 8f, y + 10.5f, 2f, 2.5f, dark)
        } else if (kind == 2) {
            // bebe panda : oreilles + taches noires
            r(x - 1f, y - 2f, 4f, 4f, dark); r(x + 11f, y - 2f, 4f, 4f, dark)
            r(x + 1.5f, y + 5f, 5f, 6f, dark); r(x + 7.5f, y + 5f, 5f, 6f, dark)
            r(x + 3f, y + 6.5f, 2.5f, 3f, Color.WHITE); r(x + 8.5f, y + 6.5f, 2.5f, 3f, Color.WHITE)
            r(x + 3.8f, y + 7.5f, 1.2f, 1.6f, dark); r(x + 9.3f, y + 7.5f, 1.2f, 1.6f, dark)
            r(x + 5.5f, y + 10.5f, 3f, 1.8f, dark)
            r(x + 1f, y + 11f, 2.2f, 1.4f, c("#FF8FA3")); r(x + 10.8f, y + 11f, 2.2f, 1.4f, c("#FF8FA3"))
        } else if (kind == 3) {
            // chiot : oreilles tombantes, museau, queue
            r(x - 2.5f, y + 1f, 3f, 8f, c("#7A5230")); r(x + 13.5f, y + 1f, 3f, 8f, c("#7A5230"))
            r(x + 2f, y + 6f, 4f, 5f, Color.WHITE); r(x + 8f, y + 6f, 4f, 5f, Color.WHITE)
            r(x + 3.5f, y + 7.5f, 2.5f, 3f, dark); r(x + 9.5f, y + 7.5f, 2.5f, 3f, dark)
            r(x + 3.5f, y + 7.5f, 1f, 1f, Color.WHITE); r(x + 9.5f, y + 7.5f, 1f, 1f, Color.WHITE)
            r(x + 4f, y + 9.5f, 6f, 4f, c("#E8D2B5")); r(x + 5.8f, y + 9.8f, 2.4f, 1.6f, dark)
            r(x + 6.5f, y + 12f, 1f, 1.2f, c("#B4232C"))
            r(x + 14f, y + 15f, 4f, 2.4f, c("#A57F56")); r(x + 17f, y + 13f, 2f, 3f, c("#A57F56"))
        } else if (kind == 4) {
            // petit cochon : oreilles, groin, narines
            r(x + 0.5f, y - 1.5f, 3f, 2.5f, skinD); r(x + 10.5f, y - 1.5f, 3f, 2.5f, skinD); r(x + 2.5f, y + 5f, 2.5f, 2.5f, dark); r(x + 9f, y + 5f, 2.5f, 2.5f, dark)
            r(x + 2.8f, y + 5.2f, 1f, 1f, Color.WHITE); r(x + 9.3f, y + 5.2f, 1f, 1f, Color.WHITE); r(x + 4f, y + 7.5f, 6f, 4f, c("#F06292")); r(x + 5f, y + 9f, 1.2f, 1.6f, c("#9D174D"))
            r(x + 7.8f, y + 9f, 1.2f, 1.6f, c("#9D174D")); r(x + 1f, y + 9.5f, 2f, 1.4f, c("#FF8FA3")); r(x + 11f, y + 9.5f, 2f, 1.4f, c("#FF8FA3")); r(x + 6f, y + 12f, 2f, 0.8f, dark)
        } else if (kind == 5) {
            // petit mouton : laine bouclee en blocs, visage beige
            r(x - 1f, y - 1f, 4f, 3f, Color.WHITE); r(x + 3.5f, y - 1.5f, 5f, 2.5f, Color.WHITE); r(x + 9f, y - 1f, 5f, 3f, Color.WHITE); r(x - 1f, y + 2.5f, 2f, 5f, Color.WHITE)
            r(x + 13f, y + 2.5f, 2f, 5f, Color.WHITE); r(x + 2f, y + 3f, 10f, 10f, c("#E8D5C4")); r(x - 2f, y + 5f, 2.5f, 2f, c("#E8D5C4")); r(x + 13.5f, y + 5f, 2.5f, 2f, c("#E8D5C4"))
            r(x + 3.5f, y + 5.5f, 2.2f, 2.5f, dark); r(x + 8.3f, y + 5.5f, 2.2f, 2.5f, dark); r(x + 3.7f, y + 5.7f, 0.9f, 0.9f, Color.WHITE); r(x + 8.5f, y + 5.7f, 0.9f, 0.9f, Color.WHITE)
            r(x + 6f, y + 9f, 2f, 1.2f, c("#B07A5A")); r(x + 6.5f, y + 10.6f, 1f, 1f, c("#B07A5A")); r(x + 2.5f, y + 9f, 1.8f, 1.2f, c("#FF8FA3")); r(x + 9.7f, y + 9f, 1.8f, 1.2f, c("#FF8FA3"))
            r(x + 1f, y + 14f, 3f, 2f, Color.WHITE); r(x + 10f, y + 14f, 3f, 2f, Color.WHITE)
        } else if (kind == 6) {
            // petite vache : taches noires, cornes, museau rose
            r(x + 1f, y - 2f, 2f, 2f, c("#F1E3C4")); r(x + 11f, y - 2f, 2f, 2f, c("#F1E3C4")); r(x - 2f, y + 3f, 2.5f, 2f, dark); r(x + 13.5f, y + 3f, 2.5f, 2f, dark)
            r(x, y, 4f, 3f, dark); r(x + 10f, y + 1f, 4f, 3.5f, dark); r(x + 12f, y + 5f, 2f, 2f, dark); r(x + 3f, y + 5f, 2.5f, 3f, dark)
            r(x + 8.5f, y + 5f, 2.5f, 3f, dark); r(x + 3.3f, y + 5.3f, 1f, 1f, Color.WHITE); r(x + 8.8f, y + 5.3f, 1f, 1f, Color.WHITE); r(x + 3f, y + 9f, 8f, 4f, c("#F9A8C0"))
            r(x + 4.5f, y + 10.3f, 1.3f, 1.3f, c("#BE185D")); r(x + 8.2f, y + 10.3f, 1.3f, 1.3f, c("#BE185D")); r(x + 3f, y + 16f, 3f, 3f, dark); r(x + 8f, y + 18f, 3f, 2f, dark)
        } else if (kind == 7) {
            // poussin jaune : petite houppe, bec orange, ailes
            r(x + 6f, y - 2f, 2f, 2f, skin); r(x + 5f, y - 1.2f, 1f, 1.2f, skin); r(x + 8f, y - 1.2f, 1f, 1.2f, skin); r(x + 3f, y + 5f, 2.5f, 3f, dark)
            r(x + 8.5f, y + 5f, 2.5f, 3f, dark); r(x + 3.3f, y + 5.3f, 1f, 1f, Color.WHITE); r(x + 8.8f, y + 5.3f, 1f, 1f, Color.WHITE); r(x + 5f, y + 8.5f, 4f, 2f, c("#F97316"))
            r(x + 5.5f, y + 10.5f, 3f, 1f, c("#EA580C")); r(x + 1f, y + 9f, 2f, 1.4f, c("#FF8FA3")); r(x + 11f, y + 9f, 2f, 1.4f, c("#FF8FA3")); r(x - 1f, y + 17f, 3f, 4f, skinD)
            r(x + 12f, y + 17f, 3f, 4f, skinD)
        } else if (kind == 8) {
            // renard orange : oreilles pointues, museau blanc, queue
            r(x, y - 3f, 3.5f, 3f, skin); r(x + 0.5f, y - 4f, 2f, 1f, skin); r(x + 1f, y - 2.5f, 1.5f, 2f, dark); r(x + 10.5f, y - 3f, 3.5f, 3f, skin)
            r(x + 11.5f, y - 4f, 2f, 1f, skin); r(x + 11.5f, y - 2.5f, 1.5f, 2f, dark); r(x, y + 8f, 14f, 4.5f, c("#FFF7ED")); r(x, y + 7f, 3f, 1f, c("#FFF7ED"))
            r(x + 11f, y + 7f, 3f, 1f, c("#FFF7ED")); r(x + 3f, y + 5f, 2.5f, 2.5f, dark); r(x + 8.5f, y + 5f, 2.5f, 2.5f, dark); r(x + 3.3f, y + 5.2f, 0.9f, 0.9f, Color.WHITE)
            r(x + 8.8f, y + 5.2f, 0.9f, 0.9f, Color.WHITE); r(x + 6f, y + 8f, 2f, 1.5f, dark); r(x + 6.5f, y + 10f, 1f, 1f, c("#B4232C")); r(x + 14f, y + 15f, 3f, 3f, skin)
            r(x + 16.5f, y + 12f, 2.5f, 4f, skin); r(x + 16.5f, y + 11f, 2.5f, 1.5f, c("#FFF7ED"))
        } else if (kind == 9) {
            // axolotl rose : branchies sur les cotes, grand sourire doux
            r(x - 3f, y + 2f, 3f, 1.5f, c("#EC4899")); r(x - 3.5f, y + 5f, 3.5f, 1.5f, c("#EC4899")); r(x - 3f, y + 8f, 3f, 1.5f, c("#EC4899")); r(x + 14f, y + 2f, 3f, 1.5f, c("#EC4899"))
            r(x + 14f, y + 5f, 3.5f, 1.5f, c("#EC4899")); r(x + 14f, y + 8f, 3f, 1.5f, c("#EC4899")); r(x + 2.5f, y + 5.5f, 2.2f, 2.2f, dark); r(x + 9.3f, y + 5.5f, 2.2f, 2.2f, dark)
            r(x + 2.7f, y + 5.7f, 0.8f, 0.8f, Color.WHITE); r(x + 9.5f, y + 5.7f, 0.8f, 0.8f, Color.WHITE); r(x + 4f, y + 10f, 6f, 0.8f, c("#BE185D")); r(x + 3.5f, y + 9.3f, 1f, 1f, c("#BE185D"))
            r(x + 9.5f, y + 9.3f, 1f, 1f, c("#BE185D")); r(x + 1f, y + 8.5f, 2f, 1.4f, c("#FF8FA3")); r(x + 11f, y + 8.5f, 2f, 1.4f, c("#FF8FA3")); r(x + 5f, y + 16f, 4f, 4f, c("#F9A8D4"))
        } else if (kind == 10) {
            // abeille : antennes, rayures, petites ailes
            r(x + 3f, y - 3f, 1f, 3f, dark); r(x + 2.5f, y - 3.8f, 2f, 1.2f, dark); r(x + 10f, y - 3f, 1f, 3f, dark); r(x + 9.5f, y - 3.8f, 2f, 1.2f, dark)
            r(x + 2f, y + 5f, 3f, 3.5f, dark); r(x + 9f, y + 5f, 3f, 3.5f, dark); r(x + 2.3f, y + 5.3f, 1f, 1f, Color.WHITE); r(x + 9.3f, y + 5.3f, 1f, 1f, Color.WHITE)
            r(x + 1f, y + 9.5f, 2f, 1.4f, c("#FF8FA3")); r(x + 11f, y + 9.5f, 2f, 1.4f, c("#FF8FA3")); r(x + 6f, y + 11f, 2f, 1f, c("#B4232C")); r(x + 2f, y + 16f, 10f, 1.5f, dark)
            r(x + 2f, y + 19f, 10f, 1.5f, dark); r(x - 3f, y + 11f, 3.5f, 3f, c("#E0F2FE")); r(x + 13.5f, y + 11f, 3.5f, 3f, c("#E0F2FE")); r(x - 3f, y + 11f, 3.5f, 0.8f, c("#BAE6FD"))
            r(x + 13.5f, y + 11f, 3.5f, 0.8f, c("#BAE6FD"))
        } else if (kind == 11) {
            // grenouille verte : gros yeux en haut, grande bouche
            r(x + 0.5f, y - 3f, 5f, 5f, skin); r(x + 8.5f, y - 3f, 5f, 5f, skin); r(x + 1.5f, y - 2f, 3f, 3.5f, Color.WHITE); r(x + 9.5f, y - 2f, 3f, 3.5f, Color.WHITE)
            r(x + 2.5f, y - 1f, 2f, 2f, dark); r(x + 10.5f, y - 1f, 2f, 2f, dark); r(x + 2.7f, y - 0.8f, 0.7f, 0.7f, Color.WHITE); r(x + 10.7f, y - 0.8f, 0.7f, 0.7f, Color.WHITE)
            r(x + 5.5f, y + 5f, 1f, 1f, skinD); r(x + 7.5f, y + 5f, 1f, 1f, skinD); r(x + 2f, y + 9f, 10f, 0.9f, c("#166534")); r(x + 1.5f, y + 8f, 1f, 1f, c("#166534"))
            r(x + 11.5f, y + 8f, 1f, 1f, c("#166534")); r(x + 1f, y + 10f, 2f, 1.4f, c("#FF8FA3")); r(x + 11f, y + 10f, 2f, 1.4f, c("#FF8FA3")); r(x + 4f, y + 15f, 6f, 5f, c("#BBF7D0"))
        } else if (kind == 12) {
            // chat gris : oreilles, rayures, moustaches, queue
            r(x, y - 2.5f, 3.5f, 2.5f, skin); r(x + 0.5f, y - 3.5f, 2f, 1f, skin); r(x + 1f, y - 2f, 1.5f, 1.5f, c("#F9A8D4")); r(x + 10.5f, y - 2.5f, 3.5f, 2.5f, skin)
            r(x + 11.5f, y - 3.5f, 2f, 1f, skin); r(x + 11.5f, y - 2f, 1.5f, 1.5f, c("#F9A8D4")); r(x + 5f, y, 1f, 2.5f, skinD); r(x + 8f, y, 1f, 2.5f, skinD)
            r(x + 6.5f, y, 1f, 1.5f, skinD); r(x + 2.5f, y + 5.5f, 3f, 3f, c("#84CC16")); r(x + 8.5f, y + 5.5f, 3f, 3f, c("#84CC16")); r(x + 3.5f, y + 5.5f, 1f, 3f, dark)
            r(x + 9.5f, y + 5.5f, 1f, 3f, dark); r(x + 6f, y + 9f, 2f, 1.2f, c("#F472B6")); r(x + 6.5f, y + 10.2f, 1f, 1f, dark); r(x - 2f, y + 9.5f, 4f, 0.5f, dark)
            r(x - 2f, y + 11f, 4f, 0.5f, dark); r(x + 12f, y + 9.5f, 4f, 0.5f, dark); r(x + 12f, y + 11f, 4f, 0.5f, dark); r(x + 14f, y + 17f, 3f, 2f, skin)
            r(x + 16f, y + 13f, 2f, 5f, skin); r(x + 16f, y + 13f, 2f, 1.5f, skinD)
        } else if (kind == 13) {
            // petit cube de gelee vert translucide : coeur plus fonce, reflets
            r(x + 2f, y + 2f, 10f, 10f, c("#4ADE80")); r(x + 1f, y + 1f, 3f, 1f, Color.WHITE); r(x + 1f, y + 2f, 1f, 2f, Color.WHITE); r(x + 11f, y + 10f, 1.5f, 1.5f, c("#BBF7D0"))
            r(x + 3.5f, y + 5f, 2.5f, 2.5f, dark); r(x + 8f, y + 5f, 2.5f, 2.5f, dark); r(x + 3.7f, y + 5.2f, 0.9f, 0.9f, Color.WHITE); r(x + 8.2f, y + 5.2f, 0.9f, 0.9f, Color.WHITE)
            r(x + 6f, y + 9.5f, 2f, 1f, dark); r(x + 2f, y + 8.5f, 1.8f, 1.2f, c("#FF8FA3")); r(x + 10.2f, y + 8.5f, 1.8f, 1.2f, c("#FF8FA3")); r(x + 2f, y + 13.5f, 1.5f, 1.5f, skinD)
            r(x + 10f, y + 13.5f, 1.5f, 2f, skinD)
        } else if (kind == 14) {
            // villageois : cheveux courts, gros nez, robe marron + ceinture
            r(x, y - 0.5f, 14f, 3f, c("#5B3A1E")); r(x, y + 2f, 1.5f, 2.5f, c("#5B3A1E")); r(x + 12.5f, y + 2f, 1.5f, 2.5f, c("#5B3A1E")); r(x + 2f, y + 5f, 4f, 3f, Color.WHITE)
            r(x + 8f, y + 5f, 4f, 3f, Color.WHITE); r(x + 3.5f, y + 5.5f, 2f, 2f, c("#16A34A")); r(x + 8.5f, y + 5.5f, 2f, 2f, c("#16A34A")); r(x + 2f, y + 4f, 3.5f, 0.8f, c("#5B3A1E"))
            r(x + 8.5f, y + 4f, 3.5f, 0.8f, c("#5B3A1E")); r(x + 5.5f, y + 7f, 3f, 5f, skinD); r(x + 5.5f, y + 7f, 3f, 1f, c("#C4916A")); r(x + 1f, y + 9.5f, 2f, 1.4f, c("#FF8FA3"))
            r(x + 11f, y + 9.5f, 2f, 1.4f, c("#FF8FA3")); r(x + 2f, y + 18f, 10f, 1f, c("#FACC15"))
        } else if (kind == 15) {
            // petit robot gentil : antenne, visiere, yeux lumineux
            r(x + 6.5f, y - 3f, 1f, 3f, c("#64748B")); r(x + 6f, y - 4f, 2f, 1.5f, c("#F87171")); r(x - 1f, y + 6f, 1.5f, 2f, c("#64748B")); r(x + 13.5f, y + 6f, 1.5f, 2f, c("#64748B"))
            r(x + 1.5f, y + 4.5f, 11f, 4.5f, c("#1E293B")); r(x + 3f, y + 5.5f, 3f, 2.5f, c("#22D3EE")); r(x + 8f, y + 5.5f, 3f, 2.5f, c("#22D3EE")); r(x + 3f, y + 5.5f, 1f, 1f, Color.WHITE)
            r(x + 8f, y + 5.5f, 1f, 1f, Color.WHITE); r(x + 4.5f, y + 10.5f, 5f, 1.5f, c("#64748B")); r(x + 5.5f, y + 10.5f, 0.6f, 1.5f, skin); r(x + 7f, y + 10.5f, 0.6f, 1.5f, skin)
            r(x + 8.5f, y + 10.5f, 0.6f, 1.5f, skin); r(x + 1f, y + 10f, 2f, 1.2f, c("#FF8FA3")); r(x + 11f, y + 10f, 2f, 1.2f, c("#FF8FA3")); r(x + 6f, y + 16f, 2f, 2f, c("#22D3EE"))
        }
        if (celebrate) {
            // Grand sourire
            r(x + 4f, y + 10.2f, 6f, 3f, c("#B4232C")); r(x + 4.6f, y + 10.2f, 4.8f, 1.1f, Color.WHITE)
        } else {
            // Goutte de sueur
            r(x + 15.5f, y + 4f, 1.5f, 3f, c("#38BDF8")); r(x + 15.8f, y + 3f, 0.9f, 1f, c("#38BDF8"))
        }
    }

    private fun drawToilet(x: Float, y: Float, s: Float) {
        val line = c("#8A94A6"); val white = c("#F8FAFC"); val shade = c("#DDE3EC")
        val water = c("#7DD3FC")
        // Panneau WC
        r(x + 1f, y - 11f, 12f, 7f, c("#1D4ED8")); r(x + 1f, y - 11f, 12f, 1f, c("#3B82F6"))
        text.color = Color.WHITE
        text.textSize = 5.2f * u
        canvas.drawText("WC", ox + (x + 7f) * u, oy + (y - 5.4f) * u, text)
        // Reservoir
        r(x - 0.8f, y - 0.8f, 14.6f, 11.6f, line)
        r(x, y, 13f, 10f, white); r(x + 10f, y, 3f, 10f, shade); r(x, y, 13f, 1.5f, shade)
        r(x + 10.2f, y + 3f, 2f, 2f, c("#FACC15"))
        // Siege + cuvette
        r(x - 4.8f, y + 9.2f, 22.6f, 4.6f, line)
        r(x - 4f, y + 10f, 21f, 3f, white)
        r(x - 3.8f, y + 13.2f, 20.6f, 7.6f, line)
        r(x - 3f, y + 14f, 19f, 6f, white); r(x - 3f, y + 18f, 19f, 2f, shade)
        r(x - 1.5f, y + 14.6f, 16f, 2.6f, water)
        // Pied
        r(x + 0.2f, y + 20.4f, 12.6f, 2.2f, line); r(x + 1f, y + 20.4f, 11f, 1.6f, white)
        // Scintillements autour (attirent l'oeil)
        val sp = if (s == 0f) c("#FFD54F") else Color.WHITE
        r(x - 6f, y + 2f, 2f, 2f, sp); r(x + 17f, y + 6f, 2f, 2f, sp)
        r(x + 15f, y - 2f, 1.5f, 1.5f, sp); r(x - 4f, y - 4f, 1.5f, 1.5f, sp)
    }

    private fun drawArrow(s: Float) {
        val yellow = c("#FFD54F"); val edge = c("#B7791F")
        val ax = 25f + s * 2f
        val ay = 22f
        r(ax - 0.7f, ay - 0.7f, 10.4f, 5.4f, edge)
        r(ax, ay, 9f, 4f, yellow)
        for (i in 0..5) {
            r(ax + 9f + i, ay - 3f + i * 0.6f, 1f, 10f - i * 2f, yellow)
        }
    }

    private fun drawBubble(s: Float) {
        val bx = 23f; val by = 3f
        r(bx - 0.8f, by - 0.8f, 11.6f, 9.6f, c("#374151"))
        r(bx, by, 10f, 8f, Color.WHITE)
        r(bx + 1f, by + 8f, 2.5f, 2.5f, c("#374151")); r(bx + 1f, by + 7.6f, 1.6f, 1.6f, Color.WHITE)
        text.color = c("#DC2626")
        text.textSize = 7f * u
        canvas.drawText(if (s == 0f) "!" else "!!", ox + (bx + 5f) * u, oy + (by + 6.5f) * u, text)
    }

    private fun drawDiamonds() {
        for (i in 0 until 5) {
            val x = 37f + i * 5.2f
            val y = 35f
            val on = i < diamonds || (celebrate && chest)
            val fill = if (on) c("#22D3EE") else c("#9CA3AF")
            val edge = if (on) c("#0E7490") else c("#6B7280")
            r(x + 0.6f, y - 0.6f, 2.8f, 1.6f, edge); r(x - 0.6f, y + 0.6f, 5.2f, 3.2f, edge); r(x + 0.6f, y + 3.2f, 2.8f, 1.6f, edge)
            r(x + 1f, y, 2f, 1f, fill); r(x, y + 1f, 4f, 2f, fill); r(x + 1f, y + 3f, 2f, 1f, fill)
            if (on) r(x + 0.6f, y + 1.1f, 1f, 1f, Color.WHITE)
        }
    }

    private fun drawChest() {
        val gold = c("#FFD54F")
        r(25.6f, 27.6f, 12.8f, 7.2f, c("#5B3A1E"))
        r(26f, 28f, 12f, 6.4f, c("#8B5A2B")); r(26f, 28f, 12f, 1.2f, c("#B07A3A"))
        r(31f, 28f, 2f, 3f, gold)
        r(26f, 24f, 12f, 3f, c("#6E4421"))
        r(27f, 25.6f, 10f, 2.4f, gold); r(29f, 24.6f, 6f, 1.4f, Color.WHITE)
    }

    private fun drawConfetti() {
        val tt = (System.currentTimeMillis() / 40).toInt()
        val colors = intArrayOf(c("#F87171"), c("#FACC15"), c("#4ADE80"), c("#60A5FA"), c("#C084FC"), c("#FB923C"))
        for (i in 0 until 46) {
            val x = (i * 37 % 64).toFloat()
            val y = ((i * 13 + tt * (1 + i % 3)) % 46).toFloat() - 4f
            r(x, y, 1.6f, 1.6f, colors[i % colors.size])
        }
    }

    private fun drawProgress() {
        val n = 12
        val filled = (progress * n).toInt()
        r(3.4f, 35.2f, 31.6f, 4f, c("#111827"))
        for (i in 0 until n) {
            val col = if (i < filled) c("#4ADE4A") else c("#3F4756")
            r(4f + i * 2.6f, 35.8f, 2.2f, 2.8f, col)
            if (i < filled) r(4f + i * 2.6f, 35.8f, 2.2f, 0.8f, c("#9BF59B"))
        }
    }
}
