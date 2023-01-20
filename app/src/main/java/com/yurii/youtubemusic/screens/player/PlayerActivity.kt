package com.yurii.youtubemusic.screens.player

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.viewbinding.library.activity.viewBinding
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.yurii.youtubemusic.R
import com.yurii.youtubemusic.databinding.ActivityPlayerBinding
import com.yurii.youtubemusic.screens.equalizer.EqualizerActivity
import com.yurii.youtubemusic.services.media.PlaybackState
import com.yurii.youtubemusic.utilities.Injector
import com.yurii.youtubemusic.utilities.setTint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {
    private val viewModel: PlayerControllerViewModel by viewModels { Injector.providePlayerControllerViewModel(application) }
    private val binding: ActivityPlayerBinding by viewBinding()
    private var isSeekBarChanging = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launchWhenCreated {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observePlaybackState() }
                launch { observePlayingPosition() }
                launch { observeQueueModes() }
            }
        }

        binding.loopMode.setOnClickListener { viewModel.loopStateClick() }

        binding.apply {
            actionButton.setOnClickListener { viewModel.pauseOrPlay() }
            openAudioEffects.setOnClickListener { startActivity(Intent(applicationContext, EqualizerActivity::class.java)) }
            moveToNext.setOnClickListener { viewModel.moveToNextTrack() }
            moveToPrevious.setOnClickListener { viewModel.moveToPreviousTrack() }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Nothing
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeekBarChanging = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isSeekBarChanging = false
                viewModel.seekTo(seekBar.progress)
            }
        })
    }

    private suspend fun observeQueueModes() = viewModel.isQueueLooped.collect { isLooped ->
        binding.loopMode.setTint(if (isLooped) R.color.colorAccent else R.color.gray)
    }

    private suspend fun observePlayingPosition() = viewModel.currentPosition.collectLatest {
        binding.currentTimePosition = it
        if (!isSeekBarChanging)
            binding.seekBar.progress = viewModel.getCurrentMappedPosition()
    }

    private suspend fun observePlaybackState() = viewModel.playbackState.collectLatest {
        when (it) {
            PlaybackState.None -> {
                //Nothing
            }
            is PlaybackState.Playing -> binding.apply {
                thumbnail.load(it.mediaItem.thumbnail)
                mediaItem = it.mediaItem
                isPlayingNow = !it.isPaused
            }
        }
    }
}