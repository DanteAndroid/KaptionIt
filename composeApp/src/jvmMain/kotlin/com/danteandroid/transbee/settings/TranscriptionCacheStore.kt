package com.danteandroid.transbee.settings

import com.danteandroid.transbee.settings.TranscriptionCacheStore.MAX_CACHE_ENTRIES
import com.danteandroid.transbee.whisper.WhisperParseResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

@Serializable
data class TranscriptionCacheKeyDto(
    val fileKey: String,
    val fileSize: Long,
    val fileLastModified: Long,
    val whisperModel: String,
    val whisperLanguage: String,
    val whisperVadEnabled: Boolean,
    val whisperThreadCount: Int,
    val glossaryTermsFingerprint: String = "",
    /** VAD 单段时长、-ml 等策略变更时 bump，避免命中旧缓存长句 */
    val whisperSegmentPolicy: String = "",
)

@Serializable
private data class TranscriptionCacheFile(
    val entries: Map<String, TranscriptionCacheEntry> = emptyMap(),
    /** 由旧到新的使用顺序；仅含 `entries` 中存在的 key，长度不超过 [MAX_CACHE_ENTRIES] */
    val lruHashes: List<String> = emptyList(),
)

@Serializable
private data class TranscriptionCacheEntry(
    val key: TranscriptionCacheKeyDto,
    val result: WhisperParseResult,
)

object TranscriptionCacheStore {
    private const val MAX_CACHE_ENTRIES = 5

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private fun cacheFile(): File = File(ToolingSettingsStore.appDataDir(), "transcription_cache.json")

    private fun hashKey(key: TranscriptionCacheKeyDto): String {
        val payload = json.encodeToString(key)
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun loadRaw(): TranscriptionCacheFile? {
        val f = cacheFile()
        if (!f.isFile) return null
        return try {
            json.decodeFromString<TranscriptionCacheFile>(f.readText())
        } catch (_: Exception) {
            null
        }
    }

    /** 旧数据无 lru 或条目过多时，裁到至多 [MAX_CACHE_ENTRIES] 条 */
    private fun normalize(data: TranscriptionCacheFile): TranscriptionCacheFile {
        val entries = data.entries.toMutableMap()
        if (entries.isEmpty()) return TranscriptionCacheFile()
        val lru = data.lruHashes.filter { it in entries }.toMutableList()
        for (k in entries.keys) {
            if (k !in lru) lru.add(k)
        }
        while (entries.size > MAX_CACHE_ENTRIES) {
            val victim = lru.removeAt(0)
            entries.remove(victim)
        }
        lru.removeAll { it !in entries }
        for (k in entries.keys) {
            if (k !in lru) lru.add(k)
        }
        while (lru.size > MAX_CACHE_ENTRIES) {
            val victim = lru.removeAt(0)
            entries.remove(victim)
        }
        return TranscriptionCacheFile(entries, lru)
    }

    private fun persist(data: TranscriptionCacheFile) {
        val normalized = normalize(data)
        if (normalized.entries.isEmpty()) {
            runCatching { cacheFile().delete() }
            return
        }
        ToolingSettingsStore.appDataDir().mkdirs()
        cacheFile().writeText(json.encodeToString(normalized))
    }

    fun get(key: TranscriptionCacheKeyDto): WhisperParseResult? {
        synchronized(this) {
            val raw = loadRaw() ?: return null
            val data = normalize(raw)
            val h = hashKey(key)
            val entry = data.entries[h] ?: return null
            if (entry.key != key) return null
            val lru = data.lruHashes.toMutableList()
            lru.remove(h)
            lru.add(h)
            persist(TranscriptionCacheFile(data.entries, lru))
            return entry.result
        }
    }

    fun put(key: TranscriptionCacheKeyDto, result: WhisperParseResult) {
        synchronized(this) {
            val data = loadRaw() ?: TranscriptionCacheFile()
            val normalized = normalize(data)
            val entries = normalized.entries.toMutableMap()
            val h = hashKey(key)
            entries[h] = TranscriptionCacheEntry(key, result)
            val lru = normalized.lruHashes.toMutableList()
            lru.remove(h)
            lru.add(h)
            while (lru.size > MAX_CACHE_ENTRIES) {
                val victim = lru.removeAt(0)
                entries.remove(victim)
            }
            persist(TranscriptionCacheFile(entries, lru))
        }
    }

    fun clearAll() {
        synchronized(this) {
            runCatching { cacheFile().delete() }
        }
    }
}
