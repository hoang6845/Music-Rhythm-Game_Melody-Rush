package com.example.musicrhythmgame_melodyrush

import androidx.annotation.Keep

@Keep
data class Note(
    val time: Float,
    val padId: Int,
    val type: NoteType,
    val holdDuration: Float = 0f
) {
    var isHit: Boolean = false
    var isMissed: Boolean = false
    var holdStartTime: Float = 0f
    var isHolding: Boolean = false
}

enum class NoteType {
    CLICK, HOLD
}

enum class JudgeResult {
    PERFECT, GOOD, MISS, NONE, HOLDING
}

data class HitFeedback(
    val result: JudgeResult,
    val padId: Int,
    val timestamp: Long = System.currentTimeMillis()
)