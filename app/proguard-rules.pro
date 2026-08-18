# Keep kotlinx.serialization rules
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.yanparker.modelforum.**$$serializer { *; }
-keepclassmembers class com.yanparker.modelforum.** {
    *** Companion;
}
-keepclasseswithmembers class com.yanparker.modelforum.** {
    kotlinx.serialization.KSerializer serializer(...);
}