package com.example.racingmanager.api.models

import kotlinx.serialization.Serializable

@Serializable
data class ProblemDetailModel(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String? = null,
)
