package com.yurii.youtubemusic.videoslist

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.yurii.youtubemusic.R
import com.yurii.youtubemusic.databinding.ItemLoadingBinding
import com.yurii.youtubemusic.databinding.ItemVideoBinding
import com.yurii.youtubemusic.models.VideoItem
import com.yurii.youtubemusic.services.downloader.Progress
import com.yurii.youtubemusic.utilities.*
import java.lang.IllegalStateException

enum class ItemState {
    DOWNLOAD, EXISTS, IS_LOADING
}

interface VideoItemInterface {
    fun onItemClickDownload(videoItem: VideoItem)
    fun remove(videoItem: VideoItem)
    fun isExisted(videoItem: VideoItem): Boolean
    fun isLoading(videoItem: VideoItem): Boolean
    fun getCurrentProgress(videoItem: VideoItem): Progress?
    fun cancelDownloading(videoItem: VideoItem)
}


class VideosListAdapter(context: Context, private val videoItemInterface: VideoItemInterface) : RecyclerView.Adapter<BaseViewHolder>() {
    companion object {
        private const val NO_POSITION = -1
        private var expandedPosition = NO_POSITION
    }

    val videos: MutableList<VideoItem> = mutableListOf()
    private var isLoaderVisible: Boolean = false
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return when (viewType) {
            VIEW_TYPE_NORMAL ->
                VideoViewHolder(DataBindingUtil.inflate(inflater, R.layout.item_video, parent, false)) {
                    notifyDataSetChanged()
                }
            VIEW_TYPE_LOADING ->
                LoadingViewHolder(DataBindingUtil.inflate<ItemLoadingBinding>(inflater, R.layout.item_loading, parent, false).root)
            else -> throw IllegalStateException("Illegal view type")
        }
    }

    override fun getItemViewType(position: Int): Int = if (isLoaderVisible)
        if (position == videos.lastIndex) VIEW_TYPE_LOADING else VIEW_TYPE_NORMAL
    else
        VIEW_TYPE_NORMAL


    fun setLoadingState() {
        isLoaderVisible = true
        videos.add(VideoItem())
        notifyItemInserted(videos.lastIndex)
    }

    fun removeLoadingState() {
        isLoaderVisible = false
        val position = videos.lastIndex
        videos.removeAt(position)
        notifyItemRemoved(position)
    }

    fun setNewVideoItems(videoItems: List<VideoItem>) {
        expandedPosition = NO_POSITION
        videos.clear()
        videos.addAll(videoItems)
        notifyDataSetChanged()
    }

    fun addVideoItems(videoItems: List<VideoItem>) {
        videos.addAll(videoItems)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = videos.size

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_LOADING)
            return

        val videoItem = videos[position]
        val videoViewHolder = holder as VideoViewHolder
        videoViewHolder.setOnRemoveClickListener(View.OnClickListener {
            videoItemInterface.remove(videoItem)
            videoViewHolder.bind(videoItem, position)
        })

        videoViewHolder.setOnDownloadClickListener(View.OnClickListener {
            videoItemInterface.onItemClickDownload(videoItem)
            videoViewHolder.bind(videoItem, position, state = ItemState.IS_LOADING)
        })

        videoViewHolder.setOnCancelClickListener(View.OnClickListener {
            videoItemInterface.cancelDownloading(videoItem)
            videoViewHolder.bind(videoItem, position)
        })

        when {
            videoItemInterface.isExisted(videoItem) -> videoViewHolder.bind(videoItem, position, state = ItemState.EXISTS)
            videoItemInterface.isLoading(videoItem) -> {
                videoViewHolder.bind(
                    videoItem,
                    position,
                    progress = videoItemInterface.getCurrentProgress(videoItem),
                    state = ItemState.IS_LOADING
                )
            }
            else -> videoViewHolder.bind(videoItem, position, state = ItemState.DOWNLOAD)
        }
    }

    class VideoViewHolder(val videoItemVideoBinding: ItemVideoBinding, private val onItemChange: ((position: Int) -> Unit)) :
        BaseViewHolder(videoItemVideoBinding.root) {
        private var isExpanded = false

        fun bind(videoItem: VideoItem, position: Int, progress: Progress? = null, state: ItemState = ItemState.DOWNLOAD) {
            videoItemVideoBinding.apply {
                this.videoItem = videoItem
                this.state = state
                this.progress = progress

                if (position == expandedPosition)
                    expandDetails().also { isExpanded = true }
                else
                    collapseDetails().also { isExpanded = false }

                this.root.setOnClickListener {
                    expandedPosition = if (isExpanded) NO_POSITION else position
                    if (expandedPosition == NO_POSITION)
                        collapseDetails().also { isExpanded = false; onItemChange.invoke(position) }
                    else
                        expandDetails().also { isExpanded = true; onItemChange.invoke(position) }
                }
            }.executePendingBindings()
        }


        private fun expandDetails() {
            videoItemVideoBinding.detailsPartLayout.visibility = View.VISIBLE
            // TODO Need to add some expanding animation
        }

        private fun collapseDetails() {
            videoItemVideoBinding.detailsPartLayout.visibility = View.GONE
            // TODO Need to add some collapsing animation
        }

        fun setOnDownloadClickListener(onClickListener: View.OnClickListener) {
            videoItemVideoBinding.download.setOnClickListener(onClickListener)
        }

        fun setOnRemoveClickListener(onClickListener: View.OnClickListener) {
            videoItemVideoBinding.remove.setOnClickListener(onClickListener)
        }

        fun setOnCancelClickListener(onClickListener: View.OnClickListener) {
            videoItemVideoBinding.cancelButton.setOnClickListener(onClickListener)
        }
    }
}