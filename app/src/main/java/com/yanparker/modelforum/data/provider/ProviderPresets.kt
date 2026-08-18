package com.yanparker.modelforum.data.provider

enum class FreeFilterMode { OPENROUTER, GENERIC, NONE }

data class ProviderPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    val chatPath: String = "/chat/completions",
    val modelsPath: String = "/models",
    val freeFilter: FreeFilterMode = FreeFilterMode.GENERIC,
    val supportsKeyInfo: Boolean = false,
)

object ProviderPresets {

    val all: List<ProviderPreset> = listOf(
        ProviderPreset("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", freeFilter = FreeFilterMode.OPENROUTER, supportsKeyInfo = true),
        ProviderPreset("groq", "Groq", "https://api.groq.com/openai/v1"),
        ProviderPreset("fireworks", "Fireworks AI", "https://api.fireworks.ai/inference/v1"),
        ProviderPreset("cerebras", "Cerebras", "https://api.cerebras.ai/v1"),
        ProviderPreset("github", "GitHub Models", "https://models.github.ai"),
        ProviderPreset("together", "Together AI", "https://api.together.xyz/v1"),
        ProviderPreset("deepinfra", "DeepInfra", "https://api.deepinfra.com/v1"),
        ProviderPreset("nvidia", "NVIDIA NIM", "https://integrate.api.nvidia.com/v1"),
        ProviderPreset("mistral", "Mistral AI", "https://api.mistral.ai/v1"),
        ProviderPreset("hf", "Hugging Face", "https://router.huggingface.co/v1"),
        ProviderPreset("custom", "Кастомный", baseUrl = "", chatPath = "/chat/completions", modelsPath = "/models", freeFilter = FreeFilterMode.GENERIC),
    )

    fun byId(id: String): ProviderPreset = all.firstOrNull { it.id == id } ?: all.last()
}

object FreeModelFilter {

    private val textOnlyExclude = listOf(
        "embed", "rerank", "whisper", "tts", "stt", "image", "flux",
        "dall-e", "speech", "transcribe", "moderation", "guard",
    )

    fun isFree(providerId: String, modelId: String): Boolean {
        val id = modelId.lowercase()
        return when (ProviderPresets.byId(providerId).freeFilter) {
            FreeFilterMode.OPENROUTER -> id.endsWith(":free") || id.contains(":free") || id.contains("-free")
            FreeFilterMode.GENERIC -> isGenericFree(modelId)
            FreeFilterMode.NONE -> true
        }
    }

    fun isTextCapable(modelId: String): Boolean {
        val id = modelId.lowercase()
        return textOnlyExclude.none { id.contains(it) }
    }

    private fun isGenericFree(modelId: String): Boolean {
        return when {
            modelId.contains(":free") || modelId.contains("-free") || modelId.contains("free") -> true
            modelId.contains("serverless") -> true
            modelId.contains("public") -> true
            modelId.contains("nvidia:") || modelId.startsWith("meta/") -> true
            else -> false
        }
    }
}