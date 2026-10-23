package com.example.kstudiorun

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var count = 0
        setContentView(R.layout.activity_main)
        val result = findViewById<TextView>(R.id.counter)
        findViewById<Button>(R.id.add).setOnClickListener { count++; result.text = "Count: $count" }
    }
}
