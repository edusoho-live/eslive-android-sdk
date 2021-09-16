package com.codeages.livecloudsdk;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.tencent.smtt.sdk.WebView;

public class BackgroundMediaWebView extends WebView {


    public BackgroundMediaWebView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(View.VISIBLE);
    }

}