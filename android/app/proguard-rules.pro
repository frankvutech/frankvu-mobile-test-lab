# kotlinx.serialization: keep generated serializers for DTOs used by Retrofit.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.mobiletestplayground.core.data.network.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
# The WebView JavaScript bridge is called by name from JavaScript.
-keepclassmembers class com.mobiletestplayground.app.scenarios.webview.WebBridge {
    @android.webkit.JavascriptInterface <methods>;
}
