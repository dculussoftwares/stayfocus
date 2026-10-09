package com.dculus.stayfocused.core.sync

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import org.junit.Test
import kotlin.test.assertEquals

class GoogleIdTokenProviderTest {
    private fun map(e: GetCredentialException) = e.toTokenResult()

    @Test fun cancellingThePickerIsCancelled() =
        assertEquals(GoogleTokenResult.Cancelled, map(GetCredentialCancellationException()))

    @Test fun noGoogleAccountIsWordedApart() =
        assertEquals(GoogleTokenResult.Failed(AuthFailure.NoGoogleAccount), map(NoCredentialException()))

    @Test fun anInterruptedRequestIsANetworkProblem() =
        assertEquals(GoogleTokenResult.Failed(AuthFailure.Network), map(GetCredentialInterruptedException()))

    @Test fun anythingElseIsOther() =
        assertEquals(GoogleTokenResult.Failed(AuthFailure.Other), map(GetCredentialUnknownException()))
}
