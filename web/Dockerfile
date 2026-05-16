# ============================================================
# STAGE 1: Install ALL dependencies
# ============================================================
FROM node:20-alpine AS deps

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci

# ============================================================
# STAGE 2: Build the Next.js application (standalone)
# ============================================================
FROM node:20-alpine AS build

WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Build Next.js (output: standalone)
RUN npm run build

# ============================================================
# STAGE 3: Production image (minimal)
# ============================================================
FROM node:20-alpine AS production

WORKDIR /app

# Copy standalone build output
COPY --from=build /app/.next/standalone ./

# Copy static assets (public, _next/static)
COPY --from=build /app/public ./public
COPY --from=build /app/.next/static ./.next/static

# Expose Next.js default port
EXPOSE 3000

# Start Next.js
CMD ["node", "server.js"]
