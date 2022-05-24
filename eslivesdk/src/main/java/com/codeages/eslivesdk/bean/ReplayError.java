package com.codeages.eslivesdk.bean;

import lombok.Data;

@Data
public class ReplayError {
    public static final int    NOT_EXIST      = 10100;
    public static final int    DELETE_SUCCESS = 10101;
    private             int    code;
    private             String message;

    public ReplayError(int code) {
        this.code = code;
    }

    public ReplayError(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getMessage() {
        if (code == NOT_EXIST) {
            return "缓存不存在";
        } else if (code == DELETE_SUCCESS) {
            return "清空完成";
        }
        return message;
    }
}
