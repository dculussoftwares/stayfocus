package com.dculus.stayfocused.core.sync

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import timber.log.Timber

/** Gets a Google ID token for the account the user picks. Replaced by a fake in tests. */
internal fun interface GoogleIdTokenProvider {
    suspend fun getIdToken(activityContext: Context): GoogleTokenResult
}

internal sealed interface GoogleTokenResult {
    data class Token(
        val idToken: String,
    ) : GoogleTokenResult

    data object Cancelled : GoogleTokenResult

    data class Failed(
        val reason: AuthFailure,
    ) : GoogleTokenResult
}

/** Credential Manager's "Sign in with Google" button flow. It needs an Activity context to show the picker. */
internal class CredentialManagerTokenProvider : GoogleIdTokenProvider {
    override suspend fun getIdToken(activityContext: Context): GoogleTokenResult {
        val clientId =
            webClientId(activityContext) ?: return GoogleTokenResult.Failed(AuthFailure.Other)
        val request =
            GetCredentialRequest
                .Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
                .build()
        return try {
            val credential =
                CredentialManager
                    .create(
                        activityContext,
                    ).getCredential(activityContext, request)
                    .credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleTokenResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleTokenResult.Failed(AuthFailure.Other)
            }
        } catch (e: GetCredentialException) {
            Timber.d(e, "Google credential request ended")
            e.toTokenResult()
        } catch (e: GoogleIdTokenParsingException) {
            Timber.w(e, "Could not read the Google ID token")
            GoogleTokenResult.Failed(AuthFailure.Other)
        }
    }

    /** `default_web_client_id` is generated into the app module from `google-services.json`; look it up by name. */
    private fun webClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return id.takeIf { it != 0 }?.let(context::getString)?.takeIf { it.isNotBlank() }
    }
}

/** Cancelling the picker is not an error; no Google account on the device and a dropped connection are worded apart. */
internal fun GetCredentialException.toTokenResult(): GoogleTokenResult =
    when (this) {
        is GetCredentialCancellationException -> GoogleTokenResult.Cancelled
        is NoCredentialException -> GoogleTokenResult.Failed(AuthFailure.NoGoogleAccount)
        is GetCredentialInterruptedException -> GoogleTokenResult.Failed(AuthFailure.Network)
        else -> GoogleTokenResult.Failed(AuthFailure.Other)
    }
