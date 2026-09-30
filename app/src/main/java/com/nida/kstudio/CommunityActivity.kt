package com.nida.kstudio

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommunityActivity : Activity() {

    private data class Post(val user: String, val text: String, val createdAt: Long)

    private lateinit var feed: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var postInput: EditText
    private val posts = ArrayList<Post>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadPosts()
        renderPosts()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(TextView(this).apply {
            text = "コミュニティ"
            textSize = 23f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, dp(52), 1f))

        header.addView(Button(this).apply {
            text = "戻る"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(76), dp(44)))
        root.addView(header)

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        searchInput = EditText(this).apply {
            hint = "#タグ / ワード検索"
            setSingleLine(true)
            textSize = 15f
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, dp(48), 1f))

        searchRow.addView(Button(this).apply {
            text = "全て"
            setOnClickListener { searchInput.text.clear() }
        }, LinearLayout.LayoutParams(dp(70), dp(48)))
        root.addView(searchRow)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderPosts() }
            override fun afterTextChanged(s: Editable?) {}
        })

        root.addView(TextView(this).apply {
            text = "登録アカウント名で投稿。#タグ / @メンション対応。"
            textSize = 12f
            setPadding(dp(4), dp(8), dp(4), dp(8))
        })

        val scroll = ScrollView(this)
        feed = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        scroll.addView(feed)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        postInput = EditText(this).apply {
            hint = "いまどうしてる？  #KStudio @user"
            gravity = Gravity.TOP or Gravity.START
            minLines = 3
            maxLines = 6
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        root.addView(postInput, LinearLayout.LayoutParams(-1, dp(96)))

        root.addView(Button(this).apply {
            text = "投稿"
            setOnClickListener { submitPost() }
        }, LinearLayout.LayoutParams(-1, dp(48)))

        setContentView(root)
    }

    private fun submitPost() {
        val text = postInput.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "投稿内容を入力してください", Toast.LENGTH_SHORT).show()
            return
        }
        if (text.length > 2000) {
            Toast.makeText(this, "投稿は2000文字以内です", Toast.LENGTH_SHORT).show()
            return
        }

        val accountName = getSharedPreferences("KStudio", Context.MODE_PRIVATE)
            .getString("account_name", "")?.trim().orEmpty()

        if (accountName.isEmpty()) {
            Toast.makeText(this, "先にアカウント名を設定してください", Toast.LENGTH_SHORT).show()
            return
        }

        posts.add(0, Post(accountName, text, System.currentTimeMillis()))
        savePosts()
        postInput.text.clear()
        renderPosts()
    }

    private fun renderPosts() {
        feed.removeAllViews()
        val query = searchInput.text.toString().trim().lowercase(Locale.ROOT)
        val filtered = posts.filter {
            query.isEmpty() || it.text.lowercase(Locale.ROOT).contains(query)
        }

        if (filtered.isEmpty()) {
            feed.addView(TextView(this).apply {
                text = if (posts.isEmpty()) "まだ投稿はありません。" else "検索結果がありません。"
                textSize = 15f
                setPadding(dp(12), dp(24), dp(12), dp(24))
            })
            return
        }

        filtered.take(100).forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = roundedBackground()
            }

            card.addView(TextView(this).apply {
                text = "@" + item.user
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })

            card.addView(TextView(this).apply {
                text = item.text
                textSize = 15f
                setPadding(0, dp(6), 0, dp(6))
            })

            card.addView(TextView(this).apply {
                text = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(Date(item.createdAt))
                textSize = 11f
            })

            feed.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            })
        }
    }

    private fun savePosts() {
        val array = JSONArray()
        posts.take(500).forEach { item ->
            array.put(JSONObject().apply {
                put("user", item.user)
                put("text", item.text)
                put("createdAt", item.createdAt)
            })
        }
        getSharedPreferences("KStudio", Context.MODE_PRIVATE)
            .edit()
            .putString("community_posts", array.toString())
            .apply()
    }

    private fun loadPosts() {
        val raw = getSharedPreferences("KStudio", Context.MODE_PRIVATE)
            .getString("community_posts", null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                posts.add(Post(
                    item.optString("user"),
                    item.optString("text"),
                    item.optLong("createdAt")
                ))
            }
        }
    }

    private fun roundedBackground(): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(if (isNight()) Color.rgb(38, 38, 38) else Color.rgb(245, 245, 245))
            cornerRadius = dp(14).toFloat()
        }

    private fun isNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
