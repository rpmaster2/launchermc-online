# Changelog

## 1.4.0

### Added
- Microsoft sign-in (OAuth device-code → Xbox Live/XSTS → Minecraft Services).
  The launcher verifies `product_minecraft` and `game_minecraft` before launch.
- Automatic Adoptium Temurin JRE download (Java 8/17/21/25).
- Fabric Loader installation with Maven libraries and inherited parent versions.
- Shared libraries in `versions/libraries`.
- Russian/English interface, live log, version search and filters.

### Changed
- Microsoft sign-in and a successful Java Edition entitlement check are required
  before the game can be started.
- Mojang allow-list rejection (`Invalid app registration`) shows a clear dialog
  with the official application link.
- README and `PRIVACY.md` describe the online-only build.
