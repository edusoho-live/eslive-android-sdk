package com.codeages.eslivesdk.server;

import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.PathUtils;
import com.codeages.eslivesdk.LiveCloudLocal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import fi.iki.elonen.NanoHTTPD;

public class HttpServer extends NanoHTTPD {
    public HttpServer(int port) {
        super(port);
    }


    @Override
    public Response serve(IHTTPSession session) {
        NanoHTTPD.Response response = null;
        try {
            String url = session.getUri();
            LogUtils.d(url);
            String[] split1   = url.split("[.]");
            String   suffix   = split1.length > 1 ? split1[split1.length - 1] : "";
            String   filePath = "";
            if (url.contains(LiveCloudLocal.LIVE_CLOUD_PLAYER_PATH)) {
                filePath = PathUtils.getExternalAppFilesPath() + url;
            } else if (url.contains(LiveCloudLocal.LIVE_CLOUD_REPLAY_PATH)) {
                filePath = PathUtils.getExternalAppFilesPath() + url;
            }
            String contentType = "";
            File   file        = new File(filePath);
            switch (suffix) {
                case "txt":
                    contentType = "text/plain";
                    break;
                case "html":
                    contentType = "text/html";
                    break;
                case "js":
                    contentType = "application/x-javascript";
                    break;
                case "ico":
                    contentType = "image/x-icon";
                    break;
                case "m3u8":
                    contentType = "application/vnd.apple.mpegurl";
                    break;
                case "ts":
                    contentType = "video/mp2t";
                    break;
                case "png":
                    contentType = "image/png";
                    break;
                default:
                    contentType = "image/png";
                    LogUtils.d("other:" + url);
                    break;
            }
            FileInputStream targetStream = new FileInputStream(file);
            response = new NanoHTTPD.Response(Response.Status.OK, contentType, targetStream, targetStream.available()) {
                @Override
                public void close() throws IOException {
                    super.close();
                }
            };
        } catch (Exception ex) {
            LogUtils.d(ex.getMessage());
        }

        return response;
    }
}
