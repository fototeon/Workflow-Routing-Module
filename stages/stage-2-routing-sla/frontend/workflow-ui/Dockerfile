FROM node:22-alpine AS build
WORKDIR /build

# The image only needs the production bundle: browsers for the e2e suite are never used here.
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
    NPM_CONFIG_AUDIT=false \
    NPM_CONFIG_FUND=false \
    NPM_CONFIG_FETCH_RETRIES=5 \
    NPM_CONFIG_FETCH_RETRY_MAXTIMEOUT=120000

# `ca.crt` is optional (note the glob): drop your corporate root certificate next to this Dockerfile
# and the install trusts it. Networks that intercept TLS otherwise fail with SELF_SIGNED_CERT_IN_CHAIN.
COPY package.json package-lock.json ca.cr[t] ./
RUN if [ -f ca.crt ]; then export NODE_EXTRA_CA_CERTS=/build/ca.crt; fi && npm ci

COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
