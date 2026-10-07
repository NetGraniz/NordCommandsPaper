# Paper / Folia compatibility — 1.2.0

Use `NordCommands-Paper-1.2.0.jar` on either platform with JDK 25. Entity/global scheduler APIs support both platforms; no separate branch is needed.

Player command checks and command-tree refreshes run on the owning region. Reload completion and bounded refresh pump use the global scheduler. Preserve the installed allowlist, including authentication commands; the default allowlist remains empty.

## Verification and limits

Run `mvn clean verify`. The [isolated integration harness](https://github.com/NetGraniz/NordChat/tree/main/test-support) boots all eight adapted plugins together with synthetic loopback clients, separated regions, local HTTPS and synthetic data. Movement cancellation dispatches an owning-region PlayerMoveEvent, not real-client movement. Never install the helper JAR on a real server. The harness records runtime results outside the repository. This is not a 1000-player load test, a production database test or a guarantee for future Minecraft versions.

## Updating

Back up configuration and player data, stop the server, replace only this plugin JAR without duplicates, and preserve installed data. Do not overwrite working config with repository templates. This release does not migrate worlds or production data.
