#!/bin/bash

# Script to create and push a version tag with current date format vYYYY.MM.DD
# Usage: ./create-release-tag.sh [custom-version-suffix]

set -e

# Get current date in YYYY.MM.DD format
CURRENT_DATE=$(date +%Y.%m.%d)

# Use custom suffix if provided, otherwise just date
if [ $# -eq 0 ]; then
    VERSION="v${CURRENT_DATE}"
else
    VERSION="v${CURRENT_DATE}-$1"
fi

echo "Creating and pushing tag: $VERSION"

# Create annotated tag with current date
git tag -a "$VERSION" -m "Release $VERSION"

# Push the tag to trigger Gitea Actions workflow (primary)
git push gitea "$VERSION"

# Optionally push to GitHub as well (comment out if not needed)
# git push origin "$VERSION"

echo "Tag $VERSION created and pushed. GitHub workflow should start building and releasing."