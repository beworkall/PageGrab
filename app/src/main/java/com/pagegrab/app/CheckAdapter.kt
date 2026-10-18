package com.pagegrab.app

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView

/** Dark-themed checkable list adapter — the stock Android white-on-white list looks broken on our theme. */
class CheckAdapter(ctx: Context, private val rows: List<String>) :
    ArrayAdapter<String>(ctx, android.R.layout.simple_list_item_multiple_choice, rows) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val ctv = (convertView as? CheckedTextView) ?: CheckedTextView(
            context, null, android.R.attr.checkedTextViewStyle
        ).apply {
            setPadding(UI.dp(context, 14), UI.dp(context, 12), UI.dp(context, 14), UI.dp(context, 12))
            checkMarkTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(UI.ACCENT))
            gravity = Gravity.CENTER_VERTICAL
        }
        ctv.text = rows[position]
        ctv.setTextColor(Color.parseColor(UI.TEXT))
        ctv.textSize = 14f
        ctv.setBackgroundColor(Color.parseColor(if (position % 2 == 0) UI.SURFACE else UI.SURFACE_2))
        return ctv
    }
}
