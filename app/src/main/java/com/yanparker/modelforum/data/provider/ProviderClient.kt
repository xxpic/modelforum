package com.yanparker.modelforum.data.provider

import com.yanparker.modelforum.data.network.ApiException
import com.yanparker.modelforum.data.network.ChatRequest
import com.yanparker.modelforum.data.network.ChatResponse
import com.yanparker.modelforum.data.network.KeyInfoResponse
import com.yanparker.modelforum.data.network.ModelListResponse
import com.yanparker.modelforum.data.network.SseChunk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class StreamResult(
    val text: String,
    val reason: String?,
)

data class FreePlatformInfo(
    val limitRemaining: Long?,
    val limitReset: String?,
)

class ProviderClient(
    private val okHttp: OkHttpClient,
    private val streamClient: OkHttpClient,
    private val presets: Map<String, ProviderPreset>,
) {
    private val json = sharedJson

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun presetOf(providerId: String): ProviderPreset =
        presets[providerId] ?: presets.values.last()

    /** Пресет с учётом кастомных URL участника (для провайдера «Кастомный»). */
    fun presetFor(providerId: String, baseUrl: String = "", chatPath: String = "", modelsPath: String = ""): ProviderPreset {
        val base = presetOf(providerId)
        if (baseUrl.isNotBlank()) {
            return base.copy(
                baseUrl = baseUrl,
                chatPath = chatPath.ifBlank { "/chat/completions" },
                modelsPath = modelsPath.ifBlank { "/models" },
            )
        }
        return base
    }

    // ---------- Chat (без стриминга) ----------

    suspend fun chat(key: String, preset: ProviderPreset, request: ChatRequest): ChatResponse {
        val body = json.encodeToString(ChatRequest.serializer(), request.copy(stream = false))
            .toRequestBody(jsonMedia)
        val httpRequest = buildChatRequest(preset, key, body)
        return okHttp.executeParsed(httpRequest) { raw -> parseChatResponse(raw) }
    }

    // ---------- Chat (стриминг) ----------

    suspend fun chatStream(
        key: String,
        preset: ProviderPreset,
        request: ChatRequest,
        onDelta: suspend (text: String) -> Unit,
        onReasoning: suspend (text: String) -> Unit = {},
    ): StreamResult = suspendCancellableCoroutine { cont ->
        val body = json.encodeToString(ChatRequest.serializer(), request.copy(stream = true))
            .toRequestBody(jsonMedia)
        val httpRequest = buildChatRequest(preset, key, body)

        val accumulated = StringBuilder()
        var finishReason: String? = null
        var gotAnything = false
        var latestDeltaAt = System.currentTimeMillis()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]" || data.isBlank()) return
                val chunk = try {
                    json.decodeFromString(SseChunk.serializer(), data)
                } catch (e: Exception) {
                    if (System.currentTimeMillis() - latestDeltaAt > 3000) {
                        cont.resumeWithException(
                            ApiException.ServerError(500, "Ошибка формата SSE: ${e.message}", data.take(200))
                        )
                    }
                    return
                }
                if (chunk.error != null) {
                    cont.resumeWithException(parseApiError(chunk.error.code, chunk.error.message))
                    return
                }
                val choice = chunk.choices.firstOrNull() ?: return
                choice.delta?.content?.let { d ->
                    latestDeltaAt = System.currentTimeMillis()
                    gotAnything = true
                    accumulated.append(d)
if (cont.isActive) {
                    val scope = CoroutineScope(cont.context + Job())
                    scope.launch { onDelta(d) }
                }
                }
                if (choice.finishReason != null) finishReason = choice.finishReason
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                if (cont.isCancelled) return
                response?.let { r ->
                    r.toApiException()?.let {
                        cont.resumeWithException(it)
                        return
                    }
                }
                cont.resumeWithException(ApiException.Network(IOException(t?.message ?: "stream failed")))
            }

            override fun onClosed(eventSource: EventSource) {
                if (cont.isCancelled) return
                if (!gotAnything && finishReason == null) {
                    cont.resumeWithException(ApiException.ServerError(500, "Пустой ответ от модели"))
                } else {
                    cont.resume(StreamResult(accumulated.toString(), finishReason))
                }
            }
        }

        val eventSource = EventSources.createFactory(streamClient)
            .newEventSource(httpRequest.newBuilder().build(), listener)
        cont.invokeOnCancellation { eventSource.cancel() }
    }

    // ---------- Список моделей ----------

    suspend fun fetchModels(key: String, preset: ProviderPreset): List<String> {
        val url = preset.baseUrl.trimEnd('/') + preset.modelsPath
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $key")
            .get()
            .build()
        return okHttp.executeParsed(request) { raw ->
            val resp = json.decodeFromString(ModelListResponse.serializer(), raw)
            (resp.data ?: resp.models ?: emptyList()).map { it.id }.distinct()
        }
    }

    // ---------- Лимиты (только OpenRouter) ----------

    suspend fun keyInfo(key: String, preset: ProviderPreset): FreePlatformInfo? {
        if (!preset.supportsKeyInfo) return null
        val url = preset.baseUrl.trimEnd('/') + "/key"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $key")
            .get()
            .build()
        return try {
            okHttp.executeParsed(request) { raw ->
                val resp = json.decodeFromString(KeyInfoResponse.serializer(), raw)
                resp.data?.let { FreePlatformInfo(it.limitRemaining, it.limitReset) }
            }
        } catch (_: Exception) {
            null
        }
    }
}

fun buildChatRequest(preset: ProviderPreset, key: String, body: okhttp3.RequestBody): Request {
    val url = preset.baseUrl.trimEnd('/') + preset.chatPath
    return Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer $key")
        .addHeader("Content-Type", "application/json")
        .post(body)
        .build()
}

suspend fun <T> OkHttpClient.executeParsed(request: Request, parser: (String) -> T): T =
    suspendCancellableCoroutine { cont ->
        newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(ApiException.Network(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (!it.isSuccessful) {
                            val err = it.toApiException()
                            if (!cont.isCancelled) {
                                if (err != null) cont.resumeWithException(err)
                                else cont.resumeWithException(ApiException.ServerError(it.code, "HTTP ${it.code}"))
                            }
                            return
                        }
                        val raw = it.body?.string().orEmpty()
                        if (!cont.isCancelled) cont.resume(parser(raw))
                    }
                } catch (e: IOException) {
                    if (!cont.isCancelled) cont.resumeWithException(ApiException.Network(e))
                } catch (e: Exception) {
                    if (!cont.isCancelled) {
                        cont.resumeWithException(ApiException.ServerError(500, "Ошибка разбора ответа: ${e.message}"))
                    }
                }
            }
        })
    }

fun Response.toApiException(): ApiException? {
    val body = body?.string().orEmpty()
    return when {
        code == 429 -> ApiException.RateLimited(parseRetryAfter(headers["Retry-After"]))
        code == 402 -> ApiException.NoBalance()
        code >= 400 -> ApiException.ServerError(code, "HTTP $code", body.take(300))
        else -> null
    }
}

fun parseRetryAfter(raw: String?): Long {
    if (raw.isNullOrBlank()) return 60_000
    raw.toLongOrNull()?.let { return it.coerceIn(1, 600) * 1000 }
    return try {
        java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US)
            .parse(raw)?.let { it.time - System.currentTimeMillis() }
            ?.coerceIn(1000, 600_000) ?: 60_000
    } catch (_: Exception) {
        60_000
    }
}

val sharedJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
    coerceInputValues = true
    isLenient = true
}

fun parseChatResponse(raw: String): ChatResponse {
    val resp = sharedJson.decodeFromString(ChatResponse.serializer(), raw)
    if (resp.error != null) throw parseApiError(resp.error.code, resp.error.message)
    return resp
}

fun parseApiError(code: Int?, message: String?): ApiException = when (code) {
    429 -> ApiException.RateLimited(60_000)
    402 -> ApiException.NoBalance(message ?: "")
    else -> ApiException.ServerError(code ?: 500, message ?: "Неизвестная ошибка")
}