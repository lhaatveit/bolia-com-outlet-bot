package no.haatveit.bolia


data class Option (
    val title: String?,
    val value: String?,
    val selected: Boolean,
    val count: Int,
)

data class Facet (
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

data class Location (
    val inventoryLocationId: String? = null,
    val hub: String? = null,
    val name: String? = null,
    val isStorage: Boolean = false,
    val storeId: String? = null,
)

data class Raw (
    val amount: Double = 0.0,
    val currency: String? = null
)

data class ListPrice (
    val raw: Raw? = null,
    val amount: String? = null,
    val amountWithDecimals: String? = null,
    val currencyCode: String? = null
)

data class SalesPrice (
    val raw: Raw? = null,
    val amount: String? = null,
    val amountWithDecimals: String? = null,
    val currencyCode: String? = null
)

data class Result (
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

class Results (
    val results: ArrayList<Result>,
    val type: String?
)

data class Products (
    val total: Int = 0,
    val results: ArrayList<Results>
)

data class Image (
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

data class CategoryHeroBanner (
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

data class ApiResult (
    val facets: ArrayList<Facet>? = null,
    val rangeFacetLimits: ArrayList<RangeFacetLimit>? = null,
    val showAsProducts: Boolean = false,
    val products: Products,
    val total: Int = 0,
    val totalShown: Int = 0,
    val categoryHeroBanner: CategoryHeroBanner? = null,
    val totalResults: Int = 0,
)
