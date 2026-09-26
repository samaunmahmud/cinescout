# syntax=docker/dockerfile:1

# --- build: the static web app ---------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts
COPY . .
# Map tiles (see lib/mapTiles.ts): baked in at build time, like every VITE_ variable.
ARG VITE_MAP_TILE_URL=""
ARG VITE_MAP_TILE_ATTRIBUTION=""
RUN npm run build

# --- serve: nginx with the app, proxying the API to the backend -------------------------------------
FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist /usr/share/nginx/html
# nginx substitutes these into the template at start-up.
ENV BACKEND_URL=http://backend:8081
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
