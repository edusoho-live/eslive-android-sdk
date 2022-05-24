package com.codeages.eslivesdk;

import android.os.CountDownTimer;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LiveCloudLogger {
    public static final String DEBUG = "DEBUG";
    public static final String INFO = "INFO";
    public static final String WARN = "WARN";
    public static final String ERROR = "ERROR";

    private static LiveCloudLogger logger;

    private final String mLogUrl;

    private final Map<String, Object> jwt;

    private List<Map<String, Object>> logs;

    private final String logIdPrefix = LiveCloudUtils.randomString(8) + "_";

    private long logId = 1;

    public static LiveCloudLogger getInstance(String logUrl, String roomUrl) {
        if (null == logger) {
            synchronized (LiveCloudLogger.class) {
                logger = new LiveCloudLogger(logUrl, roomUrl);
            }
        }
        return logger;
    }

    public static LiveCloudLogger getInstance(Long roomId, Long userId, String userName, String logUrl) {
        if (null == logger) {
            synchronized (LiveCloudLogger.class) {
                logger = new LiveCloudLogger(roomId, userId, userName, logUrl);
            }
        }
        return logger;
    }

    private LiveCloudLogger(String logUrl, String roomUrl) {
        mLogUrl = logUrl != null ? logUrl : "https://live-log.edusoho.com/collect";
        jwt = LiveCloudUtils.parseJwt(roomUrl);
        logs = new ArrayList<>();
    }

    private LiveCloudLogger(Long roomId, Long userId, String userName, String logUrl) {
        mLogUrl = logUrl != null ? logUrl : "https://live-log.edusoho.com/collect";
        jwt = new HashMap<String, Object>(){{
            put("rid", roomId);
            put("uid", userId);
            put("name", userName);
        }};
        logs = new ArrayList<>();
    }

    protected void debug(String action, String message, Map<String, Object> context) {
        pushLog(DEBUG, action, message, context);
    }

    protected void info(String action, String message, Map<String, Object> context) {
        pushLog(INFO, action, message, context);
    }

    protected void warn(String action, String message, Map<String, Object> context) {
        pushLog(WARN, action, message, context);
    }

    protected void error(String action, String message, Map<String, Object> context) {
        pushLog(ERROR, action, message, context);
    }

    private void pushLog(String level, String action, String message, Map<String, Object> context) {
        String dateString = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault())
                .format(new Date());

        Map<String, Object> log = new HashMap<>();
        log.put("@timestamp", dateString);
        log.put("message", "[event] @(" + action + ")" + (message != null ? (", " + message) : ""));
        log.put("event", new HashMap<String, String>() {
            {
                put("action", action);
                put("dataset", "livecloud.client");
                put("id", logIdPrefix + logId);
                put("kind", "event");
            }
        });
        log.put("log", new HashMap<String, String>() {
            {
                put("level", level);
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
                put("name", jwt.get("name") != null ? jwt.get("name") : "");
                put("roles", new Object[]{jwt.get("role") != null ? jwt.get("role") : ""});
            }
        });
        if (context != null) {
            log.putAll(context);
        }
        logId ++;

        if (logs.size() < 500) {
            logs.add(log);
        }
        countDownTimer.start();
    }


    private final CountDownTimer countDownTimer = new CountDownTimer(10000, 10000) {

        @Override
        public void onTick(long l) {

        }

        @Override
        public void onFinish() {
            postLog();
        }
    };

    private void postLog() {
        if (logs.size() == 0) {
            return;
        }
        int countNum = Math.min(logs.size(), 100);
        String dateString = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault())
                .format(new Date());
        Map<String, Object> payload = new HashMap<String, Object>() {
            {
                put("@timestamp", dateString);
                put("logs", logs.subList(0, countNum));
            }
        };
        logs = logs.subList(countNum, logs.size());
        LiveCloudHttpClient.post(mLogUrl, new JSONObject(payload).toString(), 6000, null);
    }
}
