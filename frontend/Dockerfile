# syntax=docker/dockerfile:1
# Stage 1: Build Angular SPA
FROM node:22-alpine AS builder
WORKDIR /app

# Copia manifesti npm per sfruttare la cache dei layer
COPY package.json package-lock.json ./
RUN npm ci || npm install

# Copia sorgenti ed esegui la build di produzione
COPY . .
RUN npm run build

# Stage 2: Nginx server minimale e performante
FROM nginx:alpine
COPY --from=builder /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
