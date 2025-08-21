#!/bin/bash

# Color codes for pretty output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================"
echo "   VelocityEssentials Version Manager"
echo -e "========================================${NC}"
echo

# Get current version from pom.xml
CURRENT_VERSION=$(grep -m1 '<version>' pom.xml | sed 's/.*<version>\(.*\)<\/version>.*/\1/')

echo -e "Current version: ${YELLOW}$CURRENT_VERSION${NC}"
echo

# Parse current version
IFS='.' read -ra VERSION_PARTS <<< "$CURRENT_VERSION"
MAJOR=${VERSION_PARTS[0]}
MINOR=${VERSION_PARTS[1]}
PATCH=${VERSION_PARTS[2]}

# Calculate next versions
NEXT_PATCH=$((PATCH + 1))
NEXT_MINOR=$((MINOR + 1))
NEXT_MAJOR=$((MAJOR + 1))

echo "Available version options:"
echo -e "  ${GREEN}1)${NC} Patch Release: ${YELLOW}$MAJOR.$MINOR.$NEXT_PATCH${NC}  (Bug fixes)"
echo -e "  ${GREEN}2)${NC} Minor Release: ${YELLOW}$MAJOR.$NEXT_MINOR.0${NC}        (New features)"
echo -e "  ${GREEN}3)${NC} Major Release: ${YELLOW}$NEXT_MAJOR.0.0${NC}               (Breaking changes)"
echo -e "  ${GREEN}4)${NC} Custom Version"
echo -e "  ${GREEN}5)${NC} Keep current version and build"
echo -e "  ${RED}0)${NC} Cancel"
echo

read -p "Select option (0-5): " CHOICE

case $CHOICE in
    0)
        echo -e "${RED}Build cancelled.${NC}"
        exit 0
        ;;
    1)
        NEW_VERSION="$MAJOR.$MINOR.$NEXT_PATCH"
        ;;
    2)
        NEW_VERSION="$MAJOR.$NEXT_MINOR.0"
        ;;
    3)
        NEW_VERSION="$NEXT_MAJOR.0.0"
        ;;
    4)
        read -p "Enter custom version (e.g., 1.4.0): " NEW_VERSION
        ;;
    5)
        echo -e "${GREEN}Keeping version $CURRENT_VERSION${NC}"
        NEW_VERSION=$CURRENT_VERSION
        ;;
    *)
        echo -e "${RED}Invalid choice!${NC}"
        exit 1
        ;;
esac

if [ "$CHOICE" != "5" ]; then
    echo
    echo -e "${BLUE}Updating to version $NEW_VERSION...${NC}"
    
    # Update all pom.xml files
    echo "Updating main pom.xml..."
    mvn versions:set -DnewVersion=$NEW_VERSION -q
    
    echo "Updating backend pom.xml..."
    cd backend
    mvn versions:set -DnewVersion=$NEW_VERSION -q
    cd ..
    
    echo
    echo -e "${GREEN}========================================"
    echo -e "Version updated to $NEW_VERSION"
    echo -e "========================================${NC}"
fi

echo
echo -e "${BLUE}Building VelocityEssentials v$NEW_VERSION...${NC}"
echo

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
cp target/*.jar output/ 2>/dev/null
cp backend/target/*.jar output/ 2>/dev/null

echo
echo -e "${GREEN}========================================"
echo -e "Build complete! Version: $NEW_VERSION"
echo -e "========================================${NC}"
echo "JARs are in the output/ directory:"
ls -1 output/*.jar | xargs -n1 basename
echo

# Optional: Create git tag
read -p "Create git tag for v$NEW_VERSION? (y/n): " CREATE_TAG
if [[ $CREATE_TAG =~ ^[Yy]$ ]]; then
    git add -A
    git commit -m "Release version $NEW_VERSION"
    git tag -a v$NEW_VERSION -m "Version $NEW_VERSION"
    echo -e "${GREEN}Git tag v$NEW_VERSION created!${NC}"
    echo "Don't forget to push: git push origin v$NEW_VERSION"
fi