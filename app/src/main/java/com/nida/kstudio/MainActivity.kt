package com.nida.kstudio

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var codeEditor: EditText
    private lateinit var runButton: Button
    private lateinit var previewContainer: android.widget.FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        codeEditor = findViewById(R.id.codeEditor)
        runButton = findViewById(R.id.runButton)
        previewContainer = findViewById(R.id.previewContainer)

        // Default Code
        codeEditor.setText(
            """
            fun main() {
                println("Hello, KStudio!")
            }
            """.trimIndent()
        )

        // Run
        runButton.setOnClickListener {
            updatePreview()
        }

        updatePreview()
    }

    private fun updatePreview() {
        previewContainer.removeAllViews()

        val preview = TextView(this).apply {
            text = "Preview\n\n" +
                    "KStudio is running!\n\n" +
                    "Code length: ${codeEditor.text.length}"
            textSize = 16f
            setPadding(24, 24, 24, 24)
        }

        previewContainer.addView(preview)
    }
}
