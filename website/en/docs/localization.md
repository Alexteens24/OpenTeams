# UI & localization

OpenTeams provides two UI adapters and message catalogs in `openteams-dialog-ui`.

## UI modes

| Mode | Behavior |
|---|---|
| `auto` | Dialog adapter; clickable chat fallback if dynamic Dialog creation fails |
| `dialog` | Currently the same Dialog and fallback behavior as `auto` |
| `chat` | Adventure chat components with run-command click events only |

Experimental Paper Dialog types never enter the public API. Addons contribute platform-neutral `UiAction`s and Core chooses how to render them.

## Locale selection

With `follow-player-locale: false`, everyone uses `ui.default-locale`. When enabled, lookup tries addon translations in the player locale, then the Core bundle in that locale, then the default locale, and finally displays the key. Bundled catalogs currently include base English and `vi_VN`.

Default messages are mostly uncolored. Color is reserved for success/error feedback, destructive/confirmation actions, selected actions, enabled state, and clickable command calls-to-action. Small caps are limited to special labels/headings.

If an async Dialog mutation fails, the Dialog closes before the error component is sent, ensuring the player can read chat.

## Addon translations

```java
Registration translations = api.translations().register(
    plugin, Locale.forLanguageTag("en-US"),
    Map.of("example.ui.label", "Example tool",
           "example.ui.description", "Open the addon feature")
);
```

Use addon-namespaced keys in `labelKey` and `descriptionKey`, never raw display strings.

## MiniMessage chat format

`chat.format` supports `<tag>`, `<player>`, and `<message>`. The last value is an Adventure message component. Test custom syntax on a staging server; invalid MiniMessage markup may render unexpectedly.
