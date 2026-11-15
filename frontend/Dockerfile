# Imagen de un cliente Angular (tienda o admin) servido con nginx.
#   docker build --build-arg APP=tienda --build-arg API_URL=http://localhost:8080 -t tienda-cliente .
FROM node:22-alpine AS build
ARG APP=tienda
ARG API_URL=http://localhost:8080
WORKDIR /app
COPY package*.json ./
# npm install (y no npm ci): el lock de npm 10 genera un falso "fuera de sincronía" con Angular 18
RUN npm install --no-audit --no-fund
COPY . .
# La dirección del servidor se fija al construir la imagen (el navegador la usará tal cual)
RUN sed -i "s#http://localhost:8080#${API_URL}#" projects/shared/src/config.ts \
 && npx ng build ${APP} --configuration production

FROM nginx:alpine
ARG APP=tienda
COPY --from=build /app/dist/${APP}/browser /usr/share/nginx/html
EXPOSE 80
