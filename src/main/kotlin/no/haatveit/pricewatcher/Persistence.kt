package no.haatveit.pricewatcher

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiFunction

interface Persistent<T> {

    fun store(o: T): Mono<Void>
    fun load(): Mono<T>

    fun <R> transforming(restore: (T) -> R, save: (R) -> T): Persistent<R> {
        val delegate = this
        return object : Persistent<R> {
            override fun store(o: R): Mono<Void> {
                return delegate.store(save(o))
            }

            override fun load(): Mono<R> {
                return delegate.load().map { restore(it) }
            }
        }
    }

    class ObjectMapperPersistent<T>(private val file: File, private val fallbackValue: T, private val clazz: Class<T>) :
        Persistent<T> {

        companion object {
            val LOGGER: Logger = LoggerFactory.getLogger(Persistent::class.java)
        }

        override fun store(o: T): Mono<Void> = Mono.fromRunnable {
            LOGGER.debug("Writing to ${file.toPath().toAbsolutePath()}")
            file.toPath().parent.toFile().mkdirs()
            OBJECT_MAPPER.writeValue(file, o)
        }

        override fun load(): Mono<T> =
            Mono.fromSupplier {
                if (!file.exists() || file.length() == 0L) {
                    throw FileNotFoundException(file.path)
                }
                OBJECT_MAPPER.readValue(file, clazz)
            }
                .onErrorReturn({ e ->
                    when (e) {
                        is FileNotFoundException -> true
                        else -> false
                    }
                }, fallbackValue)
    }

    companion object {

        private val HOME_DIRECTORY: Path = Path.of(System.getProperty("user.home"))
        val CONF_DIRECTORY: Path = HOME_DIRECTORY.resolve(".bolia")

        inline fun <reified T> create(cacheKey: String, fallbackValue: T): ObjectMapperPersistent<T> {
            return ObjectMapperPersistent(
                CONF_DIRECTORY.resolve("$cacheKey.json").toFile(),
                fallbackValue,
                T::class.java
            )
        }
    }
}

inline fun <T, reified A> Flux<T>.scanPersistent(
    persistent: Persistent<A>,
    accumulator: BiFunction<A, T, A>
): Flux<A> {
    return persistent.load()
        .flatMapMany { initialValue: A ->
            this@scanPersistent.scan(initialValue, accumulator)
        }
        .transform { flux ->
            val latest = AtomicReference<A?>(null)
            val storeLatest = Runnable {
                latest.get()?.let {
                    persistent.store(it).block()
                }
            }
            flux
                .doOnNext(latest::set)
                .doOnTerminate(storeLatest)
                .doOnCancel(storeLatest)
        }
}

fun <T, K> Flux<T>.distinctPersistent(knownKeys: Persistent<Set<K>>, keySelector: (T) -> K): Flux<T> {

    val persistent = object : Persistent<Pair<T?, Set<K>>> {
        override fun store(o: Pair<T?, Set<K>>): Mono<Void> {
            return knownKeys.store(o.second)
        }

        override fun load(): Mono<Pair<T?, Set<K>>> {
            return knownKeys.load().map { known -> null to known }
        }
    }

    return scanPersistent(
        persistent
    ) { (_, processed), t ->
        val key: K = keySelector(t)
        val maybeT: T? = if (key !in processed) t else null
        maybeT to (processed + key)
    }
        .filter { (maybeT, _) -> maybeT != null }
        .map { (t, _) -> t!! }
}
