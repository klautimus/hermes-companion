# Plan 022: Email 2FA UI Flow

> **Executor**: Use CodeGraph MCP tools for Android at `/home/kevin/.hermes/projects/HermesCompanion`.

## Status
- **Priority**: P0 | **Effort**: M | **Risk**: LOW | **Depends on**: daemon 017 | **Category**: feature
- **Planned at**: commit `4a85552`, 2026-06-19

## Why this matters
Email 2FA is required for public launch. The backend (Plan 017) provides the endpoints; this plan builds the Android UI flow.

## Current state
- Login flow: SetupWizardScreen → ServerConnectionScreen → CredentialsScreen → BoardSelectionScreen
- No 2FA prompt exists

## Scope
**In scope**: `SetupWizardScreen.kt` (add 2FA step), `ApiClient.kt` (add 2FA methods), `MainViewModel.kt` (add 2FA state)
**Out of scope**: Backend (daemon Plan 017)

## Steps

### Step 1: Add 2FA verification screen
Create a `TwoFactorScreen` composable that:
- Shows "Enter the 6-digit code sent to your email"
- Has a 6-digit OTP input (auto-advancing digit boxes or single text field)
- Submit button calls `POST /api/auth/2fa/verify` with challenge_id + code
- Resend button calls `POST /api/auth/2fa/resend`
- On success: proceed to board selection
- On failure: show error, clear input

### Step 2: Modify login flow
After CredentialsScreen succeeds, check response for `requires_2fa`:
- If true: navigate to TwoFactorScreen with the challenge_id
- If false: proceed directly to board selection

### Step 3: Add 2FA setup/disable in Settings
In SettingsScreen, add:
- Toggle: "Enable Email 2FA" (calls `/api/auth/2fa/setup`)
- Toggle: "Disable 2FA" (requires OTP verification first)

### Step 4: Add ApiClient methods
```kotlin
suspend fun verify2fa(challengeId: String, code: String): String
suspend fun setup2fa(): String
suspend fun disable2fa(code: String): String
suspend fun resend2fa(challengeId: String): String
```

## Done criteria
- [ ] `./gradlew assembleDebug` succeeds
- [ ] `./gradlew test` passes
- [ ] Login flow shows 2FA prompt when enabled
- [ ] Correct code → proceeds to board selection
- [ ] Wrong code → error shown
- [ ] `git status` clean; `git log -1` shows commit
