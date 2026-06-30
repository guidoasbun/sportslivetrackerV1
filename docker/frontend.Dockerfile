# --- Stage 1: Build ---
FROM node:22-alpine AS builder
WORKDIR /app

# Accept build arguments
ARG NEXT_PUBLIC_COGNITO_CLIENT_ID
ARG NEXT_PUBLIC_COGNITO_DOMAIN
ARG NEXT_PUBLIC_APP_URL

# Set them as environment variables during the build phase
ENV NEXT_PUBLIC_COGNITO_CLIENT_ID=$NEXT_PUBLIC_COGNITO_CLIENT_ID
ENV NEXT_PUBLIC_COGNITO_DOMAIN=$NEXT_PUBLIC_COGNITO_DOMAIN
ENV NEXT_PUBLIC_APP_URL=$NEXT_PUBLIC_APP_URL

# Install dependencies first (caches this step if package.json hasn't changed)
COPY package*.json ./
RUN npm ci

# Copy the rest of the source code and build
COPY . .
RUN npm run build

# --- Stage 2: Production Runtime ---
FROM node:22-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000

# Copy only the necessary files from the builder stage
COPY --from=builder /app/public ./public
# The standalone folder contains the compiled Next.js server
COPY --from=builder /app/.next/standalone ./
# The static folder contains all the compiled CSS/JS assets
COPY --from=builder /app/.next/static ./.next/static

EXPOSE 3000

# Start the standalone server!
CMD ["node", "server.js"]
