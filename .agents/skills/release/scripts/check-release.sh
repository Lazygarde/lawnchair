#!/usr/bin/env bash
set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=== [1/4] Checking Git Status ===${NC}"
git status -s

echo -e "\n${BLUE}=== [2/4] Latest Release Tags ===${NC}"
LATEST_TAG=$(git tag --list "lib-v*" --sort=-v:refname | head -n 5 || true)
echo "Recent tags:"
echo "$LATEST_TAG"

echo -e "\n${BLUE}=== [3/4] Processing launcher-ui Manifest ===${NC}"
./gradlew :launcher-ui:processLawnWithQuickstepReleaseManifest --quiet

MERGED_MANIFEST="launcher-ui/build/intermediates/merged_manifest/lawnWithQuickstepRelease/processLawnWithQuickstepReleaseManifest/AndroidManifest.xml"

if [ ! -f "$MERGED_MANIFEST" ]; then
    echo -e "${RED}ERROR: Merged manifest not found at $MERGED_MANIFEST${NC}"
    exit 1
fi

echo -e "\n${BLUE}=== [4/4] Verifying Permissions Compliance in launcher-ui ===${NC}"

# Permissions that MUST NOT be in launcher-ui
FORBIDDEN_PERMISSIONS=(
    "QUERY_ALL_PACKAGES"
    "CALL_PHONE"
    "READ_CONTACTS"
    "READ_EXTERNAL_STORAGE"
    "READ_MEDIA_IMAGES"
    "READ_MEDIA_VIDEO"
    "READ_MEDIA_AUDIO"
    "READ_MEDIA_VISUAL_USER_SELECTED"
    "PACKAGE_USAGE_STATS"
    "FOREGROUND_SERVICE"
    "FOREGROUND_SERVICE_DATA_SYNC"
    "BIND_ACCESSIBILITY_SERVICE"
    "WRITE_SECURE_SETTINGS"
    "INTERNAL_SYSTEM_WINDOW"
    "STATUS_BAR_SERVICE"
    "CAPTURE_BLACKOUT_CONTENT"
)

HAS_ERROR=0
for PERM in "${FORBIDDEN_PERMISSIONS[@]}"; do
    if grep -q "$PERM" "$MERGED_MANIFEST"; then
        # Exclude TestInformationProvider or benign provider attributes
        if grep -q "<uses-permission.*$PERM" "$MERGED_MANIFEST" || grep -q "service.*$PERM" "$MERGED_MANIFEST"; then
            echo -e "${RED}[VIOLATION] Found forbidden item: $PERM${NC}"
            HAS_ERROR=1
        fi
    fi
done

if [ $HAS_ERROR -eq 0 ]; then
    echo -e "${GREEN}✓ All permission checks passed! No high-risk or restricted permissions found in :launcher-ui.${NC}"
else
    echo -e "${RED}✗ Permission check failed. Please clean up manifests before tagging a release.${NC}"
    exit 1
fi

echo -e "\n${GREEN}=== Pre-release checks completed successfully! ===${NC}"
