package com.lyra.kisskhtv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Remote-operated touch pointer: works with normal embedded players without inspecting their content. */
class RemotePointerView(context: Context) : View(context) {
    var pointerActive = false
        set(value) { field = value; visibility = if (value) VISIBLE else GONE; invalidate() }
    var cursorX = 0f
        private set
    var cursorY = 0f
        private set
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { isFocusable = false; isClickable = false; visibility = GONE }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cursorX = w / 2f; cursorY = h / 2f
    }
    fun move(direction: String, delta: Float) {
        if (direction == "left") cursorX -= delta
        if (direction == "right") cursorX += delta
        if (direction == "up") cursorY -= delta
        if (direction == "down") cursorY += delta
        cursorX = cursorX.coerceIn(1f, (width - 1).coerceAtLeast(1).toFloat())
        cursorY = cursorY.coerceIn(1f, (height - 1).coerceAtLeast(1).toFloat())
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = 9 * resources.displayMetrics.density
        paint.color = Color.BLACK; paint.style = Paint.Style.FILL
        canvas.drawCircle(cursorX, cursorY, radius + 2, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(cursorX, cursorY, radius, paint)
        paint.color = Color.rgb(0, 150, 200)
        canvas.drawCircle(cursorX, cursorY, radius / 3, paint)
    }
}
