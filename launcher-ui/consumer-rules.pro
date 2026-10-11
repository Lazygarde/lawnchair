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

# Allow safe references to newer or internal window APIs
-dontwarn android.window.**
