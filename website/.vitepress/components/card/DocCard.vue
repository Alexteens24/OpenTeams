<script setup lang="ts">
import { withBase } from "vitepress";

const props = defineProps<{ title: string; icon?: string; link: string; desc: string }>();
const href = props.link.startsWith("/") ? withBase(props.link) : props.link;
</script>

<template>
  <a class="ot-doc-card" :href="href">
    <span class="ot-doc-card__icon">{{ icon || "↗" }}</span>
    <div><strong>{{ title }}</strong><p>{{ desc }}</p></div>
    <span class="ot-doc-card__arrow">→</span>
  </a>
</template>
