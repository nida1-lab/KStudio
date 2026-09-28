package com.nida.kstudio

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
            runKStudio()
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

    // Run

    private fun runKStudio() {

        // Test message

        Toast.makeText(
            this,
            "RUN TEST",
            Toast.LENGTH_SHORT
        ).show()

        // Error UI test

        showError(
            line = 10,
            code = "K001",
            fileName = "MainActivity.kt",
            message = "Kotlinコードに問題があります。",
            source = "KStudio"
        )
    }

    // Error UI

    private fun showError(
        line: Int,
        code: String,
        fileName: String,
        message: String,
        source: String
    ) {

        previewContainer.removeAllViews()

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    20,
                    20,
                    20,
                    20
                )
            }

        // Header

        val header =
            TextView(this).apply {

                text = "🔴  Error"

                textSize = 22f

                setTextColor(
                    Color.rgb(
                        210,
                        40,
                        40
                    )
                )

                setPadding(
                    0,
                    0,
                    0,
                    16
                )
            }

        root.addView(header)

        // Source

        val sourceLabel =
            TextView(this).apply {

                text = "エラーコード"

                textSize = 13f
            }

        root.addView(sourceLabel)

        val sourceSpinner =
            Spinner(this)

        val sources =
            arrayOf(
                "KStudio",
                "Kotlin",
                "Gradle",
                "Android",
                "Git"
            )

        val adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                sources
            )

        sourceSpinner.adapter = adapter

        val selectedIndex =
            sources.indexOf(source)
                .coerceAtLeast(0)

        sourceSpinner.setSelection(
            selectedIndex
        )

        root.addView(
            sourceSpinner,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // Error Row

        val errorRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    0,
                    20,
                    0,
                    20
                )
            }

        // Line

        val lineView =
            TextView(this).apply {

                text = "${line}行目"

                textSize = 16f

                setTextColor(
                    Color.rgb(
                        40,
                        100,
                        210
                    )
                )

                setPadding(
                    0,
                    10,
                    16,
                    10
                )

                setOnClickListener {

                    lineInput.setText(
                        line.toString()
                    )

                    jumpToLine()
                }
            }

        errorRow.addView(lineView)

        // Error Code

        val codeView =
            TextView(this).apply {

                text = code

                textSize = 16f

                setTextColor(
                    Color.rgb(
                        180,
                        60,
                        60
                    )
                )

                setPadding(
                    16,
                    10,
                    16,
                    10
                )

                setOnClickListener {

                    showErrorDetail(
                        line,
                        code,
                        fileName,
                        message,
                        source
                    )
                }
            }

        errorRow.addView(codeView)

        // File

        val fileView =
            TextView(this).apply {

                text = fileName

                textSize = 14f

                setPadding(
                    16,
                    10,
                    16,
                    10
                )

                setOnClickListener {

                    lineInput.setText(
                        line.toString()
                    )

                    jumpToLine()
                }
            }

        errorRow.addView(fileView)

        // Time

        val time =
            SimpleDateFormat(
                "HH:mm",
                Locale.getDefault()
            ).format(Date())

        val timeView =
            TextView(this).apply {

                text = time

                textSize = 13f

                setTextColor(
                    Color.GRAY
                )

                setPadding(
                    16,
                    10,
                    0,
                    10
                )
            }

        errorRow.addView(timeView)

        root.addView(errorRow)

        // Message

        val messageView =
            TextView(this).apply {

                text = message

                textSize = 15f

                setPadding(
                    0,
                    8,
                    0,
                    20
                )
            }

        root.addView(messageView)

        // Copy

        val copyButton =
            Button(this).apply {

                text = "エラーコードをコピー"

                setOnClickListener {

                    val clipboard =
                        getSystemService(
                            Context.CLIPBOARD_SERVICE
                        ) as ClipboardManager

                    val copyText =
                        "$code\n" +
                        "$fileName\n" +
                        "${line}行目"

                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            "KStudio Error",
                            copyText
                        )
                    )

                    Toast.makeText(
                        this@MainActivity,
                        "エラーコードをコピーしました",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        root.addView(copyButton)

        // Preview

        val previewButton =
            Button(this).apply {

                text = "Previewに戻る"

                setOnClickListener {
                    updatePreview()
                }
            }

        root.addView(previewButton)

        previewContainer.addView(root)
    }

// Error Detail

private fun showErrorDetail(
    line: Int,
    code: String,
    fileName: String,
    message: String,
    source: String
) {

    previewContainer.removeAllViews()

    val root =
        LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                20,
                20,
                20,
                20
            )
        }

    // Code

    val title =
        TextView(this).apply {

            text = code

            textSize = 28f

            setTextColor(
                Color.rgb(
                    190,
                    50,
                    50
                )

        setPadding(
            0,
            0,
            0,
            20
        )

        setOnClickListener {

            showError(
                line = line,
                code = code,
                fileName = fileName,
                message = message,
                source = source
            )
        }
    }
            )

            setPadding(
                0,
                0,
                0,
                20
            )
        }

    root.addView(title)

    // Description

    val description =
        TextView(this).apply {

            text =
                message +
                "\n\n" +
                "ファイル: $fileName\n" +
                "場所: ${line}行目\n" +
                "種類: $source"

            textSize = 16f

            setPadding(
                0,
                10,
                0,
                20
            )
        }

    root.addView(description)

    // Copy

    val copyButton =
        Button(this).apply {

            text = "エラーコードをコピー"

            setOnClickListener {

                val clipboard =
                    getSystemService(
                        Context.CLIPBOARD_SERVICE
                    ) as ClipboardManager

                val clip =
                    ClipData.newPlainText(
                        "KStudio Error",
                        code
                    )

                clipboard.setPrimaryClip(clip)

                Toast.makeText(
                    this@MainActivity,
                    "$code をコピーしました",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    root.addView(
        copyButton,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    )

    // Back

    val backButton =
        Button(this).apply {

            text = "エラーコードに戻る"

            setOnClickListener {

                showError(
                    line = line,
                    code = code,
                    fileName = fileName,
                    message = message,
                    source = source
                )
            }
        }

    root.addView(
        backButton,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    )

    previewContainer.addView(root)
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
                .subSequence(
                    start,
                    end
                )
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

        if (
            start <= 0 ||
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
            codeEditor.selectionEnd
                .coerceAtLeast(0)

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

        if (
            line == null ||
            line < 1
        ) {
            lineInput.text.clear()
            return
        }

        val text =
            codeEditor.text.toString()

        val lines =
            text.split("\n")

        val targetLine =
            line.coerceAtMost(
                lines.size
            )

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

        codeEditor.setSelection(
            position
        )

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
            codeEditor.text
                ?: return

        if (highlighting) {
            return
        }

        highlighting = true

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

        editable.setSpan(
            ForegroundColorSpan(
                Color.BLACK
            ),
            0,
            editable.length,
            0
        )

        val keywords =
            arrayOf(
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