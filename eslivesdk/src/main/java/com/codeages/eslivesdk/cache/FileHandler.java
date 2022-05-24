package com.codeages.eslivesdk.cache;

import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.PathUtils;
import com.codeages.eslivesdk.LiveCloudLocal;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.FileEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

import java.io.File;
import java.io.IOException;

public class FileHandler implements HttpRequestHandler {

    private int mRoomId;

    public FileHandler(int roomId) {
        this.mRoomId = roomId;
    }

    @Override
    public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
        try {
            String url = httpRequest.getRequestLine().getUri();
            LogUtils.d(url);
            String[] split1   = url.split("[.]");
            String   suffix   = split1.length > 1 ? split1[split1.length - 1] : "";
            String   filePath = "";
            if (url.contains(LiveCloudLocal.LIVE_CLOUD_PLAYER_PATH)) {
                filePath = PathUtils.getExternalAppFilesPath() + url;
            } else if (url.contains(LiveCloudLocal.LIVE_CLOUD_REPLAY_PATH)) {
                filePath = PathUtils.getExternalAppFilesPath() + url;
            }
            FileEntity fileEntity;
            String     contentType = "";
            File       file        = new File(filePath);
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
            }
            fileEntity = new FileEntity(file, contentType);
            httpResponse.setEntity(fileEntity);
        } catch (Exception ex) {
            LogUtils.d(ex.getMessage());
        }


//        if (url.startsWith(PLAYLIST_KEY)) {
//            Log.d("flag--", "handle: ");
//            M3U8Resource resource = mM3U8FileService.getM3U8Resource(mResNo);
//            StringEntity entity = new StringEntity(resource.getOfflineContent(), "utf-8");
//            entity.setContentType("application/vnd.apple.mpegurl");
//            entity.setContentEncoding("utf-8");
//            httpResponse.setEntity(entity);
//        } else if (url.startsWith("ext_x_key")) {
//            String resNoInUrl = filterResNoByKeyUrl(url);
//            String key = findKey(mResNo, resNoInUrl);
//            if (key == null) {
//                Log.e("flag--", "key not exists!");
//                key = "";
//            }
//            StringEntity entity = new StringEntity(key);
//            entity.setContentType("text/html");
//            entity.setContentEncoding("utf-8");
//            httpResponse.setEntity(entity);
//        } else if (url.contains("schoolId=") && url.contains("fileGlobalId=")) {
//            String m3u8SegmentFileName = findM3U8SegmentFileName(url);
//            File m3u8SegmentFile = findM3U8SegmentFile(mResNo, m3u8SegmentFileName);
//            if (m3u8SegmentFile == null) {
//                Log.e("playlist", "file not exists!");
//                m3u8SegmentFile = new File("");
//            }
//            FileEntity fileEntity = new FileEntity(m3u8SegmentFile, "video/mp2t");
//            httpResponse.setEntity(fileEntity);
//        }
    }

}
