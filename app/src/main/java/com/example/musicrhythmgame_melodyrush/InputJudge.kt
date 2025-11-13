package com.example.musicrhythmgame_melodyrush

import android.util.Log
import com.example.musicrhythmgame_melodyrush.view.MelodyRushView
import kotlin.math.abs

class InputJudge(
    private val audioSyncClock: AudioSyncClock,
    private val noteSpawner: NoteSpawner,
    private val soundEffectPool: SoundEffectPool
) {
    
    private var score: Int = 0
    private var combo: Int = 0
    private var maxCombo: Int = 0
    
    private var perfectCount: Int = 0
    private var goodCount: Int = 0
    private var missCount: Int = 0
    
    private val activeHolds = mutableMapOf<Int, Note>()  // padId -> Note
    
    var onJudge: ((HitFeedback) -> Unit)? = null
    var onComboChange: ((Int) -> Unit)? = null
    var onScoreChange: ((Int) -> Unit)? = null

    fun onPadPressed(padId: Int, triggerHitEffect: (Int)-> Unit, triggerMissEffect: (Int)-> Unit): JudgeResult {
        val note = noteSpawner.getNoteForPad(padId)
        if (note == null) {
            return JudgeResult.NONE
        }

        if (note.isHit || note.isMissed) {
            return JudgeResult.NONE
        }

        val currentTime = audioSyncClock.getCurrentTimeSeconds()
        Log.d(
            "NoteJudge",
            "Pad $padId pressed | noteTime=${note.time} | currentTime=$currentTime | noteType=${note.type}"
        )

        return if (note.type == NoteType.HOLD) {
            val timeDiff = currentTime - note.time
            if (timeDiff < -noteSpawner.missWindow){
                triggerMissEffect(padId)
                handleMiss(note)
                onJudge?.invoke(HitFeedback(JudgeResult.EARLY, padId))
                JudgeResult.EARLY
            }else {
                // Hold note: bắt đầu giữ, chưa tính điểm
                note.isHolding = true
                note.holdStartTime = currentTime
                activeHolds[padId] = note
                soundEffectPool.playHoldStart()
                Log.d("NoteJudge", "Pad $padId → HOLD note started")

                onJudge?.invoke(HitFeedback(JudgeResult.HOLDING, padId))
                JudgeResult.HOLDING
            }

        } else {
            val timeDiff = currentTime - note.time
            val result = when {
                abs(timeDiff) <= noteSpawner.perfectWindow -> {
                    triggerHitEffect(padId)
                    handlePerfectHit(note)
                    Log.d("NoteJudge", "Pad $padId → PERFECT")
                    JudgeResult.PERFECT
                }
                abs(timeDiff) <= noteSpawner.goodWindow -> {
                    triggerHitEffect(padId)
                    handleGoodHit(note)
                    Log.d("NoteJudge", "Pad $padId → GOOD")
                    JudgeResult.GOOD
                }
                timeDiff < -noteSpawner.missWindow ->{
                    triggerMissEffect(padId)
                    handleMiss(note)
                    Log.d("NoteJudge", "Pad $padId → EARLY")
                    JudgeResult.EARLY
                }
                else -> {
                    triggerMissEffect(padId)
                    handleMiss(note)
                    Log.d("NoteJudge", "Pad $padId → MISS")
                    JudgeResult.MISS
                }
            }
            onJudge?.invoke(HitFeedback(result, padId))
            result
        }
    }



    fun onPadReleased(padId: Int, triggerMissEffect: (Int) -> Unit, completedHoldNote: (Int) -> Unit) {
        val holdNote = activeHolds[padId] ?: return

        val currentTime = audioSyncClock.getCurrentTimeSeconds()
        val holdTime = currentTime - holdNote.holdStartTime
        val expectedHoldTime = holdNote.holdDuration
        val holdDiff = abs(holdTime - expectedHoldTime)

        Log.d(
            "NoteJudge",
            "Pad $padId released | holdTime=$holdTime | expectedHoldTime=$expectedHoldTime | holdDiff=$holdDiff | start ${holdNote.holdStartTime} |curent $currentTime"
        )

        val result = when {
            holdDiff <= noteSpawner.perfectWindow -> {
                completedHoldNote(padId)
                addScore(100)
                soundEffectPool.playPerfectHit()
                holdNote.isHit = true
                Log.d("NoteJudge", "Pad $padId → PERFECT")
                JudgeResult.PERFECT
            }
            holdDiff <= noteSpawner.goodWindow -> {
                completedHoldNote(padId)
                addScore(50)
                soundEffectPool.playGoodHit()
                holdNote.isHit = true
                Log.d("NoteJudge", "Pad $padId → GOOD")
                JudgeResult.GOOD
            }
            else -> {
                triggerMissEffect(padId)
                breakCombo()
                soundEffectPool.playMiss()
                holdNote.isMissed = true
                Log.d("NoteJudge", "Pad $padId → MISS")
                JudgeResult.MISS
            }
        }

        holdNote.isHolding = false
        soundEffectPool.playHoldEnd()
        activeHolds.remove(padId)

        onJudge?.invoke(HitFeedback(result, padId))
    }


    private fun handlePerfectHit(note: Note) {
        note.isHit = true
        perfectCount++
        addScore(200)
        addCombo(2)
        soundEffectPool.playPerfectHit()
    }
    
    private fun handleGoodHit(note: Note) {
        note.isHit = true
        goodCount++
        addScore(100)
        addCombo()
        soundEffectPool.playGoodHit()
    }
    
    private fun handleMiss(note: Note) {
        note.isMissed = true
        missCount++
        breakCombo()
        soundEffectPool.playMiss()
    }
    
    fun checkNotes(gameView: MelodyRushView) {
        val currentTime = audioSyncClock.getCurrentTimeSeconds()

        noteSpawner.getActiveNotes().forEach { note ->
            if (!note.isHit && !note.isMissed) {
                val timeDiff = currentTime - note.time
                if (note.type == NoteType.HOLD && note.isHolding) {
                    return@forEach
                }
                if (timeDiff > noteSpawner.missWindow) {
                    handleMiss(note)
                    onJudge?.invoke(HitFeedback(JudgeResult.MISS, note.padId))
                }
            }
        }

        activeHolds.entries.toList().forEach { (padId, note) ->
            val currentTime = audioSyncClock.getCurrentTimeSeconds()
            val holdTime = currentTime - note.holdStartTime

            if (holdTime > note.holdDuration) {
                gameView.completeHoldNote(padId)
                note.isHit = true
                note.isHolding = false
                activeHolds.remove(padId)

                addScore(100)
                soundEffectPool.playPerfectHit()
                onJudge?.invoke(HitFeedback(JudgeResult.PERFECT, padId))
            }
        }
    }
    
    private fun addScore(points: Int) {
        score += points * (1 + combo / 10)
        onScoreChange?.invoke(score)
    }
    
    private fun addCombo(value: Int = 1) {
        combo+= value
        if (combo > maxCombo) maxCombo = combo
        onComboChange?.invoke(combo)
    }
    
    fun breakCombo() {
        combo = 0
        onComboChange?.invoke(combo)
    }
    
    fun getScore(): Int = score
    fun getCombo(): Int = combo
    fun getMaxCombo(): Int = maxCombo
    fun getPerfectCount(): Int = perfectCount
    fun getGoodCount(): Int = goodCount
    fun getMissCount(): Int = missCount
    
    fun reset() {
        score = 0
        combo = 0
        maxCombo = 0
        perfectCount = 0
        goodCount = 0
        missCount = 0
        activeHolds.clear()
    }
}