package com.nida.kstudio

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class CommunityActivity : Activity() {

    private data class Post(
        val user: String,
        val text: String,
        val createdAt: Long
    )

    private data class ThreadItem(
        val id: String,
        var tag: String,
        var title: String,
        var starter: String,
        var createdAt: Long,
        var updatedAt: Long,
        val posts: ArrayList<Post>
    )

    private lateinit var root: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var threadList: LinearLayout
    private lateinit var threadTitle: TextView
    private lateinit var threadFeed: LinearLayout
    private lateinit var postInput: EditText
    private lateinit var suggestionPanel: LinearLayout
    private lateinit var channelList: LinearLayout

    private val threads = ArrayList<ThreadItem>()
    private var selectedThreadId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUi()
        loadThreads()
        renderThreadList()

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                OnBackInvokedCallback { handleBack() }
            )
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT < 33) {
            handleBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun handleBack() {
        if (selectedThreadId != null) {
            selectedThreadId = null
            renderThreadList()
        } else {
            finish()
        }
    }

    private fun prefs() =
        getSharedPreferences("KStudio", Context.MODE_PRIVATE)

    private fun accountName(): String =
        prefs().getString("account_name", "")?.trim().orEmpty()

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface())
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        header.addView(Button(this).apply {
            text = "←"
            textSize = 20f
            styleButton(this)
            setOnClickListener { handleBack() }
        }, LinearLayout.LayoutParams(dp(58), dp(46)))

        header.addView(
            TextView(this).apply {
                text = "コミュニティ"
                textSize = 20f
                setTextColor(textPrimary())
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
            },
            LinearLayout.LayoutParams(0, dp(46), 1f)
        )

        header.addView(Button(this).apply {
            text = "＋"
            textSize = 22f
            styleButton(this)
            contentDescription = "新しいスレッド"
            setOnClickListener { showNewThreadPanel() }
        }, LinearLayout.LayoutParams(dp(50), dp(46)).apply {
            marginStart = dp(10)
        })

        root.addView(header)

        searchInput = EditText(this).apply {
            hint = "#タグ / ワード検索"
            setSingleLine(true)
            textSize = 15f
            setTextColor(textPrimary())
            setHintTextColor(textSecondary())
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundedBackground(editorSurface(), 10)
        }
        root.addView(searchInput, LinearLayout.LayoutParams(-1, dp(50)).apply {
            setMargins(dp(12), dp(4), dp(12), dp(8))
        })

        channelList = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(2), dp(12), dp(8))
        }
        val channelScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(channelList)
        }
        root.addView(channelScroll, LinearLayout.LayoutParams(-1, dp(48)))

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderThreadList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        setContentView(root)
    }

    private fun renderThreadList() {
        root.removeViews(3, root.childCount - 3)

        val info = TextView(this).apply {
            text = if (accountName().isBlank()) {
                "投稿には登録アカウント名が必要です。"
            } else {
                "@" + accountName() + " で参加中"
            }
            textSize = 12f
            setTextColor(textSecondary())
            setPadding(dp(14), dp(2), dp(14), dp(8))
        }
        root.addView(info)

        val contentScroll = ScrollView(this)
        threadList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(24))
        }
        contentScroll.addView(threadList)
        root.addView(contentScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        renderChannels()
        val query = searchInput.text.toString().trim()
        val filtered = threads.filter {
            if (query.isBlank()) true
            else if (query.startsWith("#")) {
                val q = query.removePrefix("#").lowercase(Locale.ROOT)
                it.tag.lowercase(Locale.ROOT).contains(q)
            } else {
                val q = query.lowercase(Locale.ROOT)
                it.title.lowercase(Locale.ROOT).contains(q) ||
                    it.tag.lowercase(Locale.ROOT).contains(q) ||
                    it.starter.lowercase(Locale.ROOT).contains(q) ||
                    it.posts.any { post ->
                        post.text.lowercase(Locale.ROOT).contains(q) ||
                            post.user.lowercase(Locale.ROOT).contains(q)
                    }
            }
        }

        if (filtered.isEmpty()) {
            threadList.addView(TextView(this).apply {
                text = if (threads.isEmpty()) "まだスレッドはありません。" else "検索結果がありません。"
                textSize = 15f
                setTextColor(textSecondary())
                setPadding(dp(12), dp(24), dp(12), dp(24))
            })
            return
        }

        filtered.sortedByDescending { it.updatedAt }.forEach { thread ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = roundedBackground(editorSurface(), 14)
                isClickable = true
                isFocusable = true
            }

            addText(card, "#" + thread.tag, 13f, textSecondary(), true)
            addText(card, thread.title, 17f, textPrimary(), true, top = 5)
            addText(card, "@" + thread.starter + "  ・  " + formatTime(thread.updatedAt), 12f, textSecondary(), false, top = 6)
            addText(card, thread.posts.size.toString() + "件の投稿", 12f, textSecondary(), false, top = 4)

            card.setOnClickListener {
                selectedThreadId = thread.id
                renderSelectedThread()
            }

            threadList.addView(
                card,
                LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(12)
                }
            )
        }
    }

    private fun renderChannels() {
        channelList.removeAllViews()
        val tags = linkedSetOf<String>()
        tags.addAll(threads.map { it.tag }.filter { it.isNotBlank() }.take(20))
        if (tags.isEmpty()) tags.add("KStudio")

        tags.forEach { tag ->
            channelList.addView(
                Button(this).apply {
                    text = "#" + tag
                    textSize = 12f
                    styleButton(this)
                    setOnClickListener {
                        searchInput.setText("#" + tag)
                        searchInput.setSelection(searchInput.length())
                    }
                },
                LinearLayout.LayoutParams(dp(110), dp(40)).apply {
                    marginEnd = dp(10)
                }
            )
        }
    }

    private fun renderSelectedThread() {
        root.removeViews(3, root.childCount - 3)

        val thread = threads.firstOrNull { it.id == selectedThreadId }
        if (thread == null) {
            selectedThreadId = null
            renderThreadList()
            return
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(6))
        }
        threadTitle = TextView(this).apply {
            text = "#" + thread.tag + "  " + thread.title
            textSize = 19f
            setTextColor(textPrimary())
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(threadTitle)
        header.addView(TextView(this).apply {
            text = "スレッド作成者: @" + thread.starter
            textSize = 12f
            setTextColor(textSecondary())
            setPadding(0, dp(5), 0, 0)
        })
        root.addView(header)

        val feedScroll = ScrollView(this)
        threadFeed = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(14))
        }
        feedScroll.addView(threadFeed)
        root.addView(feedScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        postInput = EditText(this).apply {
            hint = "メッセージ  #タグ @アカウント"
            gravity = Gravity.TOP or Gravity.START
            minLines = 2
            maxLines = 5
            setTextColor(textPrimary())
            setHintTextColor(textSecondary())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(editorSurface(), 12)
        }
        root.addView(postInput, LinearLayout.LayoutParams(-1, dp(84)).apply {
            setMargins(dp(12), dp(4), dp(12), dp(4))
        })

        suggestionPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            visibility = View.GONE
        }
        root.addView(suggestionPanel)

        val postButton = Button(this).apply {
            text = "送信"
            styleButton(this)
            setOnClickListener { submitPost(thread) }
        }
        root.addView(postButton, LinearLayout.LayoutParams(-1, dp(46)).apply {
            setMargins(dp(12), dp(4), dp(12), dp(12))
        })

        postInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderSuggestions()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        thread.posts.forEach { post ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = roundedBackground(editorSurface(), 14)
            }

            addText(card, "@" + post.user, 16f, textPrimary(), true)
            val body = TextView(this).apply {
                text = highlightTokens(post.text)
                textSize = 15f
                setTextColor(textPrimary())
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                setPadding(0, dp(6), 0, 0)
            }
            card.addView(body)
            addText(card, formatTime(post.createdAt), 11f, textSecondary(), false, top = 8)

            threadFeed.addView(
                card,
                LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(10)
                }
            )
        }

        feedScroll.post { feedScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun submitPost(thread: ThreadItem) {
        val account = accountName()
        if (account.isBlank()) {
            showMessage("投稿できません", "先にKStudioのアカウント名を設定してください。")
            return
        }

        val text = postInput.text.toString().trim()
        if (text.isBlank()) {
            showMessage("投稿できません", "投稿内容を入力してください。")
            return
        }
        if (text.length > 2000) {
            showMessage("投稿できません", "投稿は2000文字以内です。")
            return
        }

        thread.posts.add(Post(account, text, System.currentTimeMillis()))
        thread.updatedAt = System.currentTimeMillis()
        saveThreads()
        postInput.text.clear()
        renderSelectedThread()
    }

    private fun renderSuggestions() {
        if (!::suggestionPanel.isInitialized) return
        val text = postInput.text.toString()
        val cursor = postInput.selectionStart.coerceAtLeast(0)
        val prefix = text.substring(0, min(cursor, text.length))
        val match = Regex("([@#][A-Za-z0-9_一-龯ぁ-んァ-ンー-]*)$").find(prefix)
        if (match == null || match.value.length < 2) {
            suggestionPanel.visibility = View.GONE
            suggestionPanel.removeAllViews()
            return
        }

        val symbol = match.value.first()
        val keyword = match.value.drop(1).lowercase(Locale.ROOT)
        val values = if (symbol == '@') {
            knownUsers().filter { it.lowercase(Locale.ROOT).contains(keyword) }.take(5)
        } else {
            knownTags().filter { it.lowercase(Locale.ROOT).contains(keyword) }.take(5)
        }

        suggestionPanel.removeAllViews()
        if (values.isEmpty()) {
            suggestionPanel.visibility = View.GONE
            return
        }

        values.forEach { value ->
            suggestionPanel.addView(
                Button(this).apply {
                    setText(if (symbol == '@') "@" + value else "#" + value)
                    textSize = 13f
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    styleButton(this)
                    setOnClickListener {
                        val start = match.range.first
                        val end = cursor
                        val replacement = symbol + value
                        postInput.text.replace(start, end, replacement)
                        postInput.setSelection(start + replacement.length)
                        renderSuggestions()
                    }
                },
                LinearLayout.LayoutParams(-1, dp(42)).apply {
                    bottomMargin = dp(6)
                }
            )
        }
        suggestionPanel.visibility = View.VISIBLE
    }

    private fun knownUsers(): List<String> {
        val set = linkedSetOf<String>()
        threads.forEach { thread ->
            set.add(thread.starter)
            thread.posts.forEach { post -> set.add(post.user) }
        }
        accountName().takeIf { it.isNotBlank() }?.let(set::add)
        return set.toList()
    }

    private fun knownTags(): List<String> {
        val set = linkedSetOf<String>()
        threads.forEach { thread ->
            if (thread.tag.isNotBlank()) set.add(thread.tag)
        }
        return set.toList()
    }

    private fun highlightTokens(text: String): SpannableString {
        val out = SpannableString(text)
        val regex = Regex("([@#][A-Za-z0-9_一-龯ぁ-んァ-ンー]+)")
        regex.findAll(text).forEach { match ->
            out.setSpan(
                ForegroundColorSpan(Color.rgb(0, 128, 255)),
                match.range.first,
                match.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return out
    }

    private fun showNewThreadPanel() {
        val overlay = FrameLayoutCompat(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = roundedBackground(surface(), 16)
        }

        addText(panel, "新しいスレッド", 21f, textPrimary(), true)
        addText(panel, "#タグを作成して会話を始めます。", 13f, textSecondary(), false, top = 8)

        val tagInput = EditText(this).apply {
            hint = "タグ（例: Kotlin）"
            setSingleLine(true)
            setTextColor(textPrimary())
            setHintTextColor(textSecondary())
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundedBackground(editorSurface(), 10)
        }
        panel.addView(tagInput, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12) })

        val titleInput = EditText(this).apply {
            hint = "スレッド名"
            setSingleLine(true)
            setTextColor(textPrimary())
            setHintTextColor(textSecondary())
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundedBackground(editorSurface(), 10)
        }
        panel.addView(titleInput, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(10) })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancel = Button(this).apply { text = "キャンセル"; styleButton(this) }
        val create = Button(this).apply { text = "作成"; styleButton(this) }
        buttons.addView(cancel, LinearLayout.LayoutParams(0, dp(46), 1f))
        buttons.addView(create, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(12) })
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(12) })

        overlay.addPanel(panel)
        val popup = overlay.popup()

        cancel.setOnClickListener { popup.dismiss() }
        overlay.scrim.setOnClickListener { popup.dismiss() }
        create.setOnClickListener {
            val account = accountName()
            val tag = sanitizeTag(tagInput.text.toString())
            val title = titleInput.text.toString().trim()
            if (account.isBlank()) {
                popup.dismiss()
                showMessage("作成できません", "先にKStudioのアカウント名を設定してください。")
                return@setOnClickListener
            }
            if (tag.isBlank() || title.isBlank()) {
                showMessage("作成できません", "タグとスレッド名を入力してください。")
                return@setOnClickListener
            }
            val now = System.currentTimeMillis()
            threads.add(
                ThreadItem(
                    id = now.toString() + "_" + threads.size,
                    tag = tag,
                    title = title,
                    starter = account,
                    createdAt = now,
                    updatedAt = now,
                    posts = ArrayList()
                )
            )
            saveThreads()
            popup.dismiss()
            renderThreadList()
        }

        popup.showAtLocation(root, Gravity.CENTER, 0, 0)
    }

    private fun sanitizeTag(value: String): String =
        value.trim()
            .removePrefix("#")
            .replace(Regex("[^A-Za-z0-9_一-龯ぁ-んァ-ンー-]"), "")
            .take(32)

    private fun loadThreads() {
        threads.clear()
        val raw = prefs().getString("community_threads", null)
        if (raw.isNullOrBlank()) {
            seedThreadsIfEmpty()
            return
        }

        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val posts = ArrayList<Post>()
                val postArray = obj.optJSONArray("posts") ?: JSONArray()
                for (j in 0 until postArray.length()) {
                    val post = postArray.getJSONObject(j)
                    posts.add(
                        Post(
                            post.optString("user"),
                            post.optString("text"),
                            post.optLong("createdAt")
                        )
                    )
                }
                threads.add(
                    ThreadItem(
                        id = obj.optString("id"),
                        tag = obj.optString("tag"),
                        title = obj.optString("title"),
                        starter = obj.optString("starter"),
                        createdAt = obj.optLong("createdAt"),
                        updatedAt = obj.optLong("updatedAt"),
                        posts = posts
                    )
                )
            }
        }
    }

    private fun seedThreadsIfEmpty() {
        val now = System.currentTimeMillis()
        threads.add(
            ThreadItem(
                id = "welcome",
                tag = "KStudio",
                title = "KStudioコミュニティへようこそ",
                starter = "KStudio",
                createdAt = now,
                updatedAt = now,
                posts = ArrayList()
            )
        )
        saveThreads()
    }

    private fun saveThreads() {
        val array = JSONArray()
        threads.take(200).forEach { thread ->
            array.put(JSONObject().apply {
                put("id", thread.id)
                put("tag", thread.tag)
                put("title", thread.title)
                put("starter", thread.starter)
                put("createdAt", thread.createdAt)
                put("updatedAt", thread.updatedAt)
                val postArray = JSONArray()
                thread.posts.takeLast(500).forEach { post ->
                    postArray.put(JSONObject().apply {
                        put("user", post.user)
                        put("text", post.text)
                        put("createdAt", post.createdAt)
                    })
                }
                put("posts", postArray)
            })
        }
        prefs().edit().putString("community_threads", array.toString()).apply()
        // Home compatibility
        val posts = JSONArray()
        threads.sortedByDescending { it.updatedAt }.take(20).forEach { thread ->
            thread.posts.lastOrNull()?.let { post ->
                posts.put(JSONObject().apply {
                    put("user", post.user)
                    put("text", post.text)
                    put("createdAt", post.createdAt)
                })
            }
        }
        prefs().edit().putString("community_posts", posts.toString()).apply()
    }

    private fun addText(
        parent: LinearLayout,
        value: String,
        size: Float,
        color: Int,
        bold: Boolean,
        top: Int = 0
    ) {
        parent.addView(TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            if (top > 0) setPadding(0, dp(top), 0, 0)
        })
    }

    private fun formatTime(time: Long): String =
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(Date(time))

    private fun showMessage(title: String, message: String) {
        val overlay = FrameLayoutCompat(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = roundedBackground(surface(), 16)
        }
        addText(panel, title, 20f, textPrimary(), true)
        addText(panel, message, 14f, textPrimary(), false, top = 10)
        val close = Button(this).apply {
            text = "閉じる"
            styleButton(this)
        }
        panel.addView(close, LinearLayout.LayoutParams(-1, dp(46)).apply {
            topMargin = dp(14)
        })
        overlay.addPanel(panel)
        val popup = overlay.popup()
        close.setOnClickListener { popup.dismiss() }
        overlay.scrim.setOnClickListener { popup.dismiss() }
        popup.showAtLocation(root, Gravity.CENTER, 0, 0)
    }

    private fun styleButton(button: Button) {
        button.setAllCaps(false)
        button.setTextColor(textPrimary())
        button.background = roundedBackground(
            if (isNight()) Color.rgb(48, 48, 52) else Color.rgb(236, 238, 242),
            10
        )
        button.minHeight = dp(44)
        button.gravity = Gravity.CENTER
        button.setPadding(dp(12), 0, dp(12), 0)
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private inner class FrameLayoutCompat(context: Context) : android.widget.FrameLayout(context) {
        val scrim = View(context).apply {
            setBackgroundColor(Color.argb(110, 0, 0, 0))
        }

        init {
            setBackgroundColor(Color.TRANSPARENT)
            addView(scrim, LayoutParams(-1, -1))
        }

        fun addPanel(panel: View) {
            addView(
                panel,
                LayoutParams(
                    minOf(dp(390), resources.displayMetrics.widthPixels - dp(32)),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }

        fun popup(): PopupWindow =
            PopupWindow(this, -1, -1, true).apply {
                isOutsideTouchable = true
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
    }

    private fun surface(): Int = if (isNight()) Color.rgb(28, 28, 30) else Color.WHITE
    private fun editorSurface(): Int = if (isNight()) Color.rgb(38, 38, 41) else Color.rgb(245, 245, 245)
    private fun textPrimary(): Int = if (isNight()) Color.WHITE else Color.BLACK
    private fun textSecondary(): Int = if (isNight()) Color.LTGRAY else Color.DKGRAY
    private fun isNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
