package com.avishkar.stickpick.whatsapp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.avishkar.stickpick.data.local.PackStorage
import com.avishkar.stickpick.data.model.StickerPack
import java.io.File

class StickerContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.avishkar.stickpick.stickercontentprovider"

        // Column names MUST match official WhatsApp sticker SDK exactly
        const val STICKER_PACK_IDENTIFIER = "sticker_pack_identifier"
        const val STICKER_PACK_NAME = "sticker_pack_name"
        const val STICKER_PACK_PUBLISHER = "sticker_pack_publisher"
        const val STICKER_PACK_ICON = "sticker_pack_icon"
        const val ANDROID_APP_DOWNLOAD_LINK = "android_play_store_link"
        const val IOS_APP_DOWNLOAD_LINK = "ios_app_download_link"
        const val PUBLISHER_EMAIL = "sticker_pack_publisher_email"
        const val PUBLISHER_WEBSITE = "sticker_pack_publisher_website"
        const val PRIVACY_POLICY_WEBSITE = "sticker_pack_privacy_policy_website"
        const val LICENSE_AGREEMENT_WEBSITE = "sticker_pack_license_agreement_website"
        const val IMAGE_DATA_VERSION = "image_data_version"
        const val AVOID_CACHE = "whatsapp_will_not_cache_stickers"
        const val ANIMATED_STICKER_PACK = "animated_sticker_pack"

        const val STICKER_FILE_NAME = "sticker_file_name"
        const val STICKER_FILE_EMOJI = "sticker_emoji"

        private const val METADATA = "metadata"
        private const val STICKERS = "stickers"
        private const val STICKERS_ASSET = "stickers_asset"

        private const val METADATA_CODE = 1
        private const val METADATA_CODE_FOR_SINGLE_PACK = 2
        private const val STICKERS_CODE = 3
        private const val STICKERS_ASSET_CODE = 4
        private const val STICKER_PACK_TRAY_ICON_CODE = 5
    }

    private lateinit var packStorage: PackStorage
    private val matcher = UriMatcher(UriMatcher.NO_MATCH)

    override fun onCreate(): Boolean {
        packStorage = PackStorage(context!!)
        matcher.addURI(AUTHORITY, METADATA, METADATA_CODE)
        matcher.addURI(AUTHORITY, "$METADATA/*", METADATA_CODE_FOR_SINGLE_PACK)
        matcher.addURI(AUTHORITY, "$STICKERS/*", STICKERS_CODE)
        matcher.addURI(AUTHORITY, "$STICKERS_ASSET/*/tray.png", STICKER_PACK_TRAY_ICON_CODE)
        matcher.addURI(AUTHORITY, "$STICKERS_ASSET/*/*.png", STICKER_PACK_TRAY_ICON_CODE)
        // High-speed static wildcard matching: eliminates 11,000+ repetitive UriMatcher loop registrations
        matcher.addURI(AUTHORITY, "$STICKERS_ASSET/*/*", STICKERS_ASSET_CODE)
        return true
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? {
        return when (matcher.match(uri)) {
            METADATA_CODE -> getPacksCursor(uri)
            METADATA_CODE_FOR_SINGLE_PACK -> getSinglePackCursor(uri)
            STICKERS_CODE -> getStickersCursor(uri)
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val matchCode = matcher.match(uri)
        if (matchCode == STICKERS_ASSET_CODE || matchCode == STICKER_PACK_TRAY_ICON_CODE) {
            return getImageAsset(uri)
        }
        return null
    }

    override fun openTypedAssetFile(
        uri: Uri,
        mimeTypeFilter: String,
        opts: android.os.Bundle?
    ): AssetFileDescriptor? {
        return openAssetFile(uri, "r")
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val matchCode = matcher.match(uri)
        if (matchCode == STICKERS_ASSET_CODE || matchCode == STICKER_PACK_TRAY_ICON_CODE) {
            return getFileDescriptor(uri)
        }
        return null
    }

    private fun getImageAsset(uri: Uri): AssetFileDescriptor? {
        val pathSegments = uri.pathSegments
        if (pathSegments.size != 3) return null
        val identifier = pathSegments[1]
        val fileName = pathSegments[2]
        val file = resolveFile(identifier, fileName) ?: return null
        return AssetFileDescriptor(
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
            0, file.length()
        )
    }

    private fun getFileDescriptor(uri: Uri): ParcelFileDescriptor? {
        val pathSegments = uri.pathSegments
        if (pathSegments.size != 3) return null
        val identifier = pathSegments[1]
        val fileName = pathSegments[2]
        val file = resolveFile(identifier, fileName) ?: return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun resolveFile(identifier: String, fileName: String): File? {
        if (identifier.contains("..") || fileName.contains("..") || identifier.contains("/") || fileName.contains("/")) {
            Log.w("StickerCP", "Rejected invalid path traversal: $identifier/$fileName")
            return null
        }

        val dir = packStorage.getConvertedDir(identifier)

        // Special handling for tray icon requests
        if (fileName.contains("tray") || fileName.endsWith(".png") || fileName == "$identifier.png") {
            for (candidate in listOf("tray.png", "tray_icon.png", fileName, "tray.webp", "tray_icon.webp")) {
                val f = File(dir, candidate)
                if (f.exists()) return f
            }
        }

        // 1. Try exact pack dir (instant O(1))
        var file = File(dir, fileName)
        if (file.exists()) return file

        // 2. For split packs, try base pack dir
        val lastUnderscore = identifier.lastIndexOf('_')
        if (lastUnderscore > 0) {
            val baseId = identifier.substring(0, lastUnderscore)
            val baseDir = packStorage.getConvertedDir(baseId)
            if (fileName.contains("tray") || fileName.endsWith(".png") || fileName == "$identifier.png") {
                for (candidate in listOf("tray.png", "tray_icon.png", fileName, "tray.webp", "tray_icon.webp")) {
                    val f = File(baseDir, candidate)
                    if (f.exists()) return f
                }
            }
            file = File(baseDir, fileName)
            if (file.exists()) return file
        }

        Log.w("StickerCP", "File not found: $identifier/$fileName")
        return null
    }

    private val metadataColumns = arrayOf(
        STICKER_PACK_IDENTIFIER, STICKER_PACK_NAME, STICKER_PACK_PUBLISHER,
        STICKER_PACK_ICON, ANDROID_APP_DOWNLOAD_LINK, IOS_APP_DOWNLOAD_LINK,
        PUBLISHER_EMAIL, PUBLISHER_WEBSITE, PRIVACY_POLICY_WEBSITE,
        LICENSE_AGREEMENT_WEBSITE, IMAGE_DATA_VERSION, AVOID_CACHE,
        ANIMATED_STICKER_PACK
    )

    private fun getPacksCursor(uri: Uri): Cursor {
        val cursor = MatrixCursor(metadataColumns)
        packStorage.loadPacks().forEach { addPackRow(cursor, it) }
        cursor.setNotificationUri(context!!.contentResolver, uri)
        return cursor
    }

    private fun getSinglePackCursor(uri: Uri): Cursor {
        val identifier = uri.lastPathSegment
        val cursor = MatrixCursor(metadataColumns)
        if (identifier != null) {
            packStorage.getPack(identifier)?.let { addPackRow(cursor, it) }
        }
        cursor.setNotificationUri(context!!.contentResolver, uri)
        return cursor
    }

    private fun addPackRow(cursor: MatrixCursor, pack: StickerPack) {
        val builder = cursor.newRow()
        builder.add(pack.identifier ?: "")
        builder.add(pack.name ?: "")
        builder.add(pack.publisher ?: "")
        builder.add(pack.trayImageFile ?: "")
        val storeLink = pack.androidPlayStoreLink?.ifEmpty { "https://play.google.com/store/apps/details?id=com.avishkar.stickpick" } ?: "https://play.google.com/store/apps/details?id=com.avishkar.stickpick"
        builder.add(storeLink)
        builder.add(pack.iosAppStoreLink ?: "")
        builder.add(pack.publisherEmail ?: "")
        builder.add(pack.publisherWebsite ?: "")
        builder.add(pack.privacyPolicyWebsite ?: "")
        builder.add(pack.licenseAgreementWebsite ?: "")
        builder.add(pack.imageDataVersion?.ifEmpty { "2" } ?: "2")
        builder.add(if (pack.avoidCache == true) 1 else 0)
        builder.add(if (pack.animatedStickerPack == true) 1 else 0)
    }

    private fun getStickersCursor(uri: Uri): Cursor {
        val identifier = uri.lastPathSegment
        val cursor = MatrixCursor(arrayOf(STICKER_FILE_NAME, STICKER_FILE_EMOJI))
        if (identifier != null) {
            packStorage.getPack(identifier)?.stickers?.forEach { sticker ->
                val emojisStr = sticker.emojis?.joinToString(",")?.ifEmpty { "😀" } ?: "😀"
                cursor.addRow(arrayOf(sticker.imageFileName ?: "", emojisStr))
            }
        }
        cursor.setNotificationUri(context!!.contentResolver, uri)
        return cursor
    }

    override fun getType(uri: Uri): String {
        return when (matcher.match(uri)) {
            METADATA_CODE -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$METADATA"
            METADATA_CODE_FOR_SINGLE_PACK -> "vnd.android.cursor.item/vnd.$AUTHORITY.$METADATA"
            STICKERS_CODE -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$STICKERS"
            STICKERS_ASSET_CODE -> {
                val last = uri.lastPathSegment ?: ""
                if (last.contains("tray") || last.endsWith(".png")) "image/png" else "image/webp"
            }
            STICKER_PACK_TRAY_ICON_CODE -> "image/png"
            else -> "application/octet-stream"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
