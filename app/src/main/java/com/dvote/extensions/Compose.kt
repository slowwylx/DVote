package com.dvote.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter

@Composable
fun ImageVector.toPainter(): Painter {
    return rememberVectorPainter(this)
}