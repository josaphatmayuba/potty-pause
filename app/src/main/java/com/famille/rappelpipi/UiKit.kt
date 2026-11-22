package com.famille.rappelpipi

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Kit d'interface unique (reglages + futur mode parent). Toutes les valeurs sont definies ICI, une seule fois :
 *  - echelle d'espacement : S4 / S8 / S12 / S16 / S24 (dp) ;
 *  - tailles de texte : T_TITLE 24, T_SECTION 18, T_BODY 16 (champs, boutons, statuts), T_HELP 14 (libelles, aides) ;
 *  - hauteur unique des champs, listes et boutons : CONTROL_H = 48 dp ; message multiligne = 2 x CONTROL_H.
 * Clair / sombre selon le theme du systeme ; contrastes AA.
 */
class UiKit(val ctx: Context) {
    companion object {
        const val S4 = 4
        const val S8 = 8
        const val S12 = 12
        const val S16 = 16
        const val S24 = 24

        const val T_TITLE = 24f
        const val T_SECTION = 18f
        const val T_BODY = 16f
        const val T_HELP = 14f

        const val CONTROL_H = 48
        const val CARD_R = 16
        const val CONTROL_R = 12
        const val MAX_W = 560
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }

    enum class Kind { PRIMARY, SECONDARY, DANGER }

    class Palette(
        val bg: Int, val card: Int, val text: Int, val textSec: Int, val primary: Int, val onPrimary: Int,
        val outline: Int, val ctrl: Int, val field: Int, val ok: Int, val warn: Int, val danger: Int, val ripple: Int
    )

    private fun h(s: String) = Color.parseColor(s)

    val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    val c: Palette = if (night) Palette(
        bg = h("#121316"), card = h("#1E2025"), text = h("#ECEDEE"), textSec = h("#B6BCC6"),
        primary = h("#8AB4F8"), onPrimary = h("#0B1B33"), outline = h("#3A3F47"), ctrl = h("#8B93A0"),
        field = h("#262A31"), ok = h("#6FCF7F"), warn = h("#F5A04A"), danger = h("#F28B82"), ripple = 0x33FFFFFF
    ) else Palette(
        bg = h("#F4F5F7"), card = h("#FFFFFF"), text = h("#1F2328"), textSec = h("#4B5563"),
        primary = h("#1A56DB"), onPrimary = h("#FFFFFF"), outline = h("#D8DDE5"), ctrl = h("#6B7280"),
        field = h("#F9FAFB"), ok = h("#1B6E2D"), warn = h("#B45309"), danger = h("#B42318"), ripple = 0x331A56DB
    )

    fun dp(v: Int) = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    private fun shape(fill: Int, radius: Int, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke > 0) setStroke(dp(stroke), strokeColor)
        }

    private fun ripple(bg: Drawable) = RippleDrawable(ColorStateList.valueOf(c.ripple), bg, null)

    // ---------- Textes ----------
    private fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
        setLineSpacing(0f, 1.15f)
    }

    fun title(t: String) = tv(t, T_TITLE, c.text, true)
    fun section(t: String) = tv(t, T_SECTION, c.text, true)
    fun label(t: String) = tv(t, T_HELP, c.text, true)
    fun body(t: String) = tv(t, T_BODY, c.text)
    fun help(t: String) = tv(t, T_HELP, c.textSec)
    fun status(t: String) = tv(t, T_BODY, c.text, true)

    // ---------- Champs ----------
    fun field(hint: String, value: String, number: Boolean, multi: Boolean = false): EditText = EditText(ctx).apply {
        setText(value)
        this.hint = hint
        textSize = T_BODY
        setTextColor(c.text); setHintTextColor(c.textSec)
        background = shape(c.field, CONTROL_R, 1, c.ctrl)
        if (multi) {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP
            minHeight = dp(2 * CONTROL_H)
            setPadding(dp(S16), dp(S12), dp(S16), dp(S12))
        } else {
            if (number) inputType = InputType.TYPE_CLASS_NUMBER else setSingleLine(true)
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(CONTROL_H)
            setPadding(dp(S16), 0, dp(S16), 0)
        }
    }

    fun spinner(items: List<String>, selected: Int): Spinner {
        val ad = object : ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_item, items) {
            private fun style(v: TextView, drop: Boolean) {
                v.textSize = T_BODY
                v.setTextColor(c.text)
                v.minHeight = dp(CONTROL_H)
                v.gravity = Gravity.CENTER_VERTICAL
                if (drop) { v.setPadding(dp(S16), 0, dp(S16), 0); v.setBackgroundColor(c.card) }
                else { v.setPadding(0, 0, 0, 0); v.maxLines = 1; v.ellipsize = android.text.TextUtils.TruncateAt.END }
            }
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).also { style(it, false) }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getDropDownView(position, convertView, parent) as TextView).also { style(it, true) }
        }
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return Spinner(ctx).apply {
            adapter = ad
            setSelection(selected.coerceAtLeast(0))
            val arrow = ctx.getDrawable(R.drawable.ic_spinner_arrow)!!.mutate().apply { setTint(c.textSec) }
            val ld = LayerDrawable(arrayOf(shape(c.field, CONTROL_R, 1, c.ctrl), arrow))
            ld.setLayerSize(1, dp(24), dp(24))
            ld.setLayerGravity(1, Gravity.END or Gravity.CENTER_VERTICAL)
            ld.setLayerInsetEnd(1, dp(S12))
            background = ld
            setPadding(dp(S16), 0, dp(S16 + S24 + S8), 0)
            setPopupBackgroundDrawable(shape(c.card, CONTROL_R, 1, c.outline))
        }
    }

    fun checkbox(t: String, checked: Boolean): CheckBox = CheckBox(ctx).apply {
        text = t; isChecked = checked
        textSize = T_BODY; setTextColor(c.text)
        buttonTintList = ColorStateList.valueOf(c.primary)
        minHeight = dp(CONTROL_H)
    }

    // ---------- Boutons ----------
    private class UiButton(ctx: Context) : Button(ctx) {
        override fun setEnabled(enabled: Boolean) {
            super.setEnabled(enabled)
            alpha = if (enabled) 1f else 0.45f
        }
    }

    fun button(t: String, kind: Kind = Kind.SECONDARY): Button = UiButton(ctx).apply {
        text = t
        isAllCaps = false
        stateListAnimator = null
        elevation = 0f
        textSize = T_BODY
        setTypeface(null, Typeface.BOLD)
        minWidth = 0; minimumWidth = 0
        minHeight = dp(CONTROL_H); minimumHeight = dp(CONTROL_H)
        gravity = Gravity.CENTER
        setPadding(dp(S16), 0, dp(S16), 0)
        when (kind) {
            Kind.PRIMARY -> { setTextColor(c.onPrimary); background = ripple(shape(c.primary, CONTROL_R)) }
            Kind.SECONDARY -> { setTextColor(c.primary); background = ripple(shape(Color.TRANSPARENT, CONTROL_R, 2, c.primary)) }
            Kind.DANGER -> { setTextColor(c.danger); background = ripple(shape(Color.TRANSPARENT, CONTROL_R, 2, c.danger)) }
        }
    }

    // ---------- Mise en page ----------
    fun card(title: String?): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(c.card, CARD_R, 1, c.outline)
        setPadding(dp(S16), dp(S16), dp(S16), dp(S16))
        if (title != null) addView(section(title))
    }

    fun column(): LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    /** Ajoute v dans parent, pleine largeur, avec une marge haute prise dans l'echelle ; h = hauteur en dp (0 = auto). */
    fun add(parent: LinearLayout, v: View, top: Int = S12, h: Int = 0): View {
        parent.addView(v, LinearLayout.LayoutParams(MATCH, if (h > 0) dp(h) else WRAP).apply { topMargin = dp(top) })
        return v
    }

    /** Deux (ou plus) controles de meme largeur sur une ligne, meme hauteur CONTROL_H. */
    fun row(vararg views: View): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEachIndexed { i, v ->
            addView(v, LinearLayout.LayoutParams(0, dp(CONTROL_H), 1f).apply { if (i > 0) marginStart = dp(S8) })
        }
    }

    /** Ecran : contenu defilant centre (largeur max MAX_W) + barre d'action fixe en bas (facultative). */
    fun screen(a: Activity, content: LinearLayout, bottom: View?) {
        val tablet = ctx.resources.configuration.smallestScreenWidthDp >= 600
        val pad = dp(if (tablet) S24 else S16)
        val maxW = minOf(dp(MAX_W), ctx.resources.displayMetrics.widthPixels)
        content.setPadding(pad, pad, pad, dp(S24))
        val frame = FrameLayout(ctx).apply {
            addView(content, FrameLayout.LayoutParams(maxW, WRAP, Gravity.CENTER_HORIZONTAL))
        }
        val scroll = ScrollView(ctx).apply { isFillViewport = true; addView(frame) }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(c.bg)
            addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
        var barRef: LinearLayout? = null
        if (bottom != null) {
            val bar = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(c.card)
                addView(View(ctx).apply { setBackgroundColor(c.outline) }, LinearLayout.LayoutParams(MATCH, dp(1)))
                val inner = FrameLayout(ctx).apply {
                    setPadding(pad, dp(S12), pad, dp(S12))
                    addView(bottom, FrameLayout.LayoutParams(MATCH, dp(CONTROL_H)))
                }
                addView(inner, LinearLayout.LayoutParams(maxW, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })
            }
            root.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
            barRef = bar
        }
        // Android 15+ (targetSdk 35+) : l'ecran passe sous les barres systeme. On rend les marges
        // (barre d'etat, de navigation, decoupe, clavier) : haut/cotes sur la racine, bas dans la barre fixe.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(sys.bottom, ime.bottom)
            v.setPadding(sys.left, sys.top, sys.right, if (barRef == null) bottomInset else 0)
            barRef?.setPadding(0, 0, 0, bottomInset)
            insets
        }
        a.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        a.setContentView(root)
        val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(a.window, root).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        ViewCompat.requestApplyInsets(root)
    }
}
