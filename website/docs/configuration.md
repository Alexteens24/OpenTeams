# Configuration

File: `plugins/OpenTeams/config.yml`. OpenTeams gọi `saveDefaultConfig()` ở startup; thay đổi cần full server restart.

## Default config

```yaml
database:
  type: sqlite
  namespace: default
  sqlite-file: openteams.db
  host: localhost
  port: 3306
  database: openteams
  username: openteams
  password: change-me
  pool-size: 8
  connection-timeout-ms: 3000

team:
  default-member-limit: 20
  invitation-expiry-seconds: 604800

ui:
  mode: auto
  default-locale: vi_VN
  follow-player-locale: false

friendly-fire:
  mode: deny

audit:
  retention-days: 90

chat:
  format: "<aqua>[<tag>]</aqua> <white><player>:</white> <gray><message></gray>"

addons:
  policy-global-timeout-ms: 500
```

<ConfigGroup title="database" description="Storage engine, namespace và JDBC pool behavior.">
  <ConfigProperty name="database.type" type="enum" default-value="sqlite" required>
    `sqlite`, `mysql` hoặc `mariadb`. Giá trị không hợp lệ làm initialization fail-safe và plugin tự disable.
  </ConfigProperty>
  <ConfigProperty name="database.namespace" type="string" default-value="default" required>
    Logical data/lease boundary. Mọi Core table key đều chứa namespace. Chỉ một live writer được giữ lease trên cùng namespace.
  </ConfigProperty>
  <ConfigProperty name="database.sqlite-file" type="path" default-value="openteams.db">
    Path tương đối với `plugins/OpenTeams/`, chỉ dùng cho SQLite.
  </ConfigProperty>
  <ConfigProperty name="database.host" type="string" default-value="localhost">
    MySQL/MariaDB hostname.
  </ConfigProperty>
  <ConfigProperty name="database.port" type="integer" default-value="3306">
    MySQL/MariaDB TCP port.
  </ConfigProperty>
  <ConfigProperty name="database.database" type="string" default-value="openteams">
    Remote schema/database name.
  </ConfigProperty>
  <ConfigProperty name="database.username" type="string" default-value="openteams">
    Remote login. SQLite bỏ qua.
  </ConfigProperty>
  <ConfigProperty name="database.password" type="string" default-value="change-me">
    Remote password. Bảo vệ file config bằng filesystem permissions.
  </ConfigProperty>
  <ConfigProperty name="database.pool-size" type="integer" default-value="8">
    Clamp tối thiểu 1. MySQL/MariaDB JDBC semaphore dùng pool size; SQLite Core vẫn giới hạn concurrency là 1.
  </ConfigProperty>
  <ConfigProperty name="database.connection-timeout-ms" type="milliseconds" default-value="3000">
    Clamp tối thiểu 250 ms. Hikari connection timeout và failure detection boundary.
  </ConfigProperty>
</ConfigGroup>

### JDBC URLs được tạo

```text
SQLite    jdbc:sqlite:<absolute-plugin-data-path>/<sqlite-file>
MySQL     jdbc:mysql://<host>:<port>/<database>?useSSL=true&tcpKeepAlive=true
MariaDB   jdbc:mariadb://<host>:<port>/<database>?tcpKeepAlive=true
```

<ConfigGroup title="team" description="Defaults áp dụng khi tạo aggregate mới.">
  <ConfigProperty name="team.default-member-limit" type="integer" default-value="20">
    Member capacity của team mới. Existing team giữ value đã persisted.
  </ConfigProperty>
  <ConfigProperty name="team.invitation-expiry-seconds" type="seconds" default-value="604800">
    7 ngày mặc định. Store dùng cùng duration cho invitation và join request expiry.
  </ConfigProperty>
</ConfigGroup>

<ConfigGroup title="ui" description="Paper Dialog adapter, fallback và locale selection.">
  <ConfigProperty name="ui.mode" type="enum" default-value="auto">
    `auto` và `dialog` tạo Dialog adapter có chat fallback; `chat` luôn dùng clickable chat. Unknown value disable plugin lúc startup.
  </ConfigProperty>
  <ConfigProperty name="ui.default-locale" type="locale" default-value="vi_VN">
    Chuyển `_` thành `-` rồi parse bằng `Locale.forLanguageTag`. Dùng làm fallback bundle.
  </ConfigProperty>
  <ConfigProperty name="ui.follow-player-locale" type="boolean" default-value="false">
    `true` ưu tiên locale Minecraft client; key thiếu fallback về default locale rồi về key text.
  </ConfigProperty>
</ConfigGroup>

<ConfigGroup title="gameplay" description="Friendly fire và team chat.">
  <ConfigProperty name="friendly-fire.mode" type="enum" default-value="deny">
    `allow` cho phép server default và typed team setting default `true`; giá trị khác được hiểu là deny.
  </ConfigProperty>
  <ConfigProperty name="chat.format" type="MiniMessage" default-value="[tag] player: message">
    Hỗ trợ `&lt;tag&gt;`, `&lt;player&gt;`, `&lt;message&gt;`. User message được truyền dưới dạng Adventure Component, không parse như format markup.
  </ConfigProperty>
</ConfigGroup>

<ConfigGroup title="maintenance & addons">
  <ConfigProperty name="audit.retention-days" type="days" default-value="90">
    Cleanup cutoff cho audit entries. Cleanup chỉ chạy khi admin xác nhận command.
  </ConfigProperty>
  <ConfigProperty name="addons.policy-global-timeout-ms" type="milliseconds" default-value="500">
    Deadline chung cho toàn bộ pre-commit policy chain. Mỗi policy còn có timeout riêng tối đa 2 giây.
  </ConfigProperty>
</ConfigGroup>

## Ví dụ MySQL

```yaml
database:
  type: mysql
  namespace: survival-main
  host: db.internal.example
  port: 3306
  database: openteams
  username: openteams
  password: use-a-secret-here
  pool-size: 8
  connection-timeout-ms: 3000
```

::: danger Namespace không phải server ID tùy ý
Hai server active dùng cùng database **và cùng namespace** sẽ cạnh tranh một lease; chỉ một instance được ghi. Nếu chúng phải độc lập, dùng namespace khác. Nếu bạn kỳ vọng multi-server active-active shared teams, kiến trúc hiện tại không cung cấp điều đó.
:::

## Validation behavior

Config parsing clamp một số numeric values nhưng không tự sửa file. Lỗi enum/database initialization được log và plugin disable safely. Sau khi thay config, full restart và chạy `/teamadmin doctor`.
