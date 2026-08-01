<script setup lang="ts">
defineProps<{ name: string; type: string; defaultValue: string; required?: boolean }>();
</script>

<template>
  <article class="ot-config-property">
    <div class="ot-config-property__meta">
      <code>{{ name }}</code><span>{{ type }}</span><em>mặc định: {{ defaultValue }}</em><b v-if="required">bắt buộc</b>
    </div>
    <div class="ot-config-property__body"><slot /></div>
  </article>
</template>
