# Contributing to Hermes Companion

Thank you for your interest in contributing! This document outlines how to submit issues, propose changes, and submit pull requests.

## Getting Started

1. Fork the repository.
2. Clone your fork:
   ```bash
   git clone https://github.com/<your-username>/hermes-companion.git
   ```
3. Create a branch for your change:
   ```bash
   git checkout -b fix/your-feature-name
   ```

## Development Setup

### Server

```bash
cd server
python3 -m venv venv
source venv/bin/activate
pip install aiohttp pytest
export API_SERVER_KEY=test-key
python server.py
```

### App

1. Open the project in Android Studio.
2. Sync Gradle.
3. Run tests: `./gradlew testDebugUnitTest`

## Submitting Changes

1. **Write tests** for any new functionality.
2. **Run all tests** before submitting:
   ```bash
   # Server tests
   cd server && pytest

   # App tests
   ./gradlew testDebugUnitTest
   ```
3. **Update documentation** if your change affects configuration, deployment, or the API.
4. **Commit with clear messages:**
   ```
   fix: handle timeout in ApiClient.chat()
   feat: add QR code scanning to setup wizard
   docs: update Docker deployment guide
   ```
5. **Open a pull request** against `main`.

## Code Style

### Python (Server)
- Follow PEP 8.
- Use type hints for function signatures.
- Docstrings for public functions.

### Kotlin (App)
- Follow Kotlin coding conventions.
- Use `camelCase` for variables/functions, `PascalCase` for classes.
- Prefer immutable data (`val` over `var`).

## PR Guidelines

- Keep PRs focused on a single concern.
- Include a description of what changed and why.
- Reference any related issues.
- Ensure CI passes (tests, linting).

## Reporting Issues

When reporting a bug, include:
- What you expected to happen.
- What actually happened.
- Steps to reproduce.
- Server version / app version.
- Relevant logs (server logs, app logs).

## Code of Conduct

Be respectful and constructive. Disagreement is fine — hostility is not.

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
