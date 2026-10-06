package com.rishinnovations.isai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rishinnovations.isai.data.MusicRepository
import com.rishinnovations.isai.data.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository(application.contentResolver)
    private val _tracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val tracks: StateFlow<List<MusicTrack>> = _tracks

    fun scan() {
        viewModelScope.launch(Dispatchers.IO) {
            _tracks.value = repository.scan()
        }
    }
}
