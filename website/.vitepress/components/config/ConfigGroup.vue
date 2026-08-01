<script setup lang="ts">defineProps<{ title: string; description?: string }>();</script>

<template>
  <section class="ot-config-group">
    <header><h3>{{ title }}</h3><p v-if="description">{{ description }}</p></header>
    <div><slot /></div>
  </section>
</template>
