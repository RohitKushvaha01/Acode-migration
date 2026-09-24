package runner.webView;

import android.content.Intent;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import androidx.webkit.WebViewAssetLoader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Collections;
import runner.Bridge;
import runner.MainActivity;
import runner.Service;
import runner.ServicePathHandler;

public final class WebViewClient extends android.webkit.WebViewClient {

  private final MainActivity activity;
  private final Bridge bridge;

  public WebViewClient(MainActivity activity, Bridge bridge) {
    this.activity = activity;
    this.bridge = bridge;
  }

  @Override
  public void onReceivedSslError(
    WebView view,
    android.webkit.SslErrorHandler handler,
    android.net.http.SslError error
  ) {
    if (com.foxdebug.acode.BuildConfig.DEBUG) handler.proceed();
    else handler.cancel();
  }

  @Override
  public void onPageStarted(
    WebView view,
    String url,
    android.graphics.Bitmap icon
  ) {
    ((AppView) view).resetChrome();
    bridge.reset();
    super.onPageStarted(view, url, icon);
  }

  @Override
  public boolean shouldOverrideUrlLoading(
    WebView view,
    WebResourceRequest request
  ) {
    Uri uri = request.getUrl();
    if (
      "https".equals(uri.getScheme()) || "http".equals(uri.getScheme())
    ) return false;
    if (!request.isForMainFrame()) return false;
    try {
      activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
    } catch (android.content.ActivityNotFoundException ignored) {}
    return true;
  }

  @Override
  public WebResourceResponse shouldInterceptRequest(
    WebView view,
    WebResourceRequest request
  ) {
    Uri uri = request.getUrl();
    if (
      !"https".equals(uri.getScheme()) || !"localhost".equals(uri.getHost())
    ) return null;
    String path = uri.getPath();
    if (path == null || path.equals("/")) path = "/index.html";
    Service file = bridge.getService("File");
    ServicePathHandler handler = file.getPathHandler();
    WebResourceResponse response = handler
      .getPathHandler()
      .handle(path.substring(1));
    if (response != null) return response;
    if (
      java.util.Arrays.asList(path.split("/")).contains("..") ||
      path.indexOf('\0') != -1
    ) return missing();
    try {
      InputStream stream = activity.getAssets().open("bundle" + path);
      String mime = path.endsWith(".js")
        ? "application/javascript"
        : path.endsWith(".wasm")
          ? "application/wasm"
          : URLConnection.guessContentTypeFromName(path);
      if (mime == null) mime = "application/octet-stream";
      return new WebResourceResponse(
        mime,
        mime.startsWith("text/") || mime.equals("application/javascript")
          ? "UTF-8"
          : null,
        stream
      );
    } catch (java.io.IOException exception) {
      return missing();
    }
  }

  private static WebResourceResponse missing() {
    return new WebResourceResponse(
      "text/plain",
      "UTF-8",
      404,
      "Not Found",
      Collections.emptyMap(),
      new ByteArrayInputStream(new byte[0])
    );
  }
}
