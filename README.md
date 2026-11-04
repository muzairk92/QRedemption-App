# Redemption Tester (Android)

A small Android app for testing BingoCommerce QR ticket redemption from end to end. It
does what a player tablet will do: scan a customer's ticket QR and redeem it through the
Bingo Portal Caller relay. It then shows exactly what came back and whether that matches
the contract.

It is a **test tool only**. It never loads cards or games; the tablet game app is
separate work by another team.

AI agents and new developers: read [AGENTS.md](AGENTS.md) first. It records the rules,
the contract sources, the architecture and the current status.

```text
This app (acting as Caller + tablet)
   │  POST {portal}/api/caller/redemptions   (JSON, Caller credentials in the body)
   ▼
Bingo Portal  ──►  BingoCommerce  (both unchanged; this repo never edits them)
```

## What it does

1. **Caller setup.** Scan the Caller label: the *OEM / Pre-Authorized* QR on the Portal's
   Product ID batch sheet. Its content is `{"pid":"<Caller ID>","ak":"<API key>"}`
   (`ProductBatchPdfService` in the Portal). You can also type the details in. An
   optional **Check with server** calls the read-only
   `POST /api/caller/verify-credentials`.
2. **Scan a ticket.** Customer QRs look like `BC1.<Hall site ID>.<64 hex>`. You can also
   type or paste the code.
3. **Redeem.** The app saves the attempt on the phone first, then sends it, so the
   original `request_id` survives a crash.
4. **Result.** You see:
   - the status;
   - the Session (MM/DD/YYYY, local time and time zone) and Package;
   - the digital faces per Portal Game Type, with total;
   - **contract checks** run on the raw JSON (echoed IDs, UUID format, field types,
     exact field set, valid dates and time zones);
   - a **verdict** for the scenario: *As expected*, *Not as expected* or *Not confirmed*.
5. **Follow-up tests**, one tap each:

   | Test | Expected answer |
   |---|---|
   | Retry same request | 200, `recovered: true`, same saved result. The app checks it matches the original |
   | Rescan as a new request | 409 `already_redeemed` |
   | Same request, another tablet | 409 `request_id_conflict` |

6. **History.** Every attempt, encrypted on the phone. Open any entry to retry it with
   the same request ID. A badge counts requests that never got a final answer.
7. **Settings:**
   - Portal server: Staging, or a custom address. `http://` is allowed in debug builds
     only, for a Portal running on your Mac.
   - This phone's tablet ID.
   - Caller: check, replace or remove.

## Contract source

Every rule in the app comes from these files in the neighbouring repositories (read
only):

- `bingo_portal/docs/BINGOCOMMERCE_INTEGRATION.md`, section "Caller QR redemption relay"
- `bingo_portal/app/Services/Integration/RedemptionContract.php`: error codes and statuses
- `bingo_portal/app/Http/Requests/Caller/RedeemTicketRequest.php`: request validation
- `bingocommerce/docs/decisions/adr-047-redemption-lifecycle-transport.md`
- `bingocommerce/docs/runbooks/qr-redemption-testing.md`: expected test outcomes

Key rules the app follows:

- **Request ID.**
  - `request_id` is a lowercase UUID v4.
  - It is created once per scan and reused, unchanged, for every retry of that scan.
- **Tablet ID.**
  - `player_tablet_id` is opaque UTF-8 of 1–128 bytes, with no control characters.
  - It is never trimmed or changed.
- **Credentials.** They travel only in the JSON body. Redirects are never followed and
  OkHttp never resends on its own.
- **Unknown outcomes.** After a timeout, 429 or 503, the outcome is unknown. Retry with
  the **same** request ID only.
- **Rate limit.** 15 requests per minute per Caller, shared with the Caller's other
  traffic.

## Architecture

Single-module app following Google's recommended architecture: UI → ViewModel →
repository → data source, with unidirectional data flow.

| Layer | Package | Contents |
|---|---|---|
| Domain | `domain/` | Pure Kotlin rules: `TicketQr`, `CallerQrParser`, `TabletIdRules`, `RequestIdRules`, `BaseUrlRules`, `ScenarioEvaluator`, outcome models |
| Data | `data/network` | `PortalClient` (OkHttp), DTOs, `RedemptionResponseInterpreter`, `RedemptionContractValidator` |
| | `data/storage`, `data/security` | Jetpack DataStore with JSON. The vault (Caller credentials and history) is AES-256-GCM encrypted with an Android Keystore key |
| | `data/PortalRepository` | Saves the attempt, sends it, evaluates it, records it. Runs in an app-wide scope so leaving a screen never loses a result |
| UI | `ui/` | Jetpack Compose + Material 3 (dynamic colour), type-safe Navigation, one ViewModel per screen |
| DI | `di/AppContainer` | Manual dependency injection, with no framework, to keep the app light |

The libraries are all official Google/AndroidX ones, plus OkHttp and kotlinx.serialization:

- Compose
- Material 3
- Navigation
- Lifecycle
- DataStore
- CameraX with ML Kit barcode scanning, through Google Play services so the model isn't
  bundled
- Core SplashScreen

Icons are Google Material Symbols (Rounded), stored as vector drawables.

**Build:**

| Item | Version |
|---|---|
| AGP | 9.5.0, built-in Kotlin |
| Kotlin | 2.4.21 |
| Gradle | 9.8.2 |
| compileSdk | 37 |
| targetSdk | 36 |
| minSdk | 26 (Android 8.0) |

targetSdk is 36 rather than 37 on purpose: apps targeting Android 17 must request the
`ACCESS_LOCAL_NETWORK` runtime permission to reach a LAN server. That would block
testing against a Portal running on a developer machine.

## First-time setup (Mac)

1. Install **Android Studio** from https://developer.android.com/studio (Apple Silicon
   build) and finish its setup wizard. It installs Java and the Android SDK.
2. Open this folder in Android Studio: **File → Open → `andriodApp-QR Redemption`**.
   Android Studio downloads Gradle 9.8.2 and all dependencies.
3. Generate the Gradle wrapper scripts. Gradle's wrapper JAR is a binary file, so it is
   not written by hand. Run from this folder:

   ```bash
   "$(ls -d ~/.gradle/wrapper/dists/gradle-9.8.2-bin/*/gradle-9.8.2 | head -1)/bin/gradle" wrapper --gradle-version 9.8.2
   ```

4. On your phone:
   - Turn on **Developer options** (Settings → About phone → tap Build number 7 times).
   - Turn on **USB debugging**.
   - Plug the phone in.

## Build, test and install

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew lintDebug
```

```bash
./gradlew installDebug
```

```bash
./gradlew connectedDebugAndroidTest
```

- `testDebugUnitTest` runs the JVM unit tests. They need no phone and use a fake server
  (MockWebServer), not staging.
- `connectedDebugAndroidTest` runs the Compose UI tests on the connected phone.
- `assembleRelease` builds a shrunk release APK, to check the app's size.

The debug app installs as **Redemption Tester (debug)** and can sit beside a release
build.

## Testing against staging

1. Settings → Portal server → **Staging** (`https://staging.bingoinnovation.com`).
2. Set up the Caller: scan its OEM label, or type the Caller ID and API key from the
   Portal.
3. Buy a Digital or Hybrid Package on the Hall's store and open the order to show its QR.
4. Scan it, then tap **Redeem ticket**. Then run the follow-up tests.

> A redeemed ticket stays redeemed. Each first-redemption test needs a new paid order.

## Security notes

- The Caller API key and ticket QRs are bearer credentials.
- They are stored only in the encrypted vault, and never logged or shown in full.
- Error previews have them removed.
- Backup and device transfer are disabled.
- Release builds allow HTTPS only.
- The release build is signed with the debug key so it can be installed locally. It is
  not for distribution.
