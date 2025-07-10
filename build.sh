#!/bin/bash

echo "Building VelocityEssentials..."

# Build main plugin
echo "Building Velocity plugin..."
mvn clean package

# Build backend
echo "Building Paper backend..."
cd backend
mvn clean package
cd ..

# Copy JARs to output
mkdir -p output
cp target/*.jar output/
cp backend/target/*.jar output/

echo ""
echo "Build complete! JARs are in the output/ directory:"
ls -la output/