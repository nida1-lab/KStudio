package com.nida.kstudio

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.text.Editable
import android.text.SpannableString
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

        // Editor

        codeEditor.setText("")

        updateLineNumbers()
        highlightCode()

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

        // Scroll Line Numbers

        codeEditor.viewTreeObserver.addOnScrollChangedListener {
            lineNumbers.scrollTo(0, codeEditor.scrollY)
        }

        // Run

        runButton.setOnClickListener {
            updatePreview()
        }

        // Search Button

        searchButton.setOnClickListener {
            searchCode()
        }
        // Search Enter

searchInput.setOnEditorActionListener { _, actionId, event ->

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

// Line Enter

lineInput.setOnEditorActionListener { _, actionId, event ->

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
        // Line Button

        lineButton.setOnClickListener {
            jumpToLine()
        }

        // Line Enter

        lineInput.setOnEditorActionListener { _, actionId, event ->

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

        updatePreview()
    }

    // Line Numbers

    private fun updateLineNumbers() {

        val text = codeEditor.text.toString()

        val lineCount =
            if (text.isEmpty()) {
                1
            } else {
                text.count { it == '\n' } + 1
            }

        val builder = StringBuilder()

        for (i in 1..lineCount) {
            builder.append(i)

            if (i < lineCount) {
                builder.append("\n")
            }
        }

        lineNumbers.text = builder.toString()

        val digits = lineCount.toString().length

        lineNumbers.minWidth = 32 + (digits * 10)
    }

    // Search

    private fun searchCode() {

        val keyword = searchInput.text.toString()

        if (keyword.isEmpty()) {
            return
        }

        val text = codeEditor.text.toString()

        val start = codeEditor.selectionEnd
            .coerceAtLeast(0)

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

            codeEditor.post {
                codeEditor.scrollTo(
                    0,
                    getScrollPosition(index)
                )
            }
        }

        searchInput.text.clear()
    }

    // Line Jump

    private fun jumpToLine() {

        val line = lineInput.text
            .toString()
            .toIntOrNull()

        if (line == null || line < 1) {
            lineInput.text.clear()
            return
        }

        val text = codeEditor.text.toString()

        val lines = text.split("\n")

        val targetLine =
            line.coerceAtMost(lines.size)

        var position = 0

        for (i in 0 until targetLine - 1) {
            position += lines[i].length + 1
        }

        codeEditor.requestFocus()

        codeEditor.setSelection(
            position.coerceAtMost(text.length)
        )

        codeEditor.post {
            codeEditor.scrollTo(
                0,
                getScrollPosition(position)
            )
        }

        lineInput.text.clear()
    }

    // Scroll Position

    private fun getScrollPosition(position: Int): Int {

        val layout = codeEditor.layout ?: return 0

        val line = layout.getLineForOffset(
            position.coerceIn(0, codeEditor.length())
        )

        return (
            line * codeEditor.lineHeight -
            codeEditor.height / 3
        ).coerceAtLeast(0)
    }

    // Syntax Highlight

    private fun highlightCode() {

        if (changingText) {
            return
        }

        val text = codeEditor.text.toString()

        val spannable = SpannableString(text)

        if (spannable.isEmpty()) {
            return
        }

        // Reset

        spannable.setSpan(
            ForegroundColorSpan(Color.BLACK),
            0,
            spannable.length,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
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
                Pattern.compile("\\b$keyword\\b")

            val matcher = pattern.matcher(text)

            while (matcher.find()) {

                spannable.setSpan(
                    ForegroundColorSpan(
                        Color.rgb(150, 60, 180)
                    ),
                    matcher.start(),
                    matcher.end(),
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // Strings

        val stringPattern =
            Pattern.compile("\"[^\"]*\"")

        val stringMatcher =
            stringPattern.matcher(text)

        while (stringMatcher.find()) {

            spannable.setSpan(
                ForegroundColorSpan(
                    Color.rgb(40, 140, 70)
                ),
                stringMatcher.start(),
                stringMatcher.end(),
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Comments

        val commentPattern =
            Pattern.compile("//.*")

        val commentMatcher =
            commentPattern.matcher(text)

        while (commentMatcher.find()) {

            spannable.setSpan(
                ForegroundColorSpan(Color.GRAY),
                commentMatcher.start(),
                commentMatcher.end(),
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val selectionStart =
            codeEditor.selectionStart

        val selectionEnd =
            codeEditor.selectionEnd

        changingText = true

        codeEditor.setText(spannable)

        val safeStart =
            selectionStart.coerceIn(
                0,
                codeEditor.length()
            )

        val safeEnd =
            selectionEnd.coerceIn(
                0,
                codeEditor.length()
            )

        codeEditor.setSelection(
            safeStart,
            safeEnd
        )

        changingText = false
    }

    // Preview

    private fun updatePreview() {

        previewContainer.removeAllViews()

        val preview = TextView(this).apply {

            text =
                if (codeEditor.text.isEmpty()) {
                    "KStudio is ready."
                } else {
                    "KStudio is running!\n\n" +
                    "Code length: ${codeEditor.text.length}"
                }

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
