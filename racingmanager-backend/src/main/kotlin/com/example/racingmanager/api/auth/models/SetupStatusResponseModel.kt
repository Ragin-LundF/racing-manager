package com.example.racingmanager.api.auth.models

import kotlinx.serialization.Serializable

@Serializable
data class SetupStatusResponseModel(
    val firstRun: Boolean,
)
