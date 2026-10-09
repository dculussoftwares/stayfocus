package com.dculus.stayfocused.core.sync

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** Firebase exceptions call `TextUtils`, so these run on Robolectric. */
@RunWith(RobolectricTestRunner::class)
class AuthErrorsTest {
    @Test
    fun wrongPasswordAndUnknownUserAreInvalidCredentials() {
        assertEquals(
            AuthFailure.InvalidCredentials,
            FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "x").toAuthFailure(),
        )
        assertEquals(
            AuthFailure.InvalidCredentials,
            FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "x").toAuthFailure(),
        )
    }

    @Test
    fun malformedEmailIsInvalidEmail() {
        assertEquals(
            AuthFailure.InvalidEmail,
            FirebaseAuthInvalidCredentialsException("ERROR_INVALID_EMAIL", "x").toAuthFailure(),
        )
    }

    @Test
    fun existingEmailIsEmailInUse() {
        assertEquals(
            AuthFailure.EmailInUse,
            FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "x").toAuthFailure(),
        )
    }

    @Test
    fun weakPasswordWinsOverItsCredentialsParent() {
        assertEquals(
            AuthFailure.WeakPassword,
            FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "x", "too short").toAuthFailure(),
        )
    }

    @Test
    fun networkAndUnknownFailures() {
        assertEquals(AuthFailure.Network, FirebaseNetworkException("offline").toAuthFailure())
        assertEquals(AuthFailure.Other, IllegalStateException("boom").toAuthFailure())
    }

    @Test
    fun profileNameFallsBackToTheEmailPrefix() {
        assertEquals("Asha", profileName("Asha", "a@b.co"))
        assertEquals("a", profileName(null, "a@b.co"))
        assertEquals("a", profileName("  ", "a@b.co"))
        assertEquals("Parent", profileName(null, ""))
    }
}
