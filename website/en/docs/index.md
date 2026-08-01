# Welcome to OpenTeams

**OpenTeams** is a team platform for Paper and Folia. It combines a Paper Dialog management experience, command fallback, transactional persistence, and a public addon API designed for stability.

## Quick links

<CardGrid>
  <DocCard title="Features" icon="✨" link="/en/docs/features" desc="Gameplay scope, consistency, and the extension platform." />
  <DocCard title="Download and build" icon="⬇️" link="/en/docs/download" desc="Get the source, build the JAR, and verify the artifact." />
  <DocCard title="Installation" icon="📦" link="/en/docs/installation" desc="Go from an empty server to a WRITABLE runtime." />
  <DocCard title="Commands" icon="⌨️" link="/en/docs/commands" desc="The complete player and administrator command tree." />
  <DocCard title="Configuration" icon="⚙️" link="/en/docs/configuration" desc="Every setting, default, and trade-off." />
  <DocCard title="Database" icon="🗄️" link="/en/docs/database" desc="Storage engines, lease fencing, and recovery." />
  <DocCard title="Troubleshooting" icon="🧯" link="/en/docs/troubleshooting" desc="Schema, read-only, Dialog, offline invite, and reload issues." />
  <DocCard title="Addon API" icon="🔌" link="/en/docs/addon-api" desc="Queries, mutations, events, and extension lifecycle." />
</CardGrid>

## When should you use OpenTeams?

OpenTeams fits servers that need:

- a complete team lifecycle: create, invite, join requests, leave, transfer, and disband;
- moderation by role priority: kick, ban, approve, and role assignment;
- a modern UI with command and chat fallbacks;
- transactional database state, audit records, and explicit cache consistency;
- addon contributions with ownership, permissions, and timeout contracts;
- Paper or Folia with Java 21 bytecode.

OpenTeams should **not yet** be treated as stable 1.0 if you need a complete custom-role editor, MySQL/MariaDB Testcontainers coverage, a public compatibility guarantee, or generated API reference. See [Release status](./release-status).

## Mental model

<StatGrid>

**Database**<br>
The authoritative source of truth.

**Snapshot cache**<br>
Hot-path reads never touch JDBC.

**Mutation pipeline**<br>
Policy → transaction → cache → event.

</StatGrid>

A `TeamSnapshot` is immutable data at one aggregate version. Methods ending in `Cached` never access the database. Authoritative queries and mutations return `CompletionStage` and complete on an OpenTeams worker thread.

## Requirements

| Component | Requirement |
|---|---|
| Server | Paper or Folia, API `1.21.11+` |
| Java bytecode | 21 |
| Build toolchain | Java 25 through Gradle |
| Storage | SQLite, MySQL, or MariaDB |
| Plugin dependencies | None required |

Continue with [Download and build](./download), then [Installation](./installation). Addon authors can go directly to the [Addon API](./addon-api).
