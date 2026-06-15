# Docker Deployment

Deploy the Hermes Companion server using Docker Compose — the recommended approach for production use.

## Prerequisites

- Docker Engine 20.10+
- Docker Compose v2+
- A running Hermes Agent instance

## Quick Start

```bash
# Clone the repository
git clone https://github.com/hermes-community/hermes-companion.git
cd hermes-companion

# Set your API key in .env
echo "API_SERVER_KEY=your-hermes-api-key" > .env

# Start the server
docker compose up -d

# Verify health
curl http://localhost:8777/health
```

## docker-compose.yml

```yaml
version: "3.9"

services:
  companion:
    build: ./server
    container_name: hermes-companion
    restart: unless-stopped
    ports:
      - "8777:8777"
    environment:
      - API_SERVER_KEY=${API_SERVER_KEY}
      - COMPANION_HOST=0.0.0.0
      - COMPANION_PORT=8777
      - HERMES_API_URL=http://host.docker.internal:8642
    volumes:
      - companion-data:/data
      - ./auth.json:/app/auth.json:ro
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8777/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 10s

volumes:
  companion-data:
    driver: local
```

## Production with Nginx + SSL

For production deployments, use `docker-compose.prod.yml` which adds an Nginx reverse proxy with TLS termination:

```bash
docker compose -f docker-compose.prod.yml up -d
```

### Volumes

| Volume | Purpose | Path inside container |
|---|---|---|
| `companion-data` | Attachments and persistent data | `/data` |
| `./auth.json` (bind mount) | User credentials | `/app/auth.json` |

### Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `API_SERVER_KEY` | Yes | — | Hermes API bearer token |
| `COMPANION_HOST` | No | `0.0.0.0` | Bind address (use `0.0.0.0` in Docker) |
| `COMPANION_PORT` | No | `8777` | Bind port |
| `HERMES_API_URL` | No | `http://host.docker.internal:8642` | Hermes API URL |

### Health Checks

The container exposes a health check endpoint at `GET /health`:

- **200** — Server is healthy and Hermes API is reachable
- **503** — Server is running but Hermes API is unreachable

Docker automatically restarts the container if the health check fails 3 consecutive times.

## Updating

```bash
docker compose pull
docker compose up -d
```

## Viewing Logs

```bash
docker compose logs -f companion
```

## Stopping

```bash
docker compose down
# To also remove volumes:
docker compose down -v
```
