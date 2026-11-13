package com.musicreader.app.ui.song

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicreader.app.R
import com.musicreader.app.ui.common.AppIcon
import com.musicreader.app.ui.common.AppIcons
import com.musicreader.app.ui.common.sectionDisplayName

private enum class LyricsMode { MAIN_TEXT, SECOND_VOICE, SYLLABLES }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LyricsScreen(viewModel: SongViewModel, sectionId: String, onBack: () -> Unit) {
    val flow = remember(viewModel, sectionId) { viewModel.lyricsState(sectionId) }
    val state by flow.collectAsStateWithLifecycle(initialValue = null)
    var mode by rememberSaveable { mutableStateOf(LyricsMode.MAIN_TEXT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val s = state
                    Text(
                        if (s == null) stringResource(R.string.lyrics)
                        else stringResource(R.string.lyrics_of, sectionDisplayName(s.type, s.label, s.number)),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { AppIcon(AppIcons.Back, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == LyricsMode.MAIN_TEXT,
                    onClick = { mode = LyricsMode.MAIN_TEXT },
                    label = { Text(stringResource(R.string.lyrics)) },
                )
                FilterChip(
                    selected = mode == LyricsMode.SECOND_VOICE,
                    onClick = { mode = LyricsMode.SECOND_VOICE },
                    label = { Text(stringResource(R.string.second_voice)) },
                )
                FilterChip(
                    selected = mode == LyricsMode.SYLLABLES,
                    onClick = { mode = LyricsMode.SYLLABLES },
                    label = { Text(stringResource(R.string.syllables)) },
                )
            }
            if (s.linkedCount > 1) {
                Text(
                    pluralStringResource(R.plurals.lyrics_linked_hint, s.linkedCount, s.linkedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            when (mode) {
                LyricsMode.MAIN_TEXT -> MainTextEditor(
                    key = s.sectionId,
                    text = s.mainText,
                    onTextChange = { viewModel.setMainLyrics(s.sectionId, it) },
                )
                LyricsMode.SECOND_VOICE -> SecondVoiceEditor(
                    lines = s.lines,
                    onLineChange = { mainLineId, text -> viewModel.setSecondLine(s.sectionId, mainLineId, text) },
                )
                LyricsMode.SYLLABLES -> SyllableView(
                    lines = s.lines,
                    onSplit = { wordId, syllable, offset -> viewModel.splitSyllable(s.sectionId, wordId, syllable, offset) },
                    onMerge = { wordId, left -> viewModel.mergeSyllables(s.sectionId, wordId, left) },
                    onReset = { wordId -> viewModel.resetWord(s.sectionId, wordId) },
                )
            }
        }
    }
}

/**
 * Plain text editor for the main voice. The field keeps its own text so typing is never
 * disturbed; every change is sent to the ViewModel, which keeps the syllables of unchanged words.
 */
@Composable
private fun MainTextEditor(key: String, text: String, onTextChange: (String) -> Unit) {
    var draft by rememberSaveable(key) { mutableStateOf(text) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            value = draft,
            onValueChange = {
                draft = it
                onTextChange(it)
            },
            label = { Text(stringResource(R.string.main_voice)) },
            placeholder = { Text(stringResource(R.string.lyrics_placeholder)) },
            minLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * One field per main line for its second voice (D-022). While writing the second line, its
 * main line is shown below it.
 */
@Composable
private fun SecondVoiceEditor(lines: List<LyricPairUi>, onLineChange: (mainLineId: String, text: String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.second_voice_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (lines.isEmpty()) Text(stringResource(R.string.no_lyrics), style = MaterialTheme.typography.bodyLarge)
        lines.forEachIndexed { index, line ->
            key(line.mainLineId) { SecondVoiceField(index, line, onLineChange) }
        }
    }
}

@Composable
private fun SecondVoiceField(index: Int, line: LyricPairUi, onLineChange: (mainLineId: String, text: String) -> Unit) {
    var draft by rememberSaveable(line.mainLineId) { mutableStateOf(line.secondText) }
    Column {
        OutlinedTextField(
            value = draft,
            onValueChange = {
                draft = it.replace('\n', ' ')
                onLineChange(line.mainLineId, draft)
            },
            label = { Text(stringResource(R.string.second_voice_line, index + 1)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            line.mainText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyllableView(
    lines: List<LyricPairUi>,
    onSplit: (wordId: String, syllableIndex: Int, offset: Int) -> Unit,
    onMerge: (wordId: String, leftIndex: Int) -> Unit,
    onReset: (wordId: String) -> Unit,
) {
    @Composable
    fun WordsRow(words: List<WordUi>) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            words.forEach { word ->
                WordChip(
                    word = word,
                    onSplit = { syllable, offset -> onSplit(word.id, syllable, offset) },
                    onMerge = { left -> onMerge(word.id, left) },
                    onReset = { onReset(word.id) },
                )
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.syllables_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (lines.isEmpty()) Text(stringResource(R.string.no_lyrics), style = MaterialTheme.typography.bodyLarge)
        lines.forEach { line ->
            WordsRow(line.mainWords)
            if (line.secondWords.isNotEmpty()) {
                Text(
                    stringResource(R.string.second_voice),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                WordsRow(line.secondWords)
            }
            HorizontalDivider()
        }
    }
}

/** A word as syllables separated by dots: tap between letters to split, tap a dot to join. */
@Composable
private fun WordChip(
    word: WordUi,
    onSplit: (syllableIndex: Int, offset: Int) -> Unit,
    onMerge: (leftIndex: Int) -> Unit,
    onReset: () -> Unit,
) {
    val border = if (word.manual) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = border,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            word.syllables.forEachIndexed { index, syllable ->
                if (index > 0) {
                    val joinLabel = stringResource(R.string.join_syllables)
                    Box(
                        Modifier
                            .widthIn(min = 24.dp)
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = joinLabel }
                            .clickable { onMerge(index - 1) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("·", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                SyllableText(syllable, onSplitAt = { offset -> onSplit(index, offset) }, onLongPress = onReset)
            }
        }
    }
}

/** A syllable; a tap between two of its letters splits it there, a long press resets the word. */
@Composable
private fun SyllableText(text: String, onSplitAt: (Int) -> Unit, onLongPress: () -> Unit) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val split by rememberUpdatedState(onSplitAt)
    val reset by rememberUpdatedState(onLongPress)
    Text(
        text,
        style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 2.sp),
        textAlign = TextAlign.Center,
        onTextLayout = { layout = it },
        modifier = Modifier.pointerInput(text) {
            detectTapGestures(
                onTap = { position ->
                    val offset = layout?.getOffsetForPosition(position) ?: return@detectTapGestures
                    if (offset in 1 until text.length) split(offset)
                },
                onLongPress = { reset() },
            )
        },
    )
}
