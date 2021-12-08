package com.codeages.livecloudsdk;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.widget.FrameLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ContentLoadingProgressBar;

import com.codeages.livecloudsdk.server.HttpServerFactory;
import com.tencent.smtt.export.external.TbsCoreSettings;
import com.tencent.smtt.export.external.extension.interfaces.IX5WebChromeClientExtension;
import com.tencent.smtt.export.external.extension.interfaces.IX5WebViewExtension;
import com.tencent.smtt.export.external.interfaces.ConsoleMessage;
import com.tencent.smtt.export.external.interfaces.IX5WebViewBase;
import com.tencent.smtt.export.external.interfaces.JsPromptResult;
import com.tencent.smtt.export.external.interfaces.JsResult;
import com.tencent.smtt.export.external.interfaces.MediaAccessPermissionsCallback;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LiveCloudActivity extends AppCompatActivity {

    protected WebView x5WebView;
    protected ReplayWebView nativeWebView;
    protected String url = "";
    protected String logUrl = "";
    protected boolean isLive;
    private boolean disableX5;
    private boolean testUrl;
    private String roomId = "0";
    private android.webkit.PermissionRequest nativeRequest;
    private MediaAccessPermissionsCallback permissionsCallback;
    private String permissionSite;
    private long permissionType;
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
        if (options != null) {
            if (options.get("logUrl") != null) {
                intent.putExtra("logUrl", (String) options.get("logUrl"));
            }
            if (options.get("disableX5") != null) {
                intent.putExtra("disableX5", (Boolean) options.get("disableX5"));
            }
            if (options.get("testUrl") != null) {
                intent.putExtra("testUrl", (Boolean) options.get("testUrl"));
            }
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
        LiveCloudUtils.checkClearCaches(context);

        initTbs(context);

        if (intent.getBooleanExtra("testUrl", false)) {
            intent.putExtra("disableX5", intent.getBooleanExtra("disableX5", false));
            context.startActivity(intent);
            return;
        }

        if (intent.getBooleanExtra("disableX5", false)) {
            intent.putExtra("disableX5", true);
            context.startActivity(intent);
            return;
        }

        if ((!isLive && QbSdk.getTbsVersion(context) < 45613) ||    // 倍速播放bug
                (isLive && QbSdk.getTbsVersion(context) < 45000)) { // 直播
            intent.putExtra("disableX5", true);
            context.startActivity(intent);
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
                        if (!msg.getBoolean("replayX5")) {
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
                return;
            }
            if (LiveCloudUtils.getTimeoutTimes(context, roomId) > 2) {
                disableX5 = true;
                LiveCloudUtils.disableX5(context, roomId);
            }
            intent.putExtra("disableX5", disableX5);
            context.startActivity(intent);
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
        testUrl = getIntent().getBooleanExtra("testUrl", false);

        loadingView = findViewById(R.id.loadingView);
        loadingView.show();

        createWebView();

        loadRoomURL();

        new KeyboardHeightProvider(this).init().setHeightListener((height, density, cutout) -> {
            evalJs("liveCloudNativeEventCallback({name:'keyboardHeight', payload:{height:" + (int)(height/density) + "}})");
            List<String> rects = new ArrayList<>();
            for (Rect c: cutout) {
                StringBuilder sb = new StringBuilder(32);
                sb.append("["); sb.append(c.left); sb.append(",");
                sb.append(c.top); sb.append(","); sb.append(c.right);
                sb.append(","); sb.append(c.bottom); sb.append("]");
                rects.add(sb.toString());
            }
            evalJs("liveCloudNativeEventCallback({name:'DisplayCutout', payload:{cutout:" +  Arrays.deepToString(rects.toArray()) + "}})");
            if (isFullscreen) {
                LiveCloudUtils.hideNavigationBar(this);
            }
        });

        enterTimestamp = System.currentTimeMillis();

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
    }

    @Override
    public void onBackPressed() {
        if (connect) {
            evalJs("liveCloudNativeEventCallback({name:'back'})");
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
        if (testUrl) {
            if (x5WebView != null) {
                x5WebView.loadUrl(url);
            } else {
                nativeWebView.loadUrl(url);
            }
            return;
        }
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
        if (!disableX5) {
//            WebView.setWebContentsDebuggingEnabled(true);
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
            x5WebView.setWebChromeClientExtension(createX5Extension());
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
//            android.webkit.WebView.setWebContentsDebuggingEnabled(true);
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

    private IX5WebChromeClientExtension createX5Extension() {
        return new IX5WebChromeClientExtension() {

            @Override
            public Object getX5WebChromeClientInstance() {
                return null;
            }

            @Override
            public View getVideoLoadingProgressView() {
                return null;
            }

            @Override
            public void onAllMetaDataFinished(IX5WebViewExtension ix5WebViewExtension, HashMap<String, String> hashMap) {

            }

            @Override
            public void onBackforwardFinished(int i) {

            }

            @Override
            public void onHitTestResultForPluginFinished(IX5WebViewExtension ix5WebViewExtension, IX5WebViewBase.HitTestResult hitTestResult, Bundle bundle) {

            }

            @Override
            public void onHitTestResultFinished(IX5WebViewExtension ix5WebViewExtension, IX5WebViewBase.HitTestResult hitTestResult) {

            }

            @Override
            public void onPromptScaleSaved(IX5WebViewExtension ix5WebViewExtension) {

            }

            @Override
            public void onPromptNotScalable(IX5WebViewExtension ix5WebViewExtension) {

            }

            @Override
            public boolean onAddFavorite(IX5WebViewExtension ix5WebViewExtension, String s, String s1, JsResult jsResult) {
                return false;
            }

            @Override
            public void onPrepareX5ReadPageDataFinished(IX5WebViewExtension ix5WebViewExtension, HashMap<String, String> hashMap) {

            }

            @Override
            public boolean onSavePassword(String s, String s1, String s2, boolean b, Message message) {
                return false;
            }

            @Override
            public boolean onSavePassword(ValueCallback<String> valueCallback, String s, String s1, String s2, String s3, String s4, boolean b) {
                return false;
            }

            @Override
            public void onX5ReadModeAvailableChecked(HashMap<String, String> hashMap) {

            }

            @Override
            public void addFlashView(View view, ViewGroup.LayoutParams layoutParams) {

            }

            @Override
            public void h5videoRequestFullScreen(String s) {

            }

            @Override
            public void h5videoExitFullScreen(String s) {

            }

            @Override
            public void requestFullScreenFlash() {

            }

            @Override
            public void exitFullScreenFlash() {

            }

            @Override
            public void jsRequestFullScreen() {

            }

            @Override
            public void jsExitFullScreen() {

            }

            @Override
            public void acquireWakeLock() {

            }

            @Override
            public void releaseWakeLock() {

            }

            @Override
            public Context getApplicationContex() {
                return null;
            }

            @Override
            public boolean onPageNotResponding(Runnable runnable) {
                return false;
            }

            @Override
            public Object onMiscCallBack(String s, Bundle bundle) {
                return null;
            }

            @Override
            public void openFileChooser(ValueCallback<Uri[]> valueCallback, String s, String s1) {

            }

            @Override
            public void onPrintPage() {

            }

            @Override
            public void onColorModeChanged(long l) {

            }

            @Override
            public boolean onPermissionRequest(String s, long l, MediaAccessPermissionsCallback mediaAccessPermissionsCallback) {
                permissionSite = s;
                permissionType = l;
                permissionsCallback = mediaAccessPermissionsCallback;
                if ((l & MediaAccessPermissionsCallback.BITMASK_RESOURCE_AUDIO_CAPTURE) != 0) {
                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
                } else if ((l & MediaAccessPermissionsCallback.BITMASK_RESOURCE_VIDEO_CAPTURE) != 0) {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA);
                }

                return true;
            }
        };
    }

    private WebChromeClient createX5ChromeClient() {
        return new WebChromeClient() {

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
                                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
                                break;
                            }
                            case PermissionRequest.RESOURCE_VIDEO_CAPTURE: {
                                requestPermissionLauncher.launch(Manifest.permission.CAMERA);
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

    private void evalJs(String js) {
        if (x5WebView != null) {
            x5WebView.evaluateJavascript(js, null);
        } else {
            nativeWebView.evaluateJavascript(js, null);
        }
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> runOnUiThread(() -> {
                if (isGranted) {
                    if (nativeRequest != null) {
                        nativeRequest.grant(nativeRequest.getResources());
                    } else if (permissionsCallback != null) {
                        permissionsCallback.invoke(permissionSite, permissionType, true);
                    }
                } else {
                    if (nativeRequest != null) {
                        nativeRequest.deny();
                    } else if (permissionsCallback != null) {
                        permissionsCallback.invoke(permissionSite, permissionType, false);
                    }
                }
            }));
}
