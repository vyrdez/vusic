package com.feishin.mobile.data.remote.drive

import retrofit2.http.GET
import retrofit2.http.Query

/** Minimal Google Drive v3 API interface for account verification (`P12.2-C/E` extend with `files.list`). */
interface DriveApi {
    @GET("drive/v3/about")
    suspend fun getAbout(
        @Query("fields") fields: String = "user(displayName,emailAddress,photoLink)",
    ): DriveAboutResponse
}
