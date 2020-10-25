package com.yurii.youtubemusic.viewmodels

import android.app.Application
import android.content.Context
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.*
import com.yurii.youtubemusic.services.mediaservice.MusicServiceConnection
import com.yurii.youtubemusic.services.mediaservice.NOTHING_PLAYING
import com.yurii.youtubemusic.models.MediaMetaData
import java.lang.IllegalStateException

class PlayerBottomControllerViewModel(application: Application, private val musicServiceConnection: MusicServiceConnection) :
    AndroidViewModel(application) {
    val playingNow: LiveData<MediaMetaData?> = Transformations.map(musicServiceConnection.nowPlaying) {
        if (it != NOTHING_PLAYING)
            MediaMetaData.createFrom(it)
        else
            null
    }

    val currentPlaybackState: LiveData<PlaybackStateCompat> = musicServiceConnection.playbackState

    fun isPlaying(): Boolean = currentPlaybackState.value?.state?.run {
        this == PlaybackStateCompat.STATE_PLAYING || this == PlaybackStateCompat.STATE_BUFFERING
    } ?: false

    fun stopPlaying() = musicServiceConnection.transportControls.stop()

    fun pausePlaying() = musicServiceConnection.transportControls.pause()

    fun continuePlaying() = musicServiceConnection.transportControls.play()
}

@Suppress("UNCHECKED_CAST")
class PlayerBottomControllerFactory(private val context: Context, private val musicServiceConnection: MusicServiceConnection) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayerBottomControllerViewModel::class.java))
            return PlayerBottomControllerViewModel(context as Application, musicServiceConnection) as T
        throw IllegalStateException("Given the model class is not assignable from PlayerBottomControllerViewModel class")
    }
}