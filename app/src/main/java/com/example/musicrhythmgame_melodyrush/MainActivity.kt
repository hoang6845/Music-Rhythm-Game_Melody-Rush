package com.example.musicrhythmgame_melodyrush

import android.graphics.PointF
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.example.musicrhythmgame_melodyrush.view.MelodyRushView
import com.example.musicrhythmgame_melodyrush.databinding.ActivityMainBinding
import com.example.musicrhythmgame_melodyrush.view.FeedbackOverlay

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    private lateinit var musicPlayer: MusicPlayer
    private lateinit var soundEffects: SoundEffectPool
    private lateinit var audioClock: AudioSyncClock
    private lateinit var noteSpawner: NoteSpawner
    private lateinit var inputJudge: InputJudge
    private lateinit var gameView: MelodyRushView
    private lateinit var feedbackOverlay: FeedbackOverlay

    private lateinit var scoreText: TextView
    private lateinit var comboText: TextView

    private var gameLoop: Runnable? = null
    private var isGameRunning = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        gameView = MelodyRushView(this)
        feedbackOverlay = FeedbackOverlay(viewModel, this)
        binding.gameView.addView(gameView)
        binding.gameView.addView(feedbackOverlay)

        scoreText = TextView(this).apply {
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
            x = 50f
            y = 50f
        }

        comboText = TextView(this).apply {
            textSize = 20f
            setTextColor(android.graphics.Color.WHITE)
            x = 50f
            y = 120f
        }

        binding.gameView.addView(scoreText)
        binding.gameView.addView(comboText)

        // Initialize game components
        initializeGame()

        // Start game
        startGame()

        viewModel.setPerfect { x, y, color1, color2, color3 ->
            gameView.handlePerfect(x, y, color1, color2, color3)
        }
        viewModel.bgPerfect.observe(this){value ->
            if (!value) gameView.resetBackground()
        }
    }

    private fun initializeGame() {
        // 1. Initialize core components
        musicPlayer = MusicPlayer(this)
        soundEffects = SoundEffectPool(this)
        audioClock = AudioSyncClock(musicPlayer)
        noteSpawner = NoteSpawner(audioClock)
        inputJudge = InputJudge(audioClock, noteSpawner, soundEffects)

        // 2. Load sound effects
        // soundEffects.loadSound(SoundEffectPool.SoundType.PERFECT_HIT, R.raw.perfect)
        // soundEffects.loadSound(SoundEffectPool.SoundType.GOOD_HIT, R.raw.good)
        // soundEffects.loadSound(SoundEffectPool.SoundType.MISS, R.raw.miss)

        // 3. Load notes từ JSON
        val json = assets.open("gamedata.json").bufferedReader().use { it.readText() }
        noteSpawner.loadNotesFromJson(json)
        gameView.setNoteSpawner(noteSpawner)
        gameView.setAudioClock(audioClock)

        // 4. Load music
        musicPlayer.loadMusic("android.resource://${packageName}/${R.raw.song}")

        // 5. Setup InputJudge callbacks
        setupInputJudgeCallbacks()

        // 6. Setup GameView callbacks
        setupGameViewCallbacks()
    }

    private fun setupInputJudgeCallbacks() {
        // Callback khi có judgment (Perfect/Good/Miss)
        inputJudge.onJudge = { feedback ->
            val padPosition = getPadPosition(feedback.padId)

            feedbackOverlay.showFeedback(feedback.result, padPosition)
        }

        inputJudge.onComboChange = { combo ->
            runOnUiThread {
                comboText.text = "Combo: $combo"
                feedbackOverlay.updateCombo(combo)
            }
        }

        inputJudge.onScoreChange = { score ->
            runOnUiThread {
                scoreText.text = "Score: $score"
            }
        }
    }

    private fun setupGameViewCallbacks() {
        // Khi pad được nhấn
        gameView.onPadPressed = { padId ->
            val result = inputJudge.onPadPressed(
                padId,
                { gameView.triggerHitEffect(padId) },
                { gameView.triggerMissEffect(padId) })
            // InputJudge sẽ tự động gọi callback onJudge
            // callback đó sẽ gọi feedbackOverlay.showFeedback()
        }

        // Khi pad được nhả (cho hold notes)
        gameView.onPadReleased = { padId ->
            inputJudge.onPadReleased(
                padId, { gameView.triggerMissEffect(padId) },
                { gameView.completeHoldNote(padId) })
        }
    }

    private fun getPadPosition(padId: Int): PointF {
        // Tính toán vị trí của pad dựa trên ID (1-9)
        val screenWidth = gameView.width
        val screenHeight = gameView.height

        val row = (padId - 1) / 3
        val col = (padId - 1) % 3

        val gridSize = kotlin.math.min(screenWidth, screenHeight) * 0.8f
        val padSize = (gridSize - 16f * 4) / 3
        val startX = (screenWidth - gridSize) / 2
        val startY = (screenHeight - gridSize) / 2

        val x = startX + col * (padSize + 16f) + 16f + padSize / 2
        val y = startY + row * (padSize + 16f) + 16f + padSize / 2

        return PointF(x, y)
    }

    private fun startGame() {
        isGameRunning = true

        // Reset components
        audioClock.reset()
        noteSpawner.reset()
        inputJudge.reset()
        feedbackOverlay.reset()

        // Start music
        audioClock.start()
        musicPlayer.play()

        // Start game loop
        startGameLoop()
    }

    private fun startGameLoop() {
        gameLoop = object : Runnable {
            override fun run() {
                if (!isGameRunning) return

                // 1. Update note spawner
                noteSpawner.update(inputJudge.onJudge, gameView, { inputJudge.breakCombo() })

                // 2. Check for missed notes
                inputJudge.checkNotes(gameView)

                // 3. Update game view
                gameView.update()

                // không cần gọi update() riêng

                // Loop at 60 FPS
                gameView.postDelayed(this, 16)
            }
        }

        gameView.post(gameLoop!!)
    }

    private fun pauseGame() {
        isGameRunning = false
        musicPlayer.pause()
        audioClock.pause()
        gameLoop?.let { gameView.removeCallbacks(it) }
    }

    private fun resumeGame() {
        isGameRunning = true
        musicPlayer.play()
        audioClock.resume()
        startGameLoop()
    }

    override fun onPause() {
        super.onPause()
        pauseGame()
    }

    override fun onResume() {
        super.onResume()
        if (isGameRunning) {
            resumeGame()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isGameRunning = false
        gameLoop?.let { gameView.removeCallbacks(it) }
        musicPlayer.release()
        soundEffects.release()
    }
}