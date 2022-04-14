package no.haatveit.bolia

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

private val LOGGER = LoggerFactory.getLogger("no.haatveit.bolia.Telegram")

val TELEGRAM_BOT_TOKEN: String by System.getenv()
val TELEGRAM_API_URL = "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN"

data class BotSubscriptionCommandState(val chatId: Long, val filter: String)

data class BotSearchCommand(val chatId: Long, val filter: String)

data class BotState(val subscriptions: Set<BotSubscriptionCommandState> = emptySet())

@JsonIgnoreProperties(ignoreUnknown = true)
data class User(val id: Long, val username: String?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Chat(val id: Long, val type: String, val title: String?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Message(
    val message_id: Long,
    val from: User?,
    val sender_chat: Chat?,
    val chat: Chat,
    val text: String?,
)

data class MessageEntity(
    val type: String,
    val offset: Long,
    val length: Long,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Update(
    @JsonProperty("update_id") val updateId: Long,
    val message: Message?,
    @JsonProperty("edited_message") val editedMessage: Message?,
    val entities: List<MessageEntity>?,
)

data class BotCommand(val command: String, val description: String)

data class TelegramResult<T>(val ok: Boolean, val result: T)

private inline fun <reified T, reified R> invokeTelegramMethod(method: String, req: T): Mono<R> {
    return Mono.fromCallable {
        LOGGER.debug("Invoking $method")
        val client = HttpClient.newHttpClient()
        val uri = URI("$TELEGRAM_API_URL/$method")
        val httpResponse = client.send(
            HttpRequest.newBuilder(uri).POST(
                HttpRequest.BodyPublishers.ofByteArray(
                    OBJECT_MAPPER.writeValueAsBytes(req)
                )
            ).header("Content-Type", "application/json").build(), HttpResponse.BodyHandlers.ofString()
        )
        LOGGER.debug("Telegram responded with ${httpResponse.statusCode()}")
        if (httpResponse.statusCode() !in 200..299) {
            LOGGER.error("API call to $method failed")
            throw IllegalStateException("Failed to send Telegram message: ${httpResponse.body()}")
        }
        val apiResponse = OBJECT_MAPPER
            .readValue<TelegramResult<R>>(
                httpResponse.body(),
                OBJECT_MAPPER.typeFactory.constructParametricType(TelegramResult::class.java, R::class.java)
            )
        LOGGER.debug("Call to $method succeeded: $apiResponse")
        apiResponse.result
    }
        .doOnError { e -> LOGGER.error("Call to $method failed", e) }
}

fun setBotCommands(commands: List<BotCommand>): Mono<Void> {
    data class SetMyCommandsRequest(
        val commands: List<BotCommand>
    )
    return invokeTelegramMethod<SetMyCommandsRequest, Boolean>(
        "setMyCommands", SetMyCommandsRequest(
            commands
        )
    ).then()
}

fun receiveUpdates(startFromOffset: Long?, pollInterval: Duration = Duration.ofSeconds(10)): Flux<Update> {

    data class GetUpdates(
        val offset: Long?,
        val limit: Long?,
        val timeout: Long?,
        val allowed_updates: List<String> = listOf("message")
    )

    class Updates : ArrayList<Update>()

    fun invokeGetUpdate(offset: Long?) = invokeTelegramMethod<GetUpdates, Updates>(
        "getUpdates",
        GetUpdates(offset = offset, limit = 100, timeout = null)
    )
        .doOnNext { LOGGER.debug("Received ${it.size} updates from TG") }
        .flatMapIterable { it }

    return Flux.defer {
        val prevOffset = AtomicLong(startFromOffset ?: 0L)
        Flux.interval(pollInterval)
            .doOnNext { LOGGER.debug("Polling Telegram for updates") }
            .flatMap {
                invokeGetUpdate(
                    when (val v = prevOffset.get()) {
                        0L -> null
                        else -> v
                    }
                )
                    .doOnNext { prevOffset.set(it.updateId + 1) }
            }
    }
}

data class SendMessage(
    @JsonProperty("chat_id")
    val chatId: Long,
    val text: String
) {
    init {
        require(chatId > 0L)
        require(this.text.length in 1..4095)
    }
}

fun sendMessage(chatId: Long, text: String): Mono<Message> {
    return invokeTelegramMethod<SendMessage, Message>(
        "sendMessage",
        SendMessage(chatId, text)
    )
        .doFirst { LOGGER.info("Sending message to $chatId: $text") }
}
