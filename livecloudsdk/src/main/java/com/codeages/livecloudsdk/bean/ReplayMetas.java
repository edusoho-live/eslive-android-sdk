package com.codeages.livecloudsdk.bean;

import lombok.Data;

@Data
public class ReplayMetas {

    private int     roomId;
    private String  roomName;
    private String  version;
    private boolean showChat;
    private String  duration;
    private String  trafficTag;
    private String  dataUrl;
    private String  playerUrl;
    private String  videoBaseUri;
    private String  documentBaseUri;
    private String  playerBaseUri;
    private String  dataBaseUri;
    private int     status;

    public String getShowChat() {
        return showChat ? "1" : "0";
    }
}
