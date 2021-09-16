package com.example.livecloud;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.codeages.livecloudsdk.LiveCloudActivity;
import com.codeages.livecloudsdk.LiveCloudSDK;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class MainActivity extends AppCompatActivity {


    private static final String TAG       = "livecloud";
    private static final int    KEY       = 12306;
    private static final int    USER_ID   = 8;
    private static final String USER_NAME = "Jesse";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        LiveCloudSDK.init(getApplicationContext());

        Button enter             = findViewById(R.id.button);
        Button btnDownloadReplay = findViewById(R.id.btn_download);
        Switch type              = findViewById(R.id.type);
        enter.setOnClickListener(view -> {
            String api   = ((TextInputLayout) findViewById(R.id.apiText)).getEditText().getText().toString();
            String room  = ((TextInputLayout) findViewById(R.id.roomText)).getEditText().getText().toString();
            String token = ((TextInputLayout) findViewById(R.id.tokenText)).getEditText().getText().toString();
            if (TextUtils.isEmpty(api) || TextUtils.isEmpty(room) || TextUtils.isEmpty(token)) {
                return;
            }
            if (!api.startsWith("http")) {
                api = "https://" + api;
            }
            if (api.endsWith("/")) {
                api = api.substring(0, api.length() - 1);
            }
            String url = api + (type.isChecked() ? "/h5/room/" : "/h5/replay/") + room + "/enter?inapp=1&token=" + token;

            try {
                int permission = ActivityCompat.checkSelfPermission(this, "android.permission.WRITE_EXTERNAL_STORAGE");
                if (permission != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE}, 1);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            LiveCloudActivity.launch(MainActivity.this, url, type.isChecked(), true, null);
        });

        btnDownloadReplay.setOnClickListener(v -> {
            startActivity(new Intent(this, DownloadActivity.class));
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


}