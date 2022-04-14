package no.haatveit.bolia

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.RetryBackoffSpec
import reactor.util.retry.RetrySpec
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

/**
 * Publish new additions to the observed set.
 */
fun <T> Flux<Set<T>>.changes(): Flux<T> =
    scan(emptySet<T>() to emptySet<T>()) { (prev, _), next -> next to (next - prev) }
        .flatMapIterable { (_, difference) -> difference }

val String.isValidFilter: Boolean get() = length in 3..255 && matches(Regex("""\w*"""))

val RETRY_FOREVER_WITH_BACKOFF: RetryBackoffSpec = RetrySpec.backoff(Long.MAX_VALUE, Duration.ofSeconds(10))

fun main(args: Array<String>) {

    val receiveUpdates = receiveUpdates(null)
        .retryWhen(RETRY_FOREVER_WITH_BACKOFF)
        .share()

    val receiveSaleItemSubscriptions = receiveUpdates
        .doOnNext { println("Update from Telegram: $it") }
        .filter { it.message?.text?.startsWith("/subscribe") ?: false }
        .map { it.message!!.chat.id to it.message!!.text!!.substringAfter("/subscribe ") }
        .map { (chatId, filter) -> BotSubscriptionCommandState(chatId, filter) }
        .delayUntil { cmd ->
            if (cmd.filter.isValidFilter) {
                sendMessage(cmd.chatId, "You will now receive updates for \"${cmd.filter}\".")
            } else {
                sendMessage(cmd.chatId, "Invalid subscription filter.")
            }
        }
        .filter { it.filter.isValidFilter }
        .scanPersistent(Persistence.DEFAULT_PATH.toFile(), BotState(), { acc: BotState, sub ->
            acc.copy(subscriptions = acc.subscriptions + sub)
        })
        .doOnNext { println("Subscriptions: ${it.subscriptions}") }
        .map { it.subscriptions }
        .filter { it.isNotEmpty() }
        .share()

    val resultSetPublisher = receiveOutletSaleResultSet()
        .cache(1)
        .retryWhen(RETRY_FOREVER_WITH_BACKOFF)

    val publishSearchResults = receiveUpdates.filter { it.message?.text?.startsWith("/search") ?: false }
        .map { BotSearchCommand(it.message!!.chat.id, it.message!!.text!!.substringAfter("/search ")) }
        .withLatestFrom(resultSetPublisher) { cmd, saleItems -> cmd to saleItems }
        .flatMap { (cmd, saleItems) ->
            val results = saleItems.filter { it.title.contains(cmd.filter, ignoreCase = true) }
                .map { it.blurbText }
            val list = results
                .joinToString("\n", "* ")
                .take(3500)
            sendMessage(
                cmd.chatId,
                "Found ${results.size} matching items.\n$list"
            )
        }

    val receiveNewSaleItems = resultSetPublisher.changes()

    val publishNewItemsAlerts = receiveNewSaleItems
        .doOnNext { println("New item on sale: ${it.blurbText}") }
        .withLatestFrom(receiveSaleItemSubscriptions) { result, subCommands -> result to subCommands }
        .flatMapIterable { (result, subs) ->
            subs.filter { sub -> result.title.contains(sub.filter, ignoreCase = true) }
                .map { chat -> result to chat }
        }
        .flatMap { (result, subCommand) ->
            sendMessage(subCommand.chatId, "${result.blurbText}. ${result.url}")
        }

    val publishCommands = setBotCommands(
        listOf(
            BotCommand("subscribe", "Subscribe to price alerts."),
            BotCommand("unsubscribe", "Unsubscribe to price alerts."),
            BotCommand("search", "Search in sale items.")
        )
    )

    publishCommands
        .thenMany(publishNewItemsAlerts)
        .mergeWith(publishSearchResults)
        .blockLast()
}
