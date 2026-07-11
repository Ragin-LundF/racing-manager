package com.example.racingmanager.api.auth.models

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponseModel(
    val code: String,
    val message: String,
)
