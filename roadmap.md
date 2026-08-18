# Roadmap: «Форум ИИ-моделей» (Android 14)

Приложение-форум, где несколько ИИ-моделей из разных агрегаторов обсуждают темы
между собой и отвечают на вопросы пользователя. Используются только бесплатные
модели, API-ключи и модели настраиваются через CRUD в настройках.

## 1. Обзор и цели

- **Режим 1 — Дискуссия:** пользователь создаёт тему, модели обсуждают её по
  кругу, могут спорить, задавать вопросы и менять тему. Идёт, пока пользователь
  не остановит или не закончатся лимиты. Если лимиты закончились — дискуссия
  сама продолжается, когда они снимутся.
- **Режим 2 — Вопрос:** пользователь задаёт запрос, все модели думают,
  одна (судья) анализирует ответы и выдаёт самый проверенный итог.
- Только бесплатные модели. Ключи шифруются (Keystore).

## 2. Стек

- Kotlin 2.0.x, Jetpack Compose + Material 3 (Material You, dynamic color)
- minSdk 26, targetSdk 34 (Android 14), compileSdk 34
- Retrofit + OkHttp + kotlinx-serialization, okhttp-sse (стриминг)
- Room (история), EncryptedSharedPreferences (ключи), DataStore (настройки)
- Foreground Service + уведомления, WorkManager (авто-возобновление)
- Ручной DI (AppContainer), Coroutines + Flow
- JUnit (юнит-тесты движка), Gradle Kotlin DSL

## 3. Архитектура

```
app/
  MainActivity.kt
  App.kt                     — Application, AppContainer
  di/AppContainer.kt         — ручной DI
  data/
    network/                 — Retrofit-клиенты, SSE
      OpenAiCompatApi.kt     — универсальный /chat/completions
      ModelsApi.kt           — список моделей провайдера
      dto/                   — ChatRequest/Response, SSE, ModelDto
    provider/
      ProviderPresets.kt     — 10 пресетов + кастомный
      ProviderClient.kt      — клиент конкретного провайдера
      ModelListParser.kt     — парсеры форматов (data / models)
      FreeModelFilter.kt     — эвристики бесплатных моделей
    db/                      — Room: Discussion, Message, ParticipantDao
    key/KeyStore.kt          — EncryptedSharedPreferences + Keystore
    settings/AppSettings.kt  — DataStore
    quota/                   — счётчики запросов, статусы лимитов
  engine/
    RequestScheduler.kt      — очередь, интервал ≥3 c, 429/Retry-After/402
    DiscussionEngine.kt      — режим 1 (ротация, свобода темы, стриминг)
    QuestionEngine.kt        — режим 2 (параллель ≤3, судья)
    prompts/Prompts.kt       — системные промпты (RU)
  ui/
    theme/                   — Material 3, dynamic color, тёмная тема
    participants/            — экран «Участники» (CRUD ключей и моделей)
    forum/                   — экран дискуссии (режим 1)
    ask/                     — экран вопроса (режим 2)
    settings/                — настройки, экспорт истории
    nav/                     — навигация
  service/
    DiscussionService.kt     — Foreground Service
    ResumeWorker.kt          — WorkManager-возобновление
```

## 4. Провайдеры (агрегаторы)

Все OpenAI-совместимы: `POST {baseUrl}{path}/chat/completions` (Bearer).

| Провайдер | Base URL по умолчанию | Путь чата | Формат списка моделей |
|---|---|---|---|
| OpenRouter | https://openrouter.ai/api/v1 | /chat/completions | `data[]` + `:free` |
| Groq | https://api.groq.com/openai/v1 | /chat/completions | `data[]` |
| Fireworks | https://api.fireworks.ai/inference/v1 | /chat/completions | `models[]` |
| Cerebras | https://api.cerebras.ai/v1 | /chat/completions | `data[]` |
| GitHub Models | https://models.github.ai | /chat/completions | `data[]` |
| Together AI | https://api.together.xyz/v1 | /chat/completions | `data[]` |
| DeepInfra | https://api.deepinfra.com/v1 | /chat/completions | `data[]` |
| NVIDIA NIM | https://integrate.api.nvidia.com/v1 | /chat/completions | `data[]` |
| Mistral AI | https://api.mistral.ai/v1 | /chat/completions | `data[]` |
| Hugging Face | https://router.huggingface.co/v1 | /chat/completions | `data[]` |
| Кастомный | задаёт пользователь | задаёт пользователь | автоопределение |

- **CRUD в настройках:** пресет → ключ → «загрузить модели» → выбор бесплатной
  текстовой модели (выпадающий список) → сохранить / редактировать / удалить.
- **Фильтр бесплатных:** OpenRouter `:free`; Fireworks/прочие — эвристика по
  имени (`free`, `llama`, `qwen`, `gemma`, `mistral`, `phi`, `deepseek`) + любая
  модель доступна для ручного выбора.
- **Лимиты:** 429 → `Retry-After`; сброс квоты в полночь UTC; авто-ретраи с
  экспоненциальным backoff; 402 → участник отключается на время.
  OpenRouter: `GET /key` (limit_remaining, limit_reset). Прочие: локальный
  счётчик запросов за день.

## 5. Хранилище

- **Ключи:** EncryptedSharedPreferences (Keystore AES). В историю/логи не
  попадают.
- **Room:** `Discussion(id, title, mode, state, createdAt)`,
  `Message(id, discussionId, participantId, role, text, status, tokens, createdAt)`,
  `Participant(id, providerId, name, model, color, keyRef, enabled, order, quota)`.
- **DataStore:** интервал запросов, дефолты ходов, язык промптов (RU), тема.

## 6. Планировщик запросов и лимиты

- Глобальная очередь с интервалом ≥3 c между запросами (защита от RPM-лимитов
  бесплатного тира).
- 429: читаем `Retry-After` → участник «⏳ лимит до HH:MM», ход откладывается.
- Стриминг-сбои (`finish_reason: error` в SSE) трактуются как недоставка,
  ретрай по правилам выше.
- Недописанное сообщение помечается, при продолжении — достраивается или
  помечается «оборвано» (на выбор в настройках, дефолт: достраивать).
- Авто-возобновление: таймер до `limit_reset` + WorkManager каждые 15 мин.

## 7. Движок дискуссии (режим 1)

- Порядок ходов: ротация с лёгкой случайностью; модель отвечает на последние
  2–3 сообщения, разрешено менять тему (в промпте).
- Системный промпт (RU): «Ты — [ник], участник форума. Читай контекст, отвечай
  по делу, спорь, задавай вопросы, при исчерпании темы предложи новую».
- Каждому участнику: `system` + контекст как `[Ник]: текст` в одном user-сообщении.
- Стриминг в UI, аватары-цвета участников.
- Параметры хода: макс. сообщений на модель (дефолт 15), max_tokens (800),
  температура (0.7), интервал.
- Состояния: RUNNING / PAUSED / WAITING_LIMITS / STOPPED / DONE.
- Защита от вечного разговора: бюджет ходов, автообрезка контекста с пометкой.

## 8. Движок вопроса (режим 2)

- Параллельные запросы участникам (concurrency ≤3, интервал 3 c).
- Судья (любой участник, дефолт — первый добавленный): получает вопрос + все
  ответы → «найди противоречия, отметь совпадения, дай самый проверенный ответ,
  честно укажи неуверенность».
- Итог: карточка судьи + сворачиваемые оригиналы + разбор «согласны/спорят».

## 9. UI (Material You)

1. Главная: вкладки «Дискуссии» / «Вопрос» / «Участники» / «Настройки».
2. Дискуссия: чат, статус-бар (чей ход, лимиты, счётчики), Старт/Пауза/
   Продолжить/Стоп.
3. Вопрос: поле запроса, выбор участников и судьи, прогресс, итог.
4. Участники: CRUD ключей, тест соединения, статусы лимитов.
5. Настройки: интервалы, дефолты, поведение при 429, экспорт истории (TXT/JSON).

## 10. Фоновые сервисы

- Foreground Service: активная дискуссия при выключенном экране, уведомление с
  кнопками пауза/стоп и прогрессом.
- WorkManager (15 мин): проверка «спящих» дискуссий, возобновление после снятия
  лимитов.

## 11. Тесты и сборка

- Юнит-тесты: ротация/свобода темы, обработка 429/Retry-After, backoff, бюджет
  запросов, парсеры списков моделей, агрегатор-промпт.
- `./gradlew assembleDebug`, lint; ручная проверка на устройстве/эмуляторе API 34.

## 12. Этапы (чек-лист)

- [x] Окружение (Java, SDK, Gradle)
- [x] roadmap.md
- [ ] Скаффолд проекта (Gradle, манифест, Material 3 тема)
- [ ] Сеть + провайдеры + шифрованные ключи + Room
- [ ] Планировщик запросов и обработка лимитов
- [ ] Движок дискуссии (режим 1)
- [ ] Движок вопроса (режим 2)
- [ ] UI (все экраны)
- [ ] Foreground-сервис + уведомления + WorkManager
- [ ] Тесты + assembleDebug + проверка