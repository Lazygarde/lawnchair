# Lawnchair UI Consumer ProGuard Rules

# Keep Predictive Back callbacks invoked by Android framework
-keep class * implements android.window.OnBackInvokedCallback {
    public void onBackInvoked();
}
-keep class * implements android.window.OnBackAnimationCallback {
    public void onBackInvoked();
    public void onBackStarted(android.window.BackEvent);
    public void onBackProgressed(android.window.BackEvent);
    public void onBackCancelled();
}
-keep interface android.window.OnBackInvokedCallback { *; }
-keep interface android.window.OnBackAnimationCallback { *; }

# Keep hidden-API / AIDL stub names shipped by launcher-ui, compatlib, and systemui-shared so
# runtime boot-classpath classes and framework IPC casts match by name.
-keepnames class android.os.BinderUtils** { *; }
-keepnames class android.os.IRemoteCallback** { *; }
-keepnames class android.view.ISystemGestureExclusionListener** { *; }
-keepnames class android.view.InsetsFrameProvider** { *; }
-keepnames class android.view.InsetsSource** { *; }
-keepnames class android.window.** { *; }

# Allow safe references to newer, OEM, or internal platform APIs
-dontwarn android.app.IApplicationThread**
-dontwarn android.app.WindowConfiguration
-dontwarn android.os.BinderProxy
-dontwarn android.view.SemBlurInfo**
-dontwarn android.window.**
-dontwarn com.android.internal.util.**
-dontwarn com.skydoves.balloon.**
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**

# Public host API for drawer slots
-keep class app.lawnchair.drawerslots.** { *; }

# Launcher3 classes loaded dynamically by name from XML config strings (ResourceBasedOverride)
-keep class ** implements com.android.launcher3.util.ResourceBasedOverride {
    <init>(...);
}

# FontCache deserializes saved font entries via Class.forName(className).getMethod("fromJson", ...)
-keep class * extends app.lawnchair.font.FontCache$Font {
    public static *** fromJson(android.content.Context, org.json.JSONObject);
}

# ObjectAnimator properties referenced by string name (XML animators / non-View drawables)
-keep class com.android.launcher3.allapps.DiscoveryBounce$VerticalProgressWrapper {
    public void setProgress(float);
    public float getProgress();
}
-keepclassmembers class com.android.systemui.shared.navigationbar.KeyButtonRipple {
    public void setGlowScale(float);
    public float getGlowScale();
    public void setGlowAlpha(float);
    public float getGlowAlpha();
}

# Protobuf lite messages (LawnchairProto, LauncherAtom, viewcapturelib) read fields via reflection
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# SystemUI flags & plugin annotations
-keep class * implements com.android.systemui.flags.ParcelableFlag
-keep interface com.android.systemui.plugins.annotations.** { *; }
-keep,allowshrinking,allowoptimization,allowobfuscation,allowaccessmodification @com.android.systemui.plugins.annotations.** class * {
    void <init>();
}
