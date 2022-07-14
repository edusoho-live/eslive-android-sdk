package com.codeages.eslivesdk.bean;

import java.io.Serializable;

public class CloudOptions implements Serializable {

    private boolean enable;
    private String  watermark;
    private String logUrl;
    private boolean disableX5;
    private boolean testUrl;

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public String getWatermark() {
        return watermark;
    }

    public void setWatermark(String watermark) {
        this.watermark = watermark;
    }

    public String getLogUrl() {
        return logUrl;
    }

    public void setLogUrl(String logUrl) {
        this.logUrl = logUrl;
    }

    public boolean isDisableX5() {
        return disableX5;
    }

    public void setDisableX5(boolean disableX5) {
        this.disableX5 = disableX5;
    }

    public boolean isTestUrl() {
        return testUrl;
    }

    public void setTestUrl(boolean testUrl) {
        this.testUrl = testUrl;
    }
}
