package com.nida.kstudio

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import android.widget.ScrollView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import java.io.IOException

class MainActivity : Activity() {

    private lateinit var codeEditor: EditText
    private lateinit var lineNumbers: LineNumberView
    private lateinit var searchInput: EditText
    private lateinit var lineInput: EditText
    private lateinit var previewContainer: FrameLayout

    private lateinit var topMenuBar: View
    private lateinit var pageTitle: TextView
    private lateinit var accountButton: Button
    private lateinit var projectBar: View
    private lateinit var searchBar: View
    private lateinit var editorContainer: View
    private lateinit var bottomHeader: View
    private lateinit var previewHeader: View
    private lateinit var previewArea: View
    private lateinit var homeTab: View
    private lateinit var homeProjectTitle: TextView
    private lateinit var homeFileList: LinearLayout
    private lateinit var homeMoreButton: Button
    private lateinit var saveCommitOpenButton: Button
    private lateinit var projectTab: View
    private lateinit var projectList: LinearLayout
    private lateinit var fileTab: View
    private lateinit var fileBrowserPanel: View
    private lateinit var fileCreatePanel: View
    private lateinit var fileList: LinearLayout
    private lateinit var filePathView: TextView
    private lateinit var fileCreateLocation: TextView
    private lateinit var fileCreatePathInput: EditText
    private lateinit var fileCreateContentInput: EditText
    private lateinit var previewTitle: TextView
    private lateinit var swapButton: Button
    private lateinit var projectTabBackButton: Button
    private lateinit var fileTabBackButton: Button
    private lateinit var fileNewButton: Button
    private lateinit var fileNewFolderButton: Button
    private lateinit var fileUpButton: Button
    private lateinit var fileRefreshButton: Button
    private lateinit var fileCreateButton: Button
    private lateinit var fileCreateCancelButton: Button
    private lateinit var historyTab: View
    private lateinit var historyText: LinearLayout
    private lateinit var historyClearButton: Button
    private lateinit var historyTabBackButton: Button
    private lateinit var fullscreenButton: Button
    private lateinit var undoButton: Button
    private lateinit var redoButton: Button
    private lateinit var saveButton: Button
    private lateinit var saveCommitPanel: LinearLayout
    private lateinit var saveCommitFileNameInput: EditText
    private lateinit var saveCommitMessageInput: EditText
    private lateinit var saveCommitButton: Button
    private lateinit var saveCommitCancelButton: Button
    private var imagePathPopup: PopupWindow? = null
    private var searchSuggestionsPopup: PopupWindow? = null
    private lateinit var fileCreateHiddenButton: Button
    private var fileCreateHidden = false

    private data class ManagedEntry(
        val uri: Uri,
        val name: String,
        val mimeType: String,
        val isDirectory: Boolean
    )

    private val undoStack = java.util.ArrayDeque<String>()
    private val redoStack = java.util.ArrayDeque<String>()
    private val recentFolders = java.util.ArrayList<Uri>()

    private var highlighting = false
    private var autoPairing = false
    private var autoPairingEnabled = true
    private var historyApplying = false
    private var isDirty = false
    private var fullscreen = false
    private var editorPreviewSwapped = false
    private var projectName = "App"
    private var currentScreen = "HOME"
    private var backWarningShowing = false
    private val lockHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val lockHeartbeat = object : Runnable {
        override fun run() {
            currentFileUri?.let { acquireFileLock(it) }
            if (currentScreen == "EDITOR") {
                lockHandler.postDelayed(this, 30_000L)
            }
        }
    }

    private var folderRootUri: Uri? = null
    private var currentFolderUri: Uri? = null
    private var currentFileUri: Uri? = null
    private var currentFileName = "新規ファイル"
    private val currentFolderStack = java.util.ArrayList<Uri>()

    private companion object {
        const val REQUEST_UPLOAD_FILE = 4101
        const val REQUEST_UPLOAD_FOLDER = 4102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                OnBackInvokedCallback { handleBackNavigation() }
            )
        }

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

        previewArea = findViewById(R.id.previewArea)
        homeTab = findViewById(R.id.homeTab)
        homeProjectTitle = findViewById(R.id.homeProjectTitle)
        homeFileList = findViewById(R.id.homeFileList)
        setupHomeAndSaveUi()
        projectTab = findViewById(R.id.projectTab)
        projectList = findViewById(R.id.projectList)
        fileTab = findViewById(R.id.fileTab)
        fileBrowserPanel = findViewById(R.id.fileBrowserPanel)
        fileCreatePanel = findViewById(R.id.fileCreatePanel)
        fileList = findViewById(R.id.fileList)
        filePathView = findViewById(R.id.filePathView)
        fileCreateLocation = findViewById(R.id.fileCreateLocation)
        fileCreatePathInput = findViewById(R.id.fileCreatePathInput)
        fileCreateContentInput = findViewById(R.id.fileCreateContentInput)
        previewTitle = findViewById(R.id.previewTitle)
        swapButton = findViewById(R.id.swapButton)
        projectTabBackButton = findViewById(R.id.projectTabBackButton)
        fileTabBackButton = findViewById(R.id.fileTabBackButton)
        fileNewButton = findViewById(R.id.fileNewButton)
        fileNewFolderButton = findViewById(R.id.fileNewFolderButton)
        fileUpButton = findViewById(R.id.fileUpButton)
        fileRefreshButton = findViewById(R.id.fileRefreshButton)
        fileCreateButton = findViewById(R.id.fileCreateButton)
        fileCreateCancelButton = findViewById(R.id.fileCreateCancelButton)
        historyTab = findViewById(R.id.historyTab)
        historyText = findViewById(R.id.historyText)
        historyClearButton = findViewById(R.id.historyClearButton)
        historyTabBackButton = findViewById(R.id.historyTabBackButton)

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

        menuButton.setTextColor(primaryTextColor())

        pageTitle = findViewById(R.id.pageTitle)
        accountButton = findViewById(R.id.accountButton)

        fullscreenButton =
            findViewById(
                R.id.fullscreenButton
            )

        undoButton = findViewById(R.id.undoButton)
        redoButton = findViewById(R.id.redoButton)
        saveButton = findViewById(R.id.saveButton)

        loadRecentFolders()

        projectName =
            getSharedPreferences(
                "KStudio",
                Context.MODE_PRIVATE
            )
                .getString(
                    "projectName",
                    "App"
                ) ?: "App"

        loadEditorSettings()
        applySystemTheme()
        updateHistoryButtons()
        updateEditorHeader()

        // Editor

        codeEditor.setText("")

        showStartupScreen()

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
                        currentFileUri?.let { acquireFileLock(it) }
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

            if (event.isCtrlPressed &&
                keyCode == KeyEvent.KEYCODE_Z
            ) {
                if (event.isShiftPressed) {
                    redoEditor()
                } else {
                    undoEditor()
                }
                return@setOnKeyListener true
            }

            if (event.isCtrlPressed &&
                keyCode == KeyEvent.KEYCODE_Y
            ) {
                redoEditor()
                return@setOnKeyListener true
            }

            if (event.isCtrlPressed &&
                keyCode == KeyEvent.KEYCODE_S
            ) {
                saveCurrentFile()
                return@setOnKeyListener true
            }

            if (keyCode == KeyEvent.KEYCODE_DEL) {
                return@setOnKeyListener handleDeletePair()
            }

            if (keyCode == KeyEvent.KEYCODE_TAB) {
                insertTextAtCursor(" ".repeat(getTabWidth()))
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
                autoPairingEnabled &&
                (typed == '}' || typed == ')' || typed == ']' || typed == '"' || typed == '\'') &&
                next == typed &&
                codeEditor.selectionStart == codeEditor.selectionEnd
            ) {
                codeEditor.setSelection(cursor + 1)
                return@setOnKeyListener true
            }

            if (autoPairingEnabled) {
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
            } else {
                false
            }
        }


        codeEditor.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    showImagePathSuggestionsIfNeeded()
                }
                override fun afterTextChanged(s: Editable?) {}
            }
        )

        searchInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    showSearchSuggestionsIfNeeded()
                }
                override fun afterTextChanged(s: Editable?) {}
            }
        )

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

        configurePageHeader("HOME", true)
        menuButton.setOnClickListener { showMainMenu(menuButton) }
        accountButton.setOnClickListener { showAccountDialog() }

        swapButton.setOnClickListener {
            swapEditorAndPreview()
        }

        projectTabBackButton.setOnClickListener {
            showHomeTab()
        }

        fileTabBackButton.setOnClickListener {
            showHomeTab()
        }

        fileNewButton.setOnClickListener {
            showCreateFileTab()
        }

        fileNewFolderButton.setOnClickListener {
            val folder = currentFolderUri
            if (folder != null) {
                askCreateFolder(folder) {
                    renderFileBrowser()
                }
            } else {
                showNoFolderToast()
            }
        }

        fileUpButton.setOnClickListener {
            navigateFileManagerUp()
        }

        fileRefreshButton.setOnClickListener {
            renderFileBrowser()
        }

        fileCreateCancelButton.setOnClickListener {
            showFileBrowserTab()
        }

        fileCreateButton.setOnClickListener {
            createGitHubStyleFile()
        }

        historyTabBackButton.setOnClickListener {
            showHomeTab()
        }

        historyClearButton.setOnClickListener {
            clearHistory()
        }

        // Fullscreen

        fullscreenButton.setOnClickListener {

            togglePreviewFullscreen()
        }

        // Initial

        lineNumbers.invalidate()

        if (savedInstanceState != null) {

            historyApplying = true

            codeEditor.setText(
                savedInstanceState.getString(
                    "editorText",
                    ""
                )
            )

            historyApplying = false

            currentFileName =
                savedInstanceState.getString(
                    "fileName",
                    currentFileName
                )

            val savedFileUri =
                savedInstanceState.getString(
                    "fileUri"
                )

            currentFileUri =
                savedFileUri?.let {
                    Uri.parse(it)
                }

            isDirty =
                savedInstanceState.getBoolean(
                    "dirty",
                    false
                )

            editorPreviewSwapped =
                savedInstanceState.getBoolean(
                    "editorPreviewSwapped",
                    false
                )

            projectName =
                savedInstanceState.getString(
                    "projectName",
                    projectName
                ) ?: "App"

            updateEditorHeader()
            updateHistoryButtons()
        }

        updatePreview()

        if (
            savedInstanceState == null &&
            folderRootUri == null
        ) {
            openFolderPicker()
        } else if (
            folderRootUri == null
        ) {
            showStartupScreen()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT < 33) {
            handleBackNavigation()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            releaseFileLock(currentFileUri)
        }
        lockHandler.removeCallbacks(lockHeartbeat)
        super.onDestroy()
    }

    private fun handleBackNavigation() {
        if (backWarningShowing) return

        when (currentScreen) {
            "EDITOR" -> {
                if (isDirty) {
                    backWarningShowing = true
                    showKStudioConfirm(
                        "編集内容が保存されていません",
                        "この画面を離れると、保存していない変更が失われる可能性があります。",
                        "離れる"
                    ) {
                        backWarningShowing = false
                        releaseFileLock(currentFileUri)
                        isDirty = false
                        showHomeTab()
                    }
                } else {
                    releaseFileLock(currentFileUri)
                    showHomeTab()
                }
            }
            "PROJECTS", "HISTORY" -> showHomeTab()
            "FILES" -> {
                when {
                    fileCreatePanel.visibility == View.VISIBLE -> showFileBrowserTab()
                    currentFolderStack.isNotEmpty() -> navigateFileManagerUp()
                    else -> showHomeTab()
                }
            }
            "HOME" -> finish()
            else -> finish()
        }
    }

    private fun configurePageHeader(title: String, isHome: Boolean) {
        pageTitle.text = title
        accountButton.visibility = if (isHome) View.VISIBLE else View.GONE
        val menu = findViewById<Button>(R.id.menuButton)
        menu.text = if (isHome) "≡" else "←"
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {

        outState.putString(
            "editorText",
            codeEditor.text.toString()
        )

        outState.putString(
            "fileName",
            currentFileName
        )

        outState.putString(
            "fileUri",
            currentFileUri?.toString()
        )

        outState.putBoolean(
            "dirty",
            isDirty
        )

        outState.putBoolean(
            "editorPreviewSwapped",
            editorPreviewSwapped
        )

        outState.putString(
            "projectName",
            projectName
        )

        super.onSaveInstanceState(
            outState
        )
    }

    private fun showSearchSuggestionsIfNeeded() {
    if (!isSearchSuggestionsEnabled()) {
        hideSearchSuggestions()
        return
    }

    val query = searchInput.text.toString().trim()
    if (query.isEmpty()) {
        hideSearchSuggestions()
        return
    }

    val words = Regex("[A-Za-z_][A-Za-z0-9_]{2,}")
        .findAll(codeEditor.text.toString())
        .map { it.value }
        .filter { it.contains(query, ignoreCase = true) }
        .distinct()
        .take(8)
        .toList()

    if (words.isEmpty()) {
        hideSearchSuggestions()
        return
    }

    hideSearchSuggestions()

    val list = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            dpToPx(6),
            dpToPx(6),
            dpToPx(6),
            dpToPx(6)
        )
        background = roundedBackground(surfaceColor(), 12)
    }

    words.forEach { word ->
        val item = Button(this).apply {
            text = word
            textSize = 14f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            styleKStudioButton(this)
            setOnClickListener {
                searchInput.setText(word)
                searchInput.setSelection(word.length)
                hideSearchSuggestions()
            }
        }
        list.addView(
            item,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(42)
            )
        )
    }

    searchSuggestionsPopup = PopupWindow(
        list,
        minOf(
            dpToPx(320),
            resources.displayMetrics.widthPixels - dpToPx(24)
        ),
        ViewGroup.LayoutParams.WRAP_CONTENT,
        true
    ).apply {
        isOutsideTouchable = true
        elevation = dpToPx(8).toFloat()
        setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        )
    }

    searchSuggestionsPopup?.showAsDropDown(
        searchInput,
        0,
        dpToPx(4)
    )
    }

    private fun hideSearchSuggestions() {
    searchSuggestionsPopup?.dismiss()
    searchSuggestionsPopup = null
    }

    private fun showEditorScreen() {
        if (::saveCommitPanel.isInitialized) saveCommitPanel.visibility = View.GONE
        homeTab.visibility = View.GONE
        editorContainer.visibility = View.VISIBLE
        previewArea.visibility = View.VISIBLE
        projectTab.visibility = View.GONE
        fileTab.visibility = View.GONE
        historyTab.visibility = View.GONE
        bottomHeader.visibility = View.VISIBLE
        searchBar.visibility = View.VISIBLE
        projectBar.visibility = View.VISIBLE

        previewTitle.text = "Preview"
        currentScreen = "EDITOR"
        configurePageHeader(currentFileName.ifBlank { "コード" }, false)
        findViewById<Button>(R.id.menuButton).setOnClickListener { handleBackNavigation() }

        applyEditorPreviewOrder()
        updatePreview()
    }
    private fun setupHomeAndSaveUi() {
        homeProjectTitle.visibility = View.GONE

        saveCommitOpenButton = Button(this).apply {
            text = "編集を保存"
            textSize = 12f
            minWidth = 0
            styleKStudioButton(this)
            setOnClickListener { showSaveCommitPanel() }
        }

        val projectLayout = projectBar as LinearLayout
        val title = findViewById<TextView>(R.id.projectName)
        title.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        projectLayout.addView(
            saveCommitOpenButton,
            LinearLayout.LayoutParams(dpToPx(108), dpToPx(42)).apply {
                marginStart = dpToPx(10)
            }
        )
    }
    private fun showHomeActionsMenu(anchor: View) {
        val menu = LinearLayout(this)
        menu.orientation = LinearLayout.VERTICAL
        menu.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
        menu.setBackgroundColor(surfaceColor())

        val popup = PopupWindow(
            menu,
            dpToPx(280),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        val newFile = Button(this)
        newFile.text = "Create new file"
        newFile.setTextColor(primaryTextColor())
        setSimpleIcon(newFile, R.drawable.ic_file_simple)
        newFile.setOnClickListener {
            folderRootUri?.let {
                currentFolderUri = DocumentsContract.buildDocumentUriUsingTree(
                    it,
                    DocumentsContract.getTreeDocumentId(it)
                )
                showCreateFileTab()
            } ?: showNoFolderToast()
            popup.dismiss()
        }
        menu.addView(newFile)

        val newFolder = Button(this)
        newFolder.text = "Create new folder"
        newFolder.setTextColor(primaryTextColor())
        setSimpleIcon(newFolder, R.drawable.ic_folder_simple)
        newFolder.setOnClickListener {
            folderRootUri?.let {
                val rootDocument = DocumentsContract.buildDocumentUriUsingTree(
                    it,
                    DocumentsContract.getTreeDocumentId(it)
                )
                askCreateFolder(rootDocument) { showHomeTab() }
            } ?: showNoFolderToast()
            popup.dismiss()
        }
        menu.addView(newFolder)

        val upload = Button(this)
        upload.text = "Upload file"
        upload.setTextColor(primaryTextColor())
        setSimpleIcon(upload, R.drawable.ic_file_simple)
        upload.setOnClickListener {
            openUploadFilePicker()
            popup.dismiss()
        }
        menu.addView(upload)

        val uploadFolder = Button(this)
        uploadFolder.text = "Upload folder"
        uploadFolder.setTextColor(primaryTextColor())
        setSimpleIcon(uploadFolder, R.drawable.ic_folder_simple)
        uploadFolder.setOnClickListener {
            openUploadFolderPicker()
            popup.dismiss()
        }
        menu.addView(uploadFolder)

        val search = Button(this)
        search.text = "Search file"
        search.setTextColor(primaryTextColor())
        setSimpleIcon(search, R.drawable.ic_search_simple)
        search.setOnClickListener {
            showFileSearchDialog()
            popup.dismiss()
        }
        menu.addView(search)

        popup.showAsDropDown(
            anchor,
            -dpToPx(230),
            dpToPx(4)
        )
    }

    private fun showFileSearchDialog() {
        showKStudioInputDialog(
            "ファイル検索",
            "ファイル名の一部を入力してください。",
            "ファイル名",
            "",
            "検索"
        ) { keywordRaw ->
            val keyword = keywordRaw.trim()
            if (keyword.isEmpty()) return@showKStudioInputDialog

            val root = folderRootUri ?: return@showKStudioInputDialog
            val results = queryFolder(root).filter {
                !it.isDirectory && it.name.contains(keyword, true)
            }
            if (results.isEmpty()) {
                showKStudioMessage("検索結果", "該当するファイルがありません。")
                return@showKStudioInputDialog
            }

            showKStudioChoiceDialog(
                "検索結果",
                results.map { it.name }
            ) { which ->
                val entry = results.getOrNull(which) ?: return@showKStudioChoiceDialog
                openManagedFile(entry.uri, entry.name)
            }
        }
    }
    private fun showSaveCommitPanel() {
        if (currentFileUri == null) {
            showKStudioNotice("No file to save")
            return
        }

        if (!::saveCommitPanel.isInitialized) {
            buildSaveCommitPanel()
        }

        saveCommitFileNameInput.setText(currentFileName)
        saveCommitMessageInput.setText("")

        topMenuBar.visibility = View.GONE
        projectBar.visibility = View.GONE
        searchBar.visibility = View.GONE
        bottomHeader.visibility = View.GONE
        homeTab.visibility = View.GONE
        editorContainer.visibility = View.GONE
        previewArea.visibility = View.GONE
        projectTab.visibility = View.GONE
        fileTab.visibility = View.GONE
        historyTab.visibility = View.GONE
        saveCommitPanel.visibility = View.VISIBLE
    }

    private fun buildSaveCommitPanel() {
        saveCommitPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(16))
            setBackgroundColor(surfaceColor())
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val backButton = Button(this).apply {
            text = "←"
            textSize = 22f
            contentDescription = "戻る"
            styleKStudioButton(this)
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            setOnClickListener {
                saveCommitPanel.visibility = View.GONE
                showEditorScreen()
            }
        }
        header.addView(backButton, LinearLayout.LayoutParams(dpToPx(54), dpToPx(48)))

        header.addView(
            TextView(this).apply {
                text = "保存"
                textSize = 21f
                setTextColor(primaryTextColor())
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(12), 0, 0, 0)
            },
            LinearLayout.LayoutParams(0, dpToPx(48), 1f)
        )

        saveCommitButton = Button(this).apply {
            text = "保存"
            textSize = 15f
            styleKStudioButton(this)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.rgb(40, 170, 90))
                cornerRadius = dpToPx(10).toFloat()
            }
            setTextColor(Color.WHITE)
            setPadding(dpToPx(12), 0, dpToPx(12), 0)
            setOnClickListener { saveEditCommit() }
        }
        header.addView(saveCommitButton, LinearLayout.LayoutParams(dpToPx(88), dpToPx(48)))
        saveCommitPanel.addView(header)

        val divider = View(this).apply {
            setBackgroundColor(if (isDarkMode()) Color.rgb(70, 70, 74) else Color.rgb(220, 224, 228))
        }
        saveCommitPanel.addView(
            divider,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)).apply {
                topMargin = dpToPx(8)
                bottomMargin = dpToPx(18)
            }
        )

        saveCommitPanel.addView(
            TextView(this).apply {
                text = "ファイル名"
                textSize = 14f
                setTextColor(secondaryTextColor())
                includeFontPadding = true
                setPadding(0, 0, 0, dpToPx(6))
            }
        )

        saveCommitFileNameInput = EditText(this).apply {
            setSingleLine(true)
            isEnabled = false
            textSize = 16f
            includeFontPadding = true
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            background = roundedBackground(editorSurfaceColor(), 10)
        }
        saveCommitPanel.addView(
            saveCommitFileNameInput,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(52)).apply {
                bottomMargin = dpToPx(24)
            }
        )

        saveCommitPanel.addView(
            TextView(this).apply {
                text = "説明"
                textSize = 14f
                setTextColor(secondaryTextColor())
                includeFontPadding = true
                setPadding(0, 0, 0, dpToPx(6))
            }
        )

        saveCommitMessageInput = EditText(this).apply {
            hint = "編集内容を入力"
            textSize = 16f
            gravity = Gravity.TOP or Gravity.START
            minLines = 6
            includeFontPadding = true
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            background = roundedBackground(editorSurfaceColor(), 10)
        }
        saveCommitPanel.addView(
            saveCommitMessageInput,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(150))
        )

        findViewById<ViewGroup>(R.id.workArea).addView(
            saveCommitPanel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun saveEditCommit() {
        val descriptionText = saveCommitMessageInput.text.toString().trim()
        val description = if (descriptionText.isEmpty()) "編集を保存" else descriptionText

        saveCurrentFile {
            saveEditHistory(currentFileName, description)
            saveCommitPanel.visibility = View.GONE
            showEditorScreen()
        }
    }

    private fun saveEditHistory(
        fileName: String,
        description: String
    ) {
        prependHistoryEntry(
            type = "save",
            detail = fileName,
            success = true,
            extra = description
        )
    }

    // Main Menu

    private fun showMainMenu(anchor: View) {
        val root = FrameLayout(this)

        val scrim = View(this).apply {
            setBackgroundColor(Color.argb(105, 0, 0, 0))
            alpha = 0f
        }
        root.addView(
            scrim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val panelWidth = minOf(
            dpToPx(340),
            resources.displayMetrics.widthPixels
        )

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dpToPx(18),
                dpToPx(28),
                dpToPx(18),
                dpToPx(18)
            )
            background = roundedBackground(surfaceColor(), 0)
        }

        val title = TextView(this).apply {
            text = "KStudio"
            textSize = 24f
            setTextColor(primaryTextColor())
            setPadding(0, 0, 0, dpToPx(20))
        }
        panel.addView(title)

        fun addItem(label: String, action: () -> Unit, icon: Int? = null) {
            val button = Button(this).apply {
                text = label
                textSize = 16f
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                if (icon != null) setSimpleIcon(this, icon)
                styleKStudioButton(this)
                setOnClickListener { action() }
            }
            panel.addView(
                button,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(54)
                ).apply { bottomMargin = dpToPx(4) }
            )
        }

        addItem("Home", { showHomeTab() }, R.drawable.ic_project_simple)
        addItem("フォルダ", { openFolderPicker() }, R.drawable.ic_folder_simple)
        addItem("履歴", { showHistory() })
        addItem("プロジェクト一覧", { showProjects() })
        addItem("ファイル管理", { showFileManager() })
        addItem("設定", { showSettingsDialog() })
        addItem("コミュニティ", { startActivity(Intent(this, CommunityActivity::class.java)) })
        addItem("アカウント", { showAccountDialog() })

        val close = Button(this).apply {
            text = "閉じる"
            styleKStudioButton(this)
        }
        panel.addView(
            close,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(54)
            ).apply { topMargin = dpToPx(12) }
        )

        root.addView(
            panel,
            FrameLayout.LayoutParams(
                panelWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.TOP
            )
        )

        val drawer = PopupWindow(
            root,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = dpToPx(16).toFloat()
            setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            )
        }

        fun dismissDrawer() {
            panel.translationX = -panelWidth.toFloat()
            scrim.alpha = 0f
            drawer.dismiss()
        }

        scrim.setOnClickListener { dismissDrawer() }
        close.setOnClickListener { dismissDrawer() }

        for (i in 0 until panel.childCount) {
            val child = panel.getChildAt(i)
            if (child is Button && child !== close) {
                child.setOnClickListener {
                    when (child.text.toString()) {
                        "Home" -> showHomeTab()
                        "フォルダ" -> openFolderPicker()
                        "履歴" -> showHistory()
                        "プロジェクト一覧" -> showProjects()
                        "ファイル管理" -> showFileManager()
                        "設定" -> showSettingsDialog()
                        "コミュニティ" -> startActivity(Intent(this, CommunityActivity::class.java))
                        "アカウント" -> showAccountDialog()
                    }
                    dismissDrawer()
                }
            }
        }

        drawer.showAtLocation(
            topMenuBar,
            Gravity.START or Gravity.TOP,
            0,
            0
        )

        panel.translationX = 0f
        panel.alpha = 1f
        scrim.alpha = 1f
    }

    private fun showSettingsDialog() {
        val root = FrameLayout(this)
        val scrim = View(this).apply {
            setBackgroundColor(Color.argb(110, 0, 0, 0))
            alpha = 0f
        }
        root.addView(
            scrim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val panelWidth = resources.displayMetrics.widthPixels

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dpToPx(12),
                dpToPx(16),
                dpToPx(12),
                dpToPx(12)
            )
            background = roundedBackground(surfaceColor(), 0)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "設定"
            textSize = 24f
            setTextColor(primaryTextColor())
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        header.addView(
            title,
            LinearLayout.LayoutParams(0, dpToPx(54), 1f)
        )

        lateinit var popup: PopupWindow
        val close = Button(this).apply {
            text = "閉じる"
            styleKStudioButton(this)
        }
        header.addView(
            close,
            LinearLayout.LayoutParams(dpToPx(82), dpToPx(44))
        )
        panel.addView(header)

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(4), 0, dpToPx(12))
        }
        scroll.addView(
            content,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        panel.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        fun addSection(textValue: String) {
            content.addView(
                TextView(this).apply {
                    text = textValue
                    textSize = 14f
                    setTextColor(secondaryTextColor())
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(
                        dpToPx(4),
                        dpToPx(14),
                        dpToPx(4),
                        dpToPx(6)
                    )
                }
            )
        }

        fun addSettingRow(
            titleText: String,
            description: String,
            currentText: () -> String,
            onClick: () -> Unit
        ) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundedBackground(editorSurfaceColor(), 12)
                setPadding(
                    dpToPx(12),
                    dpToPx(10),
                    dpToPx(10),
                    dpToPx(10)
                )
            }

            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            texts.addView(
                TextView(this).apply {
                    text = titleText
                    textSize = 16f
                    setTextColor(primaryTextColor())
                }
            )
            texts.addView(
                TextView(this).apply {
                    text = description
                    textSize = 12f
                    setTextColor(secondaryTextColor())
                    setPadding(0, dpToPx(3), 0, 0)
                }
            )

            val action = Button(this).apply {
                text = currentText()
                textSize = 13f
                styleKStudioButton(this)
                setOnClickListener {
                    onClick()
                    text = currentText()
                }
            }

            row.addView(
                texts,
                LinearLayout.LayoutParams(0, dpToPx(68), 1f)
            )
            row.addView(
                action,
                LinearLayout.LayoutParams(dpToPx(104), dpToPx(48))
            )
            content.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(6)
                }
            )
        }

        fun addChoiceRow(
            titleText: String,
            description: String,
            values: List<String>,
            current: () -> String,
            onChange: (String) -> Unit
        ) {
            addSettingRow(
                titleText,
                description,
                current
            ) {
                val currentValue = current()
                val index = values.indexOf(currentValue).coerceAtLeast(0)
                onChange(values[(index + 1) % values.size])
            }
        }

        addSection("エディタ")

        addSettingRow(
            "自動かっこ補完",
            "括弧・クォートを入力したときに閉じ側を自動入力",
            { if (autoPairingEnabled) "ON" else "OFF" }
        ) {
            autoPairingEnabled = !autoPairingEnabled
            getSharedPreferences("KStudio", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("auto_pairing", autoPairingEnabled)
                .apply()
        }

        addChoiceRow(
            "Tab幅",
            "Tabキーで入れるスペース数",
            listOf("2", "4", "8"),
            { getTabWidth().toString() }
        ) { value ->
            getSharedPreferences("KStudio", Context.MODE_PRIVATE)
                .edit()
                .putInt("tab_width", value.toInt())
                .apply()
        }

        addSettingRow(
            "行番号",
            "エディタ左側の行番号を表示",
            {
                if (lineNumbers.visibility == View.VISIBLE) "ON" else "OFF"
            }
        ) {
            val enabled = lineNumbers.visibility != View.VISIBLE
            getSharedPreferences("KStudio", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("line_numbers", enabled)
                .apply()
            applyEditorSettings()
        }

        addSettingRow(
            "検索候補",
            "コード内の単語を検索候補として表示",
            { if (isSearchSuggestionsEnabled()) "ON" else "OFF" }
        ) {
            val enabled = !isSearchSuggestionsEnabled()
            getSharedPreferences("KStudio", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("search_suggestions", enabled)
                .apply()
            if (!enabled) hideSearchSuggestions()
        }

        addChoiceRow(
            "文字サイズ",
            "エディタの文字サイズ",
            listOf("12sp", "14sp", "15sp", "16sp", "18sp", "20sp"),
            { getEditorFontSize().toString() + "sp" }
        ) { value ->
            getSharedPreferences("KStudio", Context.MODE_PRIVATE)
                .edit()
                .putInt("font_size", value.removeSuffix("sp").toInt())
                .apply()
            applyEditorSettings()
        }

        addSettingRow(
            "長い行の折り返し",
            "長いコードを画面幅で折り返す",
            { if (isLineWrapEnabled()) "ON" else "OFF" }
        ) {
            val enabled = !isLineWrapEnabled()
            getSharedPreferences("KStudio", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("line_wrap", enabled)
                .apply()
            applyEditorSettings()
        }

        addSection("表示")

        addChoiceRow(
            "テーマ",
            "ライト・ダーク・システム設定",
            listOf("システム", "ライト", "ダーク"),
            { getThemeModeLabel() }
        ) { value ->
            val mode = when (value) {
                "ライト" -> "light"
                "ダーク" -> "dark"
                else -> "system"
            }
            getSharedPreferences("KStudio", Context.MODE_PRIVATE)
                .edit()
                .putString("theme_mode", mode)
                .apply()
            popup.dismiss()
            recreate()
        }

        addSection("履歴")

        addSettingRow(
            "履歴",
            "Run・編集保存の履歴を記録",
            { if (isHistoryEnabled()) "ON" else "OFF" }
        ) {
            val enabled = !isHistoryEnabled()
            getSharedPreferences("KStudio", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("history_enabled", enabled)
                .apply()
        }

        addChoiceRow(
            "自動削除期間",
            "古い履歴を自動で削除",
            listOf("なし", "7日", "30日", "90日", "1年"),
            { getHistoryRetentionLabel() }
        ) { value ->
            val days = when (value) {
                "7日" -> 7
                "30日" -> 30
                "90日" -> 90
                "1年" -> 365
                else -> -1
            }
            getSharedPreferences("KStudio", Context.MODE_PRIVATE)
                .edit()
                .putInt("history_retention_days", days)
                .apply()
            purgeHistory()
        }

        content.addView(
            TextView(this).apply {
                text = "保存履歴はカードをタップすると、保存時に入力した編集内容の説明を確認できます。"
                textSize = 12f
                setTextColor(secondaryTextColor())
                setPadding(
                    dpToPx(4),
                    dpToPx(8),
                    dpToPx(4),
                    dpToPx(18)
                )
            }
        )

        root.addView(
            panel,
            FrameLayout.LayoutParams(
                panelWidth,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END
            )
        )

        popup = PopupWindow(
            root,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = dpToPx(16).toFloat()
            setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            )
        }

        close.setOnClickListener {
            closeKStudioPopup(popup, panel, true)
        }
        scrim.setOnClickListener {
            closeKStudioPopup(popup, panel, true)
        }

        popup.showAtLocation(
            topMenuBar,
            Gravity.START or Gravity.TOP,
            0,
            0
        )

        panel.translationX = 0f
        panel.alpha = 1f
        scrim.alpha = 1f
    }

    private fun showKStudioChoiceDialog(
        titleText: String,
        options: List<String>,
        onChoice: (Int) -> Unit
    ) {
        val root = FrameLayout(this)
        val scrim = View(this).apply { setBackgroundColor(Color.argb(110, 0, 0, 0)) }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(18), dpToPx(18), dpToPx(18), dpToPx(18))
            background = roundedBackground(surfaceColor(), 16)
        }
        panel.addView(TextView(this).apply {
            text = titleText
            textSize = 20f
            setTextColor(primaryTextColor())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        lateinit var popup: PopupWindow

        options.forEachIndexed { index, option ->
            panel.addView(
                Button(this).apply {
                    text = option
                    textSize = 14f
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    styleKStudioButton(this)
                    setOnClickListener {
                        popup.dismiss()
                        onChoice(index)
                    }
                },
                LinearLayout.LayoutParams(-1, dpToPx(46)).apply {
                    topMargin = if (index == 0) dpToPx(12) else dpToPx(10)
                }
            )
        }

        root.addView(panel, FrameLayout.LayoutParams(
            minOf(dpToPx(390), resources.displayMetrics.widthPixels - dpToPx(32)),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        popup = PopupWindow(root, -1, -1, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        scrim.setOnClickListener { popup.dismiss() }
        popup.showAtLocation(topMenuBar, Gravity.CENTER, 0, 0)
    }
    private fun showKStudioInputDialog(
        titleText: String,
        message: String?,
        hint: String,
        initial: String,
        confirmText: String,
        onConfirm: (String) -> Unit
    ) {
        val root = FrameLayout(this)
        val scrim = View(this).apply { setBackgroundColor(Color.argb(110, 0, 0, 0)) }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(18), dpToPx(20), dpToPx(18))
            background = roundedBackground(surfaceColor(), 16)
        }
        panel.addView(TextView(this).apply {
            text = titleText
            textSize = 21f
            setTextColor(primaryTextColor())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        if (!message.isNullOrBlank()) {
            panel.addView(TextView(this).apply {
                text = message
                textSize = 13f
                setTextColor(secondaryTextColor())
                setPadding(0, dpToPx(8), 0, dpToPx(12))
            })
        }

        val input = EditText(this).apply {
            this.hint = hint
            setSingleLine(true)
            setText(initial)
            selectAll()
            setTextColor(primaryTextColor())
            setHintTextColor(secondaryTextColor())
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            background = roundedBackground(editorSurfaceColor(), 10)
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, dpToPx(52)))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancel = Button(this).apply { text = "キャンセル"; styleKStudioButton(this) }
        val confirm = Button(this).apply { text = confirmText; styleKStudioButton(this) }
        buttons.addView(cancel, LinearLayout.LayoutParams(0, dpToPx(46), 1f))
        buttons.addView(confirm, LinearLayout.LayoutParams(0, dpToPx(46), 1f).apply { marginStart = dpToPx(12) })
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dpToPx(58)).apply { topMargin = dpToPx(12) })

        root.addView(panel, FrameLayout.LayoutParams(
            minOf(dpToPx(390), resources.displayMetrics.widthPixels - dpToPx(32)),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        lateinit var popup: PopupWindow
        popup = PopupWindow(root, -1, -1, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        cancel.setOnClickListener { popup.dismiss() }
        scrim.setOnClickListener { popup.dismiss() }
        confirm.setOnClickListener {
            val value = input.text.toString()
            popup.dismiss()
            onConfirm(value)
        }
        popup.showAtLocation(topMenuBar, Gravity.CENTER, 0, 0)
        input.requestFocus()
    }
    private fun showKStudioThreeChoice(
        titleText: String,
        message: String,
        firstText: String,
        secondText: String,
        thirdText: String,
        firstAction: () -> Unit,
        secondAction: () -> Unit
    ) {
        val root = FrameLayout(this)
        val scrim = View(this).apply { setBackgroundColor(Color.argb(110, 0, 0, 0)) }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(18), dpToPx(20), dpToPx(18))
            background = roundedBackground(surfaceColor(), 16)
        }
        panel.addView(TextView(this).apply {
            text = titleText
            textSize = 21f
            setTextColor(primaryTextColor())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        panel.addView(TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(primaryTextColor())
            setPadding(0, dpToPx(12), 0, dpToPx(16))
        })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        lateinit var popup: PopupWindow

        fun addAction(textValue: String, action: (() -> Unit)?) {
            val button = Button(this).apply {
                text = textValue
                textSize = 13f
                styleKStudioButton(this)
                setOnClickListener {
                    popup.dismiss()
                    action?.invoke()
                }
            }
            row.addView(button, LinearLayout.LayoutParams(0, dpToPx(46), 1f).apply {
                marginStart = if (row.childCount == 0) 0 else dpToPx(10)
            })
        }

        addAction(firstText, firstAction)
        addAction(secondText, secondAction)
        addAction(thirdText, null)
        panel.addView(row)

        root.addView(panel, FrameLayout.LayoutParams(
            minOf(dpToPx(430), resources.displayMetrics.widthPixels - dpToPx(24)),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        popup = PopupWindow(root, -1, -1, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        scrim.setOnClickListener { popup.dismiss() }
        popup.showAtLocation(topMenuBar, Gravity.CENTER, 0, 0)
    }
    private fun showAccountDialog() {
        val root = FrameLayout(this)
        val scrim = View(this).apply { setBackgroundColor(Color.argb(110, 0, 0, 0)) }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(18), dpToPx(20), dpToPx(18))
            background = roundedBackground(surfaceColor(), 16)
        }
        panel.addView(TextView(this).apply {
            text = "アカウント"
            textSize = 22f
            setTextColor(primaryTextColor())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        panel.addView(TextView(this).apply {
            text = "コミュニティや共同コーディングで表示する登録名です。"
            textSize = 13f
            setTextColor(secondaryTextColor())
            setPadding(0, dpToPx(8), 0, dpToPx(12))
        })
        val input = EditText(this).apply {
            hint = "アカウント名"
            setSingleLine(true)
            setText(getAccountName())
            selectAll()
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, dpToPx(52)))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancel = Button(this).apply { text = "キャンセル"; styleKStudioButton(this) }
        val save = Button(this).apply { text = "保存"; styleKStudioButton(this) }
        buttons.addView(cancel, LinearLayout.LayoutParams(0, dpToPx(46), 1f))
        buttons.addView(save, LinearLayout.LayoutParams(0, dpToPx(46), 1f).apply { marginStart = dpToPx(12) })
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dpToPx(58)).apply { topMargin = dpToPx(12) })

        root.addView(panel, FrameLayout.LayoutParams(
            minOf(dpToPx(380), resources.displayMetrics.widthPixels - dpToPx(32)),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        lateinit var popup: PopupWindow
        popup = PopupWindow(root, -1, -1, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        cancel.setOnClickListener { popup.dismiss() }
        scrim.setOnClickListener { popup.dismiss() }
        save.setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isBlank() || name.length > 40) {
                popup.dismiss()
                showKStudioMessage("アカウント", "アカウント名は1〜40文字で設定してください。")
                return@setOnClickListener
            }
            getSharedPreferences("KStudio", Context.MODE_PRIVATE).edit()
                .putString("account_name", name)
                .apply()
            popup.dismiss()
            if (currentScreen == "HOME") renderHomeFiles()
        }
        popup.showAtLocation(topMenuBar, Gravity.CENTER, 0, 0)
    }
    private fun showKStudioNotice(message: String) {
        val textView = TextView(this).apply {
            text = message
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
            background = roundedBackground(Color.rgb(45, 45, 48), 12)
        }
        val popup = PopupWindow(
            textView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = false
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        popup.showAtLocation(topMenuBar, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dpToPx(24))
        textView.postDelayed({ popup.dismiss() }, 1600L)
    }

    private fun showKStudioMessage(titleText: String, message: String) {
        val root = FrameLayout(this)
        val scrim = View(this).apply {
            setBackgroundColor(Color.argb(110, 0, 0, 0))
        }
        root.addView(
            scrim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dpToPx(20),
                dpToPx(18),
                dpToPx(20),
                dpToPx(18)
            )
            background = roundedBackground(surfaceColor(), 16)
        }

        panel.addView(
            TextView(this).apply {
                text = titleText
                textSize = 21f
                setTextColor(primaryTextColor())
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
        )
        panel.addView(
            TextView(this).apply {
                text = message
                textSize = 15f
                setTextColor(primaryTextColor())
                setPadding(0, dpToPx(14), 0, dpToPx(14))
            }
        )

        val close = Button(this).apply {
            text = "閉じる"
            styleKStudioButton(this)
        }
        panel.addView(
            close,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(46)
            )
        )

        root.addView(
            panel,
            FrameLayout.LayoutParams(
                minOf(
                    dpToPx(360),
                    resources.displayMetrics.widthPixels - dpToPx(32)
                ),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        lateinit var popup: PopupWindow
        popup = PopupWindow(
            root,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            )
        }

        close.setOnClickListener {
            closeKStudioPopup(popup, panel, false)
        }
        scrim.setOnClickListener {
            closeKStudioPopup(popup, panel, false)
        }

        popup.showAtLocation(topMenuBar, Gravity.CENTER, 0, 0)
        panel.alpha = 1f
        panel.scaleX = 1f
        panel.scaleY = 1f
    }

    private fun startNewProject() {

        val folder = folderRootUri
        if (folder == null) {
            openFolderPicker()
            return
        }

        askProjectName()
    }

    private fun askProjectName() {
        showKStudioInputDialog(
            "プロジェクト名を設定",
            "この名前はKStudioのHOMEに表示されます。実際のフォルダ名は変更しません。",
            "プロジェクト名",
            getFolderName(folderRootUri ?: Uri.EMPTY).ifBlank { "App" },
            "開始"
        ) { rawName ->
            val name = rawName.trim()
            if (name.isEmpty() || name.length > 80) {
                showKStudioMessage("プロジェクト名", "1〜80文字で入力してください。")
                return@showKStudioInputDialog
            }

            projectName = name
            getSharedPreferences("KStudio", Context.MODE_PRIVATE)
                .edit()
                .putString("projectName", projectName)
                .apply()

            createInitialProjectFile {
                renderHomeFiles()
                showEditorScreen()
                showKStudioMessage("プロジェクト", "「" + name + "」を開始しました。")
            }
        }
    }
    private fun createInitialProjectFile(
        onReady: () -> Unit
    ) {

        val root =
            currentFolderUri
                ?: run {
                    onReady()
                    return
                }

        val existing =
            findChildByName(
                root,
                "MainActivity.kt"
            )

        if (existing != null) {
            if (existing.isDirectory) {
                showKStudioNotice("MainActivity.kt というフォルダが既にあります")
                onReady()
                return
            }

            openManagedFile(
                existing.uri,
                existing.name
            )
            onReady()
            return
        }

        try {

            val fileUri =
                DocumentsContract.createDocument(
                    contentResolver,
                    root,
                    "text/plain",
                    "MainActivity.kt"
                ) ?: throw IllegalStateException(
                    "MainActivity.kt を作成できませんでした"
                )

            val starter =
                "package com.example.app\n\n" +
                "import android.app.Activity\n" +
                "import android.os.Bundle\n\n" +
                "class MainActivity : Activity() {\n\n" +
                "    override fun onCreate(savedInstanceState: Bundle?) {\n" +
                "        super.onCreate(savedInstanceState)\n" +
                "    }\n" +
                "}\n"

            contentResolver
                .openOutputStream(
                    fileUri,
                    "wt"
                )
                ?.use { stream ->
                    stream.write(
                        starter.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }
                ?: throw IllegalStateException(
                    "初期ファイルを書き込めませんでした"
                )

            historyApplying = true
            codeEditor.setText(starter)
            historyApplying = false

            currentFileUri = fileUri
            currentFileName = "MainActivity.kt"
            isDirty = false

            undoStack.clear()
            redoStack.clear()

            updateEditorHeader()
            updateHistoryButtons()
            highlightCode()
            lineNumbers.invalidate()

            onReady()

        } catch (e: Exception) {

            showKStudioNotice("初期ファイル作成に失敗しました: " +
                    (e.message ?: "unknown"))

            onReady()
        }
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
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK || data == null) return

        when (requestCode) {
            1001 -> handleProjectFolderResult(data)
            REQUEST_UPLOAD_FILE -> handleUploadFiles(data)
            REQUEST_UPLOAD_FOLDER -> handleUploadFolder(data)
        }
    }

    private fun handleProjectFolderResult(data: Intent) {
        val uri = data.data ?: return
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {}

        folderRootUri = uri
        currentFolderUri = DocumentsContract.buildDocumentUriUsingTree(
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
        askProjectName()
    }

    private fun openUploadFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, REQUEST_UPLOAD_FILE)
    }

    private fun openUploadFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_UPLOAD_FOLDER)
    }

    private fun handleUploadFiles(data: Intent) {
        val destination = currentFolderUri ?: folderRootUri ?: run {
            showNoFolderToast()
            return
        }

        try {
            val uris = ArrayList<Uri>()
            data.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
            } ?: data.data?.let { uris.add(it) }

            var count = 0
            for (uri in uris) {
                copyExternalFile(uri, destination)
                count++
            }

            renderHomeFiles()
            if (fileTab.visibility == View.VISIBLE) renderFileBrowser()
            showKStudioNotice(count.toString() + " 個のファイルをアップロードしました")
        } catch (e: Exception) {
            showKStudioNotice("アップロードに失敗しました: " + (e.message ?: "unknown"))
        }
    }

    private fun handleUploadFolder(data: Intent) {
        val destination = currentFolderUri ?: folderRootUri ?: run {
            showNoFolderToast()
            return
        }
        val source = data.data ?: return

        try {
            val name = getDocumentName(source).ifBlank { "UploadedFolder" }
            if (findChildByName(destination, name) != null) {
                throw IOException("同名のファイルまたはフォルダがあります")
            }
            val target = DocumentsContract.createDocument(
                contentResolver,
                destination,
                DocumentsContract.Document.MIME_TYPE_DIR,
                name
            ) ?: throw IOException("フォルダ作成に失敗しました")

            copyExternalFolder(source, target, source)
            renderHomeFiles()
            if (fileTab.visibility == View.VISIBLE) renderFileBrowser()
            showKStudioNotice("フォルダをアップロードしました")
        } catch (e: Exception) {
            showKStudioNotice("フォルダのアップロードに失敗しました: " + (e.message ?: "unknown"))
        }
    }

    private fun copyExternalFile(source: Uri, destination: Uri) {
        val name = getDocumentName(source).ifBlank { "uploaded_file" }
        if (findChildByName(destination, name) != null) {
            throw IOException("同名のファイルがあります: " + name)
        }

        val target = DocumentsContract.createDocument(
            contentResolver,
            destination,
            contentResolver.getType(source) ?: "application/octet-stream",
            name
        ) ?: throw IOException("ファイル作成に失敗しました")

        try {
            contentResolver.openInputStream(source).use { input ->
                contentResolver.openOutputStream(target).use { output ->
                    if (input == null || output == null) throw IOException("ファイルを開けません")
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            try { DocumentsContract.deleteDocument(contentResolver, target) } catch (_: Exception) {}
            throw e
        }
    }

    private fun copyExternalFolder(
        source: Uri,
        destination: Uri,
        treeUri: Uri
    ) {
        for (entry in queryFolderFromTree(source, treeUri)) {
            if (entry.isDirectory) {
                val target = DocumentsContract.createDocument(
                    contentResolver,
                    destination,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    entry.name
                ) ?: throw IOException("フォルダ作成に失敗しました: " + entry.name)
                copyExternalFolder(entry.uri, target, treeUri)
            } else {
                copyExternalFile(entry.uri, destination)
            }
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

    // File Manager Tab

    private fun showFileManager() {
        if (fullscreen) togglePreviewFullscreen()
        if (folderRootUri == null || currentFolderUri == null) {
            showNoFolderToast()
            return
        }

        homeTab.visibility = View.GONE
        projectTab.visibility = View.GONE
        fileTab.visibility = View.VISIBLE
        historyTab.visibility = View.GONE
        editorContainer.visibility = View.GONE
        previewArea.visibility = View.GONE
        bottomHeader.visibility = View.GONE
        searchBar.visibility = View.GONE
        projectBar.visibility = View.GONE

        currentScreen = "FILES"
        configurePageHeader("ファイル", false)
        findViewById<Button>(R.id.menuButton).setOnClickListener { handleBackNavigation() }
        showFileBrowserTab()
    }
    private fun showFileBrowserTab() {
        if (folderRootUri == null || currentFolderUri == null) {
            showNoFolderToast()
            return
        }

        fileTab.visibility = View.VISIBLE
        projectTab.visibility = View.GONE
        historyTab.visibility = View.GONE
        homeTab.visibility = View.GONE
        editorContainer.visibility = View.GONE
        previewArea.visibility = View.GONE
        bottomHeader.visibility = View.GONE
        searchBar.visibility = View.GONE
        projectBar.visibility = View.GONE
        fileBrowserPanel.visibility = View.VISIBLE
        fileCreatePanel.visibility = View.GONE

        currentScreen = "FILES"
        configurePageHeader("ファイル", false)
        findViewById<Button>(R.id.menuButton).setOnClickListener { handleBackNavigation() }
        renderFileBrowser()
    }
    private fun showCreateFileTab() {
        if (folderRootUri == null || currentFolderUri == null) {
            showNoFolderToast()
            return
        }

        projectTab.visibility = View.GONE
        fileTab.visibility = View.VISIBLE
        historyTab.visibility = View.GONE
        homeTab.visibility = View.GONE
        editorContainer.visibility = View.GONE
        previewArea.visibility = View.GONE
        bottomHeader.visibility = View.GONE
        searchBar.visibility = View.GONE
        projectBar.visibility = View.GONE
        fileBrowserPanel.visibility = View.GONE
        fileCreatePanel.visibility = View.VISIBLE

        currentScreen = "FILES"
        configurePageHeader("新規ファイル", false)
        findViewById<Button>(R.id.menuButton).setOnClickListener { handleBackNavigation() }

        fileCreateLocation.text = "作成先: " + getCurrentFolderDisplayName()
        fileCreatePathInput.setText("Main.kt")
        fileCreatePathInput.selectAll()
        fileCreateContentInput.setText("")

        if (!::fileCreateHiddenButton.isInitialized) {
            fileCreateHiddenButton = Button(this).apply {
                text = "隠しファイル: OFF"
                textSize = 13f
                styleKStudioButton(this)
                setOnClickListener {
                    fileCreateHidden = !fileCreateHidden
                    updateFileCreateHiddenButton()
                }
            }
            val createPanel = fileCreatePanel as? ViewGroup
            createPanel?.addView(
                fileCreateHiddenButton,
                (createPanel.indexOfChild(fileCreateContentInput) + 1).coerceAtLeast(0),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(46)
                ).apply {
                    topMargin = dpToPx(8)
                    bottomMargin = dpToPx(4)
                }
            )
        }

        fileCreateHidden = false
        updateFileCreateHiddenButton()
        fileCreatePathInput.requestFocus()
    }
    private fun renderFileBrowser() {

        val current =
            currentFolderUri
                ?: return

        filePathView.text =
            getCurrentFolderDisplayName()
        setSimpleIcon(filePathView, R.drawable.ic_folder_simple)

        fileList.removeAllViews()

        val entries =
            queryFolder(current)

        if (entries.isEmpty()) {

            val empty =
                TextView(this).apply {
                    text =
                        "このフォルダは空です。\n\n" +
                        "「＋ ファイル」からGitHub風に新規ファイルを作成できます。"
                    textSize = 15f
                    setTextColor(
                        secondaryTextColor()
                    )
                    setPadding(
                        dpToPx(8),
                        dpToPx(20),
                        dpToPx(8),
                        dpToPx(20)
                    )
                }

            fileList.addView(empty)
        }

        for (entry in entries) {

            val button =
                Button(this).apply {

                    text = entry.name
                    setSimpleIcon(
                        this,
                        if (entry.isDirectory) {
                            R.drawable.ic_folder_simple
                        } else {
                            R.drawable.ic_file_simple
                        }
                    )

                    gravity =
                        Gravity.START or
                        Gravity.CENTER_VERTICAL

                    setAllCaps(false)

                    setOnClickListener {

                        if (entry.isDirectory) {

                            currentFolderStack.add(current)
                            currentFolderUri =
                                entry.uri

                            renderFileBrowser()

                        } else {

                            if (
                                !isSupportedTextFile(
                                    entry.name,
                                    entry.mimeType
                                )
                            ) {
                                showKStudioNotice("このファイルはKStudioで編集できません")

                                return@setOnClickListener
                            }

                            openManagedFile(
                                entry.uri,
                                entry.name
                            )
                        }
                    }

                    setOnLongClickListener {

                        showFileActions(
                            entry
                        ) {
                            renderFileBrowser()
                        }

                        true
                    }
                }

            fileList.addView(
                button,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(54)
                ).apply {
                    bottomMargin = dpToPx(3)
                }
            )
        }
    }

    private fun navigateFileManagerUp() {

        if (currentFolderStack.isEmpty()) {

            showKStudioNotice("これ以上上には移動できません")

            return
        }

        currentFolderUri =
            currentFolderStack.removeAt(
                currentFolderStack.lastIndex
            )

        renderFileBrowser()
    }

    private fun getCurrentFolderDisplayName(): String {

        val rootName =
            folderRootUri?.let {
                getFolderName(it)
            }?.ifBlank {
                "プロジェクト"
            } ?: "プロジェクト"

        val currentName =
            currentFolderUri?.let {
                getDocumentName(it)
            }?.ifBlank {
                rootName
            } ?: rootName

        return if (currentName == rootName) {
            rootName
        } else {
            rootName + " / " + currentName
        }
    }

    private fun showNoFolderToast() {

        showKStudioNotice("先にフォルダを選択してください")
    }

    private fun queryFolderFromTree(
        folderUri: Uri,
        treeUri: Uri
    ): List<ManagedEntry> {
        val parentId = try {
            DocumentsContract.getDocumentId(folderUri)
        } catch (_: Exception) {
            DocumentsContract.getTreeDocumentId(treeUri)
        }

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            parentId
        )

        val result = mutableListOf<ManagedEntry>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        contentResolver.query(
            childrenUri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex) ?: "(名称なし)"
                val mime = cursor.getString(mimeIndex) ?: "application/octet-stream"

                result.add(
                    ManagedEntry(
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                        name,
                        mime,
                        mime == DocumentsContract.Document.MIME_TYPE_DIR
                    )
                )
            }
        }

        return result
    }

    // Image path shortcut

    private fun showImagePathSuggestionsIfNeeded() {
        val cursor = codeEditor.selectionStart
        if (cursor < 4) {
            hideImagePathSuggestions()
            return
        }

        val before = codeEditor.text.toString().substring(0, cursor)
        if (!Regex("(^|\\\\s)pass$").containsMatchIn(before)) {
            hideImagePathSuggestions()
            return
        }

        val root = folderRootUri ?: run {
            hideImagePathSuggestions()
            return
        }

        val results = mutableListOf<String>()
        collectImageFiles(root, "", results)
        showImagePathSuggestions(results)
    }

    private fun collectImageFiles(
        uri: Uri,
        relativePath: String,
        results: MutableList<String>
    ) {
        if (results.size >= 30) return

        val children = queryFolder(uri)
        for (child in children) {
            val name = child.name
            val relative = if (relativePath.isBlank()) {
                name
            } else {
                relativePath + "/" + name
            }
            val mime = child.mimeType
            val lower = name.lowercase(Locale.getDefault())

            if (mime.startsWith("image/") ||
                lower.endsWith(".png") ||
                lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg") ||
                lower.endsWith(".webp") ||
                lower.endsWith(".gif") ||
                lower.endsWith(".bmp") ||
                lower.endsWith(".svg") ||
                lower.endsWith(".img")
            ) {
                results.add(relative)
            }

            if (child.isDirectory) {
                collectImageFiles(child.uri, relative, results)
            }

            if (results.size >= 30) return
        }
    }

    private fun showImagePathSuggestions(paths: List<String>) {
        hideImagePathSuggestions()
        if (paths.isEmpty()) return

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }

        paths.forEach { path ->
            val item = TextView(this).apply {
                text = path
                textSize = 14f
                setTextColor(primaryTextColor())
                setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
                setOnClickListener {
                    val cursor = codeEditor.selectionStart
                    val text = codeEditor.text
                    val before = text.substring(0, cursor)
                    val start = before.lastIndexOf("pass")
                    if (start >= 0) {
                        text.replace(start, cursor, path)
                        codeEditor.setSelection(start + path.length)
                    }
                    hideImagePathSuggestions()
                }
            }
            list.addView(
                item,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        imagePathPopup = PopupWindow(
            list,
            dpToPx(320),
            dpToPx(285),
            true
        ).apply {
            elevation = dpToPx(8).toFloat()
            setBackgroundDrawable(getDrawable(R.drawable.rounded_panel))
            isOutsideTouchable = true
        }

        imagePathPopup?.showAsDropDown(
            codeEditor,
            dpToPx(8),
            -dpToPx(285)
        )
    }

    private fun hideImagePathSuggestions() {
        imagePathPopup?.dismiss()
        imagePathPopup = null
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
                null
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

            showKStudioNotice("フォルダを読み込めませんでした: " +
                    (e.message ?: "unknown"))
        }

        return result.sortedWith(
            compareBy<ManagedEntry>(
                { !it.isDirectory },
                { it.name.lowercase(Locale.getDefault()) }
            )
        )
    }

    private fun openManagedFile(
        uri: Uri,
        name: String
    ) {
        val lockOwner = getFileLockOwner(uri)
        val account = getAccountName()

        if (!lockOwner.isNullOrBlank() &&
            !lockOwner.equals(account, ignoreCase = true)
        ) {
            showKStudioMessage(
                "ファイルは編集中です",
                name + " は @" + lockOwner + " が編集中です。\n同時編集はできません。"
            )
            return
        }

        confirmUnsavedChanges {
            try {
                val text =
                    contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: ""

                if (text.length > 2_000_000) {
                    showKStudioNotice("大きすぎるファイルです")
                    return@confirmUnsavedChanges
                }

                if (currentFileUri != null && currentFileUri != uri) {
                    releaseFileLock(currentFileUri)
                }

                currentFileUri = uri
                currentFileName = name
                isDirty = false

                acquireFileLock(uri)

                historyApplying = true
                codeEditor.setText(text)
                historyApplying = false

                undoStack.clear()
                redoStack.clear()

                updateEditorHeader()
                updateHistoryButtons()
                lineNumbers.invalidate()
                highlightCode()
                showEditorScreen()

                showKStudioNotice("開きました: " + name)
            } catch (e: Exception) {
                historyApplying = false
                releaseFileLock(uri)
                showKStudioNotice(
                    "ファイルを開けませんでした: " +
                        (e.message ?: "unknown")
                )
            }
        }
    }
    private fun saveCurrentFile(
        onComplete: (() -> Unit)? = null
    ) {
        val uri = currentFileUri
        if (uri == null) {
            showKStudioNotice("保存先ファイルがありません")
            onComplete?.invoke()
            return
        }

        val lockOwner = getFileLockOwner(uri)
        val account = getAccountName()
        if (!lockOwner.isNullOrBlank() &&
            !lockOwner.equals(account, ignoreCase = true)
        ) {
            showKStudioMessage("保存できません", "@" + lockOwner + " がこのファイルを編集中です。")
            return
        }

        try {
            contentResolver
                .openOutputStream(uri, "wt")
                ?.use { stream ->
                    stream.write(
                        codeEditor.text.toString().toByteArray(Charsets.UTF_8)
                    )
                }
                ?: throw IllegalStateException("保存先を開けません")

            isDirty = false
            acquireFileLock(uri)
            updateEditorHeader()

            showKStudioNotice("保存しました")
            onComplete?.invoke()
        } catch (e: Exception) {
            showKStudioNotice("保存に失敗しました: " + (e.message ?: "unknown"))
        }
    }
    private fun askCreateFile(
        folderUri: Uri
    ) {

        currentFolderUri = folderUri
        showCreateFileTab()
    }

    private fun askCreateFolder(
        folderUri: Uri,
        onCreated: (() -> Unit)? = null
    ) {
        showKStudioInputDialog(
            "新しいフォルダ",
            null,
            "フォルダ名",
            "NewFolder",
            "作成"
        ) { rawName ->
            val name = rawName.trim()
            val validation = validatePathSegment(name, "フォルダ名")
            if (validation != null) {
                showKStudioMessage("フォルダ作成", validation)
                return@showKStudioInputDialog
            }

            try {
                if (findChildByName(folderUri, name) != null) {
                    showKStudioMessage("フォルダ作成", "同名のファイルまたはフォルダがあります。")
                    return@showKStudioInputDialog
                }

                DocumentsContract.createDocument(
                    contentResolver,
                    folderUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    name
                ) ?: throw IllegalStateException("フォルダ作成に失敗しました")

                onCreated?.invoke()
                showKStudioMessage("フォルダ作成", "「" + name + "」を作成しました。")
            } catch (e: Exception) {
                showKStudioMessage(
                    "フォルダ作成",
                    "作成に失敗しました: " + (e.message ?: "unknown")
                )
            }
        }
    }
    private fun updateFileCreateHiddenButton() {
        if (!::fileCreateHiddenButton.isInitialized) return
        fileCreateHiddenButton.text =
            if (fileCreateHidden) "隠しファイル: ON" else "隠しファイル: OFF"
    }

    private fun createGitHubStyleFile() {

        val baseFolder =
            currentFolderUri
                ?: run {
                    showNoFolderToast()
                    return
                }

        val rawPath =
            fileCreatePathInput.text
                .toString()
                .trim()

        val content =
            fileCreateContentInput.text
                .toString()

        if (rawPath.isEmpty()) {
            showKStudioNotice("ファイル名を入力してください")
            return
        }

        if (content.length > 2_000_000) {
            showKStudioNotice("ファイル内容が大きすぎます（2MB以下）")
            return
        }

        if (
            rawPath.contains('\\') ||
            rawPath.startsWith("/")
        ) {
            showKStudioNotice("ファイルパスが正しくありません")
            return
        }

        val segments =
            rawPath
                .split("/")
                .map { it.trim() }

        if (
            segments.isEmpty() ||
            segments.any { it.isEmpty() }
        ) {
            showKStudioNotice("ファイルパスに空の階層があります")
            return
        }

        for (segment in segments) {

            val validation =
                validatePathSegment(
                    segment,
                    "ファイル名"
                )

            if (validation != null) {
                showKStudioNotice(validation)
                return
            }
        }

        if (segments.size > 20) {
            showKStudioNotice("フォルダ階層が深すぎます")
            return
        }

        confirmUnsavedChanges {

            try {

                var targetFolder =
                    baseFolder

                for (
                    directoryName in
                    segments.dropLast(1)
                ) {

                    val existing =
                        findChildByName(
                            targetFolder,
                            directoryName
                        )

                    if (existing != null) {

                        if (!existing.isDirectory) {
                            throw IllegalStateException(
                                "'$directoryName' はファイルです"
                            )
                        }

                        targetFolder =
                            existing.uri

                    } else {

                        targetFolder =
                            DocumentsContract.createDocument(
                                contentResolver,
                                targetFolder,
                                DocumentsContract.Document.MIME_TYPE_DIR,
                                directoryName
                            )
                                ?: throw IllegalStateException(
                                    "フォルダ '$directoryName' を作成できませんでした"
                                )
                    }
                }

                var fileName =
                    segments.last()

                if (fileCreateHidden && !fileName.startsWith(".")) {
                    fileName = "." + fileName
                }

                if (
                    findChildByName(
                        targetFolder,
                        fileName
                    ) != null
                ) {
                    throw IllegalStateException(
                        "同名のファイルまたはフォルダがあります"
                    )
                }

                val fileUri =
                    DocumentsContract.createDocument(
                        contentResolver,
                        targetFolder,
                        mimeTypeForFile(fileName),
                        fileName
                    )
                        ?: throw IllegalStateException(
                            "ファイル作成に失敗しました"
                        )

                try {

                    contentResolver
                        .openOutputStream(
                            fileUri,
                            "wt"
                        )
                        ?.use { stream ->
                            stream.write(
                                content.toByteArray(
                                    Charsets.UTF_8
                                )
                            )
                        }
                        ?: throw IllegalStateException(
                            "作成したファイルを開けませんでした"
                        )

                } catch (writeError: Exception) {

                    try {
                        DocumentsContract.deleteDocument(
                            contentResolver,
                            fileUri
                        )
                    } catch (_: Exception) {
                    }

                    throw writeError
                }

                historyApplying = true
                codeEditor.setText(content)
                historyApplying = false

                undoStack.clear()
                redoStack.clear()

                currentFileUri = fileUri
                currentFileName = fileName
                isDirty = false

                updateEditorHeader()
                updateHistoryButtons()
                highlightCode()
                lineNumbers.invalidate()

                fileCreatePathInput.setText("")
                fileCreateContentInput.setText("")

                showHomeTab()

                showKStudioNotice("ファイルを作成しました: " +
                        rawPath)

            } catch (e: Exception) {

                historyApplying = false

                showKStudioNotice("ファイル作成に失敗しました: " +
                        (e.message ?: "unknown"))
            }
        }
    }

    private fun validatePathSegment(
        value: String,
        label: String
    ): String? {

        if (value.isEmpty()) {
            return label + "を入力してください"
        }

        if (
            value == "." ||
            value == ".."
        ) {
            return label + "に . や .. は使えません"
        }

        if (
            value.contains('/') ||
            value.contains('\\') ||
            value.indexOf(0.toChar()) >= 0
        ) {
            return label + "に使用できない文字があります"
        }

        if (value.length > 255) {
            return label + "が長すぎます"
        }

        return null
    }

    private fun findChildByName(
        folderUri: Uri,
        targetName: String
    ): ManagedEntry? {

        return queryFolder(
            folderUri
        ).firstOrNull {
            it.name == targetName
        }
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

            showKStudioNotice("作成しました: " + name)

        } catch (e: Exception) {

            historyApplying = false

            showKStudioNotice("ファイル作成に失敗しました: " +
                    (e.message ?: "unknown"))
        }
    }

    private fun showFileActions(
        entry: ManagedEntry,
        refresh: () -> Unit
    ) {
        val options = if (entry.isDirectory) {
            listOf("名前変更", "削除", "キャンセル")
        } else {
            listOf("内容をコピー", "クリップボードで置換", "名前変更", "削除", "キャンセル")
        }

        showKStudioChoiceDialog(entry.name, options) { which ->
            if (entry.isDirectory) {
                when (which) {
                    0 -> askRename(entry, refresh)
                    1 -> askDelete(entry, refresh)
                }
            } else {
                when (which) {
                    0 -> copyFileContent(entry)
                    1 -> replaceFileFromClipboard(entry, refresh)
                    2 -> askRename(entry, refresh)
                    3 -> askDelete(entry, refresh)
                }
            }
        }
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

            showKStudioNotice("ファイル内容をコピーしました")

        } catch (_: Exception) {

            showKStudioNotice("コピーに失敗しました")
        }
    }

    private fun replaceFileFromClipboard(
        entry: ManagedEntry,
        refresh: () -> Unit
    ) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) {
            showKStudioMessage("置換", "クリップボードが空です。")
            return
        }

        val clip = clipboard.primaryClip ?: return
        val value = clip.getItemAt(0).coerceToText(this).toString()

        showKStudioConfirm(
            "ファイルを置換しますか？",
            entry.name,
            "置換"
        ) {
            try {
                contentResolver.openOutputStream(entry.uri, "wt")?.use { stream ->
                    stream.write(value.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("保存先を開けません")

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
                showKStudioMessage("置換", "ファイルを置換しました。")
            } catch (e: Exception) {
                historyApplying = false
                showKStudioMessage("置換失敗", e.message ?: "unknown")
            }
        }
    }
    private fun askRename(
        entry: ManagedEntry,
        refresh: () -> Unit
    ) {
        showKStudioInputDialog(
            "名前変更",
            null,
            "名前",
            entry.name,
            "変更"
        ) { rawName ->
            val newName = rawName.trim()
            if (newName.isEmpty()) return@showKStudioInputDialog
            try {
                DocumentsContract.renameDocument(contentResolver, entry.uri, newName)
                if (currentFileUri == entry.uri) {
                    currentFileName = newName
                    updateEditorHeader()
                    configurePageHeader(newName, false)
                }
                refresh()
            } catch (e: Exception) {
                showKStudioMessage("名前変更", "変更に失敗しました: " + (e.message ?: "unknown"))
            }
        }
    }
    private fun askDelete(
        entry: ManagedEntry,
        refresh: () -> Unit
    ) {
        showKStudioConfirm(
            "削除しますか？",
            entry.name,
            "削除"
        ) {
            confirmUnsavedChanges {
                try {
                    DocumentsContract.deleteDocument(contentResolver, entry.uri)
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
                } catch (e: Exception) {
                    showKStudioMessage("削除", "削除に失敗しました: " + (e.message ?: "unknown"))
                }
            }
        }
    }
    private fun confirmUnsavedChanges(
        onContinue: () -> Unit
    ) {
        if (!isDirty) {
            onContinue()
            return
        }

        showKStudioThreeChoice(
            "未保存の変更",
            "変更内容を保存してから続行しますか？",
            "保存",
            "破棄",
            "キャンセル",
            {
                saveCurrentFile(onComplete = onContinue)
            },
            {
                isDirty = false
                onContinue()
            }
        )
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


    private fun fileLockKey(uri: Uri): String =
        "file_lock_" + uri.toString()

    private fun getFileLockOwner(uri: Uri): String? {
        val raw = getSharedPreferences("KStudio", Context.MODE_PRIVATE)
            .getString(fileLockKey(uri), null)
            ?: return null

        val parts = raw.split("|", limit = 2)
        if (parts.size != 2) {
            getSharedPreferences("KStudio", Context.MODE_PRIVATE)
                .edit()
                .remove(fileLockKey(uri))
                .apply()
            return null
        }

        val owner = parts[0].trim()
        val timestamp = parts[1].toLongOrNull() ?: 0L
        if (owner.isBlank() ||
            System.currentTimeMillis() - timestamp > 120_000L
        ) {
            getSharedPreferences("KStudio", Context.MODE_PRIVATE)
                .edit()
                .remove(fileLockKey(uri))
                .apply()
            return null
        }

        return owner
    }

    private fun acquireFileLock(uri: Uri) {
        val account = getAccountName()
        if (account.isBlank()) return

        val owner = getFileLockOwner(uri)
        if (owner == null || owner.equals(account, ignoreCase = true)) {
            getSharedPreferences("KStudio", Context.MODE_PRIVATE)
                .edit()
                .putString(
                    fileLockKey(uri),
                    account + "|" + System.currentTimeMillis()
                )
                .apply()
            lockHandler.removeCallbacks(lockHeartbeat)
            lockHandler.postDelayed(lockHeartbeat, 30_000L)
        }
    }

    private fun releaseFileLock(uri: Uri?) {
        if (uri == null) return
        val account = getAccountName()
        if (account.isBlank()) return

        val owner = getFileLockOwner(uri)
        if (owner != null && owner.equals(account, ignoreCase = true)) {
            getSharedPreferences("KStudio", Context.MODE_PRIVATE)
                .edit()
                .remove(fileLockKey(uri))
                .apply()
        }
        if (currentFileUri == uri) {
            lockHandler.removeCallbacks(lockHeartbeat)
        }
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
        val themeMode = getSharedPreferences("KStudio", Context.MODE_PRIVATE)
            .getString("theme_mode", "system")

        return when (themeMode) {
            "light" -> false
            "dark" -> true
            else -> {
                val mode =
                    resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK
                mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
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

        fileCreateContentInput.setTextColor(
            primaryTextColor()
        )

        fileCreateContentInput.setHintTextColor(
            secondaryTextColor()
        )

        fileCreateContentInput.setBackgroundColor(
            editorSurfaceColor()
        )

        fileCreatePathInput.setTextColor(
            primaryTextColor()
        )

        fileCreatePathInput.setHintTextColor(
            secondaryTextColor()
        )

        applyEditorSettings()
        styleButtonsInView(findViewById(android.R.id.content))
        accountButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (isDarkMode()) Color.rgb(48, 48, 52) else Color.rgb(236, 238, 242))
        }
    }

    // Preview Fullscreen    // Preview Fullscreen

    private fun togglePreviewFullscreen() {

        fullscreen = !fullscreen

        if (fullscreen) {

            topMenuBar.visibility = View.GONE
            projectBar.visibility = View.GONE
            searchBar.visibility = View.GONE
            editorContainer.visibility = View.GONE
            bottomHeader.visibility = View.GONE
            projectTab.visibility = View.GONE
            previewArea.visibility = View.VISIBLE

            previewArea.layoutParams =
                previewArea.layoutParams.apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }

            fullscreenButton.text = "✕"

        } else {

            topMenuBar.visibility = View.VISIBLE
            projectBar.visibility = View.VISIBLE
            searchBar.visibility = View.VISIBLE
            bottomHeader.visibility = View.VISIBLE
            editorContainer.visibility = View.VISIBLE
            previewArea.visibility = View.VISIBLE

            previewArea.layoutParams =
                previewArea.layoutParams.apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = dpToPx(180)
                }

            fullscreenButton.text = "⛶"

            applyEditorPreviewOrder()
        }
    }

    // Tabs

    // Home

    private fun showStartupScreen() {
        homeTab.visibility = View.VISIBLE
        editorContainer.visibility = View.GONE
        previewArea.visibility = View.GONE
        projectTab.visibility = View.GONE
        fileTab.visibility = View.GONE
        historyTab.visibility = View.GONE
        bottomHeader.visibility = View.GONE
        searchBar.visibility = View.GONE
        projectBar.visibility = View.GONE

        currentScreen = "HOME"
        configurePageHeader("HOME", true)
        findViewById<Button>(R.id.menuButton).setOnClickListener {
            showMainMenu(findViewById(R.id.menuButton))
        }
        renderHomeFiles()
    }
    private fun showHomeTab() {
        if (fullscreen) togglePreviewFullscreen()

        if (::saveCommitPanel.isInitialized) saveCommitPanel.visibility = View.GONE
        homeTab.visibility = View.VISIBLE
        editorContainer.visibility = View.GONE
        previewArea.visibility = View.GONE
        projectTab.visibility = View.GONE
        fileTab.visibility = View.GONE
        historyTab.visibility = View.GONE
        bottomHeader.visibility = View.GONE
        searchBar.visibility = View.GONE
        projectBar.visibility = View.GONE

        configurePageHeader("HOME", true)
        findViewById<Button>(R.id.menuButton).setOnClickListener {
            showMainMenu(findViewById(R.id.menuButton))
        }
        renderHomeFiles()
    }
    private fun renderHomeFiles() {
        homeFileList.removeAllViews()

        val accountName = getAccountName()
        homeFileList.addView(TextView(this).apply {
            text = if (accountName.isBlank()) "KStudio HOME" else "ようこそ、" + accountName
            textSize = 23f
            setTextColor(primaryTextColor())
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dpToPx(12), 0, dpToPx(16))
        })

        addHomeSection("プロジェクト", "すべて表示", { showProjects() }, buildHomeProjectSection())
        addHomeSection("履歴", "すべて表示", { showHistory() }, buildHomeHistorySection())
        addHomeSection(
            "コミュニティ",
            "すべて表示",
            { startActivity(Intent(this, CommunityActivity::class.java)) },
            buildHomeCommunitySection()
        )

        homeFileList.addView(TextView(this).apply {
            text = "クラウド"
            textSize = 18f
            setTextColor(primaryTextColor())
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dpToPx(16), 0, dpToPx(10))
        })
        homeFileList.addView(TextView(this).apply {
            text = cloudUsageText()
            textSize = 14f
            setTextColor(secondaryTextColor())
            setPadding(0, 0, 0, dpToPx(8))
        })

        val quota = getCloudQuotaBytes()
        val used = getCloudUsedBytes()
        val ratio = if (quota > 0L) {
            (used.toDouble() / quota.toDouble()).coerceIn(0.0, 1.0)
        } else 0.0

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBackground(lineNumberSurfaceColor(), 8)
        }
        bar.addView(
            View(this).apply {
                setBackgroundColor(if (quota > 0L) Color.rgb(0, 128, 255) else secondaryTextColor())
            },
            LinearLayout.LayoutParams(0, dpToPx(16), ratio.toFloat())
        )
        bar.addView(
            View(this).apply { setBackgroundColor(editorSurfaceColor()) },
            LinearLayout.LayoutParams(0, dpToPx(16), (1f - ratio).toFloat())
        )
        homeFileList.addView(
            bar,
            LinearLayout.LayoutParams(-1, dpToPx(16)).apply {
                bottomMargin = dpToPx(24)
            }
        )
    }

    private fun addHomeSection(
        title: String,
        actionText: String,
        action: () -> Unit,
        content: View
    ) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(this).apply {
                text = title
                textSize = 18f
                setTextColor(primaryTextColor())
                setTypeface(null, android.graphics.Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, dpToPx(46), 1f)
        )
        header.addView(
            Button(this).apply {
                text = actionText
                textSize = 13f
                styleKStudioButton(this)
                setOnClickListener { action() }
            },
            LinearLayout.LayoutParams(dpToPx(92), dpToPx(44)).apply {
                marginStart = dpToPx(12)
            }
        )
        homeFileList.addView(header)
        homeFileList.addView(content)
        homeFileList.addView(
            View(this).apply { setBackgroundColor(secondaryTextColor()) },
            LinearLayout.LayoutParams(-1, dpToPx(1)).apply {
                topMargin = dpToPx(14)
                bottomMargin = dpToPx(16)
            }
        )
    }

    private fun buildHomeProjectSection(): View {
        val scroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val items = recentFolders.take(6)
        if (items.isEmpty()) {
            row.addView(TextView(this).apply {
                text = "まだプロジェクトがありません"
                textSize = 14f
                setTextColor(secondaryTextColor())
                setPadding(dpToPx(4), dpToPx(12), 0, dpToPx(12))
            })
        } else {
            items.forEach { uri ->
                row.addView(
                    buildProjectCard(uri, true),
                    LinearLayout.LayoutParams(dpToPx(280), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = dpToPx(12)
                    }
                )
            }
        }
        scroll.addView(row)
        return scroll
    }

    private fun buildHomeHistorySection(): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        readRecentHistory(3).forEach { item ->
            container.addView(
                buildHistorySummaryCard(item),
                LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(10)
                }
            )
        }
        if (container.childCount == 0) {
            container.addView(TextView(this).apply {
                text = "まだ履歴はありません"
                textSize = 14f
                setTextColor(secondaryTextColor())
                setPadding(dpToPx(4), dpToPx(12), 0, dpToPx(12))
            })
        }
        return container
    }

    private fun buildHomeCommunitySection(): View {
        val scroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val items = readCommunityPosts(6)
        if (items.isEmpty()) {
            row.addView(TextView(this).apply {
                text = "まだ投稿はありません"
                textSize = 14f
                setTextColor(secondaryTextColor())
                setPadding(dpToPx(4), dpToPx(12), 0, dpToPx(12))
            })
        } else {
            items.forEach { item ->
                row.addView(
                    LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
                        background = roundedBackground(editorSurfaceColor(), 14)
                        addView(TextView(this@MainActivity).apply {
                            text = "@" + item.first
                            textSize = 15f
                            setTextColor(primaryTextColor())
                            setTypeface(null, android.graphics.Typeface.BOLD)
                        })
                        addView(TextView(this@MainActivity).apply {
                            text = item.second
                            textSize = 14f
                            setTextColor(primaryTextColor())
                            maxLines = 4
                            setPadding(0, dpToPx(6), 0, 0)
                        })
                    },
                    LinearLayout.LayoutParams(dpToPx(280), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = dpToPx(12)
                    }
                )
            }
        }
        scroll.addView(row)
        return scroll
    }

    private fun buildProjectCard(uri: Uri, compact: Boolean): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
            background = roundedBackground(editorSurfaceColor(), 14)
            isClickable = true
            isFocusable = true
        }
        card.addView(TextView(this).apply {
            text = getProjectDisplayName(uri)
            textSize = if (compact) 17f else 19f
            setTextColor(primaryTextColor())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = "最終編集時間: " + getFolderLastModified(uri)
            textSize = 13f
            setTextColor(secondaryTextColor())
            setPadding(0, dpToPx(6), 0, 0)
        })
        card.addView(TextView(this).apply {
            text = "編集者: " + getAccountName().ifBlank { "未設定" }
            textSize = 13f
            setTextColor(secondaryTextColor())
            setPadding(0, dpToPx(4), 0, 0)
        })
        card.addView(TextView(this).apply {
            text = "クラウドへのバックアップ: " + if (isCloudBackupEnabled(uri)) "有効" else "無効"
            textSize = 13f
            setTextColor(secondaryTextColor())
            setPadding(0, dpToPx(4), 0, 0)
        })
        if (!compact) {
            card.addView(
                Button(this).apply {
                    text = "共同コーディング"
                    styleKStudioButton(this)
                    setOnClickListener { openCollaboration(uri) }
                },
                LinearLayout.LayoutParams(-1, dpToPx(46)).apply {
                    topMargin = dpToPx(12)
                }
            )
        }
        card.setOnClickListener { openRecentProject(uri) }
        return card
    }

    private fun getProjectDisplayName(uri: Uri): String =
        if (uri == folderRootUri) projectName else getFolderName(uri).ifBlank { "プロジェクト" }

    private fun getAccountName(): String =
        getSharedPreferences("KStudio", Context.MODE_PRIVATE)
            .getString("account_name", "")?.trim().orEmpty()

    private fun getCloudQuotaBytes(): Long =
        getSharedPreferences("KStudio", Context.MODE_PRIVATE).getLong("cloud_quota_bytes", 0L)

    private fun getCloudUsedBytes(): Long =
        getSharedPreferences("KStudio", Context.MODE_PRIVATE).getLong("cloud_used_bytes", 0L)

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 GB"
        val gb = bytes.toDouble() / 1024.0 / 1024.0 / 1024.0
        return if (gb < 0.01) (bytes / 1024L / 1024L).toString() + " MB"
        else String.format(Locale.JAPAN, "%.2f GB", gb)
    }

    private fun cloudUsageText(): String {
        val quota = getCloudQuotaBytes()
        val used = getCloudUsedBytes()
        return if (quota <= 0L) "未接続（使用量 0 GB / 0 GB）"
        else formatBytes(used) + " / " + formatBytes(quota) + " 使用"
    }

    private fun isCloudBackupEnabled(uri: Uri): Boolean =
        getSharedPreferences("KStudio", Context.MODE_PRIVATE)
            .getBoolean("cloud_backup_" + uri.toString(), false)

    private fun readRecentHistory(limit: Int): List<List<String>> =
        getSharedPreferences("KStudio", Context.MODE_PRIVATE)
            .getString("history", "").orEmpty()
            .split("")
            .filter { it.isNotBlank() }
            .map { it.split("") }
            .filter { it.size >= 4 }
            .take(limit)

    private fun buildHistorySummaryCard(item: List<String>): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
            background = roundedBackground(editorSurfaceColor(), 14)
            addView(TextView(this@MainActivity).apply {
                text = item[3].ifBlank { if (item[0] == "run") "Run" else "保存" }
                textSize = 15f
                setTextColor(primaryTextColor())
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = "編集ファイル: " + currentFileName
                textSize = 13f
                setTextColor(secondaryTextColor())
                setPadding(0, dpToPx(5), 0, 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Run固有ナンバー: " + item[2] + " / " + item[1]
                textSize = 12f
                setTextColor(secondaryTextColor())
                setPadding(0, dpToPx(4), 0, 0)
            })
        }

    private fun readCommunityPosts(limit: Int): List<Pair<String, String>> {
        val raw = getSharedPreferences("KStudio", Context.MODE_PRIVATE)
            .getString("community_posts", null) ?: return emptyList()
        val out = ArrayList<Pair<String, String>>()
        runCatching {
            val array = org.json.JSONArray(raw)
            for (i in 0 until minOf(array.length(), limit)) {
                val obj = array.getJSONObject(i)
                out.add(obj.optString("user") to obj.optString("text"))
            }
        }
        return out
    }

    private fun openCollaboration(uri: Uri) {
        startActivity(
            Intent(this, CollaborationActivity::class.java)
                .putExtra("project_uri", uri.toString())
                .putExtra("project_name", getProjectDisplayName(uri))
        )
    }
    private fun openHomeFolder(uri: Uri) {

        currentFolderStack.add(
            currentFolderUri
                ?: uri
        )

        currentFolderUri = uri
        showHomeTab()
    }

    private fun swapEditorAndPreview() {
        if (
            fullscreen ||
            projectTab.visibility == View.VISIBLE ||
            fileTab.visibility == View.VISIBLE ||
            historyTab.visibility == View.VISIBLE
        ) {
            return
        }

        editorPreviewSwapped = !editorPreviewSwapped
        applyEditorPreviewOrder()
    }

    private fun applyEditorPreviewOrder() {

        val parent = editorContainer.parent as? LinearLayout
            ?: return

        parent.removeView(editorContainer)
        parent.removeView(previewArea)

        val editorParams =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
            ).apply {
                weight = 1f
            }

        val previewParams =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(180)
            )

        if (editorPreviewSwapped) {
            parent.addView(previewArea, 0, previewParams)
            parent.addView(editorContainer, 1, editorParams)
        } else {
            parent.addView(editorContainer, 0, editorParams)
            parent.addView(previewArea, 1, previewParams)
        }
    }

    // Projects Tab

    private fun showProjects() {
        if (fullscreen) togglePreviewFullscreen()

        homeTab.visibility = View.GONE
        projectTab.visibility = View.VISIBLE
        fileTab.visibility = View.GONE
        historyTab.visibility = View.GONE
        editorContainer.visibility = View.GONE
        previewArea.visibility = View.GONE
        bottomHeader.visibility = View.GONE
        searchBar.visibility = View.GONE
        projectBar.visibility = View.GONE

        currentScreen = "PROJECTS"
        configurePageHeader("プロジェクト", false)
        findViewById<Button>(R.id.menuButton).setOnClickListener { handleBackNavigation() }

        projectList.removeAllViews()
        if (recentFolders.isEmpty()) {
            projectList.addView(TextView(this).apply {
                text = "まだプロジェクトがありません。"
                textSize = 16f
                setTextColor(secondaryTextColor())
                setPadding(dpToPx(8), dpToPx(28), 0, dpToPx(28))
            })
            return
        }

        recentFolders.forEach { uri ->
            projectList.addView(
                buildProjectCard(uri, false),
                LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(14)
                }
            )
        }
    }
    private fun openRecentProject(uri: Uri) {

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

        showHomeTab()

        showKStudioNotice("プロジェクトを開きました")
    }

    private fun getFolderLastModified(uri: Uri): String {

        return try {

            val documentUri =
                DocumentsContract.buildDocumentUriUsingTree(
                    uri,
                    DocumentsContract.getTreeDocumentId(uri)
                )

            contentResolver.query(
                documentUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null,
                null,
                null
            )?.use { cursor ->

                if (cursor.moveToFirst()) {

                    val index =
                        cursor.getColumnIndex(
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED
                        )

                    if (
                        index >= 0 &&
                        !cursor.isNull(index)
                    ) {

                        SimpleDateFormat(
                            "yyyy/MM/dd HH:mm",
                            Locale.getDefault()
                        ).format(
                            Date(
                                cursor.getLong(index)
                            )
                        )

                    } else {
                        "不明"
                    }

                } else {
                    "不明"
                }

            } ?: "不明"

        } catch (_: Exception) {
            "不明"
        }
    }

    // Run

    private fun runKStudio() {

        val source =
            codeEditor.text
                .toString()

        val error =
            validateCode(source)

        if (error != null) {

            saveRunHistory(false)

            showError(
                line = error.first,
                code = "K001",
                fileName = currentFileName,
                message = error.second,
                source = "KStudio"
            )

            return
        }

        val treeUri =
            folderRootUri

        if (treeUri == null) {
            saveRunHistory(false)
            showError(
                line = 1,
                code = "P000",
                fileName = currentFileName,
                message = "プロジェクトフォルダが選択されていません。",
                source = "Android"
            )
            return
        }

        val rootUri =
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )

        previewTitle.text = "Android Preview (BETA)"
        previewContainer.removeAllViews()

        val result =
            try {
                AndroidRuntimePreview(this).render(
                    treeUri = treeUri,
                    rootUri = rootUri,
                    container = previewContainer,
                    projectName = projectName
                )
            } catch (e: Exception) {
                AndroidRuntimePreview.Result(
                    success = false,
                    message = e.message
                        ?: "Android Previewの生成に失敗しました。"
                )
            }

        if (!result.success) {
            saveRunHistory(false)

            showError(
                line = 1,
                code = "P001",
                fileName = result.layoutPath.ifBlank {
                    currentFileName
                },
                message =
                    result.message +
                    "\n\nプロジェクト全体を再帰的に確認しました。" +
                    "\n走査ファイル数: " +
                    result.scannedFiles,
                source = "Android"
            )

            return
        }

        saveRunHistory(true)

        showKStudioNotice("Android Previewを起動しました (" +
                result.scannedFiles +
                " files)")
    }

    private fun validateCode(
        source: String
    ): Pair<Int, String>? {

        val stack =
            mutableListOf<Pair<Char, Int>>()

        var line = 1
        var inString = false
        var inRawString = false
        var inChar = false
        var escaped = false
        var inLineComment = false
        var index = 0

        while (index < source.length) {

            val ch = source[index]
            val next = source.getOrNull(index + 1)

            if (inRawString) {

                if (
                    source.startsWith(
                        "\"\"\"",
                        index
                    )
                ) {
                    inRawString = false
                    index += 3
                    continue
                }

                if (ch == '\n') {
                    line++
                }

                index++
                continue
            }

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
                source.startsWith(
                    "\"\"\"",
                    index
                )
            ) {
                inRawString = true
                index += 3
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

        if (inString || inRawString || inChar) {
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
                text = "Run OK"
                setSimpleIcon(this, R.drawable.ic_play_simple)
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

    // History Tab

    private fun showHistory() {
        if (fullscreen) togglePreviewFullscreen()

        homeTab.visibility = View.GONE
        projectTab.visibility = View.GONE
        fileTab.visibility = View.GONE
        historyTab.visibility = View.VISIBLE
        editorContainer.visibility = View.GONE
        previewArea.visibility = View.GONE
        bottomHeader.visibility = View.GONE
        searchBar.visibility = View.GONE
        projectBar.visibility = View.GONE

        currentScreen = "HISTORY"
        configurePageHeader("履歴", false)
        findViewById<Button>(R.id.menuButton).setOnClickListener { handleBackNavigation() }
        renderHistoryTab()
    }
    private fun renderHistoryTab() {
        purgeHistory()

        val preferences = getSharedPreferences("KStudio", Context.MODE_PRIVATE)
        val history = preferences.getString("history", "") ?: ""
        historyText.removeAllViews()

        if (history.isBlank()) {
            val empty = TextView(this).apply {
                text = if (isHistoryEnabled()) {
                    "まだ履歴はありません。\n\nRun または「編集を保存」を実行するとここに履歴が表示されます。"
                } else {
                    "履歴機能はOFFです。\n\n設定からONにすると、新しいRun・保存履歴が記録されます。"
                }
                textSize = 15f
                setTextColor(primaryTextColor())
                setPadding(dpToPx(16), dpToPx(28), dpToPx(16), dpToPx(28))
            }
            historyText.addView(empty)
            return
        }

        val entries = history.split("\u001E").filter { it.isNotBlank() }
        if (entries.isEmpty()) {
            val legacy = TextView(this).apply {
                text = history
                textSize = 15f
                setTextColor(primaryTextColor())
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            }
            historyText.addView(legacy)
            return
        }

        entries.forEach { raw ->
            val fields = raw.split("\u001F")
            if (fields.size >= 4) {
                addHistoryCard(
                    type = fields[0],
                    time = fields[1],
                    serial = fields[2],
                    detail = fields[3],
                    success = fields.getOrNull(4) == "success",
                    comment = fields.getOrNull(5).orEmpty()
                )
            }
        }
    }

    private fun addHistoryCard(
        type: String,
        time: String,
        serial: String,
        detail: String,
        success: Boolean,
        comment: String
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            background = roundedBackground(surfaceColor(), 14)
            isClickable = type == "save" && comment.isNotBlank()
            isFocusable = isClickable
        }

        val timeRow = TextView(this).apply {
            text = "│  " + time + "  " + if (type == "run") "Run" else "保存"
            textSize = 15f
            setTextColor(primaryTextColor())
        }
        val serialRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(6), 0, 0)
        }

        serialRow.addView(
            TextView(this).apply {
                text = "│  " + serial
                textSize = 13f
                setTextColor(secondaryTextColor())
                gravity = Gravity.CENTER_VERTICAL
            },
            LinearLayout.LayoutParams(0, dpToPx(40), 1f)
        )

        serialRow.addView(
            Button(this).apply {
                text = "コピー"
                textSize = 12f
                styleKStudioButton(this)
                setOnClickListener {
                    val clipboard =
                        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("KStudio 履歴コード", serial)
                    )
                    showKStudioNotice("固有コードをコピーしました")
                }
            },
            LinearLayout.LayoutParams(dpToPx(76), dpToPx(40))
        )
        val detailRow = TextView(this).apply {
            text = if (type == "run") {
                if (success) "│  成功" else "│  失敗"
            } else {
                "│  " + detail
            }
            textSize = 14f
            setTextColor(
                if (type == "run" && !success) {
                    Color.rgb(210, 70, 70)
                } else {
                    primaryTextColor()
                }
            )
            setPadding(0, dpToPx(6), 0, 0)
        }

        card.addView(timeRow)
        card.addView(serialRow)
        card.addView(detailRow)

        if (type == "save" && comment.isNotBlank()) {
            card.addView(
                TextView(this).apply {
                    text = "コメントを見る"
                    textSize = 12f
                    setTextColor(secondaryTextColor())
                    setPadding(0, dpToPx(10), 0, 0)
                }
            )
            card.setOnClickListener {
                showKStudioMessage("編集内容の説明", comment)
            }
        }

        val restore = Button(this).apply {
            text = "その時のファイルに戻す"
            textSize = 13f
            isEnabled = false
            alpha = 0.55f
            styleKStudioButton(this)
        }
        card.addView(
            restore,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(42)
            ).apply {
                topMargin = dpToPx(10)
            }
        )

        historyText.addView(
            card,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(10)
            }
        )
    }

    private fun clearHistory() {
        showKStudioConfirm(
            "履歴を削除しますか？",
            "実行履歴・保存履歴をすべて削除します。"
        ) {
            getSharedPreferences("KStudio", Context.MODE_PRIVATE)
                .edit()
                .remove("history")
                .apply()
            renderHistoryTab()
            showKStudioNotice("履歴を削除しました")
        }
    }

    // History Save
    // History Save

    private fun loadEditorSettings() {
        val preferences = getSharedPreferences("KStudio", Context.MODE_PRIVATE)

        if (!preferences.contains("auto_pairing")) {
            preferences.edit().putBoolean("auto_pairing", true).apply()
        }
        if (!preferences.contains("tab_width")) {
            preferences.edit().putInt("tab_width", 4).apply()
        }
        if (!preferences.contains("line_numbers")) {
            preferences.edit().putBoolean("line_numbers", true).apply()
        }
        if (!preferences.contains("search_suggestions")) {
            preferences.edit().putBoolean("search_suggestions", true).apply()
        }
        if (!preferences.contains("font_size")) {
            preferences.edit().putInt("font_size", 15).apply()
        }
        if (!preferences.contains("line_wrap")) {
            preferences.edit().putBoolean("line_wrap", true).apply()
        }
        if (!preferences.contains("theme_mode")) {
            preferences.edit().putString("theme_mode", "system").apply()
        }
        if (!preferences.contains("history_enabled")) {
            preferences.edit().putBoolean("history_enabled", true).apply()
        }
        if (!preferences.contains("history_retention_days")) {
            preferences.edit().putInt("history_retention_days", -1).apply()
        }

        applyEditorSettings()
    }

    private fun applyEditorSettings() {
        if (!::codeEditor.isInitialized) return

        autoPairingEnabled = getSharedPreferences("KStudio", Context.MODE_PRIVATE)
            .getBoolean("auto_pairing", true)

        codeEditor.setTextSize(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            getEditorFontSize().toFloat()
        )
        codeEditor.setHorizontallyScrolling(!isLineWrapEnabled())

        if (::lineNumbers.isInitialized) {
            lineNumbers.visibility =
                if (getSharedPreferences("KStudio", Context.MODE_PRIVATE)
                    .getBoolean("line_numbers", true)
                ) View.VISIBLE else View.GONE
        }

        updateHistoryButtons()
        lineNumbers.invalidate()
    }

    private fun getTabWidth(): Int =
        getSharedPreferences("KStudio", Context.MODE_PRIVATE)
            .getInt("tab_width", 4)
            .let { if (it == 2 || it == 4 || it == 8) it else 4 }

    private fun getEditorFontSize(): Int =
        getSharedPreferences("KStudio", Context.MODE_PRIVATE)
            .getInt("font_size", 15)
            .coerceIn(10, 30)

    private fun isLineWrapEnabled(): Boolean =
        getSharedPreferences("KStudio", Context.MODE_PRIVATE)
            .getBoolean("line_wrap", true)

    private fun isSearchSuggestionsEnabled(): Boolean =
        getSharedPreferences("KStudio", Context.MODE_PRIVATE)
            .getBoolean("search_suggestions", true)

    private fun isHistoryEnabled(): Boolean =
        getSharedPreferences("KStudio", Context.MODE_PRIVATE)
            .getBoolean("history_enabled", true)

    private fun getThemeModeLabel(): String =
        when (
            getSharedPreferences("KStudio", Context.MODE_PRIVATE)
                .getString("theme_mode", "system")
        ) {
            "light" -> "ライト"
            "dark" -> "ダーク"
            else -> "システム"
        }

    private fun getHistoryRetentionLabel(): String =
        when (
            getSharedPreferences("KStudio", Context.MODE_PRIVATE)
                .getInt("history_retention_days", -1)
        ) {
            7 -> "7日"
            30 -> "30日"
            90 -> "90日"
            365 -> "1年"
            else -> "なし"
        }

    private fun purgeHistory() {
        val preferences = getSharedPreferences("KStudio", Context.MODE_PRIVATE)
        val retentionDays = preferences
            .getInt("history_retention_days", -1)
        if (retentionDays < 0) return

        val history = preferences.getString("history", "") ?: return
        if (history.isBlank()) return

        val format = SimpleDateFormat(
            "yyyy/MM/dd HH:mm",
            Locale.getDefault()
        )
        val cutoff = System.currentTimeMillis() -
            retentionDays * 24L * 60L * 60L * 1000L

        val kept = history
            .split("\u001E")
            .filter { raw ->
                if (raw.isBlank()) return@filter false
                val timeText = raw.split("\u001F").getOrNull(1)
                    ?: return@filter true
                val time = runCatching { format.parse(timeText)?.time }.getOrNull()
                time == null || time >= cutoff
            }
            .joinToString("\u001E")

        if (kept != history) {
            preferences.edit().putString("history", kept).apply()
        }
    }

    private fun roundedBackground(fill: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dpToPx(radiusDp).toFloat()
        }

    private fun styleKStudioButton(button: Button) {
        button.setAllCaps(false)
        val fill = if (isDarkMode()) Color.rgb(48, 48, 52) else Color.rgb(236, 238, 242)
        button.setTextColor(primaryTextColor())
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            setStroke(
                dpToPx(1),
                if (isDarkMode()) Color.rgb(78, 78, 84) else Color.rgb(214, 216, 220)
            )
            cornerRadius = dpToPx(10).toFloat()
        }
        button.minHeight = dpToPx(44)
        button.gravity = Gravity.CENTER
        button.includeFontPadding = true
        button.setPadding(dpToPx(12), 0, dpToPx(12), 0)
    }
    private fun styleButtonsInView(view: View) {
        if (view is Button) styleKStudioButton(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                styleButtonsInView(view.getChildAt(i))
            }
        }
    }

    private fun closeKStudioPopup(
        popup: PopupWindow,
        panel: View,
        animate: Boolean
    ) {
        popup.dismiss()
    }

    private fun showKStudioConfirm(
        titleText: String,
        message: String,
        onConfirm: () -> Unit
    ) {
        showKStudioConfirm(titleText, message, "削除", onConfirm)
    }

    private fun showKStudioConfirm(
        titleText: String,
        message: String,
        confirmText: String,
        onConfirm: () -> Unit
    ) {
        val root = FrameLayout(this)
        val scrim = View(this).apply { setBackgroundColor(Color.argb(110, 0, 0, 0)) }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(18), dpToPx(20), dpToPx(18))
            background = roundedBackground(surfaceColor(), 16)
        }
        panel.addView(TextView(this).apply {
            text = titleText
            textSize = 21f
            setTextColor(primaryTextColor())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        panel.addView(TextView(this).apply {
            text = message
            textSize = 15f
            setTextColor(primaryTextColor())
            setPadding(0, dpToPx(14), 0, dpToPx(18))
        })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancel = Button(this).apply { text = "キャンセル"; styleKStudioButton(this) }
        val confirm = Button(this).apply { text = confirmText; styleKStudioButton(this) }
        buttons.addView(cancel, LinearLayout.LayoutParams(0, dpToPx(46), 1f))
        buttons.addView(confirm, LinearLayout.LayoutParams(0, dpToPx(46), 1f).apply {
            marginStart = dpToPx(12)
        })
        panel.addView(buttons)

        root.addView(panel, FrameLayout.LayoutParams(
            minOf(dpToPx(380), resources.displayMetrics.widthPixels - dpToPx(32)),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        lateinit var popup: PopupWindow
        popup = PopupWindow(root, -1, -1, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        cancel.setOnClickListener {
            popup.dismiss()
            backWarningShowing = false
        }
        scrim.setOnClickListener {
            popup.dismiss()
            backWarningShowing = false
        }
        confirm.setOnClickListener {
            popup.dismiss()
            onConfirm()
        }
        popup.showAtLocation(topMenuBar, Gravity.CENTER, 0, 0)
    }
    private fun historySerial(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = java.util.Random()
        return buildString { repeat(7) { append(chars[random.nextInt(chars.length)]) } }
    }

    private fun prependHistoryEntry(
        type: String,
        detail: String,
        success: Boolean = true,
        extra: String = ""
    ) {
        if (!isHistoryEnabled()) return

        purgeHistory()

        val preferences = getSharedPreferences("KStudio", Context.MODE_PRIVATE)
        val oldHistory = preferences.getString("history", "") ?: ""
        val time = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date())
        val serial = historySerial()

        val safeDetail = detail
            .replace("\u001E", "")
            .replace("\u001F", "")
        val safeExtra = extra
            .replace("\u001E", "")
            .replace("\u001F", "")

        val entry = listOf(
            type,
            time,
            serial,
            safeDetail,
            if (success) "success" else "failed",
            safeExtra
        ).joinToString("\u001F")

        preferences
            .edit()
            .putString("history", entry + "\u001E" + oldHistory)
            .apply()
    }

    private fun saveRunHistory(success: Boolean) {
        prependHistoryEntry("run", "", success)
    }

    private fun saveHistory() {
        saveRunHistory(true)
    }

    // History (dedicated tab)

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

                text = "Error"
                setSimpleIcon(this, R.drawable.ic_error_simple)

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

                    showKStudioNotice("エラーコードをコピーしました")
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

            showKStudioNotice("$code をコピーしました")
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
            hideSearchSuggestions()
            return
        }

        hideSearchSuggestions()

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

                        "KStudio is ready.\n\n" +
                        "ファイルを開くか、コードを書いてください。"

                    } else {

                        "KStudio Preview\n\n" +
                        "File: " +
                        currentFileName +
                        "\n" +
                        "Lines: " +
                        source.lines().size +
                        "\n" +
                        "Characters: " +
                        source.length +
                        "\n\n" +
                        "Run でコードの基本チェックを実行できます。"
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

    private fun setSimpleIcon(
        view: TextView,
        drawableId: Int
    ) {
        view.setCompoundDrawablesWithIntrinsicBounds(
            drawableId,
            0,
            0,
            0
        )
        view.compoundDrawablePadding = dpToPx(8)
    }

    private fun dpToPx(
        dp: Int
    ): Int {

        return (
            dp *
            resources.displayMetrics.density
        ).toInt()
    }
}