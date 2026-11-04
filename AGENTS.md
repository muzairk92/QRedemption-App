# AGENTS.md — Redemption Tester (Android)

Read this file first, every session. It records what this app is, how it is built, the
rules it must follow, and exactly where the work stands, so any AI agent or developer can
continue safely. Keep it current: update the **Status** section at the end of every
session.

---

## 1. What this app is

An Android test tool that does what a player tablet will do with a BingoCommerce digital
ticket:

1. scan the customer's ticket QR;
2. redeem it through the Bingo Portal Caller relay;
3. show exactly what came back, and whether it matches the published contract.

It is **for testing only**. It never loads cards or games; a separate Android team builds
the real tablet game app. The project owner uses it on their own Android phone against the
staging Portal.

```text
This app (acts as Caller + player tablet)
   │  POST {portal}/api/caller/redemptions   JSON body, Caller credentials in the body
   ▼
Bingo Portal (Laravel)  ──►  BingoCommerce (WordPress multisite)
```

The app never calls BingoCommerce directly.

## 2. Hard rules (the owner's instructions)

1. **Never edit the neighbouring repositories.** `/Users/abdesign/Project/bingo_portal` and
   `/Users/abdesign/Project/bingocommerce` (and its backups or frontend folders) are
   **read-only** references. All work happens inside this folder.
2. **Never commit, push or create branches unless the owner explicitly asks.** The one
   commit so far (`caa9bbc`, the initial `.gitignore`) was requested. Everything since is
   uncommitted.
3. **Quality bar.** The owner asked for big-tech standards (Google-style):
   - clean architecture and real tests;
   - no shortcuts and no invented facts;
   - a lightweight app with no heavy frameworks.
4. **UI.** Google-app look and feel: Material 3, Material Symbols icons, and every
   user-facing string in `strings.xml`.
5. **No guessing.** Verify every API, version and contract detail against official
   sources or the neighbouring repositories' code and docs. If unsure, ask the owner.
6. **Explaining.** The owner prefers plain-language explanations with concrete examples.

## 3. Contract sources (read these, don't guess)

| Topic | Source (read-only) |
|---|---|
| Relay contract, success/error shapes, retry rules | `bingo_portal/docs/BINGOCOMMERCE_INTEGRATION.md`, section "Caller QR redemption relay" |
| Error codes and HTTP statuses | `bingo_portal/app/Services/Integration/RedemptionContract.php` |
| Request validation (QR regex, UUID, tablet ID) | `bingo_portal/app/Http/Requests/Caller/RedeemTicketRequest.php`, `app/Http/Middleware/CallerRedemptionBoundary.php` |
| Caller authentication | `bingo_portal/app/Http/Middleware/AuthenticateCaller.php` (upper-cases CallerSN) |
| Caller label QR `{"pid","ak"}` | `bingo_portal/app/Services/Device/ProductBatchPdfService.php` |
| Read-only credential check | `bingo_portal/routes/caller.php` → `POST /api/caller/verify-credentials` |
| Commerce redemption logic | `bingocommerce/wp-content/plugins/bingocommerce-digital-tickets/src/RedemptionService.php`, `RedemptionContract.php` |
| Lifecycle design | `bingocommerce/docs/decisions/adr-047-redemption-lifecycle-transport.md` |
| Expected test outcomes, Postman | `bingocommerce/docs/runbooks/qr-redemption-testing.md`, `bingocommerce/tests/postman/qr-redemption.postman_collection.json` |

### Key contract facts implemented in the app

- **Endpoint.** `POST {portal}/api/caller/redemptions`.
  - Headers: `Content-Type: application/json` and `Accept: application/json`.
  - Body has exactly `CallerSN`, `APIKey`, `qr`, `player_tablet_id`, `request_id`.
  - No query string is allowed.
- **Ticket QR.** `BC1.<site id 1–19 digits>.<64 lowercase hex>`, sent exactly as scanned.
- **`request_id`.**
  - A lowercase UUID v4, created once per scan.
  - Reused unchanged for every retry of that scan, including after an app restart.
  - A new ID for an already accepted ticket returns `409 already_redeemed`.
- **`player_tablet_id`.** Opaque UTF-8 of 1–128 bytes with no control characters. It is
  never trimmed or normalised, and it must be unique per physical tablet within a Hall.
- **Success (200) fields.**
  - Top level: `api_version:1`, `request_id`, `player_tablet_id`, `redemption_id`
    (UUID v4), `recovered`, `state:"redeemed"`, `session`, `package`,
    `digital_faces_by_game_type`.
  - `session`: `commerce_session_id`, `name`, `date`, `start_time_local`,
    `timezone_name` and `starts_at_utc`. The last three are all null or all set.
  - `package`: `commerce_package_id`, `name`.
  - Each `digital_faces_by_game_type` entry: `portal_game_type_id`, `faces`, and a
    nullable `game_type_name` (added by the Portal, display only).
- **Errors.** `{api_version:1, error:{code, message, retryable}}`. Codes and statuses are
  in `domain/redemption/RedemptionErrorCode.kt`.
- **Unknown outcomes.** A timeout, 429 or 503 means the outcome is unknown. Retry with
  the **same** request ID; never invent a new one.
- **Rate limit.** 15 requests per minute per CallerSN, shared with the Caller's other
  traffic.
- **Credential check.** `verify-credentials` checks only that the Caller is active, not
  that it is assigned to a Hall. It is limited to 5 requests per minute.
- **Staging host.** `https://staging.bingoinnovation.com`. All four core tests passed
  there on 2026-11-03, from Postman.

## 4. Architecture

Single `:app` module in Google's recommended layers: UI → ViewModel → repository → data
source, with unidirectional data flow and `StateFlow` UI state. Package root:
`com.bingoinnovation.redemptiontester`.

| Package | Responsibility |
|---|---|
| `domain/ticket` | `TicketQr`: parse and mask the BC1 ticket |
| `domain/caller` | `CallerCredentials` (masked `toString`), `CallerCredentialRules`, `CallerQrParser` (OEM label), `CallerCheck` |
| `domain/redemption` | Request and tablet ID rules; outcome models (`RedemptionOutcome` sealed: Success, Rejected, UnexpectedResponse, TransportFailure); `ContractRule`; `RedemptionErrorCode`; `TestScenario`; `ScenarioEvaluator` (verdicts); `RedemptionAttempt` (history record) |
| `domain/server` | `ServerSettings` (Staging or Custom), `BaseUrlRules` |
| `data/network` | `PortalClient` (OkHttp: no redirects, no silent retries, bounded body, never throws for network problems); `HttpExchange`; DTOs; `RedemptionResponseInterpreter`; `RedemptionContractValidator` (checks the raw JSON) |
| `data/security` | `DataCipher`, `KeystoreDataCipher` (AES-256-GCM, Android Keystore key) |
| `data/storage` | `JsonDataStoreSerializer` (DataStore + kotlinx JSON, optional encryption); `SettingsRepository` (plain); `Vault` with `CallerRepository` and `HistoryRepository` (encrypted) |
| `data/PortalRepository` | Saves the attempt **before** sending, sends it, adds the recovery check, evaluates the verdict, saves the result. Runs in the app-wide scope so leaving a screen never loses a result |
| `di/AppContainer` | Manual dependency injection, one instance in `RedemptionTesterApplication` |
| `ui/*` | Compose screens plus ViewModels: `setup` (Caller), `scan` (home), `ticket` (redeem, result, follow-up tests), `history`, `settings`; shared `components`, `theme`, `text` (enum → string resource), `util/DisplayFormats` (MM/DD/YYYY, 12-hour time) |

**Navigation:** type-safe Navigation Compose routes in `ui/navigation/AppNavHost.kt`:

- `CallerSetupRoute(replacing)`
- `ScanRoute`
- `TicketRoute(qr, attemptId?)`
- `HistoryRoute`
- `SettingsRoute`

The start route is set by `MainViewModel`: setup until a Caller is saved, then the
scanner.

**Test scenarios and verdicts** (`domain/redemption/TestScenario.kt`):

| Scenario | Expected answer |
|---|---|
| `FIRST_REDEMPTION` | 200, `recovered:false`, every check passes |
| `RETRY_SAME_REQUEST` | 200, `recovered:true` with the same facts, if this app saw the original succeed |
| `NEW_REQUEST_SAME_TICKET` | 409 `already_redeemed` |
| `OTHER_TABLET_SAME_REQUEST` | 409 `request_id_conflict` |

- **Not confirmed:** every temporary answer (retryable error, 5xx, transport failure).
- **Ticket screen:** the "current request" is sent once, then only retried or probed.

## 5. Build setup

All versions below were verified against official sources (Google Maven, Maven Central,
Gradle services and the AGP release notes) on 2026-11-03.

| Item | Value |
|---|---|
| AGP | **9.5.0**, built-in Kotlin: no `org.jetbrains.kotlin.android` plugin, no `kotlinOptions` |
| Kotlin | **2.4.21**, via the Compose and serialization compiler plugins declared in the root build |
| Gradle | **9.8.2** (wrapper properties with SHA-256). AGP 9.5 needs 9.6+ and JDK 17+ |
| SDK levels | compileSdk **37**, targetSdk **36**, minSdk **26** |
| Libraries | `gradle/libs.versions.toml`: Compose BOM 2026.09.00, CameraX 1.6.2, ML Kit barcode (Play services) 18.3.1, DataStore 1.3.0, Navigation 2.10.2, Lifecycle 2.11.0, OkHttp 4.12.0 (same as Google's Now in Android), kotlinx-serialization 1.12.0, coroutines 1.11.0 |

**targetSdk 36 is deliberate.** Apps targeting API 37 need the runtime
`ACCESS_LOCAL_NETWORK` permission to reach a LAN Portal, which would block local
testing.

**Network security:**

- `src/main` network security config allows HTTPS only.
- `src/debug` allows plain HTTP, and the app also refuses `http://` unless
  `BuildConfig.DEBUG`.
- Debug builds use the application ID suffix `.debug` and the name
  "Redemption Tester (debug)".

**Release build:** `isMinifyEnabled` and `isShrinkResources` are on. It is signed with
the debug key for local installs only (commented in `app/build.gradle.kts`).

**Gradle wrapper:**

- Only `gradle/wrapper/gradle-wrapper.properties` exists.
- `gradlew`, `gradlew.bat` and `gradle-wrapper.jar` are **not** written by hand. Generate
  them from the Gradle distribution once Android Studio has downloaded it (command in
  `README.md`).

## 6. Conventions

- **Kotlin style.** Official style, `.editorconfig` with a 120-character line limit,
  trailing commas, and no wildcard imports.
- **Secrets.** Never log them, and never put them in `toString`, URLs or error text.
  `preview()` in `RedemptionResponseInterpreter.kt` removes the API key and ticket from
  server bodies.
- **Strings.** All in `res/values/strings.xml`, using typographic apostrophes (’).
- **Mapping enums to text.** Use `ui/text/OutcomeText.kt`.
- **Icons.** Material Symbols Rounded, converted from Google's official SVGs
  (`github.com/google/material-design-icons`, `symbols/web/<name>/materialsymbolsrounded`)
  into `res/drawable/ic_<name>.xml` with `<group android:translateY="960">`. Never draw
  icon paths by hand. To add one, fetch the SVG and convert it the same way.
- **Dates.** MM/DD/YYYY for display (BingoCommerce convention); ISO strings on the wire.
- **Errors.** Prefer explicit sealed results over exceptions. Repositories may throw
  `IOException` (storage); ViewModels catch it and show `error_storage`.
- **Tests:**
  - JUnit 4, Truth, `kotlinx-coroutines-test` and MockWebServer.
  - `testing/` has `TestData` (modelled on the real 2026-11-03 staging answer),
    `InMemoryDataStore`, `FakePortalGateway`, `XorDataCipher` and `MainDispatcherRule`.
  - Compose UI tests go in `androidTest`.

## 7. Status (update every session)

### Done (2026-11-03)

- Read both neighbouring repositories in full: docs, routes, the redemption code, and
  the earlier Claude conversations (the Android analysis of 2026-10-06 and the staging
  Postman test of 2026-11-03).
- Wrote the complete project, all uncommitted:
  - Gradle files, manifest, resources and adaptive launcher icon;
  - 44 Kotlin source files;
  - 12 unit-test classes plus 3 test helpers;
  - 1 Compose UI test;
  - 30 Material Symbols icons;
  - `README.md` and this file.
- Static checks passed without a compiler:
  - every `R.string`/`R.plurals` reference is defined and every string is used (239);
  - every drawable reference exists and nothing unused remains;
  - all XML is well-formed;
  - brackets balance in all 60 Kotlin files;
  - every project import resolves.

### Not done or not verified

- **The code has never been compiled, linted or tested.** Android Studio, Java and the
  Android SDK were not installed on the owner's Mac. The owner chose to install Android
  Studio themselves.
- The Gradle wrapper scripts and JAR are not generated yet.
- Never run on the owner's phone or against staging from the app.

### Next steps, in order

1. Once Android Studio is installed:
   - open the project;
   - generate the wrapper (`README.md`);
   - set `JAVA_HOME` to Android Studio's bundled JBR
     (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`) when using the
     command line.
2. Run `./gradlew testDebugUnitTest lintDebug assembleDebug`. Fix every compile error,
   test failure and lint error properly; don't weaken tests or rules to make them pass.
3. Install on the owner's phone (`./gradlew installDebug`, with USB debugging on):
   - set up the Caller from its OEM label;
   - run the four core tests against staging with a **new** paid Digital or Hybrid
     order (each first redemption uses up a ticket).
4. Run `./gradlew connectedDebugAndroidTest` on the phone.
5. Optional, only if the owner asks:
   - the remaining Postman-equivalent scenarios (another Caller, electronic limit,
     wrong Hall, voided ticket);
   - measure the release APK size with `assembleRelease`.

### Known design choices to keep

- **Custom server address.** There is no Production preset on purpose. Its address
  wasn't verified, and testing there would use up real customer tickets. Production can
  be entered as a Custom address.
- **History.** It keeps the 300 newest attempts, stored encrypted. If the Keystore key
  is lost, the vault resets and the Caller must be scanned again.
- **Scanner.** It uses ML Kit through Google Play services (unbundled model), so the
  first scan may wait for the model download; the app shows "Getting the scanner ready".
