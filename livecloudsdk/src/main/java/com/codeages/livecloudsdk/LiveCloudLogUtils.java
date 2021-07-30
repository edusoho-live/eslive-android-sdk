package com.codeages.livecloudsdk;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LiveCloudLogUtils {
    private final String mLogUrl;

    private final String mUrl;

    public LiveCloudLogUtils(String logUrl, String roomUrl) {
        mLogUrl = logUrl;
        mUrl = roomUrl;
    }


    protected void collectDeviceLog(Map<String, Object> device) {
        Map<String, Object> logData = new HashMap<String, Object>() {
            {
                put("message", "[event] @(SDK.Enter), " + new JSONObject(device).toString());
                put("device", device);
            }
        };
        postLog("SDK.Enter", logData);
    }

    protected void collectX5Installed() {
        Map<String, Object> logData = new HashMap<String, Object>() {
            {
                put("message", "[event] @(SDK.X5Installed)");
            }
        };
        postLog("SDK.X5Installed", logData);
    }

    protected void collectPermissionDeny() {
        Map<String, Object> logData = new HashMap<String, Object>() {
            {
                put("message", "[event] @(SDK.PermissionDeny)");
            }
        };
        postLog("SDK.PermissionDeny", logData);
    }

    protected void postLog(String action, Map<String, Object> logData) {
        String dateString = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault())
                .format(new Date());
        Map<String, Object> jwt = LiveCloudUtils.parseJwt(mUrl);

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


        LiveCloudHttpClient.post(mLogUrl, new JSONObject(payload).toString(), null);
    }
}
