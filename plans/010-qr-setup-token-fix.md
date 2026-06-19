# Plan 010: Fix QR Setup Token Flow End-to-End

> **Executor instructions**: Follow this plan step by step.
>
> **Drift check (run first)**: `cd ~/.hermes/companion && git diff --stat 98f270d..HEAD -- server.py setup_wizard.py`
> Also: `cd ~/.hermes/projects/HermesCompanion && git diff --stat e44d810..HEAD -- app/src/main/java/org/hermes/community/companion/SetupWizardScreen.kt app/src/main/java/org/hermes/community/companion/data/ApiClient.kt`

## Status

- **Priority**: P0
- **Effort**: S
- **Risk**: LOW
- **Depends on**: 009 (daemon must have /api/setup/redeem endpoint)
- **Category**: bug (broken end-to-end feature)
- **Planned at**: commit `98f270d` (daemon), `e44d810` (Android), 2026-06-19

## Why this matters

The QR code setup flow is broken. The daemon's `setup_wizard.py` generates a QR code with `token=` parameter (line 176), but:
1. The daemon has NO endpoint to redeem the token (Plan 009 adds this)
2. The Android app's `parseQrUri` in SetupWizardScreen.kt handles both `token=` and `pass=`, but when it gets `token=`, it calls `redeemSetupToken()` which hits the non-existent `/api/setup/redeem` endpoint → 404 → setup fails

## Current state

### setup_wizard.py generate_qr_code (line 163-180)
Generates URI: `hermescompanion://configure?url=...&user=...&token=...&board=...`
The `token=` parameter is a one-time token, NOT the actual password.

### SetupWizardScreen.kt parseQrUri
Parses QR URI extracting `url`, `user`, `pass`, `token`, `board` parameters.
When `token=` is present (and `pass=` is absent), it calls `redeemSetupToken()` which hits `POST /api/setup/redeem`.
When `pass=` is present, it uses the password directly.

### ApiClient.kt redeemSetupToken (lines 193-223)
Calls `POST /api/setup/redeem` with `{"token": "<token>"}`.
Expects response: `{"username":"...", "password":"...", "host":"...", "port":8777, "board":"..."}`

## Scope

**In scope**:
- `~/.hermes/companion/setup_wizard.py` — verify QR URI generation includes both `token=` and `pass=` as fallback
- `~/.hermes/projects/HermesCompanion/app/src/main/java/org/hermes/community/companion/SetupWizardScreen.kt` — verify redeem flow calls correct endpoint after Plan 009 adds it

**Out of scope**:
- Daemon server.py changes (done in Plan 009)
- Android ApiClient changes (done in Plan 009)

## Steps

### Step 1: Verify daemon QR generation is consistent

Check that `setup_wizard.py:generate_qr_code()` (line 163) generates URI with `token=` parameter. It does — confirmed at line 176: `"token": token`.

### Step 2: Verify Android parseQrUri handles token correctly

In SetupWizardScreen.kt, find `parseQrUri` function. Verify it:
1. Extracts `token` from URI query parameter
2. When token is present, calls `redeemSetupToken(baseUrl, token)` 
3. On success, saves returned username + password to SessionManager
4. On failure (404), shows clear error message

The redeem flow should now work after Plan 009 adds the `/api/setup/redeem` endpoint. This plan is a verification gate.

### Step 3: Test end-to-end QR flow

After Plan 009 is implemented:
1. Run `hermes-companion setup` on server → generates QR with token
2. Scan QR in Android app
3. App calls `POST /api/setup/redeem` with token
4. Daemon returns credentials
5. App saves credentials and proceeds to main screen

**Verify**: Manual test with real QR code scan. If no physical device available, verify with:
```bash
# Get token from setup_token.json
TOKEN=$(python3 -c "import json; d=json.load(open('$HOME/.hermes/companion/setup_token.json')); print(d['tokens'][0]['token'])")
curl -s http://127.0.0.1:8777/api/setup/redeem -d "{"token":"$TOKEN"}" -H 'Content-Type: application/json'
```
Expected: JSON with username, password, host, port, board.

### Step 4: Commit verification

If any fixes were needed during verification, commit them.

## Done criteria

- [ ] `curl` test of `/api/setup/redeem` with valid token returns 200 + credentials
- [ ] `curl` test with expired token returns 410
- [ ] `curl` test with invalid token returns 403
- [ ] Android `redeemSetupToken()` function compiles and is callable
- [ ] `git status` is CLEAN

## STOP conditions

- If the daemon returns 404 for `/api/setup/redeem` — Plan 009 was not implemented correctly. Re-check the route registration and middleware exemption.
- If the token in `setup_token.json` has expired (>5 min) — re-run `hermes-companion setup` to generate a fresh token.
