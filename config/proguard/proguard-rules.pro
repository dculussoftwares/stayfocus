# Shared R8 rules for :app and :kids. Library AARs ship their own consumer rules (Room, Hilt, Firebase,
# ML Kit, Compose, kotlinx.serialization); only what they do not cover is listed here.

# kotlinx.serialization: keep generated serializers of @Serializable classes (official recommended rules).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-if @kotlinx.serialization.Serializable class ** { static **$* *; }
-keepclassmembers class <2>$<3> { kotlinx.serialization.KSerializer serializer(...); }
-if @kotlinx.serialization.Serializable class ** { public static ** INSTANCE; }
-keepclassmembers class <1> { public static <1> INSTANCE; kotlinx.serialization.KSerializer serializer(...); }

# Room: generated *_Impl classes are instantiated reflectively by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Firebase (Firestore POJO mapping): keep annotated members and generic signatures.
-keepclassmembers class com.dculus.stayfocused.** { @com.google.firebase.firestore.PropertyName <fields>; }
-keepattributes Signature

# ML Kit / Play services ship consumer rules; silence optional-dependency warnings only.
-dontwarn com.google.mlkit.**

# Readable crash stack traces.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
