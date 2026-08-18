package com.yanparker.modelforum

import com.yanparker.modelforum.data.provider.FreeModelFilter
import com.yanparker.modelforum.data.provider.parseRetryAfter
import com.yanparker.modelforum.engine.Prompts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeModelFilterTest {

    @Test
    fun `openrouter free models end with free suffix`() {
        assertTrue(FreeModelFilter.isFree("openrouter", "qwen/qwen3.5-plus:free"))
        assertTrue(FreeModelFilter.isFree("openrouter", "meta-llama/llama-3.3-70b-instruct:free"))
        assertFalse(FreeModelFilter.isFree("openrouter", "openai/gpt-4o-mini"))
    }

    @Test
    fun `generic providers mark serverless and keyword models`() {
        assertTrue(FreeModelFilter.isFree("groq", "accounts/fireworks/models/llama-3.3-70b-instruct:serverless"))
        assertTrue(FreeModelFilter.isFree("fireworks", "llama-3.1-8b-instruct-free"))
    }

    @Test
    fun `text capable excludes embeddings and audio`() {
        assertTrue(FreeModelFilter.isTextCapable("llama-3.3-70b-instruct"))
        assertFalse(FreeModelFilter.isTextCapable("text-embedding-3-small"))
        assertFalse(FreeModelFilter.isTextCapable("whisper-1"))
    }
}

class RetryAfterTest {

    @Test
    fun `parses seconds`() {
        assertEquals(30_000, parseRetryAfter("30"))
    }

    @Test
    fun `parses http-date`() {
        val future = System.currentTimeMillis() + 60_000
        val date = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US)
            .format(java.util.Date(future))
        val parsed = parseRetryAfter(date)
        assertTrue(parsed in 50_000..70_000)
    }

    @Test
    fun `blank returns default one minute`() {
        assertEquals(60_000, parseRetryAfter(null))
        assertEquals(60_000, parseRetryAfter(""))
    }
}

class PromptsTest {

    @Test
    fun `judge prompt contains all answers and question`() {
        val prompt = Prompts.judgePrompt(
            "Сколько планет?",
            listOf("Модель А" to "Восемь", "Модель Б" to "Девять"),
        )
        assertTrue(prompt.contains("Сколько планет?"))
        assertTrue(prompt.contains("Модель А"))
        assertTrue(prompt.contains("Модель Б"))
        assertTrue(prompt.contains("Восемь"))
        assertTrue(prompt.contains("Девять"))
        assertTrue(prompt.contains("Где мнения разошлись"))
    }

    @Test
    fun `context contains topic and authors`() {
        val ctx = Prompts.forumContext("Танки", listOf("Qwen" to "Привет всем"))
        assertTrue(ctx.contains("ТОПИК ФОРУМА: Танки"))
        assertTrue(ctx.contains("[Qwen]: Привет всем"))
    }

    @Test
    fun `empty discussion invites model to open the topic`() {
        val ctx = Prompts.forumContext("Танки", emptyList())
        assertTrue(ctx.contains("Ты открываешь обсуждение"))
    }
}