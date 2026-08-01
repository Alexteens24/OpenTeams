import DefaultTheme from "vitepress/theme";
import type { Theme } from "vitepress";
import CardGrid from "../components/card/CardGrid.vue";
import DocCard from "../components/card/DocCard.vue";
import ConfigGroup from "../components/config/ConfigGroup.vue";
import ConfigProperty from "../components/config/ConfigProperty.vue";
import StatGrid from "../components/StatGrid.vue";
import "./style.css";

export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    app.component("CardGrid", CardGrid);
    app.component("DocCard", DocCard);
    app.component("ConfigGroup", ConfigGroup);
    app.component("ConfigProperty", ConfigProperty);
    app.component("StatGrid", StatGrid);
  },
} satisfies Theme;
