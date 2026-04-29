<template>
  <h1 class="titulo">Pagina de productos</h1>

  <RouterLink to="/" class="btn">Volver a la página principal</RouterLink>
</template>

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
