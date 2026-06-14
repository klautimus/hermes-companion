# Hermes Companion — Full App Review Spec v1

**Created:** 2026-06-14 | **Orchestrator:** Atlas | **Review Board:** companion-review

## Scope

Full end-to-end review of Hermes Companion — Android app (Kotlin/Compose) + 
Companion daemon (Python/aiohttp). Review authority: find bugs, fix in-flight,
verify fixes. Fix policy: **fix-as-you-go** — reviewers find, ops fixes.

### In Scope
- Android app: all 3 screens (Chat, Kanban, Settings), ViewModel, data layer, theme
- Companion daemon: server.py (auth, kanban CLI wrapper, session passthrough, health)
- Gradle build: dependencies, compiler plugins, minify/ProGuard rules
- Wire-shape contracts: Models.kt vs actual API response shapes

### Out of Scope
- Cloudflare tunnel configuration
- Hermes API server internals
- New feature development

## Diagnostic Findings (Pre-Review)

### BLOCKING

| # | Layer | Issue |
|---|-------|-------|
| B1 | Companion | `_kanban()` inserts `--board <slug>` as a top-level `hermes` flag; CLI rejects it. `HERMES_KANBAN_BOARD` env var is already set correctly — just remove the `--board` flag insertion (lines 174-175 of server.py) |
| B2 | Build | `app/build.gradle` missing `apply plugin: 'kotlinx-serialization'` → no serializer codegen → `SessionsList` crash |
| B3 | Build | Root `build.gradle` may need `kotlinx-serialization` classpath dependency for plugin resolution |
| B4 | ViewModel | All kanban action methods are stubs (`/* existing */`): `loadBoards`, `loadTasks`, `loadTask`, `completeTask`, `commentOnTask`, `assignTask`, `setBoard`. Kanban screen receives zero data. |

### HIGH

| # | Layer | Issue |
|---|-------|-------|
| H1 | ViewModel | `loadSessions()` is never called from ChatScreen → session drawer always empty "No sessions yet". Also need to verify Hermes `/api/sessions` endpoint response shape matches `SessionsList` |
| H2 | ViewModel | `newSession()` → `loadSessionHistory()`: calls `/api/sessions/{id}/messages` — verify companion proxies this correctly |
| H3 | KanbanScreen | `LaunchedEffect(Unit) { viewModel.loadBoards(); viewModel.loadTasks() }` — both no-ops. No data ever arrives. |

### MEDIUM

| # | Layer | Issue |
|---|-------|-------|
| M1 | KanbanScreen | Hardcoded `"analyst"` assignee (line 175 of KanbanScreen.kt). Should be configurable or read from companion. |
| M2 | SettingsScreen | `setBoard()` stub — changing board slug in Settings never persists. |
| M3 | Models.kt | `KanbanBoard` expects `counts` with `ready`/`running` — some boards return counts without those fields. Harmless with defaults, but verify all boards work. |
| M4 | Companion | Verify `/api/sessions` passthrough returns the exact shape `SessionsList` expects |

### LOW

| # | Layer | Issue |
|---|-------|-------|
| L1 | Models.kt | Duplicate model: `SessionsResponse` (line 73) is dead code. |
| L2 | Composer | Clear button no-op: `/* clear not implemented in VM yet */` |
| L3 | KanbanScreen | `selectedTask!!` force-unwrap (line 107) — safe by guard, but cleanup |
| L4 | MainViewModel | `setBoard()` stub — changed board via Settings or Kanban dropdown doesn't save |
| L5 | Root build.gradle | `versionCode 1, versionName "1.0.0"` — bump to `1.1.0` after fixes |

## Review Layers

**Phase 1 (parallel, no deps):**
1. **Build & Gradle** — `app/build.gradle`, root `build.gradle`, `settings.gradle`, ProGuard, kotlinx-serialization plugin
2. **Data Layer** — `Models.kt`, `ApiClient.kt`, `SessionManager.kt` — wire-shape contracts
3. **ViewModel + Screens** — `MainViewModel.kt`, `ChatScreen.kt`, `KanbanScreen.kt`, `SettingsScreen.kt`, `Composer.kt`, `MessageList.kt`, `MainActivity.kt`
4. **Companion Daemon** — `server.py` (kanban CLI, auth, session passthrough)

**Phase 2 (sequential, depends on ALL Phase 1):**
5. **ops-fix** — reads all Phase 1 findings, implements fixes
6. **analyst-verify** — builds APK, runs on phone, verifies E2E (chat, kanban, settings)

**Phase 3 (depends on Phase 2):**
7. **writer-synthesis** — produces final `REVIEW.md` with all findings and fix summaries

## Pipeline Discipline

All workers MUST follow `using-agent-skills`:
1. `skill_view(name="using-agent-skills")` — router
2. `skill_view(name="code-review-and-quality")` — five-axis review (Phase 1)
3. `skill_view(name="debugging-and-error-recovery")` → `incremental-implementation` → `test-driven-development` (Phase 2 fix)
4. `interview-me` SKIPS — review intent is locked in this spec.

## Success Criteria

- [ ] APK builds cleanly with `./gradlew assembleDebug`
- [ ] `Serializer for class 'SessionsList' is not found` crash is gone
- [ ] Kanban tab loads boards list and tasks per column from all boards
- [ ] Kanban actions work: Complete, Comment, Assign against real companion endpoints
- [ ] Chat: new session, select session, send message, receive reply, load history
- [ ] Settings: test connection green, board slug persists, save works
- [ ] Companion daemon kanban endpoints return 200 for all boards
- [ ] No stubs remain in MainViewModel kanban methods
