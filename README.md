# Mediaserver Loadbalancer

A load balancer for distributing media sessions across a pool of media servers. It periodically polls each server for resource metrics (CPU, memory, active RTP streams) and selects the least-loaded server on demand using a weighted scoring algorithm.

## Architecture

```
                          ┌─────────────────────────────┐
                          │  MediaServerLoadbalancer     │
                          │  (Jetty, port 8102)          │
                          └──────┬──────────┬────────────┘
                                 │          │
                  ┌──────────────┘          └──────────────┐
                  v                                        v
         MediaServerPoller                         LoadbalancerApi
         (scheduled polling)                       (REST API)
                  │                                        │
                  │  JSON-RPC                               │  uses
                  │  getLoadReport()                        │  BalancerStrategy
                  v                                        v
         ┌───────────────┐                        WeightedScoreStrategy
         │ Media Server 1 │                       (select least-loaded)
         │ Media Server 2 │
         │ ...            │
         └───────────────┘
```

### Modules

| Module | Description |
|--------|-------------|
| **shared** | DTOs and the JSON-RPC service interface (`LoadReport`, `PauseState`, `LoadReportService`) shared between the loadbalancer and media servers |
| **server** | The loadbalancer service itself: polling, balancing strategy, REST API, and main entry point |

### Key Components

- **MediaServerLoadbalancer** — Main entry point. Loads pool config, starts the poller, and registers the REST API on Jetty.
- **MediaServerPoller** — Periodically calls `getLoadReport()` on every configured media server via JSON-RPC and maintains per-server state (`MediaServerState`).
- **WeightedScoreStrategy** — Selects the server with the lowest composite score. Only servers that are both healthy and in the `ENABLED` pause state are considered.
- **LoadbalancerApi** — JAX-RS resource exposing REST endpoints for server selection, status, and pause state control.

## Load Balancing Algorithm

Each candidate server is scored as:

```
score = 0.4 × cpuUsage + 0.3 × memoryUsage + 0.3 × min(1.0, rtpStreamCount / 500)
```

The server with the **lowest** score is selected. Servers that are unreachable, have no load report, or are not in the `ENABLED` state are excluded.

## Pause States

Media servers report a `PauseState` that controls whether they receive new sessions:

| State | Description |
|-------|-------------|
| `STARTING` | Server is initializing, not yet ready |
| `ENABLED` | Fully operational — eligible for new sessions |
| `PAUSED` | Draining — existing sessions continue, but no new ones assigned |
| `STOPPED` | Completely excluded from load balancing |

The loadbalancer can change a server's pause state via the `/api/server/pause` endpoint, which forwards the command to the media server over JSON-RPC.

## REST API

Base path: `/api` on port **8102**.

### `GET /api/select?pool={poolName}`

Select the best available media server from a pool.

**Response (200):**
```json
{"host": "ms1.example.com", "port": 9092, "pool": "default"}
```

| Status | Meaning |
|--------|---------|
| 200 | Server selected |
| 400 | Missing `pool` parameter |
| 404 | Unknown pool name |
| 503 | No healthy servers available in pool |

### `GET /api/status`

Returns the current state of all pools and servers.

**Response (200):**
```json
{
  "pools": {
    "default": [
      {
        "host": "ms1.example.com",
        "port": 9092,
        "reachable": true,
        "healthy": true,
        "consecutiveFailures": 0,
        "lastPollTimeMillis": 1710000000000,
        "lastReport": {
          "cpuUsage": 0.35,
          "memoryUsage": 0.42,
          "rtpStreamCount": 120,
          "pauseState": "ENABLED",
          "timestamp": 1710000000000
        }
      }
    ]
  }
}
```

### `PUT /api/server/pause?host={host}&port={port}&state={state}`

Set the pause state of a specific media server.

| Parameter | Description |
|-----------|-------------|
| `host` | Media server hostname |
| `port` | Media server RPC port |
| `state` | One of: `STARTING`, `ENABLED`, `PAUSED`, `STOPPED` |

**Response (200):**
```json
{"host": "ms1.example.com", "port": 9092, "state": "PAUSED"}
```

| Status | Meaning |
|--------|---------|
| 200 | State updated |
| 400 | Missing or invalid parameters |
| 404 | Unknown server |
| 502 | JSON-RPC call to media server failed |

## JSON-RPC Interface (Media Server Contract)

Media servers must implement the `LoadReportService` interface at `http://{host}:{port}/rpc/loadreport`:

| Method | Description |
|--------|-------------|
| `LoadReport getLoadReport()` | Returns current CPU, memory, stream count, and pause state |
| `void setPauseState(PauseState state)` | Sets the server's operational state |

## Configuration

The pool configuration is loaded from the first file found in this order:

1. Path specified by `-Dpools.config=<path>`
2. `./pools.json` in the working directory
3. `/etc/mediaserverloadbalancer/pools.json`

**Example (`pools.json`):**
```json
{
  "pollingIntervalSeconds": 10,
  "pools": {
    "default": {
      "servers": [
        {"host": "ms1.example.com", "rpcPort": 9092},
        {"host": "ms2.example.com", "rpcPort": 9092}
      ]
    },
    "conference": {
      "servers": [
        {"host": "ms3.example.com", "rpcPort": 9092}
      ]
    }
  }
}
```

| Field | Default | Description |
|-------|---------|-------------|
| `pollingIntervalSeconds` | 10 | How often each media server is polled |
| `pools` | — | Named groups of media servers |
| `servers[].host` | — | Media server hostname |
| `servers[].rpcPort` | 9092 | JSON-RPC port on the media server |

## Building

```bash
mvn clean install -DskipTests
```

## Tech Stack

- Java 21
- Jetty (embedded via Telavox Base)
- Jersey (JAX-RS) for REST endpoints
- jsonrpc4j for JSON-RPC communication
- Jackson for JSON serialization