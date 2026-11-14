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
import com.musicreader.app.ui.common.sectionDisplayName
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
    var sheetChord by remember { mutableStateOf<ChordSymbol?>(null) }
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
            ChordSheet(chord, Color(state.chordColor(chord).toInt()))
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
    onChordLongPress: (ChordSymbol) -> Unit,
) {
    val barColor = MaterialTheme.colorScheme.outlineVariant
    val scale = state.fontScale
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .drawBehind {
                // Bar lines across the whole row.
                row.barLines.forEach { x ->
                    val px = (x * size.width).toFloat()
                    drawLine(barColor, Offset(px, 0f), Offset(px, size.height), strokeWidth = 1.dp.toPx())
                }
            },
    ) {
        if (state.layers.chords && row.chords.isNotEmpty()) {
            PlacedLane(row.chords.map { it.x }) {
                row.chords.forEach { chord ->
                    ChordLabel(chord, Color(state.chordColor(chord.symbol).toInt()), scale, glistenPhase, onChordLongPress)
                }
            }
        }
        if (state.layers.tab && row.tab.isNotEmpty()) {
            TabLane(row.tab, state, scale)
        }
        if (state.layers.lyrics) {
            if (row.lyrics.isNotEmpty()) {
                LyricLane(row.lyrics, MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp * scale), MaterialTheme.colorScheme.onSurface)
            }
            if (row.secondLyrics.isNotEmpty()) {
                LyricLane(
                    row.secondLyrics,
                    MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp * scale, fontStyle = FontStyle.Italic),
                    MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun LyricLane(texts: List<PlacedText>, style: TextStyle, color: Color) {
    PlacedLane(texts.map { it.x }) {
        texts.forEach { Text(it.text, style = style, color = color, maxLines = 1) }
    }
}

/**
 * Places its children at x fractions of the width. A child never overlaps the one before it
 * (it is pushed right), and when the row overflows everything is shifted left as far as possible.
 */
@Composable
private fun PlacedLane(xs: List<Double>, content: @Composable () -> Unit) {
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val width = constraints.maxWidth
        val placeables = measurables.map { it.measure(Constraints()) }
        val lefts = IntArray(placeables.size)
        var right = -gap
        placeables.forEachIndexed { i, p ->
            val wanted = ((xs.getOrElse(i) { 0.0 }) * width).toInt()
            lefts[i] = maxOf(wanted, right + gap)
            right = lefts[i] + p.width
        }
        val overflow = (right - width).coerceAtMost(lefts.firstOrNull() ?: 0)
        if (overflow > 0) for (i in lefts.indices) lefts[i] -= overflow
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { i, p -> p.place(lefts[i], 0) }
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
    onLongPress: (ChordSymbol) -> Unit,
) {
    val name = chord.symbol.toString()
    val description = stringResource(R.string.chord_label_description, name)
    Text(
        name,
        color = color,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp * scale, fontWeight = FontWeight.Bold),
        modifier = Modifier
            .semantics { contentDescription = description }
            .pointerInput(chord.symbol) { detectTapGestures(onLongPress = { onLongPress(chord.symbol) }) }
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

/** Six string lines with fret numbers in finger colors (open strings: white outline). */
@Composable
private fun TabLane(notes: List<PlacedTabNote>, state: ViewerUiState, scale: Float) {
    val measurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val background = MaterialTheme.colorScheme.background
    val style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp * scale, fontWeight = FontWeight.Bold)
    val spacing = 11.dp * scale
    Canvas(Modifier.fillMaxWidth().height(spacing * 6).padding(vertical = 2.dp)) {
        val gap = size.height / TabEvent.LOWEST_STRING
        fun y(string: Int) = gap * (string - 0.5f)
        for (string in TabEvent.HIGHEST_STRING..TabEvent.LOWEST_STRING) {
            drawLine(lineColor, Offset(0f, y(string)), Offset(size.width, y(string)), strokeWidth = 1.dp.toPx())
        }
        notes.forEach { note ->
            val color = Color(state.fingerColor(note.finger).toInt())
            val layout = measurer.measure(TabNotation.label(note.fret, note.technique), style.copy(color = color))
            val x = (note.x * size.width).toFloat().coerceIn(0f, size.width - layout.size.width)
            val top = y(note.string) - layout.size.height / 2f
            val pad = 2.dp.toPx()
            drawRect(background, Offset(x - pad, top), Size(layout.size.width + 2 * pad, layout.size.height.toFloat()))
            if (note.finger == 0) {
                // Open string: an outline around the label (a circle for "0", a pill for "<12>").
                val h = layout.size.height.toFloat()
                val w = maxOf(layout.size.width.toFloat(), h)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x + layout.size.width / 2f - w / 2f - pad, top),
                    size = Size(w + 2 * pad, h),
                    cornerRadius = CornerRadius(h / 2f),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            drawText(layout, topLeft = Offset(x, top))
        }
    }
}

/** Long-press sheet: the chord name in its color, with root, type and bass. */
@Composable
private fun ChordSheet(chord: ChordSymbol, color: Color) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(chord.toString(), style = MaterialTheme.typography.displayMedium, color = color, fontWeight = FontWeight.Bold)
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

