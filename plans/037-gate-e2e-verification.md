# Plan 037: GATE — Full E2E Verification

## Status
- **Priority**: P0 | **Effort**: M | **Risk**: LOW | **Depends on**: ALL plans (030-036) | **Category**: tests
- **Planned at**: commit `b09390e`, 2026-06-19

## Why this matters
Every prior board shipped "done" without verifying on a real device. This GATE runs the full verification suite.

## Verification checklist

### Build
- [ ] `./gradlew assembleDebug --no-daemon` → BUILD SUCCESSFUL
- [ ] `python3 -m pytest server/test_server.py -x -q` → all pass
- [ ] APK exists at `app/build/outputs/apk/debug/app-debug.apk`

### Server endpoints
- [ ] `POST /api/kanban/tasks?board=test` with `{"title":"Test"}` → 201
- [ ] `POST /api/kanban/links?board=test` → 201
- [ ] `POST /api/kanban/tasks/{id}/attachments` → 201
- [ ] `POST /api/kanban/tasks/{id}/decompose` → 200/201

### Android E2E (Pixel 4 XL)
- [ ] APK installs without error
- [ ] App launches, no crash in 60s idle (check logcat for FATAL EXCEPTION)
- [ ] Kanban: create task → appears in board
- [ ] Kanban: edit task title → saves
- [ ] Kanban: add comment → appears
- [ ] Kanban: decompose → subtasks created
- [ ] Chat: send message → streams response
- [ ] Chat: send attachment → uploads + displays
- [ ] Markdown: code block has syntax highlighting colors
- [ ] Markdown: `:rocket:` renders as 🚀

### Git
- [ ] `git status` CLEAN
- [ ] All plans committed
- [ ] `plans/README.md` status matches reality

## Done criteria
ALL items pass. If any fails → collect failures, spawn fix sub-board.
