package com.feishin.mobile.data.remote.drive

import kotlinx.serialization.Serializable

/** Response from `GET /drive/v3/about` used to verify Google Drive OAuth credentials. */
@Serializable
data class DriveAboutResponse(
    val user: DriveUserDto? = null,
)

@Serializable
data class DriveUserDto(
    val displayName: String? = null,
    val emailAddress: String? = null,
    val photoLink: String? = null,
)
