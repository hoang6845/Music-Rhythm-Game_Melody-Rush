package com.example.musicrhythmgame_melodyrush// MusicPlayer.kt
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

import androidx.media3.exoplayer.ExoPlayer

class MusicPlayer(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private var onPlaybackStateChanged: ((isPlaying: Boolean) -> Unit)? = null

    init {
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> onPlaybackStateChanged?.invoke(true)
                        Player.STATE_ENDED -> onPlaybackStateChanged?.invoke(false)
                    }
                }
            })
        }
    }

    fun loadMusic(uri: String) {
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun stop() {
        exoPlayer?.stop()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }

    fun getCurrentPositionSeconds(): Float {
        return getCurrentPosition() / 1000f
    }

    fun getDuration(): Long {
        return exoPlayer?.duration ?: 0L
    }

    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying ?: false
    }

    fun setPlaybackStateListener(listener: (Boolean) -> Unit) {
        onPlaybackStateChanged = listener
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }

    // Lấy thời gian chính xác cho sync
    fun getPlaybackPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }
}