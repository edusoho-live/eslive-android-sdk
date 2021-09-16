package com.codeages.livecloudsdk.bean;

import java.util.List;

import lombok.Data;

@Data
public class ParseReplayResult {

    private PlayerInfo           playerInfo;
    private ReplayMetas          replayMetas;
    private List<ReplayMetaItem> replayMetaItems;
    private boolean              sign;
    public ParseReplayResult(PlayerInfo playerInfo, ReplayMetas replayMetas, List<ReplayMetaItem> replayMetaItems, boolean sign) {
        this.playerInfo = playerInfo;
        this.replayMetas = replayMetas;
        this.replayMetaItems = replayMetaItems;
        this.sign = sign;
    }

}
