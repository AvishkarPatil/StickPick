package com.avishkar.stickpick.data.model

import com.google.gson.annotations.SerializedName

// Telegram API Models
data class TelegramResponse<T>(
    val ok: Boolean,
    val result: T?,
    val description: String?
)

data class TelegramStickerSet(
    val name: String,
    val title: String,
    @SerializedName("is_animated") val isAnimated: Boolean = false,
    @SerializedName("is_video") val isVideo: Boolean = false,
    val stickers: List<TelegramSticker>
)

data class TelegramSticker(
    @SerializedName("file_id") val fileId: String,
    @SerializedName("file_unique_id") val fileUniqueId: String,
    val width: Int,
    val height: Int,
    @SerializedName("is_animated") val isAnimated: Boolean = false,
    @SerializedName("is_video") val isVideo: Boolean = false,
    val emoji: String? = null
)

data class TelegramFile(
    @SerializedName("file_id") val fileId: String,
    @SerializedName("file_unique_id") val fileUniqueId: String,
    @SerializedName("file_size") val fileSize: Long? = null,
    @SerializedName("file_path") val filePath: String? = null
)

// Local Pack Models
data class StickerPack(
    val identifier: String,
    val name: String,
    val publisher: String,
    val trayImageFile: String,
    val stickers: List<Sticker>,
    val publisherEmail: String = "",
    val publisherWebsite: String = "",
    val privacyPolicyWebsite: String = "",
    val licenseAgreementWebsite: String = "",
    val imageDataVersion: String = "2",
    val avoidCache: Boolean = false,
    val animatedStickerPack: Boolean = false,
    val androidPlayStoreLink: String = "https://play.google.com/store/apps/details?id=com.avishkar.stickpick",
    val iosAppStoreLink: String = ""
)

data class Sticker(
    val imageFileName: String,
    val emojis: List<String> = listOf("😀"),
    val rawFilePath: String = "",
    val convertedFilePath: String = ""
)

data class StickerPackIndex(
    @SerializedName("android_play_store_link") val androidPlayStoreLink: String = "",
    @SerializedName("ios_app_store_link") val iosAppStoreLink: String = "",
    @SerializedName("sticker_packs") val stickerPacks: List<StickerPack>
)

// UI State Models
data class DownloadProgress(
    val packName: String = "",
    val totalStickers: Int = 0,
    val downloadedStickers: Int = 0,
    val currentSpeed: String = "",
    val isComplete: Boolean = false,
    val error: String? = null
) {
    val percentage: Float get() = if (totalStickers > 0) downloadedStickers.toFloat() / totalStickers else 0f
}

data class ConversionProgress(
    val totalStickers: Int = 0,
    val convertedStickers: Int = 0,
    val isComplete: Boolean = false,
    val error: String? = null
) {
    val percentage: Float get() = if (totalStickers > 0) convertedStickers.toFloat() / totalStickers else 0f
}
