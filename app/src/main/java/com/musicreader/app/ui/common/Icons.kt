package com.musicreader.app.ui.common

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.musicreader.app.R

/** Small wrapper so screens use the app's own vector icons (res/drawable/ic_*.xml). */
@Composable
fun AppIcon(id: Int, contentDescription: String?, modifier: Modifier = Modifier) {
    Icon(painter = painterResource(id), contentDescription = contentDescription, modifier = modifier)
}

object AppIcons {
    val Add = R.drawable.ic_add
    val Back = R.drawable.ic_arrow_back
    val More = R.drawable.ic_more_vert
    val DragHandle = R.drawable.ic_drag_handle
    val Link = R.drawable.ic_link
    val Remove = R.drawable.ic_remove
}
