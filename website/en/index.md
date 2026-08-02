---
layout: home

hero:
  name: OpenTeams
  text: A team system should do more than run.
  tagline: It should be correct. A correctness-first team platform for Paper and Folia with Dialog UI, transactional storage, and a public addon API.
  image:
    src: /favicon.svg
    alt: OpenTeams
  actions:
    - theme: brand
      text: Get started
      link: /en/docs/installation
    - theme: alt
      text: Read the docs
      link: /en/docs/
    - theme: alt
      text: GitHub
      link: https://github.com/Alexteens24/OpenTeams

features:
  - icon: 🧭
    title: Dialog-first UX
    details: Command center, public discovery, invitation and request inboxes, with clickable chat fallback when Paper Dialog is unavailable.
  - icon: 🛡️
    title: Correctness-first
    details: Transactional mutations, optimistic versioning, lease fencing, immutable cache, and read-only degradation.
  - icon: 🔌
    title: Addon-ready
    details: Public service API for commands, settings, permissions, UI actions, translations, placeholders, and mutation policies.
  - icon: 🗄️
    title: Three database engines
    details: SQLite for one server, or MySQL and MariaDB for remote storage. JDBC concurrency is bounded per engine.
  - icon: 💬
    title: Gameplay hot paths
    details: Friendly fire and team chat are cache-only. Team-chat preferences persist across sessions.
  - icon: 🧰
    title: Safe operations
    details: Runtime state machine, recovery cache rebuild, audit retention, doctor command, and graceful lifecycle.
---

## One place for all documentation

From first installation, commands, and permissions to database recovery, the addon API, and consistency architecture—every section reflects the current source.

<CardGrid>
  <DocCard title="Installation" icon="📦" link="/en/docs/installation" desc="Requirements, build artifact, first boot, and verification checklist." />
  <DocCard title="Player guide" icon="👥" link="/en/docs/player-guide" desc="Create teams, invite offline players, handle public requests, roles, and team chat." />
  <DocCard title="Configuration" icon="⚙️" link="/en/docs/configuration" desc="Interactive reference for every config.yml key." />
  <DocCard title="Operations" icon="🩺" link="/en/docs/operations" desc="Deploy, backup, doctor, cleanup, read-only, and recovery runbooks." />
  <DocCard title="Addon API" icon="🧩" link="/en/docs/addon-api" desc="Service discovery, queries, mutations, threading, and lifecycle." />
  <DocCard title="Release status" icon="🚧" link="/en/docs/release-status" desc="What is complete and which release gates remain during indev." />
</CardGrid>

::: warning Currently in development
`0.1.0` is not a production-stable 1.0 release. The schema is a development baseline; upgrade migrations begin only after the first released schema is frozen.
:::
