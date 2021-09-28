package com.codeages.livecloudsdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;


public class BackgroundMediaWebView extends WebView {
    private static final String JSInterface = "LiveCloudBridge";

    private String playInfo;

    @SuppressLint("SetJavaScriptEnabled")
    public BackgroundMediaWebView(Context context, String params) {
        super(context);
        this.playInfo = params;
        WebSettings webSettings = getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setDefaultTextEncodingName("utf-8");
        webSettings.setDomStorageEnabled(true);
        webSettings.setMixedContentMode(0);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        addJavascriptInterface(this, JSInterface);
    }

    public BackgroundMediaWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BackgroundMediaWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(View.VISIBLE);
    }

    @JavascriptInterface
    public void connect() {
        ((Activity) getContext()).runOnUiThread(()->
                evaluateJavascript("liveCloudNativeEventCallback(" + playInfo + ")", null));
    }

    public void getPlayedTime(ValueCallback<String> resultCallback) {
        ((Activity) getContext()).runOnUiThread(()->
                evaluateJavascript("liveCloudNativeEventCallback({name:'getPlayedTime', needReturn:true})", resultCallback));
    }

}