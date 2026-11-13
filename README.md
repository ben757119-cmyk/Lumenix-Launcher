# Lumenix Launcher

[![Build](https://github.com/ben757119-cmyk/Lumenix-Launcher/actions/workflows/build.yml/badge.svg)](https://github.com/ben757119-cmyk/Lumenix-Launcher/actions/workflows/build.yml)
[![Release](https://github.com/ben757119-cmyk/Lumenix-Launcher/actions/workflows/release.yml/badge.svg)](https://github.com/ben757119-cmyk/Lumenix-Launcher/actions/workflows/release.yml)

Persistent **Minecraft Java 26.2 + Fabric** installer/launcher for **Lumenix Free** and **Lumenix Paid**.

## What it does

- Uses a **persistent** install under `%APPDATA%/Lumenix/` (not a disposable download folder)
- Installs **Minecraft 26.2** + **Fabric** once, then reuses it
- Downloads **only missing/outdated** files (SHA checks, cache)
- Switches Free ↔ Paid **without** duplicating the Minecraft install
- **Repair Installation** rescans and fixes only bad/missing files
- Downloads Lumenix JARs from GitHub Releases (`ben757119-cmyk/Lumenix`)
- Microsoft account login (device-code flow)

## Directory layout

```
%APPDATA%/Lumenix/
  Minecraft/     shared game files & libraries
  Versions/      version profiles
  Clients/       Lumenix Free/Paid JARs
  Mods/          active mod folder for launches
  Cache/         download cache
  Logs/          launcher logs
  Config/        settings + install metadata
```

## Build (local)

```bash
mvn -B package
# output: target/LumenixLauncher.jar
```

## GitHub Actions

| Workflow | Trigger | Output |
|----------|---------|--------|
| **Build** | push / PR | `LumenixLauncher.jar` + Windows ZIP artifact |
| **Release** | tag `v*` or manual | GitHub Release with JAR/ZIP + SHA-256 |

### Trigger a build
Push to `main` → **Actions → Build**.

### Create a release
```bash
git tag v1.0.0
git push origin v1.0.0
```
Or **Actions → Release → Run workflow** with version `1.0.0`.

### Find binaries
- **Actions** → Artifacts → `lumenix-launcher-windows`
- **Releases** → `LumenixLauncher.jar` / ZIP / app-image ZIP

## Acceptance path

1. Download launcher from Releases  
2. Microsoft Login  
3. Choose Free or Paid  
4. Install / Update (first time installs MC + Fabric)  
5. PLAY  
6. Quit → PLAY again → no full redownload  
7. New Lumenix client release → only client JAR updates  
