package com.nida.kstudio

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import java.util.regex.Pattern

class MainActivity : Activity() {

    private lateinit var codeEditor: EditText
    private lateinit var lineNumbers: LineNumberView
    private lateinit var searchInput: EditText
    private lateinit var lineInput: EditText
    private lateinit var previewContainer: FrameLayout

    private var highlighting = false
    private var autoPairing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        codeEditor = findViewById(R.id.codeEditor)
        lineNumbers = findViewById(R.id.lineNumbers)
        searchInput = findViewById(R.id.searchInput)
        lineInput = findViewById(R.id.lineInput)
        previewContainer = findViewById(R.id.previewContainer)

        val runButton =
            findViewById<Button>(R.id.runButton)

        val searchButton =
            findViewById<Button>(R.id.searchButton)

        val lineButton =
            findViewById<Button>(R.id.lineButton)

        // Editor

        codeEditor.setText("")

        lineNumbers.setEditor(codeEditor)

        codeEditor.addTextChangedListener(
            object : TextWatcher {

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
                    lineNumbers.invalidate()
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {
                    if (!highlighting && !autoPairing) {
                        highlightCode()
                    }

                    lineNumbers.invalidate()
                }
            }
        )

        // Auto Pair

        codeEditor.setOnKeyListener { _, keyCode, event ->

            if (event.action != KeyEvent.ACTION_DOWN) {
                return@setOnKeyListener false
            }

            if (keyCode == KeyEvent.KEYCODE_DEL) {

                return@setOnKeyListener handleDeletePair()
            }

            val pair =
                when (event.unicodeChar.toChar()) {
                    '{' -> '}'
                    '(' -> ')'
                    '[' -> ']'
                    '"' -> '"'
                    '\'' -> '\''
                    else -> null
                }

            if (pair != null) {

                insertPair(
                    event.unicodeChar.toChar(),
                    pair
                )

                true
            } else {
                false
            }
        }

        // Scroll

        codeEditor.viewTreeObserver
            .addOnScrollChangedListener {
                lineNumbers.invalidate()
            }

        // Layout

        codeEditor.viewTreeObserver
            .addOnGlobalLayoutListener {
                lineNumbers.invalidate()
            }

        // Run

        runButton.setOnClickListener {
            updatePreview()
        }

        // Search

        searchButton.setOnClickListener {
            searchCode()
        }

        searchInput.setOnEditorActionListener {
                _, actionId, event ->

            val enter =
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER

            if (enter) {
                searchCode()
                true
            } else {
                false
            }
        }

        // Line Jump

        lineButton.setOnClickListener {
            jumpToLine()
        }

        lineInput.setOnEditorActionListener {
                _, actionId, event ->

            val enter =
                actionId == EditorInfo.IME_ACTION_GO ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER

            if (enter) {
                jumpToLine()
                true
            } else {
                false
            }
        }

        // Initial

        lineNumbers.invalidate()
        updatePreview()
    }

    // Auto Pair

    private fun insertPair(
        open: Char,
        close: Char
    ) {

        val start =
            codeEditor.selectionStart

        val end =
            codeEditor.selectionEnd

        if (start < 0 || end < 0) {
            return
        }

        val selected =
            codeEditor.text
                .subSequence(start, end)
                .toString()

        val replacement =
            if (selected.isEmpty()) {
                "$open$close"
            } else {
                "$open$selected$close"
            }

        autoPairing = true

        codeEditor.text.replace(
            start,
            end,
            replacement
        )

        autoPairing = false

        val cursorPosition =
            if (selected.isEmpty()) {
                start + 1
            } else {
                start + replacement.length
            }

        codeEditor.setSelection(
            cursorPosition.coerceAtMost(
                codeEditor.length()
            )
        )

        highlightCode()
        lineNumbers.invalidate()
    }

    // Delete Pair

    private fun handleDeletePair(): Boolean {

        val start =
            codeEditor.selectionStart

        val end =
            codeEditor.selectionEnd

        if (start < 0 || end < 0) {
            return false
        }

        if (start != end) {
            return false
        }

        if (start <= 0 ||
            start >= codeEditor.length()
        ) {
            return false
        }

        val text =
            codeEditor.text

        val left =
            text[start - 1]

        val right =
            text[start]

        val isPair =
            (left == '{' && right == '}') ||
            (left == '(' && right == ')') ||
            (left == '[' && right == ']') ||
            (left == '"' && right == '"') ||
            (left == '\'' && right == '\'')

        if (!isPair) {
            return false
        }

        autoPairing = true

        text.delete(
            start - 1,
            start + 1
        )

        autoPairing = false

        codeEditor.setSelection(
            (start - 1).coerceAtLeast(0)
        )

        highlightCode()
        lineNumbers.invalidate()

        return true
    }

    // Search

    private fun searchCode() {

        val keyword =
            searchInput.text.toString()

        if (keyword.isEmpty()) {
            return
        }

        val text =
            codeEditor.text.toString()

        val start =
            codeEditor.selectionEnd.coerceAtLeast(0)

        var index =
            text.indexOf(
                keyword,
                start
            )

        if (index == -1) {
            index =
                text.indexOf(keyword)
        }

        if (index != -1) {

            codeEditor.requestFocus()

            codeEditor.setSelection(
                index,
                index + keyword.length
            )

            codeEditor.post {

                codeEditor.scrollTo(
                    0,
                    getScrollPosition(index)
                )

                lineNumbers.invalidate()
            }
        }

        searchInput.text.clear()
    }

    // Line Jump

    private fun jumpToLine() {

        val line =
            lineInput.text
                .toString()
                .toIntOrNull()

        if (line == null || line < 1) {
            lineInput.text.clear()
            return
        }

        val text =
            codeEditor.text.toString()

        val lines =
            text.split("\n")

        val targetLine =
            line.coerceAtMost(lines.size)

        var position = 0

        for (i in 0 until targetLine - 1) {
            position +=
                lines[i].length + 1
        }

        position =
            position.coerceIn(
                0,
                text.length
            )

        codeEditor.requestFocus()

        codeEditor.setSelection(position)

        codeEditor.post {

            codeEditor.scrollTo(
                0,
                getScrollPosition(position)
            )

            lineNumbers.invalidate()
        }

        lineInput.text.clear()
    }

    // Scroll Position

    private fun getScrollPosition(
        position: Int
    ): Int {

        val layout =
            codeEditor.layout
                ?: return 0

        val line =
            layout.getLineForOffset(
                position.coerceIn(
                    0,
                    codeEditor.length()
                )
            )

        return (
            line * codeEditor.lineHeight -
            codeEditor.height / 3
        ).coerceAtLeast(0)
    }

    // Syntax Highlight

    private fun highlightCode() {

        val editable =
            codeEditor.text ?: return

        if (highlighting) {
            return
        }

        highlighting = true

        // Remove old colors

        val oldSpans =
            editable.getSpans(
                0,
                editable.length,
                ForegroundColorSpan::class.java
            )

        for (span in oldSpans) {
            editable.removeSpan(span)
        }

        val text =
            editable.toString()

        if (text.isEmpty()) {
            highlighting = false
            return
        }

        // Default

        editable.setSpan(
            ForegroundColorSpan(Color.BLACK),
            0,
            editable.length,
            0
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

            val pattern =
                Pattern.compile(
                    "\\b$keyword\\b"
                )

            val matcher =
                pattern.matcher(text)

            while (matcher.find()) {

                editable.setSpan(
                    ForegroundColorSpan(
                        Color.rgb(
                            150,
                            60,
                            180
                        )
                    ),
                    matcher.start(),
                    matcher.end(),
                    0
                )
            }
        }

        // Strings

        val stringPattern =
            Pattern.compile(
                "\"[^\"]*\""
            )

        val stringMatcher =
            stringPattern.matcher(text)

        while (stringMatcher.find()) {

            editable.setSpan(
                ForegroundColorSpan(
                    Color.rgb(
                        40,
                        140,
                        70
                    )
                ),
                stringMatcher.start(),
                stringMatcher.end(),
                0
            )
        }

        // Comments

        val commentPattern =
            Pattern.compile(
                "//.*"
            )

        val commentMatcher =
            commentPattern.matcher(text)

        while (commentMatcher.find()) {

            editable.setSpan(
                ForegroundColorSpan(
                    Color.GRAY
                ),
                commentMatcher.start(),
                commentMatcher.end(),
                0
            )
        }

        highlighting = false

        lineNumbers.invalidate()
    }

    // Preview

    private fun updatePreview() {

        previewContainer.removeAllViews()

        val preview =
            TextView(this).apply {

                text =
                    if (codeEditor.text.isEmpty()) {

                        "KStudio is ready."

                    } else {

                        "KStudio is running!\n\n" +
                        "Code length: " +
                        codeEditor.text.length
                    }

                textSize = 16f

                setPadding(
                    24,
                    24,
                    24,
                    24
                )
            }

        previewContainer.addView(
            preview
        )
    }
}