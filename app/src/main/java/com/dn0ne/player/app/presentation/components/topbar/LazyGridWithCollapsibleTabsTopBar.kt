package com.dn0ne.player.app.presentation.components.topbar

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dn0ne.player.app.presentation.components.design.EditorialType
import com.dn0ne.player.app.presentation.components.library.LibraryStickyHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Library header: a fixed title block (provided through [topBarButtons]), a pinned row of
 * outlined filter chips and the paged content underneath.
 *
 * The chip row still drives the same pager, so swiping between sections keeps working.
 * Track sections get a floating sticky label plus a thin draggable fast scroller.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyGridWithCollapsibleTabsTopBar(
    topBarTabs: List<Tab>,
    defaultSelectedTab: Tab = Tab.Tracks,
    onTabChange: (tab: Tab) -> Unit = {},
    tabTitleTextStyle: TextStyle = MaterialTheme.typography.headlineLarge,
    tabRowTitleTextStyle: TextStyle = MaterialTheme.typography.titleLarge,
    topBarButtons: @Composable BoxScope.(tab: Tab) -> Unit = {},
    minTopBarHeight: Dp = 60.dp,
    maxTopBarHeight: Dp = 250.dp,
    maxTopBarHeightLandscape: Dp = 150.dp,
    collapsedByDefault: Boolean = false,
    collapseFraction: (Float) -> Unit = {},
    onScrollOffsetChange: (Float) -> Unit = {},
    gridStates: Map<Tab, LazyGridState> = emptyMap(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentHorizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    contentVerticalArrangement: Arrangement.Vertical = Arrangement.Top,
    enableScrollbar: Boolean = true,
    showTabsRow: Boolean = true,
    modifier: Modifier = Modifier,
    gridCells: (tab: Tab) -> GridCells = { GridCells.Fixed(1) },
    stickyHeaderForItem: (tab: Tab, index: Int) -> LibraryStickyHeader? = { _, _ -> null },
    tabContent: LazyGridScope.(tab: Tab) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var selectedTab by rememberSaveable {
        mutableStateOf(defaultSelectedTab)
    }

    val fallbackGridState = rememberLazyGridState()
    val resolvedGridStates = remember(topBarTabs, gridStates) {
        if (gridStates.isEmpty()) {
            topBarTabs.associateWith { LazyGridState() }
        } else gridStates
    }
    fun gridStateOf(tab: Tab): LazyGridState = resolvedGridStates[tab] ?: fallbackGridState

    val pagerState = rememberPagerState(
        initialPage = topBarTabs.indexOf(selectedTab).coerceAtLeast(0),
        pageCount = { topBarTabs.size }
    )

    LaunchedEffect(pagerState, topBarTabs) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val tab = topBarTabs.getOrNull(page) ?: return@collect
            if (tab != selectedTab) {
                selectedTab = tab
                onTabChange(tab)
            }
        }
    }

    LaunchedEffect(selectedTab, topBarTabs, density) {
        snapshotFlow {
            val info = gridStateOf(selectedTab).layoutInfo.visibleItemsInfo.firstOrNull()
            (info?.index ?: 0) to (info?.offset?.y ?: 0)
        }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                val scrolled = if (index > 0) {
                    80f + (-offset).toFloat()
                } else {
                    (-offset).toFloat()
                }
                collapseFraction((scrolled / 80f).coerceIn(0f, 1f))
                onScrollOffsetChange(scrolled)
            }
    }

    fun selectTab(tab: Tab) {
        val index = topBarTabs.indexOf(tab)
        if (index < 0) return

        selectedTab = tab
        onTabChange(tab)
        coroutineScope.launch {
            pagerState.animateScrollToPage(
                page = index,
                animationSpec = tween(durationMillis = 220)
            )
        }
    }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            BackHandler(enabled = selectedTab != defaultSelectedTab) {
                selectTab(defaultSelectedTab)
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface
                ) {
                    topBarButtons(selectedTab)
                }
            }

            if (showTabsRow) {
                LibraryTabsRow(
                    tabs = topBarTabs,
                    selectedTab = selectedTab,
                    onSelect = { selectTab(it) }
                )
            }

            HorizontalPager(
                state = pagerState,
                key = { page -> topBarTabs[page] },
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val tab = topBarTabs[page]
                val tabGridState = gridStateOf(tab)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val pageOffset = (pagerState.currentPage - page) +
                                    pagerState.currentPageOffsetFraction
                            alpha = 1f - (abs(pageOffset) * 0.8f).coerceIn(0f, 1f)
                            // Later chips live to the right, so content slides left.
                            translationX = -pageOffset * 24.dp.toPx()
                        }
                ) {
                    LazyVerticalGrid(
                        columns = gridCells(tab),
                        state = tabGridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                        horizontalArrangement = contentHorizontalArrangement,
                        verticalArrangement = contentVerticalArrangement
                    ) {
                        tabContent(tab)
                    }

                    val firstVisible = tabGridState.layoutInfo.visibleItemsInfo.firstOrNull()
                    val header = firstVisible?.let {
                        stickyHeaderForItem(tab, it.index)
                    }
                    val inlineHeaderHeightPx = with(density) { 42.dp.toPx() }

                    if (
                        header != null &&
                        (!header.isSectionStart ||
                                (firstVisible?.offset?.y ?: 0).toFloat() < -inlineHeaderHeightPx)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 20.dp, top = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = header.label,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 18.sp,
                                style = EditorialType.cardTitle
                            )
                        }
                    }

                    if (enableScrollbar) {
                        LibraryFastScroller(
                            state = tabGridState,
                            labelForIndex = { index ->
                                stickyHeaderForItem(tab, index)?.label
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(24.dp)
                                .padding(vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Thin draggable strip on the right edge. While dragging it shows the current section
 * letter/date in a bubble; the bubble disappears 1.5s after the finger lifts.
 */
@Composable
private fun LibraryFastScroller(
    state: LazyGridState,
    labelForIndex: (Int) -> String?,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var showBubble by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier) {
        val total = state.layoutInfo.totalItemsCount
        val visible = state.layoutInfo.visibleItemsInfo.size
        val firstIndex = state.firstVisibleItemIndex
        val maxIndex = (total - 1).coerceAtLeast(1)
        val fraction = (firstIndex.toFloat() / maxIndex).coerceIn(0f, 1f)
        val label = labelForIndex(firstIndex)
        val accent = MaterialTheme.colorScheme.primary

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(total, constraints.maxHeight) {
                    if (total <= 0) return@pointerInput

                    fun scrollTo(y: Float) {
                        val targetFraction = (y / size.height.toFloat()).coerceIn(0f, 1f)
                        val target = (targetFraction * (total - 1)).toInt().coerceIn(0, total - 1)
                        scope.launch {
                            state.scrollToItem(target)
                        }
                    }

                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            showBubble = true
                            scrollTo(offset.y)
                        },
                        onDragEnd = {
                            dragging = false
                            scope.launch {
                                delay(1500)
                                if (!dragging) showBubble = false
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch {
                                delay(1500)
                                if (!dragging) showBubble = false
                            }
                        }
                    ) { change, _ ->
                        scrollTo(change.position.y)
                    }
                }
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val stripWidth = 3.dp.toPx()
                val x = (size.width - stripWidth) / 2f
                val corner = stripWidth / 2f

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.10f),
                    topLeft = Offset(x, 0f),
                    size = Size(stripWidth, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
                )

                val thumbHeight = (size.height * (visible.toFloat() / total.coerceAtLeast(1)))
                    .coerceAtLeast(48.dp.toPx())
                    .coerceAtMost(size.height)
                val thumbTop = (size.height - thumbHeight) * fraction

                drawRoundRect(
                    color = accent.copy(alpha = if (dragging) 0.95f else 0.45f),
                    topLeft = Offset(x, thumbTop),
                    size = Size(stripWidth, thumbHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
                )
            }
        }

        if (showBubble && label != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-34).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 14.sp,
                    style = EditorialType.cardTitle
                )
            }
        }
    }
}

@Composable
private fun LibraryTabsRow(
    tabs: List<Tab>,
    selectedTab: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedTab, tabs) {
        val index = tabs.indexOf(selectedTab)
        if (index < 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isNotEmpty() && visible.none { it.index == index }) {
            listState.animateScrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = tabs,
            key = { _, tab -> tab.name }
        ) { _, tab ->
            val selected = tab == selectedTab
            val accent = MaterialTheme.colorScheme.primary

            Box(
                modifier = Modifier
                    .then(
                        if (selected) {
                            Modifier.shadow(
                                elevation = 10.dp,
                                shape = CircleShape,
                                ambientColor = accent,
                                spotColor = accent
                            )
                        } else Modifier
                    )
                    .clip(CircleShape)
                    .border(
                        width = 1.dp,
                        color = if (selected) {
                            accent.copy(alpha = 0.85f)
                        } else {
                            Color.White.copy(alpha = 0.14f)
                        },
                        shape = CircleShape
                    )
                    .background(
                        if (selected) accent.copy(alpha = 0.16f) else Color.Transparent
                    )
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 15.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(tab.titleResId),
                    style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 0.8.sp,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
