# Hermes Companion ProGuard rules
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.atlas.hermescompanion.**$$serializer { *; }
-keepclassmembers class com.atlas.hermescompanion.** { *** Companion; }
-keepclasseswithmembers class com.atlas.hermescompanion.** { kotlinx.serialization.KSerializer serializer(...); }
