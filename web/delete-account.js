// Account deletion for the web (Play requirement). Signs the user in with the Firebase JS SDK, then calls the
// `deleteAccount` callable, which deletes the user's data and Auth account (M6-06).
// The Firebase web config is public by design; it is written to firebase-config.json at deploy time.
import { initializeApp } from "https://www.gstatic.com/firebasejs/12.19.0/firebase-app.js";
import {
  initializeAppCheck,
  ReCaptchaEnterpriseProvider,
} from "https://www.gstatic.com/firebasejs/12.19.0/firebase-app-check.js";
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signInWithEmailAndPassword,
  signOut,
} from "https://www.gstatic.com/firebasejs/12.19.0/firebase-auth.js";
import { getFunctions, httpsCallable } from "https://www.gstatic.com/firebasejs/12.19.0/firebase-functions.js";

const $ = (id) => document.getElementById(id);
const status = $("status");

function say(message, isError = false) {
  status.textContent = message;
  status.classList.toggle("error", isError);
}

async function loadConfig() {
  try {
    const response = await fetch("firebase-config.json", { cache: "no-store" });
    if (!response.ok) return null;
    const config = await response.json();
    return config && config.apiKey ? config : null;
  } catch {
    return null;
  }
}

function describe(error) {
  switch (error && error.code) {
    case "auth/invalid-credential":
    case "auth/wrong-password":
    case "auth/user-not-found":
      return "Wrong email or password.";
    case "auth/popup-closed-by-user":
    case "auth/cancelled-popup-request":
      return "Sign-in was cancelled.";
    case "functions/unauthenticated":
    case "functions/permission-denied":
      return "Please sign in again and retry.";
    default:
      return "Something went wrong. Please try again.";
  }
}

async function main() {
  const config = await loadConfig();
  if (!config) {
    $("unconfigured").hidden = false;
    return;
  }
  const { functionsRegion, appCheckSiteKey, ...firebaseConfig } = config;
  const app = initializeApp(firebaseConfig);
  // The callable enforces App Check. The site key is supplied by Terraform in the
  // public config, so the SDK attaches a token before any callable is invoked.
  if (appCheckSiteKey) {
    initializeAppCheck(app, {
      provider: new ReCaptchaEnterpriseProvider(appCheckSiteKey),
      isTokenAutoRefreshEnabled: true,
    });
  }
  const auth = getAuth(app);
  const deleteAccount = httpsCallable(getFunctions(app, functionsRegion || "europe-west1"), "deleteAccount");

  function show(signedIn) {
    $("signin").hidden = signedIn;
    $("confirm").hidden = !signedIn;
  }

  function afterSignIn(user) {
    $("who").textContent = user.email || user.displayName || "your account";
    show(true);
  }

  show(false);

  $("google").addEventListener("click", async () => {
    say("");
    try {
      const result = await signInWithPopup(auth, new GoogleAuthProvider());
      afterSignIn(result.user);
    } catch (error) {
      say(describe(error), true);
    }
  });

  $("email-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    say("");
    try {
      const result = await signInWithEmailAndPassword(auth, $("email").value, $("password").value);
      $("password").value = "";
      afterSignIn(result.user);
    } catch (error) {
      say(describe(error), true);
    }
  });

  $("cancel").addEventListener("click", async () => {
    await signOut(auth);
    show(false);
    say("");
  });

  $("delete").addEventListener("click", async () => {
    if (!window.confirm("Delete your account and all data? This cannot be undone.")) return;
    $("delete").disabled = true;
    say("Deleting...");
    try {
      await deleteAccount();
      await signOut(auth).catch(() => {});
      $("signin").hidden = true;
      $("confirm").hidden = true;
      say("Account and all data deleted.");
    } catch (error) {
      say(describe(error), true);
      $("delete").disabled = false;
    }
  });
}

main();
