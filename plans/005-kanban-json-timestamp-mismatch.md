# Plan 005: Fix Hermes Companion Kanban JSON Deserialization — timestamp type mismatch

## Finding

**Category**: Correctness/Bugs | **Impact**: HIGH (blocks entire Kanban board view) | **Effort**: XS | **Risk**: LOW | **Confidence**: HIGH

The Hermes kanban JSON pipeline emits unix timestamps as **integer literals** (e.g. `"created_at": 1781052885`), but the Android Kotlin serializer in `Models.kt` declares these fields as `String?`. The first time the app tries to deserialize a task list, kotlinx.serialization throws:

```
Unexpected JSON token at offset 7643: Expected quotation mark "", but had '1' instead at path: $[0].created_at
```

and the whole `LoadResult.failure` path returns the error string to the UI — every column shows 0 tasks and the error banner is shown (matches the user-reported screenshot exactly).

The fix is a 2-line type change. After it lands, the Kanban board view will populate. A secondary same-class bug exists in `BoardStats` (different field name) — fix it in the same commit since it costs nothing.

### Root cause

`app/src/main/java/org/hermes/community/companion/data/Models.kt:28-45` (the `KanbanTask` data class):

```kotlin
@Serializable
data class KanbanTask(
    ...
    @SerialName(\"created_at\") val created: String? = null,   // WRONG: server returns Long
    @SerialName(\"updated_at\") val updated: String? = null,   // WRONG: server returns Long
    ...
    @SerialName(\"started_at\") val startedAt: Long? = null,   // OK: matches wire format
    ...
)
```

Meanwhile the server returns (verified via `curl https://.../api/kanban/tasks?board=...`):

```json
{
  \"id\": \"t_ab413aab\",
  \"title\": \"....\",
  \"created_at\": 1781052885,    // ← integer
  \"started_at\": 1781054284,    // ← integer
  \"completed_at\": 1781056138   // ← integer
}
```

The same class of mismatch appears elsewhere — the `*Stats*` model expects `counts_by_status` while the server returns `by_status` (verified). That's why the Stats overlay reads empty even after the timestamp fix ships. Both are fixed in this plan.

Note: `TaskShowResponse`, `KanbanComment`, `KanbanEvent`, `Attachment`, `Run` already correctly declare `Long` for timestamps. Only `KanbanTask` was missed during the data-class import burst.

## Scope

**Files to modify** (only):
- `~/.hermes/projects/HermesCompanion/app/src/main/java/org/hermes/community/companion/data/Models.kt` — fix `KanbanTask.created` and `KanbanTask.updated` types; fix `BoardStats.countsByStatus` `@SerialName`.

**Files NOT to touch**:
- Server (`server.py`) — emits correct wire types; no server change needed
- Any other Android files (KanbanScreen, MainViewModel, etc.) — these consume the model correctly once it's right
- `TaskShowResponse`, `KanbanComment`, `KanbanEvent`, `Attachment`, `Run` — already correct
- Build config, Gradle, signing — no infra changes

## Implementation Steps

### Step 1 — Fix the timestamp types in `KanbanTask`

In `Models.kt:35-36`, change:

```kotlin
@SerialName(\"created_at\") val created: String? = null,
@SerialName(\"updated_at\") val updated: String? = null,
```

to:

```kotlin
@SerialName(\"created_at\") val created: Long? = null,
@SerialName(\"updated_at\") val updated: Long? = null,
```

That's the entire root-cause fix.

### Step 2 — Fix `BoardStats.countsByStatus` wire name

In `Models.kt:184-188`, the server returns `by_status` not `counts_by_status`:

```kotlin
@Serializable
data class BoardStats(
    val total: Int = 0,
    @SerialName(\"counts_by_status\") val countsByStatus: Map<String, Int> = emptyMap(),
    @SerialName(\"oldest_ready_age_seconds\") val oldestReadyAgeSeconds: Long? = null,
)
```

Change to:

```kotlin
@Serializable
data class BoardStats(
    val total: Int = 0,
    @SerialName(\"by_status\") val countsByStatus: Map<String, Int> = emptyMap(),
    @SerialName(\"oldest_ready_age_seconds\") val oldestReadyAgeSeconds: Long? = null,
)
```

Also note the server response also has `now: 1781761451` and `by_assignee: {...}`. Both are ignored under existing `ignoreUnknownKeys = true` so no model change needed.

### Step 3 — Rebuild and reinstall

```bash
cd ~/.hermes/projects/HermesCompanion
./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk /mnt/c/Users/kevin/Downloads/HermesCompanion-release.apk
powershell.exe -Command \"& 'C:\\Users\\kevin\\Downloads\\platform-tools-latest-windows\\platform-tools\\adb.exe' -s 'adb-9B071FFBA0003R-zeEKU6 (2)._adb-tls-connect._tcp' install -r 'C:\\Users\\kevin\\Downloads\\HermesCompanion-release.apk'\"
powershell.exe -Command \"& 'C:\\Users\\kevin\\Downloads\\platform-tools-latest-windows\\platform-tools\\adb.exe' -s 'adb-9B071FFBA0003R-zeEKU6 (2)._adb-tls-connect._tcp' shell am force-stop org.hermes.community.companion\"
powershell.exe -Command \"& 'C:\\Users\\kevin\\Downloads\\platform-tools-latest-windows\\platform-tools\\adb.exe' -s 'adb-9B071FFBA0003R-zeEKU6 (2)._adb-tls-connect._tcp' shell am start -n org.hermes.community.companion/.MainActivity\"
```

The `force-stop` is important — without it the cached classloader keeps the old model.

## Verification

### Build verification

```bash
cd ~/.hermes/projects/HermesCompanion && ./gradlew assembleRelease
# Expected last line: BUILD SUCCESSFUL
```

### Live server-side sanity (no Android needed)

```bash
curl -s -u kevin:'Kevi667n!1991!' \"http://127.0.0.1:8777/api/kanban/tasks?board=agent-engineering-skills-integration\" \\
  | jq '.[0] | {id: .id, created_at, started_at, completed_at}'
# Expected: 3 integer fields
```

### End-to-end Android verification

Ask the user to:
1. Open Hermes Companion, switch to Kanban tab
2. Select `agent-engineering-skills-integration` board
3. Expect: tasks appear in Done column (was previously \"0 tasks\" with red error banner)
4. Tap the bar-chart icon — stats sheet now shows non-zero `done` count from `by_status`

If even one column is empty after the fix, pull `adb logcat | grep -i \"JsonDecodingException\\|KS error\"` — any remaining parse failure will surface there.

## Done Criteria

- [ ] `Models.kt:35-36` — both fields now `Long?`
- [ ] `Models.kt:186` — `@SerialName(\"by_status\")`
- [ ] `./gradlew assembleRelease` exits 0
- [ ] APK installed on Pixel 4 XL
- [ ] User opens Kanban board and tasks render in their status columns
- [ ] Stats overlay shows non-zero counts when tapped
- [ ] No red error banner on Kanban screen

## Risk Assessment

**Risk**: VERY LOW
- Two-line type change + one annotation change
- Existing models (`TaskShowResponse` etc.) already use `Long` for timestamps — fix is consistent with established convention
- `ignoreUnknownKeys = true` is set in all `Json { }` configs (`ApiClient`, integration tests), so any field we miss will be silently dropped, not crash
- No API contract changes (server is the source of truth and is correct)

**Rollback**: revert Models.kt; rebuild & reinstall. ~2 min.

## Tests / characterization

There are no existing unit tests in `app/src/test/` for `Models.kt` deserialization (verified via `ls app/src/test/` — directory empty in this module). Optionally add one in `app/src/test/java/org/hermes/community/companion/data/ModelsSerializationTest.kt`:

```kotlin
@Test
fun kanbanTask_deserializes_integer_timestamps() {
    val json = \"\"\"{\"id\":\"t_1\",\"title\":\"x\",\"status\":\"done\",\"created_at\":1781052885,\"updated_at\":1781052886,\"started_at\":1781052900}\"\"\"
    val task = Json.decodeFromString<KanbanTask>(json)
    assertEquals(1781052885L, task.created)
}
```

Out of scope for this fix (not blocking the user) — can be added in a follow-up plan if the user wants regression coverage.

## Maintenance note

Whenever the Hermes kanban wire format evolves (new timestamps, renames), `@SerialName` is the bridge. A lightweight habit: when you add a new timestamp field anywhere in Models.kt, default to `Long?` — the Hermes CLI emits integers, never ISO strings. The Hermes **API server** (different codebase) emits ISO strings — that's why `SessionMessages.at: Double?` exists. Don't mix the two conventions within a single data class.
