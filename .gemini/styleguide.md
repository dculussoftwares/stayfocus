# Stay Focused review guide

Free, open-source (GPL-3.0) Android app blocker plus a companion Kids app. Read `AGENTS.md` and
`docs/IMPLEMENTATION_PLAN.md` for the rules; the points below are the ones to check on every PR.

- Kotlin + Jetpack Compose; follow the conventions in `docs/IMPLEMENTATION_PLAN.md` §5.
- Play policy: no new permission, manifest flag or `QUERY_ALL_PACKAGES` unless the story says so.
- The project must build without `google-services.json`.
- Public repo: flag any secret, keystore, `google-services.json`, Terraform state or real user data.
- Copy is final: UI strings must match `design_handoff_stay_focused_phase1/` verbatim.
- Infrastructure changes only through Terraform in `infra/`, never console or CLI creation.
- The PR must stay inside its story's scope and `Closes #<issue>`; flag unrelated changes.
- Tests: new logic needs unit tests; flag missing coverage for rule evaluation, time ranges and parsing.
