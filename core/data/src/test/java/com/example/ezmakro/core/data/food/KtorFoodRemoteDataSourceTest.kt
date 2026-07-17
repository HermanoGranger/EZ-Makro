package com.example.ezmakro.core.data.food

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.example.ezmakro.core.data.network.HttpClientFactory
import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Result
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class KtorFoodRemoteDataSourceTest {
    private fun jsonEngine(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = MockEngine {
        respond(
            content = ByteReadChannel(body),
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    @Test
    fun `every request carries the mandatory User-Agent header`() =
        runTest {
            val engine = jsonEngine("""{"products":[]}""")
            val source = KtorFoodRemoteDataSource(HttpClientFactory.create(engine))

            source.searchByName("apple")

            val sent = engine.requestHistory.single()
            assertThat(sent.headers[HttpHeaders.UserAgent]).isEqualTo(HttpClientFactory.USER_AGENT)
        }

    @Test
    fun `searchByName maps products to food items`() =
        runTest {
            val body =
                """
                {"products":[
                    {"code":"1","product_name_de":"Apfel",
                     "nutriments":{"energy-kcal_100g":52.0,"proteins_100g":0.3,
                     "fat_100g":0.2,"carbohydrates_100g":14.0,"fiber_100g":2.4}},
                    {"code":"2","product_name":"Banana",
                     "nutriments":{"energy-kcal_100g":89.0}}
                ]}
                """.trimIndent()
            val source = KtorFoodRemoteDataSource(HttpClientFactory.create(jsonEngine(body)))

            val result = source.searchByName("fruit")

            assertThat(result).isInstanceOf(Result.Success::class)
            val items = (result as Result.Success).data
            assertThat(items).hasSize(2)
            assertThat(items[0].name).isEqualTo("Apfel")
        }

    @Test
    fun `searchByName drops products without a usable name`() =
        runTest {
            val body = """{"products":[{"code":"1"},{"code":"2","product_name":"Rice"}]}"""
            val source = KtorFoodRemoteDataSource(HttpClientFactory.create(jsonEngine(body)))

            val result = (source.searchByName("x") as Result.Success).data

            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("Rice")
        }

    @Test
    fun `getByBarcode returns the mapped product when status is 1`() =
        runTest {
            val body =
                """
                {"status":1,"product":{"code":"12345","product_name_de":"Haferflocken",
                 "nutriments":{"energy-kcal_100g":350.0}}}
                """.trimIndent()
            val source = KtorFoodRemoteDataSource(HttpClientFactory.create(jsonEngine(body)))

            val result = source.getByBarcode("12345")

            assertThat(result).isInstanceOf(Result.Success::class)
            assertThat((result as Result.Success).data?.name).isEqualTo("Haferflocken")
        }

    @Test
    fun `getByBarcode returns null when status is 0`() =
        runTest {
            val source =
                KtorFoodRemoteDataSource(
                    HttpClientFactory.create(jsonEngine("""{"status":0}""")),
                )

            val result = source.getByBarcode("00000")

            assertThat(result).isInstanceOf(Result.Success::class)
            assertThat((result as Result.Success).data).isNull()
        }

    @Test
    fun `network error is propagated as a typed error`() =
        runTest {
            val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
            val source = KtorFoodRemoteDataSource(HttpClientFactory.create(engine))

            val result = source.getByBarcode("1")

            assertThat(result).isEqualTo(Result.Error(DataError.Network.SERVER_ERROR))
        }

    @Test
    fun `no internet is mapped to NO_INTERNET`() =
        runTest {
            val engine = MockEngine { throw java.net.UnknownHostException("no dns") }
            val source = KtorFoodRemoteDataSource(HttpClientFactory.create(engine))

            val result = source.searchByName("x")

            assertThat(result).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
        }
}
