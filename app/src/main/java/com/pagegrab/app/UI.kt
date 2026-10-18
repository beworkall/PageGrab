package com.pagegrab.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

/** Shared look for the whole app — one dark theme, one accent, rounded cards/buttons everywhere. */
object UI {
    const val BG = "#0E0F13"
    const val SURFACE = "#1A1C22"
    const val SURFACE_2 = "#22252C"
    const val ACCENT = "#5B8CFF"
    const val ACCENT_DIM = "#3A4A7A"
    const val TEXT = "#F2F3F5"
    const val TEXT_DIM = "#9AA0AC"
    const val DANGER = "#FF5B6E"
    const val OK = "#3DD68C"

    fun dp(ctx: Context, n: Int) = (n * ctx.resources.displayMetrics.density).toInt()

    private fun rounded(color: String, radius: Float, strokeColor: String? = null): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(Color.parseColor(color))
        d.cornerRadius = radius
        if (strokeColor != null) d.setStroke(2, Color.parseColor(strokeColor))
        return d
    }

    fun card(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(SURFACE, dp(ctx, 14).toFloat())
        setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(ctx, 8)
        layoutParams = lp
    }

    fun title(ctx: Context, text: String, size: Float = 24f): TextView = TextView(ctx).apply {
        this.text = text
        textSize = size
        setTextColor(Color.parseColor(TEXT))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    fun label(ctx: Context, text: String, dim: Boolean = false): TextView = TextView(ctx).apply {
        this.text = text
        textSize = if (dim) 12.5f else 14.5f
        setTextColor(Color.parseColor(if (dim) TEXT_DIM else TEXT))
    }

    fun heading(ctx: Context, text: String): TextView = TextView(ctx).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.parseColor(ACCENT))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(ctx, 18), 0, dp(ctx, 6))
        letterSpacing = 0.04f
    }

    /** Primary filled button (accent color). */
    fun primaryButton(ctx: Context, text: String, click: () -> Unit): Button = Button(ctx).apply {
        this.text = text
        isAllCaps = true
        setTextColor(Color.WHITE)
        background = rounded(ACCENT, dp(ctx, 24).toFloat())
        setPadding(dp(ctx, 18), dp(ctx, 10), dp(ctx, 18), dp(ctx, 10))
        setOnClickListener { click() }
        stateListAnimator = null
    }

    /** Secondary outline-style button. */
    fun secondaryButton(ctx: Context, text: String, click: () -> Unit): Button = Button(ctx).apply {
        this.text = text
        isAllCaps = true
        setTextColor(Color.parseColor(TEXT))
        background = rounded(SURFACE_2, dp(ctx, 24).toFloat(), ACCENT_DIM)
        setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10))
        setOnClickListener { click() }
        stateListAnimator = null
    }

    fun iconButton(ctx: Context, glyph: String, click: () -> Unit): TextView = TextView(ctx).apply {
        text = glyph
        textSize = 17f
        setTextColor(Color.parseColor(TEXT))
        gravity = Gravity.CENTER
        background = rounded(SURFACE_2, dp(ctx, 20).toFloat())
        val size = dp(ctx, 40)
        val lp = LinearLayout.LayoutParams(size, size)
        lp.marginEnd = dp(ctx, 6)
        layoutParams = lp
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }

    fun pill(ctx: Context, text: String, color: String = ACCENT): TextView = TextView(ctx).apply {
        this.text = text
        textSize = 11f
        setTextColor(Color.WHITE)
        background = rounded(color, dp(ctx, 10).toFloat())
        setPadding(dp(ctx, 8), dp(ctx, 2), dp(ctx, 8), dp(ctx, 2))
    }

    fun styledEditText(ctx: Context, hint: String): EditText = EditText(ctx).apply {
        this.hint = hint
        setHintTextColor(Color.parseColor(TEXT_DIM))
        setTextColor(Color.parseColor(TEXT))
        background = rounded(SURFACE_2, dp(ctx, 12).toFloat())
        setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
    }

    /** Fully rounded "pill" background, used for the Chrome-style browser address bar. */
    fun pillBackground(ctx: Context): GradientDrawable = rounded(SURFACE_2, dp(ctx, 24).toFloat())

    fun applyWindowBg(root: View) {
        root.setBackgroundColor(Color.parseColor(BG))
    }

    fun divider(ctx: Context): View = View(ctx).apply {
        setBackgroundColor(Color.parseColor(SURFACE_2))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1))
        lp.topMargin = dp(ctx, 10); lp.bottomMargin = dp(ctx, 4)
        layoutParams = lp
    }
}
