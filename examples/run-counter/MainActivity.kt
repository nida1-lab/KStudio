package com.example.kstudiorun

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var count = 0
        val result = TextView(this).apply { text = "Count: 0"; textSize = 28f }
        val button = Button(this).apply {
            text = "ADD"
            setOnClickListener { count++; result.text = "Count: $count" }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 24)
            addView(result); addView(button)
        })
    }
}
