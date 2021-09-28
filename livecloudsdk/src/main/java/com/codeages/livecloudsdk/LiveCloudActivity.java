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
import java.util.Base64;
import java.util.HashMap;
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
        String roomId = String.valueOf(LiveCloudUtils.parseJwt(url).get("rid"));
        intent.putExtra("roomId", roomId);
        if (options != null && options.get("logUrl") != null) {
            intent.putExtra("logUrl", (String) options.get("logUrl"));
        }

        ProgressDialog progressDialog = ProgressDialog.show(context, "", "加载中", true, true);

        LiveCloudUtils.checkClearCaches(context);

        initTbs(context);

        if (!isLive && QbSdk.getTbsVersion(context) < 45613) { // 倍速播放bug
            QbSdk.forceSysWebView();
            intent.putExtra("disableX5", true);

            context.startActivity(intent);
            progressDialog.dismiss();
            return;
        }

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
                LiveCloudUtils.disableX5(context, roomId);
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
                LiveCloudUtils.deleteCache(this);
                logger.warn("SDK.ConnectTimeout", roomId + "=" + times, null);
            } else {
                logger.warn("SDK.NotConnect", null, null);
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

        logger = new LiveCloudLogger(logUrl, url);

        createWebView();

        loadRoomURL();

        logger.info("SDK.Enter", new JSONObject(deviceInfoMap()).toString(), deviceInfoMap());

        if (!getIntent().getBooleanExtra("isGrantedPermission", true)) {
            logger.debug("SDK.PermissionDeny", null, null);
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
                logger.info("SDK.X5Installed", null, null);
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
        webSettings.setMixedContentMode(0);
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
                logger.debug("SDK.WebViewError", "WebResourceError: " + error.getErrorCode() + " " + error.getDescription(), null);
//                String x5CrashInfo = WebView.getCrashExtraMessage(view.getContext());
            }

            @Override
            public void onReceivedSslError(WebView webView, SslErrorHandler sslErrorHandler, SslError sslError) {
                sslErrorHandler.proceed();
                logger.debug("SDK.WebViewError", "sslError: " + sslError.getPrimaryError() + " " + sslError.getUrl(), null);
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
                    logger.error("SDK.WebViewError", "ConsoleMessage: " + consoleMessage.message(), null);
                }
                return super.onConsoleMessage(consoleMessage);
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
        runOnUiThread(() -> finish());

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
                        myRequest.grant(myRequest.getResources());
                    } else {
                        myRequest.deny();
                    }
                });
            });
}
