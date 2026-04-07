package com.danteandroid.transbee.feishu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.io.InputStreamReader
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Properties

/**
 * 飞书多维表格（Bitable）预置 Key 分发：串行获取 token → 解析表 ID → 查询待领取记录 → 标记已领取。
 * 同一进程内通过 [Mutex] 串行化，降低并发重复领取概率；跨进程/多实例仍需表格侧约束或业务幂等。
 */
object FeishuKeyManager {

    private val props by lazy { loadKeyProperties() }

    private val appId: String by lazy { req("appId") }
    private val appSecret: String by lazy { req("appSecret") }

    private fun loadKeyProperties(): Properties {
        val stream =
            FeishuKeyManager::class.java.classLoader?.getResourceAsStream("key.properties")
                ?: error(
                    "未找到 key.properties。请将 composeApp/src/jvmMain/resources/key.properties.example " +
                        "复制为 key.properties 并填写 appId、appSecret。",
                )
        return stream.use {
            Properties().apply { load(InputStreamReader(it, StandardCharsets.UTF_8)) }
        }
    }

    private fun req(key: String): String {
        val v = props.getProperty(key)?.trim()
        if (v.isNullOrEmpty()) error("key.properties 缺少或为空: $key")
        return v
    }

    /** 多维表格 app_token（一般为「分享」链接中 `base/` 后的一段；若 wiki 链接不可用请替换为 base 链接中的 token） */
    private const val BITABLE_APP_TOKEN = "VQ5RwAqgnimIADk25tPcMzVHnvc"

    /**
     * 数据表 ID：与 wiki/分享链接中 `table=` 参数一致（如 `...?table=tblxxx`）。
     * 非空时直接使用，不再依赖按表名匹配（接口返回的 name 可能与界面不一致）。
     */
    private const val BITABLE_TABLE_ID = "tbl4VHqbfWGDNEvC"

    private const val TABLE_NAME = "Transbee"
    private const val FIELD_STATUS = "status"
    /** 与多维表格列名一致（界面为「Key」）；旧表曾用 key_content */
    private const val FIELD_KEY = "Key"
    private const val STATUS_PENDING = "待领取"
    private const val STATUS_CLAIMED = "已领取"

    /** 飞书 Bitable 字段类型：3=单选（筛选/更新须用选项 id） */
    private const val FIELD_TYPE_SINGLE_SELECT = 3

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val requestTimeout: Duration = Duration.ofSeconds(60)

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val apiMutex = Mutex()

    @Volatile
    private var cachedTenantToken: String? = null

    @Volatile
    private var tenantTokenExpireAtEpochMs: Long = 0L

    @Volatile
    private var cachedTableId: String? = null

    @Volatile
    private var cachedStatusBindingTableId: String? = null

    @Volatile
    private var cachedStatusBinding: StatusFieldBinding? = null

    /**
     * 领取一条预置 Key：查询 Status 为「待领取」的一条记录，更新为「已领取」后返回 Key 列内容。
     * @throws NoAvailableKeyException 无待领取记录
     * @throws FeishuApiException 飞书业务错误（body 内 code != 0）
     */
    suspend fun claimPresetKey(): String = apiMutex.withLock {
        withContext(Dispatchers.IO) {
            val token = obtainTenantAccessTokenInternal()
            val tableId = resolveTableIdCached(token)
            val statusBinding = resolveStatusFieldBinding(token, tableId)
            val (recordId, keyContent) = searchOnePendingRecord(token, tableId, statusBinding)
            updateRecordClaimed(token, tableId, recordId, statusBinding)
            keyContent
        }
    }

    /**
     * 在 VIP 列中查找包含 [deviceId] 的记录；若存在则取匹配结果中**最后一条**的 Key 列文本。
     */
    suspend fun verifyPurchaseAndFetchKey(deviceId: String): String = apiMutex.withLock {
        withContext(Dispatchers.IO) {
            val token = obtainTenantAccessTokenInternal()
            val tableId = resolveTableIdCached(token)
            val fields = fetchAllTableFields(token, tableId)
            val vipField = findFieldByNames(fields, listOf("VIP", "vip")) ?: throw IllegalStateException("未找到 VIP 列")
            val keyField = findFieldByNames(fields, listOf("Key", "key", "KEY")) ?: throw IllegalStateException("未找到 Key 列")
            val all = searchAllRecordsVipContains(token, tableId, vipField, deviceId)
            if (all.isEmpty()) {
                throw PurchaseNotVerifiedException()
            }
            val last = all.last()
            val keyText = extractFieldText(last.fields, keyField.field_name)
            if (keyText.isBlank()) {
                throw FeishuApiException(null, "匹配到记录但 Key 列为空")
            }
            keyText
        }
    }

    fun clearCaches() {
        cachedTenantToken = null
        tenantTokenExpireAtEpochMs = 0L
        cachedTableId = null
        cachedStatusBindingTableId = null
        cachedStatusBinding = null
    }

    private fun obtainTenantAccessTokenInternal(): String {
        val now = System.currentTimeMillis()
        val marginMs = 120_000L
        val cached = cachedTenantToken
        if (cached != null && now < tenantTokenExpireAtEpochMs - marginMs) {
            return cached
        }
        val body = json.encodeToString(
            InternalTokenRequest(app_id = appId, app_secret = appSecret),
        )
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"))
            .timeout(requestTimeout)
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val parsed = executeJson(request, TenantTokenResponse.serializer())
        if (parsed.code != 0) {
            throw FeishuApiException(parsed.code, parsed.msg.ifBlank { "tenant_access_token 获取失败" })
        }
        val token = parsed.tenant_access_token
            ?: throw FeishuApiException(parsed.code, "响应缺少 tenant_access_token")
        val expireSec = parsed.expire?.takeIf { it > 0 } ?: 3600
        cachedTenantToken = token
        tenantTokenExpireAtEpochMs = now + expireSec * 1000L
        return token
    }

    private fun resolveTableIdCached(token: String): String {
        cachedTableId?.let { return it }
        if (BITABLE_TABLE_ID.isNotBlank()) {
            cachedTableId = BITABLE_TABLE_ID
            return BITABLE_TABLE_ID
        }
        var pageToken: String? = null
        while (true) {
            val url = buildString {
                append("https://open.feishu.cn/open-apis/bitable/v1/apps/")
                append(BITABLE_APP_TOKEN)
                append("/tables?page_size=100")
                if (!pageToken.isNullOrEmpty()) {
                    append("&page_token=")
                    append(URLEncoder.encode(pageToken, StandardCharsets.UTF_8))
                }
            }
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer $token")
                .GET()
                .build()
            val envelope = executeJson(request, TablesListEnvelope.serializer())
            if (envelope.code != 0) {
                throw FeishuApiException(envelope.code, envelope.msg.ifBlank { "获取 tables 失败" })
            }
            val data = envelope.data
                ?: throw FeishuApiException(envelope.code, "tables 响应缺少 data")
            val items = data.items.orEmpty()
            val found = items.firstOrNull { it.name == TABLE_NAME }?.table_id
            if (found != null) {
                cachedTableId = found
                return found
            }
            if (data.has_more != true) break
            pageToken = data.page_token
            if (pageToken.isNullOrEmpty()) break
        }
        throw IllegalStateException("未找到名为「$TABLE_NAME」的数据表，请确认表名与 app_token")
    }

    /**
     * 单选列筛选/更新需使用选项 id；文本列使用文案。先拉取 fields 再解析，避免 field validation failed。
     */
    private fun resolveStatusFieldBinding(token: String, tableId: String): StatusFieldBinding {
        if (cachedStatusBindingTableId == tableId && cachedStatusBinding != null) {
            return cachedStatusBinding!!
        }
        val fields = fetchAllTableFields(token, tableId)
        fun nameMatches(f: FieldItem): Boolean =
            f.field_name.equals(FIELD_STATUS, ignoreCase = true) || f.field_name == "状态"
        val named = fields.filter(::nameMatches)
        for (f in named) {
            if (f.field_id.isBlank()) continue
            if (f.type == FIELD_TYPE_SINGLE_SELECT) {
                val opts = parseSelectOptions(f.property)
                val pendingId = opts.firstOrNull { it.second == STATUS_PENDING }?.first
                val claimedId = opts.firstOrNull { it.second == STATUS_CLAIMED }?.first
                if (pendingId != null && claimedId != null) {
                    val b = StatusFieldBinding(
                        fieldId = f.field_id,
                        fieldName = f.field_name,
                        filterPendingValues = listOf(pendingId),
                        claimedValue = claimedId,
                    )
                    cachedStatusBindingTableId = tableId
                    cachedStatusBinding = b
                    return b
                }
                continue
            }
            val b = StatusFieldBinding(
                fieldId = f.field_id,
                fieldName = f.field_name,
                filterPendingValues = listOf(STATUS_PENDING),
                claimedValue = STATUS_CLAIMED,
            )
            cachedStatusBindingTableId = tableId
            cachedStatusBinding = b
            return b
        }
        for (f in fields) {
            if (f.field_id.isBlank() || f.type != FIELD_TYPE_SINGLE_SELECT) continue
            val opts = parseSelectOptions(f.property)
            val pendingId = opts.firstOrNull { it.second == STATUS_PENDING }?.first
            val claimedId = opts.firstOrNull { it.second == STATUS_CLAIMED }?.first
            if (pendingId != null && claimedId != null) {
                val b = StatusFieldBinding(
                    fieldId = f.field_id,
                    fieldName = f.field_name,
                    filterPendingValues = listOf(pendingId),
                    claimedValue = claimedId,
                )
                cachedStatusBindingTableId = tableId
                cachedStatusBinding = b
                return b
            }
        }
        throw IllegalStateException(
            "未解析到状态列：需要文本列「$FIELD_STATUS」或单选列且含选项「$STATUS_PENDING」「$STATUS_CLAIMED」",
        )
    }

    private fun fetchAllTableFields(token: String, tableId: String): List<FieldItem> {
        val all = mutableListOf<FieldItem>()
        var pageToken: String? = null
        while (true) {
            val url = buildString {
                append("https://open.feishu.cn/open-apis/bitable/v1/apps/")
                append(BITABLE_APP_TOKEN)
                append("/tables/")
                append(tableId)
                append("/fields?page_size=100")
                if (!pageToken.isNullOrEmpty()) {
                    append("&page_token=")
                    append(URLEncoder.encode(pageToken, StandardCharsets.UTF_8))
                }
            }
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer $token")
                .GET()
                .build()
            val envelope = executeJson(request, FieldsListEnvelope.serializer())
            if (envelope.code != 0) {
                throw FeishuApiException(envelope.code, envelope.msg.ifBlank { "获取 fields 失败" })
            }
            val data = envelope.data
                ?: throw FeishuApiException(envelope.code, "fields 响应缺少 data")
            all += data.items.orEmpty()
            if (data.has_more != true) break
            pageToken = data.page_token
            if (pageToken.isNullOrEmpty()) break
        }
        return all
    }

    private fun parseSelectOptions(property: JsonObject?): List<Pair<String, String>> {
        if (property == null) return emptyList()
        val arr = property["options"] ?: return emptyList()
        val jsonArray = arr as? JsonArray ?: return emptyList()
        return jsonArray.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = o["name"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
            id to name
        }
    }

    private fun searchOnePendingRecord(
        token: String,
        tableId: String,
        status: StatusFieldBinding,
    ): Pair<String, String> {
        val body = buildSearchRecordsPostJson(status)
        val url =
            "https://open.feishu.cn/open-apis/bitable/v1/apps/$BITABLE_APP_TOKEN/tables/$tableId/records/search"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(requestTimeout)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val envelope = executeJson(request, SearchRecordsEnvelope.serializer())
        if (envelope.code != 0) {
            throw FeishuApiException(envelope.code, envelope.msg.ifBlank { "search records 失败" })
        }
        val items = envelope.data?.items.orEmpty()
        if (items.isEmpty()) {
            throw NoAvailableKeyException()
        }
        val first = items.first()
        val recordId = first.record_id
        val rawFields = first.fields
        val keyContent = extractKeyContent(rawFields)
        if (keyContent.isBlank()) {
            throw FeishuApiException(envelope.code, "记录 $recordId 的「$FIELD_KEY」列为空")
        }
        return recordId to keyContent
    }

    /**
     * 手写 JSON，避免 `field_names: null` 等与飞书校验冲突；条件同时带 field_name + field_id（与 list fields 一致）。
     */
    private fun buildSearchRecordsPostJson(status: StatusFieldBinding): String {
        val condition = buildJsonObject {
            put("field_name", JsonPrimitive(status.fieldName))
            put("field_id", JsonPrimitive(status.fieldId))
            put("operator", JsonPrimitive("is"))
            put(
                "value",
                buildJsonArray {
                    status.filterPendingValues.forEach { add(JsonPrimitive(it)) }
                },
            )
        }
        val root = buildJsonObject {
            put(
                "filter",
                buildJsonObject {
                    put("conjunction", JsonPrimitive("and"))
                    put(
                        "conditions",
                        buildJsonArray {
                            add(condition)
                        },
                    )
                },
            )
            put("page_size", JsonPrimitive(1))
        }
        return json.encodeToString(root)
    }

    private fun findFieldByNames(fields: List<FieldItem>, candidates: List<String>): FieldItem? {
        for (f in fields) {
            if (f.field_id.isBlank()) continue
            if (candidates.any { f.field_name.equals(it, ignoreCase = true) }) return f
        }
        return null
    }

    private fun buildSearchContainsPostJson(
        field: FieldItem,
        substring: String,
        pageSize: Int,
        pageToken: String?,
    ): String {
        val condition = buildJsonObject {
            put("field_name", JsonPrimitive(field.field_name))
            put("field_id", JsonPrimitive(field.field_id))
            put("operator", JsonPrimitive("contains"))
            put(
                "value",
                buildJsonArray {
                    add(JsonPrimitive(substring))
                },
            )
        }
        val root = buildJsonObject {
            put(
                "filter",
                buildJsonObject {
                    put("conjunction", JsonPrimitive("and"))
                    put(
                        "conditions",
                        buildJsonArray {
                            add(condition)
                        },
                    )
                },
            )
            put("page_size", JsonPrimitive(pageSize))
            if (!pageToken.isNullOrEmpty()) {
                put("page_token", JsonPrimitive(pageToken))
            }
        }
        return json.encodeToString(root)
    }

    private fun searchAllRecordsVipContains(
        token: String,
        tableId: String,
        vipField: FieldItem,
        deviceId: String,
    ): List<RecordRow> {
        val raw = mutableListOf<RecordRow>()
        var pageToken: String? = null
        while (true) {
            val body = buildSearchContainsPostJson(vipField, deviceId, 500, pageToken)
            val url =
                "https://open.feishu.cn/open-apis/bitable/v1/apps/$BITABLE_APP_TOKEN/tables/$tableId/records/search"
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val envelope = executeJson(request, SearchRecordsEnvelope.serializer())
            if (envelope.code != 0) {
                throw FeishuApiException(envelope.code, envelope.msg.ifBlank { "search VIP 失败" })
            }
            val data = envelope.data ?: break
            raw += data.items.orEmpty()
            if (data.has_more != true) break
            pageToken = data.page_token
            if (pageToken.isNullOrEmpty()) break
        }
        return raw.filter { row ->
            extractFieldText(row.fields, vipField.field_name).contains(deviceId)
        }
    }

    private fun extractFieldText(fields: JsonObject?, columnName: String): String {
        if (fields == null) return ""
        val el = fields[columnName] ?: return ""
        return extractTextFromFieldElement(el)
    }

    private fun updateRecordClaimed(
        token: String,
        tableId: String,
        recordId: String,
        status: StatusFieldBinding,
    ) {
        val bodyMap = UpdateFieldsBody(
            fields = mapOf(status.fieldName to status.claimedValue),
        )
        val body = json.encodeToString(UpdateFieldsBody.serializer(), bodyMap)
        val url =
            "https://open.feishu.cn/open-apis/bitable/v1/apps/$BITABLE_APP_TOKEN/tables/$tableId/records/$recordId"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(requestTimeout)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json; charset=utf-8")
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val envelope = executeJson(request, SimpleFeishuEnvelope.serializer())
        if (envelope.code != 0) {
            throw FeishuApiException(envelope.code, envelope.msg.ifBlank { "更新记录失败" })
        }
    }

    private fun extractKeyContent(fields: JsonObject?): String {
        val k = extractFieldText(fields, FIELD_KEY)
        if (k.isNotBlank()) return k
        return extractFieldText(fields, "key_content")
    }

    private fun extractTextFromFieldElement(el: JsonElement): String {
        return when (el) {
            is JsonPrimitive -> el.contentOrNull ?: el.toString()
            is JsonArray -> el.firstOrNull()?.let { extractTextFromFieldElement(it) }.orEmpty()
            is JsonObject -> {
                el["text"]?.jsonPrimitive?.contentOrNull
                    ?: el["content"]?.jsonPrimitive?.contentOrNull
                    ?: ""
            }
        }
    }

    private fun <T> executeJson(request: HttpRequest, serializer: KSerializer<T>): T {
        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException(e)
        }
        val bodyString = response.body() ?: ""
        val status = response.statusCode()
        if (status !in 200..299) {
            val feishu = runCatching {
                json.decodeFromString(SimpleFeishuEnvelope.serializer(), bodyString)
            }.getOrNull()
            if (feishu != null) {
                throw FeishuApiException(feishu.code, feishu.msg.ifBlank { "HTTP $status" })
            }
            throw IOException("HTTP $status ${response.request().uri()}: $bodyString")
        }
        val bizCode = runCatching {
            json.parseToJsonElement(bodyString).jsonObject["code"]?.jsonPrimitive?.content?.toIntOrNull()
        }.getOrNull()
        if (bizCode != null && bizCode != 0) {
            val msg = runCatching {
                json.parseToJsonElement(bodyString).jsonObject["msg"]?.jsonPrimitive?.contentOrNull
            }.getOrNull().orEmpty()
            throw FeishuApiException(bizCode, msg.ifBlank { "业务错误 code=$bizCode" })
        }
        return try {
            json.decodeFromString(serializer, bodyString)
        } catch (e: Exception) {
            throw IOException("JSON 解析失败: ${e.message}，body=$bodyString", e)
        }
    }
}

private data class StatusFieldBinding(
    val fieldId: String,
    val fieldName: String,
    val filterPendingValues: List<String>,
    val claimedValue: String,
)

class NoAvailableKeyException(
    message: String = "没有可用的 Key（待领取记录为空）",
) : Exception(message)

class FeishuApiException(
    val feishuCode: Int?,
    message: String,
) : Exception(message)

class PurchaseNotVerifiedException(
    message: String = "未检测到购买，请用支付宝联系。",
) : Exception(message)

@Serializable
private data class InternalTokenRequest(
    val app_id: String,
    val app_secret: String,
)

@Serializable
private data class TenantTokenResponse(
    val code: Int,
    val msg: String = "",
    val tenant_access_token: String? = null,
    val expire: Int? = null,
)

@Serializable
private data class TablesListEnvelope(
    val code: Int,
    val msg: String = "",
    val data: TablesListData? = null,
)

@Serializable
private data class TablesListData(
    val items: List<TableMeta>? = null,
    val has_more: Boolean? = null,
    val page_token: String? = null,
)

@Serializable
private data class TableMeta(
    val table_id: String,
    val name: String,
)

@Serializable
private data class FieldsListEnvelope(
    val code: Int,
    val msg: String = "",
    val data: FieldsListData? = null,
)

@Serializable
private data class FieldsListData(
    val items: List<FieldItem>? = null,
    val has_more: Boolean? = null,
    val page_token: String? = null,
)

@Serializable
private data class FieldItem(
    val field_id: String = "",
    val field_name: String = "",
    val type: Int = 0,
    val property: JsonObject? = null,
)

@Serializable
private data class SearchRecordsEnvelope(
    val code: Int,
    val msg: String = "",
    val data: SearchRecordsData? = null,
)

@Serializable
private data class SearchRecordsData(
    val items: List<RecordRow>? = null,
    val has_more: Boolean? = null,
    val page_token: String? = null,
)

@Serializable
private data class RecordRow(
    val record_id: String,
    val fields: JsonObject? = null,
)

@Serializable
private data class UpdateFieldsBody(
    val fields: Map<String, String>,
)

@Serializable
private data class SimpleFeishuEnvelope(
    val code: Int,
    val msg: String = "",
)
