package com.example.musicrhythmgame_melodyrush

// AudioSyncClock.kt
class AudioSyncClock(private val musicPlayer: MusicPlayer) {

    private var startTime: Long = 0L
    private var pauseTime: Long = 0L
    private var isPaused: Boolean = true
    private var audioOffset: Long = 0L
    var timeDirection = 1f


    fun start() {
        startTime = System.currentTimeMillis()
        isPaused = false
    }

    fun pause() {
        if (!isPaused) {
            pauseTime = System.currentTimeMillis()
            isPaused = true
        }
    }

    fun resume() {
        if (isPaused) {
            val pauseDuration = System.currentTimeMillis() - pauseTime
            startTime += pauseDuration
            isPaused = false
        }
    }

    fun reset() {
        startTime = 0L
        pauseTime = 0L
        isPaused = true
        audioOffset = 0L
    }
    fun getCurrentTimeSeconds(): Float {
        return if (!isPaused) {
            musicPlayer.getCurrentPositionSeconds() + (audioOffset / 1000f)
        }  else {
            (pauseTime - startTime) / 1000f + (audioOffset / 1000f)
        }
    }

    fun rewind(seconds: Float) {
        val newPos = (musicPlayer.getCurrentPosition() - (seconds * 1000)).coerceAtLeast(0f)
        musicPlayer.seekTo(newPos.toLong())
    }

    fun getCurrentTimeMillis(): Long {
        return if (!isPaused) {
            musicPlayer.getCurrentPosition() + audioOffset
        } else {
            0L
        }
    }

    // Điều chỉnh offset nếu phát hiện lag
    fun adjustOffset(offsetMs: Long) {
        audioOffset += offsetMs
    }

    fun setOffset(offsetMs: Long) {
        audioOffset = offsetMs
    }

    fun getOffset(): Long = audioOffset

    fun isPaused(): Boolean = isPaused
}