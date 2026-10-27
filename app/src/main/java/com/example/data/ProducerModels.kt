package com.example.data

data class ProducerShowSummary(
    val id: String,
    val title: String,
    val coverUrl: String,
    val feedUrl: String = "",
    val category: String = "Podcast",
    val trackCount: Int = 0,
    val isSubscribed: Boolean = false
)

data class ProducerInfo(
    val name: String,
    val avatarUrl: String = "",
    val shows: List<ProducerShowSummary> = emptyList(),
    val totalEpisodesCount: Int = 0,
    val primaryCategory: String = "Podcast Producer",
    val bio: String = ""
)

enum class SearchFilterTab(val label: String) {
    ALL("All"),
    PRODUCERS("Producers / Artists"),
    SHOWS("Shows"),
    EPISODES("Produced Episodes")
}
