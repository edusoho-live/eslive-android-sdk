package com.codeages.livecloudsdk;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.UrlQuerySanitizer;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebIconDatabase;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;

import androidx.core.app.ActivityCompat;

import com.tencent.smtt.sdk.QbSdk;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static android.content.Context.MODE_PRIVATE;

public class LiveCloudUtils {

    private static final int SDKVersion = 1;

    private static final String ALLOWED_CHARACTERS = "0123456789qwertyuiopasdfghjklzxcvbnm";

    protected static String randomString(final int sizeOfRandomString) {
        final Random random = new Random();
        final StringBuilder sb = new StringBuilder(sizeOfRandomString);
        for (int i = 0; i < sizeOfRandomString; ++i) {
            sb.append(ALLOWED_CHARACTERS.charAt(random.nextInt(ALLOWED_CHARACTERS.length())));
        }
        return sb.toString();
    }

    protected static Map<String, Object> parseJwt(String url) {
        String token = new UrlQuerySanitizer(url).getValue("token");
        String[] chunks = token.split("\\.");
        if (chunks.length < 1) {
            return new HashMap<>();
        }

        String payload = new String(android.util.Base64.decode(chunks[1], android.util.Base64.URL_SAFE));
        try {
            JSONObject json = new JSONObject(payload);
            Map<String, Object> result = new HashMap<>();
            result.put("rid", json.get("rid"));
            result.put("role", json.get("role"));
            result.put("uid", json.get("uid"));
            result.put("name", json.get("name"));
            return result;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new HashMap<>();
    }

    protected static Map<String, Object> deviceInfo(Context context) {
        Map<String, Object> result = new HashMap<>();
        result.put("device", android.os.Build.MANUFACTURER + android.os.Build.MODEL);
        result.put("osName", "android");
        result.put("osVersion", android.os.Build.VERSION.RELEASE);
        result.put("network", getNetworkState(context));
        result.put("appName", context.getApplicationInfo().processName);
        result.put("appVersion", getAppVersion(context));
        result.put("sdkVersion", SDKVersion);
        result.put("resolution", context.getResources().getDisplayMetrics().widthPixels + "x"
                + context.getResources().getDisplayMetrics().heightPixels);

        return result;
    }

    protected static void checkClearCaches(Context context) {
        try {
            String nowVersion = getAppVersion(context);
            SharedPreferences sharedPref = context.getSharedPreferences("LiveCloudPref", MODE_PRIVATE);
            String lastVersion = sharedPref.getString("appVersion", null);
            if (lastVersion == null || !lastVersion.equals(nowVersion)) {
                deleteCache(context);
            }
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("appVersion", nowVersion);
            editor.apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected static void deleteCache(Context context) {
        Context appContext = context.getApplicationContext();
        QbSdk.clearAllWebViewCache(appContext, true);
        WebView webView = new WebView(appContext);
        webView.removeJavascriptInterface("searchBoxJavaBridge_");
        webView.removeJavascriptInterface("accessibility");
        webView.removeJavascriptInterface("accessibilityTraversal");
        webView.clearCache(true);
        CookieSyncManager.createInstance(appContext);
        CookieManager.getInstance().removeAllCookies(null);
        WebViewDatabase.getInstance(appContext).clearUsernamePassword();
        WebViewDatabase.getInstance(appContext).clearHttpAuthUsernamePassword();
        WebViewDatabase.getInstance(appContext).clearFormData();
        WebStorage.getInstance().deleteAllData();
        WebIconDatabase.getInstance().removeAllIcons();

        deleteDir(new File(appContext.getCacheDir(), "WebView"));
        deleteDir(new File(appContext.getCacheDir(), "org.chromium.android_webview"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            deleteDir(new File(appContext.getDataDir(), "app_webview"));
        }
//        deleteDir(context.getCacheDir());
//        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
//            deleteDir(context.getExternalCacheDir());
//        }
    }

    private static void deleteDir(File dir) {
        if (dir == null) {
            return;
        }
        if (dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    deleteDir(new File(dir, child));
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        dir.delete();
    }

    protected static int connectTimeout(Context context, String roomId) {
        SharedPreferences sharedPref = context.getSharedPreferences("LiveCloudPref", MODE_PRIVATE);
        int times = sharedPref.getInt(roomId, 0) + 1;
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(roomId, times);
        editor.apply();
        return times;
    }

    protected static int getTimeoutTimes(Context context, String roomId) {
        SharedPreferences sharedPref = context.getSharedPreferences("LiveCloudPref", MODE_PRIVATE);
        return sharedPref.getInt(roomId, 0);
    }

    private static String getNetworkState(Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (null == connManager) {
            return "none";
        }
        NetworkInfo activeNetInfo = connManager.getActiveNetworkInfo();
        if (activeNetInfo == null || !activeNetInfo.isAvailable()) {
            return "none";
        }
        NetworkInfo wifiInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (null != wifiInfo) {
            NetworkInfo.State state = wifiInfo.getState();
            if (null != state) {
                if (state == NetworkInfo.State.CONNECTED || state == NetworkInfo.State.CONNECTING) {
                    return "wifi";
                }
            }
        }
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return "cellular";
        }
        int networkType = telephonyManager.getNetworkType();
        switch (networkType) {
            /*
             GPRS : 2G(2.5) General Packet Radia Service 114kbps
             EDGE : 2G(2.75G) Enhanced Data Rate for GSM Evolution 384kbps
             UMTS : 3G WCDMA 联通3G Universal Mobile Telecommunication System 完整的3G移动通信技术标准
             CDMA : 2G 电信 Code Division Multiple Access 码分多址
             EVDO_0 : 3G (EVDO 全程 CDMA2000 1xEV-DO) Evolution - Data Only (Data Optimized) 153.6kps - 2.4mbps 属于3G
             EVDO_A : 3G 1.8mbps - 3.1mbps 属于3G过渡，3.5G
             1xRTT : 2G CDMA2000 1xRTT (RTT - 无线电传输技术) 144kbps 2G的过渡,
             HSDPA : 3.5G 高速下行分组接入 3.5G WCDMA High Speed Downlink Packet Access 14.4mbps
             HSUPA : 3.5G High Speed Uplink Packet Access 高速上行链路分组接入 1.4 - 5.8 mbps
             HSPA : 3G (分HSDPA,HSUPA) High Speed Packet Access
             IDEN : 2G Integrated Dispatch Enhanced Networks 集成数字增强型网络 （属于2G，来自维基百科）
             EVDO_B : 3G EV-DO Rev.B 14.7Mbps 下行 3.5G
             LTE : 4G Long Term Evolution FDD-LTE 和 TDD-LTE , 3G过渡，升级版 LTE Advanced 才是4G
             EHRPD : 3G CDMA2000向LTE 4G的中间产物 Evolved High Rate Packet Data HRPD的升级
             HSPAP : 3G HSPAP 比 HSDPA 快些
             */
            // 2G网络
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return "2G";
            // 3G网络
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "3G";
            // 4G网络
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "4G";
            default:
                return "cellular";
        }
    }

    private static String getAppVersion(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return "";
    }

}
