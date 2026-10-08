# NordCommands 1.2.0 — Paper/Folia

Command allowlist and command-tree filtering for Paper 26.2 and Folia 26.2. One Java 25 JAR supports both platforms.

## Command policy

`allowed-commands` contains command labels, not permissions. Allowing `tempban`, for example, only lets the command reach its handler; the player still needs the permission checked by the ban plugin.

The bundled allowlist is empty. Configure the authentication and player commands your server needs before admitting players.

NordCommands checks the original command at LOWEST and the current command text at HIGHEST, while respecting earlier cancellation. This blocks ordinary-priority rewrites to forbidden or namespaced roots without changing command arguments.

Namespaced commands are blocked unless the player has bypass. An administrator can still use the plugin's own `nordcommands:nordcommands` management command.

Console commands are not filtered.

## Permissions

| Permission | Allows | Default |
| --- | --- | --- |
| `nordcommands.admin` | `/nordcommands health` and `/nordcommands reload` | Operators |
| `nordcommands.bypass` | Bypass the ordinary command allowlist and visibility filter when the policy is valid | Operators |

Bypass does not grant another plugin's command permissions. It also does not grant access to NordCommands management: the owned management root still requires `nordcommands.admin`.

To expose only selected moderator commands, keep bypass denied, add their labels to `allowed-commands` and grant their actual permissions through your permission provider.

## Configuration and reload

Keep your existing `plugins/NordCommands/config.yml`. Labels must be bare command roots: no leading slash, arguments, namespace or whitespace. Wrong types, unknown keys, malformed YAML and oversized input are rejected.

An invalid initial policy blocks player commands, including bypass users, except authorized management commands. An invalid reload keeps the last valid policy.

Reload uses one bounded asynchronous file reader. A second request is rejected while the first is pending. Wait for the explicit success or rejection message in the console. Client command trees refresh at 8 players per tick, with at most 4096 pending entries. Overflow may leave a client tree stale; command execution still uses the new policy.

## Limits

| Input or queue | Limit |
| --- | --- |
| Configuration file | 1 MiB |
| Allowlist | 256 entries |
| Command label | 64 characters |
| Denial message | 512 characters |
| Command input | 32767 characters |
| Reload | One request/result |
| Pending command-tree refreshes | 4096 |

Quit handling releases player state. Normal shutdown cancels owned work. Hot reload and hot replacement are unsupported. Production deployment is a separate stopped-server operation: retain the installed policy and keep production data private. Use only isolated local fixtures for synthetic runtime tests.

## Build and tests

Use Maven 3.9+ and JDK 25: `mvn clean verify` or `./build.ps1`. The output is `target/NordCommands-Paper-1.2.0.jar`; no active server directory is needed. See [BUILDING.md](BUILDING.md) and [FOLIA.md](FOLIA.md).

Regression tests run with assertions enabled. `test-support/integration.cjs` uses fresh loopback-only fixtures. Its `--baseline` mode accepts an unchanged older JAR for a controlled reproduction, not as a source for patches. `CommandsTestProbe` is test-only and is not packaged in the release JAR.

The historical security report is `SECURITY-1.1.0.md`. Reports matching `SECURITY-*.md` are excluded from the public repository.
