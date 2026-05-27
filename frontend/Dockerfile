# -------- Build Stage --------
FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

ARG VITE_API_URL=/api
ENV VITE_API_URL=${VITE_API_URL}

ENV NODE_OPTIONS=--max-old-space-size=8192

RUN npm run build

# -------- Runtime Stage --------
FROM nginxinc/nginx-unprivileged:1.28-alpine

USER root
RUN apk add --no-cache gettext

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY docker-entrypoint.sh /docker-entrypoint.sh

RUN chown -R 101:0 /usr/share/nginx/html /etc/nginx/conf.d /etc/nginx/templates /docker-entrypoint.sh \
  && chmod 500 /docker-entrypoint.sh

USER 101

EXPOSE 8080

ENTRYPOINT ["/docker-entrypoint.sh"]
