# Keep Acode classes
-keep class runner.** { *; }

# Acode discovers plugins by class name from services.json and instantiates them
# reflectively, and plugins can dispatch actions by method name through
# reflection too (e.g. Ftp.execute() uses
# getClass().getDeclaredMethod(action, JSONArray, Callback)).
# Without keeping the members, R8 strips methods such as connect()/listDirectory()
# and those plugin calls fail at runtime with NoSuchMethodException.
-keep public class * extends runner.Service { *; }

# WebView JS bridge methods are invoked by name from JavaScript.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# BuildInfo resolves the app BuildConfig and its fields reflectively.
-keep class **.BuildConfig { *; }

# maverick-synergy (SSH/SFTP) references java.lang.management from
# Utils.generateThreadDump(), which does not exist on Android. That method is
# never reached on Android, so ignore the missing Java SE classes.
-dontwarn java.lang.management.**

# Keep Javascript Interface attributes
-keepattributes *Annotation*,EnclosingMethod,InnerClasses,Signature
