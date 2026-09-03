# Keep kotlinx.serialization generated serializers for the seed/export models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.pranav.speed400garage.** {
    *** Companion;
}
-keepclasseswithmembers class dev.pranav.speed400garage.** {
    kotlinx.serialization.KSerializer serializer(...);
}
