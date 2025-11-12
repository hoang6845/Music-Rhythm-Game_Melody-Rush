package com.example.musicrhythmgame_melodyrush// SoundEffectPool.kt
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class SoundEffectPool(private val context: Context) {
    
    private var soundPool: SoundPool? = null
    private val soundMap = mutableMapOf<SoundType, Int>()
    
    enum class SoundType {
        PERFECT_HIT,
        GOOD_HIT,
        MISS,
        HOLD_START,
        HOLD_END
    }
    
    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build()
    }
    
    fun loadSound(type: SoundType, resourceId: Int) {
        soundPool?.let { pool ->
            val soundId = pool.load(context, resourceId, 1)
            soundMap[type] = soundId
        }
    }
    
    fun playSound(type: SoundType, volume: Float = 1.0f) {
        soundMap[type]?.let { soundId ->
            soundPool?.play(soundId, volume, volume, 1, 0, 1.0f)
        }
    }
    
    fun playPerfectHit() {
        playSound(SoundType.PERFECT_HIT, 1.0f)
    }
    
    fun playGoodHit() {
        playSound(SoundType.GOOD_HIT, 0.8f)
    }
    
    fun playMiss() {
        playSound(SoundType.MISS, 0.5f)
    }
    
    fun playHoldStart() {
        playSound(SoundType.HOLD_START, 0.9f)
    }
    
    fun playHoldEnd() {
        playSound(SoundType.HOLD_END, 0.9f)
    }
    
    fun release() {
        soundPool?.release()
        soundPool = null
        soundMap.clear()
    }
}