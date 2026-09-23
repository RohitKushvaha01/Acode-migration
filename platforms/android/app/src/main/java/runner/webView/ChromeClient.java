package runner.webView;

import android.net.Uri;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;
import androidx.core.view.ViewCompat;
import runner.MainActivity;

public final class ChromeClient extends WebChromeClient {

  private final Dialogs dialogs;
  private final FileChooser fileChooser;
  private android.webkit.PermissionRequest pendingPermission;
  private final androidx.activity.result.ActivityResultLauncher<
    String[]
  > permissionLauncher;
  private final MainActivity activity;
  private final AppView webView;
  private final ImmersiveFullscreen immersive;
  private CustomViewCallback fullscreenCallback;
  private FrameLayout fullscreenView;
  private boolean backHandler;

  public ChromeClient(MainActivity activity, AppView webView) {
    this.activity = activity;
    this.webView = webView;
    dialogs = new Dialogs(activity);
    fileChooser = new FileChooser(activity.getHost());
    permissionLauncher = activity.registerForActivityResult(
      new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
      grants -> {
        android.webkit.PermissionRequest request = pendingPermission;
        pendingPermission = null;
        if (request == null) return;
        if (!grants.isEmpty() && !grants.containsValue(false)) request.grant(
          request.getResources()
        );
        else request.deny();
      }
    );
    immersive = new ImmersiveFullscreen(
      activity,
      true,
      () -> backHandler = false
    );
  }

  public boolean isFullscreen() {
    return fullscreenView != null;
  }

  public void pause() {
    immersive.pause();
  }

  public void resume() {
    immersive.resume();
  }

  public void setBackHandler(boolean enabled) {
    if (enabled && !isFullscreen()) throw new IllegalStateException(
      "Fullscreen is not active."
    );
    backHandler = enabled;
  }

  public void setOrientation(String orientation) {
    if (orientation == null) immersive.unlockOrientation();
    else immersive.lockOrientation(orientation);
  }

  public boolean handleBack() {
    if (!isFullscreen()) return false;
    if (backHandler) webView.fireDocumentEvent("fullscreenbackbutton");
    else onHideCustomView();
    return true;
  }

  @Override
  public void onShowCustomView(View view, CustomViewCallback callback) {
    if (isFullscreen()) {
      callback.onCustomViewHidden();
      return;
    }
    fullscreenCallback = callback;
    fullscreenView = new FrameLayout(activity);
    fullscreenView.setBackgroundColor(android.graphics.Color.BLACK);
    fullscreenView.addView(view, new FrameLayout.LayoutParams(-1, -1));
    activity
      .getContentView()
      .addView(fullscreenView, new FrameLayout.LayoutParams(-1, -1));
    webView.setVisibility(View.INVISIBLE);
    immersive.enter(fullscreenView);
    ViewCompat.requestApplyInsets(activity.getContentView());
  }

  @Override
  public void onHideCustomView() {
    if (!isFullscreen()) return;
    immersive.exit();
    activity.getContentView().removeView(fullscreenView);
    fullscreenView = null;
    webView.setVisibility(View.VISIBLE);
    CustomViewCallback callback = fullscreenCallback;
    fullscreenCallback = null;
    callback.onCustomViewHidden();
    ViewCompat.requestApplyInsets(activity.getContentView());
  }

  @Override
  public boolean onShowFileChooser(
    WebView view,
    ValueCallback<Uri[]> callback,
    FileChooserParams params
  ) {
    return fileChooser.show(view, callback, params);
  }

  @Override
  public boolean onJsAlert(
    WebView view,
    String url,
    String message,
    android.webkit.JsResult result
  ) {
    dialogs.showAlert(message, (success, value) -> {
      if (success) result.confirm();
      else result.cancel();
    });
    return true;
  }

  @Override
  public boolean onJsConfirm(
    WebView view,
    String url,
    String message,
    android.webkit.JsResult result
  ) {
    dialogs.showConfirm(message, (success, value) -> {
      if (success) result.confirm();
      else result.cancel();
    });
    return true;
  }

  @Override
  public boolean onJsPrompt(
    WebView view,
    String url,
    String message,
    String value,
    android.webkit.JsPromptResult result
  ) {
    dialogs.showPrompt(message, value, (success, input) -> {
      if (success) result.confirm(input);
      else result.cancel();
    });
    return true;
  }

  @Override
  public void onGeolocationPermissionsShowPrompt(
    String origin,
    android.webkit.GeolocationPermissions.Callback callback
  ) {
    callback.invoke(origin, true, false);
  }

  @Override
  public void onPermissionRequest(android.webkit.PermissionRequest request) {
    java.util.List<String> permissions = new java.util.ArrayList<>();
    for (String resource : request.getResources()) {
      if (
        resource.equals(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE)
      ) permissions.add(android.Manifest.permission.CAMERA);
      if (
        resource.equals(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE)
      ) {
        permissions.add(android.Manifest.permission.MODIFY_AUDIO_SETTINGS);
        permissions.add(android.Manifest.permission.RECORD_AUDIO);
      }
    }
    if (permissions.isEmpty()) request.grant(request.getResources());
    else {
      if (pendingPermission != null) pendingPermission.deny();
      pendingPermission = request;
      permissionLauncher.launch(permissions.toArray(new String[0]));
    }
  }

  @Override
  public void onPermissionRequestCanceled(
    android.webkit.PermissionRequest request
  ) {
    if (pendingPermission == request) pendingPermission = null;
  }

  public void reset() {
    onHideCustomView();
    dialogs.destroyLastDialog();
  }

  @Override
  public boolean onConsoleMessage(ConsoleMessage message) {
    android.util.Log.d(
      "AcodeWebView",
      message.message() +
        " at " +
        message.sourceId() +
        ":" +
        message.lineNumber()
    );
    return true;
  }
}
