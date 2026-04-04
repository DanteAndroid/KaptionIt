package com.danteandroid.transbee.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 2.dp),
        style = ScrollbarStyle(
            minimalHeight = 16.dp,
            thickness = 9.dp,
            shape = RoundedCornerShape(4.5.dp),
            hoverDurationMillis = 300,
            unhoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            hoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
    )
}
