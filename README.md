# My Habit App

An offline-first **Android** habit tracker that two-way syncs with a personal **Notion** habit database.

Create habits, check them off each day, watch streaks build — with every habit and completion
mirrored to and from Notion. Built natively in Kotlin with Jetpack Compose.

> **Status: pre-alpha. No application code exists yet.**
> The repository currently holds the project's constitution, roadmap and decision log.
> Work has not started on milestone M0.

---

## How it works

The app is a native Android client for a Notion database, not a wrapper around it.

- **Room is the source of truth for the UI.** Every screen reads from the local database and only
  from the local database. The network is never on the render path.
- **Every action succeeds immediately and locally**, online or not. Checking off a habit writes to
  Room and returns — it is never blocked on a request.
- **A background sync engine reconciles the two.** WorkManager pushes local changes up to Notion
  and pulls remote changes down, resolving conflicts last-write-wins per record.
- **Offline is the normal case**, not the error case. Lose connectivity for a week and the app
  keeps working, then catches up.

### Authentication

The app connects using a Notion **internal integration token**, which the owner pastes in once and
which is stored encrypted via the Android Keystore. There is no backend server and no account
system.

The trade-off is deliberate and documented: this build serves **one Notion workspace — the
owner's**. Supporting other people connecting their own workspaces would require OAuth plus a
token-exchange server, because Notion's OAuth flow needs a client secret that cannot be safely
shipped inside an APK. See [ADR-004](docs/decisions.md).

---

## Tech stack

| Concern | Choice |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + unidirectional data flow |
| DI | Hilt |
| Local database | Room (KSP) |
| Preferences | DataStore |
| Networking | Ktor Client (OkHttp engine) |
| Serialization | kotlinx.serialization |
| Background work | WorkManager |
| Navigation | Navigation Compose, type-safe routes |
| Build | Gradle Kotlin DSL + version catalog |

`minSdk 26` · single `:app` module · packaged by feature.

---

## Privacy

- Habit data is transmitted to **Notion**, at the user's explicit instruction, and to nowhere else.
  `api.notion.com` is the only host this app contacts.
- **No analytics. No crash reporting. No telemetry.** The `INTERNET` permission exists for Notion
  sync and for nothing else.
- The integration token never leaves the device except as an authorization header to Notion.

---

## Repository map

| Path | What it is |
| --- | --- |
| [`CLAUDE.md`](CLAUDE.md) | The constitution — architecture, coding rules, guardrails. Read first. |
| [`Plan.md`](Plan.md) | Live state: current step, milestones M0–M14, session log. Read second. |
| [`docs/decisions.md`](docs/decisions.md) | Append-only architecture decision record. |
| `docs/design-spec.md` | Extracted UI spec (written in M0). |
| `docs/tokens.md` | Design tokens from the Figma prototype (M0). |
| `docs/notion-schema.md` | The real Notion database schema (M0). |
| `docs/notion-api.md` | Notion API facts confirmed from live docs (M4). |

---

## Building

Nothing to build yet — the Android project is created in milestone M1.

Once it exists:

```bash
./gradlew assembleDebug
```

```bash
./gradlew ktlintCheck detekt lint test assembleDebug
```

---

## Roadmap

| | Milestone | |
| --- | --- | --- |
| M0 | Design extraction & specification | ← next |
| M1 | Project bootstrap | |
| M2 | Design system in Compose | |
| M3 | Data & domain layer | |
| M4 | **Notion integration & sync engine** | |
| M5 | Navigation shell | |
| M6 | Today screen (the core loop) | |
| M7 | Create & edit habit | |
| M8 | Habit detail & history | |
| M9 | Statistics & insights | |
| M10 | Reminders & notifications | |
| M11 | Settings, theming & data portability | |
| M12 | Onboarding & polish | |
| M13 | Quality hardening | |
| M14 | Release preparation | |

Full detail, step by step, in [`Plan.md`](Plan.md).
