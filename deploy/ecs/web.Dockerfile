# "web" container for Amazon ECS: the React build served by nginx, which
# also does the gateway's job (security headers, rate limits, /api/ proxy).
#
# On EC2 the same work is done by two containers (frontend + gateway, see
# docker-compose.yml). In an ECS task all containers share one network, so
# one nginx is simpler: it serves the site and sends /api/ to the backend
# container at 127.0.0.1:8080.
#
# Build from the repository root:
#   docker build -f deploy/ecs/web.Dockerfile .

FROM node:22-alpine AS build

WORKDIR /app

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ .

# No VITE_API_BASE_URL: the site calls /api on its own address.
RUN npm run build

FROM nginx:1.27-alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY deploy/ecs/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
