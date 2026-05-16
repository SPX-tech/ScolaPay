# ============================================================
# STAGE 1: Install ALL dependencies (including devDependencies)
# ============================================================
FROM node:20-alpine AS deps

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci

# ============================================================
# STAGE 2: Build the application
# ============================================================
FROM node:20-alpine AS build

WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Generate Prisma client
RUN npx prisma generate

# Build NestJS
RUN npm run build

# ============================================================
# STAGE 3: Production image
# ============================================================
FROM node:20-alpine AS production

WORKDIR /app

# Install only production dependencies
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force

# Copy Prisma generated client from build stage
COPY --from=build /app/node_modules/.prisma ./node_modules/.prisma
COPY --from=build /app/prisma ./prisma

# Copy built application
COPY --from=build /app/dist ./dist

# Expose the API port
EXPOSE 3000

# Fix permissions for the node user
RUN chown -R node:node /app

# Use non-root user for security
USER node

# Run database migrations and start the server
CMD ["sh", "-c", "npx prisma migrate deploy && node dist/src/main"]
