@file:Suppress("MagicNumber")

package com.dculus.stayfocused.kids

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.blocking.evaluator.Decision
import com.dculus.stayfocused.core.blocking.screen.BlockScreenExtras
import com.dculus.stayfocused.core.sync.REQUEST_MINUTES_OPTIONS
import com.dculus.stayfocused.core.sync.UnlockRequestRepository
import com.dculus.stayfocused.core.ui.components.WhiteButton
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.kids.monitoring.ParentLinkStore
import com.dculus.stayfocused.kids.monitoring.PendingRequestStore
import com.dculus.stayfocused.kids.monitoring.ReportTargetStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Inject

internal const val ASK_BUTTON_TAG = "ask_for_more_time"
internal const val ASK_OPTION_TAG_PREFIX = "ask_option_"
internal const val ASK_SENT_TAG = "ask_sent"

/** Where the "ask for more time" flow of the block screen stands. */
internal enum class AskStage { None, Pick, Sent }

/** Prototype `dlabel`: 15 -> "15m", 60 -> "1h", 90 -> "1h30". */
internal fun minutesLabel(mins: Int): String =
    when {
        mins < 60 -> "${mins}m"
        mins % 60 == 0 -> "${mins / 60}h"
        else -> "${mins / 60}h${mins % 60}"
    }

/** The block screen's extra action (prototype "Kids: Blocked overlay"): ask, pick 15 / 30 / 60 min, "Request sent". */
@Composable
internal fun AskForMoreTime(
    parent: String,
    stage: AskStage,
    onAsk: () -> Unit,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (stage) {
            AskStage.None -> {
                WhiteButton(
                    text = stringResource(R.string.kids_ask_button, parent),
                    onClick = onAsk,
                    modifier = Modifier.fillMaxWidth().testTag(ASK_BUTTON_TAG),
                )
            }

            AskStage.Pick -> {
                Text(
                    stringResource(R.string.kids_ask_how_much),
                    style = StayFocusedTheme.type.label,
                    color = colors.secondary,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    REQUEST_MINUTES_OPTIONS.forEach { mins ->
                        Surface(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(60.dp)
                                    .testTag(ASK_OPTION_TAG_PREFIX + mins)
                                    .clickable(role = Role.Button) { onPick(mins) },
                            shape = RoundedCornerShape(16.dp),
                            color = colors.panel,
                            border = BorderStroke(1.dp, colors.hairline14),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    minutesLabel(mins),
                                    style = StayFocusedTheme.type.numeric,
                                    color = colors.text,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }

            AskStage.Sent -> {
                Column(
                    modifier = Modifier.testTag(ASK_SENT_TAG),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.kids_ask_sent, parent),
                        style = StayFocusedTheme.type.bodyL,
                        color = colors.text,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.kids_ask_sent_hint),
                        style = StayFocusedTheme.type.bodyS,
                        color = colors.secondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Binds the flow to this phone: it shows only while a parent is linked, and a pick creates the request document.
 * [Context] is used for the app's label, which is all the parent sees of it.
 */
class KidsBlockScreenExtras
    @Inject
    internal constructor(
        private val links: ParentLinkStore,
        private val targets: ReportTargetStore,
        private val requests: UnlockRequestRepository,
        private val pending: PendingRequestStore,
    ) : BlockScreenExtras {
        @Composable
        override fun Content(
            pkg: String,
            decision: Decision.Block,
            modifier: Modifier,
        ) {
            val target = targets.target ?: return
            val context = LocalContext.current
            var stage by remember(pkg) { mutableStateOf(AskStage.None) }
            AskForMoreTime(
                parent = links.parentName ?: stringResource(R.string.kids_parent_fallback),
                stage = stage,
                onAsk = { stage = AskStage.Pick },
                onPick = { mins ->
                    // The server may refuse the write later; the id is known only after `create` returns.
                    var created: String? = null
                    val id =
                        requests.create(target, appLabel(context, pkg), mins) {
                            Timber.w("The request for more time was refused")
                            created?.let(pending::forget)
                            stage = AskStage.None
                        }
                    created = id
                    if (id.isNotBlank()) pending.remember(id, pkg)
                    stage = AskStage.Sent
                },
                modifier = modifier,
            )
        }
    }

private fun appLabel(
    context: Context,
    pkg: String,
): String =
    try {
        val info = context.packageManager.getApplicationInfo(pkg, 0)
        context.packageManager
            .getApplicationLabel(info)
            .toString()
            .ifBlank { pkg.substringAfterLast('.') }
    } catch (e: PackageManager.NameNotFoundException) {
        Timber.d(e, "No label for %s", pkg)
        pkg.substringAfterLast('.')
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class KidsBlockScreenModule {
    @Binds
    abstract fun extras(impl: KidsBlockScreenExtras): BlockScreenExtras
}
