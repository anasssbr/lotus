package com.dn0ne.player.app.presentation.components.playlist

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dn0ne.player.R
import com.dn0ne.player.app.domain.track.Playlist
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.presentation.components.CoverArt
import com.dn0ne.player.ui.theme.LotusTeal
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class QuickStart {
    Empty,
    CurrentQueue,
    MostPlayed,
    RecentlyAdded,
    RandomMix
}

private val QuickStart.labelRes: Int
    get() = when (this) {
        QuickStart.Empty -> R.string.quick_start_empty
        QuickStart.CurrentQueue -> R.string.quick_start_current_queue
        QuickStart.MostPlayed -> R.string.quick_start_most_played
        QuickStart.RecentlyAdded -> R.string.quick_start_recently_added
        QuickStart.RandomMix -> R.string.quick_start_random_mix
    }

/**
 * A palette of four vivid colors derived from a random base hue.
 * Never persisted: a fresh one is generated every time the screen opens.
 */
private data class GradientPalette(
    val baseHue: Float,
    val colors: List<Color>
) {
    companion object {
        fun random(
            previousBaseHue: Float? = null,
            random: Random = Random.Default
        ): GradientPalette {
            var baseHue = random.nextFloat() * 360f

            if (previousBaseHue != null) {
                var attempts = 0
                while (
                    angularDistance(baseHue, previousBaseHue) < 40f &&
                    attempts < 40
                ) {
                    baseHue = random.nextFloat() * 360f
                    attempts++
                }

                if (angularDistance(baseHue, previousBaseHue) < 40f) {
                    baseHue = (previousBaseHue + 40f + random.nextFloat() * 280f) % 360f
                }
            }

            val thirdOffset = if (random.nextBoolean()) 150f else 180f
            val offsets = floatArrayOf(0f, 35f, thirdOffset, 260f)

            val colors = offsets.map { offset ->
                val hue = (baseHue + offset) % 360f
                val saturation = 0.75f + random.nextFloat() * 0.20f
                var lightness = 0.45f + random.nextFloat() * 0.15f

                // Low-lightness green-yellows turn muddy; lift them a little.
                if (hue in 55f..145f && lightness < 0.54f) {
                    lightness = 0.54f + random.nextFloat() * 0.05f
                }

                Color.hsl(
                    hue = hue,
                    saturation = saturation,
                    lightness = lightness
                )
            }

            return GradientPalette(baseHue = baseHue, colors = colors)
        }

        private fun angularDistance(first: Float, second: Float): Float {
            val delta = kotlin.math.abs(first - second) % 360f
            return kotlin.math.min(delta, 360f - delta)
        }
    }
}

/**
 * Keeps the current screen's palette alive across configuration changes only.
 * The random seed is generated on every open; this holder never survives a close.
 */
private object NewPlaylistPaletteMemory {
    var lastBaseHue: Float? = null
    var activePalette: GradientPalette? = null
}

@Composable
private fun rememberFreshPlaylistPalette(): GradientPalette {
    val context = LocalContext.current
    val activity = remember(context) {
        context.findActivity()
    }
    val palette = remember {
        NewPlaylistPaletteMemory.activePalette ?: GradientPalette.random(
            previousBaseHue = NewPlaylistPaletteMemory.lastBaseHue
        ).also {
            NewPlaylistPaletteMemory.lastBaseHue = it.baseHue
            NewPlaylistPaletteMemory.activePalette = it
        }
    }

    DisposableEffect(activity) {
        onDispose {
            if (activity?.isChangingConfigurations != true) {
                NewPlaylistPaletteMemory.activePalette = null
            }
        }
    }

    return palette
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private data class Blob(
    val x: Float,
    val y: Float,
    val amplitudeX: Float,
    val amplitudeY: Float,
    val radius: Float,
    val phase: Float
)

private val Blobs = listOf(
    Blob(x = 0.18f, y = 0.16f, amplitudeX = 0.16f, amplitudeY = 0.10f, radius = 0.42f, phase = 0f),
    Blob(x = 0.84f, y = 0.20f, amplitudeX = 0.14f, amplitudeY = 0.12f, radius = 0.38f, phase = 1.7f),
    Blob(x = 0.14f, y = 0.82f, amplitudeX = 0.15f, amplitudeY = 0.09f, radius = 0.45f, phase = 3.1f),
    Blob(x = 0.86f, y = 0.86f, amplitudeX = 0.13f, amplitudeY = 0.11f, radius = 0.36f, phase = 4.6f)
)

@Composable
fun NewPlaylistScreen(
    allTracks: List<Track>,
    currentQueue: List<Track>,
    playlists: List<Playlist>,
    onLoadMostPlayed: suspend () -> List<Track>,
    onCreate: (String, List<Track>, Uri?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var isResumed by remember {
        mutableStateOf(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isResumed = true
                Lifecycle.Event.ON_PAUSE -> isResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val reduceMotion = remember(context) {
        val resolver = context.contentResolver
        Settings.Global.getFloat(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f || Settings.Global.getFloat(
            resolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f
        ) == 0f
    }
    val animate = isResumed && !reduceMotion

    val palette = rememberFreshPlaylistPalette()
    var title by rememberSaveable {
        mutableStateOf("")
    }
    val selectedTracks = remember {
        mutableStateListOf<Track>()
    }
    var customCover by remember {
        mutableStateOf<Uri?>(null)
    }
    var selectedQuickStart by remember {
        mutableStateOf<QuickStart?>(QuickStart.Empty)
    }
    var showSongsSheet by rememberSaveable {
        mutableStateOf(false)
    }
    var showDiscardDialog by remember {
        mutableStateOf(false)
    }
    var chipHint by remember {
        mutableStateOf<Int?>(null)
    }
    var mostPlayedTracks by remember {
        mutableStateOf<List<Track>>(emptyList())
    }
    var isLoadingMostPlayed by remember {
        mutableStateOf(true)
    }
    var creating by remember {
        mutableStateOf(false)
    }
    val createScale = remember {
        Animatable(1f)
    }

    val focusRequester = remember {
        FocusRequester()
    }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(160)
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    LaunchedEffect(Unit) {
        mostPlayedTracks = runCatching { onLoadMostPlayed() }.getOrDefault(emptyList())
        isLoadingMostPlayed = false
    }

    val trimmedTitle = title.trim()
    val isDuplicate by remember(trimmedTitle, playlists) {
        derivedStateOf {
            trimmedTitle.isNotEmpty() && playlists.any { it.name == trimmedTitle }
        }
    }
    val canCreate = trimmedTitle.isNotEmpty() && !isDuplicate && !creating

    val recentlyAddedTracks = remember(allTracks) {
        allTracks.sortedByDescending { it.dateModified }.take(25)
    }

    fun tracksForChip(chip: QuickStart): List<Track> = when (chip) {
        QuickStart.Empty -> emptyList()
        QuickStart.CurrentQueue -> currentQueue
        QuickStart.MostPlayed -> mostPlayedTracks
        QuickStart.RecentlyAdded -> recentlyAddedTracks
        QuickStart.RandomMix -> allTracks.shuffled().take(25)
    }

    fun isChipAvailable(chip: QuickStart): Boolean = when (chip) {
        QuickStart.Empty -> true
        QuickStart.CurrentQueue -> currentQueue.isNotEmpty()
        QuickStart.MostPlayed -> !isLoadingMostPlayed && mostPlayedTracks.isNotEmpty()
        QuickStart.RecentlyAdded -> recentlyAddedTracks.isNotEmpty()
        QuickStart.RandomMix -> allTracks.isNotEmpty()
    }

    fun chipHintRes(chip: QuickStart): Int = when (chip) {
        QuickStart.CurrentQueue -> R.string.quick_start_hint_no_queue
        QuickStart.MostPlayed -> R.string.quick_start_hint_no_history
        QuickStart.Empty,
        QuickStart.RecentlyAdded,
        QuickStart.RandomMix -> R.string.quick_start_hint_no_tracks
    }

    fun applyChip(chip: QuickStart) {
        val tracks = tracksForChip(chip)
        if (chip != QuickStart.Empty && tracks.isEmpty()) {
            chipHint = chipHintRes(chip)
            return
        }

        selectedTracks.clear()
        selectedTracks.addAll(tracks)
        selectedQuickStart = chip
        chipHint = null
    }

    fun create() {
        if (!canCreate) return
        creating = true
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch {
            createScale.animateTo(targetValue = 0.94f, animationSpec = tween(70))
            createScale.animateTo(targetValue = 1f, animationSpec = tween(130))
            onCreate(trimmedTitle, selectedTracks.toList(), customCover)
        }
    }

    fun requestDismiss() {
        if (selectedTracks.isNotEmpty()) {
            showDiscardDialog = true
        } else {
            onDismiss()
        }
    }

    BackHandler {
        requestDismiss()
    }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            customCover = uri
        }
    }

    val coverUris by remember(selectedTracks.toList()) {
        derivedStateOf {
            selectedTracks
                .map { it.coverArtUri }
                .filter { it != Uri.EMPTY }
                .distinct()
                .take(4)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedGradientBackground(
            palette = palette,
            animate = animate,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    end = 24.dp,
                    top = 16.dp,
                    bottom = 16.dp
                )
            ) {
                item(key = "cover") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        PlaylistCoverPreview(
                            customCover = customCover,
                            trackCovers = coverUris,
                            palette = palette,
                            onClick = {
                                coverPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            onRemoveCustomCover = { customCover = null },
                            modifier = Modifier.fillMaxWidth(0.5f)
                        )
                    }
                }

                item(key = "title") {
                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = stringResource(R.string.new_playlist_title),
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item(key = "title_field") {
                    Spacer(modifier = Modifier.height(22.dp))

                    PlaylistTitleField(
                        value = title,
                        onValueChange = { value ->
                            title = value.trimStart().filter {
                                it.isDigit() || it.isLetter() || it == ' '
                            }
                        },
                        focusRequester = focusRequester,
                        onDone = { create() },
                        isError = isDuplicate,
                        modifier = Modifier.fillMaxWidth()
                    )

                    AnimatedVisibility(visible = isDuplicate) {
                        Text(
                            text = stringResource(R.string.playlist_already_exists_error),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        )
                    }
                }

                item(key = "quick_start") {
                    Spacer(modifier = Modifier.height(18.dp))

                    QuickStartRow(
                        selected = selectedQuickStart,
                        isAvailable = ::isChipAvailable,
                        onSelect = ::applyChip
                    )

                    chipHint?.let { hint ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(hint),
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item(key = "add_songs") {
                    Spacer(modifier = Modifier.height(20.dp))

                    AddSongsRow(
                        count = selectedTracks.size,
                        onClick = { showSongsSheet = true }
                    )
                }

                if (selectedTracks.isEmpty()) {
                    item(key = "empty_hint") {
                        Text(
                            text = stringResource(R.string.no_songs_selected_hint),
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                } else {
                    itemsIndexed(
                        items = selectedTracks,
                        key = { index, track -> "${track.uri}-$index" }
                    ) { _, track ->
                        SelectedTrackRow(
                            track = track,
                            onRemove = {
                                selectedTracks.remove(track)
                                selectedQuickStart = null
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 4.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GhostPill(
                    text = stringResource(R.string.cancel),
                    onClick = ::requestDismiss,
                    modifier = Modifier.weight(1f)
                )

                CreatePill(
                    text = stringResource(R.string.create),
                    enabled = canCreate,
                    scale = createScale.value,
                    onClick = ::create,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (showSongsSheet) {
            AddSongsBottomSheet(
                allTracks = allTracks,
                selectedTracks = selectedTracks,
                onToggle = { track ->
                    if (!selectedTracks.remove(track)) {
                        selectedTracks.add(track)
                    }
                    selectedQuickStart = null
                },
                onSelectAll = {
                    selectedTracks.clear()
                    selectedTracks.addAll(allTracks)
                    selectedQuickStart = null
                },
                onClear = {
                    selectedTracks.clear()
                    selectedQuickStart = null
                },
                onDismissRequest = { showSongsSheet = false }
            )
        }

        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = {
                    Text(text = stringResource(R.string.discard_playlist_title))
                },
                text = {
                    Text(text = stringResource(R.string.discard_playlist_text))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDiscardDialog = false
                            onDismiss()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.discard),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDiscardDialog = false }
                    ) {
                        Text(text = stringResource(R.string.keep_editing))
                    }
                }
            )
        }
    }
}

@Composable
private fun AnimatedGradientBackground(
    palette: GradientPalette,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = animatedPaletteColors(palette = palette, animate = animate)
    val progress = rememberBlobProgress(animate = animate)

    Box(
        modifier = modifier
            .background(color = Color(0xFF07090B))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            Blobs.forEachIndexed { index, blob ->
                val angle = progress[index].value * 2f * PI.toFloat() + blob.phase
                val center = Offset(
                    x = (blob.x + sin(angle) * blob.amplitudeX) * width,
                    y = (blob.y + cos(angle * 0.85f + 1.4f) * blob.amplitudeY) * height
                )
                val radius = width * blob.radius * (0.94f + 0.06f * sin(angle + 1.1f))
                val color = colors[index]

                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.92f),
                            color.copy(alpha = 0.55f),
                            color.copy(alpha = 0f)
                        ),
                        center = center,
                        radius = radius.coerceAtLeast(1f)
                    )
                )
            }

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.35f),
                        Color.Black.copy(alpha = 0.55f)
                    )
                )
            )
        }
    }
}

@Composable
private fun animatedPaletteColors(
    palette: GradientPalette,
    animate: Boolean
): List<Color> {
    val spec: AnimationSpec<Color> = if (animate) {
        tween(durationMillis = 600)
    } else {
        snap()
    }

    val color0 by animateColorAsState(
        targetValue = palette.colors[0],
        animationSpec = spec,
        label = "palette-0"
    )
    val color1 by animateColorAsState(
        targetValue = palette.colors[1],
        animationSpec = spec,
        label = "palette-1"
    )
    val color2 by animateColorAsState(
        targetValue = palette.colors[2],
        animationSpec = spec,
        label = "palette-2"
    )
    val color3 by animateColorAsState(
        targetValue = palette.colors[3],
        animationSpec = spec,
        label = "palette-3"
    )

    return listOf(color0, color1, color2, color3)
}

@Composable
private fun rememberBlobProgress(animate: Boolean): List<State<Float>> {
    if (!animate) {
        return remember {
            List(4) { mutableStateOf(0f) }
        }
    }

    val transition = rememberInfiniteTransition(label = "playlist-gradient")

    return listOf(
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 12000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "blob-0"
        ),
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 14500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "blob-1"
        ),
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 17000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "blob-2"
        ),
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 19500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "blob-3"
        )
    )
}

@Composable
private fun PlaylistCoverPreview(
    customCover: Uri?,
    trackCovers: List<Uri>,
    palette: GradientPalette,
    onClick: () -> Unit,
    onRemoveCustomCover: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(
                elevation = 22.dp,
                shape = shape,
                ambientColor = palette.colors[0].copy(alpha = 0.45f),
                spotColor = palette.colors[2].copy(alpha = 0.55f)
            )
            .clip(shape)
            .background(color = Color.Black.copy(alpha = 0.25f))
            .clickable(onClick = onClick)
    ) {
        when {
            customCover != null -> CoverArt(
                uri = customCover,
                modifier = Modifier.fillMaxSize()
            )

            trackCovers.size >= 2 -> CoverMosaic(
                uris = trackCovers,
                palette = palette,
                modifier = Modifier.fillMaxSize()
            )

            trackCovers.size == 1 -> CoverArt(
                uri = trackCovers.first(),
                modifier = Modifier.fillMaxSize()
            )

            else -> GradientCover(
                palette = palette,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (customCover != null) {
            SmallRoundButton(
                icon = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.remove_playlist_cover),
                onClick = onRemoveCustomCover,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(30.dp)
            )
        }
    }
}

@Composable
private fun CoverMosaic(
    uris: List<Uri>,
    palette: GradientPalette,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = modifier.padding(4.dp)
    ) {
        Row(modifier = Modifier.weight(1f)) {
            MosaicCell(
                uri = uris.getOrNull(0),
                palette = palette,
                shape = shape,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            Spacer(modifier = Modifier.width(4.dp))

            MosaicCell(
                uri = uris.getOrNull(1),
                palette = palette,
                shape = shape,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(modifier = Modifier.weight(1f)) {
            MosaicCell(
                uri = uris.getOrNull(2),
                palette = palette,
                shape = shape,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            Spacer(modifier = Modifier.width(4.dp))

            MosaicCell(
                uri = uris.getOrNull(3),
                palette = palette,
                shape = shape,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun MosaicCell(
    uri: Uri?,
    palette: GradientPalette,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier
) {
    if (uri != null) {
        CoverArt(
            uri = uri,
            modifier = modifier.clip(shape)
        )
    } else {
        GradientCover(
            palette = palette,
            modifier = modifier.clip(shape)
        )
    }
}

@Composable
private fun GradientCover(
    palette: GradientPalette,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(
            brush = Brush.linearGradient(palette.colors)
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = stringResource(R.string.pick_playlist_cover),
            tint = Color.White.copy(alpha = 0.42f),
            modifier = Modifier.fillMaxSize(0.28f)
        )
    }
}

@Composable
private fun PlaylistTitleField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onDone: () -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember {
        MutableInteractionSource()
    }
    val focused by interactionSource.collectIsFocusedAsState()

    val underlineColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.error
            focused -> LotusTeal
            else -> Color.White.copy(alpha = 0.20f)
        },
        animationSpec = tween(220),
        label = "title-underline-color"
    )
    val underlineHeight by animateDpAsState(
        targetValue = if (focused) 2.dp else 1.dp,
        animationSpec = tween(220),
        label = "title-underline-height"
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(LotusTeal),
        interactionSource = interactionSource,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone() }
        ),
        modifier = modifier
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onDone()
                    true
                } else {
                    false
                }
            },
        decorationBox = { innerTextField ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.new_playlist_title_hint),
                            color = Color.White.copy(alpha = 0.42f),
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    innerTextField()
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(underlineHeight)
                        .background(underlineColor)
                )
            }
        }
    )
}

@Composable
private fun QuickStartRow(
    selected: QuickStart?,
    isAvailable: (QuickStart) -> Boolean,
    onSelect: (QuickStart) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(QuickStart.entries) { chip ->
            val available = isAvailable(chip)
            val isSelected = selected == chip

            Surface(
                onClick = { onSelect(chip) },
                shape = CircleShape,
                color = if (isSelected) {
                    LotusTeal.copy(alpha = 0.22f)
                } else {
                    Color.White.copy(alpha = 0.08f)
                },
                contentColor = if (isSelected) {
                    LotusTeal
                } else {
                    Color.White.copy(alpha = if (available) 0.85f else 0.35f)
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                        LotusTeal.copy(alpha = 0.65f)
                    } else {
                        Color.White.copy(alpha = if (available) 0.14f else 0.06f)
                    }
                ),
                modifier = Modifier.alpha(if (available) 1f else 0.55f)
            ) {
                Text(
                    text = stringResource(chip.labelRes),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }
    }
}

@Composable
private fun AddSongsRow(
    count: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(LotusTeal.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = LotusTeal,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = stringResource(R.string.add_songs),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(R.string.songs_selected_count, count),
                color = if (count > 0) LotusTeal else Color.White.copy(alpha = 0.45f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SelectedTrackRow(
    track: Track,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArt(
            uri = track.coverArtUri,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title ?: track.data.substringAfterLast('/'),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist ?: stringResource(R.string.unknown),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        SmallRoundButton(
            icon = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.remove_song),
            onClick = onRemove,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun GhostPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.35f),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        modifier = modifier
            .height(54.dp)
            .shadow(
                elevation = 16.dp,
                shape = CircleShape,
                ambientColor = Color.White.copy(alpha = 0.10f),
                spotColor = Color.White.copy(alpha = 0.10f)
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CreatePill(
    text: String,
    enabled: Boolean,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (enabled) LotusTeal else LotusTeal.copy(alpha = 0.22f),
        animationSpec = tween(200),
        label = "create-pill-color"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = containerColor,
        contentColor = if (enabled) Color(0xFF00201C) else Color.White.copy(alpha = 0.5f),
        modifier = modifier
            .height(54.dp)
            .shadow(
                elevation = if (enabled) 22.dp else 0.dp,
                shape = CircleShape,
                ambientColor = LotusTeal.copy(alpha = 0.35f),
                spotColor = LotusTeal.copy(alpha = 0.55f)
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SmallRoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSongsBottomSheet(
    allTracks: List<Track>,
    selectedTracks: List<Track>,
    onToggle: (Track) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF111315),
        contentColor = Color.White,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.22f))
        }
    ) {
        var query by rememberSaveable {
            mutableStateOf("")
        }
        val filteredTracks = remember(query, allTracks) {
            if (query.isBlank()) {
                allTracks
            } else {
                allTracks.filter { track ->
                    track.title?.contains(query, ignoreCase = true) == true ||
                            track.artist?.contains(query, ignoreCase = true) == true
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .imePadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.add_songs),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.weight(1f))

                TextButton(onClick = onDismissRequest) {
                    Text(
                        text = stringResource(R.string.done),
                        color = LotusTeal,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_songs_hint),
                        color = Color.White.copy(alpha = 0.4f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LotusTeal,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = LotusTeal,
                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onSelectAll,
                    enabled = allTracks.isNotEmpty()
                ) {
                    Text(
                        text = stringResource(R.string.select_all),
                        color = if (allTracks.isNotEmpty()) {
                            LotusTeal
                        } else {
                            Color.White.copy(alpha = 0.3f)
                        }
                    )
                }

                TextButton(
                    onClick = onClear,
                    enabled = selectedTracks.isNotEmpty()
                ) {
                    Text(
                        text = stringResource(R.string.clear),
                        color = if (selectedTracks.isNotEmpty()) {
                            LotusTeal
                        } else {
                            Color.White.copy(alpha = 0.3f)
                        }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = stringResource(R.string.songs_selected_count, selectedTracks.size),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = filteredTracks,
                    key = { it.uri.toString() }
                ) { track ->
                    val isSelected = selectedTracks.contains(track)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggle(track) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoverArt(
                            uri = track.coverArtUri,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title ?: track.data.substringAfterLast('/'),
                                color = Color.White,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist ?: stringResource(R.string.unknown),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = if (isSelected) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.RadioButtonUnchecked
                            },
                            contentDescription = null,
                            tint = if (isSelected) {
                                LotusTeal
                            } else {
                                Color.White.copy(alpha = 0.25f)
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
