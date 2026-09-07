-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.bell.launcher.** {
    *** Companion;
}
-keepclasseswithmembers class com.bell.launcher.** {
    kotlinx.serialization.KSerializer serializer(...);
}
