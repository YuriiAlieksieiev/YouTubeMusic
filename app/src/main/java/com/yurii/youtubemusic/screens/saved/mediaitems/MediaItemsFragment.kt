package com.yurii.youtubemusic.screens.saved.mediaitems

import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.View
import android.viewbinding.library.fragment.viewBinding
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yurii.youtubemusic.R
import com.yurii.youtubemusic.databinding.FragmentMediaItemsBinding
import com.yurii.youtubemusic.models.Category
import com.yurii.youtubemusic.models.MediaMetaData
import com.yurii.youtubemusic.ui.ConfirmDeletionDialog
import com.yurii.youtubemusic.ui.SelectCategoriesDialog
import com.yurii.youtubemusic.utilities.Injector
import com.yurii.youtubemusic.adapters.MediaListAdapter
import com.yurii.youtubemusic.adapters.MediaListAdapterController
import com.yurii.youtubemusic.screens.main.MainActivityViewModel
import com.yurii.youtubemusic.screens.youtube.models.Item
import com.yurii.youtubemusic.utilities.requireParcelable
import kotlinx.coroutines.flow.collectLatest

class MediaItemsFragment : Fragment(R.layout.fragment_media_items) {
    private val mainActivityViewModel: MainActivityViewModel by activityViewModels()
    private val viewModel: MediaItemsViewModel by viewModels {
        Injector.provideMediaItemsViewModel(requireContext(), requireArguments().requireParcelable(EXTRA_CATEGORY))
    }
    private lateinit var mediaItemsAdapterController: MediaListAdapterController
    private val binding: FragmentMediaItemsBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initViewModel()
        initRecyclerView(binding.mediaItems)

        lifecycleScope.launchWhenCreated {
            mainActivityViewModel.event.collectLatest {
                when (it) {
                    is MainActivityViewModel.Event.ItemHasBeenDeleted -> {
                        mediaItemsAdapterController.removeItemWithId(it.item.id)
                        checkWhetherMediaItemsAreEmpty()
                    }
                    is MainActivityViewModel.Event.ItemHasBeenDownloaded -> {
                        val metadata = viewModel.getMetaData(it.videoItem.id)
                        if (viewModel.category == Category.ALL || viewModel.category in metadata.categories) {
                            mediaItemsAdapterController.addNewMediaItem(metadata)
                            checkWhetherMediaItemsAreEmpty()
                        }
                    }
                    is MainActivityViewModel.Event.ItemHasBeenModified -> updateMediaItem(it.item)
                    else -> {
                        //nothing
                    }
                }
            }
        }
    }

    private fun updateMediaItem(mediaItem: MediaMetaData) {
        if (viewModel.category == Category.ALL) {
            mediaItemsAdapterController.updateMediaItem(mediaItem)
            return
        }

        if (viewModel.category in mediaItem.categories)
            addOrUpdateMediaItem(mediaItem)
        else
            mediaItemsAdapterController.removeItemWithId(mediaItem.mediaId)
        checkWhetherMediaItemsAreEmpty()
    }

    private fun initViewModel() {
        viewModel.mediaItems.observe(viewLifecycleOwner, Observer {
            binding.loadingBar.isVisible = false
            mediaItemsAdapterController.setMediaItems(it)
            checkWhetherMediaItemsAreEmpty()
        })

        viewModel.playbackState.observe(viewLifecycleOwner, Observer {
            if (it.state == PlaybackStateCompat.STATE_PLAYING || it.state == PlaybackStateCompat.STATE_PAUSED || it.state == PlaybackStateCompat.STATE_STOPPED) {
                mediaItemsAdapterController.onChangePlaybackState(it)
            }
        })
    }

    private fun initRecyclerView(recyclerView: RecyclerView) {
        val mediaItemsAdapter = MediaListAdapter(requireContext(), viewModel.category, MediaListAdapterCallBack())
        mediaItemsAdapterController = mediaItemsAdapter
        recyclerView.apply {
            layoutAnimation = android.view.animation.AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.bottom_lifting_animation)
            this.setHasFixedSize(true)
            this.layoutManager = LinearLayoutManager(requireContext())
            this.adapter = mediaItemsAdapter
        }
    }

    private fun checkWhetherMediaItemsAreEmpty() {
        if (mediaItemsAdapterController.isEmptyList()) {
            binding.noMediaItems.isVisible = true
            binding.mediaItems.isVisible = false
        } else {
            binding.noMediaItems.isVisible = false
            binding.mediaItems.isVisible = true
        }
    }

    private fun addOrUpdateMediaItem(mediaItem: MediaMetaData) {
        if (mediaItemsAdapterController.contains(mediaItem.mediaId))
            mediaItemsAdapterController.updateMediaItem(mediaItem)
        else
            mediaItemsAdapterController.addNewMediaItem(mediaItem)
    }

    private fun deleteMediaItem(mediaItem: MediaMetaData) {
        ConfirmDeletionDialog.create(
            titleId = R.string.dialog_confirm_deletion_music_title,
            messageId = R.string.dialog_confirm_deletion_music_message,
            onConfirm = {
                viewModel.deleteMediaItem(mediaItem)
                mediaItemsAdapterController.removeItemWithId(mediaItem.mediaId)
                mainActivityViewModel.notifyMediaItemHasBeenDeleted(mediaItem)
            }
        ).show(requireActivity().supportFragmentManager, "RequestToDeleteMediaItem")

    }

    private fun openCategoriesEditor(mediaItem: MediaMetaData) {
        SelectCategoriesDialog.selectCategories(requireContext(), mediaItem.categories) {
            mediaItem.categories.clear()
            mediaItem.categories.addAll(it)
            mainActivityViewModel.notifyMediaItemHasBeenModified(mediaItem)
        }
    }

    private inner class MediaListAdapterCallBack : MediaListAdapter.CallBack {
        override fun getPlaybackState(mediaItem: MediaMetaData): PlaybackStateCompat? = viewModel.getPlaybackState(mediaItem)

        override fun onOptionsClick(mediaItem: MediaMetaData, view: View) {
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.menuInflater.inflate(R.menu.media_item_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.item_delete_media_item -> deleteMediaItem(mediaItem)
                    R.id.item_edit_categories -> openCategoriesEditor(mediaItem)
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            popupMenu.show()
        }

        override fun onItemClick(mediaItem: MediaMetaData) {
            viewModel.onClickMediaItem(mediaItem)
        }

    }

    companion object {
        private const val EXTRA_CATEGORY: String = "com.yurii.youtubemusic.media.items.category"
        fun create(category: Category): MediaItemsFragment = MediaItemsFragment().apply {
            arguments = Bundle().apply {
                putParcelable(EXTRA_CATEGORY, category)
            }
        }
    }
}