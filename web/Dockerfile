# ============================================================
# STAGE 1: Install ALL dependencies
# ============================================================
FROM node:24.22.1-alpine AS base
RUN npm install --global npm@11.19.1

FROM base AS deps

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci

# ============================================================
# STAGE 2: Build the Next.js application (standalone)
# ============================================================
FROM base AS build

WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# NEXT_PUBLIC_* variables are inlined by Next.js at BUILD time, not runtime.
# It must therefore be provided as a build arg (wired in docker-compose) so the
# client bundle calls the right API host instead of the localhost fallback.
ARG NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL

# Build Next.js (output: standalone)
RUN npm run build

# ============================================================
# STAGE 3: Production image (minimal)
# ============================================================
FROM base AS production

WORKDIR /app

# Copy standalone build output
COPY --from=build /app/.next/standalone ./

# Copy static assets (public, _next/static)
COPY --from=build /app/public ./public
COPY --from=build /app/.next/static ./.next/static

# Expose Next.js default port
EXPOSE 3000

ENV NODE_ENV=production HOSTNAME=0.0.0.0 PORT=3000
USER node

# Start Next.js
CMD ["node", "server.js"]
