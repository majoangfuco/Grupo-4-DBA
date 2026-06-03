# Etapa 1: Construcción (Build)
FROM node:20-alpine AS build
WORKDIR /app

# Copiar package.json y package-lock.json e instalar dependencias
COPY package*.json ./
RUN npm install

# Copiar el resto del código y compilar para producción
COPY . .
RUN npm run build

# Etapa 2: Servidor Web (Nginx)
FROM nginx:alpine
# Copiar los archivos compilados de Vue a la carpeta que sirve Nginx
COPY --from=build /app/dist /usr/share/nginx/html

# Copiar nuestra configuración personalizada de Nginx para soportar Vue Router
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Exponer el puerto 80
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
