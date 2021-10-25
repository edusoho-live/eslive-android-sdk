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
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.widget.FrameLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ContentLoadingProgressBar;

import com.codeages.livecloudsdk.server.HttpServerFactory;
import com.tencent.smtt.export.external.TbsCoreSettings;
import com.tencent.smtt.export.external.interfaces.ConsoleMessage;
import com.tencent.smtt.export.external.interfaces.JsPromptResult;
import com.tencent.smtt.export.external.interfaces.JsResult;
import com.tencent.smtt.export.external.interfaces.PermissionRequest;
import com.tencent.smtt.export.external.interfaces.SslError;
import com.tencent.smtt.export.external.interfaces.SslErrorHandler;
import com.tencent.smtt.export.external.interfaces.WebResourceError;
import com.tencent.smtt.export.external.interfaces.WebResourceRequest;
import com.tencent.smtt.export.external.interfaces.WebResourceResponse;
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
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class LiveCloudActivity extends AppCompatActivity {

    protected WebView x5WebView;
    protected ReplayWebView nativeWebView;
    protected String url = "";
    protected String logUrl = "";
    protected boolean isLive;
    private boolean disableX5;
    private String roomId = "0";
    private PermissionRequest x5Request;
    private android.webkit.PermissionRequest nativeRequest;
    private Boolean connect = false;
    private Boolean isFullscreen = false;
    private Long enterTimestamp;
    private ContentLoadingProgressBar loadingView;
    private LiveCloudLogger logger;

    private static final String JSInterface = "LiveCloudBridge";

    /**
     * @param isGrantedPermission true: 权限获取成功 false：权限获取失败
     */
    public static void launch(Context context, String url, boolean isLive, boolean isGrantedPermission, Map<String, Object> options) {
        Intent intent = new Intent(context, LiveCloudActivity.class);
        intent.putExtra("url", url);
        intent.putExtra("isLive", isLive);
        intent.putExtra("isGrantedPermission", isGrantedPermission);
        Map<String, Object> jwt = LiveCloudUtils.parseJwt(url);
        String roomId = String.valueOf(jwt.get("rid"));
        String accessKey = String.valueOf(jwt.get("kid"));
        intent.putExtra("roomId", roomId);
        if (options != null && options.get("logUrl") != null) {
            intent.putExtra("logUrl", (String) options.get("logUrl"));
        }

        start(context, isLive, intent, roomId, accessKey);
    }

    public static void launchOffline(Context context, String url, String roomId, String userId, String userName) {
        Intent intent = new Intent(context, LiveCloudActivity.class);
        intent.putExtra("url", url);
        intent.putExtra("roomId", roomId);
        intent.putExtra("userId", userId);
        intent.putExtra("userName", userName);

        start(context, false, intent, roomId, "");
    }

    private static void start(Context context, boolean isLive, Intent intent, String roomId, String accessKey) {
        ProgressDialog progressDialog = ProgressDialog.show(context, "", "加载中", true, true);

        LiveCloudUtils.checkClearCaches(context);

        initTbs(context);

        if ((!isLive && QbSdk.getTbsVersion(context) < 45613) ||    // 倍速播放bug
                (isLive && QbSdk.getTbsVersion(context) < 45000)) { // 直播
            intent.putExtra("disableX5", true);
            context.startActivity(intent);
            progressDialog.dismiss();
            return;
        }

        String blacklistUrl = "https://livecloud-storage-sh.edusoho.net/metas/feature-config.json?ts=" + System.currentTimeMillis();
        LiveCloudHttpClient.get(blacklistUrl, 3000, (successMsg, errorMsg) -> {
            boolean disableX5 = false;
            if (successMsg != null) {
                String version = String.valueOf(QbSdk.getTbsVersion(context));
                try {
                    JSONObject msg = new JSONObject(successMsg);
                    if (!isLive) {
                        boolean replayX5 = msg.getBoolean("replayX5");
                        if (!replayX5) {
                            disableX5 = true;
                        } else {
                            JSONArray schoolList = msg.getJSONArray("replayX5SchoolBlacklist");
                            for (int i = 0; i < schoolList.length(); i++) {
                                if (schoolList.getString(i).equals(accessKey)) {
                                    disableX5 = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (!disableX5) {
                        JSONArray versionList = msg.getJSONArray(isLive ? "liveX5VersionBlacklist" : "replayX5VersionBlacklist");
                        for (int i = 0; i < versionList.length(); i++) {
                            if (versionList.getString(i).equals(version)) {
                                disableX5 = true;
                                break;
                            }
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    intent.putExtra("disableX5", true);
                    context.startActivity(intent);
                    progressDialog.dismiss();
                    return;
                }
            } else {
                if (!isLive) {
                    disableX5 = true;
                }
            }

            if (disableX5) {
                intent.putExtra("disableX5", true);
                context.startActivity(intent);
                progressDialog.dismiss();
                return;
            }
            if (LiveCloudUtils.getTimeoutTimes(context, roomId) > 2) {
                disableX5 = true;
                LiveCloudUtils.disableX5(context, roomId);
            }
            intent.putExtra("disableX5", disableX5);
            context.startActivity(intent);
            progressDialog.dismiss();
        });
    }

    private static void initTbs(Context context) {
        QbSdk.setDownloadWithoutWifi(true);

        Map<String, Object> map = new HashMap<>();
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_SPEEDY_CLASSLOADER, true);
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_DEXLOADER_SERVICE, true);
        QbSdk.initTbsSettings(map);

        QbSdk.initX5Environment(context.getApplicationContext(), null);
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

        String userId = getIntent().getStringExtra("userId");
        String userName = getIntent().getStringExtra("userName");
        if (null != userId) {
            logger = LiveCloudLogger.getInstance(Long.parseLong(roomId), Long.parseLong(userId), userName, logUrl);
        } else {
            logger = LiveCloudLogger.getInstance(logUrl, url);

            logger.info("SDK.Enter", new JSONObject(deviceInfoMap()).toString(), new HashMap<String, Object>() {{
                put("device", deviceInfoMap());
            }});
            if (!getIntent().getBooleanExtra("isGrantedPermission", true)) {
                logger.debug("SDK.PermissionDeny", null, null);
            }
        }

        createWebView();

        loadRoomURL();

        enterTimestamp = System.currentTimeMillis();

    }

    @Override
    public void onBackPressed() {
        if (connect) {
            if (x5WebView != null) {
                x5WebView.evaluateJavascript("liveCloudNativeEventCallback({name:'back'})", null);
            } else {
                nativeWebView.evaluateJavascript("liveCloudNativeEventCallback({name:'back'})", null);
            }
        } else {
            if (enterDurationSecond() >= 10) {
                int times = LiveCloudUtils.connectTimeout(this, roomId);
                LiveCloudUtils.deleteCache(this);
                logger.warn("SDK.ConnectTimeout", roomId + "=" + times, null);
            } else {
                logger.warn("SDK.NotConnect", null, null);
            }
            super.onBackPressed();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (x5WebView != null) {
            x5WebView.removeJavascriptInterface(JSInterface);
            x5WebView.setWebViewClient(null);
            x5WebView.setWebChromeClient(null);
            x5WebView.loadDataWithBaseURL(null, "", "text/html", "utf-8", null);
            x5WebView.clearHistory();
            x5WebView.destroy();
            x5WebView = null;
        } else if (nativeWebView != null) {
            nativeWebView.removeJavascriptInterface(JSInterface);
            nativeWebView.setWebViewClient(null);
            nativeWebView.setWebChromeClient(null);
            nativeWebView.loadDataWithBaseURL(null, "", "text/html", "utf-8", null);
            nativeWebView.clearHistory();
            nativeWebView.destroy();
            nativeWebView = null;
        }
        HttpServerFactory.getInstance().stop();
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

        if (x5WebView != null) {
            x5WebView.loadUrl(url + "&device=" + base64String);
        } else {
            nativeWebView.loadUrl(url + "&device=" + base64String);
        }
//        webView.loadUrl("https://debugtbs.qq.com");
//        webView.loadUrl("https://live.edusoho.com/h5/detection");
//        webView.loadUrl("https://webrtc.github.io/samples/");
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void createWebView() {
//        android.webkit.WebView.setWebContentsDebuggingEnabled(true);
//        WebView.setWebContentsDebuggingEnabled(true);

        if (!disableX5) {
            QbSdk.setTbsListener(new TbsListener() {
                @Override
                public void onDownloadFinish(int i) {

                }

                @Override
                public void onInstallFinish(int i) {
                    logger.info("SDK.X5Installed", null, null);
                }

                @Override
                public void onDownloadProgress(int i) {

                }
            });
            x5WebView = findViewById(R.id.xWebView);
            x5WebView.setWebViewClient(createX5Client());
            x5WebView.setWebChromeClient(createX5ChromeClient());
            x5WebView.addJavascriptInterface(this, JSInterface);
            WebSettings webSettings = x5WebView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webSettings.setUseWideViewPort(true);
            webSettings.setLoadWithOverviewMode(true);
            webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
            webSettings.setDefaultTextEncodingName("utf-8");
            webSettings.setDomStorageEnabled(true);
            webSettings.setMixedContentMode(0);
            webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
            webSettings.setMediaPlaybackRequiresUserGesture(false);
        } else {
            nativeWebView = new ReplayWebView(this);
            nativeWebView.setWebViewClient(createNativeClient());
            nativeWebView.setWebChromeClient(createNativeChromeClient());
            nativeWebView.addJavascriptInterface(this, JSInterface);
            FrameLayout rootLayout = findViewById(R.id.viewLayout);
            rootLayout.addView(nativeWebView);
        }
    }


    private WebViewClient createX5Client() {
        return new WebViewClient() {

            @Override
            public void onPageFinished(WebView webView, String s) {
                super.onPageFinished(webView, s);
                loadingView.hide();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                logger.debug("SDK.WebViewError", "WebResourceError: " + error.getErrorCode() + " " + error.getDescription(), null);
//                String x5CrashInfo = WebView.getCrashExtraMessage(view.getContext());
            }

            @Override
            public void onReceivedSslError(WebView webView, SslErrorHandler sslErrorHandler, SslError sslError) {
                sslErrorHandler.proceed();
                logger.debug("SDK.WebViewError", "sslError: " + sslError.getPrimaryError() + " " + sslError.getUrl(), null);
            }

            @Override
            public void onReceivedHttpError(WebView webView, WebResourceRequest webResourceRequest, WebResourceResponse webResourceResponse) {
                super.onReceivedHttpError(webView, webResourceRequest, webResourceResponse);
                logger.debug("SDK.WebViewError", "httpError: " + webResourceResponse.getStatusCode() + " " + webResourceResponse.getReasonPhrase(), null);
            }

            @Override
            public void onReceivedError(WebView webView, int i, String s, String s1) {
                super.onReceivedError(webView, i, s, s1);
                logger.debug("SDK.WebViewError", "WebResourceError: " + i + " " + s + " " + s1, null);
            }
        };
    }

    private android.webkit.WebViewClient createNativeClient() {
        return new android.webkit.WebViewClient() {
            @Override
            public void onPageFinished(android.webkit.WebView view, String url) {
                super.onPageFinished(view, url);
                loadingView.hide();
            }

            @Override
            public void onReceivedError(android.webkit.WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                logger.debug("SDK.WebViewError", "WebResourceError: " + errorCode + " " + description + " " + failingUrl, null);
            }

            @Override
            public void onReceivedSslError(android.webkit.WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                super.onReceivedSslError(view, handler, error);
                logger.debug("SDK.WebViewError", "sslError: " + error.getPrimaryError() + " " + error.getUrl(), null);
            }

            @Override
            public void onReceivedHttpError(android.webkit.WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                logger.debug("SDK.WebViewError", "httpError: " + errorResponse.getStatusCode() + " " + errorResponse.getReasonPhrase(), null);
            }
        };
    }

    private WebChromeClient createX5ChromeClient() {
        return new WebChromeClient() {

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    for (String permission : request.getResources()) {
                        x5Request = request;
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
//                if (enterDurationSecond() < 10 && consoleMessage.messageLevel().equals(ConsoleMessage.MessageLevel.ERROR)) {
//                    logger.error("SDK.WebViewError", "ConsoleMessage: " + consoleMessage.message(), null);
//                }
                return super.onConsoleMessage(consoleMessage);
            }
        };
    }

    private android.webkit.WebChromeClient createNativeChromeClient() {
        return new android.webkit.WebChromeClient() {
            @Override
            public void onPermissionRequest(android.webkit.PermissionRequest request) {
                runOnUiThread(() -> {
                    for (String permission : request.getResources()) {
                        nativeRequest = request;
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
            public boolean onJsPrompt(android.webkit.WebView view, String url, String message, String defaultValue, android.webkit.JsPromptResult result) {
                result.cancel();
                return true;
            }

            @Override
            public boolean onJsConfirm(android.webkit.WebView view, String url, String message, android.webkit.JsResult result) {
                result.cancel();
                return true;
            }

            @Override
            public boolean onJsAlert(android.webkit.WebView view, String url, String message, android.webkit.JsResult result) {
                result.cancel();
                return true;
            }

            @Override
            public boolean onJsBeforeUnload(android.webkit.WebView view, String url, String message, android.webkit.JsResult result) {
                result.cancel();
                return true;
            }
        };
    }

    @JavascriptInterface
    public void connect() {
        connect = true;
        LiveCloudUtils.clearTimeoutTimes(this, roomId);
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
        runOnUiThread(this::finish);
    }


    private Map<String, Object> deviceInfoMap() {
        Map<String, Object> info = new HashMap<>(LiveCloudUtils.deviceInfo(this));
        info.put("x5Version", QbSdk.getTbsVersion(this));
        info.put("disableX5", disableX5);
        info.put("x5Loaded", x5WebView != null && x5WebView.getX5WebViewExtension() != null);
        return info;
    }

    private void setWindowFullScreen() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        LiveCloudUtils.fullScreen(this, true);
    }

    private void setWindowShrinkScreen() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        LiveCloudUtils.fullScreen(this,false);
    }

    private long enterDurationSecond() {
        return (System.currentTimeMillis() - enterTimestamp) / 1000;
    }

    private void askForPermission(String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) !=
                PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permission);
        }
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                runOnUiThread(() -> {
                    if (isGranted) {
                        if (x5Request != null) {
                            x5Request.grant(x5Request.getResources());
                        } else {
                            nativeRequest.grant(nativeRequest.getResources());
                        }
                    } else {
                        if (x5Request != null) {
                            x5Request.deny();
                        } else {
                            nativeRequest.deny();
                        }
                    }
                });
            });
}
