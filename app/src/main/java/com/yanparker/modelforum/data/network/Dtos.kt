package com.yanparker.modelforum.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("top_p") val topP: Double? = null,
)

@Serializable
data class Delta(
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
)

@Serializable
data class ChatChoice(
    val message: ChatMessage? = null,
    val delta: Delta? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class Usage(
    @SerialName("total_tokens") val totalTokens: Long? = null,
)

@Serializable
data class ApiErrorBody(
    val code: Int? = null,
    val message: String? = null,
    val metadata: JsonObject? = null,
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<ChatChoice> = emptyList(),
    val usage: Usage? = null,
    val error: ApiErrorBody? = null,
)

@Serializable
data class SseChunk(
    val choices: List<ChatChoice> = emptyList(),
    val error: ApiErrorBody? = null,
)

@Serializable
data class ModelDto(
    val id: String = "",
)

@Serializable
data class ModelListResponse(
    val data: List<ModelDto>? = null,
    val models: List<ModelDto>? = null,
)

@Serializable
data class KeyInfoData(
    val label: String? = null,
    val usage: Long? = null,
    val limit: Long? = null,
    @SerialName("limit_remaining") val limitRemaining: Long? = null,
    @SerialName("is_free_tier") val isFreeTier: Boolean? = null,
    @SerialName("limit_reset") val limitReset: String? = null,
)

@Serializable
data class KeyInfoResponse(
    val data: KeyInfoData? = null,
)

sealed class ApiException(message: String) : Exception(message) {
    class RateLimited(val retryAfterMs: Long) : ApiException("Лимит запросов: повторите через ${retryAfterMs / 1000} с")
    class NoBalance(message: String = "Недостаточно средств / исчерпана дневная квота") : ApiException(message)
    class ServerError(val code: Int, message: String, val details: String? = null) :
        ApiException("Ошибка $code: $message")
    class Network(val ex: java.io.IOException) : ApiException("Сеть недоступна: ${ex.message}")
}