package org.lattenwald.moonvocab.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

internal val OuterCorner = 24.dp
internal val InnerCorner = 6.dp

@Composable
internal fun segmentedRowShapes(index: Int, count: Int): ListItemShapes =
    ListItemDefaults.segmentedShapes(index, count, ListItemDefaults.shapes(shape = RoundedCornerShape(InnerCorner)))
