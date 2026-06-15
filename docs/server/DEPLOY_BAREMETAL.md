# Bare-Metal Deployment

Deploy the Hermes Companion server directly on a Linux machine without Docker.

## Prerequisites

- Python 3.10+
- pip
- A running Hermes Agent instance
- (Optional) nginx — for reverse proxy and TLS
- (Optional) systemd — for service management

## Installation

```bash
# Clone the repository
git clone https://github.com/hermes-community/hermes-companion.git
cd hermes-companion/server

# Create a virtual environment
python3 -m venv venv
source venv/bin/activate

# Install dependencies
pip install aiohttp

# Verify installation
python -c "import aiohttp; print(aiohttp.__version__)"
```

## Running the Server

### Required Environment Variable

```bash
export API_SERVER_KEY="your-hermes-api-key"
```

### Start the Server

```bash
python server.py
# → Companion daemon starting on 127.0.0.1:8777
```

### Verify It's Working

```bash
curl http://localhost:8777/health
# {"status": "ok", "uptime": 5, "hermes_api_reachable": true}
```

## Systemd Service

For automatic startup and process management, install a systemd service:

```bash
# Copy the service file
sudo cp systemd/hermes-companion.service /etc/systemd/system/

# Edit to match your setup
sudo systemctl edit hermes-companion
```

Example override (`/etc/systemd/system/hermes-companion.service.d/override.conf`):

```ini
[Service]
Environment=API_SERVER_KEY=your-hermes-api-key
Environment=HERMES_API_URL=http://127.0.0.1:8642
WorkingDirectory=/opt/hermes-companion/server
ExecStart=/opt/hermes-companion/server/venv/bin/python server.py
```

```bash
# Enable and start
sudo systemctl daemon-reload
sudo systemctl enable --now hermes-companion

# Check status
sudo systemctl status hermes-companion

# View logs
journalctl -u hermes-companion -f
```

## Nginx Reverse Proxy

For TLS termination and rate limiting, put Nginx in front of the companion server:

```nginx
# /etc/nginx/sites-available/hermes-companion

upstream companion {
    server 127.0.0.1:8777;
}

# Rate limiting zone
limit_req_zone $binary_remote_addr zone=companion:10m rate=30r/m;

server {
    listen 443 ssl http2;
    server_name companion.example.com;

    ssl_certificate /etc/letsencrypt/live/companion.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/companion.example.com/privkey.pem;

    # Security headers
    add_header X-Frame-Options DENY;
    add_header X-Content-Type-Options nosniff;
    add_header X-XSS-Protection "1; mode=block";

    location / {
        limit_req zone=companion burst=10 nodelay;

        proxy_pass http://companion;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Timeouts (match companion's read timeout)
        proxy_read_timeout 300s;
        proxy_connect_timeout 10s;
    }
}
```

```bash
# Enable the site
sudo ln -s /etc/nginx/sites-available/hermes-companion /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

## SSL with Let's Encrypt

```bash
sudo certbot --nginx -d companion.example.com
```

## File Permissions

Ensure sensitive files have restrictive permissions:

```bash
chmod 600 /opt/hermes-companion/auth.json
chown www-data:www-data /opt/hermes-companion/auth.json
```
