package com.feishin.mobile.data.remote.drive

import java.time.OffsetDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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

/** Accepts either JSON string `"12345"` (Drive v3 default) or unquoted JSON number `12345`. */
object DriveFlexibleStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DriveFlexibleString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String? {
        if (decoder is JsonDecoder) {
            val el = decoder.decodeJsonElement()
            return (el as? JsonPrimitive)?.contentOrNull
        }
        return decoder.decodeString()
    }
}

/** Parses an RFC 3339 timestamp (e.g. `2026-11-28T12:15:32.123Z`) into epoch millis, or `0L` if invalid. */
fun parseDriveRfc3339EpochMs(raw: String?): Long {
    val cleaned = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return 0L
    return runCatching { OffsetDateTime.parse(cleaned).toInstant().toEpochMilli() }.getOrDefault(0L)
}

@Serializable
data class DriveFileDto(
    val id: String,
    val name: String = "",
    val mimeType: String = "",
    @Serializable(with = DriveFlexibleStringSerializer::class)
    val size: String? = null,
    val modifiedTime: String? = null,
    val parents: List<String> = emptyList(),
    val trashed: Boolean = false,
    val md5Checksum: String? = null,
) {
    val sizeBytes: Long
        get() = size?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L

    val modifiedTimeMs: Long
        get() = parseDriveRfc3339EpochMs(modifiedTime)
}

@Serializable
data class DriveFileListResponse(
    val nextPageToken: String? = null,
    val files: List<DriveFileDto> = emptyList(),
)
