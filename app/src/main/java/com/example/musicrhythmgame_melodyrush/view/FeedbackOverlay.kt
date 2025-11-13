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
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withTranslation
import com.example.musicrhythmgame_melodyrush.Constant
import com.example.musicrhythmgame_melodyrush.JudgeResult
import com.example.musicrhythmgame_melodyrush.MainViewModel
import kotlin.apply
import kotlin.collections.forEach
import kotlin.math.cos
import kotlin.math.sin
import kotlin.ranges.coerceIn
import kotlin.ranges.until

@SuppressLint("ViewConstructor")
class FeedbackOverlay @JvmOverloads constructor(
    private var viewModel: MainViewModel,
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val feedbackItems = mutableListOf<FeedbackItem>()
    private val particles = mutableListOf<Particle>()

    private val perfectPaint = Paint().apply {
        textSize = 80f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface= Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val goodPaint = Paint().apply {
        color = "#4ecdc4".toColorInt()
        textSize = 70f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val missPaint = Paint().apply {
        color = "#ff6b6b".toColorInt()
        textSize = 60f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }


    private val earlyPaint = Paint().apply {
        color = "#FFD700".toColorInt()
        textSize = 60f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val comboPaint = Paint().apply {
        color = Color.WHITE
        textSize = 100f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val comboLabelPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val glowPaint = Paint().apply {
        isAntiAlias = true
        maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
    }

    private data class FeedbackItem(
        val result: JudgeResult,
        val x: Float,
        val y: Float,
        val startTime: Long = System.currentTimeMillis(),
        var alpha: Int = 255,
        var scale: Float = 1.0f,
        var offsetY: Float = 0f,
        var rotation: Float = 0f
    )

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float = 1f,
        var size: Float,
        var color: Int,
        var rotation: Float = 0f,
        var rotationSpeed: Float = 0f
    )

    private var comboScale: Float = 1.0f
    private var perfectCount: Int = 0
    private var milestoneAnimationProgress: Float = 0f

    fun showFeedback(result: JudgeResult, padPosition: PointF) {
        Log.d(
            "FeedbackOverlay",
            "showFeedback called | result=$result | padPosition=(${padPosition.x}, ${padPosition.y})"
        )

        val item = FeedbackItem(
            result = result,
            x = padPosition.x,
            y = padPosition.y
        )
        feedbackItems.add(item)

        if (result == JudgeResult.PERFECT) {
            perfectCount++
            createPerfectParticles(padPosition.x, padPosition.y)

            if (perfectCount % 10 == 0) {
                triggerMilestoneEffect(padPosition.x, padPosition.y)
            }
        } else if (result == JudgeResult.MISS) {
            perfectCount = 0
        }

        if ((viewModel.currentCombo.value
                ?: 0) >= Constant.PERFECT_BACKGROUND && viewModel.bgPerfect.value == false
        ) {
            viewModel.setBgPerfect(true)
            viewModel.handlePerfect(
                padPosition.x,
                padPosition.y,
                "#CBBACC".toColorInt(),
                "#2580B3".toColorInt(),
                "#2580B3".toColorInt()
            )
        } else if ((viewModel.currentCombo.value ?: 0) < Constant.PERFECT_BACKGROUND
        ) {
            viewModel.setBgPerfect(false)
        }

        postDelayed({
            feedbackItems.remove(item)
        }, 1000)
    }

    private fun createPerfectParticles(x: Float, y: Float) {
        val particleCount = 15
        for (i in 0 until particleCount) {
            val angle = (360f / particleCount) * i * Math.PI / 180
            val speed = 3f + Math.random().toFloat() * 3f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle).toFloat() * speed,
                    vy = sin(angle).toFloat() * speed,
                    size = 8f + Math.random().toFloat() * 8f,
                    color = when ((Math.random() * 3).toInt()) {
                        0 -> "#FFE29F".toColorInt()
                        1 -> "#FFA99F".toColorInt()
                        else -> "#FF719A".toColorInt()
                    },
                    rotationSpeed = (Math.random().toFloat() - 0.5f) * 20f
                )
            )
        }
    }

    private fun triggerMilestoneEffect(x: Float, y: Float) {
        val burstCount = 30
        for (i in 0 until burstCount) {
            val angle = (360f / burstCount) * i * Math.PI / 180
            val speed = 5f + Math.random().toFloat() * 7f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle).toFloat() * speed,
                    vy = sin(angle).toFloat() * speed,
                    size = 12f + Math.random().toFloat() * 12f,
                    color = when ((Math.random() * 3).toInt()) {
                        0 -> "#FFE29F".toColorInt()
                        1 -> "#FFA99F".toColorInt()
                        else -> "#FF719A".toColorInt()
                    },
                    rotationSpeed = (Math.random().toFloat() - 0.5f) * 30f
                )
            )
        }

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 500
        animator.addUpdateListener { animation ->
            milestoneAnimationProgress = animation.animatedValue as Float
            invalidate()
        }
        animator.start()
    }

    fun updateCombo(combo: Int) {
        viewModel.setCurrentCombo(combo)

        // Animation scale cho combo
        val animator = ValueAnimator.ofFloat(1.5f, 1.0f)
        animator.duration = 200
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            comboScale = animation.animatedValue as Float
            invalidate()
        }
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val currentTime = System.currentTimeMillis()

        updateAndDrawParticles(canvas)

        feedbackItems.forEach { item ->
            val elapsed = currentTime - item.startTime
            val progress = (elapsed / 600f).coerceIn(0f, 1f)

            // Animation: fade out và di chuyển lên với rotation
            item.alpha = (255 * (1f - progress)).toInt()
            item.offsetY = -80f * progress
            item.scale = 1f + 0.3f * progress
            item.rotation = 360f * progress * 0.3f

            canvas.withTranslation(item.x, item.y + item.offsetY) {
                scale(item.scale, item.scale)

                val text = when (item.result) {
                    JudgeResult.PERFECT -> "PERFECT!"
                    JudgeResult.GOOD -> "GOOD"
                    JudgeResult.MISS -> "MISS"
                    JudgeResult.EARLY -> "EARLY"
                    JudgeResult.HOLDING -> ""
                    JudgeResult.NONE -> ""
                }

                if (item.result == JudgeResult.PERFECT) {
                    drawPerfectText(this, text, item.alpha)
                } else {
                    val paint = when (item.result) {
                        JudgeResult.GOOD -> goodPaint
                        JudgeResult.MISS -> missPaint
                        JudgeResult.EARLY -> earlyPaint
                        else -> goodPaint
                    }

                    paint.alpha = item.alpha

                    // Vẽ shadow
//                    paint.style = Paint.Style.STROKE
//                    paint.strokeWidth = 8f
//                    paint.color = Color.BLACK
//                    drawText(text, 0f, 0f, paint)

                    // Vẽ text chính
                    val originalColor = paint.color
                    paint.style = Paint.Style.FILL
                    paint.color = originalColor
                    drawText(text, 0f, 0f, paint)

                    paint.alpha = 255
                }

            }
        }

        // Vẽ combo ở góc trên bên phải
        if ((viewModel.currentCombo.value ?: 0) > 0) {
            drawCombo(canvas)
        }

        invalidate()
    }

    private fun drawPerfectText(canvas: Canvas, text: String, alpha: Int) {
        val textBounds = Rect()
        perfectPaint.getTextBounds(text, 0, text.length, textBounds)

        // Tạo gradient từ trên xuống
        val gradient = LinearGradient(
            0f,
            textBounds.top.toFloat(),
            0f,
            textBounds.bottom.toFloat(),
            intArrayOf(
                "#FFE29F".toColorInt(),
                "#FFA99F".toColorInt(),
                Color.parseColor("#FF719A")
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )

        perfectPaint.shader = gradient
        perfectPaint.alpha = alpha

        // Vẽ glow effect
        glowPaint.color = Color.parseColor("#FF719A")
        glowPaint.alpha = (alpha * 0.6f).toInt()
        canvas.drawText(text, 0f, 0f, glowPaint)

        // Vẽ stroke
        perfectPaint.style = Paint.Style.STROKE
        perfectPaint.strokeWidth = 10f
        canvas.drawText(text, 0f, 0f, perfectPaint)

        // Vẽ fill
        perfectPaint.style = Paint.Style.FILL
        canvas.drawText(text, 0f, 0f, perfectPaint)

        perfectPaint.alpha = 255
        perfectPaint.shader = null
    }

    private fun updateAndDrawParticles(canvas: Canvas) {
        val particlePaint = Paint().apply {
            isAntiAlias = true
        }

        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()

            particle.x += particle.vx
            particle.y += particle.vy
            particle.vy += 0.2f // Gravity
            particle.life -= 0.02f
            particle.rotation += particle.rotationSpeed

            if (particle.life <= 0) {
                iterator.remove()
                continue
            }

            particlePaint.color = particle.color
            particlePaint.alpha = (particle.life * 255).toInt()

            canvas.withTranslation(particle.x, particle.y) {
                rotate(particle.rotation)

                if (perfectCount % 10 < 5) {
                    drawCircle(0f, 0f, particle.size, particlePaint)
                } else {
                    drawStar(this, particlePaint, particle.size)
                }

            }
        }
    }

    private fun drawStar(canvas: Canvas, paint: Paint, size: Float) {
        val path = Path()
        val points = 5
        val outerRadius = size
        val innerRadius = size * 0.4f

        for (i in 0 until points * 2) {
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val angle = (i * Math.PI / points).toFloat()
            val x = cos(angle) * radius
            val y = sin(angle) * radius

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawCombo(canvas: Canvas) {
        val comboX = width - 150f
        val comboY = 150f

        canvas.withTranslation(comboX, comboY) {
            scale(comboScale, comboScale)

            // Vẽ số combo với gradient nếu đủ milestone
            if ((viewModel.currentCombo.value ?: 0) >= Constant.PERFECT_TEXT) {
                val gradient = LinearGradient(
                    0f, -50f, 0f, 50f,
                    intArrayOf(
                        "#FFE29F".toColorInt(),
                        "#FFA99F".toColorInt(),
                        "#FF719A".toColorInt()
                    ),
                    floatArrayOf(0f, 0.48f, 1f),
                    Shader.TileMode.CLAMP
                )
                comboPaint.shader = gradient
            } else {
                comboPaint.shader = null
                comboPaint.color = when {
                    (viewModel.currentCombo.value ?: 0) >= 20 -> "#4ecdc4".toColorInt()
                    else -> Color.WHITE
                }
            }

            drawText((viewModel.currentCombo.value ?: 0).toString(), 0f, 0f, comboPaint)
            drawText("COMBO", 0f, 60f, comboLabelPaint)

            comboPaint.shader = null
        }
    }

    fun reset() {
        feedbackItems.clear()
        particles.clear()
        viewModel.setCurrentCombo(0)
        comboScale = 1.0f
        perfectCount = 0
        milestoneAnimationProgress = 0f
        invalidate()
    }
}