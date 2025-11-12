package com.example.musicrhythmgame_melodyrush

import com.example.musicrhythmgame_melodyrush.view.MelodyRushView
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

class NoteSpawner(private val audioSyncClock: AudioSyncClock) {

    private val allNotes = mutableListOf<Note>()
    private val activeNotes = mutableListOf<Note>()
    private var currentNoteIndex = 0

    private val spawnOffset = 3f

    val perfectWindow = 0.15f
    val goodWindow = 0.3f
    val missWindow = 0.3f

    data class NoteData(
        @SerializedName("time") val time: Float,
        @SerializedName("pad_id") val padId: Int,
        @SerializedName("type") val type: String,
        @SerializedName("hold_duration") val holdDuration: Float
    )

    fun loadNotesFromJson(jsonString: String) {
        val gson = Gson()
        val noteDataList = gson.fromJson(jsonString, Array<NoteData>::class.java)

        allNotes.clear()
        allNotes.addAll(noteDataList.map { data ->
            Note(
                time = data.time,
                padId = data.padId,
                type = if (data.type == "hold") NoteType.HOLD else NoteType.CLICK,
                holdDuration = data.holdDuration
            )
        }.sortedBy { it.time })

        currentNoteIndex = 0
        activeNotes.clear()
    }

    fun update(onJudge: ((HitFeedback) -> Unit)?, gameView: MelodyRushView, breakCombo: () -> Unit) {
        val currentTime = audioSyncClock.getCurrentTimeSeconds()

        while (currentNoteIndex < allNotes.size) {
            val note = allNotes[currentNoteIndex]

            if (currentTime >= note.time - spawnOffset) {
                activeNotes.add(note)
                currentNoteIndex++
            } else {
                break
            }
        }

        activeNotes.removeAll { note ->
            val timeDiff = currentTime - note.time

            if (timeDiff > missWindow && !note.isHit && !note.isMissed && note.type == NoteType.CLICK) {
                note.isMissed = true
//                gameView.triggerMissEffect(note.padId)
                onJudge?.invoke(HitFeedback(JudgeResult.MISS, note.padId))
                breakCombo()
                return@removeAll true
            }

            if (note.isHit && note.type == NoteType.CLICK) {
                return@removeAll true
            }

            if (note.isHit && note.type == NoteType.HOLD && !note.isHolding) {
                val holdTime = currentTime - note.holdStartTime
                return@removeAll holdTime >= note.holdDuration
            }

            false
        }
    }

    fun getActiveNotes(): List<Note> = activeNotes.toList()

    fun getNoteForPad(padId: Int): Note? {
        val currentTime = audioSyncClock.getCurrentTimeSeconds()

        return activeNotes
            .filter { it.padId == padId && !it.isHit && !it.isMissed }
            .minByOrNull { kotlin.math.abs(it.time - currentTime) }
    }

    fun getAllNotes(): List<Note> = allNotes.toList()

    fun reset() {
        currentNoteIndex = 0
        activeNotes.clear()
        allNotes.forEach {
            it.isHit = false
            it.isMissed = false
            it.isHolding = false
        }
    }

    fun getSpawnOffset(): Float = spawnOffset
}