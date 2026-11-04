<script setup>
import { watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

watch(
  () => userStore.unauthorizedMessage,
  (message) => {
    if (!message) return
    userStore.clearUnauthorizedMessage()
    if (route.meta?.requiresAuth) {
      router.replace({ name: 'login', query: { redirect: route.fullPath, reason: 'expired' } })
    }
  },
)
</script>

<template>
  <RouterView v-slot="{ Component }">
    <Transition name="page-fade" mode="out-in">
      <component :is="Component" />
    </Transition>
  </RouterView>
</template>
