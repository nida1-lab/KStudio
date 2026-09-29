package com.nida.kstudio

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.DocumentsContract
import android.text.InputType
import android.util.Xml
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Space
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.ToggleButton
import java.io.InputStreamReader
import java.util.Locale
import org.xmlpull.v1.XmlPullParser

class AndroidRuntimePreview(
    private val context: Context
) {

    data class Result(
        val success: Boolean,
        val layoutPath: String = "",
        val scannedFiles: Int = 0,
        val message: String = ""
    )

    private data class ProjectFile(
        val uri: Uri,
        val path: String,
        val name: String,
        val mimeType: String,
        val isDirectory: Boolean
    )

    private data class ResourceTables(
        val strings: Map<String, String>,
        val colors: Map<String, Int>,
        val dimens: Map<String, String>
    )

    private val resolver = context.contentResolver
    private val allFiles = ArrayList<ProjectFile>()

    fun render(
        treeUri: Uri,
        rootUri: Uri,
        container: FrameLayout,
        projectName: String
    ): Result {
        allFiles.clear()
        scanFolder(treeUri, rootUri, "", 0)

        if (allFiles.isEmpty()) {
            return Result(
                success = false,
                scannedFiles = 0,
                message = "プロジェクトフォルダ内にファイルがありません。"
            )
        }

        val tables = loadResourceTables()
        val layout = chooseLayout()
            ?: return Result(
                success = false,
                scannedFiles = allFiles.size,
                message = "Androidレイアウトが見つかりません。res/layout/*.xml を用意してください。"
            )

        container.removeAllViews()

        val shell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val runtimeBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(12),
                dp(8),
                dp(12),
                dp(8)
            )
            setBackgroundColor(Color.rgb(245, 245, 245))
        }

        val title = TextView(context).apply {
            text = "Android Preview  BETA"
            textSize = 14f
            setTextColor(Color.rgb(25, 25, 25))
        }

        val meta = TextView(context).apply {
            text =
                "  " +
                layout.path.substringAfterLast('/') +
                "  •  " +
                projectName.ifBlank { "App" }
            textSize = 12f
            setTextColor(Color.GRAY)
            gravity = Gravity.END
        }

        runtimeBar.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                dp(32),
                1f
            )
        )
        runtimeBar.addView(
            meta,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(32)
            )
        )

        shell.addView(
            runtimeBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        val deviceFrame = FrameLayout(context).apply {
            setBackgroundColor(Color.rgb(238, 238, 238))
            setPadding(
                dp(8),
                dp(8),
                dp(8),
                dp(8)
            )
        }

        val deviceSurface = FrameLayout(context).apply {
            setBackgroundColor(Color.WHITE)
            clipToPadding = true
        }

        try {
            val stream = resolver.openInputStream(layout.uri)
                ?: throw IllegalStateException("レイアウトを開けませんでした")

            val parser = Xml.newPullParser()
            parser.setInput(
                InputStreamReader(
                    stream,
                    Charsets.UTF_8
                )
            )

            val event = moveToStartTag(parser)
            if (event != XmlPullParser.START_TAG) {
                stream.close()
                throw IllegalStateException("レイアウトXMLが空です")
            }

            val rootView = inflateElement(
                parser,
                null,
                tables
            )

            stream.close()

            if (rootView == null) {
                throw IllegalStateException("レイアウトのルートViewを作成できませんでした")
            }

            if (rootView is ViewGroup) {
                deviceSurface.addView(
                    rootView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            } else {
                val wrapper = FrameLayout(context)
                wrapper.addView(
                    rootView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP
                    )
                )
                deviceSurface.addView(
                    wrapper,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        } catch (e: Exception) {
            deviceSurface.removeAllViews()
            val error = TextView(context).apply {
                text =
                    "Preview Error\n\n" +
                    (e.message ?: "XMLを表示できませんでした") +
                    "\n\n対象: " +
                    layout.path
                textSize = 14f
                setTextColor(Color.rgb(185, 45, 45))
                setPadding(
                    dp(18),
                    dp(18),
                    dp(18),
                    dp(18)
                )
            }
            deviceSurface.addView(
                error,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            addDeviceSurface(
                shell,
                deviceFrame,
                deviceSurface
            )
            addRuntimeNote(shell)

            container.addView(
                shell,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            return Result(
                success = false,
                layoutPath = layout.path,
                scannedFiles = allFiles.size,
                message = e.message ?: "Preview生成に失敗しました。"
            )
        }

        addDeviceSurface(
            shell,
            deviceFrame,
            deviceSurface
        )
        addRuntimeNote(shell)

        container.addView(
            shell,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        return Result(
            success = true,
            layoutPath = layout.path,
            scannedFiles = allFiles.size,
            message = "Android Viewをプロジェクト全体から構築しました。"
        )
    }

    private fun addDeviceSurface(
        shell: LinearLayout,
        deviceFrame: FrameLayout,
        deviceSurface: FrameLayout
    ) {
        deviceFrame.addView(
            deviceSurface,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        shell.addView(
            deviceFrame,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }

    private fun addRuntimeNote(
        shell: LinearLayout
    ) {
        val note = TextView(context).apply {
            text =
                "BETA: 実際のAndroid Viewを使用。" +
                "EditText・ScrollView・Switch等は操作できます。" +
                "Kotlinの処理ロジックはまだ実行しません。"
            textSize = 11f
            setTextColor(Color.GRAY)
            setPadding(
                dp(10),
                dp(6),
                dp(10),
                dp(6)
            )
            setBackgroundColor(Color.rgb(248, 248, 248))
        }

        shell.addView(
            note,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun scanFolder(
        treeUri: Uri,
        folderUri: Uri,
        relativePath: String,
        depth: Int
    ) {
        if (depth > 32) return

        val parentId = try {
            DocumentsContract.getDocumentId(folderUri)
        } catch (_: Exception) {
            try {
                DocumentsContract.getTreeDocumentId(treeUri)
            } catch (_: Exception) {
                return
            }
        }

        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                parentId
            )

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        try {
            resolver.query(
                childrenUri,
                projection,
                null,
                null,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME + " ASC"
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
                            ?.takeIf { it.isNotBlank() }
                            ?: "(名称なし)"
                    val mime =
                        cursor.getString(mimeIndex)
                            ?: "application/octet-stream"

                    val childUri =
                        DocumentsContract.buildDocumentUriUsingTree(
                            treeUri,
                            id
                        )

                    val isDirectory =
                        mime ==
                        DocumentsContract.Document.MIME_TYPE_DIR

                    val path =
                        if (relativePath.isBlank()) {
                            name
                        } else {
                            relativePath + "/" + name
                        }

                    allFiles.add(
                        ProjectFile(
                            uri = childUri,
                            path = path,
                            name = name,
                            mimeType = mime,
                            isDirectory = isDirectory
                        )
                    )

                    if (isDirectory) {
                        scanFolder(
                            treeUri,
                            childUri,
                            path,
                            depth + 1
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun chooseLayout(): ProjectFile? {
        val kotlinFiles =
            allFiles.filter {
                !it.isDirectory &&
                (
                    it.name.endsWith(".kt", true) ||
                    it.name.endsWith(".kts", true)
                )
            }

        val requestedNames =
            LinkedHashSet<String>()

        for (file in kotlinFiles) {
            val source =
                readText(file.uri)

            Regex(
                """setContentView\\s*\\(\\s*R\\.layout\\.([A-Za-z0-9_]+)"""
            ).findAll(source).forEach {
                requestedNames.add(
                    it.groupValues[1]
                )
            }
        }

        for (name in requestedNames) {
            val match =
                allFiles.firstOrNull {
                    isLayoutFile(it) &&
                    it.name.equals(
                        name + ".xml",
                        true
                    )
                }

            if (match != null) {
                return match
            }
        }

        return allFiles
            .filter(::isLayoutFile)
            .minByOrNull {
                it.path.length
            }
    }

    private fun isLayoutFile(
        file: ProjectFile
    ): Boolean {
        return !file.isDirectory &&
            (
                file.path.startsWith(
                    "res/layout/"
                ) ||
                file.path.contains(
                    "/res/layout/"
                )
            ) &&
            file.name.endsWith(
                ".xml",
                true
            )
    }

    private fun loadResourceTables(): ResourceTables {
        val strings =
            LinkedHashMap<String, String>()
        val colors =
            LinkedHashMap<String, Int>()
        val dimens =
            LinkedHashMap<String, String>()

        allFiles
            .filter {
                !it.isDirectory &&
                (
                    it.path.startsWith(
                        "res/values"
                    ) ||
                    it.path.contains(
                        "/res/values"
                    )
                ) &&
                it.name.endsWith(
                    ".xml",
                    true
                )
            }
            .forEach { file ->
                try {
                    val parser =
                        Xml.newPullParser()

                    val input =
                        resolver.openInputStream(
                            file.uri
                        )
                            ?: return@forEach

                    parser.setInput(
                        InputStreamReader(
                            input,
                            Charsets.UTF_8
                        )
                    )

                    var event =
                        parser.eventType
                    var current: String? = null
                    var currentName = ""

                    while (
                        event !=
                        XmlPullParser.END_DOCUMENT
                    ) {
                        if (
                            event ==
                            XmlPullParser.START_TAG
                        ) {
                            when (parser.name) {
                                "string",
                                "color",
                                "dimen" -> {
                                    current =
                                        parser.name
                                    currentName =
                                        parser.getAttributeValue(
                                            null,
                                            "name"
                                        ) ?: ""
                                }
                            }
                        } else if (
                            event ==
                            XmlPullParser.TEXT &&
                            current != null
                        ) {
                            val text =
                                parser.text.trim()

                            if (
                                text.isNotEmpty() &&
                                currentName.isNotBlank()
                            ) {
                                when (current) {
                                    "string" ->
                                        strings[
                                            currentName
                                        ] = text

                                    "color" ->
                                        parseColor(
                                            text
                                        )?.let {
                                            colors[
                                                currentName
                                            ] = it
                                        }

                                    "dimen" ->
                                        dimens[
                                            currentName
                                        ] = text
                                }
                            }
                        } else if (
                            event ==
                            XmlPullParser.END_TAG
                        ) {
                            if (
                                parser.name ==
                                current
                            ) {
                                current = null
                                currentName = ""
                            }
                        }

                        event = parser.next()
                    }

                    input.close()
                } catch (_: Exception) {
                }
            }

        return ResourceTables(
            strings,
            colors,
            dimens
        )
    }

    private fun inflateElement(
        parser: XmlPullParser,
        parent: ViewGroup?,
        resources: ResourceTables
    ): View? {
        val tag =
            parser.name

        val attrs =
            readAttributes(parser)

        if (
            tag == "include" ||
            tag == "merge"
        ) {
            skipChildren(parser)
            return null
        }

        val view =
            createView(tag)
                ?: createUnsupportedView(
                    tag
                )

        applyAttributes(
            view,
            attrs,
            parent,
            resources
        )

        if (
            view is ViewGroup
        ) {
            val parentDepth =
                parser.depth

            var event =
                parser.next()

            while (
                event !=
                XmlPullParser.END_DOCUMENT &&
                !(
                    event ==
                    XmlPullParser.END_TAG &&
                    parser.depth ==
                    parentDepth
                )
            ) {
                if (
                    event ==
                    XmlPullParser.START_TAG
                ) {
                    val child =
                        inflateElement(
                            parser,
                            view,
                            resources
                        )

                    if (
                        child != null
                    ) {
                        addChild(
                            view,
                            child
                        )
                    }
                }

                event =
                    parser.next()
            }
        } else {
            skipChildren(parser)
        }

        return view
    }

    private fun moveToStartTag(
        parser: XmlPullParser
    ): Int {
        var event =
            parser.eventType

        while (
            event !=
            XmlPullParser.START_TAG &&
            event !=
            XmlPullParser.END_DOCUMENT
        ) {
            event =
                parser.next()
        }

        return event
    }

    private fun skipChildren(
        parser: XmlPullParser
    ) {
        val depth =
            parser.depth

        var event =
            parser.next()

        while (
            event !=
            XmlPullParser.END_DOCUMENT &&
            !(
                event ==
                XmlPullParser.END_TAG &&
                parser.depth ==
                depth
            )
        ) {
            event =
                parser.next()
        }
    }

    private fun addChild(
        parent: ViewGroup,
        child: View
    ) {
        try {
            when (parent) {
                is ScrollView,
                is HorizontalScrollView -> {
                    if (
                        parent.childCount == 0
                    ) {
                        parent.addView(
                            child
                        )
                    }
                }

                else ->
                    parent.addView(
                        child
                    )
            }
        } catch (_: Exception) {
        }
    }

    private fun readAttributes(
        parser: XmlPullParser
    ): Map<String, String> {
        val result =
            LinkedHashMap<String, String>()

        for (
            i in 0 until parser.attributeCount
        ) {
            result[
                parser.getAttributeName(i)
            ] =
                parser.getAttributeValue(i)
        }

        return result
    }

    private fun createView(
        tag: String
    ): View? {
        return when (tag) {
            "LinearLayout" ->
                LinearLayout(context)

            "FrameLayout" ->
                FrameLayout(context)

            "RelativeLayout" ->
                android.widget.RelativeLayout(
                    context
                )

            "ScrollView" ->
                ScrollView(context)

            "HorizontalScrollView" ->
                HorizontalScrollView(context)

            "TextView" ->
                TextView(context)

            "Button" ->
                Button(context)

            "EditText" ->
                EditText(context)

            "ImageView" ->
                ImageView(context)

            "ImageButton" ->
                ImageButton(context)

            "CheckBox" ->
                CheckBox(context)

            "RadioButton" ->
                RadioButton(context)

            "Switch",
            "android.widget.Switch" ->
                Switch(context)

            "ToggleButton" ->
                ToggleButton(context)

            "ProgressBar" ->
                ProgressBar(context)

            "SeekBar" ->
                SeekBar(context)

            "Spinner" ->
                Spinner(context)

            "Space" ->
                Space(context)

            "View" ->
                View(context)

            "androidx.appcompat.widget.AppCompatButton" ->
                Button(context)

            "androidx.appcompat.widget.AppCompatTextView" ->
                TextView(context)

            "androidx.appcompat.widget.AppCompatEditText" ->
                EditText(context)

            "androidx.appcompat.widget.SwitchCompat" ->
                Switch(context)

            "com.google.android.material.button.MaterialButton" ->
                Button(context)

            "androidx.constraintlayout.widget.ConstraintLayout" ->
                FrameLayout(context)

            "androidx.recyclerview.widget.RecyclerView" ->
                ScrollView(context)

            else ->
                createByReflection(tag)
        }
    }

    private fun createByReflection(
        tag: String
    ): View? {
        if (!tag.contains(".")) {
            return null
        }

        return try {
            val clazz =
                Class.forName(tag)

            val constructor =
                clazz.getConstructor(
                    Context::class.java
                )

            constructor.newInstance(
                context
            ) as View
        } catch (_: Exception) {
            null
        }
    }

    private fun createUnsupportedView(
        tag: String
    ): View {
        return TextView(context).apply {
            text =
                "Unsupported View\n" +
                tag

            textSize = 13f

            setTextColor(
                Color.rgb(
                    160,
                    60,
                    60
                )
            )

            setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
            )

            background =
                GradientDrawable().apply {
                    setColor(
                        Color.rgb(
                            255,
                            244,
                            244
                        )
                    )

                    setStroke(
                        dp(1),
                        Color.rgb(
                            220,
                            120,
                            120
                        )
                    )
                }
        }
    }

    private fun applyAttributes(
        view: View,
        attrs: Map<String, String>,
        parent: ViewGroup?,
        resources: ResourceTables
    ) {
        val width =
            parseSize(
                attrs["layout_width"],
                resources,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

        val height =
            parseSize(
                attrs["layout_height"],
                resources,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

        view.layoutParams =
            createLayoutParams(
                parent,
                width,
                height,
                attrs,
                resources
            )

        attrs["id"]?.let {
            if (
                it.startsWith("@+id/") ||
                it.startsWith("@id/")
            ) {
                view.id =
                    View.generateViewId()
            }
        }

        attrs["visibility"]?.let {
            view.visibility =
                when (
                    it.substringAfterLast(":")
                        .lowercase(
                            Locale.getDefault()
                        )
                ) {
                    "gone" ->
                        View.GONE

                    "invisible" ->
                        View.INVISIBLE

                    else ->
                        View.VISIBLE
                }
        }

        attrs["enabled"]?.let {
            view.isEnabled =
                it.toBooleanStrictOrNull()
                    ?: true
        }

        attrs["clickable"]?.let {
            view.isClickable =
                it.toBooleanStrictOrNull()
                    ?: view.isClickable
        }

        attrs["padding"]?.let {
            val p =
                parseDimension(
                    it,
                    resources
                )

            view.setPadding(
                p,
                p,
                p,
                p
            )
        }

        attrs["paddingStart"]?.let {
            val p =
                parseDimension(
                    it,
                    resources
                )

            view.setPaddingRelative(
                p,
                view.paddingTop,
                view.paddingEnd,
                view.paddingBottom
            )
        }

        attrs["paddingEnd"]?.let {
            val p =
                parseDimension(
                    it,
                    resources
                )

            view.setPaddingRelative(
                view.paddingStart,
                view.paddingTop,
                p,
                view.paddingBottom
            )
        }

        attrs["paddingTop"]?.let {
            val p =
                parseDimension(
                    it,
                    resources
                )

            view.setPadding(
                view.paddingLeft,
                p,
                view.paddingRight,
                view.paddingBottom
            )
        }

        attrs["paddingBottom"]?.let {
            val p =
                parseDimension(
                    it,
                    resources
                )

            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                p
            )
        }

        if (
            view is LinearLayout
        ) {
            attrs["orientation"]?.let {
                view.orientation =
                    if (
                        it ==
                        "horizontal"
                    ) {
                        LinearLayout.HORIZONTAL
                    } else {
                        LinearLayout.VERTICAL
                    }
            }
        }

        attrs["gravity"]?.let {
            view.gravity =
                parseGravity(it)
        }

        if (
            view is TextView
        ) {
            attrs["text"]?.let {
                view.text =
                    resolveText(
                        it,
                        resources
                    )
            }

            attrs["hint"]?.let {
                view.hint =
                    resolveText(
                        it,
                        resources
                    )
            }

            attrs["textSize"]?.let {
                view.textSize =
                    parseTextSize(
                        it,
                        resources
                    )
            }

            attrs["textColor"]?.let {
                parseColorRef(
                    it,
                    resources
                )?.let { color ->
                    view.setTextColor(
                        color
                    )
                }
            }

            attrs["singleLine"]?.let {
                view.isSingleLine =
                    it.toBooleanStrictOrNull()
                        ?: false
            }
        }

        if (
            view is EditText
        ) {
            attrs["inputType"]?.let {
                view.inputType =
                    parseInputType(it)
            }
        }

        if (
            view is ImageView
        ) {
            attrs["scaleType"]?.let {
                view.scaleType =
                    when (
                        it.lowercase(
                            Locale.getDefault()
                        )
                    ) {
                        "center" ->
                            ImageView.ScaleType.CENTER

                        "centercrop" ->
                            ImageView.ScaleType.CENTER_CROP

                        "centerinside" ->
                            ImageView.ScaleType.CENTER_INSIDE

                        "fitcenter" ->
                            ImageView.ScaleType.FIT_CENTER

                        "fitstart" ->
                            ImageView.ScaleType.FIT_START

                        "fitend" ->
                            ImageView.ScaleType.FIT_END

                        "fitxy" ->
                            ImageView.ScaleType.FIT_XY

                        else ->
                            ImageView.ScaleType.CENTER_INSIDE
                    }
            }

            attrs["src"]?.let {
                loadImage(
                    view,
                    it
                )
            }
        }

        if (
            view is CheckBox ||
            view is RadioButton ||
            view is Switch ||
            view is ToggleButton
        ) {
            attrs["checked"]?.let {
                val checked =
                    it.toBooleanStrictOrNull()
                        ?: false

                when (view) {
                    is CheckBox ->
                        view.isChecked =
                            checked

                    is RadioButton ->
                        view.isChecked =
                            checked

                    is Switch ->
                        view.isChecked =
                            checked

                    is ToggleButton ->
                        view.isChecked =
                            checked
                }
            }
        }

        attrs["background"]?.let {
            applyBackground(
                view,
                it,
                resources
            )
        }

        installBetaInteraction(
            view
        )
    }

    private fun createLayoutParams(
        parent: ViewGroup?,
        width: Int,
        height: Int,
        attrs: Map<String, String>,
        resources: ResourceTables
    ): ViewGroup.LayoutParams {
        if (
            parent is LinearLayout
        ) {
            val lp =
                LinearLayout.LayoutParams(
                    width,
                    height
                )

            attrs["layout_weight"]
                ?.toFloatOrNull()
                ?.let {
                    lp.weight = it
                }

            attrs["layout_gravity"]
                ?.let {
                    lp.gravity =
                        parseGravity(it)
                }

            setMargins(
                lp,
                attrs,
                resources
            )

            return lp
        }

        if (
            parent is FrameLayout
        ) {
            val lp =
                FrameLayout.LayoutParams(
                    width,
                    height
                )

            attrs["layout_gravity"]
                ?.let {
                    lp.gravity =
                        parseGravity(it)
                }

            setMargins(
                lp,
                attrs,
                resources
            )

            return lp
        }

        if (
            parent is
            android.widget.RelativeLayout
        ) {
            val lp =
                android.widget.RelativeLayout.LayoutParams(
                    width,
                    height
                )

            setMargins(
                lp,
                attrs,
                resources
            )

            return lp
        }

        val lp =
            ViewGroup.LayoutParams(
                width,
                height
            )

        setMargins(
            lp,
            attrs,
            resources
        )

        return lp
    }

    private fun setMargins(
        lp: ViewGroup.LayoutParams,
        attrs: Map<String, String>,
        resources: ResourceTables
    ) {
        if (
            lp !is
            ViewGroup.MarginLayoutParams
        ) {
            return
        }

        attrs["layout_margin"]?.let {
            val m =
                parseDimension(
                    it,
                    resources
                )

            lp.setMargins(
                m,
                m,
                m,
                m
            )
        }

        attrs["layout_marginStart"]?.let {
            lp.marginStart =
                parseDimension(
                    it,
                    resources
                )
        }

        attrs["layout_marginEnd"]?.let {
            lp.marginEnd =
                parseDimension(
                    it,
                    resources
                )
        }

        attrs["layout_marginTop"]?.let {
            lp.topMargin =
                parseDimension(
                    it,
                    resources
                )
        }

        attrs["layout_marginBottom"]?.let {
            lp.bottomMargin =
                parseDimension(
                    it,
                    resources
                )
        }
    }

    private fun applyBackground(
        view: View,
        raw: String,
        resources: ResourceTables
    ) {
        parseColorRef(
            raw,
            resources
        )?.let {
            view.background =
                ColorDrawable(it)
            return
        }

        val name =
            raw.substringAfter(
                "@drawable/",
                ""
            ).substringAfter(
                "@mipmap/",
                ""
            )

        if (name.isBlank()) return

        val file =
            allFiles.firstOrNull {
                !it.isDirectory &&
                (
                    it.path.contains(
                        "/res/drawable"
                    ) ||
                    it.path.startsWith(
                        "res/drawable"
                    ) ||
                    it.path.contains(
                        "/res/mipmap"
                    ) ||
                    it.path.startsWith(
                        "res/mipmap"
                    )
                ) &&
                it.name.substringBeforeLast(
                    "."
                ).equals(
                    name,
                    true
                )
            } ?: return

        if (
            file.name.endsWith(
                ".xml",
                true
            )
        ) {
            try {
                parseDrawableXml(
                    file.uri
                )?.let {
                    view.background = it
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun parseDrawableXml(
        uri: Uri
    ): Drawable? {
        val input =
            resolver.openInputStream(
                uri
            ) ?: return null

        val parser =
            Xml.newPullParser()

        parser.setInput(
            InputStreamReader(
                input,
                Charsets.UTF_8
            )
        )

        var color =
            Color.TRANSPARENT
        var strokeColor: Int? = null
        var strokeWidth = 0
        var radius = 0
        var event =
            parser.eventType

        while (
            event !=
            XmlPullParser.END_DOCUMENT
        ) {
            if (
                event ==
                XmlPullParser.START_TAG
            ) {
                when (parser.name) {
                    "solid" -> {
                        val raw =
                            parser.getAttributeValue(
                                "http://schemas.android.com/apk/res/android",
                                "color"
                            )

                        color =
                            parseColor(
                                raw
                            ) ?: Color.TRANSPARENT
                    }

                    "stroke" -> {
                        strokeWidth =
                            parseDimension(
                                parser.getAttributeValue(
                                    "http://schemas.android.com/apk/res/android",
                                    "width"
                                ),
                                ResourceTables(
                                    emptyMap(),
                                    emptyMap(),
                                    emptyMap()
                                )
                            )

                        strokeColor =
                            parseColor(
                                parser.getAttributeValue(
                                    "http://schemas.android.com/apk/res/android",
                                    "color"
                                )
                            )
                    }

                    "corners" -> {
                        radius =
                            parseDimension(
                                parser.getAttributeValue(
                                    "http://schemas.android.com/apk/res/android",
                                    "radius"
                                ),
                                ResourceTables(
                                    emptyMap(),
                                    emptyMap(),
                                    emptyMap()
                                )
                            )
                    }
                }
            }

            event =
                parser.next()
        }

        input.close()

        return GradientDrawable().apply {
            setColor(color)

            if (
                strokeColor != null
            ) {
                setStroke(
                    strokeWidth.coerceAtLeast(1),
                    strokeColor!!
                )
            }

            cornerRadius =
                radius.toFloat()
        }
    }

    private fun loadImage(
        view: ImageView,
        raw: String
    ) {
        val name =
            raw.substringAfter(
                "@drawable/",
                ""
            ).substringAfter(
                "@mipmap/",
                ""
            ).substringBefore(
                "/"
            )
            .trim()

        if (
            name.isBlank()
        ) {
            return
        }

        val file =
            allFiles.firstOrNull {
                !it.isDirectory &&
                (
                    it.path.contains(
                        "/res/drawable"
                    ) ||
                    it.path.startsWith(
                        "res/drawable"
                    ) ||
                    it.path.contains(
                        "/res/mipmap"
                    ) ||
                    it.path.startsWith(
                        "res/mipmap"
                    )
                ) &&
                it.name.substringBeforeLast(
                    "."
                ).equals(
                    name,
                    true
                )
            } ?: return

        try {
            resolver.openInputStream(
                file.uri
            ).use { input ->
                val bitmap =
                    input?.let {
                        BitmapFactory.decodeStream(
                            it
                        )
                    }

                if (
                    bitmap != null
                ) {
                    view.setImageBitmap(
                        bitmap
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun installBetaInteraction(
        view: View
    ) {
        if (
            view is Button ||
            view is ImageButton
        ) {
            view.setOnClickListener {
                val label =
                    if (
                        view is TextView
                    ) {
                        view.text.toString()
                            .ifBlank {
                                view.javaClass
                                    .simpleName
                            }
                    } else {
                        view.javaClass
                            .simpleName
                    }

                android.widget.Toast.makeText(
                    context,
                    "Preview: " +
                    label +
                    " がタップされました",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun parseSize(
        raw: String?,
        resources: ResourceTables,
        fallback: Int
    ): Int {
        if (raw == null) return fallback

        val value =
            resolveRef(
                raw,
                resources.dimens
            )

        return when (value) {
            "match_parent",
            "fill_parent" ->
                ViewGroup.LayoutParams.MATCH_PARENT

            "wrap_content" ->
                ViewGroup.LayoutParams.WRAP_CONTENT

            else ->
                parseDimension(
                    value,
                    resources
                )
        }
    }

    private fun parseDimension(
        raw: String?,
        resources: ResourceTables
    ): Int {
        if (raw == null) return 0

        val value =
            resolveRef(
                raw,
                resources.dimens
            ).trim()

        val match =
            Regex(
                """(-?\\d+(?:\\.\\d+)?)(dp|dip|sp|px)?"""
            ).matchEntire(
                value
            ) ?: return 0

        val number =
            match.groupValues[1]
                .toFloatOrNull()
                ?: return 0

        val unit =
            match.groupValues[2]

        val density =
            context.resources
                .displayMetrics.density

        val scaledDensity =
            context.resources
                .displayMetrics.scaledDensity

        return when (unit) {
            "px" ->
                number.toInt()

            "sp" ->
                (number * scaledDensity)
                    .toInt()

            else ->
                (number * density)
                    .toInt()
        }
    }

    private fun parseTextSize(
        raw: String,
        resources: ResourceTables
    ): Float {
        val value =
            resolveRef(
                raw,
                resources.dimens
            ).trim()

        val match =
            Regex(
                """(-?\\d+(?:\\.\\d+)?)(sp|dp|px)?"""
            ).matchEntire(
                value
            ) ?: return 14f

        val number =
            match.groupValues[1]
                .toFloatOrNull()
                ?: return 14f

        return when (
            match.groupValues[2]
        ) {
            "px" ->
                number /
                context.resources
                    .displayMetrics
                    .scaledDensity

            else ->
                number
        }
    }

    private fun parseGravity(
        raw: String
    ): Int {
        var gravity =
            Gravity.NO_GRAVITY

        raw
            .replace(
                "|",
                " "
            )
            .split(
                Regex("\\s+")
            )
            .forEach {
                when (
                    it.lowercase(
                        Locale.getDefault()
                    )
                ) {
                    "center" ->
                        gravity =
                            gravity or
                            Gravity.CENTER

                    "center_vertical" ->
                        gravity =
                            gravity or
                            Gravity.CENTER_VERTICAL

                    "center_horizontal" ->
                        gravity =
                            gravity or
                            Gravity.CENTER_HORIZONTAL

                    "start" ->
                        gravity =
                            gravity or
                            Gravity.START

                    "end" ->
                        gravity =
                            gravity or
                            Gravity.END

                    "left" ->
                        gravity =
                            gravity or
                            Gravity.LEFT

                    "right" ->
                        gravity =
                            gravity or
                            Gravity.RIGHT

                    "top" ->
                        gravity =
                            gravity or
                            Gravity.TOP

                    "bottom" ->
                        gravity =
                            gravity or
                            Gravity.BOTTOM
                }
            }

        return gravity
    }

    private fun parseInputType(
        raw: String
    ): Int {
        var result =
            InputType.TYPE_CLASS_TEXT

        val parts =
            raw
                .replace(
                    "|",
                    " "
                )
                .split(
                    Regex("\\s+")
                )

        if (
            parts.any {
                it.endsWith("number")
            }
        ) {
            result =
                InputType.TYPE_CLASS_NUMBER
        }

        if (
            parts.any {
                it.contains(
                    "textMultiLine"
                )
            }
        ) {
            result =
                result or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }

        if (
            parts.any {
                it.contains(
                    "textPassword"
                )
            }
        ) {
            result =
                result or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        if (
            parts.any {
                it.contains(
                    "textEmailAddress"
                )
            }
        ) {
            result =
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        return result
    }

    private fun parseColorRef(
        raw: String,
        resources: ResourceTables
    ): Int? {
        return when {
            raw.startsWith(
                "@color/"
            ) ->
                resources.colors[
                    raw.substringAfter(
                        "@color/"
                    )
                ]

            raw.startsWith(
                "@android:color/"
            ) ->
                androidColor(
                    raw.substringAfter(
                        "@android:color/"
                    )
                )

            else ->
                parseColor(raw)
        }
    }

    private fun parseColor(
        raw: String?
    ): Int? {
        if (raw == null) return null

        return try {
            if (
                raw.trim()
                    .startsWith("#")
            ) {
                Color.parseColor(
                    raw.trim()
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun androidColor(
        name: String
    ): Int? {
        return when (name) {
            "transparent" ->
                Color.TRANSPARENT

            "black" ->
                Color.BLACK

            "white" ->
                Color.WHITE

            "gray" ->
                Color.GRAY

            "red" ->
                Color.RED

            "green" ->
                Color.GREEN

            "blue" ->
                Color.BLUE

            else ->
                null
        }
    }

    private fun resolveText(
        raw: String,
        resources: ResourceTables
    ): String {
        if (
            raw.startsWith(
                "@string/"
            )
        ) {
            return resources.strings[
                raw.substringAfter(
                    "@string/"
                )
            ] ?: raw
        }

        if (
            raw.startsWith(
                "@android:string/"
            )
        ) {
            return raw.substringAfter(
                "@android:string/"
            )
        }

        return raw
            .replace(
                "\\n",
                "\n"
            )
            .removePrefix("\"")
            .removeSuffix("\"")
    }

    private fun resolveRef(
        raw: String,
        resources: Map<String, String>
    ): String {
        if (
            raw.startsWith(
                "@dimen/"
            )
        ) {
            return resources[
                raw.substringAfter(
                    "@dimen/"
                )
            ] ?: raw
        }

        return raw
    }

    private fun readText(
        uri: Uri
    ): String {
        return try {
            resolver.openInputStream(
                uri
            )?.bufferedReader()?.use {
                it.readText()
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun dp(
        value: Int
    ): Int {
        return (
            value *
            context.resources
                .displayMetrics
                .density
        ).toInt()
    }
}
