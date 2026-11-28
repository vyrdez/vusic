package com.feishin.mobile.data.remote.drive

import com.feishin.mobile.core.network.ProfileIdTag
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Tag

/** Google Drive v3 API interface for account verification and library/folder file listing. */
interface DriveApi {
    @GET("drive/v3/about")
    suspend fun getAbout(
        @Query("fields") fields: String = "user(displayName,emailAddress,photoLink)",
    ): DriveAboutResponse

    @GET("drive/v3/files")
    suspend fun listFiles(
        @Query("q") q: String? = null,
        @Query("fields") fields: String = DEFAULT_FILE_FIELDS,
        @Query("pageSize") pageSize: Int = DEFAULT_PAGE_SIZE,
        @Query("pageToken") pageToken: String? = null,
        @Query("orderBy") orderBy: String? = null,
        @Tag profileIdTag: ProfileIdTag? = null,
    ): DriveFileListResponse

    companion object {
        const val DEFAULT_PAGE_SIZE = 1000
        const val DEFAULT_FILE_FIELDS =
            "nextPageToken,files(id,name,mimeType,size,modifiedTime,parents,trashed,md5Checksum)"
    }
}
