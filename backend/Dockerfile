# ============================================================
# STAGE 1: Install ALL dependencies (including devDependencies)
# ============================================================
FROM node:26.12.1-alpine AS base
RUN npm install --global npm@11.19.1

FROM base AS deps

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci

# ============================================================
# STAGE 2: Build the application
# ============================================================
FROM base AS build

WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Generate Prisma client
RUN DATABASE_URL=postgresql://build:build@localhost:5432/build npm run prisma:generate

# Build NestJS
RUN npm run build

# ============================================================
# STAGE 3: Production image
# ============================================================
FROM base AS production

WORKDIR /app

# Install only production dependencies
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force

COPY --from=build /app/prisma ./prisma

# Copy built application
COPY --from=build /app/dist ./dist

# Expose the API port
EXPOSE 3000

# Fix permissions for the node user
RUN chown -R node:node /app

# Use non-root user for security
USER node

# Les migrations s'exécutent dans l'image dédiée avant le démarrage des réplicas.
CMD ["node", "dist/src/main.js"]

FROM build AS migration
CMD ["npm", "run", "prisma:migrate:deploy"]
