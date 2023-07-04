package org.proxlu.appgpt;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private WebView chatWebView = null;
    private WebSettings chatWebSettings = null;
    private CookieManager chatCookieManager = null;
    private final Context context = this;
    private String TAG = "AppGPT";
    private String urlToLoad = "https://talkai.info/pt/";

    private static final ArrayList<String> allowedDomains = new ArrayList<>();

    @Override
    protected void onPause() {
        if (chatCookieManager != null) chatCookieManager.flush();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setTheme(android.R.style.Theme_DeviceDefault_DayNight);
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Create the WebView
        chatWebView = findViewById(R.id.chatWebView);

        // Set cookie options
        chatCookieManager = CookieManager.getInstance();
        chatCookieManager.setAcceptCookie(true);
        chatCookieManager.setAcceptThirdPartyCookies(chatWebView, false);

        // Restrict what gets loaded
        initURLs();

        chatWebView.setWebViewClient(new WebViewClient() {
            // Keep these in sync!
            @Override
            public WebResourceResponse shouldInterceptRequest(final WebView view, WebResourceRequest request) {
                if (request.getUrl().toString().equals("about:blank")) {
                    return null;
                }
                if (!request.getUrl().toString().startsWith("https://")) {
                    Log.d(TAG, "[shouldInterceptRequest][NON-HTTPS] Blocked access to " + request.getUrl().toString());
                    return new WebResourceResponse("text/javascript", "UTF-8", null); // Deny URLs that aren't HTTPS
                }
                boolean allowed = false;
                for (String url : allowedDomains) {
                    if (request.getUrl().getHost().endsWith(url)) {
                        allowed = true;
                    }
                }
                if (!allowed) {
                    Log.d(TAG, "[shouldInterceptRequest][NOT ON ALLOWLIST] Blocked access to " + request.getUrl().getHost());
                    if (request.getUrl().getHost().equals("login.microsoftonline.com") || request.getUrl().getHost().equals("accounts.google.com") || request.getUrl().getHost().equals("appleid.apple.com")) {
                        Toast.makeText(context, context.getString(R.string.error_microsoft_google), Toast.LENGTH_LONG).show();
                        resetChat();
                    }
                    return new WebResourceResponse("text/javascript", "UTF-8", null); // Deny URLs not on ALLOWLIST
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request.getUrl().toString().equals("about:blank")) {
                    return false;
                }
                if (!request.getUrl().toString().startsWith("https://")) {
                    Log.d(TAG, "[shouldOverrideUrlLoading][NON-HTTPS] Blocked access to " + request.getUrl().toString());
                    return true; // Deny URLs that aren't HTTPS
                }
                boolean allowed = false;
                for (String url : allowedDomains) {
                    if (request.getUrl().getHost().endsWith(url)) {
                        allowed = true;
                    }
                }
                if (!allowed) {
                    Log.d(TAG, "[shouldOverrideUrlLoading][NOT ON ALLOWLIST] Blocked access to " + request.getUrl().getHost());
                    if (request.getUrl().getHost().equals("login.microsoftonline.com") || request.getUrl().getHost().equals("accounts.google.com") || request.getUrl().getHost().equals("appleid.apple.com")) {
                        Toast.makeText(context, context.getString(R.string.error_microsoft_google), Toast.LENGTH_LONG).show();
                        resetChat();
                    }
                    return true; // Deny URLs not on ALLOWLIST
                }
                return false;
            }

        });

        // Set more options
        chatWebSettings = chatWebView.getSettings();
        // Enable some WebView features
        chatWebSettings.setJavaScriptEnabled(true);
        chatWebSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        chatWebSettings.setDomStorageEnabled(true);
        // Disable some WebView features
        chatWebSettings.setAllowContentAccess(false);
        chatWebSettings.setAllowFileAccess(false);
        chatWebSettings.setBuiltInZoomControls(false);
        chatWebSettings.setDatabaseEnabled(false);
        chatWebSettings.setDisplayZoomControls(false);
        chatWebSettings.setSaveFormData(false);
        chatWebSettings.setGeolocationEnabled(false);

        //Set copy image functionality
        chatWebView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                WebView.HitTestResult result = chatWebView.getHitTestResult();
                if (result.getType() == WebView.HitTestResult.IMAGE_TYPE) {
                    String imgUrl = result.getExtra();
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Image URL", imgUrl);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(context, "Imagem copiada", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            }
        });

        // Load ChatGPT
        chatWebView.loadUrl(urlToLoad);
        if (GithubStar.shouldShowStarDialog(this)) GithubStar.starDialog(this, "https://github.com/proxlu/AppGPT");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Credit (CC BY-SA 3.0): https://stackoverflow.com/a/6077173
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                    if (chatWebView.canGoBack() && !chatWebView.getUrl().equals("about:blank")) {
                        chatWebView.goBack();
                    } else {
                        finish();
                    }
                    return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    public void resetChat() {

        chatWebView.clearFormData();
        chatWebView.clearHistory();
        chatWebView.clearMatches();
        chatWebView.clearSslPreferences();
        chatCookieManager.removeSessionCookie();
        chatCookieManager.removeAllCookie();
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        WebStorage.getInstance().deleteAllData();
        chatWebView.loadUrl(urlToLoad);
    }

    private static void initURLs() {
        // Allowed Domains
        allowedDomains.add("cdn.auth0.com");
        allowedDomains.add("openai.com");
        allowedDomains.add("talkai.info");
        allowedDomains.add("oaidalleapiprodscus.blob.core.windows.net");
        allowedDomains.add("cdn-icons-png.flaticon.com");

    }

    private void downloadAndCopyImage(String imageUrl) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream inputStream = new java.net.URL(imageUrl).openStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    if (bitmap != null) {
                        String fileName = "image.png";
                        saveBitmapToStorage(bitmap, fileName);
                        copyImageToClipboard(fileName);
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, "Falha ao baixar a imagem", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "Falha ao baixar a imagem", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void saveBitmapToStorage(Bitmap bitmap, String fileName) {
        File directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (directory != null && !directory.exists()) {
            directory.mkdirs();
        }
        File file = new File(directory, fileName);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void copyImageToClipboard(String fileName) {
        File directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (directory != null) {
            File file = new File(directory, fileName);
            if (file.exists()) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Image", file.getAbsolutePath());
                clipboard.setPrimaryClip(clip);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, "Imagem copiada", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    private void downloadFile(String url) {
        // Implement your file download logic here
    }
}
