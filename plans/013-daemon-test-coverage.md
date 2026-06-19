# Plan 013: Fix Daemon Test Coverage — Re-enable 49 Skipped Tests

> **Executor instructions**: Follow this plan step by step.
>
> **Drift check (run first)**: `cd ~/.hermes/companion && git diff --stat 98f270d..HEAD -- tests/ test_server.py test_config.py conftest.py`

## Status

- **Priority**: P2
- **Effort**: M
- **Risk**: LOW
- **Depends on**: 009 (setup endpoints must exist)
- **Category**: tests
- **Planned at**: commit `98f270d`, 2026-06-19

## Why this matters

The daemon has 49 permanently disabled tests covering scrypt auth, password generation, QR token generation, first-run wizard, and setup flows. These tests are critical for security — they verify the authentication pipeline works correctly. When they're disabled, auth changes can silently break without detection.

## Current state

### Daemon tests (from prior audit)
- 56 tests passing
- 49 tests skipped (permanently disabled with `@pytest.mark.skipif`)
- Tests cover: scrypt password hashing, password verification, QR token generation, first-run detection, setup wizard flow, config validation

### Root cause of skips
Prior audits found that tests were skipped because of relative import issues (`from .config_schema import ...` fails when running pytest from repo root). The `conftest.py` was added to fix `sys.path`, but some test files still have skip markers.

## Commands you will need

| Purpose   | Command                          | Expected on success |
|-----------|----------------------------------|---------------------|
| Run tests | `cd ~/.hermes/companion && python3 -m pytest tests/ -v --tb=short 2>&1 | tail -20` | 105 passed, 0 skipped |
| Syntax    | `python3 -c "import ast; ast.parse(open('server.py').read())"` | exit 0 |

## Scope

**In scope**:
- `~/.hermes/companion/tests/` — fix imports, remove skip markers
- `~/.hermes/companion/test_server.py` — fix imports if needed
- `~/.hermes/companion/test_config.py` — fix imports if needed
- `~/.hermes/companion/conftest.py` — ensure sys.path is correct

**Out of scope**:
- Daemon server.py source code (don't change production code to fix tests)
- Android code

## Steps

### Step 1: Diagnose which tests are skipped and why

```bash
cd ~/.hermes/companion
python3 -m pytest tests/ -v --tb=line 2>&1 | grep -E "SKIP|skipif"
```

Record the list of skipped tests and their skip reasons.

### Step 2: Fix import patterns

For each skipped test file, check if it uses relative imports like:
```python
from .config_schema import ...
from .server import ...
```

Convert to absolute imports or add a try/except fallback:
```python
try:
    from config_schema import ...
except ImportError:
    from .config_schema import ...
```

Or ensure `conftest.py` adds the repo root to `sys.path`:
```python
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
```

### Step 3: Remove skip markers

For each test with `@pytest.mark.skipif(...)`, evaluate:
- If the skip reason is "relative import error" — fix the import (Step 2), then remove the marker
- If the skip reason is "module not found" — install the missing dependency or fix the import
- If the skip reason is "test is for a different code path" — mark as `@pytest.mark.skip("reason")` instead of permanent skipif

### Step 4: Add new tests for setup endpoints

After Plan 009 adds `/api/setup/register` and `/api/setup/redeem`, add tests:

In `~/.hermes/companion/tests/test_setup.py` (create):

```python
import pytest
import json
from aiohttp.test_utils import TestClient, TestServer
from server import create_app

@pytest.fixture
async def client(tmp_path):
    """Create test client with temp config."""
    # ... setup fixture with tmp auth.json, config ...
    app = await create_app(test_config)
    server = TestServer(app)
    await server.start_server()
    return TestClient(server)

async def test_register_first_user(client):
    resp = await client.post('/api/setup/register', json={'username': 'admin', 'password': 'testpass123'})
    assert resp.status == 201

async def test_register_rejects_short_username(client):
    resp = await client.post('/api/setup/register', json={'username': 'ab', 'password': 'testpass123'})
    assert resp.status == 400

async def test_register_rejects_short_password(client):
    resp = await client.post('/api/setup/register', json={'username': 'admin', 'password': 'short'})
    assert resp.status == 400

async def test_register_blocked_when_users_exist(client_with_user):
    resp = await client_with_user.post('/api/setup/register', json={'username': 'second', 'password': 'testpass123'})
    assert resp.status == 403
```

### Step 5: Run full test suite

```bash
cd ~/.hermes/companion
python3 -m pytest tests/ -v --tb=short
```

All tests should pass with 0 skipped (or minimal skips with documented reasons).

### Step 6: Commit

```bash
cd ~/.hermes/companion
git add tests/ test_server.py test_config.py conftest.py
git commit -m "test: re-enable 49 skipped daemon tests + add setup endpoint tests

Fix relative import patterns, remove skipif markers, add conftest.py
sys.path setup. Add tests for /api/setup/register and /api/setup/redeem."
```

## Done criteria

- [ ] `python3 -m pytest tests/ -v` shows 0 skipped (or <5 skipped with documented reasons)
- [ ] All previously-skipped tests pass or are explicitly marked with reasons
- [ ] New tests exist for `/api/setup/register` and `/api/setup/redeem`
- [ ] `git status` is CLEAN

## STOP conditions

- If a test fails because of missing dependencies (aiohttp.test_utils, pytest-aiohttp) — install them: `pip install pytest-aiohttp`
- If a test depends on specific auth.json contents — use `tmp_path` fixtures, never touch real auth.json
- If tests require a running Hermes API — mock the upstream calls

## Maintenance notes

- NEVER import production functions that write to real paths during testing. Use `tmp_path` fixtures for auth.json and config.
- The daemon must be restarted after test runs that create test users — test-created users will block future registrations.
