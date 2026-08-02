---
layout: home

hero:
  name: OpenTeams
  text: Team system không chỉ chạy.
  tagline: Nó phải chạy đúng. Nền tảng quản lý team correctness-first cho Paper và Folia, với Dialog UI, transactional storage và public addon API.
  image:
    src: /favicon.svg
    alt: OpenTeams
  actions:
    - theme: brand
      text: Bắt đầu cài đặt
      link: /docs/installation
    - theme: alt
      text: Đọc tài liệu
      link: /docs/
    - theme: alt
      text: GitHub
      link: https://github.com/Alexteens24/OpenTeams

features:
  - icon: 🧭
    title: Dialog-first UX
    details: Command center, public discovery, invitation và request inbox. Tự fallback về clickable chat khi Paper Dialog không khả dụng.
  - icon: 🛡️
    title: Correctness-first
    details: Transactional mutations, optimistic versioning, lease fencing, immutable cache và read-only degradation.
  - icon: 🔌
    title: Addon-ready
    details: Public service API cho commands, settings, permissions, UI actions, translations, placeholders và mutation policies.
  - icon: 🗄️
    title: Ba database engines
    details: SQLite cho một server; MySQL và MariaDB cho remote storage. JDBC concurrency được giới hạn theo engine.
  - icon: 💬
    title: Gameplay hot paths
    details: Friendly fire và team chat đọc cache-only. Team-chat preference được lưu qua các phiên đăng nhập.
  - icon: 🧰
    title: Vận hành an toàn
    details: Runtime state machine, recovery cache rebuild, audit retention, doctor command và graceful lifecycle.
---

## Một nơi cho toàn bộ tài liệu

Từ cài đặt lần đầu, commands và permissions đến database recovery, addon API và kiến trúc consistency — mọi phần đều được kiểm chứng với source hiện tại.

<CardGrid>
  <DocCard title="Cài đặt" icon="📦" link="/docs/installation" desc="Requirements, build artifact, first boot và checklist xác minh." />
  <DocCard title="Player guide" icon="👥" link="/docs/player-guide" desc="Tạo team, mời offline, public requests, roles và team chat." />
  <DocCard title="Configuration" icon="⚙️" link="/docs/configuration" desc="Interactive reference cho mọi key trong config.yml." />
  <DocCard title="Operations" icon="🩺" link="/docs/operations" desc="Deploy, backup, doctor, cleanup, read-only và recovery runbook." />
  <DocCard title="Addon API" icon="🧩" link="/docs/addon-api" desc="Service discovery, queries, mutations, threading và lifecycle." />
  <DocCard title="Release status" icon="🚧" link="/docs/release-status" desc="Những gì đã hoàn thành và release gates còn mở ở giai đoạn indev." />
</CardGrid>

::: warning Đang trong giai đoạn indev
`0.1.0` chưa phải production-stable 1.0. Schema hiện là development baseline; upgrade migrations chỉ bắt đầu sau khi schema phát hành đầu tiên được đóng băng.
:::
