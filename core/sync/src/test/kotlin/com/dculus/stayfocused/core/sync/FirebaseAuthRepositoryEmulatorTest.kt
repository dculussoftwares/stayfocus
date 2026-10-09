package com.dculus.stayfocused.core.sync

import android.os.Looper
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs [FirebaseAuthRepository] against the Firebase Auth and Firestore emulators. Skipped unless
 * `FIREBASE_AUTH_EMULATOR_HOST` is set; CI starts the emulators with `firebase emulators:exec` (job `auth-emulator`).
 * Firestore runs with `firebase/firestore.rules` (see `firebase/firebase.json`), so the profile write is also checked
 * against the real `users/{uid}` rules.
 */
@RunWith(RobolectricTestRunner::class)
class FirebaseAuthRepositoryEmulatorTest {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var repository: FirebaseAuthRepository

    @After
    fun tearDown() {
        if (::repository.isInitialized) repository.close()
        if (::auth.isInitialized) auth.signOut()
    }

    @Before
    fun setUp() {
        val authHost = System.getenv("FIREBASE_AUTH_EMULATOR_HOST")
        val firestoreHost = System.getenv("FIRESTORE_EMULATOR_HOST")
        assumeTrue("Auth emulator not running", !authHost.isNullOrBlank() && !firestoreHost.isNullOrBlank())
        val (sharedAuth, sharedFirestore) =
            shared ?: connect(authHost!!, firestoreHost!!).also { shared = it }
        auth = sharedAuth
        firestore = sharedFirestore
        repository = FirebaseAuthRepository(auth, firestore)
    }

    /**
     * Runs [block] off the main thread and keeps the Robolectric main looper turning: Firebase delivers task results
     * on the main looper, which a plain `runBlocking` on the test thread would starve.
     */
    private fun runIdling(block: suspend () -> Unit) {
        val job = CoroutineScope(Dispatchers.Default).async { block() }
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!job.isCompleted && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(POLL_MS)
        }
        check(job.isCompleted) { "Timed out waiting for the emulator" }
        job.getCompleted()
    }

    private companion object {
        /** Firebase instances are process-wide and `useEmulator` may only run once, so all tests share them. */
        var shared: Pair<FirebaseAuth, FirebaseFirestore>? = null

        fun connect(
            authHost: String,
            firestoreHost: String,
        ): Pair<FirebaseAuth, FirebaseFirestore> {
            val options =
                FirebaseOptions
                    .Builder()
                    .setProjectId("demo-stayfocus")
                    .setApplicationId("1:1:android:1")
                    .setApiKey("fake-api-key")
                    .build()
            val app = FirebaseApp.initializeApp(RuntimeEnvironment.getApplication(), options)
            val auth = FirebaseAuth.getInstance(app)
            auth.useEmulator(authHost.substringBefore(':'), authHost.substringAfter(':').toInt())
            val firestore = FirebaseFirestore.getInstance(app)
            firestore.useEmulator(firestoreHost.substringBefore(':'), firestoreHost.substringAfter(':').toInt())
            return auth to firestore
        }

        const val TIMEOUT_MS = 60_000L
        const val POLL_MS = 10L
        const val PROFILE_POLLS = 100
    }

    /** The profile is written in the background after sign-in, so wait for it. */
    private suspend fun awaitProfile(uid: String): DocumentSnapshot {
        repeat(PROFILE_POLLS) {
            val doc =
                firestore
                    .collection("users")
                    .document(uid)
                    .get(Source.SERVER)
                    .await()
            if (doc.exists() && doc.getTimestamp("createdAt") != null) return doc
            delay(POLL_MS * 10)
        }
        error("The profile was not created")
    }

    private fun newEmail() = "user-${UUID.randomUUID()}@example.com"

    @Test
    fun createAccountSignsInAndWritesTheProfile() =
        runIdling {
            val email = newEmail()
            assertEquals(AuthResult.Success, repository.createAccount(email, "secret1"))

            val user = repository.currentUser.filterNotNull().first()
            assertEquals(email, user.email)
            val profile = awaitProfile(user.uid)
            assertEquals(email, profile.getString("email"))
            assertEquals(email.substringBefore('@'), profile.getString("displayName"))
            assertNotNull(profile.getTimestamp("createdAt"))
        }

    @Test
    fun aNewRepositoryRepairsAMissingProfileForTheRestoredSession() =
        runIdling {
            // The session exists, but no repository was around to write its profile (as after an interrupted write).
            repository.close()
            auth.createUserWithEmailAndPassword(newEmail(), "secret1").await()
            val uid = checkNotNull(auth.currentUser).uid
            assertTrue(
                firestore
                    .collection("users")
                    .document(uid)
                    .get(Source.SERVER)
                    .await()
                    .exists()
                    .not(),
            )

            // A restart: a new repository finds the persisted session and creates the profile without a sign-in.
            repository = FirebaseAuthRepository(auth, firestore)

            assertNotNull(awaitProfile(uid))
        }

    @Test
    fun googleSignInUsesTheIdTokenAndWritesTheProfile() =
        runIdling {
            val email = newEmail()
            // The Auth emulator accepts an unsigned JSON object as a Google ID token.
            val token = """{"sub":"${UUID.randomUUID()}","email":"$email","email_verified":true}"""
            val google =
                FirebaseAuthRepository(auth, firestore, tokenProvider = { GoogleTokenResult.Token(token) })
            try {
                assertEquals(AuthResult.Success, google.signInWithGoogle(RuntimeEnvironment.getApplication()))
                val user = google.currentUser.filterNotNull().first()
                assertEquals(email, user.email)
                assertEquals(email, awaitProfile(user.uid).getString("email"))
            } finally {
                google.close()
            }
        }

    @Test
    fun cancellingGoogleSignInIsNotAnErrorAndKeepsNoSession() =
        runIdling {
            val google = FirebaseAuthRepository(auth, firestore, tokenProvider = { GoogleTokenResult.Cancelled })
            try {
                assertEquals(AuthResult.Cancelled, google.signInWithGoogle(RuntimeEnvironment.getApplication()))
                assertNull(auth.currentUser)
            } finally {
                google.close()
            }
        }

    @Test
    fun signOutThenSignInAgain() =
        runIdling {
            val email = newEmail()
            repository.createAccount(email, "secret1")
            repository.signOut()
            assertNull(repository.currentUser.first())

            assertEquals(AuthResult.Success, repository.signInWithEmail(email, "secret1"))
            assertEquals(email, repository.currentUser.first()?.email)
        }

    @Test
    fun wrongPasswordIsInvalidCredentials() =
        runIdling {
            val email = newEmail()
            repository.createAccount(email, "secret1")
            repository.signOut()

            val result = repository.signInWithEmail(email, "not-it-at-all")
            assertEquals(AuthResult.Failure(AuthFailure.InvalidCredentials), result)
        }

    @Test
    fun duplicateEmailAndWeakPasswordAreReported() =
        runIdling {
            val email = newEmail()
            repository.createAccount(email, "secret1")
            repository.signOut()

            assertEquals(AuthResult.Failure(AuthFailure.EmailInUse), repository.createAccount(email, "secret1"))
            assertEquals(AuthResult.Failure(AuthFailure.WeakPassword), repository.createAccount(newEmail(), "123"))
        }

    @Test
    fun passwordResetSucceedsForKnownAndUnknownAddresses() =
        runIdling {
            val email = newEmail()
            repository.createAccount(email, "secret1")
            assertTrue(repository.sendPasswordReset(email) is AuthResult.Success)
            assertTrue(repository.sendPasswordReset(newEmail()) is AuthResult.Success)
        }
}
