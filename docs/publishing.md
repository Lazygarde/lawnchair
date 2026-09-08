# Publishing the library modules

Lawnchair's reusable Android library modules are published as AARs to
[GitHub Packages](https://github.com/Lazygarde/lawnchair/packages) so other projects can
depend on them without vendoring this repository.

The launcher itself (the root `:` project) is an application module and is **not** published.

## Coordinates

All artifacts share the group `com.github.lazygarde.lawnchair`, configured in
`gradle.properties`:

```properties
lawnchair.publish.group=com.github.lazygarde.lawnchair
lawnchair.publish.version=16.0.0-local
lawnchair.publish.repository=Lazygarde/lawnchair
```

| Gradle project | artifactId |
| --- | --- |
| `:iconloaderlib` | `iconloaderlib` |
| `:searchuilib` | `searchuilib` |
| `:animationlib` | `animationlib` |
| `:msdllib` | `msdllib` |
| `:contextualeducationlib` | `contextualeducationlib` |
| `:viewcapturelib` | `viewcapturelib` |
| `:displaylib` | `displaylib` |
| `:mechanics` | `mechanics` |
| `:shared` | `systemui-shared` |
| `:plugin` | `systemui-plugin` |
| `:plugincore` | `systemui-plugin-core` |
| `:common` | `systemui-common` |
| `:log` | `systemui-log` |
| `:animation` | `systemui-animation` |
| `:unfold` | `systemui-unfold` |
| `:utils` | `systemui-utils` |
| `:compatLib` | `compatlib` |
| `:compatLib:compatLibVQ` … `:compatLibVBaklava` | `compatlib-vq` … `compatlib-vbaklava` |
| `:hidden-api` | `hidden-api` |
| `:androidx-lib` | `androidx-lib` |
| `:flags` | `flags` |
| `:wmshell` | `wmshell` |
| `:dagger` | `dagger` |
| `:concurrent` | `concurrent` |
| `:modules:widgetpicker` | `widgetpicker` |
| `:launcher-ui` | `launcher-ui` |

The `systemui-` prefix exists because the upstream module names (`log`, `common`,
`utils`, …) are too generic to publish unqualified.

The mapping lives in [`gradle/publishing.gradle`](../gradle/publishing.gradle). A
`com.android.library` module that is absent from that map is simply not published, so
adding a new module to the published set means adding one line there.

## Publishing

### From CI (the normal path)

The `Publish packages` workflow (`.github/workflows/publish_packages.yml`) publishes
every module. It runs on:

- a pushed tag named `lib-v<version>`, e.g. `lib-v16.0.0-alpha01`, which publishes
  version `16.0.0-alpha01`;
- a manual `workflow_dispatch`, which takes an optional version input and otherwise
  publishes `16.0.0-dev.<run number>`.

```bash
git tag lib-v16.0.0-alpha01
git push origin lib-v16.0.0-alpha01
```

GitHub Packages rejects re-publishing an existing non-snapshot version, which is why
the dev fallback is keyed by the run number rather than the commit.

### From a local machine

Publishing to GitHub Packages needs a personal access token with the `write:packages`
scope. Put it in `~/.gradle/gradle.properties` so it never lands in the repository:

```properties
gpr.user=your-github-username
gpr.key=ghp_xxxxxxxxxxxxxxxxxxxx
```

The build also accepts the `GITHUB_ACTOR` / `GITHUB_TOKEN` environment variables, which
is what CI uses.

```bash
# Everything, at the version from gradle.properties
./gradlew publishAllPublicationsToGitHubPackagesRepository

# A specific version
PUBLISH_VERSION=16.0.0-alpha01 ./gradlew publishAllPublicationsToGitHubPackagesRepository

# A single module
./gradlew :iconloaderlib:publishAllPublicationsToGitHubPackagesRepository
```

To try the artifacts without uploading anything, publish to the local Maven cache
instead — no token required:

```bash
./gradlew publishToMavenLocal
```

## Consuming the artifacts

GitHub Packages requires authentication even for public repositories, so the consuming
project needs a token with the `read:packages` scope.

`settings.gradle.kts` in the consuming project:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/Lazygarde/lawnchair")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

`build.gradle.kts` in the consuming module:

```kotlin
dependencies {
    implementation("com.github.lazygarde.lawnchair:iconloaderlib:16.0.0-alpha01")
    implementation("com.github.lazygarde.lawnchair:animationlib:16.0.0-alpha01")
}
```

Inter-module dependencies resolve automatically: `systemui-common` pulls in
`systemui-utils`, `compatlib-vbaklava` pulls in the whole `compatlib-v*` chain, and so on.

### Hosting the launcher

`launcher-ui` ships the home screen, so an app that depends on it becomes a launcher. Four things are on the host:

**1. The Application class.** `LawnchairApp` builds the Dagger graph the launcher runs on, and its superclass captures the host's package name before any content provider starts. Extend it, do not replace it:

```kotlin
class MyApp : LawnchairApp()
```

```xml
<application android:name=".MyApp" ... />
```

**2. The `<application>` identity attributes.** The library declares none of `android:icon`, `label`, `theme`, `backupAgent` or `fullBackupContent`, so the host has to. It does need `android:largeHeap="@bool/config_largeHeap"` and `android:hardwareAccelerated="true"`.

**3. Three resources the library reads but cannot know.** `resValue` is the easiest way:

```kotlin
resValue("string", "derived_app_name", "My Launcher")
resValue("string", "launcher_component", "$applicationId/app.lawnchair.LawnchairLauncher")
resValue("string", "config_release_channel", "play")
```

`launcher_component` keeps the launcher out of its own app drawer; `config_release_channel` set to `play` turns off the parts of the settings UI a Play Store build must not ship.

**4. JitPack, and core library desugaring.** Some of Lawnchair's dependencies come from `https://jitpack.io`, so the host repository list needs it. The library is built with desugaring, so the host needs `isCoreLibraryDesugaringEnabled = true` and `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:...")`.

`app.lawnchair.LawnchairLauncher` is declared in the library manifest with `CATEGORY_HOME` and `CATEGORY_LAUNCHER`. A host that already has its own entry activity will want to drop `CATEGORY_LAUNCHER` from it, which means redeclaring the activity with `tools:node="replace"`.

If you do redeclare it, keep `android:theme="@style/AppTheme"`. `tools:node="replace"` swaps the whole element rather than merging into it, and Launcher3's views read attributes that only `AppTheme` defines: under the host's own theme the launcher dies inflating `DeleteDropTarget` with `UnsupportedOperationException: Failed to resolve attribute`.

### Caveats

- **Hidden APIs.** Several modules compile against the AOSP stubs in `prebuilts/libs`
  (`framework-16.jar`, `SystemUI-core-16.jar`, `WindowManager-Shell-16.jar`) as
  `compileOnly` dependencies. Those are deliberately absent from the published POMs. If
  your code touches the hidden APIs these modules expose, copy the relevant jars into
  your project and add them as `compileOnly files(...)` yourself. At runtime the calls
  still need a device where those APIs are reachable.
- **minSdk 26** and `compileSdk 37` come from the root build script and apply to every
  published module.
- Sources jars are published; javadoc jars are not.
