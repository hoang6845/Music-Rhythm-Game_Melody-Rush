package com.example.musicrhythmgame_melodyrush.view

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.annotation.Keep
import androidx.core.graphics.toColorInt
import com.example.musicrhythmgame_melodyrush.AudioSyncClock
import com.example.musicrhythmgame_melodyrush.MainViewModel
import com.example.musicrhythmgame_melodyrush.NoteSpawner
import com.example.musicrhythmgame_melodyrush.NoteType
import kotlin.math.cos
import kotlin.math.sin

class MelodyRushView @JvmOverloads constructor(
    var viewModel: MainViewModel,
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var noteSpawner: NoteSpawner? = null
    private var audioSyncClock: AudioSyncClock? = null

    private val laneColors = listOf(
        "#FF6B9D".toColorInt(),
        "#6BCF7F".toColorInt(),
        "#6BB5FF".toColorInt(),
        "#FFD66B".toColorInt()
    )

    private val lanePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val dividerPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(100, 255, 255, 255)
        isAntiAlias = true
    }

    private val targetZonePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val notePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val holdBarPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val particlePaint = Paint().apply {
        isAntiAlias = true
        shader = null
    }

    // Target zones ở dưới cùng màn hình
    private val targetZones = mutableMapOf<Int, RectF>()
    private val laneStates = mutableMapOf<Int, LaneState>()
    private val hitEffects = mutableListOf<HitEffect>()
    private val missEffects = mutableListOf<MissEffect>()
    private val holdEffects = mutableMapOf<Int, HoldEffect>()

    private var laneWidth = 0f
    private var targetZoneHeight = 160f
    private var targetZoneY = 0f

    data class LaneState(
        var isPressed: Boolean = false,
        var glowIntensity: Float = 0f
    )

    data class HitEffect(
        val laneId: Int,
        val centerX: Float,
        val centerY: Float,
        var progress: Float = 0f,
        val startTime: Long = System.currentTimeMillis()
    )

    data class MissEffect(
        val laneId: Int,
        val centerX: Float,
        val centerY: Float,
        var progress: Float = 0f,
        val startTime: Long = System.currentTimeMillis()
    )

    data class HoldEffect(
        var progress: Float = 0f,
        var intensity: Float = 1f,
        val particles: MutableList<Particle> = mutableListOf(),
        var isCompleted: Boolean = false,
        var isMissed: Boolean = false,
        var completionTime: Long = 0L,
        var fadeOutProgress: Float = 0f,
        var rotationAngle: Float = 0f,
        var completionParticles: MutableList<Particle> = mutableListOf()

    )

    data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var alpha: Float = 1f,
        var size: Float,
        var angle: Float = 0f,
        var rotation: Float = 0f,
        var color: Int = Color.WHITE
    )

    private var lastFrameTime = System.currentTimeMillis()

    private var backgroundGradientProgress: Float = 0f
    private var currentGradientColors: IntArray = intArrayOf(
        "#0a0a0a".toColorInt(),
        "#0a0a0a".toColorInt(),
        "#0a0a0a".toColorInt()
    )
    private val backgroundRipples = mutableListOf<BackgroundRipple>()

    @Keep
    private data class BackgroundRipple(
        val x: Float,
        val y: Float,
        var progress: Float = 0f,
        val color: Int,
        val startTime: Long = System.currentTimeMillis()
    )

    init {
        for (i in 1..4) {
            laneStates[i] = LaneState()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateLanePositions()
    }

    private fun calculateLanePositions() {
        laneWidth = width / 4f
        targetZoneY = height - targetZoneHeight - 50f

        for (i in 1..4) {
            val left = (i - 1) * laneWidth
            val right = i * laneWidth
            targetZones[i] = RectF(left, targetZoneY, right, height.toFloat())
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Log.d("pause", "onDraw: fix pause")
        drawDynamicBackground(canvas)

        // Vẽ các lane dividers
        for (i in 1..3) {
            val x = i * laneWidth
            canvas.drawLine(x, 0f, x, height.toFloat(), dividerPaint)
        }

        // Vẽ target zones ở dưới
        drawTargetZones(canvas)

        // Vẽ các note đang rơi
        drawFallingNotes(canvas)

        // Vẽ hold effects
        drawHoldEffects(canvas)

        // Vẽ hit effects
        drawHitEffects(canvas)

        // Vẽ miss effects
        drawMissEffects(canvas)

        if (viewModel.isPause.value == false) {
            Log.d("pause", "onDraw: fix pause ${viewModel.isPause.value}")
            invalidate()
        }
    }

    private fun drawTargetZones(canvas: Canvas) {
        for (i in 1..4) {
            val rect = targetZones[i] ?: continue
            val state = laneStates[i] ?: continue
            val baseColor = laneColors[i - 1]

            // Vẽ background target zone
            targetZonePaint.color = baseColor
            targetZonePaint.alpha = if (state.isPressed) 200 else 120
            canvas.drawRect(rect, targetZonePaint)

            // Vẽ glow effect khi được nhấn
            if (state.glowIntensity > 0 || state.isPressed) {
                val glowPaint = Paint(targetZonePaint).apply {
                    alpha =
                        ((state.glowIntensity * 150).toInt()).coerceAtLeast(if (state.isPressed) 100 else 0)
                    maskFilter = BlurMaskFilter(30f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawRect(rect, glowPaint)
            }

            // Vẽ border
            val borderPaint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                color = lightenColor(baseColor, 0.4f)
                alpha = if (state.isPressed) 255 else 180
                isAntiAlias = true
            }
            canvas.drawRect(rect, borderPaint)

            // Vẽ horizontal lines để thể hiện hit zone
            val linePaint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = Color.WHITE
                alpha = 100
                isAntiAlias = true
            }
            canvas.drawLine(rect.left, targetZoneY + 10f, rect.right, targetZoneY + 10f, linePaint)
        }
    }

    private fun drawFallingNotes(canvas: Canvas) {
        val currentTime = audioSyncClock?.getCurrentTimeSeconds() ?: return
        val activeNotes = noteSpawner?.getActiveNotes() ?: emptyList()
        val spawnOffset = noteSpawner?.getSpawnOffset() ?: 2f

        activeNotes.forEach { note ->
            if (note.isHit || note.isMissed) return@forEach

            val timeDiff = note.time - currentTime
            if (timeDiff > spawnOffset || timeDiff < -0.5f) return@forEach

            val progress = 1f - (timeDiff / spawnOffset).coerceIn(0f, 1f)
            val noteY = progress * (targetZoneY + targetZoneHeight / 2)

            val laneId = note.padId
            if (laneId !in 1..4) return@forEach

            val centerX = (laneId - 0.5f) * laneWidth
            val baseColor = laneColors[laneId - 1]

            if (note.type == NoteType.HOLD) {
                val holdLength =
                    (note.holdDuration / spawnOffset) * (targetZoneY + targetZoneHeight / 2)
                drawHoldNote(canvas, centerX, noteY, laneWidth, baseColor, progress, spawnOffset, note)
            } else {
                drawTapNote(canvas, centerX, noteY, laneWidth * 0.7f, baseColor, progress)
            }

            val distanceToTarget = kotlin.math.abs(noteY - targetZoneY)
            val glowIntensity = (1f - (distanceToTarget / 200f)).coerceIn(0f, 1f)
            laneStates[laneId]?.glowIntensity = glowIntensity
        }
    }

    private fun drawTapNote(
        canvas: Canvas,
        centerX: Float,
        y: Float,
        width: Float,
        color: Int,
        progress: Float
    ) {
        val noteHeight = targetZoneHeight * 0.8f
        val rect = RectF(
            centerX - width / 2,
            y - noteHeight / 2,
            centerX + width / 2,
            y + noteHeight / 2
        )

        // Vẽ glow
        notePaint.shader = RadialGradient(
            centerX, y, width / 2 * 1.5f,
            intArrayOf(
                Color.argb(100, Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            rect.left - 10f,
            rect.top - 10f,
            rect.right + 10f,
            rect.bottom + 10f,
            25f,
            25f,
            notePaint
        )

        notePaint.shader = LinearGradient(
            centerX, rect.top, centerX, rect.bottom,
            intArrayOf(
                Color.WHITE,
                lightenColor(color, 0.3f),
                color
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, 20f, 20f, notePaint)

        notePaint.shader = LinearGradient(
            rect.left, rect.top,
            rect.right, rect.top + noteHeight * 0.3f,
            intArrayOf(
                Color.argb(150, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            rect.left + 10f, rect.top + 5f,
            rect.right - 10f, rect.top + noteHeight * 0.3f,
            15f, 15f, notePaint
        )

        notePaint.shader = null
    }

    private fun drawHoldNote(
        canvas: Canvas,
        centerX: Float,
        y: Float, // Vị trí trung tâm của phần đầu note khi nó di chuyển
        laneWidth: Float,
        color: Int,
        progress: Float,
        spawnOffset: Float, // Thời gian note mất để đi từ spawn đến target
        note: com.example.musicrhythmgame_melodyrush.Note // Toàn bộ đối tượng note
    ) {
        val noteWidth = laneWidth * 0.7f
        val headHeight = targetZoneHeight * 0.8f // Chiều cao của phần đầu note

        // Vị trí y của phần đầu note (phần mà người dùng sẽ nhấn)
        val headY = y + headHeight / 2

        // Chiều dài hiển thị của note trên màn hình khi nó chưa bị tiêu hao
        val maxVisualHoldLength = (note.holdDuration / spawnOffset) * (targetZoneY + targetZoneHeight / 2)

        var currentHoldLength = maxVisualHoldLength

        val currentTime = audioSyncClock?.getCurrentTimeSeconds() ?: 0f

        // Thời điểm mà note bắt đầu cần được nhấn (khi phần đầu note đến đích)
        val noteHitTime = note.time

        // Tính toán tiến trình tiêu hao:
        // Tiến trình này chỉ bắt đầu khi currentTime >= noteHitTime
        if (currentTime >= noteHitTime) {
            val timeElapsedSinceHitTime = currentTime - noteHitTime
            val decayProgress = (timeElapsedSinceHitTime / note.holdDuration).coerceIn(0f, 1f)

            // Chiều dài của note sẽ giảm dần theo decayProgress
            currentHoldLength = maxVisualHoldLength * (1f - decayProgress)

            // Đảm bảo chiều dài không bao giờ nhỏ hơn chiều cao của phần đầu note
            currentHoldLength = currentHoldLength.coerceAtLeast(headHeight)
        }

        // Nếu note chưa đến thời điểm hit, hoặc đang ở trước thời điểm hit
        // thì chiều dài của nó vẫn là maxVisualHoldLength
        // (logic này đã được bao gồm bởi currentHoldLength = maxVisualHoldLength ở trên
        // và chỉ thay đổi nếu điều kiện if thỏa mãn)


        // Tính toán vị trí Y của điểm bắt đầu hold note (phần trên cùng của note)
        // Phần đầu note (headY) vẫn di chuyển theo 'y' bình thường.
        // Phần trên cùng của note (holdNoteTop) sẽ dịch chuyển xuống theo 'currentHoldLength'
        // để tạo hiệu ứng "tiêu hao".
        val holdNoteTop = headY - currentHoldLength

        val holdRect = RectF(
            centerX - noteWidth / 2,
            holdNoteTop,
            centerX + noteWidth / 2,
            headY
        )

        // --- Bắt đầu vẽ các hiệu ứng (như trước) ---

        // 1. Vẽ glow cho toàn bộ hold note
        notePaint.shader = RadialGradient(
            centerX, holdRect.centerY(), noteWidth / 2 * 1.5f,
            intArrayOf(
                Color.argb(120, Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            holdRect.left - 15f, holdRect.top - 15f,
            holdRect.right + 15f, holdRect.bottom + 15f,
            25f, 25f, notePaint
        )

        // 2. Vẽ phần thân chính của hold note (gradient dọc theo toàn bộ chiều dài)
        notePaint.shader = LinearGradient(
            centerX, holdRect.top, centerX, holdRect.bottom,
            intArrayOf(
                Color.WHITE,
                lightenColor(color, 0.3f),
                color
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(holdRect, 20f, 20f, notePaint)

        // 3. Vẽ hiệu ứng highlight ở phía trên của hold note
        notePaint.shader = LinearGradient(
            holdRect.left, holdRect.top,
            holdRect.right, holdRect.top + headHeight * 0.3f, // Chiều cao của highlight
            intArrayOf(
                Color.argb(150, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            holdRect.left + 10f, holdRect.top + 5f,
            holdRect.right - 10f, holdRect.top + headHeight * 0.3f,
            15f, 15f, notePaint
        )

        // 4. Vẽ arrow indicator (ở phần cuối của note, tại vị trí y ban đầu)
        val arrowPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        // Tùy thuộc vào bạn muốn arrow sáng lên khi nào:
        // - Khi note đang được giữ (như trước)
        // - Hoặc khi note đang ở trong "thời gian tiêu hao" (currentTime >= noteHitTime)
        //   Ví dụ: if (note.isHolding || (currentTime >= noteHitTime && currentTime < noteHitTime + note.holdDuration))
        if (note.isHolding) arrowPaint.color = Color.WHITE
        else arrowPaint.color = Color.argb(180, 255, 255, 255)

        val arrowSize = 15f
        val arrowPath = Path().apply {
            moveTo(centerX - arrowSize, headY - arrowSize / 2)
            lineTo(centerX, headY + arrowSize / 2)
            lineTo(centerX + arrowSize, headY - arrowSize / 2)
        }
        canvas.drawPath(arrowPath, arrowPaint)

        notePaint.shader = null
        holdBarPaint.shader = null
    }

    private fun drawHitEffects(canvas: Canvas) {
        val iterator = hitEffects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            val elapsed = System.currentTimeMillis() - effect.startTime
            effect.progress = (elapsed / 500f).coerceIn(0f, 1f)

            if (effect.progress >= 1f) {
                iterator.remove()
                continue
            }

            val baseColor = laneColors[effect.laneId - 1]
            val alpha = (255 * (1f - effect.progress)).toInt()

            // Hiệu ứng burst từ tâm
            val maxRadius = laneWidth * 0.8f
            val currentRadius = maxRadius * effect.progress

            // Vẽ vòng tròn lan tỏa
            for (i in 0..2) {
                val offset = i * 0.2f
                val ringProgress = (effect.progress - offset).coerceIn(0f, 1f)
                val ringRadius = maxRadius * ringProgress
                val ringAlpha = (alpha * (1f - ringProgress)).toInt()

                val ripplePaint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 8f - i * 2f
                    color = Color.argb(ringAlpha, 255, 255, 255)
                    isAntiAlias = true
                }
                canvas.drawCircle(effect.centerX, effect.centerY, ringRadius, ripplePaint)
            }

            // Vẽ flash
            val flashPaint = Paint().apply {
                shader = RadialGradient(
                    effect.centerX, effect.centerY, currentRadius,
                    intArrayOf(
                        Color.argb(alpha, 255, 255, 255),
                        Color.argb(
                            alpha / 2,
                            Color.red(baseColor),
                            Color.green(baseColor),
                            Color.blue(baseColor)
                        ),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                isAntiAlias = true
            }
            canvas.drawCircle(effect.centerX, effect.centerY, currentRadius, flashPaint)

            // Vẽ particles
            val particleCount = 12
            for (i in 0 until particleCount) {
                val angle = (Math.PI * 2 * i / particleCount).toFloat()
                val distance = currentRadius * 1.2f
                val px = effect.centerX + cos(angle) * distance
                val py = effect.centerY + sin(angle) * distance
                val particleSize = 6f * (1f - effect.progress)

                val particlePaint = Paint().apply {
                    color = Color.argb(alpha, 255, 255, 255)
                    isAntiAlias = true
                }
                canvas.drawCircle(px, py, particleSize, particlePaint)
            }
        }
    }

    private fun drawMissEffects(canvas: Canvas) {
        val iterator = missEffects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            val elapsed = System.currentTimeMillis() - effect.startTime
            effect.progress = (elapsed / 800f).coerceIn(0f, 1f)

            if (effect.progress >= 1f) {
                iterator.remove()
                continue
            }

            val alpha = (255 * (1f - effect.progress)).toInt()

            // X mark
            val xSize = 40f * (1f + effect.progress * 0.5f)
            val missPaint = Paint().apply {
                color = Color.argb(alpha, 255, 100, 100)
                style = Paint.Style.STROKE
                strokeWidth = 6f
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
            }

            canvas.drawLine(
                effect.centerX - xSize, effect.centerY - xSize,
                effect.centerX + xSize, effect.centerY + xSize,
                missPaint
            )
            canvas.drawLine(
                effect.centerX + xSize, effect.centerY - xSize,
                effect.centerX - xSize, effect.centerY + xSize,
                missPaint
            )

            // Fade out circle
            val circlePaint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                color = Color.argb(alpha / 2, 200, 100, 100)
                isAntiAlias = true
            }
            canvas.drawCircle(
                effect.centerX,
                effect.centerY,
                xSize * 1.5f * effect.progress,
                circlePaint
            )
        }
    }

    private fun drawHoldEffects(canvas: Canvas) {
        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - lastFrameTime) / 1000f // Delta time in seconds
        lastFrameTime = currentTime

        val iterator = holdEffects.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val laneId = entry.key
            val effect = entry.value
            val rect = targetZones[laneId] ?: continue
            val baseColor = laneColors[laneId - 1]
            val centerX = rect.centerX()
            val centerY = targetZoneY + targetZoneHeight / 2

            // Cập nhật và vẽ hiệu ứng MISS
            if (effect.isMissed) {
                val elapsed =
                    System.currentTimeMillis() - effect.completionTime // Dùng completionTime để tính thời gian miss
                effect.fadeOutProgress =
                    (elapsed / 600f).coerceIn(0f, 1f) // Thời gian fade out ngắn hơn

                if (effect.fadeOutProgress >= 1f) {
                    iterator.remove()
                    continue
                }

                // Hiệu ứng "X" hoặc "fade out red circle" đơn giản khi miss
                val missAlpha = ((1f - effect.fadeOutProgress) * 200).toInt()
                val missRadius = laneWidth * 0.4f * effect.fadeOutProgress // Bắt đầu nhỏ và mở rộng
                val missPaint = Paint().apply {
                    color = Color.argb(missAlpha, 255, 50, 50) // Màu đỏ đậm
                    style = Paint.Style.FILL // Vòng tròn đặc
                    isAntiAlias = true
                }
                canvas.drawCircle(centerX, centerY, missRadius, missPaint)
                continue
            }

            // Xử lý hiệu ứng HOÀN THÀNH (completion effect)
            if (effect.isCompleted) {
                val elapsed = System.currentTimeMillis() - effect.completionTime
                effect.fadeOutProgress =
                    (elapsed / 1000f).coerceIn(0f, 1f) // Thời gian fade out lâu hơn cho completion

                if (effect.fadeOutProgress >= 1f && effect.completionParticles.isEmpty()) {
                    iterator.remove()
                    continue
                }

                if (effect.completionParticles.isEmpty() && elapsed < 50) { // Tạo hạt ngay khi hoàn thành
                    val numParticles = 30
                    for (i in 0 until numParticles) {
                        val angle = (Math.random() * Math.PI * 2).toFloat()
                        val speed = (50f + Math.random() * 100f).toFloat()
                        effect.completionParticles.add(
                            Particle(
                                centerX, centerY,
                                cos(angle) * speed, sin(angle) * speed,
                                alpha = 1f,
                                size = (10f + Math.random() * 10f).toFloat(),
                                color = lightenColor(
                                    baseColor,
                                    (0.2f + Math.random() * 0.5f).toFloat()
                                )
                            )
                        )
                    }
                }

                updateParticles(effect.completionParticles, deltaTime, isCompletionEffect = true)
                drawParticles(canvas, effect.completionParticles)

                val flashAlpha = ((1f - effect.fadeOutProgress) * 150).toInt()
                if (flashAlpha > 0) {
                    val flashRadius = laneWidth * 0.3f * (1f - (effect.fadeOutProgress * 0.5f))
                    val flashPaint = Paint().apply {
                        shader = RadialGradient(
                            centerX, centerY, flashRadius,
                            intArrayOf(
                                Color.argb(flashAlpha, 255, 255, 255),
                                Color.argb(
                                    flashAlpha / 2,
                                    Color.red(baseColor),
                                    Color.green(baseColor),
                                    Color.blue(baseColor)
                                ),
                                Color.TRANSPARENT
                            ),
                            floatArrayOf(0f, 0.5f, 1f),
                            Shader.TileMode.CLAMP
                        )
                        isAntiAlias = true
                    }
                    canvas.drawCircle(centerX, centerY, flashRadius, flashPaint)
                }

                continue
            }

            // Xử lý hiệu ứng ĐANG HOLD (khi người dùng đang giữ note)
            // Tạo hạt liên tục khi đang hold
            if (Math.random() < 0.2) { // Tần suất tạo hạt, có thể điều chỉnh
                val numParticles = 1 // Tạo 1 hạt mỗi lần
                for (i in 0 until numParticles) {
                    val angle = (Math.random() * Math.PI * 2).toFloat()
                    val speed = (20f + Math.random() * 50f).toFloat() // Tốc độ hạt khi đang giữ
                    val initialRadius =
                        (laneWidth * 0.1f + Math.random() * laneWidth * 0.2f).toFloat() // Bán kính xuất phát
                    effect.particles.add(
                        Particle(
                            centerX + cos(angle) * initialRadius,
                            centerY + sin(angle) * initialRadius,
                            cos(angle) * speed,
                            sin(angle) * speed,
                            alpha = 0.8f,
                            size = (5f + Math.random() * 5f).toFloat(),
                            color = lightenColor(baseColor, (0.3f + Math.random() * 0.3f).toFloat())
                        )
                    )
                }
            }

            updateParticles(effect.particles, deltaTime)
            drawParticles(canvas, effect.particles)

            // Optional: Thêm một vòng glow nhẹ khi đang giữ
            val glowPaint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 5f
                color = Color.argb(
                    (effect.progress * 100 + 50).toInt().coerceIn(0, 255),
                    Color.red(baseColor),
                    Color.green(baseColor),
                    Color.blue(baseColor)
                )
                maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
                isAntiAlias = true
            }
            canvas.drawCircle(centerX, centerY, laneWidth * 0.3f, glowPaint)
        }
    }

    private fun drawDynamicBackground(canvas: Canvas) {
        val gradient = LinearGradient(
            0f, 0f,
            width.toFloat(), height.toFloat(),
            currentGradientColors,
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        val bgPaint = Paint().apply {
            shader = gradient
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Background ripples
        val ripplePaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val iterator = backgroundRipples.iterator()
        while (iterator.hasNext()) {
            val ripple = iterator.next()
            val elapsed = System.currentTimeMillis() - ripple.startTime
            ripple.progress = (elapsed / 1500f).coerceIn(0f, 1f)

            if (ripple.progress >= 1f) {
                iterator.remove()
                continue
            }

            val maxRadius = kotlin.math.max(width, height).toFloat() * 1.5f
            val currentRadius = maxRadius * ripple.progress
            val alpha = ((1f - ripple.progress) * 100).toInt()

            ripplePaint.shader = RadialGradient(
                ripple.x, ripple.y, currentRadius,
                intArrayOf(
                    Color.argb(
                        alpha,
                        Color.red(ripple.color),
                        Color.green(ripple.color),
                        Color.blue(ripple.color)
                    ),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(ripple.x, ripple.y, currentRadius, ripplePaint)
        }
    }

    fun triggerHitEffect(laneId: Int) {
        val rect = targetZones[laneId] ?: return
        hitEffects.add(HitEffect(laneId, rect.centerX(), targetZoneY + targetZoneHeight / 2))
    }

    fun triggerMissEffect(laneId: Int) {
        val rect = targetZones[laneId] ?: return
        holdEffects[laneId]?.let {
            it.isMissed = true
            it.completionTime = System.currentTimeMillis()
        }
        missEffects.add(MissEffect(laneId, rect.centerX(), targetZoneY + targetZoneHeight / 2))
    }

    fun completeHoldNote(laneId: Int) {
        holdEffects[laneId]?.let { effect ->
            effect.isCompleted = true
            effect.completionTime = System.currentTimeMillis()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (viewModel.isPause.value == true) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                handleTouch(event.getX(pointerIndex), event.getY(pointerIndex), pointerId, true)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                handleTouch(event.getX(pointerIndex), event.getY(pointerIndex), pointerId, false)
            }

            MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until event.pointerCount) {
                    handleTouch(event.getX(i), event.getY(i), event.getPointerId(i), false)
                }
            }
        }
        return true
    }

    private val lanePointers = mutableMapOf<Int, Int>()

    private fun handleTouch(x: Float, y: Float, pointerId: Int, isDown: Boolean) {
        // Xác định lane dựa trên vị trí X
        val laneId = ((x / laneWidth).toInt() + 1).coerceIn(1, 4)

        if (isDown) {
            laneStates[laneId]?.isPressed = true
            lanePointers[laneId] = pointerId
            onPadPressed?.invoke(laneId)
        } else {
            if (lanePointers[laneId] == pointerId) {
                laneStates[laneId]?.isPressed = false
                lanePointers.remove(laneId)
                onPadReleased?.invoke(laneId)
            }
        }
    }

    fun update() {
        val activeNotes = noteSpawner?.getActiveNotes() ?: emptyList()

        // Reset glow intensity cho các lane không có note
        laneStates.forEach { (laneId, state) ->
            val hasActive = activeNotes.any {
                it.padId == laneId && !it.isHit && !it.isMissed
            }
            if (!hasActive) {
                state.glowIntensity = 0f
            }
        }

        // Update hold effects
        val currentTime = audioSyncClock?.getCurrentTimeSeconds() ?: 0f
        activeNotes.forEach { note ->
            if (note.type == NoteType.HOLD && note.isHolding && !note.isHit && !note.isMissed) {
                val holdProgress = (currentTime - note.holdStartTime) / note.holdDuration
                holdEffects.getOrPut(note.padId) { HoldEffect() }.apply {
                    progress = holdProgress.coerceIn(0f, 1f)
                }
            }
        }

        // Xóa hold effects cho các note không còn active
        val holdNoteLanes = activeNotes
            .filter { it.type == NoteType.HOLD && !it.isHit && !it.isMissed }
            .map { it.padId }
        holdEffects.keys.toList().forEach { laneId ->
            if (laneId !in holdNoteLanes && holdEffects[laneId]?.isCompleted != true) {
                holdEffects.remove(laneId)
            }
        }

//        invalidate()
    }

    private fun updateParticles(
        particles: MutableList<Particle>,
        deltaTime: Float,
        isCompletionEffect: Boolean = false
    ) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()

            p.x += p.vx * deltaTime
            p.y += p.vy * deltaTime
            p.alpha -= if (isCompletionEffect) 0.02f else 0.05f
            p.size -= if (isCompletionEffect) 0.05f else 0.1f

            if (p.alpha <= 0 || p.size <= 0) {
                iterator.remove()
            }
        }
    }

    private fun drawParticles(canvas: Canvas, particles: List<Particle>) {
        particles.forEach { p ->
            if (p.alpha > 0 && p.size > 0) {
                particlePaint.color = Color.argb(
                    (p.alpha * 255).toInt().coerceIn(0, 255),
                    Color.red(p.color),
                    Color.green(p.color),
                    Color.blue(p.color)
                )
                // Có thể thêm hiệu ứng glow cho hạt nếu muốn
                // particlePaint.maskFilter = BlurMaskFilter(p.size * 0.5f, BlurMaskFilter.Blur.NORMAL)
                canvas.drawCircle(p.x, p.y, p.size, particlePaint)
            }
        }
    }

    private fun lightenColor(color: Int, factor: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.rgb(
            (r + (255 - r) * factor).toInt().coerceIn(0, 255),
            (g + (255 - g) * factor).toInt().coerceIn(0, 255),
            (b + (255 - b) * factor).toInt().coerceIn(0, 255)
        )
    }

    fun resetLaneState(laneId: Int) {
        laneStates[laneId]?.apply {
            isPressed = false
            glowIntensity = 0f
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun handlePerfect(x: Float, y: Float, color1: Int, color2: Int, color3: Int) {
        Log.d("PerfectEffect", "🔥 handlePerfect() called at ($x, $y)")

        val targetColors = intArrayOf(color1, color2, color3)
        backgroundRipples.add(BackgroundRipple(x, y, 0f, color2))

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 800
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Float
            backgroundGradientProgress = progress

            val darkColor = "#0a0a0a".toColorInt()
            currentGradientColors[0] = interpolateColor(darkColor, targetColors[0], progress)
            currentGradientColors[1] = interpolateColor(darkColor, targetColors[1], progress)
            currentGradientColors[2] = interpolateColor(darkColor, targetColors[2], progress)

            Log.d("PerfectEffect", "💫 Progress = ${"%.2f".format(progress)}")
            Log.d(
                "PerfectEffect",
                "🌈 Current colors: [${currentGradientColors[0].toHexString()}, ${currentGradientColors[1].toHexString()}, ${currentGradientColors[2].toHexString()}]"
            )

            invalidate()
        }
        animator.start()
        Log.d("PerfectEffect", "🚀 Animation started")
    }

    fun resetBackground() {
        val animator = ValueAnimator.ofFloat(backgroundGradientProgress, 0f)
        animator.duration = 500
        animator.addUpdateListener { animation ->
            backgroundGradientProgress = animation.animatedValue as Float

            val darkColor = "#0a0a0a".toColorInt()
            currentGradientColors[0] = interpolateColor(
                currentGradientColors[0],
                darkColor,
                1f - backgroundGradientProgress
            )
            currentGradientColors[1] = interpolateColor(
                currentGradientColors[1],
                darkColor,
                1f - backgroundGradientProgress
            )
            currentGradientColors[2] = interpolateColor(
                currentGradientColors[2],
                darkColor,
                1f - backgroundGradientProgress
            )
            invalidate()
        }
        animator.start()

        backgroundRipples.clear()
    }

    private fun interpolateColor(startColor: Int, endColor: Int, fraction: Float): Int {
        val startR = Color.red(startColor)
        val startG = Color.green(startColor)
        val startB = Color.blue(startColor)

        val endR = Color.red(endColor)
        val endG = Color.green(endColor)
        val endB = Color.blue(endColor)

        val r = (startR + (endR - startR) * fraction).toInt()
        val g = (startG + (endG - startG) * fraction).toInt()
        val b = (startB + (endB - startB) * fraction).toInt()

        return Color.rgb(r, g, b)
    }

    var onPadPressed: ((Int) -> Unit)? = null
    var onPadReleased: ((Int) -> Unit)? = null

    fun setNoteSpawner(spawner: NoteSpawner) {
        this.noteSpawner = spawner
    }

    fun setAudioClock(clock: AudioSyncClock) {
        this.audioSyncClock = clock
    }
}