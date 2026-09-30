package com.nida.kstudio

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.PopupWindow
import android.graphics.Typeface
import org.json.JSONArray
import org.json.JSONObject

class CollaborationActivity : Activity() {

    private data class Member(
        var name: String,
        var role: String
    )

    private lateinit var root: LinearLayout
    private lateinit var memberList: LinearLayout
    private lateinit var statusText: TextView
    private val members = ArrayList<Member>()
    private var projectName = "プロジェクト"
    private var projectUri = ""
    private var currentAccount = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        projectName = intent.getStringExtra("project_name") ?: "プロジェクト"
        projectUri = intent.getStringExtra("project_uri") ?: ""
        currentAccount = prefs().getString("account_name", "")?.trim().orEmpty()

        buildUi()
        loadMembers()
        render()
    }

    override fun onBackPressed() {
        finish()
    }

    private fun prefs() =
        getSharedPreferences("KStudio", Context.MODE_PRIVATE)

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
            styleButton()
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(58), dp(46)))

        header.addView(TextView(this).apply {
            text = "共同コーディング"
            textSize = 19f
            setTextColor(textPrimary())
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }, LinearLayout.LayoutParams(0, dp(46), 1f))

        root.addView(header)

        root.addView(TextView(this).apply {
            text = projectName
            textSize = 16f
            setTextColor(textPrimary())
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(16), dp(8), dp(16), dp(4))
        })

        statusText = TextView(this).apply {
            textSize = 12f
            setTextColor(textSecondary())
            setPadding(dp(16), 0, dp(16), dp(10))
        }
        root.addView(statusText)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        val addInput = EditText(this).apply {
            hint = "追加するアカウント名"
            setSingleLine(true)
            setTextColor(textPrimary())
            setHintTextColor(textSecondary())
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundedBackground(editorSurface(), 10)
        }
        actionRow.addView(addInput, LinearLayout.LayoutParams(-1, dp(52)))

        val addButton = Button(this).apply {
            text = "メンバーを追加"
            styleButton()
            setOnClickListener {
                val name = addInput.text.toString().trim()
                if (name.isBlank()) {
                    showMessage("メンバー追加", "アカウント名を入力してください。")
                    return@setOnClickListener
                }
                if (!canManageMembers()) {
                    showMessage("権限がありません", "メンバー管理は所有者または準所有者だけが行えます。")
                    return@setOnClickListener
                }
                if (members.any { it.name.equals(name, true) }) {
                    showMessage("メンバー追加", "そのアカウントはすでに参加しています。")
                    return@setOnClickListener
                }
                members.add(Member(name, "編集者"))
                saveMembers()
                addInput.text.clear()
                render()
            }
        }
        actionRow.addView(addButton, LinearLayout.LayoutParams(-1, dp(46)).apply {
            topMargin = dp(10)
        })

        val rule = TextView(this).apply {
            text = "同じファイルを同時に編集することはできません。編集ロックを取得している人だけが変更できます。"
            textSize = 12f
            setTextColor(textSecondary())
            setPadding(0, dp(10), 0, 0)
        }
        actionRow.addView(rule)

        root.addView(actionRow)

        val scroll = ScrollView(this)
        memberList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }
        scroll.addView(memberList)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(
            Button(this).apply {
                text = "プロジェクトを削除"
                styleButton()
                visibility = View.GONE
                setOnClickListener {
                    if (!isCurrentOwner()) {
                        showMessage("権限がありません", "プロジェクトを削除できるのは所有者だけです。")
                        return@setOnClickListener
                    }
                    showDeleteProjectDialog()
                }
                tag = "owner_delete"
            },
            LinearLayout.LayoutParams(-1, dp(46)).apply {
                setMargins(dp(16), dp(4), dp(16), dp(16))
            }
        )

        setContentView(root)
    }

    private fun loadMembers() {
        members.clear()
        val raw = prefs().getString(memberKey(), null)
        if (!raw.isNullOrBlank()) {
            runCatching {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    members.add(
                        Member(
                            obj.optString("name"),
                            normalizeRole(obj.optString("role"))
                        )
                    )
                }
            }
        }

        if (members.isEmpty()) {
            val owner = currentAccount.ifBlank { "未設定" }
            members.add(Member(owner, "所有者"))
            saveMembers()
        } else if (
            currentAccount.isNotBlank() &&
            members.any { it.name == "未設定" && it.role == "所有者" }
        ) {
            members.first { it.name == "未設定" && it.role == "所有者" }.name = currentAccount
            saveMembers()
        }

        if (currentAccount.isNotBlank() && members.none { it.name.equals(currentAccount, true) }) {
            members.add(Member(currentAccount, "編集者"))
            saveMembers()
        }
    }

    private fun saveMembers() {
        val array = JSONArray()
        members.forEach { member ->
            array.put(JSONObject().apply {
                put("name", member.name)
                put("role", normalizeRole(member.role))
            })
        }
        prefs().edit().putString(memberKey(), array.toString()).apply()
    }

    private fun memberKey(): String =
        "collab_members_" + projectUri.hashCode()

    private fun normalizeRole(role: String): String =
        when (role) {
            "所有者", "準所有者", "編集者" -> role
            else -> "編集者"
        }

    private fun canManageMembers(): Boolean {
        val mine = members.firstOrNull {
            it.name.equals(currentAccount, true)
        } ?: return false
        return mine.role == "所有者" || mine.role == "準所有者"
    }

    private fun canDelete(member: Member): Boolean {
        if (!canManageMembers()) return false
        return member.role != "所有者" &&
            !member.name.equals(currentAccount, true)
    }

    private fun render() {
        val mine = members.firstOrNull { it.name.equals(currentAccount, true) }
        statusText.text =
            "あなた: " + currentAccount.ifBlank { "未設定" } +
            "  /  権限: " + (mine?.role ?: "未参加")

        root.findViewWithTag<Button>("owner_delete")?.visibility =
            if (isCurrentOwner()) View.VISIBLE else View.GONE

        memberList.removeAllViews()

        members.forEachIndexed { index, member ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = roundedBackground(editorSurface(), 14)
            }

            card.addView(TextView(this).apply {
                text = "@" + member.name
                textSize = 17f
                setTextColor(textPrimary())
                typeface = Typeface.DEFAULT_BOLD
            })

            card.addView(TextView(this).apply {
                text = "権限: " + member.role
                textSize = 13f
                setTextColor(textSecondary())
                setPadding(0, dp(5), 0, 0)
            })

            if (canManageMembers()) {
                val actions = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                val roleButton = Button(this).apply {
                    text = "権限変更"
                    styleButton()
                    isEnabled = member.role != "所有者" || member.name.equals(currentAccount, true)
                    setOnClickListener {
                        changeRole(index)
                    }
                }
                actions.addView(
                    roleButton,
                    LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                        topMargin = dp(10)
                    }
                )

                val removeButton = Button(this).apply {
                    text = "削除"
                    styleButton()
                    isEnabled = canDelete(member) && !member.name.equals(currentAccount, true)
                    setOnClickListener {
                        members.removeAt(index)
                        saveMembers()
                        render()
                    }
                }
                actions.addView(
                    removeButton,
                    LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                        topMargin = dp(10)
                        marginStart = dp(12)
                    }
                )

                card.addView(actions)
            }

            memberList.addView(
                card,
                LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(12)
                }
            )
        }
    }

    private fun changeRole(index: Int) {
        if (!canManageMembers()) return
        val member = members.getOrNull(index) ?: return
        if (member.role == "所有者") {
            showMessage("権限変更", "所有者の権限は変更できません。")
            return
        }

        member.role = when (member.role) {
            "編集者" -> "準所有者"
            "準所有者" -> "編集者"
            else -> "編集者"
        }
        saveMembers()
        render()
    }

    private fun showMessage(title: String, message: String) {
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = roundedBackground(surface(), 16)
        }
        overlay.addView(TextView(this).apply {
            text = title
            textSize = 20f
            setTextColor(textPrimary())
            typeface = Typeface.DEFAULT_BOLD
        })
        overlay.addView(TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(textPrimary())
            setPadding(0, dp(12), 0, dp(16))
        })
        val close = Button(this).apply {
            text = "閉じる"
            styleButton()
        }
        overlay.addView(close, LinearLayout.LayoutParams(-1, dp(46)))

        val popup = PopupWindow(this).apply {
            contentView = overlay
            width = minOf(dp(360), resources.displayMetrics.widthPixels - dp(32))
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            isFocusable = true
            isOutsideTouchable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        close.setOnClickListener { popup.dismiss() }
        popup.showAtLocation(root, Gravity.CENTER, 0, 0)
    }


    private fun isCurrentOwner(): Boolean =
        members.firstOrNull { it.name.equals(currentAccount, true) }?.role == "所有者"

    private fun showDeleteProjectDialog() {
        val overlay = android.widget.FrameLayout(this)
        overlay.setBackgroundColor(Color.argb(110, 0, 0, 0))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = roundedBackground(surface(), 16)
        }

        panel.addView(TextView(this).apply {
            text = "プロジェクトを削除しますか？"
            textSize = 20f
            setTextColor(textPrimary())
            typeface = Typeface.DEFAULT_BOLD
        })
        panel.addView(TextView(this).apply {
            text = "この操作は所有者だけが行えます。削除すると元に戻せません。"
            textSize = 14f
            setTextColor(textPrimary())
            setPadding(0, dp(12), 0, dp(16))
        })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        lateinit var popup: PopupWindow

        val cancel = Button(this).apply {
            text = "キャンセル"
            styleButton()
            setOnClickListener { popup.dismiss() }
        }
        val delete = Button(this).apply {
            text = "削除"
            styleButton()
            setOnClickListener {
                popup.dismiss()
                try {
                    if (projectUri.isBlank()) throw IllegalStateException("プロジェクト情報がありません")
                    DocumentsContract.deleteDocument(contentResolver, Uri.parse(projectUri))
                    finish()
                } catch (e: Exception) {
                    showMessage("削除失敗", e.message ?: "プロジェクトを削除できませんでした。")
                }
            }
        }

        row.addView(cancel, LinearLayout.LayoutParams(0, dp(46), 1f))
        row.addView(delete, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            marginStart = dp(12)
        })
        panel.addView(row)

        overlay.addView(panel, android.widget.FrameLayout.LayoutParams(
            minOf(dp(390), resources.displayMetrics.widthPixels - dp(32)),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        popup = PopupWindow(overlay, -1, -1, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        overlay.setOnClickListener { popup.dismiss() }
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
        button.setPadding(dp(12), 0, dp(12), 0)
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
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
