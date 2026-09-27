package com.nida.kstudio

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import java.util.regex.Pattern

class MainActivity : Activity() {

    private lateinit var codeEditor: EditText
    private lateinit var lineNumbers: TextView
    private lateinit var searchInput: EditText
    private lateinit var lineInput: EditText
    private lateinit var previewContainer: FrameLayout

    private var changingText = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        codeEditor = findViewById(R.id.codeEditor)
        lineNumbers = findViewById(R.id.lineNumbers)
        searchInput = findViewById(R.id.searchInput)
        lineInput = findViewById(R.id.lineInput)
        previewContainer = findViewById(R.id.previewContainer)

        val runButton = findViewById<Button>(R.id.runButton)
        val searchButton = findViewById<Button>(R.id.searchButton)
        val lineButton = findViewById<Button>(R.id.lineButton)

        // Default Code

        codeEditor.setText(
            """
            fun main() {
                println("Hello, KStudio!")
            }
            """.trimIndent()
        )

        updateLineNumbers()
        highlightCode()

        // Text Change

        codeEditor.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                updateLineNumbers()
            }

            override fun afterTextChanged(s: Editable?) {
                if (!changingText) {
                    highlightCode()
                }
            }
        })

        // Auto Brackets

        codeEditor.setOnKeyListener { _, keyCode, event ->

            if (event.action != android.view.KeyEvent.ACTION_DOWN) {
                return@setOnKeyListener false
            }

            val cursor = codeEditor.selectionStart

            if (cursor < 0) {
                return@setOnKeyListener false
            }

            val pair = when (keyCode) {
                android.view.KeyEvent.KEYCODE_LEFT_BRACKET -> "{}"
                android.view.KeyEvent.KEYCODE_9 -> "()"
                android.view.KeyEvent.KEYCODE_LEFT_BRACKET -> "{}"
                else -> null
            }

            false
        }

        // Run

        runButton.setOnClickListener {
            updatePreview()
        }

        // Search

        searchButton.setOnClickListener {
            searchCode()
        }

        // Line Jump

        lineButton.setOnClickListener {
            jumpToLine()
        }

        updatePreview()
    }

    // Line Numbers

    private fun updateLineNumbers() {

        val lines = codeEditor.text.toString().split("\n")

        val builder = StringBuilder()

        for (i in lines.indices) {
            builder.append(i + 1)

            if (i < lines.lastIndex) {
                builder.append("\n")
            }
        }

        lineNumbers.text = builder.toString()
    }

    // Search

    private fun searchCode() {

        val keyword = searchInput.text.toString()

        if (keyword.isEmpty()) {
            return
        }

        val text = codeEditor.text.toString()

        val start = codeEditor.selectionEnd.coerceAtLeast(0)

        var index = text.indexOf(keyword, start)

        if (index == -1) {
            index = text.indexOf(keyword)
        }

        if (index != -1) {
            codeEditor.requestFocus()
            codeEditor.setSelection(
                index,
                index + keyword.length
            )
        }
    }

    // Line Jump

    private fun jumpToLine() {

        val line = lineInput.text.toString().toIntOrNull()

        if (line == null || line < 1) {
            return
        }

        val text = codeEditor.text.toString()

        var currentLine = 1
        var position = 0

        while (currentLine < line && position < text.length) {

            if (text[position] == '\n') {
                currentLine++
            }

            position++
        }

        codeEditor.requestFocus()
        codeEditor.setSelection(position.coerceAtMost(text.length))
    }

    // Syntax Highlight

    private fun highlightCode() {

        if (changingText) {
            return
        }

        val text = codeEditor.text.toString()

        val spannable = SpannableString(text)

        // Reset

        spannable.setSpan(
            ForegroundColorSpan(Color.BLACK),
            0,
            spannable.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Keywords

        val keywords = arrayOf(
            "fun",
            "val",
            "var",
            "class",
            "object",
            "if",
            "else",
            "when",
            "for",
            "while",
            "return",
            "import",
            "package",
            "private",
            "public",
            "protected",
            "override",
            "true",
            "false",
            "null"
        )

        for (keyword in keywords) {

            val pattern = Pattern.compile("\\b$keyword\\b")
            val matcher = pattern.matcher(text)

            while (matcher.find()) {

                spannable.setSpan(
                    ForegroundColorSpan(Color.rgb(150, 60, 180)),
                    matcher.start(),
                    matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // Strings

        val stringPattern = Pattern.compile("\"[^\"]*\"")
        val stringMatcher = stringPattern.matcher(text)

        while (stringMatcher.find()) {

            spannable.setSpan(
                ForegroundColorSpan(Color.rgb(40, 140, 70)),
                stringMatcher.start(),
                stringMatcher.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Comments

        val commentPattern = Pattern.compile("//.*")
        val commentMatcher = commentPattern.matcher(text)

        while (commentMatcher.find()) {

            spannable.setSpan(
                ForegroundColorSpan(Color.GRAY),
                commentMatcher.start(),
                commentMatcher.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val selectionStart = codeEditor.selectionStart
        val selectionEnd = codeEditor.selectionEnd

        changingText = true

        codeEditor.setText(spannable)

        val safeStart = selectionStart.coerceIn(0, codeEditor.length())
        val safeEnd = selectionEnd.coerceIn(0, codeEditor.length())

        codeEditor.setSelection(safeStart, safeEnd)

        changingText = false
    }

    // Preview

    private fun updatePreview() {

        previewContainer.removeAllViews()

        val preview = TextView(this).apply {

            text =
                "KStudio is running!\n\n" +
                "Code length: ${codeEditor.text.length}"

            textSize = 16f

            setPadding(
                24,
                24,
                24,
                24
            )
        }

        previewContainer.addView(preview)
    }
}
