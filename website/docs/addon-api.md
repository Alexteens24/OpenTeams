# Addon API

`openteams-api` là module public, tách khỏi Paper implementation và Dialog internals. API runtime hiện trả version string `1.0.0`, nhưng binary compatibility gate chưa được thiết lập trong indev.

## Khai báo dependency

`plugin.yml`:

```yaml
name: MyTeamsAddon
version: 1.0.0
main: com.example.myaddon.MyAddonPlugin
api-version: '1.21.11'
folia-supported: true
depend: [OpenTeams]
```

Trong monorepo/composite build hiện tại:

```kotlin
dependencies {
    compileOnly(project(":openteams-api"))
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}
```

API chưa được publish lên public Maven repository. Nếu addon ở repo riêng trong indev, dùng local/composite build hoặc publish artifact API vào repository nội bộ của bạn. Không shade API classes vào addon khi Core đã cung cấp chúng.

## Lấy service

```java
public final class MyAddonPlugin extends JavaPlugin {
    private OpenTeams openTeams;

    @Override
    public void onEnable() {
        openTeams = Bukkit.getServicesManager().load(OpenTeams.class);
        if (openTeams == null) {
            throw new IllegalStateException("OpenTeams API service is unavailable");
        }

        getLogger().info("OpenTeams API " + openTeams.apiVersion());
    }
}
```

Không dùng `Bukkit.getPluginManager().getPlugin("OpenTeams")` rồi cast sang implementation class.

## OpenTeams entry point

| Method | Trả về |
|---|---|
| `apiVersion()` | API version string |
| `teams()` | `TeamService` queries và mutations |
| `commands()` | Command registry |
| `placeholders()` | Cache-only placeholder registry |
| `settings()` | Typed setting registry |
| `permissions()` | Team permission registry |
| `userInterface()` | UI action registry |
| `translations()` | Locale translation registry |
| `policies()` | Pre-commit policy registry |
| `readOnly()` | `true` nếu runtime không writable |

## Cached queries

Cached methods thread-safe và không chạm JDBC:

```java
Optional<TeamSnapshot> byId = api.teams().findCached(teamId);
Optional<TeamSnapshot> byPlayer = api.teams().findByPlayerCached(playerId);
MembershipLookup membership = api.teams().membershipCached(playerId);
TeamRelation relation = api.teams().relationCached(firstId, secondId);
boolean allowed = api.teams().hasPermissionCached(playerId, "example.use");
```

### Membership status

| Status | Ý nghĩa |
|---|---|
| `LOADING` | Authoritative membership load đang chạy |
| `PRESENT` | Có immutable team snapshot |
| `ABSENT` | Đã load và player không thuộc team |
| `FAILED` | Lần load authoritative gần nhất thất bại |

Không coi `LOADING`/`FAILED` là chắc chắn không có team. `TeamRelation.UNKNOWN` cũng khác `DIFFERENT`.

## Authoritative queries

Các method sau trả `CompletionStage` và có thể truy cập JDBC:

```java
api.teams().find(teamId);
api.teams().findByPlayer(playerId);
api.teams().searchPublicTeams(query, page, pageSize);
api.teams().invitations(playerId);
api.teams().joinRequests(teamId);
api.teams().joinRequestsByPlayer(playerId);
api.teams().outgoingInvitations(teamId);
api.teams().bans(teamId);
api.teams().roles();
api.teams().resolvePlayers(playerIds);
api.teams().rememberPlayer(playerId, currentName);
```

Directory records là immutable read models: `TeamSummary`, `PlayerSummary`, `Invitation`, `JoinRequest`, `OutgoingInvitation`, `OutgoingJoinRequest`, `Ban`, `Role` và paginated `Page<T>`.

## Snapshot contract

`TeamSnapshot` chứa:

- identity/name/tag;
- owner, state và visibility;
- member limit và aggregate version;
- created/updated timestamps;
- copied settings map;
- copied member list.

`TeamMemberSnapshot` chứa player ID, role key, copied permission set, joined/last-active time và helper `hasPermission()` có wildcard support.

Snapshot không live. Không giữ nó và kỳ vọng mutation khác tự cập nhật object.

## Mutations

Mọi domain write nhận request record và trả:

```java
CompletionStage<OperationResult<TeamSnapshot>>
```

Ví dụ:

```java
api.teams().invite(new TeamRequests.TargetAction(
    actorId, teamId, targetId
)).thenAccept(result -> {
    if (result instanceof OperationResult.Success<TeamSnapshot> success) {
        TeamSnapshot committed = success.value();
        UUID correlationId = success.correlationId();
        return;
    }

    var failure = (OperationResult.Failure<TeamSnapshot>) result;
    getLogger().warning(failure.code() + " / " + failure.correlationId());
});
```

Mutation surface:

| Nhóm | Methods |
|---|---|
| Lifecycle | `create`, `disband` |
| Invitations | `invite`, `acceptInvitation`, `declineInvitation`, `revokeInvitation` |
| Membership | `leave`, `kick`, `transferOwnership`, `changeRole` |
| Identity | `rename`, `setTag`, `setVisibility` |
| Join requests | `requestJoin`, `acceptJoinRequest`, `rejectJoinRequest`, `cancelJoinRequest` |
| Moderation | `ban`, `unban` |
| Settings | `setSetting` |

Request records luôn mang `actorId`; team actions mang `TeamId`; target actions thêm `targetId`.

## Error codes

`OperationResult.Failure` có `TeamErrorCode`, translation message key và correlation ID.

```text
NOT_FOUND · FORBIDDEN · INVALID_ARGUMENT · CONFLICT · LIMIT_REACHED
ALREADY_IN_TEAM · NOT_IN_TEAM · INVITATION_NOT_FOUND · INVITATION_EXPIRED
READ_ONLY · DATABASE_UNAVAILABLE · INTERNAL_ERROR
```

Addon nên branch theo error code, không parse human message.

## Threading contract

::: danger Completion callback không entity-safe
Authoritative query/mutation hoàn tất trên OpenTeams worker virtual thread. Trước khi thay đổi Player, Entity hoặc Inventory, schedule về owning Paper/Folia scheduler.
:::

```java
api.teams().findByPlayer(player.getUniqueId()).thenAccept(team ->
    player.getScheduler().run(this, task -> {
        player.sendMessage(Component.text(team.map(TeamSnapshot::name).orElse("No team")));
    }, null)
);
```

## Post-commit event

```java
@EventHandler
public void onCommitted(TeamMutationCommittedEvent event) {
    MutationIntent intent = event.intent();
    UUID correlationId = intent.correlationId();
    TeamSnapshot after = event.after();
}
```

Event là Bukkit async event, phát **sau database commit và cache publication**. Nó observational, không cancellable và không thể rollback mutation. `before()` có thể empty, ví dụ create.

## Lifecycle

Giữ mọi `Registration` và close trong `onDisable`:

```java
private final List<Registration> registrations = new ArrayList<>();

@Override
public void onDisable() {
    registrations.forEach(Registration::close);
    registrations.clear();
}
```

Core vẫn nghe `PluginDisableEvent` và gỡ contributions theo owner để bảo vệ khi addon cleanup thiếu.

Xem [Extension points](./extensions) cho constructor và contract cụ thể của từng registry.
