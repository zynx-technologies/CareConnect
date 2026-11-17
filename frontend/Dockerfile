FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts
COPY . .
# Drop the local-dev config.json here: the nginx stage runs as a non-root user and cannot delete root-owned files.
RUN npx ng build && rm -f dist/frontend/browser/config.json

FROM nginxinc/nginx-unprivileged:1.29-alpine
# The base image fills in ${VARIABLES} from these templates when the container starts.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist/frontend/browser /usr/share/nginx/html
EXPOSE 8080
