package com.yurii.youtubemusic.models

import android.os.Parcelable
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import com.yurii.youtubemusic.db.PlaylistEntity
import kotlinx.android.parcel.Parcelize

@Parcelize
data class MediaItemPlaylist(val id: Long, val name: String) : Parcelable {
    companion object {
        val ALL = MediaItemPlaylist(-1, "All")
    }
}

fun MediaItemPlaylist.isDefault() = this == MediaItemPlaylist.ALL

fun MediaItemPlaylist.toPlaylistEntity() = PlaylistEntity(id, name)

fun List<PlaylistEntity>.toMediaItemPlaylists(): List<MediaItemPlaylist> {
    return map { MediaItemPlaylist(it.playlistId, it.name) }
}

fun MediaItemPlaylist.toMediaItem(): MediaBrowserCompat.MediaItem {
    val mediaDescription = MediaDescriptionCompat.Builder()
        .setMediaId(this.id.toString())
        .setTitle(this.name)
        .build()
    return MediaBrowserCompat.MediaItem(mediaDescription, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
}