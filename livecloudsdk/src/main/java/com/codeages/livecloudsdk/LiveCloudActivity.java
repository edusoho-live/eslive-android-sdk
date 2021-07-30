package com.codeages.livecloudsdk;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ContentLoadingProgressBar;

import com.tencent.smtt.export.external.TbsCoreSettings;
import com.tencent.smtt.export.external.interfaces.ConsoleMessage;
import com.tencent.smtt.export.external.interfaces.JsPromptResult;
import com.tencent.smtt.export.external.interfaces.JsResult;
import com.tencent.smtt.export.external.interfaces.PermissionRequest;
import com.tencent.smtt.export.external.interfaces.SslError;
import com.tencent.smtt.export.external.interfaces.SslErrorHandler;
import com.tencent.smtt.export.external.interfaces.WebResourceError;
import com.tencent.smtt.export.external.interfaces.WebResourceRequest;
import com.tencent.smtt.sdk.QbSdk;
import com.tencent.smtt.sdk.TbsListener;
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
    private boolean disableX5;
    private String roomId = "0";
    private PermissionRequest myRequest;
    private Boolean connect = false;
    private Boolean isFullscreen = false;
    private Long enterTimestamp;
    private ContentLoadingProgressBar loadingView;

    private static final String JSInterface = "LiveCloudBridge";

    /**
     * @param isGrantedPermission true: 权限获取成功 false：权限获取失败
     */
    public static void launch(Context context, String url, boolean isLive, boolean isGrantedPermission, Map<String, Object> options) {
        Intent intent = new Intent(context, LiveCloudActivity.class);
        intent.putExtra("url", url);
        intent.putExtra("isLive", isLive);
        intent.putExtra("isGrantedPermission", isGrantedPermission);
        String roomId = String.valueOf(LiveCloudUtils.parseJwt(url).get("rid"));
        intent.putExtra("roomId", roomId);
        if (options != null && options.get("logUrl") != null) {
            intent.putExtra("logUrl", (String) options.get("logUrl"));
        } else {
            intent.putExtra("logUrl", "https://live-log.edusoho.com/collect");
        }

        ProgressDialog progressDialog = ProgressDialog.show(context, "", "加载中", true, true);

        LiveCloudUtils.checkClearCaches(context);

        initTbs(context);

        String blacklistUrl = "https://livecloud-storage-sh.edusoho.net/metas/x5blacklist.json?ts=" + System.currentTimeMillis();
        LiveCloudHttpClient.get(blacklistUrl, 3000, (successMsg, errorMsg) -> {
            if (successMsg != null) {
                String version = String.valueOf(QbSdk.getTbsVersion(context));
                try {
                    JSONObject msg = new JSONObject(successMsg);
                    JSONArray list = msg.getJSONArray(isLive ? "live" : "replay");
                    for (int i = 0; i < list.length(); i++) {
                        if (list.getString(i).equals(version)) {
                            QbSdk.forceSysWebView();
                            intent.putExtra("disableX5", true);
                            context.startActivity(intent);
                            progressDialog.dismiss();
                            return;
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            if (LiveCloudUtils.getTimeoutTimes(context, roomId) > 3) {
                QbSdk.forceSysWebView();
                intent.putExtra("disableX5", true);
            } else {
                QbSdk.unForceSysWebView();
                intent.putExtra("disableX5", false);
            }
            context.startActivity(intent);
            progressDialog.dismiss();
        });
    }

    @Override
    public void onBackPressed() {
        if (connect) {
            webView.evaluateJavascript("liveCloudNativeEventCallback({name:'back'})", null);
        } else {
            if (enterDurationSecond() >= 10) {
                int times = LiveCloudUtils.connectTimeout(this, roomId);
                LiveCloudUtils.checkClearCaches(this);
                Map<String, Object> logData = new HashMap<String, Object>() {
                    {
                        put("message", "[event] @(SDK.ConnectTimeout), " + roomId + "=" + times);
                    }
                };
                postLog("SDK.ConnectTimeout", logData);
            } else {
                Map<String, Object> logData = new HashMap<String, Object>() {
                    {
                        put("message", "[event] @(SDK.NotConnect)");
                    }
                };
                postLog("SDK.NotConnect", logData);
            }
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
        roomId = getIntent().getStringExtra("roomId");

        loadingView = findViewById(R.id.loadingView);
        loadingView.show();

        createWebView();

        loadRoomURL();

        collectDeviceLog();

        if (!getIntent().getBooleanExtra("isGrantedPermission", true)) {
            collectPermissionDeny();
        }

        enterTimestamp = System.currentTimeMillis();

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

    private void loadRoomURL() {
        byte[] infoByte = new JSONObject(deviceInfoMap()).toString().getBytes(StandardCharsets.UTF_8);
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
        QbSdk.setTbsListener(new TbsListener() {
            @Override
            public void onDownloadFinish(int i) {

            }

            @Override
            public void onInstallFinish(int i) {
                collectX5Installed();
            }

            @Override
            public void onDownloadProgress(int i) {

            }
        });

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

    private static void initTbs(Context context) {
        QbSdk.setDownloadWithoutWifi(true);

        Map<String, Object> map = new HashMap<>();
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_SPEEDY_CLASSLOADER, true);
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_DEXLOADER_SERVICE, true);
        QbSdk.initTbsSettings(map);

        QbSdk.initX5Environment(context.getApplicationContext(), null);
    }

    private void collectDeviceLog() {
        Map<String, Object> device = deviceInfoMap();
        Map<String, Object> logData = new HashMap<String, Object>() {
            {
                put("message", "[event] @(SDK.Enter), " + new JSONObject(device).toString());
                put("device", device);
            }
        };
        postLog("SDK.Enter", logData);
    }

    private void collectX5Installed() {
        Map<String, Object> logData = new HashMap<String, Object>() {
            {
                put("message", "[event] @(SDK.X5Installed)");
            }
        };
        postLog("SDK.X5Installed", logData);
    }

    private void collectPermissionDeny() {
        Map<String, Object> logData = new HashMap<String, Object>() {
            {
                put("message", "[event] @(SDK.PermissionDeny)");
            }
        };
        postLog("SDK.PermissionDeny", logData);
    }

    private void postLog(String action, Map<String, Object> logData) {
        String dateString = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault())
                .format(new Date());
        Map<String, Object> jwt = LiveCloudUtils.parseJwt(url);

        Map<String, Object> log = new HashMap<>();
        log.put("@timestamp", dateString);
        log.putAll(logData);
        log.put("event", new HashMap<String, String>() {
            {
                put("action", action);
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


    private Map<String, Object> deviceInfoMap() {
        Map<String, Object> info = new HashMap<>(LiveCloudUtils.deviceInfo(this));
        info.put("x5Version", QbSdk.getTbsVersion(this));
        info.put("disableX5", disableX5);
        info.put("x5Loaded", webView.getX5WebViewExtension() != null);
        return info;
    }

    private WebViewClient createWebViewClient() {
        return new WebViewClient() {

            @Override
            public void onPageFinished(WebView webView, String s) {
                super.onPageFinished(webView, s);
                loadingView.hide();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                Map<String, Object> logData = new HashMap<String, Object>() {
                    {
                        put("message", "[event] @(SDK.WebViewError), " + error.getErrorCode() + error.getDescription());
                    }
                };
                postLog("SDK.WebViewError", logData);
//                String x5CrashInfo = WebView.getCrashExtraMessage(view.getContext());
            }

            @Override
            public void onReceivedSslError(WebView webView, SslErrorHandler sslErrorHandler, SslError sslError) {
                sslErrorHandler.proceed();
                super.onReceivedSslError(webView, sslErrorHandler, sslError);
            }
        };
    }

    private WebChromeClient createWebChromeClient() {
        return new WebChromeClient() {

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    for (String permission : request.getResources()) {
                        myRequest = request;
                        switch (permission) {
                            case PermissionRequest.RESOURCE_AUDIO_CAPTURE: {
                                askForPermission(Manifest.permission.RECORD_AUDIO);
                                break;
                            }
                            case PermissionRequest.RESOURCE_VIDEO_CAPTURE: {
                                askForPermission(Manifest.permission.CAMERA);
                                break;
                            }
                        }
                    }
                });
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

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (enterDurationSecond() < 10 && consoleMessage.messageLevel().equals(ConsoleMessage.MessageLevel.ERROR)) {
                    Map<String, Object> logData = new HashMap<String, Object>() {
                        {
                            put("message", "[event] @(SDK.WebViewError), " + consoleMessage.message());
                        }
                    };
                    postLog("SDK.WebViewError", logData);
                }
                return super.onConsoleMessage(consoleMessage);
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
            // 全屏显示，隐藏状态栏和导航栏，拉出状态栏和导航栏显示一会儿后消失。
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
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

    private long enterDurationSecond() {
        return (System.currentTimeMillis() - enterTimestamp) / 1000;
    }

    private void askForPermission(String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED) {
//            myRequest.grant(myRequest.getResources());
        } else {
            requestPermissionLauncher.launch(permission);
        }
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                runOnUiThread(() -> {
                    if (isGranted) {
                        myRequest.grant(myRequest.getResources());
                    } else {
                        myRequest.deny();
                    }
                });
            });
}
