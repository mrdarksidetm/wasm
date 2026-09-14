package com.mrdartsidetm.wasm.util

import android.media.MediaPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Manages native audio playback for Instagram voice notes (.mp4 audio container).
 * Ensures single active audio playback at a time with play/pause, seeking, and position tracking.
 */
class AudioPlayerManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _currentPlayingPath = MutableStateFlow<String?>(null)
    val currentPlayingPath: StateFlow<String?> = _currentPlayingPath.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    fun togglePlay(file: File) {
        val path = file.absolutePath
        if (_currentPlayingPath.value == path) {
            if (_isPlaying.value) {
                pause()
            } else {
                resume()
            }
        } else {
            playNew(file)
        }
    }

    private fun playNew(file: File) {
        if (!file.exists() || file.length() == 0L) return
        stop()

        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPositionMs.value = duration
                    stopProgressTracking()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    true
                }
            }

            mediaPlayer = player
            _currentPlayingPath.value = file.absolutePath
            _durationMs.value = player.duration
            _currentPositionMs.value = 0
            player.start()
            _isPlaying.value = true

            startProgressTracking()
        } catch (e: Exception) {
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
                stopProgressTracking()
            }
        }
    }

    fun resume() {
        mediaPlayer?.let { player ->
            player.start()
            _isPlaying.value = true
            startProgressTracking()
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let { player ->
            player.seekTo(positionMs)
            _currentPositionMs.value = positionMs
        }
    }

    fun stop() {
        stopProgressTracking()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // ignore release errors
        }
        mediaPlayer = null
        _isPlaying.value = false
        _currentPlayingPath.value = null
        _currentPositionMs.value = 0
        _durationMs.value = 0
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = scope.launch {
            while (_isPlaying.value && isActive) {
                mediaPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            _currentPositionMs.value = player.currentPosition
                        }
                    } catch (e: Exception) {
                        // ignore state query errors
                    }
                }
                delay(100)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
