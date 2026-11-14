package com.musicreader.app.ui.chords

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musicreader.app.R
import com.musicreader.core.data.ChordDictionary
import com.musicreader.core.model.AccidentalPreference
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.ChordSymbol
import com.musicreader.core.model.Enharmonics
import com.musicreader.core.model.NoteName
import com.musicreader.core.model.chordColor

/**
 * Choose a chord: root (12, spelled with sharps or flats), then type, then swipe through every
 * shape the dictionary has for it. [onChoose] gets the chord name and the chosen shape's voicingId.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChordPickerSheet(
    dictionary: ChordDictionary,
    settings: AppSettings,
    initial: ChordSymbol?,
    initialVoicingId: String?,
    onPreferenceChange: (AccidentalPreference) -> Unit,
    onChoose: (ChordSymbol, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val preference = settings.accidentals
    val roots = remember(preference) { ChordDictionary.roots(preference) }
    // Selection is kept as pitch class + dictionary suffix, so switching ♯/♭ keeps it.
    var rootPc by rememberSaveable { mutableIntStateOf(initial?.let { Enharmonics.pitchClass(it.root, it.accidental) } ?: 0) }
    var suffix by rememberSaveable {
        mutableStateOf(initial?.let { dictionary.find(it)?.dictionarySuffix } ?: "major")
    }
    val root: NoteName = initial?.takeIf { Enharmonics.pitchClass(it.root, it.accidental) == rootPc && preferenceMatches(it, preference) }
        ?.let { NoteName(it.root, it.accidental) }
        ?: roots[rootPc]
    val types = remember(rootPc) { dictionary.typesFor(roots[rootPc]) }
    val chord = types.firstOrNull { it.dictionarySuffix == suffix } ?: types.first()
    val initialPage = initialVoicingId?.let { id -> chord.shapes.indexOfFirst { it.voicingId == id } }?.takeIf { it >= 0 } ?: 0
    val pager = rememberPagerState(initialPage = initialPage) { chord.shapes.size }
    LaunchedEffect(chord) { if (pager.currentPage >= chord.shapes.size) pager.scrollToPage(0) }
    val symbol = chord.symbol(root)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    symbol.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(settings.chordColor(symbol).toInt()),
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = preference == AccidentalPreference.SHARPS,
                    onClick = { onPreferenceChange(AccidentalPreference.SHARPS) },
                    label = { Text("♯") },
                )
                FilterChip(
                    selected = preference == AccidentalPreference.FLATS,
                    onClick = { onPreferenceChange(AccidentalPreference.FLATS) },
                    label = { Text("♭") },
                )
            }

            Text(stringResource(R.string.chord_root_label), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                roots.forEachIndexed { pc, name ->
                    FilterChip(selected = pc == rootPc, onClick = { rootPc = pc }, label = { Text(name.toString()) })
                }
            }

            Text(stringResource(R.string.chord_type_label), style = MaterialTheme.typography.labelLarge)
            val typesState = rememberLazyListState()
            LazyRow(state = typesState, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(types, key = { it.dictionarySuffix }) { type ->
                    FilterChip(
                        selected = type.dictionarySuffix == chord.dictionarySuffix,
                        onClick = { suffix = type.dictionarySuffix },
                        label = { Text(type.symbol(root).toString()) },
                    )
                }
            }

            Text(
                stringResource(R.string.chord_shape_of, pager.currentPage + 1, chord.shapes.size),
                style = MaterialTheme.typography.labelLarge,
            )
            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FretboardDiagram(
                        shape = chord.shapes[page],
                        fingerColors = settings.fingerColors,
                        leftHanded = settings.leftHanded,
                        width = 200.dp,
                    )
                }
            }
            Text(
                stringResource(R.string.chord_swipe_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(onClick = {
                    val shape = chord.shapes[pager.currentPage.coerceIn(0, chord.shapes.lastIndex)]
                    onChoose(symbol, shape.voicingId)
                }) { Text(stringResource(R.string.use_this_shape)) }
            }
        }
    }
}

/** True when the initial chord's own spelling fits the preference (or has no accidental). */
private fun preferenceMatches(symbol: ChordSymbol, preference: AccidentalPreference): Boolean =
    Enharmonics.spell(Enharmonics.pitchClass(symbol.root, symbol.accidental), preference) == NoteName(symbol.root, symbol.accidental)
