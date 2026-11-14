FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts
COPY . .
RUN npx ng build

FROM nginxinc/nginx-unprivileged:1.29-alpine
# The base image fills in ${VARIABLES} from these templates when the container starts.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist/frontend/browser /usr/share/nginx/html
RUN rm -f /usr/share/nginx/html/config.json
EXPOSE 8080
