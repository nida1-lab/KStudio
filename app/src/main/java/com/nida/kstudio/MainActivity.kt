package com.nida.kstudio

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
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

    private lateinit var topMenuBar: View
    private lateinit var projectBar: View
    private lateinit var searchBar: View
    private lateinit var editorContainer: View
    private lateinit var bottomHeader: View
    private lateinit var previewHeader: View
    private lateinit var fullscreenButton: Button
    private lateinit var undoButton: Button
    private lateinit var redoButton: Button
    private lateinit var saveButton: Button

    private data class ManagedEntry(
        val uri: Uri,
        val name: String,
        val mimeType: String,
        val isDirectory: Boolean
    )

    private val undoStack = java.util.ArrayDeque<String>()
    private val redoStack = java.util.ArrayDeque<String>()
    private val recentFolders = mutableListOf<Uri>()

    private var highlighting = false
    private var autoPairing = false
    private var historyApplying = false
    private var isDirty = false
    private var fullscreen = false

    private var folderRootUri: Uri? = null
    private var currentFolderUri: Uri? = null
    private var currentFileUri: Uri? = null
    private var currentFileName = "新規ファイル"
    private val currentFolderStack = mutableListOf<Uri>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        codeEditor =
            findViewById(R.id.codeEditor)

        lineNumbers =
            findViewById(R.id.lineNumbers)

        searchInput =
            findViewById(R.id.searchInput)

        lineInput =
            findViewById(R.id.lineInput)

        previewContainer =
            findViewById(R.id.previewContainer)

        topMenuBar =
            findViewById(R.id.topMenuBar)

        projectBar =
            findViewById(R.id.projectBar)

        searchBar =
            findViewById(R.id.searchBar)

        editorContainer =
            findViewById(R.id.editorContainer)

        bottomHeader =
            findViewById(R.id.bottomHeader)

        previewHeader =
            findViewById(R.id.previewHeader)

        val runButton =
            findViewById<Button>(
                R.id.runButton
            )

        val searchButton =
            findViewById<Button>(
                R.id.searchButton
            )

        val lineButton =
            findViewById<Button>(
                R.id.lineButton
            )

        val menuButton =
            findViewById<Button>(
                R.id.menuButton
            )

        fullscreenButton =
            findViewById(
                R.id.fullscreenButton
            )

        undoButton = findViewById(R.id.undoButton)
        redoButton = findViewById(R.id.redoButton)
        saveButton = findViewById(R.id.saveButton)

        loadRecentFolders()
        applySystemTheme()
        updateHistoryButtons()
        updateEditorHeader()

        // Editor

        codeEditor.setText("")

        lineNumbers.setEditor(
            codeEditor
        )

        codeEditor.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                    if (
                        !historyApplying &&
                        s != null &&
                        (count > 0 || after > 0)
                    ) {
                        pushUndoState(s.toString())
                    }
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    lineNumbers.invalidate()

                    if (!historyApplying) {
                        isDirty = true
                        updateEditorHeader()
                    }

                    updateHistoryButtons()
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {

                    if (
                        !highlighting &&
                        !autoPairing
                    ) {
                        highlightCode()
                    }

                    lineNumbers.invalidate()
                }
            }
        )

        // Editor Shortcuts

        codeEditor.setOnKeyListener { _, keyCode, event ->

            if (event.action != KeyEvent.ACTION_DOWN) {
                return@setOnKeyListener false
            }

            if (keyCode == KeyEvent.KEYCODE_DEL) {
                return@setOnKeyListener handleDeletePair()
            }

            if (keyCode == KeyEvent.KEYCODE_TAB) {
                insertTextAtCursor("    ")
                return@setOnKeyListener true
            }

            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                insertIndentedNewLine()
                return@setOnKeyListener true
            }

            val typed = event.unicodeChar.toChar()
            val cursor = codeEditor.selectionStart.coerceAtLeast(0)
            val next = codeEditor.text.toString().getOrNull(cursor)

            if (
                (typed == '}' || typed == ')' || typed == ']' || typed == '"' || typed == '\'') &&
                next == typed &&
                codeEditor.selectionStart == codeEditor.selectionEnd
            ) {
                codeEditor.setSelection(cursor + 1)
                return@setOnKeyListener true
            }

            val pair = when (typed) {
                '{' -> '}'
                '(' -> ')'
                '[' -> ']'
                '"' -> '"'
                '\'' -> '\''
                else -> null
            }

            if (pair != null) {
                insertPair(typed, pair)
                true
            } else {
                false
            }
        }

        // Editor Actions

        undoButton.setOnClickListener {
            undoEditor()
        }

        redoButton.setOnClickListener {
            redoEditor()
        }

        saveButton.setOnClickListener {
            saveCurrentFile()
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
                actionId ==
                    EditorInfo.IME_ACTION_SEARCH ||
                event?.keyCode ==
                    KeyEvent.KEYCODE_ENTER

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
                actionId ==
                    EditorInfo.IME_ACTION_GO ||
                event?.keyCode ==
                    KeyEvent.KEYCODE_ENTER

            if (enter) {

                jumpToLine()

                true

            } else {

                false
            }
        }

        // Menu

        menuButton.setOnClickListener {

            showMainMenu(
                menuButton
            )
        }

        // Fullscreen

        fullscreenButton.setOnClickListener {

            togglePreviewFullscreen()
        }

        // Initial

        lineNumbers.invalidate()

        updatePreview()
    }

    // Main Menu

    private fun showMainMenu(
        anchor: View
    ) {

        val menu =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    12,
                    12,
                    12,
                    12
                )

                setBackgroundColor(
                    Color.WHITE
                )
            }

        val popup =
            PopupWindow(
                menu,
                260,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )

        // Home

        val homeButton =
            Button(this).apply {

                text = "Home"

                setOnClickListener {

                    updatePreview()

                    popup.dismiss()
                }
            }

        menu.addView(
            homeButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                52
            )
        )

        // Folder

        val folderButton =
            Button(this).apply {

                text = "フォルダ"

                setOnClickListener {

                    openFolderPicker()

                    popup.dismiss()
                }
            }

        menu.addView(
            folderButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                52
            )
        )

        // History

        val historyButton =
            Button(this).apply {

                text = "履歴"

                setOnClickListener {

                    showHistory()

                    popup.dismiss()
                }
            }

        menu.addView(
            historyButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                52
            )
        )

        // Projects

        val projectButton =
            Button(this).apply {

                text = "プロジェクト一覧"

                setOnClickListener {

                    showProjects()

                    popup.dismiss()
                }
            }

        menu.addView(
            projectButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                52
            )
        )

        // Files

        val fileButton =
            Button(this).apply {

                text = "ファイル管理"

                setOnClickListener {

                    showFileManager()

                    popup.dismiss()
                }
            }

        menu.addView(
            fileButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                52
            )
        )

        popup.elevation = 12f

        popup.showAsDropDown(
            anchor,
            0,
            0
        )
    }

    // Folder Picker

    private fun openFolderPicker() {

        val intent =
            Intent(
                Intent.ACTION_OPEN_DOCUMENT_TREE
            )

        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )

        startActivityForResult(
            intent,
            1001
        )
    }

    // Folder Result

    @Suppress("DEPRECATION")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode == 1001 &&
            resultCode == RESULT_OK
        ) {

            val uri =
                data?.data
                    ?: return

            try {

                contentResolver
                    .takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

            } catch (_: Exception) {
            }

            folderRootUri = uri
            currentFolderUri =
                DocumentsContract.buildDocumentUriUsingTree(
                    uri,
                    DocumentsContract.getTreeDocumentId(uri)
                )
            currentFolderStack.clear()
            currentFileUri = null
            currentFileName = "新規ファイル"
            isDirty = false
            undoStack.clear()
            redoStack.clear()

            historyApplying = true
            codeEditor.setText("")
            historyApplying = false
            highlightCode()

            saveRecentFolder(uri)
            updateEditorHeader()
            updateHistoryButtons()

            Toast.makeText(
                this,
                "フォルダを開きました",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Folder Name

    private fun getFolderName(
        uri: Uri
    ): String {

        val displayName = getDocumentName(uri)

        if (displayName.isNotBlank()) {
            return displayName
        }

        val path = uri.path ?: return "選択したフォルダ"
        val name = path.substringAfterLast(":")

        return if (name.isNotEmpty()) {
            name
        } else {
            "選択したフォルダ"
        }
    }

    // File Manager

    private fun showFileManager() {

        val rootUri = folderRootUri

        if (rootUri == null || currentFolderUri == null) {
            Toast.makeText(
                this,
                "先にフォルダを選択してください",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                setBackgroundColor(surfaceColor())
            }

        val title =
            TextView(this).apply {
                text = "ファイル管理"
                textSize = 22f
                setTextColor(primaryTextColor())
            }

        val pathView =
            TextView(this).apply {
                textSize = 13f
                setTextColor(secondaryTextColor())
                setPadding(0, 6, 0, 12)
            }

        val list =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

        val scroll =
            android.widget.ScrollView(this).apply {
                addView(
                    list,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }

        val actions =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

        val newFile =
            Button(this).apply {
                text = "新規"
                setOnClickListener {
                    askCreateFile(currentFolderUri!!)
                }
            }

        val newFolder =
            Button(this).apply {
                text = "フォルダ"
                setOnClickListener {
                    askCreateFolder(currentFolderUri!!)
                }
            }

        val upButton =
            Button(this).apply {
                text = "上へ"
            }

        val closeButton =
            Button(this).apply {
                text = "閉じる"
            }

        actions.addView(
            newFile,
            LinearLayout.LayoutParams(0, 50, 1f)
        )
        actions.addView(
            newFolder,
            LinearLayout.LayoutParams(0, 50, 1f)
        )
        actions.addView(
            upButton,
            LinearLayout.LayoutParams(0, 50, 1f)
        )
        actions.addView(
            closeButton,
            LinearLayout.LayoutParams(0, 50, 1f)
        )

        root.addView(title)
        root.addView(pathView)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        root.addView(actions)

        val dialog =
            android.app.AlertDialog.Builder(this)
                .setView(root)
                .create()

        fun render() {

            val current = currentFolderUri ?: return

            pathView.text =
                "📁 " +
                getDocumentName(current).ifBlank {
                    "選択したフォルダ"
                }

            list.removeAllViews()

            val entries = queryFolder(current)

            if (entries.isEmpty()) {

                val empty =
                    TextView(this).apply {
                        text = "このフォルダは空です"
                        textSize = 15f
                        setTextColor(secondaryTextColor())
                        setPadding(8, 20, 8, 20)
                    }

                list.addView(empty)
            }

            for (entry in entries) {

                val button =
                    Button(this).apply {

                        text =
                            if (entry.isDirectory) {
                                "📁  " + entry.name
                            } else {
                                "📄  " + entry.name
                            }

                        gravity =
                            Gravity.START or
                            Gravity.CENTER_VERTICAL

                        setAllCaps(false)

                        setOnClickListener {

                            if (entry.isDirectory) {

                                currentFolderStack.add(current)
                                currentFolderUri =
                                    entry.uri

                                render()

                            } else {

                                if (
                                    !isSupportedTextFile(
                                        entry.name,
                                        entry.mimeType
                                    )
                                ) {

                                    Toast.makeText(
                                        this@MainActivity,
                                        "テキストファイルではありません",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    return@setOnClickListener
                                }

                                openManagedFile(
                                    entry.uri,
                                    entry.name
                                )

                                dialog.dismiss()
                            }
                        }

                        setOnLongClickListener {

                            showFileActions(
                                entry,
                                ::render
                            )

                            true
                        }
                    }

                list.addView(
                    button,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        54
                    )
                )
            }
        }

        upButton.setOnClickListener {

            if (currentFolderStack.isNotEmpty()) {

                currentFolderUri =
                    currentFolderStack
                        .removeAt(
                            currentFolderStack.lastIndex
                        )

                render()

            } else {

                Toast.makeText(
                    this,
                    "これ以上上には移動できません",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        render()
        dialog.show()
    }

    private fun queryFolder(
        folderUri: Uri
    ): List<ManagedEntry> {

        val treeUri = folderRootUri
            ?: return emptyList()

        val parentId =
            try {
                DocumentsContract.getDocumentId(
                    folderUri
                )
            } catch (_: Exception) {
                DocumentsContract.getTreeDocumentId(
                    treeUri
                )
            }

        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                parentId
            )

        val result = mutableListOf<ManagedEntry>()

        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )

        try {

            contentResolver.query(
                childrenUri,
                projection,
                null,
                null,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME +
                    " COLLATE NOCASE ASC"
            )?.use { cursor ->

                val idIndex =
                    cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID
                    )

                val nameIndex =
                    cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    )

                val mimeIndex =
                    cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    )

                while (cursor.moveToNext()) {

                    val id =
                        cursor.getString(idIndex)

                    val name =
                        cursor.getString(nameIndex)
                            ?: "(名称なし)"

                    val mime =
                        cursor.getString(mimeIndex)
                            ?: "application/octet-stream"

                    result.add(
                        ManagedEntry(
                            uri =
                                DocumentsContract
                                    .buildDocumentUriUsingTree(
                                        treeUri,
                                        id
                                    ),
                            name = name,
                            mimeType = mime,
                            isDirectory =
                                mime ==
                                    DocumentsContract
                                        .Document
                                        .MIME_TYPE_DIR
                        )
                    )
                }
            }

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "フォルダを読み込めませんでした: " +
                    (e.message ?: "unknown"),
                Toast.LENGTH_SHORT
            ).show()
        }

        return result
    }

    private fun openManagedFile(
        uri: Uri,
        name: String
    ) {

        confirmUnsavedChanges {

            try {

                val text =
                    contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: ""

                if (text.length > 2_000_000) {

                    Toast.makeText(
                        this,
                        "大きすぎるファイルです",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@confirmUnsavedChanges
                }

                historyApplying = true
                codeEditor.setText(text)
                historyApplying = false

                undoStack.clear()
                redoStack.clear()

                currentFileUri = uri
                currentFileName = name
                isDirty = false

                updateEditorHeader()
                updateHistoryButtons()
                lineNumbers.invalidate()
                highlightCode()

                Toast.makeText(
                    this,
                    "開きました: " + name,
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {

                historyApplying = false

                Toast.makeText(
                    this,
                    "ファイルを開けませんでした: " +
                        (e.message ?: "unknown"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun saveCurrentFile(
        onComplete: (() -> Unit)? = null
    ) {

        val uri = currentFileUri

        if (uri == null) {

            Toast.makeText(
                this,
                "保存先ファイルがありません",
                Toast.LENGTH_SHORT
            ).show()

            onComplete?.invoke()
            return
        }

        try {

            contentResolver
                .openOutputStream(uri, "wt")
                ?.use { stream ->

                    stream.write(
                        codeEditor.text
                            .toString()
                            .toByteArray(Charsets.UTF_8)
                    )
                }
                ?: throw IllegalStateException(
                    "保存先を開けません"
                )

            isDirty = false
            updateEditorHeader()

            Toast.makeText(
                this,
                "保存しました",
                Toast.LENGTH_SHORT
            ).show()

            onComplete?.invoke()

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "保存に失敗しました: " +
                    (e.message ?: "unknown"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun askCreateFile(
        folderUri: Uri
    ) {

        val input =
            EditText(this).apply {
                hint = "例: MainActivity.kt"
                setSingleLine(true)
                setText("Main.kt")
                selectAll()
            }

        android.app.AlertDialog.Builder(this)
            .setTitle("新しいファイル")
            .setView(input)
            .setNegativeButton(
                "キャンセル",
                null
            )
            .setPositiveButton(
                "作成"
            ) { _, _ ->

                val name =
                    input.text
                        .toString()
                        .trim()

                if (name.isEmpty()) {
                    return@setPositiveButton
                }

                confirmUnsavedChanges {
                    createManagedFile(
                        folderUri,
                        name
                    )
                }
            }
            .show()
    }

    private fun askCreateFolder(
        folderUri: Uri
    ) {

        val input =
            EditText(this).apply {
                hint = "フォルダ名"
                setSingleLine(true)
                setText("NewFolder")
                selectAll()
            }

        android.app.AlertDialog.Builder(this)
            .setTitle("新しいフォルダ")
            .setView(input)
            .setNegativeButton(
                "キャンセル",
                null
            )
            .setPositiveButton(
                "作成"
            ) { _, _ ->

                val name =
                    input.text
                        .toString()
                        .trim()

                if (name.isEmpty()) {
                    return@setPositiveButton
                }

                try {

                    DocumentsContract.createDocument(
                        contentResolver,
                        folderUri,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        name
                    )

                    Toast.makeText(
                        this,
                        "フォルダを作成しました",
                        Toast.LENGTH_SHORT
                    ).show()

                } catch (_: Exception) {

                    Toast.makeText(
                        this,
                        "フォルダ作成に失敗しました",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    private fun createManagedFile(
        folderUri: Uri,
        name: String
    ) {

        try {

            val mime =
                mimeTypeForFile(name)

            val uri =
                DocumentsContract.createDocument(
                    contentResolver,
                    folderUri,
                    mime,
                    name
                )
                    ?: throw IllegalStateException(
                        "ファイル作成に失敗しました"
                    )

            historyApplying = true
            codeEditor.setText("")
            historyApplying = false

            undoStack.clear()
            redoStack.clear()

            currentFileUri = uri
            currentFileName = name
            isDirty = false

            updateEditorHeader()
            updateHistoryButtons()
            highlightCode()
            lineNumbers.invalidate()

            Toast.makeText(
                this,
                "作成しました: " + name,
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {

            historyApplying = false

            Toast.makeText(
                this,
                "ファイル作成に失敗しました: " +
                    (e.message ?: "unknown"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showFileActions(
        entry: ManagedEntry,
        refresh: () -> Unit
    ) {

        val options =
            if (entry.isDirectory) {
                arrayOf(
                    "名前変更",
                    "削除",
                    "キャンセル"
                )
            } else {
                arrayOf(
                    "内容をコピー",
                    "クリップボードで置換",
                    "名前変更",
                    "削除",
                    "キャンセル"
                )
            }

        android.app.AlertDialog.Builder(this)
            .setTitle(entry.name)
            .setItems(options) { _, which ->

                if (entry.isDirectory) {

                    when (which) {

                        0 -> askRename(
                            entry,
                            refresh
                        )

                        1 -> askDelete(
                            entry,
                            refresh
                        )
                    }

                } else {

                    when (which) {

                        0 -> copyFileContent(entry)

                        1 -> replaceFileFromClipboard(
                            entry,
                            refresh
                        )

                        2 -> askRename(
                            entry,
                            refresh
                        )

                        3 -> askDelete(
                            entry,
                            refresh
                        )
                    }
                }
            }
            .show()
    }

    private fun copyFileContent(
        entry: ManagedEntry
    ) {

        try {

            val content =
                contentResolver
                    .openInputStream(entry.uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: ""

            val clipboard =
                getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager

            clipboard.setPrimaryClip(
                ClipData.newPlainText(
                    "KStudio File",
                    content
                )
            )

            Toast.makeText(
                this,
                "ファイル内容をコピーしました",
                Toast.LENGTH_SHORT
            ).show()

        } catch (_: Exception) {

            Toast.makeText(
                this,
                "コピーに失敗しました",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun replaceFileFromClipboard(
        entry: ManagedEntry,
        refresh: () -> Unit
    ) {

        val clipboard =
            getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager

        if (!clipboard.hasPrimaryClip()) {

            Toast.makeText(
                this,
                "クリップボードが空です",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val clip = clipboard.primaryClip
            ?: return

        val value =
            clip.getItemAt(0)
                .coerceToText(this)
                .toString()

        android.app.AlertDialog.Builder(this)
            .setTitle("ファイルを置換しますか？")
            .setMessage(entry.name)
            .setNegativeButton(
                "キャンセル",
                null
            )
            .setPositiveButton(
                "置換"
            ) { _, _ ->

                try {

                    contentResolver
                        .openOutputStream(
                            entry.uri,
                            "wt"
                        )
                        ?.use { stream ->
                            stream.write(
                                value.toByteArray(
                                    Charsets.UTF_8
                                )
                            )
                        }
                        ?: throw IllegalStateException(
                            "保存先を開けません"
                        )

                    if (currentFileUri == entry.uri) {

                        historyApplying = true
                        codeEditor.setText(value)
                        historyApplying = false

                        undoStack.clear()
                        redoStack.clear()
                        isDirty = false

                        updateEditorHeader()
                        updateHistoryButtons()
                        highlightCode()
                        lineNumbers.invalidate()
                    }

                    refresh()

                    Toast.makeText(
                        this,
                        "ファイルを置換しました",
                        Toast.LENGTH_SHORT
                    ).show()

                } catch (e: Exception) {

                    historyApplying = false

                    Toast.makeText(
                        this,
                        "置換に失敗しました: " +
                            (e.message ?: "unknown"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }

    private fun askRename(
        entry: ManagedEntry,
        refresh: () -> Unit
    ) {

        val input =
            EditText(this).apply {
                setSingleLine(true)
                setText(entry.name)
                selectAll()
            }

        android.app.AlertDialog.Builder(this)
            .setTitle("名前変更")
            .setView(input)
            .setNegativeButton(
                "キャンセル",
                null
            )
            .setPositiveButton(
                "変更"
            ) { _, _ ->

                val newName =
                    input.text
                        .toString()
                        .trim()

                if (newName.isEmpty()) {
                    return@setPositiveButton
                }

                try {

                    DocumentsContract.renameDocument(
                        contentResolver,
                        entry.uri,
                        newName
                    )

                    if (currentFileUri == entry.uri) {
                        currentFileName = newName
                        updateEditorHeader()
                    }

                    refresh()

                } catch (_: Exception) {

                    Toast.makeText(
                        this,
                        "名前変更に失敗しました",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    private fun askDelete(
        entry: ManagedEntry,
        refresh: () -> Unit
    ) {

        android.app.AlertDialog.Builder(this)
            .setTitle("削除しますか？")
            .setMessage(entry.name)
            .setNegativeButton(
                "キャンセル",
                null
            )
            .setPositiveButton(
                "削除"
            ) { _, _ ->

                confirmUnsavedChanges {

                    try {

                        DocumentsContract.deleteDocument(
                            contentResolver,
                            entry.uri
                        )

                        if (currentFileUri == entry.uri) {

                            historyApplying = true
                            codeEditor.setText("")
                            historyApplying = false

                            currentFileUri = null
                            currentFileName = "新規ファイル"
                            isDirty = false

                            undoStack.clear()
                            redoStack.clear()

                            updateEditorHeader()
                            updateHistoryButtons()
                        }

                        refresh()

                    } catch (_: Exception) {

                        Toast.makeText(
                            this,
                            "削除に失敗しました",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .show()
    }

    private fun confirmUnsavedChanges(
        onContinue: () -> Unit
    ) {

        if (!isDirty) {
            onContinue()
            return
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("未保存の変更")
            .setMessage(
                "変更内容を保存してから続行しますか？"
            )
            .setNegativeButton(
                "破棄"
            ) { _, _ ->
                isDirty = false
                onContinue()
            }
            .setNeutralButton(
                "キャンセル",
                null
            )
            .setPositiveButton(
                "保存"
            ) { _, _ ->
                saveCurrentFile(
                    onComplete = onContinue
                )
            }
            .show()
    }

    private fun getDocumentName(
        uri: Uri
    ): String {

        try {

            val projection =
                arrayOf(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                )

            contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->

                if (cursor.moveToFirst()) {

                    val index =
                        cursor.getColumnIndex(
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME
                        )

                    if (index >= 0) {
                        return cursor.getString(index)
                            ?: ""
                    }
                }
            }

        } catch (_: Exception) {
        }

        return ""
    }

    private fun isSupportedTextFile(
        name: String,
        mimeType: String
    ): Boolean {

        if (mimeType.startsWith("text/")) {
            return true
        }

        val lower =
            name.lowercase(Locale.getDefault())

        return lower.endsWith(".kt") ||
            lower.endsWith(".kts") ||
            lower.endsWith(".java") ||
            lower.endsWith(".xml") ||
            lower.endsWith(".json") ||
            lower.endsWith(".txt") ||
            lower.endsWith(".md") ||
            lower.endsWith(".gradle") ||
            lower.endsWith(".properties") ||
            lower.endsWith(".yml") ||
            lower.endsWith(".yaml") ||
            lower.endsWith(".html") ||
            lower.endsWith(".css") ||
            lower.endsWith(".js")
    }

    private fun mimeTypeForFile(
        name: String
    ): String {

        val lower =
            name.lowercase(Locale.getDefault())

        return when {

            lower.endsWith(".xml") ->
                "text/xml"

            lower.endsWith(".json") ->
                "application/json"

            lower.endsWith(".html") ->
                "text/html"

            lower.endsWith(".css") ->
                "text/css"

            lower.endsWith(".js") ->
                "text/javascript"

            else ->
                "text/plain"
        }
    }

    // Recent Folders

    private fun loadRecentFolders() {

        val preferences =
            getSharedPreferences(
                "KStudio",
                Context.MODE_PRIVATE
            )

        val raw =
            preferences.getString(
                "recentFolders",
                ""
            )
                ?: ""

        recentFolders.clear()

        for (value in raw.split("|")) {

            if (value.isBlank()) continue

            recentFolders.add(
                Uri.parse(value)
            )
        }

        val savedRoot =
            preferences.getString(
                "currentFolder",
                null
            )

        if (savedRoot != null) {

            try {

                val uri =
                    Uri.parse(savedRoot)

                folderRootUri = uri
                currentFolderUri =
                    DocumentsContract.buildDocumentUriUsingTree(
                        uri,
                        DocumentsContract.getTreeDocumentId(uri)
                    )

            } catch (_: Exception) {
            }
        }
    }

    private fun saveRecentFolder(
        uri: Uri
    ) {

        recentFolders.removeAll {
            it == uri
        }

        recentFolders.add(
            0,
            uri
        )

        while (recentFolders.size > 8) {
            recentFolders.removeAt(
                recentFolders.lastIndex
            )
        }

        val preferences =
            getSharedPreferences(
                "KStudio",
                Context.MODE_PRIVATE
            )

        preferences.edit()
            .putString(
                "currentFolder",
                uri.toString()
            )
            .putString(
                "recentFolders",
                recentFolders.joinToString("|") {
                    it.toString()
                }
            )
            .apply()
    }

    // Editor History

    private fun pushUndoState(
        text: String
    ) {

        if (
            undoStack.isNotEmpty() &&
            undoStack.peekLast() == text
        ) {
            return
        }

        undoStack.addLast(text)

        while (undoStack.size > 100) {
            undoStack.removeFirst()
        }

        redoStack.clear()
    }

    private fun undoEditor() {

        if (undoStack.isEmpty()) {
            return
        }

        val current =
            codeEditor.text
                .toString()

        val target =
            undoStack.removeLast()

        redoStack.addLast(current)

        historyApplying = true
        codeEditor.setText(target)
        historyApplying = false

        codeEditor.setSelection(
            target.length
        )

        isDirty = true
        updateEditorHeader()
        updateHistoryButtons()
        highlightCode()
        lineNumbers.invalidate()
    }

    private fun redoEditor() {

        if (redoStack.isEmpty()) {
            return
        }

        val current =
            codeEditor.text
                .toString()

        val target =
            redoStack.removeLast()

        undoStack.addLast(current)

        historyApplying = true
        codeEditor.setText(target)
        historyApplying = false

        codeEditor.setSelection(
            target.length
        )

        isDirty = true
        updateEditorHeader()
        updateHistoryButtons()
        highlightCode()
        lineNumbers.invalidate()
    }

    private fun updateHistoryButtons() {

        if (!::undoButton.isInitialized) {
            return
        }

        undoButton.isEnabled =
            undoStack.isNotEmpty()

        redoButton.isEnabled =
            redoStack.isNotEmpty()
    }

    private fun insertTextAtCursor(
        text: String
    ) {

        val start =
            codeEditor.selectionStart
                .coerceAtLeast(0)

        val end =
            codeEditor.selectionEnd
                .coerceAtLeast(0)

        autoPairing = true

        codeEditor.text.replace(
            start,
            end,
            text
        )

        autoPairing = false

        codeEditor.setSelection(
            (start + text.length)
                .coerceAtMost(codeEditor.length())
        )

        highlightCode()
        lineNumbers.invalidate()
    }

    private fun insertIndentedNewLine() {

        val cursor =
            codeEditor.selectionStart
                .coerceAtLeast(0)

        val text =
            codeEditor.text
                .toString()

        val lineStart =
            text.lastIndexOf(
                "\n",
                (cursor - 1).coerceAtLeast(0)
            ) + 1

        val linePrefix =
            text.substring(
                lineStart,
                cursor.coerceAtMost(text.length)
            )

        val indentation =
            Regex("^\\s*")
                .find(linePrefix)
                ?.value
                ?: ""

        val trimmed =
            linePrefix.trimEnd()

        val extra =
            if (trimmed.endsWith("{")) {
                "    "
            } else {
                ""
            }

        insertTextAtCursor(
            "\n" +
                indentation +
                extra
        )
    }

    private fun updateEditorHeader() {

        if (!::codeEditor.isInitialized) {
            return
        }

        val prefix =
            if (isDirty) "● " else ""

        val folderName =
            folderRootUri?.let {
                getFolderName(it)
            }?.takeIf {
                it.isNotBlank()
            } ?: "フォルダ未選択"

        findViewById<TextView>(
            R.id.projectName
        ).text =
            prefix +
                folderName +
                " / " +
                currentFileName
    }

    private fun isDarkMode(): Boolean {

        val mode =
            resources.configuration.uiMode and
                android.content.res.Configuration
                    .UI_MODE_NIGHT_MASK

        return mode ==
            android.content.res.Configuration
                .UI_MODE_NIGHT_YES
    }

    private fun surfaceColor(): Int =
        if (isDarkMode()) {
            Color.rgb(28, 28, 30)
        } else {
            Color.WHITE
        }

    private fun editorSurfaceColor(): Int =
        if (isDarkMode()) {
            Color.rgb(20, 20, 22)
        } else {
            Color.rgb(245, 245, 245)
        }

    private fun lineNumberSurfaceColor(): Int =
        if (isDarkMode()) {
            Color.rgb(36, 36, 38)
        } else {
            Color.rgb(238, 238, 238)
        }

    private fun previewSurfaceColor(): Int =
        if (isDarkMode()) {
            Color.rgb(24, 24, 26)
        } else {
            Color.rgb(250, 250, 250)
        }

    private fun primaryTextColor(): Int =
        if (isDarkMode()) {
            Color.WHITE
        } else {
            Color.BLACK
        }

    private fun secondaryTextColor(): Int =
        if (isDarkMode()) {
            Color.LTGRAY
        } else {
            Color.DKGRAY
        }

    private fun applySystemTheme() {

        findViewById<View>(
            android.R.id.content
        ).setBackgroundColor(
            surfaceColor()
        )

        codeEditor.setTextColor(
            primaryTextColor()
        )

        codeEditor.setHintTextColor(
            secondaryTextColor()
        )

        codeEditor.setBackgroundColor(
            editorSurfaceColor()
        )

        lineNumbers.setBackgroundColor(
            lineNumberSurfaceColor()
        )

        previewContainer.setBackgroundColor(
            previewSurfaceColor()
        )
    }

    // Preview Fullscreen    // Preview Fullscreen

    private fun togglePreviewFullscreen() {

        fullscreen = !fullscreen

        if (fullscreen) {

            topMenuBar.visibility =
                View.GONE

            projectBar.visibility =
                View.GONE

            searchBar.visibility =
                View.GONE

            editorContainer.visibility =
                View.GONE

            bottomHeader.visibility =
                View.GONE

            previewHeader.visibility =
                View.VISIBLE

            previewContainer.layoutParams =
                previewContainer.layoutParams.apply {

                    height =
                        ViewGroup.LayoutParams.MATCH_PARENT
                }

            fullscreenButton.text =
                "✕"

        } else {

            topMenuBar.visibility =
                View.VISIBLE

            projectBar.visibility =
                View.VISIBLE

            searchBar.visibility =
                View.VISIBLE

            editorContainer.visibility =
                View.VISIBLE

            bottomHeader.visibility =
                View.VISIBLE

            previewHeader.visibility =
                View.VISIBLE

            previewContainer.layoutParams =
                previewContainer.layoutParams.apply {

                    height =
                        dpToPx(180)
                }

            fullscreenButton.text =
                "⛶"
        }

        previewContainer.requestLayout()
    }

    // Run

    private fun runKStudio() {

        saveHistory()

        val source =
            codeEditor.text
                .toString()

        val error =
            validateCode(source)

        if (error != null) {

            showError(
                line = error.first,
                code = "K001",
                fileName = currentFileName,
                message = error.second,
                source = "KStudio"
            )

            return
        }

        showRunSuccess(source)
    }

    private fun validateCode(
        source: String
    ): Pair<Int, String>? {

        val stack =
            mutableListOf<Pair<Char, Int>>()

        var line = 1
        var inString = false
        var inChar = false
        var escaped = false
        var inLineComment = false
        var index = 0

        while (index < source.length) {

            val ch = source[index]
            val next = source.getOrNull(index + 1)

            if (ch == '\n') {

                line++
                inLineComment = false

                if (inString || inChar) {
                    return (line - 1) to
                        "文字列または文字リテラルが閉じられていません。"
                }

                index++
                continue
            }

            if (inLineComment) {
                index++
                continue
            }

            if (
                !inString &&
                !inChar &&
                ch == '/' &&
                next == '/'
            ) {
                inLineComment = true
                index += 2
                continue
            }

            if (!inChar && ch == '"' && !escaped) {
                inString = !inString
                index++
                continue
            }

            if (!inString && ch == '\'' && !escaped) {
                inChar = !inChar
                index++
                continue
            }

            if (
                (inString || inChar) &&
                ch == '\\' &&
                !escaped
            ) {
                escaped = true
                index++
                continue
            }

            if (inString || inChar) {
                escaped = false
                index++
                continue
            }

            when (ch) {

                '{', '(', '[' -> {
                    stack.add(ch to line)
                }

                '}', ')', ']' -> {

                    if (stack.isEmpty()) {
                        return line to
                            "閉じ括弧 " +
                            ch +
                            " に対応する開き括弧がありません。"
                    }

                    val expected =
                        when (stack.last().first) {
                            '{' -> '}'
                            '(' -> ')'
                            else -> ']'
                        }

                    if (expected != ch) {
                        return line to
                            "括弧の対応が正しくありません。期待: " +
                            expected +
                            " / 実際: " +
                            ch
                    }

                    stack.removeAt(
                        stack.lastIndex
                    )
                }
            }

            escaped = false
            index++
        }

        if (inString || inChar) {
            return line to
                "文字列または文字リテラルが閉じられていません。"
        }

        if (stack.isNotEmpty()) {

            val open = stack.last()

            val expected =
                when (open.first) {
                    '{' -> '}'
                    '(' -> ')'
                    else -> ']'
                }

            return open.second to
                "開き括弧 " +
                open.first +
                " が閉じられていません。期待: " +
                expected
        }

        return null
    }

    private fun showRunSuccess(
        source: String
    ) {

        if (fullscreen) {
            togglePreviewFullscreen()
        }

        previewContainer.removeAllViews()

        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 20, 20, 20)
            }

        val title =
            TextView(this).apply {
                text = "🟢  Run OK"
                textSize = 24f
                setTextColor(
                    Color.rgb(30, 150, 70)
                )
            }

        val info =
            TextView(this).apply {
                text =
                    "KStudio static check\n\n" +
                    "ファイル: " +
                    currentFileName +
                    "\n行数: " +
                    source.lines().size +
                    "\n文字数: " +
                    source.length +
                    "\n\n" +
                    "括弧・文字列の基本チェックに成功しました。\n" +
                    "実際のAndroid APKビルドはGitHub Actions側で実行します。"
                textSize = 15f
                setTextColor(
                    primaryTextColor()
                )
                setPadding(0, 16, 0, 16)
            }

        val back =
            Button(this).apply {
                text = "Previewに戻る"
                setOnClickListener {
                    updatePreview()
                }
            }

        root.addView(title)
        root.addView(info)
        root.addView(back)

        previewContainer.addView(root)
    }

    // History Save

    private fun saveHistory() {

        val preferences =
            getSharedPreferences(
                "KStudio",
                Context.MODE_PRIVATE
            )

        val oldHistory =
            preferences.getString(
                "history",
                ""
            ) ?: ""

        val time =
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm",
                Locale.getDefault()
            ).format(Date())

        val newHistory =
            "$time  Run\n$oldHistory"

        preferences.edit()
            .putString(
                "history",
                newHistory
            )
            .apply()
    }

    // History

    private fun showHistory() {

        val preferences =
            getSharedPreferences(
                "KStudio",
                Context.MODE_PRIVATE
            )

        val history =
            preferences.getString(
                "history",
                ""
            )

        val message =
            if (
                history.isNullOrBlank()
            ) {

                "まだ履歴はありません。"

            } else {

                history
            }

        val dialog =
            android.app.AlertDialog.Builder(
                this
            )
                .setTitle("履歴")
                .setMessage(message)
                .setPositiveButton(
                    "閉じる",
                    null
                )
                .setNegativeButton(
                    "履歴を削除"
                ) { _, _ ->

                    preferences.edit()
                        .remove("history")
                        .apply()
                }
                .create()

        dialog.show()
    }

    // Projects

    private fun showProjects() {

        if (recentFolders.isEmpty()) {

            Toast.makeText(
                this,
                "まだプロジェクトがありません",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val names =
            recentFolders.map { uri ->
                getFolderName(uri).ifBlank {
                    "選択したフォルダ"
                }
            }.toTypedArray()

        android.app.AlertDialog.Builder(this)
            .setTitle("最近のプロジェクト")
            .setItems(names) { _, which ->

                val uri = recentFolders[which]

                folderRootUri = uri
                currentFolderUri =
                    DocumentsContract.buildDocumentUriUsingTree(
                        uri,
                        DocumentsContract.getTreeDocumentId(uri)
                    )

                currentFolderStack.clear()
                currentFileUri = null
                currentFileName = "新規ファイル"
                isDirty = false

                undoStack.clear()
                redoStack.clear()

                historyApplying = true
                codeEditor.setText("")
                historyApplying = false

                updateEditorHeader()
                updateHistoryButtons()
                highlightCode()
                lineNumbers.invalidate()

                Toast.makeText(
                    this,
                    "プロジェクトを開きました",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(
                "閉じる",
                null
            )
            .show()
    }

    // Error UI

    private fun showError(
        line: Int,
        code: String,
        fileName: String,
        message: String,
        source: String
    ) {

        if (fullscreen) {

            togglePreviewFullscreen()
        }

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

        sourceSpinner.adapter =
            adapter

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

                text =
                    "エラーコードをコピー"

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

                text =
                    "Previewに戻る"

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
            LinearLayout(this)

        root.orientation =
            LinearLayout.VERTICAL

        root.setPadding(
            20,
            20,
            20,
            20
        )

        // Code

        val title =
            TextView(this)

        title.text = code

        title.textSize = 28f

        title.setTextColor(
            Color.rgb(
                190,
                50,
                50
            )
        )

        title.setPadding(
            0,
            0,
            0,
            20
        )

        // K001を押して一覧へ戻る

        title.setOnClickListener {

            showError(
                line = line,
                code = code,
                fileName = fileName,
                message = message,
                source = source
            )
        }

        root.addView(title)

        // Description

        val description =
            TextView(this)

        description.text =
            message +
            "\n\n" +
            "ファイル: $fileName\n" +
            "場所: ${line}行目\n" +
            "種類: $source"

        description.textSize = 16f

        description.setPadding(
            0,
            10,
            0,
            20
        )

        root.addView(description)

        // Copy

        val copyButton =
            Button(this)

        copyButton.text =
            "エラーコードをコピー"

        copyButton.setOnClickListener {

            val clipboard =
                getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager

            val clip =
                ClipData.newPlainText(
                    "KStudio Error",
                    code
                )

            clipboard.setPrimaryClip(
                clip
            )

            Toast.makeText(
                this,
                "$code をコピーしました",
                Toast.LENGTH_SHORT
            ).show()
        }

        root.addView(
            copyButton,
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

        if (
            start < 0 ||
            end < 0
        ) {
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
            if (
                selected.isEmpty()
            ) {

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
            if (
                selected.isEmpty()
            ) {

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

        if (
            start < 0 ||
            end < 0
        ) {
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
            (start - 1)
                .coerceAtLeast(0)
        )

        highlightCode()

        lineNumbers.invalidate()

        return true
    }

    // Search

    private fun searchCode() {

        val keyword =
            searchInput.text
                .toString()

        if (
            keyword.isEmpty()
        ) {
            return
        }

        val text =
            codeEditor.text
                .toString()

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
            codeEditor.text
                .toString()

        val lines =
            text.split("\n")

        val targetLine =
            line.coerceAtMost(
                lines.size
            )

        var position = 0

        for (
            i in 0 until targetLine - 1
        ) {

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
                primaryTextColor()
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

        for (
            keyword in keywords
        ) {

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

        while (
            stringMatcher.find()
        ) {

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

        while (
            commentMatcher.find()
        ) {

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

        val source =
            codeEditor.text
                .toString()

        val preview =
            TextView(this).apply {

                text =
                    if (source.isEmpty()) {

                        "KStudio is ready.

" +
                        "ファイルを開くか、コードを書いてください。"

                    } else {

                        "KStudio Preview

" +
                        "File: " +
                        currentFileName +
                        "
" +
                        "Lines: " +
                        source.lines().size +
                        "
" +
                        "Characters: " +
                        source.length +
                        "

" +
                        "▶ Run でコードの基本チェックを実行できます。"
                    }

                textSize = 16f

                setTextColor(
                    primaryTextColor()
                )

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

    // DP

    private fun dpToPx(
        dp: Int
    ): Int {

        return (
            dp *
            resources.displayMetrics.density
        ).toInt()
    }
}