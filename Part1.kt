@file:OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.malik.player

import android.app.Application
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

// ألوان وثيم Lark Player
val LarkOrange = Color(0xFFFF5722)
val LarkOrangeLight = Color(0xFFFF8A65)
val LarkDarkBg = Color(0xFF121212)
val LarkDarkSurface = Color(0xFF1E1E1E)
val LarkDarkCard = Color(0xFF282828)
val LarkLightBg = Color(0xFFF8F9FA)
val LarkLightSurface = Color(0xFFFFFFFF)
val LarkLightCard = Color(0xFFF1F3F5)

enum class ThemeMode { SYSTEM, DARK, LIGHT }
enum class AppLanguage { AR, EN }
enum class SortOption { DATE_ADDED, TITLE, DURATION, SIZE }
enum class RepeatModeState { OFF, REPEAT_ALL, REPEAT_ONE }

data class MediaItemModel(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val albumArtUri: Uri?,
    val path: String,
    val folderName: String,
    val size: Long,
    val dateAdded: Long,
    val isVideo: Boolean,
    val resolution: String = ""
)

data class FolderModel(
    val name: String,
    val path: String,
    val audioCount: Int,
    val videoCount: Int,
    val items: List<MediaItemModel>
)

data class AlbumModel(
    val name: String,
    val artist: String,
    val albumArtUri: Uri?,
    val songs: List<MediaItemModel>
)

data class ArtistModel(
    val name: String,
    val songs: List<MediaItemModel>
)

data class PlaylistModel(
    val id: String,
    val name: String,
    val mediaIds: List<Long>
)

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class AppStrings(
    val appName: String,
    val musicTab: String,
    val videoTab: String,
    val foldersTab: String,
    val meTab: String,
    val songsSubTab: String,
    val playlistsSubTab: String,
    val albumsSubTab: String,
    val artistsSubTab: String,
    val favorites: String,
    val shuffleAll: String,
    val searchHint: String,
    val noMediaFound: String,
    val grantPermissionMsg: String,
    val grantPermissionBtn: String,
    val themeTitle: String,
    val themeSystem: String,
    val themeDark: String,
    val themeLight: String,
    val languageTitle: String,
    val equalizer: String,
    val sleepTimer: String,
    val playbackSpeed: String,
    val playInBackground: String,
    val floatingWindow: String,
    val lyrics: String,
    val noLyricsFound: String,
    val createPlaylist: String,
    val playlistNameHint: String,
    val add: String,
    val cancel: String,
    val sortBy: String,
    val sortByDate: String,
    val sortByTitle: String,
    val sortByDuration: String,
    val sortBySize: String,
    val addToPlaylist: String,
    val fileInfo: String,
    val bassBoost: String,
    val timerOff: String,
    val minutes: String,
    val songsCount: String,
    val videosCount: String
)

val ArabicStrings = AppStrings(
    appName = "M4LIK Player",
    musicTab = "الموسيقى",
    videoTab = "الفيديو",
    foldersTab = "المجلدات",
    meTab = "الإعدادات",
    songsSubTab = "الأغاني",
    playlistsSubTab = "قوائم التشغيل",
    albumsSubTab = "الألبومات",
    artistsSubTab = "الفنانون",
    favorites = "المفضلة",
    shuffleAll = "تشغيل عشوائي للكل",
    searchHint = "ابحث في M4LIK Player...",
    noMediaFound = "لا توجد ملفات وسائط",
    grantPermissionMsg = "يرجى منح إذن الوصول للملفات لعرض وتشغيل الصوتيات والفيديوهات على جهازك",
    grantPermissionBtn = "منح الإذن الآن",
    themeTitle = "المظهر (Theme)",
    themeSystem = "حسب الجهاز",
    themeDark = "الوضع الداكن",
    themeLight = "الوضع الفاتح",
    languageTitle = "اللغة (Language)",
    equalizer = "معادل الصوت (Equalizer)",
    sleepTimer = "مؤقت النوم",
    playbackSpeed = "سرعة التشغيل",
    playInBackground = "تشغيل كصوت بالخلفية",
    floatingWindow = "نافذة عائمة (PiP)",
    lyrics = "كلمات الأغنية",
    noLyricsFound = "لا يوجد ملف كلمات (.lrc) بنفس اسم الأغنية",
    createPlaylist = "إنشاء قائمة تشغيل جديدة",
    playlistNameHint = "اسم القائمة...",
    add = "حفظ",
    cancel = "إلغاء",
    sortBy = "ترتيب حسب",
    sortByDate = "تاريخ الإضافة",
    sortByTitle = "الاسم",
    sortByDuration = "المدة",
    sortBySize = "الحجم",
    addToPlaylist = "إضافة إلى قائمة تشغيل",
    fileInfo = "تفاصيل الملف",
    bassBoost = "تعزيز البيز (Bass Boost)",
    timerOff = "إيقاف المؤقت",
    minutes = "دقيقة",
    songsCount = "أغنية",
    videosCount = "فيديو"
)

val EnglishStrings = AppStrings(
    appName = "M4LIK Player",
    musicTab = "Music",
    videoTab = "Video",
    foldersTab = "Folders",
    meTab = "Settings",
    songsSubTab = "Songs",
    playlistsSubTab = "Playlists",
    albumsSubTab = "Albums",
    artistsSubTab = "Artists",
    favorites = "Favorites",
    shuffleAll = "Shuffle All",
    searchHint = "Search in M4LIK Player...",
    noMediaFound = "No media files found",
    grantPermissionMsg = "Please grant storage permission to scan and play your local music and videos",
    grantPermissionBtn = "Grant Permission",
    themeTitle = "Appearance (Theme)",
    themeSystem = "System Default",
    themeDark = "Dark Mode",
    themeLight = "Light Mode",
    languageTitle = "Language (اللغة)",
    equalizer = "Equalizer",
    sleepTimer = "Sleep Timer",
    playbackSpeed = "Playback Speed",
    playInBackground = "Play Audio in Background",
    floatingWindow = "Floating Window (PiP)",
    lyrics = "Lyrics",
    noLyricsFound = "No synced lyrics (.lrc) file found for this track",
    createPlaylist = "Create New Playlist",
    playlistNameHint = "Playlist name...",
    add = "Save",
    cancel = "Cancel",
    sortBy = "Sort By",
    sortByDate = "Date Added",
    sortByTitle = "Title",
    sortByDuration = "Duration",
    sortBySize = "File Size",
    addToPlaylist = "Add to Playlist",
    fileInfo = "File Details",
    bassBoost = "Bass Boost",
    timerOff = "Turn Off Timer",
    minutes = "min",
    songsCount = "Songs",
    videosCount = "Videos"
)

val LocalStrings = staticCompositionLocalOf { ArabicStrings }

private val DarkColorPalette = darkColorScheme(
    primary = LarkOrange,
    secondary = LarkOrangeLight,
    background = LarkDarkBg,
    surface = LarkDarkSurface,
    surfaceVariant = LarkDarkCard,
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorPalette = lightColorScheme(
    primary = LarkOrange,
    secondary = LarkOrangeLight,
    background = LarkLightBg,
    surface = LarkLightSurface,
    surfaceVariant = LarkLightCard,
    onPrimary = Color.White,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A)
)

@Composable
fun M4likPlayerTheme(
    themeMode: ThemeMode,
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colors = if (isDark) DarkColorPalette else LightColorPalette
    val strings = if (language == AppLanguage.AR) ArabicStrings else EnglishStrings
    val layoutDirection = if (language == AppLanguage.AR) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalStrings provides strings,
        LocalLayoutDirection provides layoutDirection
    ) {
        MaterialTheme(
            colorScheme = colors,
            content = content
        )
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

fun formatFileSize(sizeBytes: Long): String {
    val mb = sizeBytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        String.format("%.2f GB", mb / 1024.0)
    } else {
        String.format("%.1f MB", mb)
    }
}

class PreferencesManager(context: Context) {
    private val prefs = context.getSharedPreferences("m4lik_player_prefs", Context.MODE_PRIVATE)

    fun getThemeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.DARK.name)!!) }
            .getOrDefault(ThemeMode.DARK)

    fun setThemeMode(mode: ThemeMode) = prefs.edit().putString("theme_mode", mode.name).apply()

    fun getLanguage(): AppLanguage =
        runCatching { AppLanguage.valueOf(prefs.getString("app_lang", AppLanguage.AR.name)!!) }
            .getOrDefault(AppLanguage.AR)

    fun setLanguage(lang: AppLanguage) = prefs.edit().putString("app_lang", lang.name).apply()

    fun getSortOption(): SortOption =
        runCatching { SortOption.valueOf(prefs.getString("sort_option", SortOption.DATE_ADDED.name)!!) }
            .getOrDefault(SortOption.DATE_ADDED)

    fun setSortOption(option: SortOption) = prefs.edit().putString("sort_option", option.name).apply()

    fun getFavorites(): Set<Long> =
        (prefs.getStringSet("favorites_ids", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()

    fun toggleFavorite(id: Long): Set<Long> {
        val current = getFavorites().toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        prefs.edit().putStringSet("favorites_ids", current.map { it.toString() }.toSet()).apply()
        return current
    }

    fun getPlaylists(): List<PlaylistModel> {
        val jsonStr = prefs.getString("playlists_json", "[]") ?: "[]"
        val list = mutableListOf<PlaylistModel>()
        runCatching {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val idsArray = obj.getJSONArray("mediaIds")
                val ids = mutableListOf<Long>()
                for (j in 0 until idsArray.length()) ids.add(idsArray.getLong(j))
                list.add(PlaylistModel(obj.getString("id"), obj.getString("name"), ids))
            }
        }
        return list
    }

    fun savePlaylists(playlists: List<PlaylistModel>) {
        val array = JSONArray()
        playlists.forEach { pl ->
            val obj = JSONObject()
            obj.put("id", pl.id)
            obj.put("name", pl.name)
            val ids = JSONArray()
            pl.mediaIds.forEach { ids.put(it) }
            obj.put("mediaIds", ids)
            array.put(obj)
        }
        prefs.edit().putString("playlists_json", array.toString()).apply()
    }
}

class MediaRepository(private val context: Context) {

    suspend fun scanAllMedia(sortOption: SortOption): Pair<List<MediaItemModel>, List<MediaItemModel>> =
        withContext(Dispatchers.IO) {
            val audioList = scanAudio()
            val videoList = scanVideo()
            Pair(sortItems(audioList, sortOption), sortItems(videoList, sortOption))
        }

    fun sortItems(list: List<MediaItemModel>, sortOption: SortOption): List<MediaItemModel> =
        when (sortOption) {
            SortOption.DATE_ADDED -> list.sortedByDescending { it.dateAdded }
            SortOption.TITLE -> list.sortedBy { it.title.lowercase() }
            SortOption.DURATION -> list.sortedByDescending { it.duration }
            SortOption.SIZE -> list.sortedByDescending { it.size }
        }

    private fun scanAudio(): List<MediaItemModel> {
        val list = mutableListOf<MediaItemModel>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 3000"

        runCatching {
            context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val artworkBaseUri = Uri.parse("content://media/external/audio/albumart")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown"
                    val artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Unknown Artist"
                    val album = cursor.getString(albumCol)?.takeIf { it != "<unknown>" } ?: "Unknown Album"
                    val albumId = cursor.getLong(albumIdCol)
                    val duration = cursor.getLong(durCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol)

                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val albumArtUri = ContentUris.withAppendedId(artworkBaseUri, albumId)
                    val folderName = File(path).parentFile?.name ?: "Music"

                    list.add(
                        MediaItemModel(
                            id = id, title = title, artist = artist, album = album,
                            duration = duration, uri = contentUri, albumArtUri = albumArtUri,
                            path = path, folderName = folderName, size = size,
                            dateAdded = dateAdded, isVideo = false
                        )
                    )
                }
            }
        }
        return list
    }

    private fun scanVideo(): List<MediaItemModel> {
        val list = mutableListOf<MediaItemModel>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.RESOLUTION
        )

        runCatching {
            context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val resCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RESOLUTION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Video"
                    val duration = cursor.getLong(durCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol)
                    val resolution = cursor.getString(resCol) ?: "HD"

                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val folderName = File(path).parentFile?.name ?: "Videos"

                    list.add(
                        MediaItemModel(
                            id = id, title = title, artist = folderName, album = "Videos",
                            duration = duration, uri = contentUri, albumArtUri = contentUri,
                            path = path, folderName = folderName, size = size,
                            dateAdded = dateAdded, isVideo = true, resolution = resolution
                        )
                    )
                }
            }
        }
        return list
    }

    fun groupByFolders(audios: List<MediaItemModel>, videos: List<MediaItemModel>): List<FolderModel> {
        val all = audios + videos
        return all.groupBy { File(it.path).parent ?: it.folderName }.map { (path, items) ->
            FolderModel(
                name = File(path).name.ifEmpty { path },
                path = path,
                audioCount = items.count { !it.isVideo },
                videoCount = items.count { it.isVideo },
                items = items
            )
        }.sortedByDescending { it.items.size }
    }

    fun groupByAlbums(audios: List<MediaItemModel>): List<AlbumModel> =
        audios.groupBy { it.album }.map { (albumName, songs) ->
            AlbumModel(
                name = albumName,
                artist = songs.firstOrNull()?.artist ?: "",
                albumArtUri = songs.firstOrNull()?.albumArtUri,
                songs = songs
            )
        }.sortedBy { it.name.lowercase() }

    fun groupByArtists(audios: List<MediaItemModel>): List<ArtistModel> =
        audios.groupBy { it.artist }.map { (artistName, songs) ->
            ArtistModel(name = artistName, songs = songs)
        }.sortedBy { it.name.lowercase() }

    fun loadLyricsForMedia(media: MediaItemModel): List<LyricLine> {
        if (media.path.isEmpty()) return emptyList()
        val mediaFile = File(media.path)
        val lrcFile = File(mediaFile.parentFile, "${mediaFile.nameWithoutExtension}.lrc")
        if (!lrcFile.exists()) return emptyList()

        val lines = mutableListOf<LyricLine>()
        val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")
        runCatching {
            lrcFile.forEachLine { line ->
                val match = regex.find(line.trim())
                if (match != null) {
                    val min = match.groupValues[1].toLong()
                    val sec = match.groupValues[2].toLong()
                    val msStr = match.groupValues[3]
                    val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
                    val text = match.groupValues[4].trim()
                    if (text.isNotEmpty()) {
                        lines.add(LyricLine(timeMs = min * 60000 + sec * 1000 + ms, text = text))
                    }
                }
            }
        }
        return lines.sortedBy { it.timeMs }
    }
}

// مشغل M4LIK الموحد لدعم الخلفية والإشعارات والـ Equalizer
object PlayerHolder {
    private var exoPlayer: ExoPlayer? = null

    fun getOrCreate(context: Context): ExoPlayer {
        return exoPlayer ?: synchronized(this) {
            exoPlayer ?: ExoPlayer.Builder(context.applicationContext)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                .setHandleAudioBecomingNoisy(true)
                .build()
                .also { exoPlayer = it }
        }
    }
}

class MediaPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlayerHolder.getOrCreate(this)
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesManager(application)
    private val repo = MediaRepository(application)
    val exoPlayer: ExoPlayer = PlayerHolder.getOrCreate(application)

    private val _themeMode = MutableStateFlow(prefs.getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _language = MutableStateFlow(prefs.getLanguage())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _sortOption = MutableStateFlow(prefs.getSortOption())
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _audioList = MutableStateFlow<List<MediaItemModel>>(emptyList())
    val audioList: StateFlow<List<MediaItemModel>> = _audioList.asStateFlow()

    private val _videoList = MutableStateFlow<List<MediaItemModel>>(emptyList())
    val videoList: StateFlow<List<MediaItemModel>> = _videoList.asStateFlow()

    private val _folders = MutableStateFlow<List<FolderModel>>(emptyList())
    val folders: StateFlow<List<FolderModel>> = _folders.asStateFlow()

    private val _albums = MutableStateFlow<List<AlbumModel>>(emptyList())
    val albums: StateFlow<List<AlbumModel>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<ArtistModel>>(emptyList())
    val artists: StateFlow<List<ArtistModel>> = _artists.asStateFlow()

    private val _favorites = MutableStateFlow(prefs.getFavorites())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _playlists = MutableStateFlow(prefs.getPlaylists())
    val playlists: StateFlow<List<PlaylistModel>> = _playlists.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<MediaItemModel>>(emptyList())
    private val _currentMedia = MutableStateFlow<MediaItemModel?>(null)
    val currentMedia: StateFlow<MediaItemModel?> = _currentMedia.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatModeState.OFF)
    val repeatMode: StateFlow<RepeatModeState> = _repeatMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isFullPlayerOpen = MutableStateFlow(false)
    val isFullPlayerOpen: StateFlow<Boolean> = _isFullPlayerOpen.asStateFlow()

    private val _videoAsAudioMode = MutableStateFlow(false)
    val videoAsAudioMode: StateFlow<Boolean> = _videoAsAudioMode.asStateFlow()

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics.asStateFlow()

    // Equalizer & Bass Boost
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private val _eqEnabled = MutableStateFlow(false)
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()
    private val _eqBands = MutableStateFlow<List<Pair<Int, Short>>>(emptyList())
    val eqBands: StateFlow<List<Pair<Int, Short>>> = _eqBands.asStateFlow()
    private val _bassStrength = MutableStateFlow<Short>(0)
    val bassStrength: StateFlow<Short> = _bassStrength.asStateFlow()

    // Sleep Timer
    private var sleepTimerJob: Job? = null
    private val _sleepTimerRemainingMin = MutableStateFlow(0)
    val sleepTimerRemainingMin: StateFlow<Int> = _sleepTimerRemainingMin.asStateFlow()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = exoPlayer.currentMediaItemIndex
                val item = _currentQueue.value.getOrNull(index)
                _currentMedia.value = item
                _duration.value = item?.duration ?: exoPlayer.duration.coerceAtLeast(0L)
                if (item != null) {
                    _lyrics.value = repo.loadLyricsForMedia(item)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = exoPlayer.duration.coerceAtLeast(0L)
                    setupAudioEffects(exoPlayer.audioSessionId)
                }
            }
        })

        viewModelScope.launch {
            while (true) {
                if (exoPlayer.isPlaying) {
                    _currentPosition.value = exoPlayer.currentPosition.coerceAtLeast(0L)
                }
                delay(500L)
            }
        }
    }

    private fun setupAudioEffects(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || equalizer != null) return
        runCatching {
            equalizer = Equalizer(0, sessionId).apply { enabled = _eqEnabled.value }
            bassBoost = BassBoost(0, sessionId).apply { enabled = _eqEnabled.value }
            refreshEqState()
        }
    }

    private fun refreshEqState() {
        val eq = equalizer ?: return
        val bands = mutableListOf<Pair<Int, Short>>()
        for (i in 0 until eq.numberOfBands) {
            val b = i.toShort()
            val freqHz = eq.getCenterFreq(b) / 1000
            bands.add(Pair(freqHz, eq.getBandLevel(b)))
        }
        _eqBands.value = bands
    }

    fun toggleEqualizer(enabled: Boolean) {
        _eqEnabled.value = enabled
        runCatching {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
        }
    }

    fun setBandLevel(bandIndex: Int, level: Short) {
        runCatching {
            equalizer?.setBandLevel(bandIndex.toShort(), level)
            refreshEqState()
        }
    }

    fun setBassStrength(strength: Short) {
        _bassStrength.value = strength
        runCatching { bassBoost?.setStrength(strength) }
    }

    fun loadMedia() {
        viewModelScope.launch {
            val (audios, videos) = repo.scanAllMedia(_sortOption.value)
            _audioList.value = audios
            _videoList.value = videos
            _folders.value = repo.groupByFolders(audios, videos)
            _albums.value = repo.groupByAlbums(audios)
            _artists.value = repo.groupByArtists(audios)
        }
    }

    fun setTheme(mode: ThemeMode) {
        prefs.setThemeMode(mode)
        _themeMode.value = mode
    }

    fun setLanguage(lang: AppLanguage) {
        prefs.setLanguage(lang)
        _language.value = lang
    }

    fun setSortOption(option: SortOption) {
        prefs.setSortOption(option)
        _sortOption.value = option
        _audioList.value = repo.sortItems(_audioList.value, option)
        _videoList.value = repo.sortItems(_videoList.value, option)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun playMediaList(list: List<MediaItemModel>, startIndex: Int, openFull: Boolean = false) {
        if (list.isEmpty() || startIndex !in list.indices) return
        _currentQueue.value = list
        val selected = list[startIndex]
        _currentMedia.value = selected
        _videoAsAudioMode.value = false
        _lyrics.value = repo.loadLyricsForMedia(selected)
        if (openFull || selected.isVideo) _isFullPlayerOpen.value = true

        val mediaItems = list.map { item ->
            MediaItem.Builder()
                .setMediaId(item.id.toString())
                .setUri(item.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setAlbumTitle(item.album)
                        .setArtworkUri(item.albumArtUri)
                        .build()
                )
                .build()
        }

        exoPlayer.setMediaItems(mediaItems, startIndex, 0L)
        exoPlayer.prepare()
        exoPlayer.play()

        runCatching {
            val app = getApplication<Application>()
            app.startService(Intent(app, MediaPlaybackService::class.java))
        }
    }

    fun shuffleAllAudio() {
        val list = _audioList.value
        if (list.isEmpty()) return
        _isShuffle.value = true
        exoPlayer.shuffleModeEnabled = true
        playMediaList(list, (list.indices).random(), openFull = true)
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun nextTrack() {
        if (exoPlayer.hasNextMediaItem()) exoPlayer.seekToNextMediaItem()
    }

    fun prevTrack() {
        if (exoPlayer.currentPosition > 4000L) {
            exoPlayer.seekTo(0L)
        } else if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
        }
    }

    fun seekTo(posMs: Long) {
        exoPlayer.seekTo(posMs)
        _currentPosition.value = posMs
    }

    fun toggleShuffle() {
        val next = !_isShuffle.value
        _isShuffle.value = next
        exoPlayer.shuffleModeEnabled = next
    }

    fun cycleRepeatMode() {
        val next = when (_repeatMode.value) {
            RepeatModeState.OFF -> RepeatModeState.REPEAT_ALL
            RepeatModeState.REPEAT_ALL -> RepeatModeState.REPEAT_ONE
            RepeatModeState.REPEAT_ONE -> RepeatModeState.OFF
        }
        _repeatMode.value = next
        exoPlayer.repeatMode = when (next) {
            RepeatModeState.OFF -> Player.REPEAT_MODE_OFF
            RepeatModeState.REPEAT_ALL -> Player.REPEAT_MODE_ALL
            RepeatModeState.REPEAT_ONE -> Player.REPEAT_MODE_ONE
        }
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    fun setFullPlayerOpen(open: Boolean) {
        _isFullPlayerOpen.value = open
    }

    fun setVideoAsAudioMode(asAudio: Boolean) {
        _videoAsAudioMode.value = asAudio
    }

    fun toggleFavorite(id: Long) {
        _favorites.value = prefs.toggleFavorite(id)
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        val updated = _playlists.value + PlaylistModel(UUID.randomUUID().toString(), name.trim(), emptyList())
        prefs.savePlaylists(updated)
        _playlists.value = updated
    }

    fun addMediaToPlaylist(playlistId: String, mediaId: Long) {
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId && !pl.mediaIds.contains(mediaId)) {
                pl.copy(mediaIds = pl.mediaIds + mediaId)
            } else pl
        }
        prefs.savePlaylists(updated)
        _playlists.value = updated
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerRemainingMin.value = minutes
        if (minutes <= 0) return
        sleepTimerJob = viewModelScope.launch {
            var rem = minutes
            while (rem > 0) {
                delay(60_000L)
                rem--
                _sleepTimerRemainingMin.value = rem
            }
            exoPlayer.pause()
        }
    }
}
