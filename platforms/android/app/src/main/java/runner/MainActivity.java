package runner;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.foxdebug.acode.BuildConfig;
import java.lang.ref.WeakReference;
import runner.webView.AppView;
import runner.webView.ChromeClient;
import runner.webView.WebViewClient;

public class MainActivity extends AppCompatActivity {

  private static WeakReference<Context> context;
  private AppView appView;
  private Host host;
  private Bridge bridge;
  private ChromeClient chromeClient;
  private FrameLayout content;
  private boolean hasPaused;
  private final java.util.Set<String> overriddenButtons =
    java.util.concurrent.ConcurrentHashMap.newKeySet();

  public static Context getContext() {
    return context == null ? null : context.get();
  }

  @Override
  public void onCreate(Bundle state) {
    SplashScreen.installSplashScreen(this);
    super.onCreate(state);
    context = new WeakReference<>(this);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    host = new Host(this);
    content = new FrameLayout(this);
    appView = new AppView(this);
    bridge = new Bridge(host, appView);
    appView.initialize(bridge);
    chromeClient = new ChromeClient(this, appView);
    appView.setWebViewClient(new WebViewClient(this, bridge));
    appView.setWebChromeClient(chromeClient);
    appView.setFullscreenController(chromeClient);
    appView.addJavascriptInterface(bridge, "Android");
    appView.setVerticalScrollBarEnabled(false);
    appView.getSettings().setJavaScriptEnabled(true);
    appView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
    appView.getSettings().setSaveFormData(false);
    appView.getSettings().setGeolocationEnabled(true);
    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(
      appView,
      true
    );
    appView.getSettings().setDomStorageEnabled(true);
    appView.getSettings().setDatabaseEnabled(true);
    appView.getSettings().setAllowFileAccess(true);
    appView.getSettings().setAllowContentAccess(true);
    appView.getSettings().setMediaPlaybackRequiresUserGesture(false);
    appView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    appView.setBackgroundColor(0xff313131);
    WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
    content.addView(appView, new FrameLayout.LayoutParams(-1, -1));
    View statusBar = new View(this);
    statusBar.setTag("statusBarView");
    content.addView(statusBar);
    setContentView(content);
    ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
      androidx.core.graphics.Insets bars = insets.getInsets(
        WindowInsetsCompat.Type.systemBars() |
          WindowInsetsCompat.Type.displayCutout()
      );
      int keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
      boolean fullscreen = chromeClient.isFullscreen();
      int top =
        !fullscreen && statusBar.getVisibility() != View.GONE ? bars.top : 0;
      FrameLayout.LayoutParams params =
        (FrameLayout.LayoutParams) appView.getLayoutParams();
      int left = fullscreen ? 0 : bars.left;
      int right = fullscreen ? 0 : bars.right;
      int bottom = fullscreen ? 0 : Math.max(bars.bottom, keyboard);
      if (
        params.leftMargin != left ||
        params.topMargin != top ||
        params.rightMargin != right ||
        params.bottomMargin != bottom
      ) {
        params.setMargins(left, top, right, bottom);
        appView.setLayoutParams(params);
      }
      statusBar.setLayoutParams(
        new FrameLayout.LayoutParams(-1, top, android.view.Gravity.TOP)
      );
      return insets;
    });
    getOnBackPressedDispatcher().addCallback(
      this,
      new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
          dispatchBack();
        }
      }
    );
    bridge.initialize();
    appView.loadUrl("https://localhost/index.html");
  }

  public void overrideButton(String button, boolean enabled) {
    if (button.equals("volumeup") || button.equals("volumedown")) button +=
      "button";
    if (enabled) overriddenButtons.add(button);
    else overriddenButtons.remove(button);
  }

  @Override
  public boolean dispatchKeyEvent(android.view.KeyEvent event) {
    if (appView == null) return super.dispatchKeyEvent(event);
    int code = event.getKeyCode();
    if (
      code == android.view.KeyEvent.KEYCODE_BACK &&
      (chromeClient.isFullscreen() || overriddenButtons.contains("backbutton"))
    ) {
      if (event.getAction() == android.view.KeyEvent.ACTION_UP) dispatchBack();
      return true;
    }
    String name =
      code == android.view.KeyEvent.KEYCODE_MENU
        ? "menubutton"
        : code == android.view.KeyEvent.KEYCODE_VOLUME_UP
          ? "volumeupbutton"
          : code == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
            ? "volumedownbutton"
            : null;
    if (name != null && overriddenButtons.contains(name)) {
      if (
        event.getAction() == android.view.KeyEvent.ACTION_UP
      ) appView.fireDocumentEvent(name);
      return true;
    }
    return super.dispatchKeyEvent(event);
  }

  private void dispatchBack() {
    if (chromeClient.handleBack()) return;
    if (overriddenButtons.contains("backbutton")) appView.fireDocumentEvent(
      "backbutton"
    );
    else if (appView.canGoBack()) appView.goBack();
    else finish();
  }

  public Host getHost() {
    return host;
  }

  public FrameLayout getContentView() {
    return content;
  }

  @Override
  public void onConfigurationChanged(
    android.content.res.Configuration configuration
  ) {
    super.onConfigurationChanged(configuration);
    for (Service service : bridge.getServices())
      service.onConfigurationChanged(configuration);
  }

  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    for (Service service : bridge.getServices()) service.onNewIntent(intent);
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (appView == null) return;
    hasPaused = true;
    appView.fireDocumentEvent("pause");
    for (Service service : bridge.getServices()) service.onPause(true);
    chromeClient.pause();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (appView == null) return;
    appView.onResume();
    appView.resumeTimers();
    chromeClient.resume();
    for (Service service : bridge.getServices()) service.onResume(true);
    if (hasPaused) appView.fireDocumentEvent("resume");
  }

  @Override
  protected void onActivityResult(int code, int result, Intent data) {
    super.onActivityResult(code, result, data);
    if (!host.onActivityResult(code, result, data)) {
      for (Service service : bridge.getServices())
        service.onActivityResult(code, result, data);
    }
  }

  @Override
  public void onRequestPermissionsResult(
    int code,
    String[] permissions,
    int[] grants
  ) {
    super.onRequestPermissionsResult(code, permissions, grants);
    try {
      if (!host.onPermissionsResult(code, permissions, grants)) {
        for (Service service : bridge.getServices())
          service.onRequestPermissionResult(code, permissions, grants);
      }
    } catch (org.json.JSONException exception) {
      android.util.Log.e("Acode", "Permission callback failed", exception);
    }
  }

  @Override
  protected void onDestroy() {
    if (bridge != null) bridge.destroy();
    if (chromeClient != null) chromeClient.reset();
    if (appView != null) {
      content.removeView(appView);
      appView.destroy();
    }
    super.onDestroy();
  }
}
