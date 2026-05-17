# syntax=docker/dockerfile:1.7

# ---- Build stage ----------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /app

ARG VITE_LOCAL_AI_URL=/ai
ARG VITE_LIVE2D_MODEL=/models/haru/haru_greeter_t03.model3.json
ENV VITE_LOCAL_AI_URL=${VITE_LOCAL_AI_URL} \
    VITE_LIVE2D_MODEL=${VITE_LIVE2D_MODEL}

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci

COPY . .
RUN npm run build

# ---- Runtime stage --------------------------------------------------------
FROM nginx:alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

# Official nginx image runs as `nginx` user by default for worker processes.
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O- http://localhost/ >/dev/null || exit 1
