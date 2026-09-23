package runner;

import org.json.JSONArray;
import org.json.JSONException;

public final class App extends Service {

  @Override
  public boolean execute(String action, JSONArray args, Callback callback)
    throws JSONException {
    switch (action) {
      case "exitApp":
        host.getActivity().runOnUiThread(() -> host.getActivity().finish());
        break;
      case "overrideButton":
        host
          .getActivity()
          .overrideButton(args.getString(0), args.getBoolean(1));
        break;
      case "clearCache":
        webView.post(() -> webView.clearCache(true));
        break;
      case "clearHistory":
        webView.post(() -> webView.clearHistory());
        break;
      case "backHistory":
        webView.post(() -> webView.goBack());
        break;
      default:
        return false;
    }
    callback.success();
    return true;
  }
}
