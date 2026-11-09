package com.dn0ne.player.app.presentation.components.trackinfo

import com.dn0ne.player.app.domain.metadata.MetadataSearchResult
import com.dn0ne.player.app.domain.track.Track

data class BulkTagFixState(
    val isScanning: Boolean = false,
    val completedCount: Int = 0,
    val suggestions: List<BulkTagFixSuggestion> = emptyList()
)

data class BulkTagFixSuggestion(
    val track: Track,
    val status: BulkTagFixStatus = BulkTagFixStatus.Pending,
    val searchResult: MetadataSearchResult? = null
)

enum class BulkTagFixStatus {
    Pending,
    Searching,
    Found,
    NotFound,
    Skipped
}
