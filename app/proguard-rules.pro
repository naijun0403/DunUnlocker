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
-dontwarn android.app.IActivityManager
-dontwarn android.app.IActivityManager$Stub
-dontwarn android.app.IInstrumentationWatcher
-dontwarn android.app.IInstrumentationWatcher$Stub
-dontwarn android.app.IUiAutomationConnection
-dontwarn android.app.UiAutomationConnection
-dontwarn android.os.TelephonyServiceManager
-dontwarn android.os.TelephonyServiceManager$ServiceRegisterer
-dontwarn android.telephony.TelephonyFrameworkInitializer
-dontwarn com.android.internal.telephony.**

-keep class dev.naijun.dununlocker.BrokerInstrumentation { *; }

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
