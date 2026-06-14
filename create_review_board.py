#!/usr/bin/env python3
"""Create companion-review kanban cards using hermes CLI."""

import subprocess, json, os, sys

def kanban_create(title, assignee, body, board="companion-review", priority=1):
    """Create a kanban task and return its task_id."""
    env = os.environ.copy()
    env["HERMES_KANBAN_BOARD"] = board
    cmd = ["hermes", "kanban", "create", title, "--assignee", assignee,
           "--body", body, "--priority", str(priority)]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=60, env=env)
    if r.returncode != 0:
        print(f"FAILED: {r.stderr.strip()}", file=sys.stderr)
        return None
    # Parse stdout for task_id. Format varies — try to extract t_<hex>
    for line in r.stdout.split("\n"):
        line = line.strip()
        if line.startswith("t_"):
            print(f"  {line} {title[:60]}")
            return line
    # Fallback: try JSON
    try:
        d = json.loads(r.stdout)
        if "task_id" in d:
            print(f"  {d['task_id']} {title[:60]}")
            return d["task_id"]
    except:
        pass
    print(f"  ??? {r.stdout[:120]}")
    return None

def kanban_link(parent_id, child_id, board="companion-review"):
    """Link parent→child."""
    env = os.environ.copy()
    env["HERMES_KANBAN_BOARD"] = board
    cmd = ["hermes", "kanban", "link", parent_id, child_id]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=30, env=env)
    return r.returncode == 0

# Phase 1 — parallel review cards (no parents)
phase1 = {}

print("Phase 1 — Parallel Review Cards:")
print("-" * 60)

t_build = kanban_create(
    "Review: Build & Gradle config",
    "analyst",
    """## Pipeline Discipline
Load skill_view(name="using-agent-skills") router -> skill_view(name="code-review-and-quality") (five-axis review). interview-me SKIPS — review locked in git:REVIEW.md. No deviations.

## Scope
Review ALL build/config files in HermesCompanion repo:
- app/build.gradle — compiler plugins, deps, compileSdk, minify, ProGuard
- build.gradle (root) — plugin classpath (kotlinx-serialization!)
- settings.gradle — plugin mgmt, repos
- gradle.properties — JVM args
- app/proguard-rules.pro — completeness
- AndroidManifest.xml — permissions, cleartext

## Known Issues
1. BLOCKING: Missing kotlinx-serialization plugin -> SessionsList crash
2. Root build.gradle may need serialization classpath
3. versionCode/versionName -> bump to 1/1.1.0 after fixes
4. ProGuard keep-rules for kotlinx.serialization + OkHttp

## Workdir: /home/kevin/.hermes/projects/HermesCompanion
## Deliverable: Self-review before completing. List findings with file:line, severity, fix.""")
if t_build: phase1["build"] = t_build

t_data = kanban_create(
    "Review: Data Layer (Models, ApiClient, SessionManager)",
    "analyst",
    """## Pipeline Discipline
Load skill_view(name="using-agent-skills") router -> skill_view(name="code-review-and-quality"). interview-me SKIPS. No deviations.

## Scope
Review data/ package in HermesCompanion:
- Models.kt — wire-shape vs actual API responses (SessionsList, KanbanBoard, KanbanTask, TaskShowResponse, CompanionHealth)
- ApiClient.kt — HTTP, auth header, error handling, JSON construction in chat()
- SessionManager.kt — DataStore correctness, CancellationException break, credential security

## Known Issues
1. DUPLICATE: SessionsResponse (line 73) === SessionsList (line 97). Delete one.
2. KanbanBoard.counts — verify actual API returns all fields. Some boards omit ready/running.
3. SessionManager.getPasswordSnapshot() — CancellationException as flow-break is anti-pattern.
4. ApiClient.chat() — manual JSON construction; special chars in content may break.
5. KanbanCounts defaults — verify safe when API omits fields.
6. API field names — confirm @SerialName matches actual wire keys (tasks list, task show).

## Workdir: /home/kevin/.hermes/projects/HermesCompanion
## Deliverable: Self-review. File:line, severity, fix for each finding.""")
if t_data: phase1["data"] = t_data

t_viewmodel = kanban_create(
    "Review: ViewModel + All Screens",
    "analyst",
    """## Pipeline Discipline
Load skill_view(name="using-agent-skills") router -> skill_view(name="code-review-and-quality"). interview-me SKIPS. No deviations.

## Scope
Review ALL non-data Kotlin files:
- MainViewModel.kt — state mgmt, coroutines, kanban stubs!, session lifecycle, error handling
- ChatScreen.kt + SessionDrawer — session drawer, error display, new session flow
- KanbanScreen.kt — column rendering, bottom sheet, actions, assignee
- SettingsScreen.kt — connection test, credential save, board slug
- Composer.kt — input handling, send/clear logic
- MessageList.kt + ChatBubble — display, auto-scroll, empty state
- MainActivity.kt — lifecycle, tabs, ViewModel scoping
- Theme.kt — color contrast, dark theme

## Known Issues (BLOCKING)
1. ALL kanban methods are stubs: loadBoards/loadTasks/loadTask/completeTask/commentOnTask/assignTask/setBoard
2. loadSessions() never called from ChatScreen -> drawer always empty
3. newSession()/loadSessionHistory() — endpoint shape may mismatch Hermes API

## Other Issues
4. KanbanScreen:175 hardcoded analyst assignee
5. KanbanScreen:107 unsafe selectedTask!!
6. Composer clear button no-op
7. setBoard() stub — board slug change has no effect
8. clearChat() vs Composer clear connection?

## Workdir: /home/kevin/.hermes/projects/HermesCompanion
## Deliverable: Self-review. All findings, file:line, severity, fix.""")
if t_viewmodel: phase1["viewmodel"] = t_viewmodel

t_companion = kanban_create(
    "Review: Companion Daemon (server.py)",
    "analyst",
    """## Pipeline Discipline
Load skill_view(name="using-agent-skills") router -> skill_view(name="code-review-and-quality"). interview-me SKIPS. No deviations.

## Scope
Review ~/.hermes/companion/server.py (~470 lines):
- _kanban() — CLI command construction (the known bug)
- All kanban handlers — boards, tasks list, task show, complete, comment, assign, create, block, unblock, link
- Auth middleware — Basic validation, /health bypass
- Session passthrough — /api/sessions/* routes
- Chat passthrough — /v1/chat/completions proxy
- Error handling, subprocess safety, timeout handling

## BLOCKING Bug
_kanban() lines 174-175: injects --board <slug> BEFORE kanban subcommand. hermes CLI has no --board flag — it uses HERMES_KANBAN_BOARD env var (already set on line 181). Fix: remove lines 174-175.

## Other Checks
- Boards list works (no board param) — confirm
- /api/sessions passthrough — verify response shape
- Chat passthrough — verify headers forwarded (X-Hermes-Session-Id, API_SERVER_KEY)
- Auth — verify health exempt, all others require Basic
- Error shape consistency across all handlers

## Workdir: /home/kevin/.hermes/companion
## Deliverable: Self-review. Line numbers, severity, fix for each finding.""")
if t_companion: phase1["companion"] = t_companion

print("\nPhase 2 — Sequential Fix + Verify (depends on ALL Phase 1):")
print("-" * 60)

t_fix = kanban_create(
    "Fix: Apply all Phase 1 review findings",
    "ops",
    """## Pipeline Discipline
Load skill_view(name="using-agent-skills") router -> skill_view(name="debugging-and-error-recovery") (reproduce compile errors) -> skill_view(name="incremental-implementation") (fix by fix) -> skill_view(name="test-driven-development") (verify each fix). interview-me SKIPS. No deviations.

## Task
Read ALL Phase 1 review findings from sibling cards. Implement every fix, in priority order:
1. Remove _kanban() --board flag (server.py lines 174-175) — RESTART COMPANION AFTER
2. Add kotlinx-serialization plugin to app/build.gradle + root build.gradle classpath
3. Implement all kanban stub methods in MainViewModel (use ApiClient to call companion endpoints per API.md)
4. Call loadSessions() in ChatScreen LaunchedEffect
5. Wire setBoard() to SessionManager + kanban reload
6. Fix all MEDIUM and LOW issues from review findings

## Companion API Contract
See /home/kevin/.hermes/companion/API.md for full endpoint specs.
The companion server is at 127.0.0.1:8777, auth: HTTP Basic kevin/Kevi667n!1991!

## Workdir
- Android: /home/kevin/.hermes/projects/HermesCompanion
- Companion: /home/kevin/.hermes/companion

## Verification
After fixing: ./gradlew assembleDebug must pass clean.
Then manually verify: curl companion endpoints work, APK installs.

## Deliverable
Complete with summary listing every fix applied. Do NOT skip any finding from Phase 1.""")
if t_fix: phase1["fix"] = t_fix

t_verify = kanban_create(
    "Verify: E2E after fixes — build, install, test all screens",
    "analyst",
    """## Pipeline Discipline
Load skill_view(name="using-agent-skills") router -> skill_view(name="test-driven-development") (verify fixes with real tests) -> skill_view(name="code-review-and-quality") (final pass). interview-me SKIPS. No deviations.

## Scope
After ops-fix completes, verify everything:

### Build
- ./gradlew assembleDebug passes cleanly
- No serializer errors, no compilation errors
- APK produced at app/build/outputs/apk/debug/

### Install
- adb install -r the APK on Pixel 4 XL (9B071FFBA0003R)
- Launch apk, verify no crash on startup

### Chat Screen
- Sessions drawer loads real sessions (not empty)
- Send message -> reply appears
- New session creates + loads correctly

### Kanban Screen
- Boards dropdown populated from companion
- Tasks displayed in correct columns
- Tap task -> bottom sheet with details and comments
- Complete/Assign/Comment actions work

### Settings Screen
- Test Connection shows green
- Save persists to DataStore
- Board slug changes take effect on kanban reload

### Companion
- /api/kanban/boards returns all boards
- /api/kanban/tasks?board=X returns tasks
- /api/sessions returns sessions

## Workdir: /home/kevin/.hermes/projects/HermesCompanion

## Deliverable
Self-review. Report: pass/fail for each check, any remaining bugs, APK path.""")
if t_verify: phase1["verify"] = t_verify

print("\nPhase 3 — Synthesis (depends on Phase 2):")
print("-" * 60)

t_synth = kanban_create(
    "Synthesize: Final REVIEW.md from all findings and fixes",
    "writer",
    """## Pipeline Discipline
Load skill_view(name="using-agent-skills") router -> skill_view(name="documentation-and-adrs") (structured deliverable). interview-me SKIPS. No deviations.

## Task
Read ALL Phase 1 review cards, the ops-fix summary, and the analyst-verify report.
Produce a comprehensive REVIEW.md at /home/kevin/.hermes/projects/HermesCompanion/REVIEW.md.

## Deliverable Structure
1. Executive Summary — what was reviewed, bugs found, fixes applied
2. Findings by Layer — build, data, viewmodel/screens, companion daemon
3. Fixes Applied — each fix with before/after, who applied it
4. Verification Results — pass/fail for each E2E check
5. Remaining Issues — any unfixed bugs, known limitations
6. Recommendations — future improvements, tech debt

## Workdir: /home/kevin/.hermes/projects/HermesCompanion
## Deliverable: REVIEW.md updated with final synthesis. Complete with summary.""")
if t_synth: phase1["synth"] = t_synth

# Now link Phase 2 -> Phase 1 (Phase 2 depends on ALL Phase 1 review cards)
print("\nLinking Phase 2 to Phase 1 parents...")
p1_ids = [v for k, v in phase1.items() if k in ("build", "data", "viewmodel", "companion")]
for p1_id in p1_ids:
    if phase1.get("fix"):
        kanban_link(p1_id, phase1["fix"])
    if phase1.get("verify"):
        kanban_link(p1_id, phase1["verify"])

# Link Phase 3 -> Phase 2
print("Linking Phase 3 to Phase 2 parents...")
for p2_key in ("fix", "verify"):
    if phase1.get(p2_key) and phase1.get("synth"):
        kanban_link(phase1[p2_key], phase1["synth"])

print(f"\nDone. All task IDs: {json.dumps(phase1, indent=2)}")
