-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.news.articles.**$$serializer { *; }
-keepclassmembers class com.news.articles.** {
    *** Companion;
}
-keepclasseswithmembers class com.news.articles.** {
    kotlinx.serialization.KSerializer serializer(...);
}
