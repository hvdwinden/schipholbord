package nl.schipholbord;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Toont het klapbord (assets/index.html) in een WebView.
 * Aanvragen naar de Schiphol-API gaan via de app zelf (SchipholBridge), zodat de
 * browserbeveiliging (CORS) ze niet kan tegenhouden.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/index.html";
    private static final String API = "https://api.schiphol.nl/public-flights/";

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        FrameLayout root = new FrameLayout(this);
        root.addView(webView);
        setContentView(root);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        webView.addJavascriptInterface(new Bridge(), "SchipholBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!HOST.equals(uri.getHost())) return null;
                String path = uri.getPath();
                if (path == null || path.equals("/")) path = "/index.html";
                try {
                    InputStream in = getAssets().open(path.substring(1));
                    return new WebResourceResponse(path.endsWith(".html") ? "text/html" : "application/octet-stream", "utf-8", in);
                } catch (IOException e) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, null);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (HOST.equals(uri.getHost())) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, uri));   // links (bv. developer.schiphol.nl) in de browser
                return true;
            }
        });

        // Terugknop: eerst een open venster in de pagina sluiten, anders de app uit
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }

        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl(START_URL);
    }

    private void handleBack() {
        webView.evaluateJavascript("window.handleBack ? window.handleBack() : false", r -> {
            if (!"true".equals(r)) finish();
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() { handleBack(); }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        webView.saveState(out);
    }

    /** Haalt een API-pad op met de sleutel van de gebruiker; antwoord via window.onBridgeResult(id, status, body). */
    private class Bridge {
        @JavascriptInterface
        public void get(String id, String path, String appId, String appKey) {
            new Thread(() -> {
                int status = 0;
                String body = "";
                try {
                    if (path.contains("..") || path.startsWith("/")) throw new IOException("ongeldig pad");
                    HttpURLConnection con = (HttpURLConnection) new URL(API + path).openConnection();
                    con.setConnectTimeout(15_000);
                    con.setReadTimeout(20_000);
                    con.setRequestProperty("Accept", "application/json");
                    con.setRequestProperty("ResourceVersion", "v4");
                    con.setRequestProperty("app_id", appId);
                    con.setRequestProperty("app_key", appKey);
                    status = con.getResponseCode();
                    InputStream in = status >= 400 ? con.getErrorStream() : con.getInputStream();
                    if (in != null) {
                        StringBuilder sb = new StringBuilder();
                        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                            String line;
                            while ((line = r.readLine()) != null) sb.append(line).append('\n');
                        }
                        body = sb.toString();
                    }
                    con.disconnect();
                } catch (Exception e) {
                    status = 0;
                    body = String.valueOf(e.getMessage());
                }
                String js = "window.onBridgeResult(" + JSONObject.quote(id) + "," + status + "," + JSONObject.quote(body) + ")";
                runOnUiThread(() -> webView.evaluateJavascript(js, null));
            }).start();
        }
    }
}
