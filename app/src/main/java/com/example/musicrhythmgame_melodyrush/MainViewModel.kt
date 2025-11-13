package com.example.musicrhythmgame_melodyrush

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    private val _currentCombo = MutableLiveData<Int>()
    val currentCombo: LiveData<Int> = _currentCombo
    fun setCurrentCombo(value: Int) {
        _currentCombo.value = value
    }

    private val _bgPerfect = MutableLiveData<Boolean>(false)
    val bgPerfect: LiveData<Boolean> = _bgPerfect
    fun setBgPerfect(value: Boolean){
        _bgPerfect.value = value
    }

    var handlePerfectListener: ((x: Float, y: Float, color1: Int, color2: Int, color3: Int) -> Unit)? =
        null

    fun setPerfect(listener: (x: Float, y: Float, color1: Int, color2: Int, color3: Int) -> Unit) {
        this.handlePerfectListener = listener
    }

    fun handlePerfect(x: Float, y: Float, color1: Int, color2: Int, color3: Int) {
        handlePerfectListener?.invoke(x, y, color1, color2, color3)
    }
    private val _isPause = MutableLiveData<Boolean>()
    val isPause: LiveData<Boolean> = _isPause
    fun togglePause(value: Boolean){
        _isPause.value = value

    }
}