<template>
  <h1 class="titulo">Pagina Principal</h1>

  <RouterLink to="/productosAdmin" class="btn">Ver Productos</RouterLink>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<style scoped>
.titulo {
  color: green;
}
.btn {
  display: inline-block;
  padding: 10px 20px;
  background-color: #42b883;
  color: white;
  text-decoration: none;
  border-radius: 6px;
  font-weight: bold;
  cursor: pointer;
}
.btn:hover {
  background-color: #369870;
}
</style>
