package com.avishkar.stickpick.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.avishkar.stickpick.conversion.ConversionEngine
import com.avishkar.stickpick.data.model.*
import com.avishkar.stickpick.data.repository.StickerRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = StickerRepository(app)
    private val converter = ConversionEngine(app)

    val botToken = repo.prefs.botToken.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val authorName = repo.prefs.authorName.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val packNamingPattern = repo.prefs.packNamingPattern.stateIn(viewModelScope, SharingStarted.Eagerly, "{name}_by_{author}")
    val onboardingComplete = repo.prefs.onboardingComplete.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady
    val autoSplit = repo.prefs.autoSplit.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val losslessConversion = repo.prefs.losslessConversion.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val packLimit = repo.prefs.packLimit.stateIn(viewModelScope, SharingStarted.Eagerly, 30)
    val themeMode = repo.prefs.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, "system")
    val whatsappBusiness = repo.prefs.whatsappBusiness.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _stickerSet = MutableStateFlow<TelegramStickerSet?>(null)
    val stickerSet: StateFlow<TelegramStickerSet?> = _stickerSet

    private val _downloadedFiles = MutableStateFlow<List<File>>(emptyList())
    val downloadedFiles: StateFlow<List<File>> = _downloadedFiles

    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress

    private val _conversionProgress = MutableStateFlow(ConversionProgress())
    val conversionProgress: StateFlow<ConversionProgress> = _conversionProgress

    private val _savedPacks = MutableStateFlow<List<StickerPack>>(emptyList())
    val savedPacks: StateFlow<List<StickerPack>> = _savedPacks

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentPackId = MutableStateFlow("")
    private var isDownloading = false
    private var isConverting = false

    init {
        loadSavedPacks()
        viewModelScope.launch {
            repo.prefs.onboardingComplete.first()
            _isReady.value = true
        }
    }

    fun completeOnboarding(token: String, author: String, pattern: String) {
        viewModelScope.launch {
            repo.prefs.saveBotToken(token)
            repo.prefs.saveAuthorName(author)
            repo.prefs.savePackNamingPattern(pattern)
            repo.prefs.setOnboardingComplete()
        }
    }

    fun updateSettings(token: String, author: String, pattern: String, autoSplit: Boolean, lossless: Boolean) {
        viewModelScope.launch {
            repo.prefs.saveBotToken(token)
            repo.prefs.saveAuthorName(author)
            repo.prefs.savePackNamingPattern(pattern)
            repo.prefs.saveAutoSplit(autoSplit)
            repo.prefs.saveLosslessConversion(lossless)
        }
    }

    fun fetchStickerSet(input: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repo.fetchStickerSet(input).fold(
                onSuccess = { set ->
                    _stickerSet.value = set
                    _currentPackId.value = set.name
                    _isLoading.value = false
                },
                onFailure = { e ->
                    _error.value = e.message
                    _isLoading.value = false
                }
            )
        }
    }

    fun downloadAllStickers() {
        if (isDownloading) return
        val set = _stickerSet.value ?: return
        isDownloading = true

        viewModelScope.launch {
            _downloadProgress.value = DownloadProgress(
                packName = set.title,
                totalStickers = set.stickers.size
            )

            val results = arrayOfNulls<File>(set.stickers.size)
            var completed = 0

            set.stickers.forEachIndexed { index, sticker ->
                repo.downloadSticker(sticker, set.name, index).onSuccess { results[index] = it }
                completed++
                _downloadProgress.value = _downloadProgress.value.copy(downloadedStickers = completed)
            }

            // Single update — no flickering
            _downloadedFiles.value = results.filterNotNull()
            _downloadProgress.value = _downloadProgress.value.copy(isComplete = true)
            isDownloading = false
        }
    }

    fun removeSticker(index: Int) {
        val current = _downloadedFiles.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _downloadedFiles.value = current
        }
    }

    fun addExternalFile(file: File) {
        _downloadedFiles.value = _downloadedFiles.value + file
    }

    fun moveSticker(from: Int, to: Int) {
        val current = _downloadedFiles.value.toMutableList()
        if (from in current.indices && to in current.indices) {
            val item = current.removeAt(from)
            current.add(to, item)
            _downloadedFiles.value = current
        }
    }

    fun convertAllStickers() {
        if (isConverting) return
        val files = _downloadedFiles.value
        val packId = _currentPackId.value
        if (files.isEmpty() || packId.isBlank()) return
        isConverting = true

        viewModelScope.launch {
            _conversionProgress.value = ConversionProgress(totalStickers = files.size)
            val results = mutableListOf<Pair<Int, Boolean>>() // index to isAnimated
            var done = 0

            files.forEachIndexed { index, file ->
                val result = converter.convertSticker(file, packId, "sticker_$index")
                done++
                result.onSuccess { results.add(index to it.isAnimated) }
                _conversionProgress.value = _conversionProgress.value.copy(convertedStickers = done)
            }

            val firstSuccess = results.firstOrNull()?.first
            if (firstSuccess != null) {
                converter.createTrayImage(files[firstSuccess], packId)
            }

            buildAndSavePacks(results)
            _conversionProgress.value = _conversionProgress.value.copy(isComplete = true)
            isConverting = false
        }
    }

    private data class StickerWithMeta(val sticker: Sticker, val isAnimated: Boolean)

    private suspend fun buildAndSavePacks(results: List<Pair<Int, Boolean>>) {
        val set = _stickerSet.value ?: return
        val packId = _currentPackId.value
        val author = authorName.value.ifBlank { "StickPick User" }
        val convertedDir = repo.storage.getConvertedDir(packId)
        val limit = packLimit.value

        val allStickers = results.map { (index, isAnimated) ->
            val file = _downloadedFiles.value[index]
            val emoji = set.stickers.getOrNull(index)?.emoji
            StickerWithMeta(
                Sticker(
                    imageFileName = "sticker_$index.webp",
                    emojis = listOfNotNull(emoji ?: "\uD83D\uDE00"),
                    rawFilePath = file.absolutePath,
                    convertedFilePath = File(convertedDir, "sticker_$index.webp").absolutePath
                ),
                isAnimated
            )
        }

        if (allStickers.size < 3) return

        // Validate every sticker file exists and is valid before building packs
        val validStickers = allStickers.filter { sm ->
            val file = File(sm.sticker.convertedFilePath)
            file.exists() && file.length() > 200
        }

        // Separate animated and static stickers into different packs
        val animatedStickers = validStickers.filter { it.isAnimated }
        val staticStickers = validStickers.filter { !it.isAnimated }

        var packIndex = 0

        // WhatsApp limits: animated=30 max, static=30 max
        val animLimit = minOf(limit, 30)
        val staticLimit = limit

        // Build animated packs
        if (animatedStickers.size >= 3) {
            val animChunks = animatedStickers.chunked(animLimit)
            animChunks.forEach { chunk ->
                packIndex++
                val suffix = if (animChunks.size + (if (staticStickers.size >= 3) 1 else 0) > 1) "_$packIndex" else ""
                saveSinglePack(chunk, suffix, packId, set.title, author, convertedDir, true)
            }
        }

        // Build static packs (animatedStickerPack=false, 100KB limit per sticker)
        if (staticStickers.size >= 3) {
            val staticChunks = staticStickers.chunked(staticLimit)
            staticChunks.forEach { chunk ->
                packIndex++
                val suffix = "_s$packIndex"
                saveSinglePack(chunk, suffix, packId, set.title + " (Static)", author, convertedDir, false)
            }
        }

        // If all stickers are same type and < 3 of the other, just make one pack type
        if (animatedStickers.size < 3 && staticStickers.size < 3 && validStickers.size >= 3) {
            // Mixed but too few of each — try as static pack (safer)
            val chunks = validStickers.chunked(animLimit)
            chunks.forEachIndexed { idx, chunk ->
                val suffix = if (chunks.size > 1) "_${idx + 1}" else ""
                saveSinglePack(chunk, suffix, packId, set.title, author, convertedDir, false)
            }
        }

        loadSavedPacks()
    }

    private suspend fun saveSinglePack(
        chunk: List<StickerWithMeta>,
        suffix: String,
        packId: String,
        name: String,
        author: String,
        convertedDir: File,
        animated: Boolean
    ) {
        val packIdentifier = "${packId}${suffix}"
        val trayFileName = "tray_${packIdentifier}.png"

        // Generate tray — always save to base convertedDir
        val firstRawPath = chunk.first().sticker.rawFilePath
        if (firstRawPath.isNotEmpty()) {
            converter.createTrayImage(File(firstRawPath), packId)
            // Rename generic tray to pack-specific name, keep in same base dir
            val genericTray = File(convertedDir, "tray_${packId}.png")
            val specificTray = File(convertedDir, trayFileName)
            if (genericTray.exists() && genericTray.absolutePath != specificTray.absolutePath) {
                genericTray.copyTo(specificTray, overwrite = true)
            }
        }

        // Verify tray exists in base dir
        val trayFile = File(convertedDir, trayFileName)
        if (!trayFile.exists()) {
            val genericTray = File(convertedDir, "tray_${packId}.png")
            if (genericTray.exists()) genericTray.copyTo(trayFile, overwrite = true)
        }

        if (chunk.size < 3) return

        val pack = StickerPack(
            identifier = packIdentifier,
            name = name,
            publisher = author,
            trayImageFile = trayFileName,
            stickers = chunk.map { it.sticker },
            animatedStickerPack = animated
        )
        repo.storage.savePack(pack)
    }

    fun loadSavedPacks() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val packs = repo.storage.loadPacks()
            _savedPacks.value = packs
        }
    }
    fun deletePack(id: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repo.storage.deletePack(id)
            val packs = repo.storage.loadPacks()
            _savedPacks.value = packs
        }
    }

    fun updatePackName(id: String, newName: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val packs = repo.storage.loadPacks().toMutableList()
            val idx = packs.indexOfFirst { it.identifier == id }
            if (idx >= 0) {
                packs[idx] = packs[idx].copy(name = newName)
                repo.storage.savePacks(packs)
                _savedPacks.value = packs
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repo.storage.clearAll()
            _savedPacks.value = emptyList()
        }
    }
    fun clearError() { _error.value = null }
    fun updatePackLimit(limit: Int) { viewModelScope.launch { repo.prefs.savePackLimit(limit) } }
    fun updateThemeMode(mode: String) { viewModelScope.launch { repo.prefs.saveThemeMode(mode) } }
    fun updateWhatsAppBusiness(enabled: Boolean) { viewModelScope.launch { repo.prefs.saveWhatsAppBusiness(enabled) } }

    fun resetWorkflow() {
        _stickerSet.value = null
        _downloadedFiles.value = emptyList()
        _downloadProgress.value = DownloadProgress()
        _conversionProgress.value = ConversionProgress()
        _currentPackId.value = ""
        isDownloading = false
        isConverting = false
    }
}
