package com.ultimate.macrobot.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.ultimate.macrobot.MacroBotApp
import com.ultimate.macrobot.engine.MacroRunner
import com.ultimate.macrobot.model.Step
import com.ultimate.macrobot.model.StepType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Floating RUN / REC / CROP controls plus the full-screen recorder and image-crop layers. */
class OverlayController(private val service: MacroAccessibilityService) {
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val density = service.resources.displayMetrics.density

    private var bubble: LinearLayout? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var bubbleJobs: Job? = null

    private var recorder: RecorderView? = null
    private var recorderParams: WindowManager.LayoutParams? = null
    private val recorded = mutableListOf<RecorderView.Tap>()

    private var picker: PickerView? = null

    private fun dp(v: Int) = (v * density).toInt()

    private fun toast(msg: String) = Toast.makeText(service, msg, Toast.LENGTH_SHORT).show()

    // ---------------------------------------------------------------- bubble

    fun showBubble() {
        if (bubble != null) return
        val runBtn = label("RUN")
        val recBtn = label("REC")
        val cropBtn = label("CROP").apply { visibility = View.GONE }
        val closeBtn = label("X")
        val handle = label("::").apply { setPadding(dp(10), dp(8), dp(10), dp(8)) }

        val root = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.argb(220, 30, 30, 36))
                cornerRadius = dp(22).toFloat()
            }
            addView(handle)
            addView(runBtn)
            addView(recBtn)
            addView(cropBtn)
            addView(closeBtn)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(80)
        }

        handle.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var downX = 0f
            var downY = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = lp.x; startY = lp.y; downX = e.rawX; downY = e.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = startX + (e.rawX - downX).toInt()
                        lp.y = startY + (e.rawY - downY).toInt()
                        if (root.isAttachedToWindow) wm.updateViewLayout(root, lp)
                    }
                }
                return true
            }
        })
        runBtn.setOnClickListener {
            if (MacroRunner.running.value) {
                MacroRunner.stop()
            } else {
                stopRecording()
                MacroRunner.start(service)?.let { toast(it) }
            }
        }
        recBtn.setOnClickListener {
            if (MacroRunner.recording.value) stopRecording() else startRecording()
        }
        cropBtn.setOnClickListener { startPicker() }
        closeBtn.setOnClickListener { hideAll() }

        bubbleJobs = Job().also { job ->
            val s = CoroutineScope(job + Dispatchers.Main)
            s.launch { MacroRunner.running.collect { runBtn.text = if (it) "STOP" else "RUN" } }
            s.launch { MacroRunner.recording.collect { recBtn.text = if (it) "DONE" else "REC" } }
            s.launch {
                MacroRunner.pendingTemplate.collect {
                    cropBtn.visibility = if (it != null) View.VISIBLE else View.GONE
                }
            }
        }
        wm.addView(root, lp)
        bubble = root
        bubbleParams = lp
    }

    private fun label(text: String) = TextView(service).apply {
        this.text = text
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    private fun bringBubbleToFront() {
        val b = bubble ?: return
        val lp = bubbleParams ?: return
        if (b.isAttachedToWindow) wm.removeView(b)
        wm.addView(b, lp)
    }

    fun hideAll() {
        MacroRunner.stop()
        stopRecording()
        removePicker()
        MacroRunner.setPendingTemplate(null)
        bubbleJobs?.cancel()
        bubbleJobs = null
        bubble?.let { if (it.isAttachedToWindow) wm.removeView(it) }
        bubble = null
    }

    fun destroy() {
        hideAll()
        scope.cancel()
    }

    private fun fullScreenParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    // ------------------------------------------------------------- recording

    private fun startRecording() {
        if (MacroBotApp.repo.active() == null) {
            toast("Open a macro in the app first")
            return
        }
        MacroRunner.stop()
        recorded.clear()
        val lp = fullScreenParams()
        val view = RecorderView(service, dp(16).toFloat()) { tap ->
            recorded += tap
            forward(tap)
        }
        wm.addView(view, lp)
        recorder = view
        recorderParams = lp
        MacroRunner.setRecording(true)
        bringBubbleToFront()
        toast("Recording: play your inputs, then press DONE")
    }

    /** Replays a recorded touch into the game so the game state follows along while recording. */
    private fun forward(tap: RecorderView.Tap) {
        val lp = recorderParams ?: return
        scope.launch {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            recorder?.let { if (it.isAttachedToWindow) wm.updateViewLayout(it, lp) }
            if (tap.isSwipe) {
                service.swipe(tap.x.roundToInt(), tap.y.roundToInt(), tap.x2.roundToInt(), tap.y2.roundToInt(), tap.durationMs)
            } else {
                service.tap(tap.x.roundToInt(), tap.y.roundToInt(), tap.durationMs)
            }
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            recorder?.let { if (it.isAttachedToWindow) wm.updateViewLayout(it, lp) }
        }
    }

    private fun stopRecording() {
        val view = recorder ?: return
        if (view.isAttachedToWindow) wm.removeView(view)
        recorder = null
        recorderParams = null
        MacroRunner.setRecording(false)

        val macro = MacroBotApp.repo.active()
        if (macro == null || recorded.isEmpty()) {
            recorded.clear()
            return
        }
        val firstPriority = macro.nextPriority()
        val steps = recorded.mapIndexed { i, r ->
            val next = recorded.getOrNull(i + 1)
            Step(
                name = "${if (r.isSwipe) "Swipe" else "Tap"} ${macro.steps.size + i + 1}",
                type = if (r.isSwipe) StepType.SWIPE else StepType.TAP,
                x = r.x.roundToInt(), y = r.y.roundToInt(),
                x2 = r.x2.roundToInt(), y2 = r.y2.roundToInt(),
                durationMs = r.durationMs,
                delayAfterMs = if (next != null) (next.downAt - r.upAt).coerceAtLeast(50) else 500,
                priority = firstPriority + i * 10,
            )
        }
        MacroBotApp.repo.update(macro.id) { it.copy(steps = it.steps + steps) }
        toast("Recorded ${steps.size} step(s)")
        recorded.clear()
    }

    // ----------------------------------------------------------- image crop

    private fun startPicker() {
        if (MacroRunner.pendingTemplate.value == null || picker != null) return
        if (ScreenCaptureService.instance?.isReady != true) {
            toast("Grant screen capture in the app first")
            return
        }
        val view = PickerView(service, dp(16).toFloat()) { rect -> finishPicker(rect) }
        wm.addView(view, fullScreenParams())
        picker = view
        bringBubbleToFront()
    }

    private fun removePicker() {
        picker?.let { if (it.isAttachedToWindow) wm.removeView(it) }
        picker = null
    }

    private fun finishPicker(rect: RectF?) {
        removePicker()
        val pending = MacroRunner.pendingTemplate.value
        if (rect == null || pending == null) return
        bubble?.visibility = View.INVISIBLE
        scope.launch {
            delay(200) // let the compositor drop our overlays before grabbing a frame
            val frame = withContext(Dispatchers.Default) { ScreenCaptureService.instance?.captureFresh() }
            bubble?.visibility = View.VISIBLE
            if (frame == null) {
                toast("Screen capture failed")
                return@launch
            }
            val name = withContext(Dispatchers.IO) { saveTemplate(frame, rect, pending.macroId, pending.stepId) }
            if (name == null) {
                toast("Selection was outside the screen")
                return@launch
            }
            MacroRunner.setPendingTemplate(null)
            toast("Image saved - reopen MacroBot")
        }
    }

    private fun saveTemplate(frame: Bitmap, rect: RectF, macroId: String, stepId: String): String? {
        val l = rect.left.toInt().coerceIn(0, frame.width - 1)
        val t = rect.top.toInt().coerceIn(0, frame.height - 1)
        val w = (rect.right.toInt().coerceIn(l + 1, frame.width)) - l
        val h = (rect.bottom.toInt().coerceIn(t + 1, frame.height)) - t
        if (w < 4 || h < 4) return null
        val repo = MacroBotApp.repo
        val name = "${UUID.randomUUID()}.png"
        FileOutputStream(File(repo.templatesDir, name)).use {
            Bitmap.createBitmap(frame, l, t, w, h).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val old = repo.get(macroId)?.steps?.firstOrNull { it.id == stepId }?.templateFile
        repo.update(macroId) { m ->
            m.copy(steps = m.steps.map { if (it.id == stepId) it.copy(templateFile = name) else it })
        }
        old?.let { File(repo.templatesDir, it).delete() }
        return name
    }
}

/** Transparent full-screen layer that records finger taps and swipes. */
class RecorderView(
    context: Context,
    private val slop: Float,
    private val onTap: (Tap) -> Unit,
) : View(context) {
    data class Tap(
        val x: Float, val y: Float, val x2: Float, val y2: Float,
        val downAt: Long, val upAt: Long, val isSwipe: Boolean,
    ) {
        val durationMs: Long get() = (upAt - downAt).coerceIn(if (isSwipe) 100 else 30, 5000)
    }

    private val markers = mutableListOf<Pair<Float, Float>>()
    private val fill = Paint().apply { color = Color.argb(30, 255, 0, 0) }
    private val border = Paint().apply {
        color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 8f
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 80, 80) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 15f * resources.displayMetrics.density
    }

    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY; downAt = e.eventTime
            }
            MotionEvent.ACTION_UP -> {
                val swipe = hypot(e.rawX - downX, e.rawY - downY) > slop
                markers += downX to downY
                invalidate()
                onTap(Tap(downX, downY, e.rawX, e.rawY, downAt, e.eventTime, swipe))
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), border)
        val loc = IntArray(2).also { getLocationOnScreen(it) }
        markers.forEachIndexed { i, (x, y) ->
            val cx = x - loc[0]
            val cy = y - loc[1]
            canvas.drawCircle(cx, cy, 18f, dot)
            canvas.drawText("${i + 1}", cx + 24f, cy - 12f, text)
        }
        canvas.drawText("Recording - inputs are captured and passed to the game", 48f, 72f + 150f, text)
    }
}

/** Full-screen layer where the user drags a box around the thing to look for. */
class PickerView(
    context: Context,
    private val minSize: Float,
    private val onDone: (RectF?) -> Unit,
) : View(context) {
    private val dim = Paint().apply { color = Color.argb(140, 0, 0, 0) }
    private val stroke = Paint().apply {
        color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 5f
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 15f * resources.displayMetrics.density
    }
    private var startX = 0f
    private var startY = 0f
    private var rect: RectF? = null

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = e.rawX; startY = e.rawY
                rect = RectF(startX, startY, startX, startY)
            }
            MotionEvent.ACTION_MOVE -> {
                rect = RectF(minOf(startX, e.rawX), minOf(startY, e.rawY), maxOf(startX, e.rawX), maxOf(startY, e.rawY))
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val r = RectF(minOf(startX, e.rawX), minOf(startY, e.rawY), maxOf(startX, e.rawX), maxOf(startY, e.rawY))
                onDone(if (r.width() >= minSize && r.height() >= minSize) r else null)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val loc = IntArray(2).also { getLocationOnScreen(it) }
        val r = rect?.let { RectF(it.left - loc[0], it.top - loc[1], it.right - loc[0], it.bottom - loc[1]) }
        canvas.save()
        if (r != null) canvas.clipOutRect(r)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
        canvas.restore()
        if (r != null) canvas.drawRect(r, stroke)
        canvas.drawText("Drag a box around what the bot should look for (tap to cancel)", 48f, 72f + 150f, text)
    }
}
