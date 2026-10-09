package com.dculus.stayfocused.core.sync

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

/** Maps a Firebase Auth exception to the failure the UI words. Weak password is first: it is a credentials subtype. */
internal fun Throwable.toAuthFailure(): AuthFailure =
    when (this) {
        is FirebaseAuthWeakPasswordException -> AuthFailure.WeakPassword

        is FirebaseAuthUserCollisionException -> AuthFailure.EmailInUse

        is FirebaseAuthInvalidCredentialsException,
        is FirebaseAuthInvalidUserException,
        -> AuthFailure.InvalidCredentials

        is FirebaseNetworkException -> AuthFailure.Network

        else -> AuthFailure.Other
    }
