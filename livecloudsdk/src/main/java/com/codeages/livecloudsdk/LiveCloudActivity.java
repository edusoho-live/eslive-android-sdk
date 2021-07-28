package com.codeages.livecloudsdk;

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
import android.view.View;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LiveCloudActivity extends AppCompatActivity {

    protected WebView webView;
    protected String url = "";
    protected String logUrl = "";
    protected boolean isLive;
    private Boolean connect = false;
    private Boolean isFullscreen = false;
    private PermissionRequest myRequest;

    private static final String JSInterface = "LiveCloudBridge";

    private boolean disableX5;

    public static void launch(Context context, String url, boolean isLive, Map<String, Object> options) {
        Intent intent = new Intent(context, LiveCloudActivity.class);
        intent.putExtra("url", url);
        intent.putExtra("isLive", isLive);
        if (options != null && options.get("logUrl") != null) {
            intent.putExtra("logUrl", (String) options.get("logUrl"));
        } else {
            intent.putExtra("logUrl", "https://live-log.edusoho.com/collect");
        }
        String blacklistUrl = "https://livecloud-storage-sh.edusoho.net/metas/x5blacklist.json?ts=" + System.currentTimeMillis();
        LiveCloudHttpClient.get(blacklistUrl, 3000, (successMsg, errorMsg) ->  {
            if (successMsg != null) {
                String version = String.valueOf(QbSdk.getTbsVersion(context));
                try {
                    JSONObject msg = new JSONObject(successMsg);
                    JSONArray list = msg.getJSONArray(isLive ? "live" : "replay");
                    for (int i = 0; i < list.length(); i++) {
                        if (list.getString(i).equals(version)) {
                            intent.putExtra("disableX5", true);
                            QbSdk.forceSysWebView();
                            context.startActivity(intent);
                            return;
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            intent.putExtra("disableX5", false);
            QbSdk.unForceSysWebView();
            context.startActivity(intent);
        });
    }

    @Override
    public void onBackPressed() {
        if (connect) {
            webView.evaluateJavascript("liveCloudNativeEventCallback({name:'back'})", null);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.LiveCloudTheme);
        setContentView(R.layout.activity_live_cloud);

        // X5网页中的视频，上屏幕的时候，可能出现闪烁的情况，需要如下设置
        getWindow().setFormat(PixelFormat.TRANSLUCENT);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        url = getIntent().getStringExtra("url");
        isLive = getIntent().getBooleanExtra("isLive", false);
        logUrl = getIntent().getStringExtra("logUrl");
        disableX5 = getIntent().getBooleanExtra("disableX5", false);

        collectDeviceLog();

        initTbs();

        createWebView();

        loadRoomURL();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
        byte[] infoByte = new JSONObject(deviceInfoString()).toString().getBytes(StandardCharsets.UTF_8);
        String base64String;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            base64String = Base64.getUrlEncoder().withoutPadding().encodeToString(infoByte);
        } else {
            base64String = android.util.Base64.encodeToString(infoByte,
                    android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE);
        }

        webView.loadUrl(url + "&device=" + base64String);
//        webView.loadUrl("https://debugtbs.qq.com");
//        webView.loadUrl("https://live.edusoho.com/h5/detection");
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

    private void collectDeviceLog() {
        String dateString = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault())
                .format(new Date());
        String token = new UrlQuerySanitizer(url).getValue("token");
        Map<String, Object> jwt = LiveCloudUtils.jwtDecodeWithJwtString(token);
        Map<String, Object> device = deviceInfoString();

        Map<String, Object> log = new HashMap<>();
        log.put("@timestamp", dateString);
        log.put("message", "[event] @(sdk.enter), " + new JSONObject(device).toString());
        log.put("device", device);
        log.put("event", new HashMap<String, String>() {
            {
                put("action", "sdk.enter");
                put("dataset", "livecloud.client");
                put("id", LiveCloudUtils.randomString(10));
                put("kind", "event");
            }
        });
        log.put("log", new HashMap<String, String>() {
            {
                put("level", "INFO");
            }
        });
        log.put("room", new HashMap<String, Object>() {
            {
                put("id", jwt.get("rid"));
            }
        });
        log.put("user", new HashMap<String, Object>() {
            {
                put("id", jwt.get("uid"));
                put("name", jwt.get("name"));
                put("roles", new Object[]{jwt.get("role")});
            }
        });
        Map<String, Object> payload = new HashMap<String, Object>() {
            {
                put("@timestamp", dateString);
                put("logs", new Object[]{log});
            }
        };


        LiveCloudHttpClient.post(logUrl, new JSONObject(payload).toString(), null);
    }


    private Map<String, Object> deviceInfoString() {
        Map<String, Object> info = new HashMap<>(LiveCloudUtils.deviceInfo(this));
        info.put("x5Version", QbSdk.getTbsVersion(this));
        info.put("disableX5", disableX5);
        return info;
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
    public void connect() {
        connect = true;
    }

    @JavascriptInterface
    public void fullscreen() {
        runOnUiThread(() -> {
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

    @JavascriptInterface
    public void exit() {
        runOnUiThread(() -> finish());

    }

    private void setWindowFullScreen() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        fullScreen(true);
    }

    private void setWindowShrinkScreen() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        fullScreen(false);
    }

    public void fullScreen(boolean isFull) {//控制是否全屏显示
        if (isFull) {
            hideNavigationBar();

            //适配刘海屏
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(lp);
            }
            //隐藏状态栏
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // 全屏显示，隐藏状态栏和导航栏，拉出状态栏和导航栏显示一会儿后消失。
                    getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                } else {
                    // 全屏显示，隐藏状态栏
                    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
                }
            }
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            showNavigationBar();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    private void hideNavigationBar() {
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        decorView.setSystemUiVisibility(uiOptions);
    }

    private void showNavigationBar() {
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_VISIBLE;
        decorView.setSystemUiVisibility(uiOptions);
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
