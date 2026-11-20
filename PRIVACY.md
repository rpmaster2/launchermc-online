# Privacy

LauncherMC is a portable desktop application. It does not run a backend service and does not collect telemetry.

## What is stored locally

| File | Purpose |
|---|---|
| `launcher_config.ini` | Launcher settings (memory, language, loader, Java path) |
| `launcher_accounts.json` | Microsoft OAuth **refresh token** and Minecraft profile name/UUID/xuid |
| `logs/` | Launcher and game logs |
| `version_manifest.json` | Cached Mojang version list |
| `versions/` | Downloaded Minecraft files, assets, libraries, worlds |
| `java/` | Downloaded Java runtimes |

## What is sent to Microsoft/Mojang

- Microsoft OAuth device-code and token requests.
- Xbox Live and XSTS token requests.
- Minecraft Services requests: `login_with_xbox`, entitlements (`product_minecraft`, `game_minecraft`) and the Minecraft profile.
- Mojang CDN requests for version metadata, libraries and assets.

These requests are required to sign in and launch a licensed copy of Minecraft: Java Edition.

## Retention and deletion

- The refresh token is stored in `launcher_accounts.json` so the user does not have to sign in every time.
- The access token is kept only in memory and is refreshed before launch.
- **Sign out** in the launcher deletes `launcher_accounts.json`.
- Users can delete the entire launcher folder to remove all local data.

## No telemetry

LauncherMC does not include analytics, crash reporting, advertising identifiers or a remote logging service.
