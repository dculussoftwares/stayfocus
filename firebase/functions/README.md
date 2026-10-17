# Stay Focused Cloud Functions

TypeScript, Node 22, 2nd-gen Functions. Every callable is created with `secureCallable()` (`src/lib/secureCallable.ts`):
App Check enforced (token consumed for one-shot operations), sign-in required, per-uid rate limit, `zod` input
validation, a role check in code, typed `HttpsError`s and logs without personal data.

```bash
npm ci
npm run lint && npm run typecheck
npm test            # Jest against the Firestore emulator (needs Java 21+; firebase-tools starts the emulator)
npm run build       # bundles src/functions/*.ts into dist/<name>/ (one deployable folder per function)
```

## Adding a function

1. `src/functions/<name>.ts` exports a `secureCallable({...})` named `<name>`.
2. Add `<name>` to `local.functions` in `infra/firebase/functions.tf` with the roles its service account needs.
   Event-triggered functions (`notifyParent`, `kind = "firestore_created"`) export `onDocumentCreated(...)` instead and
   list the document path in `functions.tf`; Terraform creates the Eventarc trigger and no public invoker. They send
   push messages only through `src/lib/notify.ts`, which builds nothing from client text unsanitised.
3. Test the checks it adds (`authorize`, input schema) next to `test/secureCallable.test.ts`.

## Deploying

Never with the Firebase CLI. The `infra-firebase` workflow runs `npm ci && npm run build` and Terraform zips each
`dist/<name>/`, uploads it under a content-hashed name and updates that function only. `firebase.json` exists for the
local and CI emulators only.
