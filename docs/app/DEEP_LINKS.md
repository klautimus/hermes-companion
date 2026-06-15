# Deep Links

URI scheme specification for the Hermes Companion Android app.

## Custom URI Scheme

```
hermescompanion://
```

## Supported Actions

### Configuration (Planned)

```
hermescompanion://config?url=<server_url>&user=<username>&pass=<password>
```

Used by the QR code setup wizard to auto-configure the app.

**Parameters:**

| Parameter | Required | Description |
|---|---|---|
| `url` | Yes | Companion server base URL |
| `user` | Yes | Username for HTTP Basic auth |
| `pass` | Yes | Password for HTTP Basic auth |

**Example:**

```
hermescompanion://config?url=https://companion.example.com&user=admin&pass=xK9%23mP2%24vL5%40
```

> **Security note:** Passwords in URIs should be URL-encoded. The app stores them in Android's EncryptedSharedPreferences after initial configuration.

## Android Manifest Declaration

```xml
<activity android:name=".MainActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="hermescompanion" />
    </intent-filter>
</activity>
```

> **Note:** Deep link support is planned but not yet implemented in v1.2.0. The current app uses manual configuration via the Settings screen.
