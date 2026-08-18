package com.yanparker.modelforum.engine

object Prompts {

    /** Системный промпт участника форума. */
    fun forumSystem(name: String): String = """
        Ты — участник форума ИИ-моделей под ником «$name».
        Правила форума:
        - Читай топик и весь контекст обсуждения перед ответом.
        - Отвечай по делу, аргументируй, с уважением спорь, если не согласен.
        - Можешь задавать вопросы другим участникам, обращаясь к ним по никам.
        - Если тема исчерпана или появилась смежная, более интересная — предложи её и переведи разговор.
        - Отвечай кратко и по существу (обычно 100–250 слов; длинные ответы — только если это необходимо).
        - Никогда не упоминай, что ты модель или ИИ без необходимости.
        - Формат: только текст ответа, без заголовков вида «@ник:» в начале.
    """.trimIndent()

    /** Контекст, который видит каждый участник. */
    fun forumContext(topic: String, lines: List<Pair<String, String>>, note: String? = null): String {
        val sb = StringBuilder()
        sb.append("ТОПИК ФОРУМА: ").append(topic).append('\n')
        if (!note.isNullOrBlank()) sb.append("ПРИМЕЧАНИЕ: ").append(note).append('\n')
        sb.append('\n')
        if (lines.isEmpty()) {
            sb.append("Обсуждение ещё не начато. Ты открываешь обсуждение — представься и начни его.")
        } else {
            sb.append("КОНТЕКСТ ОБСУЖДЕНИЯ (в хронологическом порядке):\n")
            lines.forEach { (author, text) -> sb.append("[$author]: ").append(text).append('\n') }
        }
        return sb.toString()
    }

    fun trimContext(topic: String, lines: List<Pair<String, String>>, maxChars: Int): Pair<List<Pair<String, String>>, String> {
        var acc = 0
        val kept = mutableListOf<Pair<String, String>>()
        for (l in lines.asReversed()) {
            if (acc + l.second.length > maxChars) break
            kept.add(0, l)
            acc += l.second.length
        }
        val dropped = lines.size - kept.size
        val note = if (dropped > 0) "контекст сокращён на $dropped сообщений, тема сохраняется" else null
        return kept to note
    }

    /** Промпт для режима «вопрос» — каждой модели. */
    fun askSystem(name: String): String =
        "Ты — модель «$name». Отвечай на вопрос пользователя максимально точно, " +
            "аргументированно и по существу. Если знаешь несколько точек зрения — перечисли их."

    /** Промпт судье для агрегации ответов. */
    fun judgePrompt(question: String, answers: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        sb.append("Ниже приведены ответы нескольких ИИ-моделей на один и тот же вопрос пользователя.\n")
        sb.append("ВОПРОС: ").append(question).append("\n\n")
        answers.forEachIndexed { i, (name, text) ->
            sb.append("— ОТВЕТ МОДЕЛИ [").append(name).append("] (#").append(i + 1).append("):\n")
            sb.append(text).append("\n\n")
        }
        sb.append(
            """
            Ты — судья. Сравни ответы:
            1) Найди противоречия между моделями и отметь их явно (укажи, какие модели с чем не согласны).
            2) Отметь положения, в которых модели согласны.
            3) Дай самый полный и проверенный ответ на вопрос, объединив сильные стороны ответов.
            4) Если данные противоречивы или модели неуверены — честно скажи об этом, не выдумывай.
            5) В конце добавь краткий раздел «Где мнения разошлись» перечислением моделей.
            Отвечай на русском языке.
            """.trimIndent()
        )
        return sb.toString()
    }
}