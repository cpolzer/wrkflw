<script setup lang="ts">
import AppNav from '@/components/AppNav.vue'
import ErrorToast from '@/components/ErrorToast.vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const isPublicRoute = () => route.meta.public === true
</script>

<template>
  <AppNav v-if="!isPublicRoute()" />
  <main class="app-main">
    <RouterView />
  </main>
  <ErrorToast />
</template>

<style>
* { box-sizing: border-box; }
body { margin: 0; font-family: sans-serif; background: #f5f5f5; }
.app-main { padding: 1.5rem; max-width: 1200px; margin: 0 auto; }
</style>
