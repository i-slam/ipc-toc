# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# This is a diagnostic tool: readable stack traces are worth more than the few KB they cost.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Components the platform instantiates by name. The manifest keeps these already, but the
# :overlay process service is started across process boundaries, so pin them explicitly.
-keep class com.example.MainActivity { *; }
-keep class com.example.service.** { *; }
-keep class com.example.receiver.** { *; }

# CallStateMonitor subclasses TelephonyCallback and PhoneStateListener, both of which the
# telephony stack invokes reflectively through the framework.
-keep class com.example.telephony.CallStateMonitor { *; }
-keep class * extends android.telephony.TelephonyCallback { *; }
-keep class * extends android.telephony.PhoneStateListener { *; }
-keep class * extends android.database.ContentObserver { *; }

# DeviceProfile reads hidden system properties reflectively.
-keep class android.os.SystemProperties { *; }

# Optional compile-time-only annotations pulled in by OkHttp / Retrofit / Moshi.
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
