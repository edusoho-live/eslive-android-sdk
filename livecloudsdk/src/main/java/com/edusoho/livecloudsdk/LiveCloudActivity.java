package com.edusoho.livecloudsdk;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.net.UrlQuerySanitizer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.tencent.smtt.export.external.TbsCoreSettings;
import com.tencent.smtt.export.external.interfaces.JsPromptResult;
import com.tencent.smtt.export.external.interfaces.JsResult;
import com.tencent.smtt.export.external.interfaces.PermissionRequest;
import com.tencent.smtt.export.external.interfaces.WebResourceError;
import com.tencent.smtt.export.external.interfaces.WebResourceRequest;
import com.tencent.smtt.sdk.QbSdk;
import com.tencent.smtt.sdk.WebChromeClient;
import com.tencent.smtt.sdk.WebSettings;
import com.tencent.smtt.sdk.WebView;
import com.tencent.smtt.sdk.WebViewClient;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class LiveCloudActivity extends AppCompatActivity {

    protected WebView webView;
    protected String url = "";
    private Boolean isFullscreen = false;
    private PermissionRequest myRequest;

    private static final String JSInterface = "LiveCloudBridge";

    public static void launch(Context context, String url) {
        Intent intent = new Intent(context, LiveCloudActivity.class);
        intent.putExtra("url", url);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_live_cloud);

        // X5网页中的视频，上屏幕的时候，可能出现闪烁的情况，需要如下设置
        getWindow().setFormat(PixelFormat.TRANSLUCENT);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        url = getIntent().getStringExtra("url");

        initTbs();

        createWebView();

        loadRoomURL();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (webView != null) {
            webView.removeJavascriptInterface(JSInterface);
            webView.setWebViewClient(null);
            webView.setWebChromeClient(null);
            webView.loadDataWithBaseURL(null, "", "text/html", "utf-8", null);
            webView.clearHistory();
            webView.destroy();
            webView = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            myRequest.grant(myRequest.getResources());
        } else {
            myRequest.deny();
        }
    }

    private void loadRoomURL() {
        String infoString = deviceInfoString();
        String base64String;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            base64String = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(infoString.getBytes(StandardCharsets.UTF_8));
        } else {
            base64String = android.util.Base64.encodeToString(infoString.getBytes(StandardCharsets.UTF_8),
                    android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE);
        }

        webView.loadUrl(url + "&device=" + base64String);
//        webView.loadUrl("https://debugtbs.qq.com");
//        webView.loadUrl("https://webrtc.github.io/samples/");
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void createWebView() {
        webView = findViewById(R.id.webView);
        webView.setWebViewClient(createWebViewClient());
        webView.setWebChromeClient(createWebChromeClient());
        webView.addJavascriptInterface(this, JSInterface);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setDefaultTextEncodingName("utf-8");
        webSettings.setDomStorageEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
    }

    private void initTbs() {
        QbSdk.initX5Environment(this, null);

        Map<String, Object> map = new HashMap<>();
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_SPEEDY_CLASSLOADER, true);
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_DEXLOADER_SERVICE, true);
        QbSdk.initTbsSettings(map);
    }


    private String deviceInfoString() {
        Map<String, Object> info = new HashMap<>(LiveCloudUtils.deviceInfo(this));
        info.put("x5Version", QbSdk.getTbsVersion(this));
        return new JSONObject(info).toString();
    }

    private WebViewClient createWebViewClient() {
        return new WebViewClient() {

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);

//                String x5CrashInfo = WebView.getCrashExtraMessage(view.getContext());
            }
        };
    }

    private WebChromeClient createWebChromeClient() {
        return new WebChromeClient() {

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                myRequest = request;
                for (String permission : request.getResources()) {
                    switch (permission) {
                        case PermissionRequest.RESOURCE_AUDIO_CAPTURE: {
                            askForPermission(Manifest.permission.RECORD_AUDIO, 1);
                            break;
                        }
                        case PermissionRequest.RESOURCE_VIDEO_CAPTURE: {
                            askForPermission(Manifest.permission.CAMERA, 2);
                            break;
                        }
                    }
                }
            }

            @Override
            public boolean onJsPrompt(WebView webView, String s, String s1, String s2, JsPromptResult jsPromptResult) {
                jsPromptResult.cancel();
                return true;
            }
            @Override
            public boolean onJsConfirm(WebView webView, String s, String s1, JsResult jsResult) {
                jsResult.cancel();
                return true;
            }
            @Override
            public boolean onJsAlert(WebView webView, String s, String s1, JsResult jsResult) {
                jsResult.cancel();
                return true;
            }
            @Override
            public boolean onJsBeforeUnload(WebView webView, String s, String s1, JsResult jsResult) {
                jsResult.cancel();
                return true;
            }
        };
    }

    @JavascriptInterface
    public void fullscreen() {
        new Handler(Looper.getMainLooper()).post(() -> {
            isFullscreen = !isFullscreen;
            if (isFullscreen) {
                setWindowFullScreen();
            } else {
                setWindowShrinkScreen();
            }
        });
    }

    @JavascriptInterface
    public String getDeviceInfo() {
        Map<String, Object> info = LiveCloudUtils.deviceInfo(this);
        return new JSONObject(info).toString();
    }

    private void setWindowFullScreen() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void setWindowShrinkScreen() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void askForPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(getApplicationContext(), permission)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {
                ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
            }
        } else {
            myRequest.grant(myRequest.getResources());
        }
    }
}
