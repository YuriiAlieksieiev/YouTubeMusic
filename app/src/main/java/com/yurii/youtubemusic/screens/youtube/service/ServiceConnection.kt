package com.yurii.youtubemusic.screens.youtube.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.yurii.youtubemusic.models.Category
import com.yurii.youtubemusic.screens.youtube.models.Progress
import com.yurii.youtubemusic.screens.youtube.models.VideoItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

import java.lang.Exception

class ServiceConnectionException : Exception("Cannot connect to the service")

class ServiceConnection(private val context: Context) : ServiceConnection {

    private val _downloadingReport: MutableSharedFlow<MusicDownloaderService.DownloadingReport> = MutableSharedFlow()
    val downloadingReport = _downloadingReport.asSharedFlow()

    private val _downloadingProgress: MutableSharedFlow<Pair<VideoItem, Progress>> = MutableSharedFlow()
    val downloadingProgress = _downloadingProgress.asSharedFlow()

    private val serviceIntent = Intent(context, MusicDownloaderService::class.java)
    private var service: MusicDownloaderService.ServiceInterface? = null
    private var isConnected = false

    @Throws(ServiceConnectionException::class)
    fun connect() {
        val status = context.bindService(serviceIntent, this, Context.BIND_AUTO_CREATE)
        if (!status)
            throw ServiceConnectionException()
    }


    fun download(videoItem: VideoItem, categories: List<Category>) = service?.downloadMusicFrom(videoItem, categories)

    fun retryToDownload(videoItem: VideoItem) = service?.retryToDownload(videoItem)

    fun cancelDownloading(videoItem: VideoItem) = service?.cancelDownloading(videoItem)

    fun isItemDownloading(videoItem: VideoItem): Boolean = service?.isItemDownloading(videoItem) ?: false

    fun getProgress(videoItem: VideoItem): Progress? = service?.getProgress(videoItem)

    fun isDownloadingFailed(videoItem: VideoItem): Boolean = service?.isDownloadingFailed(videoItem) ?: false

    fun getError(videoItem: VideoItem): Exception? = service?.getLastError(videoItem)

    override fun onServiceDisconnected(name: ComponentName?) {
        isConnected = false
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        isConnected = true
        this.service = (service as MusicDownloaderService.ServiceInterface).apply {
            setFlowCallbacks(_downloadingReport, _downloadingProgress)
        }
    }
}