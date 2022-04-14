package no.haatveit.bolia

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

val TELEGRAM_BOT_TOKEN: String by System.getenv()
val TELEGRAM_API_URL = "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN"

enum class Command(val str: String) {
    SUBSCRIBE("subscribe"),
    UNSUBSCRIBE("unsubscribe"),
    LIST("list");

    companion object {
        fun forStr(str: String) = values().firstOrNull { it.str == str }
    }
}

data class CommandInvocation(val command: Command, val args: String?)

object CommandParser {

    private val PATTERN =
        Regex(
            "/(?:<command>${
                Command.values().joinToString(")|(", "(", ")") { Regex.escape(it.name) }
            })(\\s(?:<arg>\\w*))?"
        )

    fun parse(str: String): CommandInvocation? {
        return when (val m = PATTERN.matchEntire(str)) {
            null -> null
            else -> Command.forStr(m.groups["command"]!!.value)
                ?.let { command -> CommandInvocation(command, m.groups["args"]?.value) }
        }
    }
}

data class BotSubscriptionCommandState(val chatId: Long, val filter: String)

data class BotSearchCommand(val chatId: Long, val filter: String)

data class BotState(val subscriptions: Set<BotSubscriptionCommandState> = emptySet())

@JsonIgnoreProperties(ignoreUnknown = true)
data class User(val id: Long, val username: String)

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

inline fun <reified T, reified R> invokeTelegramMethod(method: String, req: T): Mono<R> {
    return Mono.defer {
        val client = HttpClient.newHttpClient()
        val uri = URI("$TELEGRAM_API_URL/$method")
        Mono.just(
            client.send(
                HttpRequest.newBuilder(uri).POST(
                    HttpRequest.BodyPublishers.ofByteArray(
                        OBJECT_MAPPER.writeValueAsBytes(req)
                    )
                ).header("Content-Type", "application/json").build(), HttpResponse.BodyHandlers.ofString()
            )
        ).doOnNext {
            if (it.statusCode() !in 200..299) {
                throw IllegalStateException("Failed to send Telegram message: ${it.body()}")
            }
        }.map {
            OBJECT_MAPPER.readValue<TelegramResult<R>>(
                it.body(),
                OBJECT_MAPPER.typeFactory
                    .constructParametricType(TelegramResult::class.java, R::class.java)
            )
                .result
        }
    }
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

data class GetUpdates(
    val offset: Long?,
    val limit: Long?,
    val timeout: Long?,
    val allowed_updates: List<String> = listOf("message")
)

fun receiveUpdates(startFromOffset: Long?, pollInterval: Duration = Duration.ofSeconds(5)): Flux<Update> {

    class Updates : ArrayList<Update>()

    fun invokeGetUpdate(offset: Long?) = invokeTelegramMethod<GetUpdates, Updates>(
        "getUpdates",
        GetUpdates(offset = offset, limit = 100, timeout = null)
    )
        .flatMapIterable { it }

    return Flux.defer {
        val prevOffset = AtomicLong(startFromOffset ?: 0L)
        Flux.interval(pollInterval)
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

fun sendMessage(chatId: Long, message: String): Mono<Void> {
    require(message.length in 1..4095)
    return Mono.defer {
        val client = HttpClient.newHttpClient()
        val uri = URI(
            "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage?" + "chat_id=${chatId}&" + "text=${
                encode(
                    message
                )
            }"
        )
        Mono.just(
            client.send(
                HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString()
            )
        ).doOnNext {
            if (it.statusCode() !in 200..299) {
                throw IllegalStateException("Failed to send Telegram message: ${it.body()}")
            }
        }.then()
    }
}
