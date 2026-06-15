# Security

Security model and recommendations for the Hermes Companion project.

## Threat Model

| Threat | Severity | Mitigation |
|---|---|---|
| Password interception in transit | **Critical** | Use HTTPS (reverse proxy with TLS) |
| Brute-force auth attacks | High | Rate limiting on auth endpoints |
| Path traversal in file uploads | High | Filename sanitization, att_id validation |
| SQL injection (kanban) | Low | Uses CLI subprocess, not SQL |
| Credential leakage in app | High | Remove hardcoded defaults, use EncryptedSharedPreferences |
| Hermes API key exposure | **Critical** | Server-side only, never sent to app |

## Auth Design

### Password Hashing

Passwords are hashed using scrypt with the following parameters:

- N = 16384 (CPU/memory cost)
- r = 8 (block size)
- p = 1 (parallelization)
- Salt = 16 bytes random
- Derived key = 32 bytes

Format: `scrypt$16384$8$1$<salt-hex>$<hash-b64>`

### Auth File Permissions

```bash
chmod 600 auth.json
chown root:root auth.json
```

### No User Enumeration

Failed authentication returns a generic "Invalid credentials" message regardless of whether the username exists.

## TLS Recommendations

**Always use HTTPS in production.** The companion server itself does not support TLS — use a reverse proxy:

- Nginx with Let's Encrypt certificates
- Cloudflare Tunnel (Kevin's current setup)
- Caddy (automatic TLS)

### Nginx TLS Configuration

```nginx
ssl_protocols TLSv1.2 TLSv1.3;
ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256;
ssl_prefer_server_ciphers off;
ssl_session_timeout 1d;
ssl_session_cache shared:SSL:10m;
```

## Rate Limiting

Implement rate limiting at the reverse proxy level:

```nginx
limit_req_zone $binary_remote_addr zone=companion:10m rate=30r/m;

location / {
    limit_req zone=companion burst=10 nodelay;
    proxy_pass http://companion;
}
```

## Input Validation

| Input | Validation |
|---|---|
| Board slugs | `^[a-z0-9-]+$`, max 64 chars, no leading/trailing hyphens |
| Attachment IDs | `^att_[0-9a-f]+$` |
| Filenames | `os.path.basename()` + strip `..` |
| Comment text | Max 10 KB |
| File uploads | Max 10 MB |
| Author names | Alphanumeric + hyphen/underscore only |

## Known Issues (Pre-Universal)

| Issue | Severity | Status |
|---|---|---|
| Hardcoded password in app source (`atlas2026`) | **Critical** | Must be removed before open-source release |
| Hardcoded server URL in app | Medium | Should be empty default |
| `usesCleartextTraffic=true` in manifest | Medium | Should be `false` with HTTPS |
| No rate limiting on auth | Medium | Add at reverse proxy level |
| Auth file path hardcoded | Low | Acceptable for v1 |

## Security Checklist for Release

- [ ] Remove all hardcoded credentials from app source
- [ ] Set `usesCleartextTraffic="false"` in AndroidManifest
- [ ] Add rate limiting to reverse proxy config
- [ ] Document TLS setup
- [ ] Set `chmod 600` on auth.json in deployment docs
- [ ] Add security headers (X-Frame-Options, X-Content-Type-Options)
- [ ] Consider adding fail2ban for repeated auth failures
