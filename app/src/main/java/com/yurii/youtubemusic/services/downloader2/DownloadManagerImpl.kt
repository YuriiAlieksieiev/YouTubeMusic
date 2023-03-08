package com.yurii.youtubemusic.services.downloader2

import androidx.work.*
import com.yurii.youtubemusic.di.MainScope
import com.yurii.youtubemusic.models.MediaItemPlaylist
import com.yurii.youtubemusic.models.VideoItem
import com.yurii.youtubemusic.models.toMediaItem
import com.yurii.youtubemusic.services.media.MediaStorage
import com.yurii.youtubemusic.source.MediaCreator
import com.yurii.youtubemusic.source.MediaLibraryDomain
import com.yurii.youtubemusic.source.MediaRepository
import com.yurii.youtubemusic.utilities.asFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManagerImpl @Inject constructor(
    private val workManager: WorkManager,
    private val mediaCreator: MediaCreator,
    private val mediaRepository: MediaRepository,
    @MainScope private val coroutineScope: CoroutineScope,
    private val mediaStorage: MediaStorage,
    private val mediaLibraryDomain: MediaLibraryDomain
) : DownloadManager {
    private val cache = ConcurrentHashMap<String, Pair<DownloadManager.Status, UUID?>>()

    private val statusesFlow = MutableSharedFlow<DownloadManager.Status>()

    init {
        coroutineScope.launch { synchronize() }
        coroutineScope.launch { bindSynchronizationOfCacheWithMediaItems() }
        coroutineScope.launch { observeWorkManagerStatuses() }
    }

    private suspend fun observeWorkManagerStatuses() {
        workManager.getWorkInfosByTagLiveData(TAG_DOWNLOADING).asFlow().collect { downloadingJobs ->
            downloadingJobs.forEach { downloadingJob ->
                val cacheItem = cache.entries.find { it.value.second == downloadingJob.id }
                if (cacheItem != null) {
                    val status = getStatus(downloadingJob, cacheItem.key)
                    if (status != cacheItem.value.first) {
                        cache[cacheItem.key] = status to downloadingJob.id

                        if (downloadingJob.state == WorkInfo.State.SUCCEEDED)
                            mediaCreator.setMediaItemAsDownloaded(cacheItem.key)
                        else if (downloadingJob.state == WorkInfo.State.CANCELLED)
                            mediaRepository.getMediaItem(cacheItem.key)?.let { mediaRepository.delete(it) }

                        statusesFlow.emit(status)
                    }
                }
            }
        }
    }

    private suspend fun bindSynchronizationOfCacheWithMediaItems() {
        mediaRepository.mediaItemEntities.collect { mediaItems ->
            mediaItems.forEach {
                if (it.downloadingJobId != null) {
                    val status = DownloadManager.Status(it.mediaItemId, DownloadManager.State.Downloading(0, 0))
                    cache.putIfAbsent(it.mediaItemId, status to it.downloadingJobId)
                } else {
                    val fileSize = mediaStorage.getMediaFile(it.mediaItemId).length()
                    val status = DownloadManager.Status(it.mediaItemId, DownloadManager.State.Downloaded(fileSize))
                    cache.putIfAbsent(it.mediaItemId, status to null)
                }
            }
            cache.keys.forEach { mediaItemId ->
                if (mediaItems.find { it.mediaItemId == mediaItemId } == null) {
                    cache.remove(mediaItemId)
                    statusesFlow.emit(DownloadManager.Status(mediaItemId, DownloadManager.State.Download))
                }
            }
        }
    }

    private suspend fun synchronize() {
        mediaRepository.mediaItemEntities.first().forEach {
            if (it.downloadingJobId != null) {
                val workInfo: WorkInfo? = workManager.getWorkInfoById(it.downloadingJobId).await()
                if (workInfo == null) {
                    Timber.e("Work is not found for ${it.downloadingJobId}")
                    mediaLibraryDomain.deleteMediaItem(it.toMediaItem())
                }
            }
        }
    }

    override suspend fun enqueue(videoItem: VideoItem, playlists: List<MediaItemPlaylist>) {
        statusesFlow.emit(DownloadManager.Status(videoItem.id, DownloadManager.State.Downloading(0, 0)))
        val possibleMediaItem = mediaRepository.getDownloadingMediaItemEntity(videoItem)
        if (possibleMediaItem == null) {
            val downloadingJobId = enqueueDownloadingJob(videoItem)
            mediaCreator.registerDownloadingMediaItem(videoItem, playlists, downloadingJobId)
        }
    }

    override suspend fun retry(videoItem: VideoItem) {
        statusesFlow.emit(DownloadManager.Status(videoItem.id, DownloadManager.State.Downloading(0, 0)))
        if (mediaRepository.getDownloadingMediaItemEntity(videoItem) != null) {
            val downloadingJobId = enqueueDownloadingJob(videoItem)
            mediaRepository.updateDownloadingJobId(videoItem, downloadingJobId)
        }else
            throw IllegalStateException("Can not retry to download failed media item")
    }

    override suspend fun cancel(videoItem: VideoItem) {
        statusesFlow.emit(DownloadManager.Status(videoItem.id, DownloadManager.State.Download))
        mediaLibraryDomain.deleteMediaItem(videoItem)
        workManager.cancelUniqueWork(videoItem.id)
    }

    override fun getStatus(videoItem: VideoItem): DownloadManager.Status {
        val cacheItem = cache.entries.find { it.key == videoItem.id }
        return cacheItem?.value?.first ?: DownloadManager.Status(videoItem.id, DownloadManager.State.Download)
    }

    override fun observeStatus(): Flow<DownloadManager.Status> = statusesFlow.asSharedFlow()

    private fun getStatus(downloadingJobWorkInfo: WorkInfo, mediaItemId: String): DownloadManager.Status {
        return DownloadManager.Status(
            mediaItemId, when (downloadingJobWorkInfo.state) {
                WorkInfo.State.ENQUEUED -> DownloadManager.State.Downloading(0, 0)
                WorkInfo.State.RUNNING -> DownloadManager.State.Downloading(
                    currentSize = downloadingJobWorkInfo.progress.getLong(MusicDownloadWorker.PROGRESS_DOWNLOADED_SIZE, 0),
                    size = downloadingJobWorkInfo.progress.getLong(MusicDownloadWorker.PROGRESS_TOTAL_SIZE, 0)
                )
                WorkInfo.State.SUCCEEDED -> DownloadManager.State.Downloaded(
                    downloadingJobWorkInfo.outputData.getLong(MusicDownloadWorker.MEDIA_SIZE, 0)
                )
                WorkInfo.State.FAILED -> DownloadManager.State.Failed(
                    downloadingJobWorkInfo.outputData.getString(MusicDownloadWorker.ERROR_MESSAGE)
                )
                WorkInfo.State.BLOCKED -> TODO()
                WorkInfo.State.CANCELLED -> DownloadManager.State.Download
            }
        )
    }

    private fun enqueueDownloadingJob(videoItem: VideoItem): UUID {
        val data = workDataOf(
            MusicDownloadWorker.ARG_VIDEO_ID to videoItem.id,
            MusicDownloadWorker.ARG_VIDEO_THUMBNAIL_URL to videoItem.normalThumbnail
        )

        val request = OneTimeWorkRequestBuilder<MusicDownloadWorker>().also {
            it.setInputData(data)
            it.addTag(TAG_DOWNLOADING)
        }.build()

        workManager.enqueue(request)

        return request.id
    }

    companion object {
        private const val TAG_DOWNLOADING = "downloading"
    }
}