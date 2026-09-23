package com.foxdebug.browser;

import android.content.Intent;
import com.foxdebug.browser.BrowserActivity;
import runner.Callback;
import runner.Service;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Plugin extends Service {

  @Override
  public boolean execute(
    String action,
    JSONArray args,
    Callback callbackContext
  ) throws JSONException {
    if (action.equals("open")) {
      String url = args.getString(0);
      JSONObject theme = args.getJSONObject(1);
      boolean onlyConsole = args.optBoolean(2, false);
      String themeString = theme.toString();
      Intent intent = new Intent(host.getActivity(), BrowserActivity.class);

      intent.putExtra("url", url);
      intent.putExtra("theme", themeString);
      intent.putExtra("onlyConsole", onlyConsole);
      host.getActivity().startActivity(intent);
      callbackContext.success("Opened browser");
      return true;
    }
    return false;
  }
}
