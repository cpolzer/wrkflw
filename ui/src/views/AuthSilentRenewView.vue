<template><div></div></template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { silentRenew } from '@/auth/oidc'

onMounted(silentRenew)
</script>
