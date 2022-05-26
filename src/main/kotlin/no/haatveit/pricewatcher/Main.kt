package no.haatveit.pricewatcher

import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.util.retry.RetryBackoffSpec
import reactor.util.retry.RetrySpec
import java.time.Duration

private val LOGGER = LoggerFactory.getLogger("no.haatveit.pricewatcher.Main")

/**
 * Publish new additions to the observed set.
 */
fun <T> Flux<Set<T>>.addedItems(): Flux<T> =
    scan(emptySet<T>() to emptySet<T>()) { (prev, _), next -> next to (next - prev) }
        .flatMapIterable { (_, difference) -> difference }

val String.isValidFilter: Boolean get() = length in 3..255 && matches(Regex("""\w*"""))

val RETRY_FOREVER_WITH_BACKOFF: RetryBackoffSpec = RetrySpec.backoff(Long.MAX_VALUE, Duration.ofSeconds(10))

val RECEIVE_TELEGRAM_UPDATES_WITH_RETRY: Flux<TelegramUpdate> = receiveUpdates(null)
    .doOnError { e -> LOGGER.error("Failed to get updates from Telegram. Will retry", e) }
    .retryWhen(RETRY_FOREVER_WITH_BACKOFF)
    .share()

val RECEIVE_SUBSCRIPTIONS: Flux<Set<BotSubscriptionCommandState>> = RECEIVE_TELEGRAM_UPDATES_WITH_RETRY
    .doOnNext { LOGGER.info("Update from Telegram: $it") }
    .filter { it.message?.text?.startsWith("/subscribe") ?: false }
    .map { it.message!!.chat.id to it.message.text!!.substringAfter("/subscribe ") }
    .map { (chatId, filter) -> BotSubscriptionCommandState(chatId, filter) }
    .delayUntil { cmd ->
        if (cmd.filter.isValidFilter) {
            sendMessage(cmd.chatId, "You will now receive updates for \"${cmd.filter}\".")
        } else {
            sendMessage(cmd.chatId, "Invalid subscription filter.")
        }
    }
    .filter { it.filter.isValidFilter }
    .scanPersistent(Persistent.create("state", BotState())) { acc: BotState, sub ->
        acc.copy(subscriptions = acc.subscriptions + sub)
    }
    .doOnNext { LOGGER.info("Subscriptions: ${it.subscriptions}") }
    .map { it.subscriptions }
    .filter { it.isNotEmpty() }
    .share()

val RECEIVE_SALE_ITEMS_WITH_RETRY: Flux<SaleItems> = receiveSaleItems()
    .doOnError { e -> LOGGER.error("Failed to get sale items. Will retry.", e) }
    .cache(1)
    .retryWhen(RETRY_FOREVER_WITH_BACKOFF)

val PUBLISH_SEARCH_RESULTS: Flux<TelegramMessage> =
    RECEIVE_TELEGRAM_UPDATES_WITH_RETRY.filter { it.message?.text?.startsWith("/search") ?: false }
        .map { BotSearchCommand(it.message!!.chat.id, it.message.text!!.substringAfter("/search ")) }
        .withLatestFrom(RECEIVE_SALE_ITEMS_WITH_RETRY)
        .flatMap { (command, itemSet) ->
            val results = itemSet.filter { it.title.contains(command.filter, ignoreCase = true) }
                .map { it.blurbText }
            val list = results
                .joinToString("\n", "* ")
                .take(3500)
            sendMessage(
                command.chatId,
                "Found ${results.size} matching items.\n$list"
            )
        }

val PUBLISH_ITEM_ALERTS: Flux<TelegramMessage> = RECEIVE_SALE_ITEMS_WITH_RETRY
    .addedItems()
    .distinctPersistent(Persistent.create("processed_records", emptySet())) { result ->
        result.recId.toString()
    }
    .doOnNext { LOGGER.info("New item on sale: ${it.blurbText} (${it.recId})") }
    .withLatestFrom(RECEIVE_SUBSCRIPTIONS)
    .flatMapIterable { (result, subs) ->
        subs.filter { sub -> result.blurbText.contains(sub.filter, ignoreCase = true) }
            .map { chat -> result to chat }
    }
    .flatMap { (result, subCommand) ->
        sendMessage(subCommand.chatId, "New outlet item! ${result.blurbText}. ${result.url}")
            .doOnNext { LOGGER.info("Sent notification message to ${subCommand.chatId} for item ${result.recId}") }
    }

val BOT_COMMANDS = listOf(
    BotCommand("subscribe", "Subscribe to price alerts."),
    BotCommand("unsubscribe", "Unsubscribe to price alerts."),
    BotCommand("search", "Search in sale items.")
)

fun main() {
    LOGGER.info("Application is starting")
    setTelegramCommands(BOT_COMMANDS)
        .thenMany(PUBLISH_ITEM_ALERTS)
        .mergeWith(PUBLISH_SEARCH_RESULTS)
        .blockLast()
}
