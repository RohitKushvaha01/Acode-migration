package runner.webView;

import android.content.Context;
import android.graphics.Rect;
import android.text.InputType;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.WebView;
import runner.Bridge;
import runner.ResourceApi;

public final class AppView extends WebView {

  private int inputType = -1;
  private boolean nativeContextMenuDisabled;
  private Bridge bridge;
  private ResourceApi resourceApi;
  private ChromeClient fullscreen;

  public AppView(Context context) {
    super(context);
  }

  public void initialize(Bridge bridge) {
    this.bridge = bridge;
    resourceApi = new ResourceApi(getContext(), bridge);
  }

  public Bridge getBridge() {
    return bridge;
  }

  public ResourceApi getResourceApi() {
    return resourceApi;
  }

  public AppView getView() {
    return this;
  }

  public AppView getEngine() {
    return this;
  }

  public void setInputType(int type) {
    inputType = type;
  }

  public void setNativeContextMenuDisabled(boolean disabled) {
    nativeContextMenuDisabled = disabled;
  }

  public void setFullscreenController(ChromeClient fullscreen) {
    this.fullscreen = fullscreen;
  }

  public void resetChrome() {
    fullscreen.reset();
  }

  public void setFullscreenBackHandler(boolean enabled) {
    fullscreen.setBackHandler(enabled);
  }

  public void setFullscreenOrientation(String orientation) {
    fullscreen.setOrientation(orientation);
  }

  public void fireDocumentEvent(String event) {
    evaluateJavascript(
      "window.Bridge && Bridge.fireDocumentEvent(" +
        org.json.JSONObject.quote(event) +
        ");",
      null
    );
  }

  @Override
  public InputConnection onCreateInputConnection(EditorInfo attrs) {
    InputConnection connection = super.onCreateInputConnection(attrs);
    if (inputType == 0) attrs.inputType |=
      InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
    else if (inputType == 1) attrs.inputType =
      InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
      InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
    return connection;
  }

  @Override
  public ActionMode startActionMode(ActionMode.Callback callback) {
    return suppress(super.startActionMode(wrap(callback)));
  }

  @Override
  public ActionMode startActionMode(ActionMode.Callback callback, int type) {
    return suppress(super.startActionMode(wrap(callback), type));
  }

  @Override
  public ActionMode startActionModeForChild(
    View child,
    ActionMode.Callback callback
  ) {
    return suppress(super.startActionModeForChild(child, wrap(callback)));
  }

  @Override
  public ActionMode startActionModeForChild(
    View child,
    ActionMode.Callback callback,
    int type
  ) {
    return suppress(super.startActionModeForChild(child, wrap(callback), type));
  }

  private ActionMode.Callback wrap(ActionMode.Callback callback) {
    if (!nativeContextMenuDisabled || callback == null) return callback;
    return new ActionMode.Callback2() {
      @Override
      public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        boolean created = callback.onCreateActionMode(mode, menu);
        if (created) suppressUi(mode, menu);
        return created;
      }

      @Override
      public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        boolean prepared = callback.onPrepareActionMode(mode, menu);
        suppressUi(mode, menu);
        return prepared;
      }

      @Override
      public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        return callback.onActionItemClicked(mode, item);
      }

      @Override
      public void onDestroyActionMode(ActionMode mode) {
        callback.onDestroyActionMode(mode);
      }

      @Override
      public void onGetContentRect(ActionMode mode, View view, Rect rect) {
        if (callback instanceof ActionMode.Callback2) (
          (ActionMode.Callback2) callback
        ).onGetContentRect(mode, view, rect);
        else super.onGetContentRect(mode, view, rect);
      }
    };
  }

  private ActionMode suppress(ActionMode mode) {
    if (nativeContextMenuDisabled && mode != null) suppressUi(
      mode,
      mode.getMenu()
    );
    return mode;
  }

  private void suppressUi(ActionMode mode, Menu menu) {
    if (mode == null || !nativeContextMenuDisabled || menu == null) return;
    menu.clear();
    mode.setTitle(null);
    mode.setSubtitle(null);
    post(() -> {
      if (!nativeContextMenuDisabled) return;
      try {
        mode.hide(0);
      } catch (Throwable ignored) {}
    });
  }
}
