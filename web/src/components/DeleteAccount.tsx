// Account deletion for the web (Play requirement). Signs the user in with the Firebase JS SDK, then calls the
// `deleteAccount` callable, which deletes the user's data and Auth account (M6-06).
// The Firebase web config is public by design; web.yml writes it to firebase-config.json at deploy time. Without it
// the page tells people to delete the account in the app.
import { type FormEvent, useEffect, useRef, useState } from "react";
import { type FirebaseOptions, initializeApp } from "firebase/app";
import {
  type Auth,
  type User,
  GoogleAuthProvider,
  getAuth,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signInWithPopup,
  signOut,
} from "firebase/auth";
import { type HttpsCallable, getFunctions, httpsCallable } from "firebase/functions";

type Config = FirebaseOptions & { functionsRegion?: string };
type Step = "loading" | "unconfigured" | "signin" | "confirm" | "done";

interface Firebase {
  auth: Auth;
  deleteAccount: HttpsCallable;
}

async function loadConfig(): Promise<Config | null> {
  try {
    const response = await fetch("/firebase-config.json", { cache: "no-store" });
    if (!response.ok) return null;
    const config = (await response.json()) as Config | null;
    return config?.apiKey ? config : null;
  } catch {
    return null;
  }
}

function describe(error: unknown): string {
  const code = (error as { code?: string } | null)?.code;
  switch (code) {
    case "auth/invalid-credential":
    case "auth/wrong-password":
    case "auth/user-not-found":
      return "Wrong email or password.";
    case "auth/popup-closed-by-user":
    case "auth/cancelled-popup-request":
      return "Sign-in was cancelled.";
    case "auth/popup-blocked":
      return "Your browser blocked the sign-in window. Allow pop-ups for this site, or sign in with email.";
    case "functions/unauthenticated":
    case "functions/permission-denied":
      return "Please sign in again and retry.";
    default:
      return "Something went wrong. Please try again.";
  }
}

export default function DeleteAccount() {
  const [firebase, setFirebase] = useState<Firebase | null>(null);
  const [step, setStep] = useState<Step>("loading");
  const [who, setWho] = useState("");
  const [status, setStatus] = useState({ message: "", error: false });
  const [deleting, setDeleting] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  // The account the user confirmed. If the signed-in user changes (another tab, expiry), the confirmation is dropped.
  const confirmedUid = useRef<string | null>(null);

  const say = (message: string, error = false) => setStatus({ message, error });

  useEffect(() => {
    let unsubscribe: (() => void) | undefined;
    let cancelled = false;
    void loadConfig().then((config) => {
      if (cancelled) return;
      if (!config) {
        setStep("unconfigured");
        return;
      }
      const { functionsRegion, ...options } = config;
      const app = initializeApp(options);
      const auth = getAuth(app);
      const deleteAccount = httpsCallable(getFunctions(app, functionsRegion || "europe-west1"), "deleteAccount");
      setFirebase({ auth, deleteAccount });
      setStep("signin");
      unsubscribe = onAuthStateChanged(auth, (user) => {
        if (confirmedUid.current && (!user || user.uid !== confirmedUid.current)) {
          confirmedUid.current = null;
          setStep((current) => (current === "confirm" ? "signin" : current));
        }
      });
    });
    return () => {
      cancelled = true;
      unsubscribe?.();
    };
  }, []);

  function afterSignIn(user: User) {
    confirmedUid.current = user.uid;
    setWho(user.email || user.displayName || "your account");
    setStep("confirm");
  }

  async function signInWithGoogle() {
    if (!firebase) return;
    say("");
    try {
      afterSignIn((await signInWithPopup(firebase.auth, new GoogleAuthProvider())).user);
    } catch (error) {
      say(describe(error), true);
    }
  }

  async function signInWithEmail(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!firebase) return;
    say("");
    try {
      const result = await signInWithEmailAndPassword(firebase.auth, email, password);
      setPassword("");
      afterSignIn(result.user);
    } catch (error) {
      say(describe(error), true);
    }
  }

  async function cancel() {
    if (!firebase) return;
    confirmedUid.current = null;
    await signOut(firebase.auth);
    setStep("signin");
    say("");
  }

  async function deleteEverything() {
    if (!firebase) return;
    if (!window.confirm("Delete your account and all data? This cannot be undone.")) return;
    const current = firebase.auth.currentUser;
    if (!confirmedUid.current || !current || current.uid !== confirmedUid.current) {
      confirmedUid.current = null;
      setStep("signin");
      say("The signed-in account changed. Please sign in again.", true);
      return;
    }
    setDeleting(true);
    say("Deleting...");
    try {
      await firebase.deleteAccount();
      confirmedUid.current = null;
      await signOut(firebase.auth).catch(() => {});
      setStep("done");
      say("Account and all data deleted.");
    } catch (error) {
      say(describe(error), true);
    } finally {
      setDeleting(false);
    }
  }

  return (
    <>
      {step === "unconfigured" && (
        <p className="muted">
          Account deletion is not available on this site yet. Delete your account in the app instead (Profile, Delete
          account and all data).
        </p>
      )}

      {step === "signin" && (
        <section>
          <h2>Step 1. Sign in</h2>
          <button type="button" onClick={signInWithGoogle}>
            Continue with Google
          </button>
          <form onSubmit={signInWithEmail}>
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
            <button type="submit">Sign in with email</button>
          </form>
        </section>
      )}

      {step === "confirm" && (
        <section>
          <h2>Step 2. Confirm</h2>
          <p>
            Signed in as <strong>{who}</strong>.
          </p>
          <button type="button" className="danger" onClick={deleteEverything} disabled={deleting}>
            Delete account and all data
          </button>
          <button type="button" className="secondary" onClick={cancel} disabled={deleting}>
            Sign out
          </button>
        </section>
      )}

      <p id="status" role="status" aria-live="polite" className={status.error ? "error" : undefined}>
        {status.message}
      </p>
    </>
  );
}
