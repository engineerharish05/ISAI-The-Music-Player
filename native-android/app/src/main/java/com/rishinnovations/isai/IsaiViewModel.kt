package com.rishinnovations.isai
import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
class IsaiViewModel(app:Application):AndroidViewModel(app){private val repo=MusicRepository(app.contentResolver);private val _songs=MutableStateFlow<List<Song>>(emptyList());val songs:StateFlow<List<Song>>=_songs;private var controller:MediaController?=null;fun scan(){viewModelScope.launch{_songs.value=repo.scan()}};fun connect(){val t=SessionToken(getApplication(),ComponentName(getApplication(),PlaybackService::class.java));val f=MediaController.Builder(getApplication(),t).buildAsync();f.addListener({controller=f.get()},{it.run()})};fun play(s:Song){controller?.let{c->c.setMediaItem(MediaItem.Builder().setUri(s.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album).build()).build());c.prepare();c.play()}}}