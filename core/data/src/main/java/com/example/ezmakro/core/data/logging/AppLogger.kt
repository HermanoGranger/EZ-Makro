package com.example.ezmakro.core.data.logging

import co.touchlab.kermit.Logger

/**
 * Thin wrapper around Kermit so the rest of the app depends on a single,
 * stable logging facade instead of the raw library. No Android dependency is
 * introduced here, keeping [log] usable from any layer.
 */
object AppLogger {
    private val logger = Logger.withTag("EZMakro")

    fun v(message: String, tag: String? = null) =
        logger.v(message, tag = tag ?: "EZMakro")

    fun d(message: String, tag: String? = null) =
        logger.d(message, tag = tag ?: "EZMakro")

    fun i(message: String, tag: String? = null) =
        logger.i(message, tag = tag ?: "EZMakro")

    fun w(message: String, tag: String? = null) =
        logger.w(message, tag = tag ?: "EZMakro")

    fun e(message: String, throwable: Throwable? = null, tag: String? = null) =
        logger.e(message, throwable, tag ?: "EZMakro")
}
