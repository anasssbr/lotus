package com.dn0ne.player.app.presentation.components.topbar

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import kotlin.math.abs

/**
 * Library header: a fixed title row (provided through [topBarButtons]), a row of outlined
 * filter chips and the paged content underneath.
 *
 * The chip row replaces the old collapsible tab titles, but it still drives the same pager,
 * so swiping between sections keeps working.
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
    tabContent: LazyGridScope.(tab: Tab) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

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

    LaunchedEffect(selectedTab, topBarTabs) {
        snapshotFlow {
            gridStateOf(selectedTab).layoutInfo.visibleItemsInfo.firstOrNull()?.offset?.y ?: 0
        }
            .distinctUntilChanged()
            .collect { offset ->
                val scrolled = (-offset).toFloat()
                collapseFraction((scrolled / 240f).coerceIn(0f, 1f))
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
                            translationX = pageOffset * 24.dp.toPx()
                        }
                ) {
                    LazyVerticalGridScrollbar(
                        state = tabGridState,
                        settings = ScrollbarSettings(
                            enabled = enableScrollbar,
                            thumbUnselectedColor = MaterialTheme.colorScheme.surfaceContainer,
                            thumbSelectedColor = MaterialTheme.colorScheme.primaryContainer,
                        )
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

                            item(
                                span = {
                                    GridItemSpan(this.maxLineSpan)
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                )
                            }
                        }
                    }
                }
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
        contentPadding = PaddingValues(horizontal = 16.dp),
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
