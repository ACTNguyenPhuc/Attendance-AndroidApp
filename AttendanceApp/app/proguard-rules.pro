# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools proguard/proguard-android.txt

# Keep Firebase models
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.example.attendanceapplication.models.** { *; }
-keep class com.google.firebase.** { *; }

# ZXing
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.** { *; }

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }

# Lottie
-keep class com.airbnb.lottie.** { *; }
