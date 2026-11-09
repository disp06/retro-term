# Custom views are referenced from XML
-keep class com.disp06.retroterm.ui.** extends android.view.View { <init>(...); }
# Wearable shared library stubs used by AmbientModeSupport
-dontwarn com.google.android.wearable.**
-keep class com.disp06.retroterm.face.** { *; }
-dontwarn android.support.wearable.**
