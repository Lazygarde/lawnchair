# In-Process Minus-One Screen Overlay Guide

This guide explains how host applications can implement and inject a custom minus-one screen (left overlay panel, such as a **Custom TV Remote**, **Smart Home Dashboard**, or **Widgets Feed**) when embedding Lawnchair's `:launcher-ui` library.

---

## 1. Overview

By default, Lawnchair connects to Google Discover / Lawnfeed via an external IPC service. With the **In-Process Overlay** capability in `:launcher-ui`, host applications can supply their own in-app View, XML layout, or Jetpack Compose screen directly inside the Launcher process.

### Key Benefits:
- **Zero IPC Overhead**: Runs in-process with 60/120fps native gesture synchronization.
- **Parallax Physics**: Automatically translates, fades, and snaps smoothly alongside Lawnchair's `Workspace`.
- **Back Gesture Support**: Pressing the system Back button or swiping Back while in the overlay smoothly closes it back to the Home screen.
- **Multiple Integration Styles**: Supports Jetpack Compose, standard XML layouts, or full custom View providers.

---

## 2. Host App Configuration

### Step 1: Add Dependency

In your host app's `build.gradle.kts`:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/Lazygarde/lawnchair")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("com.github.lazygarde.lawnchair:launcher-ui:<version>")
}
```

### Step 2: Application Class

Your host `Application` class must extend `LawnchairApp`:

```kotlin
import app.lawnchair.LawnchairApp

class MyApp : LawnchairApp() {
    override fun onCreate() {
        super.onCreate()
        // Register your overlay here (see below)
    }
}
```

### Step 3: Required Resource Overrides

In your host app's `app/src/main/res/values/config.xml` (or via `resValue` in your Gradle build):

```xml
<resources>
    <!-- Component name of the launcher -->
    <string name="launcher_component" translatable="false">com.example.hostapp/app.lawnchair.LawnchairLauncher</string>

    <!-- Enable in-process custom overlay (default is true) -->
    <bool name="config_enable_custom_overlay">true</bool>
</resources>
```

---

## 3. Integration Approaches

### Approach A: Jetpack Compose (Recommended)

If your host app uses Jetpack Compose, you can define your overlay as a standard `@Composable` screen:

```kotlin
import android.app.Application
import app.lawnchair.LawnchairApp
import app.lawnchair.overlay.LawnchairOverlayRegistry
import androidx.compose.runtime.Composable

class MyApp : LawnchairApp() {
    override fun onCreate() {
        super.onCreate()

        LawnchairOverlayRegistry.setComposeContent {
            TvRemoteScreen(
                onSendCommand = { command ->
                    TvDeviceManager.sendKey(command)
                },
                onSelectDevice = {
                    showDeviceSelectorDialog()
                }
            )
        }
    }
}
```

### Approach B: Standard XML Layout

If your host app uses traditional Android Views and XML layouts:

```kotlin
import app.lawnchair.LawnchairApp
import app.lawnchair.overlay.LawnchairOverlayRegistry

class MyApp : LawnchairApp() {
    override fun onCreate() {
        super.onCreate()

        LawnchairOverlayRegistry.registerView(R.layout.layout_custom_remote_tv) { rootView ->
            // Bind buttons and click listeners
            rootView.findViewById<View>(R.id.btn_dpad_up).setOnClickListener {
                TvDeviceManager.sendKey("UP")
            }
            rootView.findViewById<View>(R.id.btn_dpad_down).setOnClickListener {
                TvDeviceManager.sendKey("DOWN")
            }
            rootView.findViewById<View>(R.id.btn_dpad_ok).setOnClickListener {
                TvDeviceManager.sendKey("SELECT")
            }
            rootView.findViewById<View>(R.id.btn_power).setOnClickListener {
                TvDeviceManager.sendKey("POWER")
            }
        }
    }
}
```

### Approach C: Advanced Provider (`LawnchairOverlayProvider`)

For complex screens requiring scroll tracking or background polling when visible:

```kotlin
import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.lawnchair.overlay.LawnchairOverlayProvider
import app.lawnchair.overlay.LawnchairOverlayRegistry

class TvRemoteOverlayProvider : LawnchairOverlayProvider {

    private var binding: LayoutRemoteTvBinding? = null

    override fun createOverlayView(context: Context, container: ViewGroup): View {
        val view = LayoutRemoteTvBinding.inflate(LayoutInflater.from(context), container, false)
        binding = view
        setupControls(view)
        return view.root
    }

    override fun onAttachedToLauncher(activity: Activity) {
        // Overlay view attached to the Launcher activity
    }

    override fun onOverlayScroll(progress: Float) {
        // progress: 0.0f (closed/workspace) -> 1.0f (fully opened)
        // Can be used to apply custom animations, fade-ins, or scale effects
    }

    override fun onOverlayOpened() {
        // Overlay is fully open: Start Wi-Fi polling or device status refresh
        TvDeviceManager.startStatusPolling()
    }

    override fun onOverlayClosed() {
        // Overlay is closed: Stop polling to save battery
        TvDeviceManager.stopStatusPolling()
    }

    override fun onDetachedFromLauncher() {
        binding = null
    }
}

// In your Application.onCreate():
LawnchairOverlayRegistry.registerProvider(TvRemoteOverlayProvider())
```

---

## 4. Gestures & Touch Conflict Handling

When building interactive controls like **TV Touchpads** or horizontal sliders inside your overlay:
- If a child view handles horizontal drag gestures (e.g., a touchpad surface), call `parent.requestDisallowInterceptTouchEvent(true)` on `ACTION_DOWN` so the parent `Workspace` does not intercept the gesture.
- When the overlay is open (`progress > 0.05f`), pressing the Android Back button automatically animates the overlay closed, returning the user to the Home workspace.

---

## 5. Toggling the Feature Dynamically

You can enable or disable the custom overlay at runtime by registering or clearing the provider:

```kotlin
// Disable custom overlay (falls back to Google Feed if configured):
LawnchairOverlayRegistry.clear()

// Re-enable custom overlay:
LawnchairOverlayRegistry.registerProvider(...)
```

Or disable it statically in `res/values/config.xml`:
```xml
<bool name="config_enable_custom_overlay">false</bool>
```
