package com.dculus.stayfocused.feature.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

internal const val KIDS_PACKAGE = "com.dculus.stayfocused.kids"
internal const val KIDS_MARKET_URI = "market://details?id=$KIDS_PACKAGE"
internal const val KIDS_WEB_URI = "https://play.google.com/store/apps/details?id=$KIDS_PACKAGE"

private fun viewIntent(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Opens the Stay Focused Kids Play listing: `market://` first, the `https` page when no Play Store is installed. */
internal fun openKidsListing(context: Context) {
    try {
        context.startActivity(viewIntent(KIDS_MARKET_URI))
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(viewIntent(KIDS_WEB_URI))
        } catch (_: ActivityNotFoundException) {
            // No Play Store and no browser: nothing more we can do.
        }
    }
}
