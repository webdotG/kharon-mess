# Kharon Messenger - v2.0


## было до v2.0

Kharon v1.x — базовый E2E зашифрованный мессенджер:
- WebSocket соединение постоянно висит 24/7
- Сообщения живут 2 минуты на сервере
- Нет режимов связи — только LIVE
- Нет уведомлений о новых сообщениях в фоне
- Нет счётчика непрочитанных
- Нет автозапуска после перезагрузки

---

## изменилось в v2.0

### 1. Reception Mode — режимы приёма

Каждый пользователь выбирает как часто он доступен:

| Режим | Поведение |
|---|---|
| LIVE | Постоянное соединение |
| PULSE_5 | Окно связи каждые 5 минут |
| PULSE_15 | Окно связи каждые 15 минут |
| PULSE_30/60/240/360/720 | Окно раз в N минут |
| PULSE_1440 | Окно раз в сутки |
| SILENT | Не принимает сообщения |

**Файл:** `model/ReceptionMode.kt`

```kotlin
enum class ReceptionMode(val label: String, val minutes: Int) {
    LIVE("Всегда онлайн", 0),
    PULSE_5("Окно каждые 5 мин", 5),
    PULSE_15("Окно каждые 15 мин", 15),
    // ...
    SILENT("Тишина", -1)
}
```

---

### 2. mode_update протокол

При подключении и при смене режима клиент сообщает серверу свой режим. Сервер бродкастит всем контактам.

**Клиент → Сервер:**
```json
{ "type": "mode_update", "mode": "PULSE_5" }
```

**Сервер → Все контакты:**
```json
{ "type": "mode_update", "from": "pubKey...", "mode": "PULSE_5" }
```

**Результат:** Заголовок чата показывает режим собеседника в реальном времени. ContactsScreen показывает режим в списке контактов.

---

### 3. PULSE цикл — AlarmManager

Когда пользователь в PULSE режиме:

```
Пользователь выбрал PULSE_5 в Settings
        ↓
ForegroundService.ACTION_START с mode=PULSE_5
        ↓
startSocket() → connect(mode=PULSE_5)
        ↓
Сервер получает hello → flush queue → queue_end
        ↓
Клиент получает queue_end → disconnect()
        ↓
onClosed → scheduleNextPulse(5 минут)
        ↓
AlarmManager будит сервис через 5 минут
        ↓
Цикл повторяется
```

**Ключевые файлы:**
- `service/KharonForegroundService.kt` — управляет циклом
- `receiver/BootReceiver.kt` — запускает сервис после перезагрузки

**AlarmManager с учётом Android версий:**
```kotlin
when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(...)  // точный
        } else {
            alarmManager.setAndAllowWhileIdle(...)  // неточный fallback
        }
    }
    else -> {
        alarmManager.setExactAndAllowWhileIdle(...)  // Android 10-11 без разрешений
    }
}
```

---

### 4. Динамический TTL на сервере

**Проблема:** сервер хранил сообщения 2 минуты. Пользователь с PULSE_5 открывает окно через 5 минут — сообщения уже истекли.

**Решение:** TTL зависит от режима получателя.

**hub.js:**
```javascript
// Сервер помнит последний режим каждого клиента
this.lastKnownMode = new Map() // pubKey -> minutes

// При register и mode_update
this.lastKnownMode.set(pubKey, parseModeMinutes(mode))

// При постановке в очередь
const recipientMinutes = this.lastKnownMode.get(toKey) ?? 0
const ttl = MSG_TTL_MS + (recipientMinutes * 60 * 1000)
queue.push({ ...envelope, expiresAt: Date.now() + ttl })
```

**Результат:**
- Получатель LIVE → TTL 2 минуты
- Получатель PULSE_5 → TTL 7 минут
- Получатель PULSE_60 → TTL 62 минуты
- Получатель PULSE_1440 → TTL 26 часов

---

### 5. Временное LIVE при открытии чата

**Проблема:** пользователь в PULSE режиме открывает чат — сокет закрыт, отправить ничего нельзя.

**Решение:**

```
Пользователь тапает на контакт
        ↓
MainActivity.onChatOpen()
        ↓
socket.markChatActive(pubKey)
        ↓
Intent ACTION_CHAT_OPEN → ForegroundService
        ↓
socket.ensureConnected() — поднимает LIVE соединение если офлайн
        ↓
ChatViewModel.init() → ensureConnected() (дублирующая защита)
        ↓
Пользователь закрывает чат
        ↓
Intent ACTION_CHAT_CLOSE с currentMode
        ↓
ForegroundService восстанавливает PULSE режим
```

**ForegroundService:**
```kotlin
ACTION_CHAT_OPEN -> {
    if (socket.state.value is ConnectionState.Disconnected) {
        val keyPair = crypto.getOrCreateKeyPair()
        socket.connect(keyPair.publicKey, ReceptionMode.LIVE)
    }
}
ACTION_CHAT_CLOSE -> {
    val mode = ReceptionMode.valueOf(intent?.getStringExtra(EXTRA_MODE) ?: currentMode.name)
    currentMode = mode
    if (currentMode != ReceptionMode.LIVE) {
        socketStarted = false
        socket.disconnect()
        Handler(Looper.getMainLooper()).postDelayed({ startSocket() }, 1000)
    }
}
```

---

### 6. Буферизация сообщений

Когда получатель офлайн — сообщения буферизуются в KharonSocket:

```kotlin
// pendingByContact — буфер: pubKey -> очередь сообщений
private val pendingByContact = ConcurrentHashMap<String, ArrayDeque<SocketEvent.Message>>()

// activeChats — открытые чаты получают события напрямую
private val activeChats = Collections.synchronizedSet(mutableSetOf<String>())
```

**При получении сообщения:**
```kotlin
if (activeChats.contains(from)) {
    emit(event)  // прямо в UI
} else {
    buf.addLast(event)  // в буфер
    _pendingCount.update { it + 1 }
}
```

**При открытии чата:**
```kotlin
fun registerChat(contactPubKey: String): List<SocketEvent.Message> {
    activeChats.add(contactPubKey)
    val pending = pendingByContact.remove(contactPubKey)?.toList() ?: emptyList()
    clearUnread(contactPubKey)
    return pending
}
```

---

### 7. Счётчик непрочитанных

**Архитектура:**
```kotlin
private val _unreadByContact = ConcurrentHashMap<String, Int>()
private val _unreadTotal = MutableStateFlow(0)
```

**Инкрементируется ВСЕГДА** при получении сообщения (и когда чат открыт и когда закрыт):
```kotlin
_unreadByContact[from] = (_unreadByContact[from] ?: 0) + 1
_unreadTotal.update { it + 1 }
```

**Сбрасывается** при открытии чата через `registerChat` и при закрытии через `onCleared`.

**ContactsViewModel** подписан на `unreadTotal`:
```kotlin
socket.unreadTotal.collect {
    _uiState.update { state ->
        state.copy(
            contacts = state.contacts.map { contact ->
                contact.copy(unreadCount = socket.getUnreadCount(contact.pubKey))
            }
        )
    }
}
```

---

### 8. Credits система

Максимум 10 одновременных неподтверждённых сообщений.

```kotlin
val sentCount = messages.count { it.isOutgoing && it.status == MessageStatus.SENT }
if (sentCount >= 10) return  // блокируем отправку
```

Credits восстанавливаются при получении `read_receipt` или при отмене сообщения.

---

### 9. Статусы сообщений

| Иконка | Статус | Событие |
|---|---|---|
| `...` | SENDING | Локально создано |
| `v` серый | SENT | Отправлено через сокет |
| `v` синий | DELIVERED | Получатель принял |
| `vv` зелёный | READ | Получатель прочитал, TTL запускается |
| `!` красный | FAILED | Ошибка отправки |

**TTL запускается с момента READ** — 2 минуты, потом сообщение исчезает из UI.

---

### 10. PULSE уведомления

Два канала уведомлений:

```kotlin
// Тихий — статус сервиса
val silentChannel = NotificationChannel(CHANNEL_ID, "Kharon статус", IMPORTANCE_LOW)

// С вибрацией — новые сообщения
val alertChannel = NotificationChannel(CHANNEL_ALERT_ID, "Kharon сообщения", IMPORTANCE_DEFAULT)
alertChannel.enableVibration(true)
alertChannel.vibrationPattern = longArrayOf(0, 250, 100, 250)
```

При получении `queue_end`:
```kotlin
val count = socket.getTotalPendingCount()
if (count > 0) {
    // Вибрация + уведомление "Новых сообщений: N"
} else {
    // Тихое "Окно связи открылось — новых сообщений нет"
}
```

---

### 11. BootReceiver

Автозапуск сервиса после перезагрузки:

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startForegroundService(Intent(context, KharonForegroundService::class.java).apply {
                action = KharonForegroundService.ACTION_START
                putExtra(KharonForegroundService.EXTRA_MODE, ReceptionMode.LIVE.name)
            })
        }
    }
}
```

---

### 12. Темы оформления

Удалена дефолтная тёмная тема, добавлена Shadow:

| Тема | ID | Иконка | Стиль |
|---|---|---|---|
| Princess | PRINCESS | ♡ | Розово-фиолетовая, **дефолтная** |
| Terminal Dark | TERMINAL_DARK | >_! | Зелёный на чёрном |
| Terminal Light | TERMINAL_LIGHT | о_0 | Тёмный на белом |
| Shadow | SHADOW | ☠ | Чёрно-красно-белая |

---

### 13. Кнопка назад в чате

Добавлен параметр `onBack` в ChatScreen и TitleBar.

```kotlin
ChatScreen(
    contactName   = name,
    contactPubKey = ...,
    onChatClose   = { onChatClose(currentMode) },
    onBack        = { navController.popBackStack() },
)
```

---

### 14. Автоскролл

Заменён `animateScrollToItem` на `scrollToItem` — мгновенный, надёжнее:

```kotlin
LaunchedEffect(state.messages.size) {
    if (state.messages.isNotEmpty()) {
        listState.scrollToItem(state.messages.lastIndex)
    }
}
```

---

## Известные ограничения

**Двойной scheduleNextPulse** — при смене режима иногда AlarmManager устанавливается дважды. Не критично — второй перезаписывает первый.

**Samsung Doze** — даже с SCHEDULE_EXACT_ALARM Samsung иногда задерживает будильники. Решение — Battery Optimization → Без ограничений.

**LIVE соединение при открытии чата** — сервис посылает `sendModeUpdate(LIVE)` временно. Это значит контакт видит тебя как LIVE пока ты в чате. После закрытия режим восстанавливается.

---

## Разрешения Android

```xml
INTERNET                          — WebSocket
CAMERA                            — QR сканер
FOREGROUND_SERVICE                — фоновый сервис
FOREGROUND_SERVICE_DATA_SYNC      — тип сервиса
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS — не убивать
POST_NOTIFICATIONS                — уведомления
SCHEDULE_EXACT_ALARM              — точные будильники
RECEIVE_BOOT_COMPLETED            — автозапуск
VIBRATE                           — вибрация
```

---

## Серверные изменения

**hub.js добавлено:**
- `lastKnownMode` Map — хранит последний режим каждого клиента
- `parseModeMinutes(mode)` — парсит PULSE_N в минуты
- `updateMode(pubKey, mode)` — обновляет режим при mode_update
- Динамический TTL в `_enqueue()`
- Логирование TTL: `[hub] enqueue for X... ttl=420s`

**index.js добавлено:**
- `hub.updateMode(clientKey, mode)` при получении mode_update

---

## Что дальше — v3.0 UnifiedPush

**Проблема AlarmManager:** требует разрешения, Samsung убивает, неточный на некоторых устройствах.

**Решение:** UnifiedPush через ntfy — self-hosted push сервер.

```
Самсунг отправил → Kharon сервер → HTTP POST на ntfy
                                          ↓
                              ntfy будит Ксиаоми мгновенно
                                          ↓
                              Kharon поднимает сокет → забирает
```

**Плюсы:**
- Мгновенные уведомления вместо ожидания N минут
- Не нужен SCHEDULE_EXACT_ALARM
- Работает когда Samsung убивает процесс
- Полностью self-hosted — ntfy на том же сервере
- Батарея тратится только при реальном сообщении

**План реализации:**
1. Docker: добавить ntfy в docker-compose.yml рядом с Kharon
2. Server: при enqueue → HTTP POST на ntfy топик получателя
3. Android: встроенный WebSocket на ntfy (не отдельное приложение)
4. Android: при получении ntfy push → поднять основной сокет