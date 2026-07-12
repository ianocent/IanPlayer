package com.ianocent.musicplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * List responsif yang selalu nampilin item secara utuh (ga ada yang kepotong),
 * ngitung tinggi tiap item berdasarkan sisa ruang layar yang tersedia (BoxWithConstraints)
 * dan pake snap fling behavior biar scroll-nya "magnet" ke item terdekat, jadi pas
 * discroll ga ada moment nanggung/kepotong setengah item.
 */
@Composable
fun <T> ResponsiveSnapList(
    items: List<T>,
    key: (T) -> Any,
    scrollbarColor: Color,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    minItemHeight: Dp = 72.dp,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 90.dp,
    itemContent: @Composable LazyItemScope.(T, Dp) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availableHeight = (maxHeight - topPadding).coerceAtLeast(minItemHeight)
        val itemsPerScreen = (availableHeight / minItemHeight).toInt().coerceAtLeast(1)
        val itemHeight = availableHeight / itemsPerScreen
        val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)

        val dedupedItems = remember(items) {
            val seenKeys = HashSet<Any>()
            items.filter { seenKeys.add(key(it)) }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                flingBehavior = snapBehavior,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding, end = 20.dp)
            ) {
                items(dedupedItems, key = key) { item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        itemContent(item, itemHeight)
                    }
                }
            }
            DraggableScrollbar(listState, scrollbarColor)
        }
    }
}

@Composable
fun BoxScope.DraggableScrollbar(
    listState: LazyListState,
    color: Color,
    thumbWidth: Dp = 6.dp
) {
    val coroutineScope = rememberCoroutineScope()
    var containerHeightPx by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    val totalItems = listState.layoutInfo.totalItemsCount
    if (totalItems <= 0) return

    val visibleCount = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val thumbFraction = (visibleCount.toFloat() / totalItems).coerceIn(0.08f, 1f)
    val firstVisible = listState.firstVisibleItemIndex
    val maxScrollableIndex = (totalItems - 1).coerceAtLeast(1)
    val scrollFraction = firstVisible.toFloat() / maxScrollableIndex

    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(28.dp)
            .onGloballyPositioned { containerHeightPx = it.size.height.toFloat() }
            .pointerInput(totalItems) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, _ ->
                        change.consume()
                        if (containerHeightPx > 0) {
                            val fraction = (change.position.y / containerHeightPx).coerceIn(0f, 1f)
                            val targetIndex = (fraction * maxScrollableIndex).toInt().coerceIn(0, maxScrollableIndex)
                            coroutineScope.launch { listState.scrollToItem(targetIndex) }
                        }
                    }
                )
            }
    ) {
        val thumbHeightDp = with(density) { (containerHeightPx * thumbFraction).toDp() }
        val offsetYDp = with(density) { (containerHeightPx * scrollFraction * (1 - thumbFraction)).toDp() }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp)
                .offset(y = offsetYDp)
                .width(thumbWidth)
                .height(thumbHeightDp.coerceAtLeast(24.dp))
                .clip(RoundedCornerShape(thumbWidth / 2))
                .background(color.copy(alpha = if (isDragging) 0.9f else 0.4f))
        )
    }
}
