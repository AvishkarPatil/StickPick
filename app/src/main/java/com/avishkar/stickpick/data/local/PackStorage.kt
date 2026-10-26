package com.avishkar.stickpick.data.local

import android.content.Context
import com.avishkar.stickpick.data.model.StickerPack
import com.avishkar.stickpick.data.model.StickerPackIndex
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class PackStorage(private val context: Context) {
    private val gson = Gson()

    private val packsDir get() = File(context.filesDir, "sticker_packs").also { it.mkdirs() }
    private val rawDir get() = File(context.filesDir, "stickers/raw").also { it.mkdirs() }
    private val convertedDir get() = File(context.filesDir, "stickers/converted").also { it.mkdirs() }
    private val indexFile get() = File(packsDir, "packs_index.json")

    companion object {
        private val lock = Any()
        @Volatile
        private var cachedPacks: List<StickerPack>? = null
        private val packMap = ConcurrentHashMap<String, StickerPack>()
        private var lastModified: Long = 0

        fun invalidateCache() {
            synchronized(lock) {
                cachedPacks = null
                packMap.clear()
                lastModified = 0
            }
        }
    }

    fun getRawDir(packId: String): File = File(rawDir, packId).also { it.mkdirs() }
    fun getConvertedDir(packId: String): File = File(convertedDir, packId).also { it.mkdirs() }

    fun loadPacks(): List<StickerPack> {
        if (!indexFile.exists()) return emptyList()
        val currentMod = indexFile.lastModified()
        val currentCache = cachedPacks
        if (currentCache != null && currentMod == lastModified) {
            return currentCache
        }
        return synchronized(lock) {
            val checkCache = cachedPacks
            if (checkCache != null && indexFile.lastModified() == lastModified) {
                checkCache
            } else {
                try {
                    val json = indexFile.readText()
                    val type = object : TypeToken<List<StickerPack>>() {}.type
                    val list: List<StickerPack> = gson.fromJson(json, type) ?: emptyList()
                    packMap.clear()
                    list.forEach { packMap[it.identifier] = it }
                    lastModified = indexFile.lastModified()
                    cachedPacks = list
                    list
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
    }

    fun getPack(identifier: String): StickerPack? {
        if (packMap.isEmpty() || cachedPacks == null) {
            loadPacks()
        }
        return packMap[identifier]
    }

    fun savePacks(packs: List<StickerPack>) {
        synchronized(lock) {
            indexFile.writeText(gson.toJson(packs))
            lastModified = indexFile.lastModified()
            cachedPacks = packs
            packMap.clear()
            packs.forEach { packMap[it.identifier] = it }
        }
    }

    fun savePack(pack: StickerPack) {
        val packs = loadPacks().toMutableList()
        val idx = packs.indexOfFirst { it.identifier == pack.identifier }
        if (idx >= 0) packs[idx] = pack else packs.add(pack)
        savePacks(packs)
    }

    fun deletePack(identifier: String) {
        val packs = loadPacks().filter { it.identifier != identifier }
        savePacks(packs)
        File(rawDir, identifier).deleteRecursively()
        File(convertedDir, identifier).deleteRecursively()
    }

    fun generateContentProviderIndex(packs: List<StickerPack>): StickerPackIndex {
        return StickerPackIndex(stickerPacks = packs)
    }

    fun clearAll() {
        invalidateCache()
        rawDir.deleteRecursively()
        convertedDir.deleteRecursively()
        indexFile.delete()
    }
}
