FROM node:22-alpine AS builder
WORKDIR /app
ARG VERSION
ENV VITE_APP_VERSION=$VERSION
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
ARG VERSION
LABEL org.opencontainers.image.version="$VERSION"
LABEL org.opencontainers.image.title="ui"
LABEL org.opencontainers.image.source="https://github.com/cpolzer/wrkflw"
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 80
