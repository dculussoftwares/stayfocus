package com.dculus.stayfocused.core.blocking.screen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dculus.stayfocused.core.blocking.evaluator.BlockReason
import com.dculus.stayfocused.core.blocking.evaluator.Decision
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.Optional
import javax.inject.Inject

/** What the block screen is asked to show. Travels in the launch intent. */
data class BlockRequest(
    val pkg: String,
    val appLabel: String?,
    val decision: Decision.Block,
) {
    fun toIntent(context: Context): Intent =
        Intent(context, BlockActivity::class.java)
            .putExtra(EXTRA_PKG, pkg)
            .putExtra(EXTRA_LABEL, appLabel)
            .putExtra(EXTRA_REASON, decision.reason.name)
            .putExtra(EXTRA_BLOCK_ID, decision.blockId)
            .putExtra(EXTRA_UNTIL, decision.until?.toEpochMilli() ?: NO_END)

    companion object {
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_BLOCK_ID = "blockId"
        private const val EXTRA_UNTIL = "untilMs"
        private const val NO_END = -1L

        fun from(intent: Intent?): BlockRequest? {
            val pkg = intent?.getStringExtra(EXTRA_PKG)
            val reason =
                intent?.getStringExtra(EXTRA_REASON)?.let { n ->
                    BlockReason.entries.firstOrNull { it.name == n }
                }
            if (intent == null || pkg == null || reason == null) return null
            val until = intent.getLongExtra(EXTRA_UNTIL, NO_END).takeIf { it != NO_END }?.let(Instant::ofEpochMilli)
            return BlockRequest(
                pkg,
                intent.getStringExtra(EXTRA_LABEL),
                Decision.Block(reason, intent.getStringExtra(EXTRA_BLOCK_ID), until),
            )
        }
    }
}

/** Visible-state flag so [BlockScreenLauncher] can tell whether a background launch was allowed. */
internal object BlockScreenState {
    @Volatile
    var visible: Boolean = false
}

/**
 * Shown on top of a blocked app. Own task, not in recents. Back and the button go to the home screen;
 * the screen finishes itself as soon as the decision turns to Allow (checked every second).
 */
@AndroidEntryPoint
class BlockActivity : ComponentActivity() {
    @Inject lateinit var decisionSource: BlockDecisionSource

    @Inject lateinit var extras: Optional<BlockScreenExtras>

    private var request by mutableStateOf<BlockRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val first = BlockRequest.from(intent)
        if (first == null) {
            finish()
            return
        }
        request = first
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(BLOCK_STATUS_BAR_COLOR))
        setContent { StayFocusedTheme { Content() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        BlockRequest.from(intent)?.let { request = it }
    }

    override fun onStart() {
        super.onStart()
        BlockScreenState.visible = true
    }

    override fun onStop() {
        BlockScreenState.visible = false
        super.onStop()
    }

    @Composable
    private fun Content() {
        val current = request ?: return
        BackHandler { goHome() }
        BlockHost(
            request = current,
            decisionSource = decisionSource,
            extras = extras.orElse(null),
            onGoHome = ::goHome,
            onAllow = ::finishAndRemoveTask,
        )
    }

    private fun goHome() {
        startActivity(homeIntent())
        finishAndRemoveTask()
    }
}

internal fun homeIntent(): Intent =
    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
