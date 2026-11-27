-adaptclassstrings
-allowaccessmodification
-repackageclasses 'x'
-renamesourcefileattribute x

-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

-keep class org.lsposed.lsparanoid.** { *; }
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

-keep class * extends android.content.ContentProvider
-keepclassmembers class * extends android.content.ContentProvider {
    <init>(...);
}

-keepclasseswithmembernames class * {
    native <methods>;
}

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn edu.umd.cs.findbugs.annotations.**

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
