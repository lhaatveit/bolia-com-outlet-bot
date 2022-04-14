package no.haatveit.bolia

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

val BOLIA_API_URI: String = System.getenv("API_URI")
    ?: "https://www.bolia.com/api/search/outlet?includerangelimits=true&language=nb-no&mode=category&pageLink=5471&size=2000&v=2021.4143.1215.1-48"
val BOLIA_POLL_INTERVAL_SECONDS: Long = System.getenv("POLL_INTERVAL_SECONDS")?.toLong() ?: 10L

fun queryWebApi(endpoint: URI = URI(BOLIA_API_URI)): Mono<Set<Result>> {
    return Mono.defer {
        val client = HttpClient.newHttpClient()
        val res = client.send(
            HttpRequest.newBuilder(endpoint).GET().build(), HttpResponse.BodyHandlers.ofInputStream()
        )
        val result = OBJECT_MAPPER.readValue(res.body(), ApiResult::class.java)
        Mono.just(result).map { it.products.toResultSet() }
    }
}

fun Products.toResultSet(): Set<Result> = results.flatMap { it.results }.toSet()

fun receiveOutletSaleResultSet(queryFn: () -> Mono<Set<Result>> = { queryWebApi() }): Flux<Set<Result>> =
    Flux.interval(Duration.ofSeconds(BOLIA_POLL_INTERVAL_SECONDS))
        .onBackpressureDrop()
        .flatMap { queryFn() }
        .distinctUntilChanged()

val Result.blurbText: String
    get() {
        return "$title - ${salesPrice?.amount} - $discountText - ${location?.name}"
    }

val Result.url: URL
    get() = URL(
        "https://www.bolia.com/nb-no/mot-oss/butikker/online-outlet/produkt/${this.urlPath}"
    )

data class Option(
    val title: String?,
    val value: String?,
    val selected: Boolean,
    val count: Int,
)

data class Facet(
    val title: String?,
    val id: String?,
    val type: String?,
    val options: ArrayList<Option>?,
    val floor: Double = 0.0,
    val ceil: Double = 0.0,
    val step: Int = 0,
    val partnerFacet: Boolean = false
)

data class RangeFacetLimit(
    val id: String? = null,
    val floor: Double = 0.0,
    val ceil: Double = 0.0
)

data class Location(
    val inventoryLocationId: String? = null,
    val hub: String? = null,
    val name: String? = null,
    val isStorage: Boolean = false,
    val storeId: String? = null,
)

data class Raw(
    val amount: Double = 0.0,
    val currency: String? = null
)

data class ListPrice(
    val raw: Raw? = null,
    val amount: String? = null,
    val amountWithDecimals: String? = null,
    val currencyCode: String? = null
)

data class SalesPrice(
    val raw: Raw? = null,
    val amount: String? = null,
    val amountWithDecimals: String? = null,
    val currencyCode: String? = null
)

data class Result(
    val recId: Any? = null,
    val inventSerial: String? = null,
    val imageVersion: Int = 0,
    val location: Location? = null,
    val originalSku: String? = null,
    val urlPath: String? = null,
    val listPrice: ListPrice? = null,
    val salesPrice: SalesPrice? = null,
    val discountText: String? = null,
    val details: String? = null,
    val designInformation: String? = null,
    val type: String? = null,
    val title: String
)

class Results(
    val results: ArrayList<Result>,
    val type: String?
)

data class Products(
    val total: Int = 0,
    val results: ArrayList<Results>
)

data class Image(
    val url: String? = null,
    val focus: Boolean = false,
    val zoom: Boolean = false,
    val focusPoints: String? = null,
    val cachebust: Int = 0,
    val description: String? = null,
    val hexCode: String? = null,
    val width: Int = 0,
    val height: Int = 0
)

data class CategoryHeroBanner(
    val image: Image? = null,
    val imageUrl: String? = null,
    val videoSrc: String? = null,
    val focus: Boolean = false,
    val zoom: Boolean = false,
    val autoPlay: Boolean = false,
    val italicBottomTitle: Boolean = false,
    val isScrimEnabled: Boolean = false,
    val renderFromBottom: Boolean = false,
    val isCampaignActive: Boolean = false,
    val isCountDownEnabled: Boolean = false,
)

data class ApiResult(
    val facets: ArrayList<Facet>? = null,
    val rangeFacetLimits: ArrayList<RangeFacetLimit>? = null,
    val showAsProducts: Boolean = false,
    val products: Products,
    val total: Int = 0,
    val totalShown: Int = 0,
    val categoryHeroBanner: CategoryHeroBanner? = null,
    val totalResults: Int = 0,
)
