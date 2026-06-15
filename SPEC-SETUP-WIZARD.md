# Spec: Hermes Companion — First-Run Setup Wizard + pip Packaging

## Objective

Provide a guided first-run setup experience (`hermes-companion setup`) that:
1. Auto-detects Hermes CLI installation
2. Prompts for configuration with sensible defaults
3. Generates secure random admin password
4. Creates config.yaml and auth.json files
5. Generates QR code for mobile app configuration
6. Refuses to start without configuration (exit code 2)
7. Packages everything for `pip install hermes-companion-server` with CLI entry point

## Tech Stack

- Python 3.10+
- aiohttp (existing)
- pyyaml (new - for config.yaml)
- qrcode[pil] (new - for QR code generation)
- secrets, hashlib (stdlib - for password generation)

## Commands

```bash
# Build
pip install -e .

# Test
cd server && python -m pytest test_server.py -v
python -m pytest tests/ -v  # new tests

# Run setup wizard
hermes-companion setup

# Run server
hermes-companion serve

# Lint (if configured)
ruff check .
```

## Project Structure

```
hermes-companion/
├── server/
│   ├── server.py           # Main server (aiohttp)
│   ├── test_server.py      # Existing tests
│   ├── setup_wizard.py     # NEW: Interactive setup wizard
│   ├── config_schema.py    # NEW: Config validation & defaults
│   ├── first_run.py        # NEW: First-run detection & exit code 2
│   └── cli.py              # NEW: CLI entry point (hermes-companion)
├── pyproject.toml          # NEW: Package metadata, entry points
├── setup.py                # NEW: Legacy compatibility
├── hermes-companion.service # Existing systemd service
└── docs/server/
    └── SETUP_WIZARD.md     # Existing docs (update when done)
```

## Code Style

```python
# Use type hints
def generate_password() -> str:
    return secrets.token_urlsafe(32)

# Prefer pathlib
from pathlib import Path
CONFIG_DIR = Path.home() / ".config" / "hermes-companion"

# Clear function names, single responsibility
def detect_hermes_cli() -> Path | None:
    ...

def hash_password(password: str) -> str:
    ...

# Follow existing patterns in server.py
# - logging.basicConfig at module level
# - logger = logging.getLogger("module")
# - async/await for I/O
```

## Testing Strategy

- **Unit tests** (server/): Existing pytest + new tests for setup_wizard.py, config_schema.py, first_run.py
- **Integration test**: Fresh install → setup → server starts → mobile connects
- **CLI tests**: Verify `hermes-companion setup` and `hermes-companion serve` work
- **QR code tests**: Verify QR encodes correct URL format

## Boundaries

### Always do:
- Generate secure random passwords (secrets.token_urlsafe(32))
- Hash with scrypt (N=16384, r=8, p=1)
- Validate config.yaml schema on load
- Exit code 2 when config missing on server start
- Write config to ~/.config/hermes-companion/ (XDG compliant)
- Support both env vars and config file (env vars take precedence)

### Ask first:
- Adding new dependencies beyond pyyaml and qrcode[pil]
- Changing the QR code URI scheme (hermescompanion://)
- Modifying existing auth.json format

### Never do:
- Store plaintext passwords
- Write config outside ~/.config/hermes-companion/
- Break backward compatibility with existing env vars
- Require interactive input for server start (only for setup)

## Success Criteria

- [ ] `hermes-companion setup` command exists and is interactive
- [ ] Detects Hermes CLI automatically (reuse T14 logic)
- [ ] Prompts for host/port (defaults: 127.0.0.1:8777)
- [ ] Prompts for Hermes API URL (default: http://127.0.0.1:8642)
- [ ] Prompts for Hermes API key (or reads from env)
- [ ] Generates secure random password for admin user
- [ ] Creates config.yaml in ~/.config/hermes-companion/
- [ ] Creates auth.json with scrypt-hashed password
- [ ] Creates attachments directory
- [ ] Outputs connection info (URL, username, password) for mobile app
- [ ] QR code encodes all needed connection params (ASCII + PNG)
- [ ] Server refuses to start without config (exit code 2, clear message)
- [ ] Setup output includes clear instructions for mobile app
- [ ] Unit tests for setup wizard flow
- [ ] Integration test: fresh install → setup → server starts → mobile connects
- [ ] `pip install hermes-companion-server` works
- [ ] CLI entry point `hermes-companion` works (setup, serve subcommands)
- [ ] systemd user service file included

## Open Questions

1. Should setup wizard support non-interactive mode (flags for all values)?
2. Should the QR code include the board slug?
3. What's the exact mobile app URI scheme? (hermescompanion://configure?url=...&user=...&pass=...&board=...)
4. Should we support multiple users in initial setup or just admin?

---

**Status: DRAFT - Awaiting human review before implementation**

## ASSUMPTIONS I'M MAKING:

1. The mobile app URI scheme is `hermescompanion://configure` with query params
2. We only create a single "admin" user during initial setup (multi-user is future)
3. Config file location: `~/.config/hermes-companion/config.yaml` (XDG)
4. Auth file location: `~/.config/hermes-companion/auth.json` (change from current hardcoded)
5. Attachments directory: `~/.config/hermes-companion/attachments/`
6. Hermes CLI auto-detection: check common paths + PATH env
7. pip package name: `hermes-companion-server` (on PyPI or local install)
8. CLI entry point: `hermes-companion` with subcommands `setup` and `serve`
9. systemd user service goes in package data, not system-wide
10. Environment variables still take precedence over config file (existing behavior)
→ Correct me now or I'll proceed with these.