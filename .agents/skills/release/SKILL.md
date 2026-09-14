---
name: release
description: >-
  Automates and guides the release and publishing workflow for Lawnchair and its reusable Android library
  modules (:launcher-ui, :iconloaderlib, :wmshell, etc.). Handles pre-release validation, manifest permission
  checks, semantic versioning, git tagging (lib-v<version>), pushing, and GitHub Packages / mavenLocal publishing.
  Use when the user asks to release, publish, tag, or bump version.
  Also activates for: release, phát hành, publish lib, publish packages, tag release, tạo release, bump version
---

# Lawnchair Library Release & Publishing Guide

This skill provides the standard operating procedure (SOP) for validating, tagging, and publishing Lawnchair's reusable Android library modules (particularly `:launcher-ui`) to **GitHub Packages** or **Maven Local**.

---

## 1. Release Architecture & Coordinates

Lawnchair publishes library modules under the Maven group `com.github.lazygarde.lawnchair`.
Coordinates are configured in `gradle.properties`:

```properties
lawnchair.publish.group=com.github.lazygarde.lawnchair
lawnchair.publish.version=16.0.0-local
lawnchair.publish.repository=Lazygarde/lawnchair
```

Published modules include:
- `:launcher-ui` (`launcher-ui` - main embeddable launcher screen)
- `:iconloaderlib`, `:animationlib`, `:shared`, `:wmshell`, `:compatLib`, etc. (see [docs/publishing.md](file:///Users/lazygarde/AndroidStudioProjects/lawnchair/docs/publishing.md))

---

## 2. Step-by-Step Release Workflow

### Step 1: Pre-Release Verification

Always verify repository state and ensure compliance with Google Play's **Principle of Least Privilege**:

1. Run the automated pre-release verification script:
   ```bash
   .agents/skills/release/scripts/check-release.sh
   ```
   This script verifies:
   - Git working tree status.
   - Merged manifest of `:launcher-ui` has **zero** dangerous/system permissions (no `QUERY_ALL_PACKAGES`, `CALL_PHONE`, `READ_CONTACTS`, `BIND_ACCESSIBILITY_SERVICE`, `FOREGROUND_SERVICE`, `CAPTURE_BLACKOUT_CONTENT`, etc.).

2. Run a full release build check for the library:
   ```bash
   ./gradlew :launcher-ui:assembleLawnWithQuickstepRelease
   ```
   Ensure the task outputs `BUILD SUCCESSFUL`.

---

### Step 2: Determine Next Release Version

List recent tags to find the current version:
```bash
git tag --list "lib-v*" --sort=-v:refname | head -n 5
```

Version naming format:
- Alpha releases: `lib-v16.0.0-alpha01`, `lib-v16.0.0-alpha02`, ...
- Beta releases: `lib-v16.0.0-beta01`, ...
- Release candidates: `lib-v16.0.0-rc01`, ...
- Stable: `lib-v16.0.0`

Confirm the target version with the user if not specified.

---

### Step 3: Commit Pending Changes (if any)

If there are uncommitted changes intended for the release:
```bash
# Stage only the intended files (avoid staging unrelated dirty submodules)
git add <files>

# Commit using Conventional Commits
git commit -m "chore(release): prepare for lib-v<version>"
```

---

### Step 4: Create Annotated Git Tag

Tags **MUST** strictly follow the pattern `lib-v<version>` because GitHub Actions `.github/workflows/publish_packages.yml` triggers exclusively on `lib-v*`:

```bash
git tag -a lib-v<version> -m "Release <version>"
```

*Example:*
```bash
git tag -a lib-v16.0.0-alpha05 -m "Release 16.0.0-alpha05"
```

---

### Step 5: Push Branch and Tag to GitHub

Push both the current branch (e.g. `16-dev`) and the tag:
```bash
git push origin 16-dev lib-v<version>
```

---

### Step 6: Monitor CI / GitHub Actions

Once pushed:
1. GitHub Actions will run the **Publish packages** workflow (`.github/workflows/publish_packages.yml`).
2. The workflow compiles all library modules and publishes them to:
   `https://github.com/Lazygarde/lawnchair/packages`
3. Check the workflow run status via GitHub CLI if available:
   ```bash
   gh run list --workflow=publish_packages.yml -L 1
   ```

---

## 3. Local Testing Alternative (publishToMavenLocal)

Before pushing to GitHub, you can publish to the local Maven repository (`~/.m2/repository/`) to test locally in consumer apps without creating git tags or requiring GitHub tokens:

```bash
# Publish all modules locally
./gradlew publishToMavenLocal

# Or publish only launcher-ui
./gradlew :launcher-ui:publishToMavenLocal
```

In the consuming project (`settings.gradle.kts`):
```kotlin
dependencyResolutionManagement {
    repositories {
        mavenLocal() // Place before remote repos for testing local builds
        google()
        mavenCentral()
    }
}
```

---

## 4. Consuming the New Version in Host Apps (e.g. holysheet)

In the host app's `build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.github.lazygarde.lawnchair:launcher-ui:<version>")
}
```

### Essential Host App Configuration Reminder:
- **`QUERY_ALL_PACKAGES`**: If the host acts as a primary launcher, declare in `app/src/main/AndroidManifest.xml`:
  ```xml
  <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
  ```
- **Application Class**: Host `Application` must extend `LawnchairApp`:
  ```kotlin
  class MyApp : LawnchairApp()
  ```
- **Resources required**:
  ```kotlin
  resValue("string", "derived_app_name", "My App")
  resValue("string", "launcher_component", "$applicationId/app.lawnchair.LawnchairLauncher")
  resValue("string", "config_release_channel", "play")
  ```
