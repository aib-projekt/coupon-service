## Local Development Standards

### Build JARs Before Starting the Stack
Docker images copy pre-compiled JARs from `target.nosync/`. Always build both services before `docker compose up`:

```bash
# 1. Build audit-storage (repo root)
mvn clean package -P iCloud.nosync

# 2. Build delegate-change-service
cd local-docker/delegate-change-service && mvn clean package -P iCloud.nosync
```

> `delegate-change-service` requires `audit-publisher-rest` and `audit-publisher-sqs` SNAPSHOTs in local `.m2` or internal Artifactory.

### Colima Requires /Users Write Mount
On macOS with Colima, start Colima with the project directory mounted writable:

```bash
colima start --mount /Users:w
```

Without this, Docker Compose volume mounts will fail.

### Init Scripts Baked into Docker Image
Local init scripts (`scripts/init-dynamodb.sh`, `scripts/init-local-stack.sh`) must be **baked into the init Docker image** (`scripts/Dockerfile.init`), not volume-mounted. Volume-mounted individual files cause Colima to treat them as directories inside containers.

### .env from .env.example
Never commit `.env`. Always copy from the example file:

```bash
cd local-docker
cp .env.example .env
```

Default values work out-of-the-box for most developers.

### Local Service Port Conventions
| Service | Port | Notes |
|---|---|---|
| `audit-storage` | 8181 | Main service under test |
| `delegate-change-service` (outbox) | 8282 | REST publisher test service |
| `delegate-change-ec-service` (eventual-consistency) | 8383 | SQS publisher test service |
| `dynamodb-admin` UI | 8001 | Browser UI — use this port |
| `dynamodb-local` API | 8000 | Raw API only — `400 MissingAuthenticationToken` in browser is expected |
| `sqs-admin` UI | 8002 | SQS browser UI |
| `postgres` | 5432 | PostgreSQL for delegate services |
| `aws-local` (LocalStack) | 4566 | SQS endpoint |

### DynamoDB Table Naming
Local DynamoDB table name: `control-plane_audit-events`.

### SQS Queue Naming Convention
Queues follow the pattern `audit-{type}-events-queue` with a matching `-dlq` dead-letter queue:
- `audit-change-events-queue` + `audit-change-events-dlq`
- `audit-intent-events-queue` + `audit-intent-events-dlq`

### Resetting the Local Stack
To fully reset (including PostgreSQL data):

```bash
docker compose down -v   # removes containers AND named volumes
docker compose up -d
```

Use `docker compose down` (without `-v`) to preserve PostgreSQL data between restarts.

### Startup Sequence
The `init-service` container must exit with code `0` before `audit-storage` starts. Verify with:

```bash
docker compose ps init-service   # Expected: exited (0)
```

If `init-service` fails, force recreate it:

```bash
docker compose up init-service --force-recreate
```
