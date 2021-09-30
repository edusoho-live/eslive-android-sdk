package com.example.livecloud;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.codeages.livecloudsdk.LiveCloudSDK;
import com.codeages.livecloudsdk.ReplayListener;
import com.codeages.livecloudsdk.bean.ReplayError;
import com.codeages.livecloudsdk.bean.ReplayMetas;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class DownloadActivity extends AppCompatActivity {


    private static final String TAG       = "livecloud";
    private static final int    KEY       = 12306;
    private static final int    USER_ID   = 8;
    private static final String USER_NAME = "Jesse";

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download_main);
        LiveCloudSDK.init(getApplicationContext());

        Button      btnDownloadReplay = findViewById(R.id.btn_donwload_replay);
        Button      btnDeleteReplay   = findViewById(R.id.btn_delete_replay);
        Button      btnUpdatePlayer   = findViewById(R.id.btn_update_player);
        Button      playBtn           = findViewById(R.id.btn_play);
        Button      btnPause          = findViewById(R.id.btn_pause_replay);
        TextView    tvSpeed           = findViewById(R.id.tv_speed);
        ProgressBar progressBar       = findViewById(R.id.progressBar);
        TextView    tvReplaySize      = findViewById(R.id.tv_replay_size);

        btnDownloadReplay.setOnClickListener(v -> {
            String downloadUrl = "https://live-dev.edusoho.cn/apiapp/replay/getOfflineMetas";
            new LiveCloudSDK.Builder()
                    .setToken(getToken())
                    .setUserId(USER_ID + "")
                    .setUsername(USER_NAME)
                    .setKey(KEY + "")
                    .setDownloadUrl(downloadUrl)
                    .setReplayListener(new ReplayListener() {
                        @Override
                        public void onPlayerReady(ReplayMetas metas, long totalLength) {
                            runOnUiThread(() -> {
                                Toast.makeText(DownloadActivity.this, R.string.update_player, Toast.LENGTH_LONG).show();
                                Log.d(TAG, "onPlayerReady: totalLength:" + totalLength);
                            });
                        }

                        @Override
                        public void onPlayerProgress(ReplayMetas metas, long currentOffset, String speed) {
                            Log.d(TAG, "onPlayerProgress: " + currentOffset);
                        }

                        @Override
                        public void onPlayerFinish(ReplayMetas metas) {
                            Toast.makeText(DownloadActivity.this, R.string.update_player_finished, Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onStart(ReplayMetas metas) {

                        }

                        @Override
                        public void onReady(ReplayMetas metas, long totalLength) {
                            progressBar.setMax((int) totalLength);
                            Log.d(TAG, "onReady: " + totalLength);
                        }

                        @Override
                        public void onProgress(ReplayMetas metas, long currentOffset, String speed) {
                            tvSpeed.setText(speed);
                            progressBar.setProgress((int) currentOffset);
                            Log.d(TAG, "onProgress: " + currentOffset + "| speed: " + speed);
                        }

                        @Override
                        public void onFinish(ReplayMetas metas) {
                            Toast.makeText(DownloadActivity.this, "回放下载完成", Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onError(ReplayError error) {
                            Toast.makeText(DownloadActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    })
                    .build()
                    .startFetchReplay();
        });

        btnPause.setOnClickListener(v -> {
            new LiveCloudSDK.Builder()
                    .setKey(KEY + "")
                    .setUserId(USER_ID + "")
                    .setReplayListener(new ReplayListener() {
                        @Override
                        public void onError(ReplayError error) {
                            Toast.makeText(DownloadActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    })
                    .build()
                    .cancelFetchReplay();
        });

        btnDeleteReplay.setOnClickListener(v -> {
            new LiveCloudSDK.Builder()
                    .setKey(KEY + "")
                    .setUserId(USER_ID + "")
                    .setReplayListener(new ReplayListener() {
                        @Override
                        public void onError(ReplayError error) {
                            Toast.makeText(DownloadActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    })
                    .build()
                    .deleteReplay();
        });

        btnUpdatePlayer.setOnClickListener(v -> {
            String token = "eyJraWQiOiJmbHZfc2VsZl9hbGl5dW4iLCJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1aWQiOjkwMDAwMDAwMDAwMDAwMjIsInJvbGUiOiJ2aWV3ZXIiLCJpc3MiOiJsaXZlIGNsaWVudCBhcGkiLCJuYW1lIjoidGVzdC0yMiIsInJpZCI6MjI5NTYsInR5cGUiOiJsYW5kc2NhcGUiLCJleHAiOjE2NDA5NjY0MDB9.fTUuM8s7F5lu3LqTAha_GE1hQgfTrJvwcODvMhlwKmQ";
            new LiveCloudSDK.Builder()
                    .setToken(token)
                    .setReplayListener(new ReplayListener() {
                        @Override
                        public void onPlayerReady(ReplayMetas metas, long totalLength) {
                            runOnUiThread(() -> {
                                Toast.makeText(DownloadActivity.this, R.string.update_player, Toast.LENGTH_LONG).show();
                                Log.d(TAG, "onPlayerReady: totalLength:" + totalLength);
                            });
                        }

                        @Override
                        public void onPlayerProgress(ReplayMetas metas, long currentOffset, String speed) {
                            Log.d(TAG, "onPlayerProgress: " + currentOffset);
                        }

                        @Override
                        public void onPlayerFinish(ReplayMetas metas) {
                            Toast.makeText(DownloadActivity.this, R.string.update_player_finished, Toast.LENGTH_LONG).show();
                            Log.d(TAG, "onPlayerFinish: ");
                        }
                    })
                    .build()
                    .updatePlayer();
        });

        playBtn.setOnClickListener(v -> {
            new LiveCloudSDK.Builder()
                    .setUserId(USER_ID + "")
                    .setUsername(USER_NAME)
                    .setKey(KEY + "")
                    .setReplayListener(new ReplayListener() {
                        @Override
                        public void onError(ReplayError error) {
                            Toast.makeText(DownloadActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                            Log.d(TAG, "onPlayerFinish: ");
                        }
                    })
                    .build()
                    .playOfflineReplay(this);
        });
    }

    private long size(File dir) {
        long   count = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    System.out.println(f.getName());
                    count++;
                } else if (f.isDirectory()) {
                    count = count + size(f);
                }
            }
        }
        return count;
    }

    private String getToken() {
        StringBuilder result = new StringBuilder();
        try {
            InputStreamReader inputReader = new InputStreamReader(getResources().getAssets().open("token.txt"));
            BufferedReader    bufReader   = new BufferedReader(inputReader);
            String            line        = "";
            while ((line = bufReader.readLine()) != null) {
                result.append(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result.toString();
    }
}