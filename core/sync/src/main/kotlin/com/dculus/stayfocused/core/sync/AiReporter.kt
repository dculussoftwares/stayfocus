package com.dculus.stayfocused.core.sync

import com.dculus.stayfocused.core.model.BlockDraft
import com.dculus.stayfocused.core.model.BlockType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What "Report a bad result" sends: the user's [sentence] and the [draft] the builder produced from it.
 * [draftFields] is exactly what is written under `draft`, so the confirm sheet can show it verbatim.
 */
data class AiReport(
    val sentence: String,
    val draft: BlockDraft,
) {
    /** Ordered, only the fields that matter for the draft's type; values are strings, numbers or string lists. */
    val draftFields: Map<String, Any>
        get() {
            val fields = linkedMapOf<String, Any>("type" to draft.type.name, "apps" to draft.apps.toList())
            when (draft.type) {
                BlockType.LIMIT -> {
                    fields["period"] = draft.period.name
                    fields["mins"] = draft.mins
                }

                BlockType.CYCLE -> {
                    fields["use"] = draft.use
                    fields["rest"] = draft.rest
                }

                BlockType.SCHEDULE -> {
                    fields["range"] = draft.range.format()
                }

                BlockType.NOW -> {
                    fields["now"] = draft.now
                }
            }
            fields["days"] = draft.days.mask
            if (draft.aiNote.isNotBlank()) fields["note"] = draft.aiNote.take(MAX_NOTE_LENGTH)
            return fields
        }

    companion object {
        const val MAX_NOTE_LENGTH = 300
    }
}

/** Sends [AiReport]s to the `aiReports` collection. Never throws (other than cancellation). */
interface AiReporter {
    /** True when the report was written. */
    suspend fun report(report: AiReport): Boolean
}

/** Firestore `aiReports/{id}`: `{uid, sentence, draft, createdAt}`; create-only for a signed-in account (see rules). */
@Singleton
class FirebaseAiReporter
    @Inject
    internal constructor(
        private val availability: FirebaseAvailability,
    ) : AiReporter {
        @Suppress("TooGenericExceptionCaught")
        override suspend fun report(report: AiReport): Boolean {
            if (!availability.isConfigured) return false
            return try {
                // Reporting needs an account (the rules refuse anonymous sessions); it never signs anyone in.
                val uid =
                    FirebaseAuth
                        .getInstance()
                        .currentUser
                        ?.takeUnless { it.isAnonymous }
                        ?.uid
                if (uid != null) write(uid, report)
                uid != null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.w(e, "Reporting the AI result failed")
                false
            }
        }

        private suspend fun write(
            uid: String,
            report: AiReport,
        ) {
            FirebaseFirestore
                .getInstance()
                .collection(COLLECTION)
                .add(
                    mapOf(
                        "uid" to uid,
                        "sentence" to report.sentence.trim().take(AiPrompt.MAX_SENTENCE_LENGTH),
                        "draft" to report.draftFields,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
        }

        private companion object {
            const val COLLECTION = "aiReports"
        }
    }
