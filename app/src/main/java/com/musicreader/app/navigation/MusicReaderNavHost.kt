package com.musicreader.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.musicreader.app.ui.about.AboutScreen
import com.musicreader.app.ui.audio.CalibrationScreen
import com.musicreader.app.ui.audio.DiagnosticsScreen
import com.musicreader.app.ui.audio.MetronomeViewModel
import com.musicreader.app.ui.chords.ChordEditorScreen
import com.musicreader.app.ui.library.LibraryScreen
import com.musicreader.app.ui.settings.SettingsScreen
import com.musicreader.app.ui.song.LyricsScreen
import com.musicreader.app.ui.song.SectionsScreen
import com.musicreader.app.ui.song.SongSettingsScreen
import com.musicreader.app.ui.song.SongViewModel
import com.musicreader.app.ui.tab.TabEditorScreen
import com.musicreader.app.ui.viewer.ViewerScreen
import com.musicreader.app.ui.viewer.ViewerViewModel
import kotlinx.serialization.Serializable

@Serializable
object LibraryRoute

@Serializable
object AboutRoute

@Serializable
object AppSettingsRoute

@Serializable
object CalibrationRoute

@Serializable
object DiagnosticsRoute

/** Nested graph for one song; its screens share one [SongViewModel]. */
@Serializable
data class SongRoute(val songId: String, val openSettings: Boolean = false)

@Serializable
object SectionsRoute

@Serializable
object SongSettingsRoute

@Serializable
data class LyricsRoute(val sectionId: String)

@Serializable
object ViewerRoute

@Serializable
data class ChordsRoute(val sectionId: String)

@Serializable
data class TabRoute(val sectionId: String)

@Composable
fun MusicReaderNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = LibraryRoute) {
        composable<LibraryRoute> {
            LibraryScreen(
                onOpenSong = { id, openSettings -> navController.navigate(SongRoute(id, openSettings)) },
                onAbout = { navController.navigate(AboutRoute) },
                onSettings = { navController.navigate(AppSettingsRoute) },
            )
        }
        composable<AboutRoute> {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable<AppSettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenCalibration = { navController.navigate(CalibrationRoute) },
                onOpenDiagnostics = { navController.navigate(DiagnosticsRoute) },
            )
        }
        composable<CalibrationRoute> {
            CalibrationScreen(onBack = { navController.popBackStack() })
        }
        composable<DiagnosticsRoute> {
            DiagnosticsScreen(onBack = { navController.popBackStack() })
        }
        navigation<SongRoute>(startDestination = SectionsRoute) {
            composable<SectionsRoute> { entry ->
                val songEntry = remember(entry) { navController.getBackStackEntry<SongRoute>() }
                val viewModel: SongViewModel = viewModel(songEntry, factory = SongViewModel.Factory)
                // Scoped to the song: keeps playing on the song's other screens, stops when it closes.
                val metronome: MetronomeViewModel =
                    viewModel(songEntry, factory = MetronomeViewModel.factory(viewModel.currentSong))
                SectionsScreen(
                    viewModel = viewModel,
                    metronome = metronome,
                    onBack = { navController.popBackStack(LibraryRoute, inclusive = false) },
                    onOpenSettings = { navController.navigate(SongSettingsRoute) },
                    onOpenLyrics = { sectionId -> navController.navigate(LyricsRoute(sectionId)) },
                    onOpenViewer = { navController.navigate(ViewerRoute) },
                    onOpenChords = { sectionId -> navController.navigate(ChordsRoute(sectionId)) },
                    onOpenTab = { sectionId -> navController.navigate(TabRoute(sectionId)) },
                )
            }
            composable<SongSettingsRoute> { entry ->
                val songEntry = remember(entry) { navController.getBackStackEntry<SongRoute>() }
                val viewModel: SongViewModel = viewModel(songEntry, factory = SongViewModel.Factory)
                SongSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable<ViewerRoute> { entry ->
                val songEntry = remember(entry) { navController.getBackStackEntry<SongRoute>() }
                val songViewModel: SongViewModel = viewModel(songEntry, factory = SongViewModel.Factory)
                val viewerViewModel: ViewerViewModel = viewModel(
                    factory = ViewerViewModel.factory(songViewModel.currentSong, songViewModel.chordDictionary),
                )
                ViewerScreen(viewModel = viewerViewModel, onBack = { navController.popBackStack() })
            }
            composable<ChordsRoute> { entry ->
                val songEntry = remember(entry) { navController.getBackStackEntry<SongRoute>() }
                val viewModel: SongViewModel = viewModel(songEntry, factory = SongViewModel.Factory)
                ChordEditorScreen(
                    viewModel = viewModel,
                    sectionId = entry.toRoute<ChordsRoute>().sectionId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<TabRoute> { entry ->
                val songEntry = remember(entry) { navController.getBackStackEntry<SongRoute>() }
                val viewModel: SongViewModel = viewModel(songEntry, factory = SongViewModel.Factory)
                TabEditorScreen(
                    viewModel = viewModel,
                    sectionId = entry.toRoute<TabRoute>().sectionId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<LyricsRoute> { entry ->
                val songEntry = remember(entry) { navController.getBackStackEntry<SongRoute>() }
                val viewModel: SongViewModel = viewModel(songEntry, factory = SongViewModel.Factory)
                LyricsScreen(
                    viewModel = viewModel,
                    sectionId = entry.toRoute<LyricsRoute>().sectionId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
