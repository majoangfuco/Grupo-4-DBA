<template>
  <div class="ordenes-pagina">
    <h1 class="titulo">Mis órdenes</h1>
    <p>Aquí aparecerán tus órdenes cuando el backend esté conectado.</p>
    <RouterLink to="/" class="btn">Volver a la página principal</RouterLink>
  </div>
</template>

<script setup lang="ts">
// Componente de placeholder para la página de órdenes de clientes.
</script>

<style scoped>
.ordenes-pagina {
  padding: 24px;
}
.titulo {
  color: #42b883;
  margin-bottom: 16px;
}
.btn {
  display: inline-block;
  padding: 10px 18px;
  background-color: #35495e;
  color: white;
  text-decoration: none;
  border-radius: 6px;
  font-weight: 600;
}
.btn:hover {
  background-color: #2c3e50;
}
</style>
