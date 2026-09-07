package com.nuvio.tv.domain.model

data class LiveChannelCategory(
    val id: Int,
    val name: String,
    val isAdult: Boolean
)

data class LiveChannel(
    val streamId: Int,
    val name: String,
    val iconUrl: String?,
    val epgChannelId: String?,
    val categoryId: Int,
    val number: Int?
)

data class LiveTvSnapshot(
    val categories: List<LiveChannelCategory>,
    val channels: List<LiveChannel>,
    val fetchedAtMillis: Long
)

data class EpgProgram(
    val title: String,
    val description: String?,
    val startEpoch: Long?,
    val endEpoch: Long?
)
