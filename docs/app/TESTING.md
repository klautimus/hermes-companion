# Testing

How to test the Hermes Companion Android app.

## Test Structure

```
app/src/
├── main/                          # Production code
└── test/
    ├── java/com/atlas/hermescompanion/
    │   ├── MainViewModelTest.kt   # ViewModel unit tests
    │   ├── data/
    │   │   ├── ApiClientTest.kt   # API client tests
    │   │   └── ModelsTest.kt      # Serialization tests
    │   └── resources/
    │       └── robolectric.properties
```

## Running Tests

### From Android Studio

1. Right-click the `test` directory → **Run 'Tests in 'com.atlas.hermescompanion'**
2. Or right-click individual test files.

### From Command Line

```bash
# Unit tests (JVM, no device needed)
./gradlew testDebugUnitTest

# All tests
./gradlew test

# With coverage
./gradlew testDebugUnitTest jacocoTestReport
```

## Test Frameworks

| Framework | Version | Purpose |
|---|---|---|
| JUnit 4 | 4.13.2 | Test runner |
| Mockito | 5.2.0 | Mocking |
| Mockito Kotlin | 5.2.1 | Kotlin-friendly Mockito |
| Turbine | 1.1.0 | Kotlin Flow testing |
| Robolectric | 4.11.1 | Android API mocking |
| Coroutines Test | 1.7.3 | Coroutine testing |
| Arch Core Testing | 2.2.0 | LiveData/ViewModel testing |

## What's Tested

### MainViewModel

- Session loading and switching
- Message sending and receiving
- Kanban board loading and task operations
- Settings persistence
- Error handling

### ApiClient

- HTTP request construction
- Auth header generation
- Error response parsing
- Network error handling (timeout, DNS, SSL)

### Models

- JSON serialization/deserialization
- Data class equality
- Null handling

## Writing New Tests

### ViewModel Test Example

```kotlin
@Test
fun `loadSessions populates session list`() = runTest {
    val viewModel = MainViewModel(mockContext)
    viewModel.loadSessions()
    val sessions = viewModel.sessions.first()
    assertThat(sessions).isNotEmpty()
}
```

### API Client Test Example

```kotlin
@Test
fun `get returns parsed JSON on 200`() = runTest {
    val client = ApiClient("http://localhost:8777", "user", "pass")
    // Mock server response...
}
```

## UI Tests (Planned)

UI tests using Espresso are planned but not yet implemented. The current test suite focuses on unit tests that run on the JVM without a device.

```bash
# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

## Device Farm Testing (Planned)

For testing across multiple device configurations, the project may use:
- Firebase Test Lab
- AWS Device Farm
- BrowserStack

This is not yet configured.
