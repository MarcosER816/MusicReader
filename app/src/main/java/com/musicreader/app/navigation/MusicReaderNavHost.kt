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
import com.musicreader.app.ui.library.LibraryScreen
import com.musicreader.app.ui.song.LyricsScreen
import com.musicreader.app.ui.song.SectionsScreen
import com.musicreader.app.ui.song.SongSettingsScreen
import com.musicreader.app.ui.song.SongViewModel
import com.musicreader.app.ui.viewer.ViewerScreen
import com.musicreader.app.ui.viewer.ViewerViewModel
import kotlinx.serialization.Serializable

@Serializable
object LibraryRoute

@Serializable
object AboutRoute

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

@Composable
fun MusicReaderNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = LibraryRoute) {
        composable<LibraryRoute> {
            LibraryScreen(
                onOpenSong = { id, openSettings -> navController.navigate(SongRoute(id, openSettings)) },
                onAbout = { navController.navigate(AboutRoute) },
            )
        }
        composable<AboutRoute> {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        navigation<SongRoute>(startDestination = SectionsRoute) {
            composable<SectionsRoute> { entry ->
                val songEntry = remember(entry) { navController.getBackStackEntry<SongRoute>() }
                val viewModel: SongViewModel = viewModel(songEntry, factory = SongViewModel.Factory)
                SectionsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack(LibraryRoute, inclusive = false) },
                    onOpenSettings = { navController.navigate(SongSettingsRoute) },
                    onOpenLyrics = { sectionId -> navController.navigate(LyricsRoute(sectionId)) },
                    onOpenViewer = { navController.navigate(ViewerRoute) },
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
                val viewerViewModel: ViewerViewModel = viewModel(factory = ViewerViewModel.factory(songViewModel.currentSong))
                ViewerScreen(viewModel = viewerViewModel, onBack = { navController.popBackStack() })
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
