package com.pagegrab.app

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class HistoryActivity : Activity() {
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(12))
        }
        root.addView(TextView(this).apply {
            text = "HISTORY"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        })

        val list = ListView(this)
        fun refresh() {
            val entries = History.all(this)
            list.adapter = ArrayAdapter(
                this, android.R.layout.simple_list_item_2, android.R.id.text1,
                entries
            ).apply {
                // simple two-line rendering
            }
            val titles = entries.map { "${if (it.ok) "✓" else "✗"} ${it.type} • ${it.savedAt}\n${it.title}" }
            list.adapter = ArrayAdapter(this@HistoryActivity, android.R.layout.simple_list_item_1, titles)
        }
        refresh()
        root.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(Button(this).apply {
            text = "CLEAR HISTORY"
            setOnClickListener {
                History.clear(this@HistoryActivity)
                refresh()
                Toast.makeText(this@HistoryActivity, "HISTORY CLEARED", Toast.LENGTH_SHORT).show()
            }
        })

        setContentView(root)
    }
}
