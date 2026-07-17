package com.example.ezmakro.core.domain.result

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import com.example.ezmakro.core.domain.result.DataError.Local
import com.example.ezmakro.core.domain.result.DataError.Network
import org.junit.jupiter.api.Test

class ResultExtensionsTest {
    @Test
    fun `map transforms Success value`() {
        val result: Result<Int, Network> = Result.Success(2)
        val mapped = result.map { it * 3 }
        assertThat(mapped).isEqualTo(Result.Success(6))
    }

    @Test
    fun `map leaves Error unchanged`() {
        val result: Result<Int, Network> = Result.Error(Network.NOT_FOUND)
        val mapped = result.map { it * 3 }
        assertThat(mapped).isEqualTo(Result.Error(Network.NOT_FOUND))
    }

    @Test
    fun `onSuccess runs only for Success`() {
        var sideEffect = 0
        Result.Success(5).onSuccess { sideEffect = it }
        assertThat(sideEffect).isEqualTo(5)

        Result.Error(Local.NOT_FOUND).onSuccess { sideEffect = 99 }
        assertThat(sideEffect).isEqualTo(5)
    }

    @Test
    fun `onFailure runs only for Error`() {
        var captured: Error? = null
        Result.Error(Network.REQUEST_TIMEOUT).onFailure { captured = it }
        assertThat(captured).isEqualTo(Network.REQUEST_TIMEOUT)

        Result.Success(1).onFailure { captured = null }
        assertThat(captured).isEqualTo(Network.REQUEST_TIMEOUT)
    }

    @Test
    fun `asEmptyResult converts Success to Unit`() {
        val empty = Result.Success("x").asEmptyResult()
        assertThat(empty).isEqualTo(Result.Success(Unit))
    }

    @Test
    fun `extension chain returns same instance`() {
        val result: Result<Int, Network> = Result.Success(1)
        val chained = result.onSuccess { }.onFailure { }
        assertThat(chained).isSameInstanceAs(result)
    }
}
