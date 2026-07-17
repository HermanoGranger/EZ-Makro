package com.example.ezmakro.core.presentation.uitext

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Platform-agnostic (within Android) representation of text shown in the UI.
 * Either a literal dynamic string or a reference to a string resource with
 * optional format args. Resolves via [asString].
 */
sealed interface UiText {
    data class DynamicString(
        val value: String,
    ) : UiText

    class StringResource(
        @StringRes val id: Int,
        val args: Array<Any> = emptyArray(),
    ) : UiText {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StringResource) return false
            return id == other.id && args.contentEquals(other.args)
        }

        override fun hashCode(): Int {
            var result = id
            result = 31 * result + args.contentHashCode()
            return result
        }
    }

    @Suppress("SpreadOperator")
    fun asString(context: Context): String =
        when (this) {
            is DynamicString -> value
            is StringResource -> context.getString(id, *args)
        }

    @Composable
    @Suppress("SpreadOperator")
    fun asString(): String =
        when (this) {
            is DynamicString -> value
            is StringResource -> stringResource(id, *args)
        }
}
