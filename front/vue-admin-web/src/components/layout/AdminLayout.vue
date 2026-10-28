<script setup>
import { RouterView } from 'vue-router'

import AppSidebar from './AppSidebar.vue'
import AppTopbar from './AppTopbar.vue'
import TagsView from './TagsView.vue'
</script>

<template>
  <div class="admin-layout">
    <AppSidebar />
    <div class="main-area">
      <AppTopbar />
      <TagsView />
      <main class="content">
        <RouterView v-slot="{ Component }">
          <component :is="Component" />
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
}

.main-area {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.content {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  background: var(--bg-page);
}
</style>