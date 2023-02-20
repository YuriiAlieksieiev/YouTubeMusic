package com.yurii.youtubemusic.services.downloader

import androidx.annotation.IntRange
import com.yurii.youtubemusic.models.MediaItemPlaylist
import com.yurii.youtubemusic.models.Progress
import com.yurii.youtubemusic.models.VideoItem
import java.lang.Exception

interface MusicDownloader {
    interface CallBack {
        fun onFinished(videoItem: VideoItem, playlists: List<MediaItemPlaylist>)
        fun onChangeProgress(videoItem: VideoItem, progress: Progress)
        fun onErrorOccurred(videoItem: VideoItem, error: Exception)
    }

    fun setCallback(callBack: CallBack)
    fun download(videoItem: VideoItem, playlists: List<MediaItemPlaylist>)
    fun retryToDownload(videoItem: VideoItem)
    fun cancel(videoItem: VideoItem)
    fun isItemDownloading(videoItem: VideoItem): Boolean
    fun isDownloadingFailed(videoItem: VideoItem): Boolean
    fun getError(videoItem: VideoItem): Exception?

    @IntRange(from = 0, to = 100)
    fun getCompletedProgress(): Int
    fun isQueueEmpty(): Boolean

    fun getProgress(videoItem: VideoItem): Progress?
}