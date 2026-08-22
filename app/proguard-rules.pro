# kotlinx.serialization keeps generated serializers reachable.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.hillbros.videoeditor.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.hillbros.videoeditor.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Media3 resolves some codecs and effects reflectively.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
