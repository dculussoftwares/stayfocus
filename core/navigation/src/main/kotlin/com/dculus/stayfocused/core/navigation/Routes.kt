package com.dculus.stayfocused.core.navigation

import kotlinx.serialization.Serializable

/*
 * Type-safe navigation routes. Feature modules never import each other, so the route objects live here and
 * every feature exposes its own `NavGraphBuilder.xxxScreen(...)` / `NavController.navigateToXxx()` pair.
 * Arguments are plain strings or null so every route can later be reached from a deep link.
 */

/** Nested graph: Welcome, SignIn, Permissions. */
@Serializable
data object OnboardingGraph

@Serializable
data object Welcome

@Serializable
data object SignIn

@Serializable
data object Permissions

/** Nested graph: the four tabs and every sub-screen. */
@Serializable
data object MainGraph

@Serializable
data object Home

@Serializable
data object Block

@Serializable
data object Devices

@Serializable
data object Insights

@Serializable
data object Account

/**
 * Block wizard. [target] is the child device id the block is for (null = this phone);
 * [prefill] is an optional template or AI-suggested block to start from (null = empty).
 */
@Serializable
data class BlockWizard(
    val target: String? = null,
    val prefill: String? = null,
)

@Serializable
data object LinkAdd

@Serializable
data object LinkScan

@Serializable
data class LinkConfirm(
    val token: String,
)

@Serializable
data class RemoteDevice(
    val deviceId: String,
)
