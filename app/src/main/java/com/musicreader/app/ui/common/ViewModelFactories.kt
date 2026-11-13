package com.musicreader.app.ui.common

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.musicreader.app.AppContainer
import com.musicreader.app.MusicReaderApplication

/** The app container, from inside a `viewModelFactory { initializer { … } }` block. */
val CreationExtras.appContainer: AppContainer
    get() = (this[APPLICATION_KEY] as MusicReaderApplication).container
