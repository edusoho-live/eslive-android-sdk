package com.codeages.eslivesdk.bean;

import java.util.List;

import lombok.Data;

@Data
public class ReplayMetaItem {
    private String       type;
    private String       name;
    private List<String> urls;
}
