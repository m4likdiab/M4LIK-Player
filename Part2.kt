@file:OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.malik.player

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val language by viewModel.language.collectAsState()

            M4likPlayerTheme(themeMode = themeMode, language = language) {
                M4likPlayerApp(
                    viewModel = viewModel,
                    onEnterPip = { enterPip() }
                )
            }
        }
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }
}

@Composable
fun M4likPlayerApp(viewModel: PlayerViewModel, onEnterPip: () -> Unit) {
    val context = LocalContext.current
    val strings = LocalStrings.current

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result.values.all { it }
        if (hasPermission) viewModel.loadMedia()
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.loadMedia() else permLauncher.launch(requiredPermissions)
    }

    val isFullPlayerOpen by viewModel.isFullPlayerOpen.collectAsState()
    val currentMedia by viewModel.currentMedia.collectAsState()
    var selectedBottomTab by remember { mutableStateOf(0) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showEqDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var mediaForPlaylist by remember { mutableStateOf<MediaItemModel?>(null) }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val language by viewModel.language.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    if (!hasPermission) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = LarkOrange, modifier = Modifier.size(80.dp))
                Spacer(Modifier.height(16.dp))
                Text(strings.appName, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(strings.grantPermissionMsg, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { permLauncher.launch(requiredPermissions) },
                    colors = ButtonDefaults.buttonColors(containerColor = LarkOrange)
                ) {
                    Text(strings.grantPermissionBtn, color = Color.White)
                }
            }
        }
        return
    }

    if (isFullPlayerOpen && currentMedia != null) {
        BackHandler { viewModel.setFullPlayerOpen(false) }
        FullPlayerScreen(
            viewModel = viewModel,
            onClose = { viewModel.setFullPlayerOpen(false) },
            onEnterPip = onEnterPip,
            onOpenEq = { showEqDialog = true },
            onOpenTimer = { showTimerDialog = true },
            onOpenSpeed = { showSpeedDialog = true },
            onAddToPlaylist = { mediaForPlaylist = it }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    title = {
                        if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text(strings.searchHint) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(LarkOrange),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(strings.appName, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) viewModel.setSearchQuery("")
                        }) {
                            Icon(if (isSearchActive) Icons.Default.Close else Icons.Default.Search, null)
                        }
                        IconButton(onClick = {
                            viewModel.setLanguage(if (language == AppLanguage.AR) AppLanguage.EN else AppLanguage.AR)
                        }) {
                            Icon(Icons.Default.Language, contentDescription = "Language", tint = LarkOrange)
                        }
                        IconButton(onClick = {
                            val nextTheme = when (themeMode) {
                                ThemeMode.DARK -> ThemeMode.LIGHT
                                ThemeMode.LIGHT -> ThemeMode.SYSTEM
                                ThemeMode.SYSTEM -> ThemeMode.DARK
                            }
                            viewModel.setTheme(nextTheme)
                        }) {
                            Icon(
                                if (themeMode == ThemeMode.LIGHT) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Theme"
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(strings.sortByDate) },
                                    onClick = { viewModel.setSortOption(SortOption.DATE_ADDED); showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.sortByTitle) },
                                    onClick = { viewModel.setSortOption(SortOption.TITLE); showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.sortByDuration) },
                                    onClick = { viewModel.setSortOption(SortOption.DURATION); showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.sortBySize) },
                                    onClick = { viewModel.setSortOption(SortOption.SIZE); showSortMenu = false }
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                Column {
                    if (currentMedia != null) {
                        MiniPlayerBar(
                            viewModel = viewModel,
                            onClick = { viewModel.setFullPlayerOpen(true) }
                        )
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        NavigationBarItem(
                            selected = selectedBottomTab == 0,
                            onClick = { selectedBottomTab = 0 },
                            icon = { Icon(Icons.Default.MusicNote, null) },
                            label = { Text(strings.musicTab) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = LarkOrange, selectedTextColor = LarkOrange)
                        )
                        NavigationBarItem(
                            selected = selectedBottomTab == 1,
                            onClick = { selectedBottomTab = 1 },
                            icon = { Icon(Icons.Default.Movie, null) },
                            label = { Text(strings.videoTab) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = LarkOrange, selectedTextColor = LarkOrange)
                        )
                        NavigationBarItem(
                            selected = selectedBottomTab == 2,
                            onClick = { selectedBottomTab = 2 },
                            icon = { Icon(Icons.Default.Folder, null) },
                            label = { Text(strings.foldersTab) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = LarkOrange, selectedTextColor = LarkOrange)
                        )
                        NavigationBarItem(
                            selected = selectedBottomTab == 3,
                            onClick = { selectedBottomTab = 3 },
                            icon = { Icon(Icons.Default.Settings, null) },
                            label = { Text(strings.meTab) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = LarkOrange, selectedTextColor = LarkOrange)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (selectedBottomTab) {
                    0 -> MusicMainTab(viewModel = viewModel, onAddToPlaylist = { mediaForPlaylist = it })
                    1 -> VideoMainTab(viewModel = viewModel)
                    2 -> FoldersMainTab(viewModel = viewModel)
                    3 -> SettingsMainTab(
                        viewModel = viewModel,
                        onOpenEq = { showEqDialog = true },
                        onOpenTimer = { showTimerDialog = true },
                        onOpenSpeed = { showSpeedDialog = true }
                    )
                }
            }
        }
    }

    if (showEqDialog) EqualizerDialog(viewModel = viewModel, onDismiss = { showEqDialog = false })
    if (showTimerDialog) SleepTimerDialog(viewModel = viewModel, onDismiss = { showTimerDialog = false })
    if (showSpeedDialog) SpeedDialog(viewModel = viewModel, onDismiss = { showSpeedDialog = false })
    if (mediaForPlaylist != null) {
        AddToPlaylistDialog(
            viewModel = viewModel,
            media = mediaForPlaylist!!,
            onDismiss = { mediaForPlaylist = null }
        )
    }
}

@Composable
fun MusicMainTab(viewModel: PlayerViewModel, onAddToPlaylist: (MediaItemModel) -> Unit) {
    val strings = LocalStrings.current
    var subTab by remember { mutableStateOf(0) }
    val subTabTitles = listOf(
        strings.songsSubTab,
        strings.playlistsSubTab,
        strings.favorites,
        strings.albumsSubTab,
        strings.artistsSubTab
    )

    val audios by viewModel.audioList.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()

    var selectedGroupTitle by remember { mutableStateOf<String?>(null) }
    var selectedGroupItems by remember { mutableStateOf<List<MediaItemModel>>(emptyList()) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }

    val filteredAudios = remember(audios, query) {
        if (query.isBlank()) audios
        else audios.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = subTab,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = LarkOrange
        ) {
            subTabTitles.forEachIndexed { idx, title ->
                Tab(
                    selected = subTab == idx,
                    onClick = { subTab = idx; selectedGroupTitle = null },
                    text = {
                        Text(
                            title,
                            fontWeight = if (subTab == idx) FontWeight.Bold else FontWeight.Normal,
                            color = if (subTab == idx) LarkOrange else MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            }
        }

        if (selectedGroupTitle != null) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedGroupTitle = null }) {
                        Icon(Icons.Default.ArrowBack, null, tint = LarkOrange)
                    }
                    Text(selectedGroupTitle!!, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                MediaListSection(
                    items = selectedGroupItems,
                    viewModel = viewModel,
                    onAddToPlaylist = onAddToPlaylist
                )
            }
        } else {
            when (subTab) {
                0 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (filteredAudios.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.shuffleAllAudio() }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(LarkOrange),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Shuffle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text("${strings.shuffleAll} (${filteredAudios.size})", fontWeight = FontWeight.Bold)
                            }
                        }
                        MediaListSection(
                            items = filteredAudios,
                            viewModel = viewModel,
                            onAddToPlaylist = onAddToPlaylist
                        )
                    }
                }
                1 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Button(
                            onClick = { showNewPlaylistDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = LarkOrange),
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Icon(Icons.Default.Add, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.createPlaylist, color = Color.White)
                        }
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(playlists) { pl ->
                                val plSongs = audios.filter { pl.mediaIds.contains(it.id) }
                                ListItem(
                                    headlineContent = { Text(pl.name, fontWeight = FontWeight.Bold) },
                                    supportingContent = { Text("${plSongs.size} ${strings.songsCount}") },
                                    leadingContent = {
                                        Icon(Icons.Default.PlaylistPlay, null, tint = LarkOrange, modifier = Modifier.size(36.dp))
                                    },
                                    modifier = Modifier.clickable {
                                        selectedGroupTitle = pl.name
                                        selectedGroupItems = plSongs
                                    }
                                )
                            }
                        }
                    }
                }
                2 -> {
                    val favSongs = audios.filter { favorites.contains(it.id) }
                    MediaListSection(items = favSongs, viewModel = viewModel, onAddToPlaylist = onAddToPlaylist)
                }
                3 -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(albums) { album ->
                            ListItem(
                                headlineContent = { Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text("${album.artist} • ${album.songs.size} ${strings.songsCount}") },
                                leadingContent = {
                                    Icon(Icons.Default.Album, null, tint = LarkOrange, modifier = Modifier.size(40.dp))
                                },
                                modifier = Modifier.clickable {
                                    selectedGroupTitle = album.name
                                    selectedGroupItems = album.songs
                                }
                            )
                        }
                    }
                }
                4 -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(artists) { artist ->
                            ListItem(
                                headlineContent = { Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text("${artist.songs.size} ${strings.songsCount}") },
                                leadingContent = {
                                    Icon(Icons.Default.Person, null, tint = LarkOrange, modifier = Modifier.size(40.dp))
                                },
                                modifier = Modifier.clickable {
                                    selectedGroupTitle = artist.name
                                    selectedGroupItems = artist.songs
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewPlaylistDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewPlaylistDialog = false },
            title = { Text(strings.createPlaylist) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(strings.playlistNameHint) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createPlaylist(name)
                    showNewPlaylistDialog = false
                }) { Text(strings.add, color = LarkOrange) }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylistDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
fun VideoMainTab(viewModel: PlayerViewModel) {
    val strings = LocalStrings.current
    val videos by viewModel.videoList.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current

    val filtered = remember(videos, query) {
        if (query.isBlank()) videos else videos.filter { it.title.contains(query, true) }
    }

    if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(strings.noMediaFound)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
        itemsIndexed(filtered) { index, video ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { viewModel.playMediaList(filtered, index, openFull = true) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 110.dp, height = 68.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(video.uri)
                                .decoderFactory(VideoFrameDecoder.Factory())
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(formatDuration(video.duration), color = Color.White, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(video.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${video.resolution} • ${formatFileSize(video.size)} • ${video.folderName}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FoldersMainTab(viewModel: PlayerViewModel) {
    val strings = LocalStrings.current
    val folders by viewModel.folders.collectAsState()
    var selectedFolder by remember { mutableStateOf<FolderModel?>(null) }

    if (selectedFolder != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedFolder = null }) {
                    Icon(Icons.Default.ArrowBack, null, tint = LarkOrange)
                }
                Column {
                    Text(selectedFolder!!.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(selectedFolder!!.path, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            MediaListSection(
                items = selectedFolder!!.items,
                viewModel = viewModel,
                onAddToPlaylist = {}
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(folders) { folder ->
                ListItem(
                    headlineContent = { Text(folder.name, fontWeight = FontWeight.Bold) },
                    supportingContent = {
                        Text("${folder.audioCount} ${strings.songsCount} • ${folder.videoCount} ${strings.videosCount}")
                    },
                    leadingContent = {
                        Icon(Icons.Default.Folder, null, tint = LarkOrange, modifier = Modifier.size(40.dp))
                    },
                    modifier = Modifier.clickable { selectedFolder = folder }
                )
            }
        }
    }
}

@Composable
fun SettingsMainTab(
    viewModel: PlayerViewModel,
    onOpenEq: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenSpeed: () -> Unit
) {
    val strings = LocalStrings.current
    val themeMode by viewModel.themeMode.collectAsState()
    val language by viewModel.language.collectAsState()
    val sleepRemaining by viewModel.sleepTimerRemainingMin.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(strings.themeTitle, fontWeight = FontWeight.Bold, color = LarkOrange)
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = themeMode == ThemeMode.SYSTEM,
                            onClick = { viewModel.setTheme(ThemeMode.SYSTEM) },
                            label = { Text(strings.themeSystem) }
                        )
                        FilterChip(
                            selected = themeMode == ThemeMode.DARK,
                            onClick = { viewModel.setTheme(ThemeMode.DARK) },
                            label = { Text(strings.themeDark) }
                        )
                        FilterChip(
                            selected = themeMode == ThemeMode.LIGHT,
                            onClick = { viewModel.setTheme(ThemeMode.LIGHT) },
                            label = { Text(strings.themeLight) }
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(strings.languageTitle, fontWeight = FontWeight.Bold, color = LarkOrange)
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(
                            selected = language == AppLanguage.AR,
                            onClick = { viewModel.setLanguage(AppLanguage.AR) },
                            label = { Text("العربية") }
                        )
                        FilterChip(
                            selected = language == AppLanguage.EN,
                            onClick = { viewModel.setLanguage(AppLanguage.EN) },
                            label = { Text("English") }
                        )
                    }
                }
            }
        }

        item {
            ListItem(
                headlineContent = { Text(strings.equalizer, fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.Default.Equalizer, null, tint = LarkOrange) },
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onOpenEq() }
            )
            ListItem(
                headlineContent = { Text(strings.sleepTimer, fontWeight = FontWeight.SemiBold) },
                supportingContent = {
                    if (sleepRemaining > 0) Text("$sleepRemaining ${strings.minutes}", color = LarkOrange)
                },
                leadingContent = { Icon(Icons.Default.Timer, null, tint = LarkOrange) },
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onOpenTimer() }
            )
            ListItem(
                headlineContent = { Text(strings.playbackSpeed, fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("${speed}x", color = LarkOrange) },
                leadingContent = { Icon(Icons.Default.Speed, null, tint = LarkOrange) },
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onOpenSpeed() }
            )
        }
    }
}

@Composable
fun MediaListSection(
    items: List<MediaItemModel>,
    viewModel: PlayerViewModel,
    onAddToPlaylist: (MediaItemModel) -> Unit
) {
    val strings = LocalStrings.current
    val currentMedia by viewModel.currentMedia.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(strings.noMediaFound)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(items) { idx, item ->
            val isSelected = currentMedia?.id == item.id
            val isFav = favorites.contains(item.id)
            ListItem(
                headlineContent = {
                    Text(
                        item.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) LarkOrange else MaterialTheme.colorScheme.onSurface
                    )
                },
                supportingContent = {
                    Text(
                        "${item.artist} • ${formatDuration(item.duration)}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (item.isVideo) Icons.Default.Movie else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = LarkOrange
                        )
                        AsyncImage(
                            model = item.albumArtUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = { viewModel.toggleFavorite(item.id) }) {
                            Icon(
                                if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isFav) LarkOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        IconButton(onClick = { onAddToPlaylist(item) }) {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                        }
                    }
                },
                modifier = Modifier.clickable { viewModel.playMediaList(items, idx) }
            )
        }
    }
}

@Composable
fun MiniPlayerBar(viewModel: PlayerViewModel, onClick: () -> Unit) {
    val media by viewModel.currentMedia.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val item = media ?: return

    Surface(
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(LarkOrange),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, null, tint = Color.White)
                AsyncImage(
                    model = item.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.artist, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { viewModel.togglePlayPause() }) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = LarkOrange,
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(onClick = { viewModel.nextTrack() }) {
                Icon(Icons.Default.SkipNext, contentDescription = null)
            }
        }
    }
}

@Composable
fun FullPlayerScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    onEnterPip: () -> Unit,
    onOpenEq: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenSpeed: () -> Unit,
    onAddToPlaylist: (MediaItemModel) -> Unit
) {
    val strings = LocalStrings.current
    val media by viewModel.currentMedia.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val videoAsAudio by viewModel.videoAsAudioMode.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()

    var showLyrics by remember { mutableStateOf(false) }
    val item = media ?: return
    val isFav = favorites.contains(item.id)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(LarkOrange.copy(alpha = 0.22f), MaterialTheme.colorScheme.background)
                    )
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(32.dp))
                }
                Text(
                    strings.appName,
                    fontWeight = FontWeight.Bold,
                    color = LarkOrange
                )
                Row {
                    if (item.isVideo) {
                        IconButton(onClick = { viewModel.setVideoAsAudioMode(!videoAsAudio) }) {
                            Icon(
                                Icons.Default.Headphones,
                                contentDescription = strings.playInBackground,
                                tint = if (videoAsAudio) LarkOrange else MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(onClick = onEnterPip) {
                            Icon(Icons.Default.PictureInPictureAlt, contentDescription = strings.floatingWindow)
                        }
                    } else {
                        IconButton(onClick = { showLyrics = !showLyrics }) {
                            Icon(
                                Icons.Default.Subtitles,
                                contentDescription = strings.lyrics,
                                tint = if (showLyrics) LarkOrange else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (item.isVideo && !videoAsAudio) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = viewModel.exoPlayer
                                useController = false
                            }
                        },
                        update = { it.player = viewModel.exoPlayer },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (showLyrics) {
                    if (lyrics.isEmpty()) {
                        Text(
                            strings.noLyricsFound,
                            modifier = Modifier.padding(24.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(lyrics) { line ->
                                val active = position >= line.timeMs
                                Text(
                                    text = line.text,
                                    fontSize = if (active) 18.sp else 15.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    color = if (active) LarkOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = LarkOrange,
                        modifier = Modifier.size(110.dp)
                    )
                    AsyncImage(
                        model = item.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                item.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                item.artist,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                maxLines = 1
            )

            Spacer(Modifier.height(16.dp))

            Slider(
                value = position.toFloat(),
                onValueChange = { viewModel.seekTo(it.toLong()) },
                valueRange = 0f..(duration.coerceAtLeast(1L).toFloat()),
                colors = SliderDefaults.colors(thumbColor = LarkOrange, activeTrackColor = LarkOrange)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(position), fontSize = 12.sp)
                Text(formatDuration(duration), fontSize = 12.sp)
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = null,
                        tint = if (isShuffle) LarkOrange else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = { viewModel.prevTrack() }) {
                    Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(38.dp))
                }
                FloatingActionButton(
                    onClick = { viewModel.togglePlayPause() },
                    containerColor = LarkOrange,
                    shape = CircleShape,
                    modifier = Modifier.size(68.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { viewModel.nextTrack() }) {
                    Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = { viewModel.cycleRepeatMode() }) {
                    Icon(
                        if (repeatMode == RepeatModeState.REPEAT_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = null,
                        tint = if (repeatMode != RepeatModeState.OFF) LarkOrange else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { viewModel.toggleFavorite(item.id) }) {
                    Icon(
                        if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFav) LarkOrange else MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = onOpenEq) {
                    Icon(Icons.Default.Equalizer, contentDescription = strings.equalizer)
                }
                IconButton(onClick = onOpenTimer) {
                    Icon(Icons.Default.Timer, contentDescription = strings.sleepTimer)
                }
                IconButton(onClick = onOpenSpeed) {
                    Icon(Icons.Default.Speed, contentDescription = strings.playbackSpeed)
                }
                IconButton(onClick = { onAddToPlaylist(item) }) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = strings.addToPlaylist)
                }
            }
        }
    }
}

@Composable
fun EqualizerDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    val enabled by viewModel.eqEnabled.collectAsState()
    val bands by viewModel.eqBands.collectAsState()
    val bass by viewModel.bassStrength.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.equalizer)
                Switch(checked = enabled, onCheckedChange = { viewModel.toggleEqualizer(it) })
            }
        },
        text = {
            Column {
                bands.forEachIndexed { idx, (freqHz, level) ->
                    Text("${freqHz} Hz", fontSize = 12.sp)
                    Slider(
                        value = level.toFloat(),
                        onValueChange = { viewModel.setBandLevel(idx, it.toInt().toShort()) },
                        valueRange = -1500f..1500f,
                        enabled = enabled,
                        colors = SliderDefaults.colors(thumbColor = LarkOrange, activeTrackColor = LarkOrange)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(strings.bassBoost, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Slider(
                    value = bass.toFloat(),
                    onValueChange = { viewModel.setBassStrength(it.toInt().toShort()) },
                    valueRange = 0f..1000f,
                    enabled = enabled,
                    colors = SliderDefaults.colors(thumbColor = LarkOrange, activeTrackColor = LarkOrange)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK", color = LarkOrange) }
        }
    )
}

@Composable
fun SleepTimerDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    val options = listOf(0, 15, 30, 45, 60, 90)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.sleepTimer) },
        text = {
            Column {
                options.forEach { min ->
                    TextButton(
                        onClick = { viewModel.setSleepTimer(min); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (min == 0) strings.timerOff else "$min ${strings.minutes}",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun SpeedDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.playbackSpeed) },
        text = {
            Column {
                speeds.forEach { sp ->
                    TextButton(
                        onClick = { viewModel.setSpeed(sp); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${sp}x", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun AddToPlaylistDialog(
    viewModel: PlayerViewModel,
    media: MediaItemModel,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val playlists by viewModel.playlists.collectAsState()
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addToPlaylist) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text(strings.playlistNameHint) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName)
                            newPlaylistName = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, null, tint = LarkOrange)
                    }
                }
                Spacer(Modifier.height(8.dp))
                playlists.forEach { pl ->
                    TextButton(
                        onClick = {
                            viewModel.addMediaToPlaylist(pl.id, media.id)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(pl.name, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}
