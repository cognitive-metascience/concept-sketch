# Server Startup Scripts

Quick-start scripts for running Word Sketch Lucene.

## Quick Start

### Windows (PowerShell)

Start just the API server:
```powershell
.\scripts\start-server.ps1
```

Start API server + web UI (recommended):
```powershell
.\scripts\start-all.ps1
```

### Linux/Mac (Bash)

Start just the API server:
```bash
chmod +x scripts/start-server.sh
./scripts/start-server.sh
```

Start API server + web UI (recommended):
```bash
chmod +x scripts/start-all.sh
./scripts/start-all.sh
```

## Customization

### Change Port
```powershell
# API on port 9090, Web on port 3001
.\scripts\start-all.ps1 -Port 9090 -WebPort 3001
```

### Change Index Location
```powershell
.\scripts\start-server.ps1 -Index "C:\path\to\my\index"
```

## Configuration

Edit the default `$Index` path in the script files:
- `start-server.ps1` (line 10)
- `start-server.sh` (line 5)
- `start-all.ps1` (line 10)
- `start-all.sh` (line 5)

## Build First

Before running, ensure the JAR is built:
```bash
mvn clean package
```

## What Gets Ignored

The `.gitignore` in this directory ignores:
- `*.bin` - Precomputed collocation files
- `*.log` - Server logs
- `*.pid` - Process ID files
- `index-*/` - Local index directories

This keeps the repository clean while allowing you to store local configuration and precomputed data.
