package github.aeonbtc.ibiswallet.data

import android.util.Log
import github.aeonbtc.ibiswallet.BuildConfig
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Service for fetching BTC/USD price from various sources
 */
class BtcPriceService {
    companion object {
        data class FiatCurrencyOption(
            val code: String,
            val name: String,
        )

        private const val TAG = "BtcPriceService"
        private const val MEMPOOL_PRICE_URL = "https://mempool.space/api/v1/prices"
        private const val MEMPOOL_ONION_PRICE_URL =
            "http://mempoolhqx4isw62xs7abwphsq7ldayuidyx2v2oethdhhj6mlo2r6ad.onion/api/v1/prices"
        private const val COINGECKO_PRICE_URL = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies="
        private const val TIMEOUT_SECONDS = 15L
        private const val TOR_TIMEOUT_SECONDS = 30L
        private const val TOR_PROXY_HOST = "127.0.0.1"
        private const val TOR_PROXY_PORT = 9050

        private val mempoolFiatOptions =
            listOf(
                FiatCurrencyOption("USD", "US Dollar"),
                FiatCurrencyOption("EUR", "Euro"),
                FiatCurrencyOption("GBP", "British Pound Sterling"),
                FiatCurrencyOption("CAD", "Canadian Dollar"),
                FiatCurrencyOption("CHF", "Swiss Franc"),
                FiatCurrencyOption("AUD", "Australian Dollar"),
                FiatCurrencyOption("JPY", "Japanese Yen"),
            )

        private val coinGeckoFiatOptions =
            listOf(
                FiatCurrencyOption("USD", "US Dollar"),
                FiatCurrencyOption("EUR", "Euro"),
                FiatCurrencyOption("GBP", "British Pound Sterling"),
                FiatCurrencyOption("CAD", "Canadian Dollar"),
                FiatCurrencyOption("CHF", "Swiss Franc"),
                FiatCurrencyOption("AUD", "Australian Dollar"),
                FiatCurrencyOption("JPY", "Japanese Yen"),
                FiatCurrencyOption("AED", "United Arab Emirates Dirham"),
                FiatCurrencyOption("ARS", "Argentine Peso"),
                FiatCurrencyOption("BDT", "Bangladeshi Taka"),
                FiatCurrencyOption("BHD", "Bahraini Dinar"),
                FiatCurrencyOption("BMD", "Bermudian Dollar"),
                FiatCurrencyOption("BRL", "Brazil Real"),
                FiatCurrencyOption("CLP", "Chilean Peso"),
                FiatCurrencyOption("CZK", "Czech Koruna"),
                FiatCurrencyOption("DKK", "Danish Krone"),
                FiatCurrencyOption("GEL", "Georgian Lari"),
                FiatCurrencyOption("HKD", "Hong Kong Dollar"),
                FiatCurrencyOption("HUF", "Hungarian Forint"),
                FiatCurrencyOption("ILS", "Israeli New Shekel"),
                FiatCurrencyOption("INR", "Indian Rupee"),
                FiatCurrencyOption("KWD", "Kuwaiti Dinar"),
                FiatCurrencyOption("LKR", "Sri Lankan Rupee"),
                FiatCurrencyOption("MMK", "Burmese Kyat"),
                FiatCurrencyOption("MXN", "Mexican Peso"),
                FiatCurrencyOption("MYR", "Malaysian Ringgit"),
                FiatCurrencyOption("NGN", "Nigerian Naira"),
                FiatCurrencyOption("NOK", "Norwegian Krone"),
                FiatCurrencyOption("NZD", "New Zealand Dollar"),
                FiatCurrencyOption("PHP", "Philippine Peso"),
                FiatCurrencyOption("PKR", "Pakistani Rupee"),
                FiatCurrencyOption("PLN", "Polish Zloty"),
                FiatCurrencyOption("SAR", "Saudi Riyal"),
                FiatCurrencyOption("SEK", "Swedish Krona"),
                FiatCurrencyOption("SGD", "Singapore Dollar"),
                FiatCurrencyOption("THB", "Thai Baht"),
                FiatCurrencyOption("TRY", "Turkish Lira"),
                FiatCurrencyOption("UAH", "Ukrainian Hryvnia"),
                FiatCurrencyOption("VEF", "Venezuelan bolivar fuerte"),
                FiatCurrencyOption("VND", "Vietnamese dong"),
                FiatCurrencyOption("XDR", "IMF Special Drawing Rights"),
                FiatCurrencyOption("ZAR", "South African Rand"),
            )

        fun getSupportedFiatCurrencies(source: String): List<FiatCurrencyOption> =
            when (source) {
                SecureStorage.PRICE_SOURCE_MEMPOOL,
                SecureStorage.PRICE_SOURCE_MEMPOOL_ONION,
                -> mempoolFiatOptions

                SecureStorage.PRICE_SOURCE_COINGECKO -> coinGeckoFiatOptions
                else -> emptyList()
            }

        fun sanitizeFiatCurrency(
            source: String,
            currencyCode: String?,
        ): String {
            val normalized = currencyCode?.uppercase(Locale.US)
            return getSupportedFiatCurrencies(source)
                .firstOrNull { it.code == normalized }
                ?.code
                ?: SecureStorage.DEFAULT_PRICE_CURRENCY
        }
    }

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    private val torClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TOR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TOR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TOR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(TOR_PROXY_HOST, TOR_PROXY_PORT)))
            .dns { hostname ->
                // Send hostname through SOCKS5 proxy for Tor resolution, avoiding DNS leaks
                listOf(InetAddress.getByAddress(hostname, byteArrayOf(0, 0, 0, 0)))
            }
            .build()
    }

    /**
     * Fetch BTC/USD price from mempool.space
     * @return Price in USD or null on failure
     */
    suspend fun fetchFromMempool(currencyCode: String): Double? =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request.Builder()
                        .url(MEMPOOL_PRICE_URL)
                        .header("Accept", "application/json")
                        .build()

                val response = client.newCall(request).execute()

                response.use {
                    if (!it.isSuccessful) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "Mempool price fetch failed: ${it.code}")
                        return@withContext null
                    }

                    val body = it.body.string()
                    if (body.isEmpty()) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "Mempool price response empty")
                        return@withContext null
                    }

                    val json = JSONObject(body)
                    val price = json.optDouble(currencyCode.uppercase(Locale.US), -1.0)

                    if (price > 0) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Mempool price: $price $currencyCode")
                        price
                    } else {
                        if (BuildConfig.DEBUG) Log.e(TAG, "Invalid price from Mempool")
                        null
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error fetching from Mempool", e)
                null
            }
        }

    /**
     * Fetch BTC/USD price from mempool.space via Tor (.onion)
     * @return Price in USD or null on failure
     */
    suspend fun fetchFromMempoolOnion(currencyCode: String): Double? =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request.Builder()
                        .url(MEMPOOL_ONION_PRICE_URL)
                        .header("Accept", "application/json")
                        .build()

                val response = torClient.newCall(request).execute()

                response.use {
                    if (!it.isSuccessful) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "Mempool onion price fetch failed: ${it.code}")
                        return@withContext null
                    }

                    val body = it.body.string()
                    if (body.isEmpty()) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "Mempool onion price response empty")
                        return@withContext null
                    }

                    val json = JSONObject(body)
                    val price = json.optDouble(currencyCode.uppercase(Locale.US), -1.0)

                    if (price > 0) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Mempool onion price: $price $currencyCode")
                        price
                    } else {
                        if (BuildConfig.DEBUG) Log.e(TAG, "Invalid price from Mempool onion")
                        null
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error fetching from Mempool onion", e)
                null
            }
        }

    /**
     * Fetch BTC/USD price from CoinGecko
     * @return Price in USD or null on failure
     */
    suspend fun fetchFromCoinGecko(currencyCode: String): Double? =
        withContext(Dispatchers.IO) {
            try {
                val normalizedCurrency = currencyCode.lowercase(Locale.US)
                val request =
                    Request.Builder()
                        .url("$COINGECKO_PRICE_URL$normalizedCurrency")
                        .header("Accept", "application/json")
                        .build()

                val response = client.newCall(request).execute()

                response.use {
                    if (!it.isSuccessful) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "CoinGecko price fetch failed: ${it.code}")
                        return@withContext null
                    }

                    val body = it.body.string()
                    if (body.isEmpty()) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "CoinGecko price response empty")
                        return@withContext null
                    }

                    // Response format: {"bitcoin":{"usd":12345.67}}
                    val json = JSONObject(body)
                    val bitcoin = json.optJSONObject("bitcoin")
                    val price = bitcoin?.optDouble(normalizedCurrency, -1.0) ?: -1.0

                    if (price > 0) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "CoinGecko price: $price $currencyCode")
                        price
                    } else {
                        if (BuildConfig.DEBUG) Log.e(TAG, "Invalid price from CoinGecko")
                        null
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error fetching from CoinGecko", e)
                null
            }
        }
}
