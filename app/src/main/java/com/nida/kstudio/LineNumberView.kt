package com.nida.kstudio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.widget.EditText

class LineNumberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF777777.toInt()
        textSize = 15f * resources.displayMetrics.scaledDensity
        typeface = android.graphics.Typeface.MONOSPACE
    }

    private var editor: EditText? = null

    fun setEditor(editText: EditText) {
        editor = editText
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        paint.color =
            if (
                (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            ) {
                0xFFAAAAAA.toInt()
            } else {
                0xFF777777.toInt()
            }

        val edit = editor ?: return
        val layout = edit.layout ?: return

        val scrollY = edit.scrollY
        val paddingTop = edit.paddingTop

        val firstLine = layout.getLineForVertical(
            scrollY
        )

        val lastLine = layout.getLineForVertical(
            scrollY + edit.height
        )

        for (line in firstLine..lastLine) {

            if (line >= layout.lineCount) {
                break
            }

            val baseline =
                layout.getLineBaseline(line) +
                paddingTop -
                scrollY

            val number = (line + 1).toString()

            canvas.drawText(
                number,
                width - 8f - paint.measureText(number),
                baseline.toFloat(),
                paint
            )
        }
    }
}