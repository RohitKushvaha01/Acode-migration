/*
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
*/
package runner.webView;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.*;
import androidx.core.content.FileProvider;
import java.io.*;
import java.util.*;
import runner.Host;
import runner.Service;

final class FileChooser {

  private static final String LOG_TAG = "AcodeFileChooser";
  private final Host host;

  FileChooser(Host host) {
    this.host = host;
  }

  public boolean show(
    WebView webView,
    final ValueCallback<Uri[]> filePathsCallback,
    final WebChromeClient.FileChooserParams fileChooserParams
  ) {
    Intent fileIntent = fileChooserParams.createIntent();

    // Check if multiple-select is specified
    Boolean selectMultiple = false;
    if (
      fileChooserParams.getMode() ==
      WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
    ) {
      selectMultiple = true;
    }
    fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, selectMultiple);

    // Uses Intent.EXTRA_MIME_TYPES to pass multiple mime types.
    String[] acceptTypes = fileChooserParams.getAcceptTypes();
    if (acceptTypes.length > 1) {
      fileIntent.setType("*/*"); // Accept all, filter mime types by Intent.EXTRA_MIME_TYPES.
      fileIntent.putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes);
    }

    // Image from camera intent
    Uri tempUri = null;
    Intent captureIntent = null;
    if (fileChooserParams.isCaptureEnabled()) {
      captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
      Context context = webView.getContext();
      if (
        context
          .getPackageManager()
          .hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) &&
        captureIntent.resolveActivity(context.getPackageManager()) != null
      ) {
        try {
          File tempFile = createTempFile(context);
          runner.LOG.d(LOG_TAG, "Temporary photo capture file: " + tempFile);
          tempUri = createUriForFile(context, tempFile);
          runner.LOG.d(LOG_TAG, "Temporary photo capture URI: " + tempUri);
          captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, tempUri);
        } catch (IOException e) {
          runner.LOG.e(
            LOG_TAG,
            "Unable to create temporary file for photo capture",
            e
          );
          captureIntent = null;
        }
      } else {
        runner.LOG.w(LOG_TAG, "Device does not support photo capture");
        captureIntent = null;
      }
    }
    final Uri captureUri = tempUri;

    // Chooser intent
    Intent chooserIntent = Intent.createChooser(fileIntent, null);
    if (captureIntent != null) {
      chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] {
        captureIntent,
      });
    }

    try {
      runner.LOG.i(LOG_TAG, "Starting intent for file chooser");
      host.startActivityForResult(
        new Service() {
          @Override
          public void onActivityResult(
            int requestCode,
            int resultCode,
            Intent intent
          ) {
            // Handle result
            Uri[] result = null;
            if (resultCode == Activity.RESULT_OK) {
              List<Uri> uris = new ArrayList<>();

              if (intent != null && intent.getData() != null) {
                // single file
                runner.LOG.v(
                  LOG_TAG,
                  "Adding file (single): " + intent.getData()
                );
                uris.add(intent.getData());
              } else if (captureUri != null) {
                // camera
                runner.LOG.v(LOG_TAG, "Adding camera capture: " + captureUri);
                uris.add(captureUri);
              } else if (intent != null && intent.getClipData() != null) {
                // multiple files
                ClipData clipData = intent.getClipData();
                int count = clipData.getItemCount();
                for (int i = 0; i < count; i++) {
                  Uri uri = clipData.getItemAt(i).getUri();
                  runner.LOG.v(LOG_TAG, "Adding file (multiple): " + uri);
                  if (uri != null) {
                    uris.add(uri);
                  }
                }
              }

              if (!uris.isEmpty()) {
                runner.LOG.d(LOG_TAG, "Receive file chooser URL: " + uris);
                result = uris.toArray(new Uri[0]);
              }
            }
            filePathsCallback.onReceiveValue(result);
          }
        },
        chooserIntent,
        0
      );
    } catch (ActivityNotFoundException e) {
      runner.LOG.w(
        LOG_TAG,
        "No activity found to handle file chooser intent.",
        e
      );
      filePathsCallback.onReceiveValue(null);
    }
    return true;
  }

  private File createTempFile(Context context) throws IOException {
    // Create an image file name
    return File.createTempFile("temp", ".jpg", context.getCacheDir());
  }

  private Uri createUriForFile(Context context, File tempFile)
    throws IOException {
    String appId = context.getPackageName();
    return FileProvider.getUriForFile(context, appId + ".provider", tempFile);
  }
}
