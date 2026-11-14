package com.musicreader.app.ui.viewer

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicreader.app.R
import com.musicreader.app.ui.common.AppIcon
import com.musicreader.app.ui.common.AppIcons
import com.musicreader.app.ui.common.SheetRowLayout
import com.musicreader.app.ui.tab.TabStaff
import com.musicreader.app.ui.common.sectionDisplayName
import com.musicreader.app.ui.chords.FretboardDiagram
import com.musicreader.core.data.ChordShape
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.ChordSymbol
import com.musicreader.core.model.Layer
import com.musicreader.core.model.LayerVisibility
import com.musicreader.core.model.PlacedChord
import com.musicreader.core.model.PlacedTabNote
import com.musicreader.core.model.PlacedText
import com.musicreader.core.model.SheetRow
import com.musicreader.core.model.TabNotation
import com.musicreader.core.model.TabEvent
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(viewModel: ViewerViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showTextSize by rememberSaveable { mutableStateOf(false) }
    val s = state

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s?.title?.ifBlank { null } ?: stringResource(R.string.untitled), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { AppIcon(AppIcons.Back, stringResource(R.string.back)) }
                },
                actions = {
                    TextButton(onClick = { showTextSize = true }) {
                        Text(stringResource(R.string.text_size_short), style = MaterialTheme.typography.titleMedium)
                    }
                },
            )
        },
        bottomBar = {
            if (s != null) {
                AutoscrollBar(
                    playing = s.playing,
                    speedPosition = s.speedSliderPosition,
                    onTogglePlaying = viewModel::togglePlaying,
                    onSpeedChange = viewModel::setSpeedFromSlider,
                )
            }
        },
    ) { padding ->
        if (s == null) return@Scaffold
        val glisten = rememberGlistenPhase()
        ViewerContent(
            state = s,
            glistenPhase = glisten,
            onToggleLayer = viewModel::toggleLayer,
            onUserTouch = viewModel::pause,
            onReachedEnd = viewModel::pause,
            modifier = Modifier.padding(padding),
        )
    }

    if (showTextSize && s != null) {
        ModalBottomSheet(onDismissRequest = { showTextSize = false }) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
                Text(stringResource(R.string.text_size), style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = s.fontScale,
                    onValueChange = viewModel::setFontScale,
                    valueRange = AppSettings.MIN_VIEWER_FONT_SCALE..AppSettings.MAX_VIEWER_FONT_SCALE,
                )
                Text(
                    "Am  C  G",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp * s.fontScale),
                )
            }
        }
    }
}

/**
 * The one glisten phase (0..1) shared by every chord label, so they all shine in sync.
 * A single InfiniteTransition drives it; labels read it only while drawing.
 */
@Composable
fun rememberGlistenPhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "glisten")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = GLISTEN_SPEC,
        label = "glistenPhase",
    )
}

private val GLISTEN_SPEC: InfiniteRepeatableSpec<Float> =
    infiniteRepeatable(tween(durationMillis = 2800, easing = LinearEasing), RepeatMode.Restart)

/** The viewer below the top bar: layer checkboxes and the scrolling song. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerContent(
    state: ViewerUiState,
    glistenPhase: State<Float>,
    onToggleLayer: (Layer) -> Unit,
    onUserTouch: () -> Unit,
    onReachedEnd: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    var sheetChord by remember { mutableStateOf<PlacedChord?>(null) }
    val density = LocalDensity.current
    val speedPx = with(density) { state.speedDpPerSecond.toFloat().dp.toPx() }
    val reachedEnd by rememberUpdatedState(onReachedEnd)

    // Autoscroll: a small step on every frame, sized by the time since the last frame.
    LaunchedEffect(state.playing, speedPx, listState) {
        if (!state.playing) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val seconds = (now - last) / 1_000_000_000f
                    listState.dispatchRawDelta(speedPx * seconds)
                    if (!listState.canScrollForward) reachedEnd()
                }
                last = now
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        LayerCheckboxes(state, onToggleLayer)
        if (state.layout.sections.isEmpty()) {
            Text(stringResource(R.string.song_is_empty), Modifier.padding(24.dp))
            return@Column
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Any touch on the song pauses the autoscroll (the touch still scrolls).
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.pressed }) onUserTouch()
                        }
                    }
                },
        ) {
            state.layout.sections.forEach { section ->
                item(key = "h-${section.sectionId}") {
                    Text(
                        sectionDisplayName(section.type, section.label, section.number),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp * state.fontScale),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                section.rows.forEachIndexed { i, row ->
                    // Rows with nothing in the visible layers are left out.
                    if (!row.hasVisibleContent(state.layers)) return@forEachIndexed
                    item(key = "r-${section.sectionId}-$i") {
                        SheetRowView(
                            row = row,
                            state = state,
                            glistenPhase = glistenPhase,
                            onChordLongPress = { sheetChord = it },
                        )
                    }
                }
            }
        }
    }

    sheetChord?.let { chord ->
        ModalBottomSheet(onDismissRequest = { sheetChord = null }) {
            ChordSheet(chord.symbol, Color(state.chordColor(chord.symbol).toInt()), state.shapeOf(chord), state.settings)
        }
    }
}

@Composable
private fun LayerCheckboxes(state: ViewerUiState, onToggle: (Layer) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            Layer.LYRICS to R.string.layer_lyrics,
            Layer.CHORDS to R.string.layer_chords,
            Layer.TAB to R.string.layer_tabs,
        ).forEach { (layer, label) ->
            val checked = state.layers.isVisible(layer)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onToggle(layer) },
                    // The last visible layer cannot be switched off.
                    enabled = !(checked && state.visibleLayerCount == 1),
                )
                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun AutoscrollBar(
    playing: Boolean,
    speedPosition: Float,
    onTogglePlaying: () -> Unit,
    onSpeedChange: (Float) -> Unit,
) {
    BottomAppBar {
        FilledIconButton(onClick = onTogglePlaying, modifier = Modifier.padding(start = 8.dp)) {
            AppIcon(
                if (playing) AppIcons.Pause else AppIcons.Play,
                stringResource(if (playing) R.string.autoscroll_pause else R.string.autoscroll_play),
            )
        }
        Text(
            stringResource(R.string.autoscroll_speed),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Slider(value = speedPosition, onValueChange = onSpeedChange, modifier = Modifier.weight(1f).padding(end = 16.dp))
    }
}

/** One row: chords on top, tab in the middle, lyrics at the bottom, on the same beat positions. */
@Composable
private fun SheetRowView(
    row: SheetRow,
    state: ViewerUiState,
    glistenPhase: State<Float>,
    onChordLongPress: (PlacedChord) -> Unit,
) {
    val scale = state.fontScale
    val lyricStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp * scale)
    val lyricColor = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        // Chords, tab and lyrics on one beat grid (D-025, D-027).
        SheetRowLayout(
            row = row,
            showChords = state.layers.chords,
            showTab = state.layers.tab,
            showLyrics = state.layers.lyrics,
            syllableContent = { Text(it.text, style = lyricStyle, color = lyricColor, maxLines = 1) },
            chordContent = { chord ->
                ChordLabel(chord, Color(state.chordColor(chord.symbol).toInt()), scale, glistenPhase, onChordLongPress)
            },
            staff = { line -> TabStaff(line, state.settings.fingerColors, scale) },
        )
        if (state.layers.lyrics && row.secondLyrics.isNotEmpty()) {
            Text(
                row.secondLyrics.joinToString(" ") { it.text },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp * scale, fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

/** A chord name in its root color with the shared glisten sweeping across it. */
@Composable
private fun ChordLabel(
    chord: PlacedChord,
    color: Color,
    scale: Float,
    glistenPhase: State<Float>,
    onLongPress: (PlacedChord) -> Unit,
) {
    val name = chord.symbol.toString()
    val description = stringResource(R.string.chord_label_description, name)
    Text(
        name,
        color = color,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp * scale, fontWeight = FontWeight.Bold),
        modifier = Modifier
            .semantics { contentDescription = description }
            .pointerInput(chord) { detectTapGestures(onLongPress = { onLongPress(chord) }) }
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithCache {
                // Built once per size; each frame only moves it (no allocation per frame).
                val band = size.width * 0.6f + size.height
                val brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent),
                    start = Offset(0f, 0f),
                    end = Offset(band, size.height),
                )
                val bandSize = Size(band, size.height)
                onDrawWithContent {
                    drawContent()
                    // The sweep crosses the label during the first half of the cycle, then rests.
                    val p = glistenPhase.value * 2f
                    if (p < 1f) {
                        translate(left = -band + p * (size.width + band)) {
                            drawRect(brush, size = bandSize, blendMode = BlendMode.SrcAtop)
                        }
                    }
                }
            },
    )
}

/** Long-press sheet: the chord name in its color, the chosen shape's diagram, root, type and bass. */
@Composable
private fun ChordSheet(chord: ChordSymbol, color: Color, shape: ChordShape?, settings: AppSettings) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(chord.toString(), style = MaterialTheme.typography.displayMedium, color = color, fontWeight = FontWeight.Bold)
        if (shape != null) {
            FretboardDiagram(
                shape = shape,
                fingerColors = settings.fingerColors,
                leftHanded = settings.leftHanded,
                width = 220.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        Text(stringResource(R.string.chord_root, chord.root.name + chord.accidental.symbol), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.chord_type, chord.suffix.ifEmpty { stringResource(R.string.chord_type_major) }),
            style = MaterialTheme.typography.bodyLarge,
        )
        chord.bass?.let { Text(stringResource(R.string.chord_bass, it.toString()), style = MaterialTheme.typography.bodyLarge) }
    }
}

/** True when the row shows something with these layers. */
internal fun SheetRow.hasVisibleContent(layers: LayerVisibility): Boolean =
    (layers.chords && chords.isNotEmpty()) ||
        (layers.tab && tab.isNotEmpty()) ||
        (layers.lyrics && (lyrics.isNotEmpty() || secondLyrics.isNotEmpty()))
