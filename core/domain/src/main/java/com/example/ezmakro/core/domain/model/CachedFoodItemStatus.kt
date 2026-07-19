package com.example.ezmakro.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Status of a locally cached OpenFoodFacts entry (ADR-004).
 *
 * - [Original]: untouched OFF data.
 * - [UserModified]: the user edited the entry locally; a sync MUST NEVER
 *   overwrite this automatically. The original OFF snapshot is kept alongside.
 * - [UpdateAvailable]: a newer OFF version exists but the local entry was
 *   either untouched or user-modified; the user decides whether to re-fetch.
 */
@Serializable
sealed interface CachedFoodItemStatus {
    @Serializable
    data object Original : CachedFoodItemStatus

    @Serializable
    data object UserModified : CachedFoodItemStatus

    @Serializable
    data object UpdateAvailable : CachedFoodItemStatus
}
