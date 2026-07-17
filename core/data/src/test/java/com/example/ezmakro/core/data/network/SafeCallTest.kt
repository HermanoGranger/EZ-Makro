package com.example.ezmakro.core.data.network

import com.example.ezmakro.core.domain.result.DataError
import com.example.ezmakro.core.domain.result.Result
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test

@Serializable
data class SampleDto(val id: String, val name: String)

class SafeCallTest {

    @Test
    fun `200 maps to Success with body`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"id":"1","name":"apple"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClientFactory.create(engine)

        val result = client.get<SampleDto>("/product/1")

        assertThat(result).isEqualTo(Result.Success(SampleDto("1", "apple")))
    }

    @Test
    fun `404 maps to NOT_FOUND`() = runTest {
        val engine = MockEngine { _ -> respondError(HttpStatusCode.NotFound) }
        val client = HttpClientFactory.create(engine)

        val result = client.get<SampleDto>("/missing")

        assertThat(result).isEqualTo(Result.Error(DataError.Network.NOT_FOUND))
    }

    @Test
    fun `500 maps to SERVER_ERROR`() = runTest {
        val engine = MockEngine { _ -> respondError(HttpStatusCode.InternalServerError) }
        val client = HttpClientFactory.create(engine)

        val result = client.get<SampleDto>("/boom")

        assertThat(result).isEqualTo(Result.Error(DataError.Network.SERVER_ERROR))
    }

    @Test
    fun `unresolved address maps to NO_INTERNET`() = runTest {
        val engine = MockEngine { _ -> throw java.net.UnknownHostException("no dns") }
        val client = HttpClientFactory.create(engine)

        val result = client.get<SampleDto>("/x")

        assertThat(result).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
    }

    @Test
    fun `malformed body maps to SERIALIZATION`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("not json"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClientFactory.create(engine)

        val result = client.get<SampleDto>("/x")

        assertThat(result).isInstanceOf(Result.Error::class)
        assertThat((result as Result.Error).error).isEqualTo(DataError.Network.SERIALIZATION)
    }
}
