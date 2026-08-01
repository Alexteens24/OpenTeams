package me.alexisbinh.openteams.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.ResourceBundle;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;

class LocalizedMessagesTest {
    @Test
    void fallsBackToEnglishAndAllowsAddonOverrides() {
        var messages = new LocalizedMessages(Locale.US,
                (locale, key) -> key.equals("addon.label") ? "Addon action" : null);

        assertThat(messages.text(Locale.JAPAN, "dashboard.create")).contains("Create a team");
        assertThat(messages.text(Locale.US, "addon.label")).isEqualTo("Addon action");
        assertThat(messages.text(Locale.US, "missing.key")).isEqualTo("missing.key");
    }

    @Test
    void serverLocaleCanOverrideClientLocale() {
        var messages = new LocalizedMessages(Locale.forLanguageTag("vi-VN"),
                (locale, key) -> null, false);

        assertThat(messages.playerText(Locale.US, "dashboard.no-team"))
                .contains("Bạn chưa tham gia team nào");
    }

    @Test
    void everyVietnameseMessageIsValidMiniMessage() {
        var bundle = ResourceBundle.getBundle(
                "me.alexisbinh.openteams.ui.messages", Locale.forLanguageTag("vi-VN"));
        var miniMessage = MiniMessage.miniMessage();

        assertThat(bundle.keySet()).hasSizeGreaterThan(150);
        bundle.keySet().forEach(key ->
                org.assertj.core.api.Assertions.assertThatCode(
                        () -> miniMessage.deserialize(bundle.getString(key)))
                        .as(key).doesNotThrowAnyException());
    }

    @Test
    void colorsAreReservedForAccentsAndStatuses() {
        var messages = new LocalizedMessages(Locale.US);

        assertThat(messages.component(Locale.US, "dashboard.members").color()).isNull();
        assertThat(messages.component(Locale.US, "dashboard.title").color()).isNotNull();
        assertThat(messages.component(Locale.US, "dashboard.create").color()).isNotNull();
        assertThat(messages.component(Locale.US, "dashboard.leave").color()).isNotNull();
        assertThat(messages.component(Locale.US, "success.created").color()).isNotNull();
        assertThat(messages.component(Locale.US, "error.forbidden").color()).isNotNull();
    }
}
