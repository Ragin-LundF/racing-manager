package io.github.raginlundf.racingmanager.api.results.models

import kotlinx.serialization.Serializable

@Serializable
data class HtmlReportResponseModel(
    val html: String,
    val filename: String,
)
