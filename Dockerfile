# Build Stage
FROM node:20-alpine AS build

# Set working directory
WORKDIR /app

# Copy package files for the mobile app
COPY mobile/package*.json ./mobile/

# Install dependencies
RUN cd mobile && npm install

# Copy the rest of the mobile app source
COPY mobile/ ./mobile/

# Build the web version of the Expo app
# Note: This requires the expo-cli and web dependencies to be in package.json
RUN cd mobile && npx expo export --platform web

# Serve Stage
FROM nginx:alpine

# Copy the static files from the build stage to Nginx
COPY --from=build /app/mobile/dist /usr/share/nginx/html

# Expose port 80
EXPOSE 80

# Start Nginx
CMD ["nginx", "-g", "daemon off;"]
