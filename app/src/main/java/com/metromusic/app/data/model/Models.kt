package com.metromusic.app.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?
)

@JsonClass(generateAdapter = true)
data class DownloadLink(
    val quality: String,
    val url: String
)

@JsonClass(generateAdapter = true)
data class SongAlbum(
    val id: String?,
    val name: String?,
    val url: String?
)

@JsonClass(generateAdapter = true)
data class ArtistMap(
    val id: String = "",
    val name: String = "",
    val role: String = "",
    val type: String = "",
    val image: List<DownloadLink> = emptyList(),
    val url: String = ""
)

@JsonClass(generateAdapter = true)
data class SongArtists(
    val primary: List<ArtistMap> = emptyList(),
    val featured: List<ArtistMap> = emptyList(),
    val all: List<ArtistMap> = emptyList()
)

@JsonClass(generateAdapter = true)
data class Song(
    val id: String,
    val name: String,
    val type: String = "",
    val year: String? = null,
    val releaseDate: String? = null,
    val duration: Int? = null,
    val label: String? = null,
    val explicitContent: Boolean = false,
    val playCount: Int? = null,
    val language: String = "",
    val hasLyrics: Boolean = false,
    val lyricsId: String? = null,
    val url: String = "",
    val copyright: String? = null,
    val album: SongAlbum = SongAlbum(null, null, null),
    val artists: SongArtists = SongArtists(),
    val image: List<DownloadLink> = emptyList(),
    val downloadUrl: List<DownloadLink> = emptyList()
) {
    val highQualityImageUrl: String?
        get() = image.lastOrNull()?.url
    
    val highQualityDownloadUrl: String?
        get() = downloadUrl.find { it.quality == "320kbps" }?.url
            ?: downloadUrl.lastOrNull()?.url
    
    val primaryArtistNames: String
        get() = artists.primary.joinToString(", ") { it.name }.ifEmpty {
            artists.all.joinToString(", ") { it.name }
        }
    
    val durationFormatted: String
        get() {
            val d = duration ?: return ""
            val min = d / 60
            val sec = d % 60
            return "%d:%02d".format(min, sec)
        }
}

@JsonClass(generateAdapter = true)
data class SearchSongResult(
    val total: Int = 0,
    val start: Int = 0,
    val results: List<Song> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SearchArtistResult(
    val total: Int = 0,
    val start: Int = 0,
    val results: List<SearchArtist> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SearchArtist(
    val id: String = "",
    val name: String = "",
    val role: String = "",
    val type: String = "",
    val image: List<DownloadLink> = emptyList(),
    val url: String = ""
) {
    val highQualityImageUrl: String?
        get() = image.lastOrNull()?.url
}

@JsonClass(generateAdapter = true)
data class ArtistDetail(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val type: String = "",
    val image: List<DownloadLink> = emptyList(),
    val followerCount: Int? = null,
    val fanCount: Int? = null,
    val isVerified: Boolean = false,
    val dominantLanguage: String? = null,
    val dominantType: String? = null,
    val topSongs: List<Song>? = null,
    val topAlbums: List<Album>? = null,
    val singles: List<Song>? = null
) {
    val highQualityImageUrl: String?
        get() = image.lastOrNull()?.url
}

@JsonClass(generateAdapter = true)
data class Album(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val year: Int? = null,
    val type: String = "",
    val playCount: Int? = null,
    val language: String = "",
    val explicitContent: Boolean = false,
    val songCount: Int? = null,
    val url: String = "",
    val artists: SongArtists? = null,
    val image: List<DownloadLink> = emptyList(),
    val songs: List<Song>? = null
) {
    val highQualityImageUrl: String?
        get() = image.lastOrNull()?.url
}

@JsonClass(generateAdapter = true)
data class Playlist(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val year: Int? = null,
    val type: String = "",
    val playCount: Int? = null,
    val language: String = "",
    val explicitContent: Boolean = false,
    val songCount: Int? = null,
    val url: String = "",
    val image: List<DownloadLink> = emptyList(),
    val songs: List<Song>? = null
) {
    val highQualityImageUrl: String?
        get() = image.lastOrNull()?.url
}

@JsonClass(generateAdapter = true)
data class ModuleItem(
    val id: String = "",
    val name: String = "",
    val subtitle: String? = null,
    val type: String = "",
    val image: List<DownloadLink> = emptyList(),
    val url: String? = null,
    val explicitContent: Boolean = false
) {
    val highQualityImageUrl: String?
        get() = image.lastOrNull()?.url
}

@JsonClass(generateAdapter = true)
data class ModuleSection(
    val title: String = "",
    val subtitle: String? = null,
    val source: String? = null,
    val position: Int = 0,
    val data: List<ModuleItem> = emptyList()
)

data class DownloadedSong(
    val id: String,
    val name: String,
    val artist: String,
    val album: String?,
    val duration: Int?,
    val filePath: String,
    val imageUrl: String?,
    val fileSize: Long = 0
) {
    val durationFormatted: String
        get() {
            val d = duration ?: return ""
            val min = d / 60
            val sec = d % 60
            return "%d:%02d".format(min, sec)
        }
    
    val fileSizeFormatted: String
        get() {
            val kb = fileSize / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1) "%.1f MB".format(mb) else "%.0f KB".format(kb)
        }
}
