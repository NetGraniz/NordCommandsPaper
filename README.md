# NordCommands 1.2.0

One release JAR for Paper 26.2 and Folia 26.2: [compatibility notes](FOLIA.md).

> Release build and installation requirements: see [BUILDING.md](BUILDING.md).
> Older local paths below describe historical test fixtures, not the release build.

Paper/Folia 26.2 / Java 25 root-label command allowlist and client visibility filter.
This is an additional routing barrier, not a replacement for command permissions.

The bundled `allowed-commands` list is intentionally empty. Configure it in the
installed server's `plugins/NordCommands/config.yml` before allowing players to
join. In particular, allow your authentication commands when needed. The template
does not grant permissions and does not replace existing server configuration.

Sources, build artifacts and tests stay on this network share. Synthetic Minecraft
runtimes run only on isolated LOCAL fixtures. Production deployment is a separate
approved stopped-server operation.

- Checks the original command at LOWEST and rechecks its current text at HIGHEST,
  respecting already-cancelled events. A normal-priority rewrite cannot silently
  route an allowed root into an unlisted or namespaced command.
- Keeps command arguments unchanged. Allowed roots do not grant permission to
  execute their handlers or subcommands.
- Namespaced player commands remain denied unless the player has intentional
  nordcommands.bypass. The owned management roots nordcommands and
  nordcommands:nordcommands require nordcommands.admin, without broad bypass.
- Strict typed configuration requires bare labels: no slash, arguments, namespace,
  whitespace or non-string entries. Unknown keys and corrupt/oversized YAML fail.
- Initial invalid policy blocks player commands, even bypass execution, except
  specifically authorized plugin management. Console dispatch is not filtered.
- Invalid reload retains the last valid immutable policy. A single bounded reader
  performs runtime file I/O off the game thread; duplicate reloads are rejected.
- A successful reload refreshes already-connected client command lists in batches
  of at most eight players per tick; execution uses the new policy immediately.
- Underlying command metadata/handler permissions must still be configured.
  Root allowlisting of resetpassword, tempban or regenchunk does NOT grant those rights.

Configuration remains plugins/NordCommands/config.yml. Existing production policy
is retained at deployment; the bundled default is not a reason to overwrite it.
Management commands: /nordcommands health and /nordcommands reload.
Reload is queued; wait for the explicit success/rejection message in console.

Build with build.ps1 using isolated local Paper 26.2 libraries. It runs
assertion-enabled tests and produces build/NordCommands-Paper-1.1.0.jar.
test-support/integration.cjs uses exact loopback-only local fixture paths and
fresh synthetic data. --baseline selects the unchanged old JAR for controlled
old-behavior reproduction. CommandsTestProbe is LOCAL-only and must never be
installed in production; it is excluded from the release JAR.

Limits: 1 MiB config, 256 entries, 64 characters per label, 512 characters denial
text, 32767 characters input, one reload request/result, 4096 view refresh entries.
Extra views can remain stale at refresh capacity, but the execution gate still
applies the new policy. Quit cleans denial-message bookkeeping. Hot-reload is
unsupported; normal shutdown closes the reader and clears owned work.

See SECURITY-1.1.0.md for evidence, scope and remaining limitations.
