package com.example.livecloud;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;

import com.codeages.eslivesdk.LiveCloudActivity;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Objects;

public class DevActivity extends AppCompatActivity {

    private static final String TAG       = "livecloud";
    private static final int    KEY       = 12306;
    private static final int    USER_ID   = 8;
    private static final String USER_NAME = "Jesse";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dev_main);
        Objects.requireNonNull(getSupportActionBar()).hide();

        Button enter             = findViewById(R.id.button);
        Button btnDownloadReplay = findViewById(R.id.btn_download);
        Button testEnter         = findViewById(R.id.testEnter);
        Switch type              = findViewById(R.id.type);
        CheckBox checkBox        = findViewById(R.id.checkBox1);
        EditText apiInput        = ((TextInputLayout) findViewById(R.id.apiText)).getEditText();
        EditText urlInput        = ((TextInputLayout) findViewById(R.id.testUrl)).getEditText();
        SharedPreferences sharedPref = getSharedPreferences("LiveCloudDemoPref", MODE_PRIVATE);
        apiInput.setText(sharedPref.getString("api", "https://live-dev.edusoho.cn"));
        urlInput.setText(sharedPref.getString("testUrl", "https://live-dev.edusoho.cn/h5/detection"));

        enter.setOnClickListener(view -> {
            String api   = apiInput.getText().toString();
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
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("api", api);
            editor.apply();

            String url = api + (type.isChecked() ? "/h5/room/" : "/h5/replay/") + room + "/enter?inapp=1&token=" + token;
            LiveCloudActivity.launch(DevActivity.this, url, type.isChecked(), true, null);
        });

        btnDownloadReplay.setOnClickListener(v -> {
            startActivity(new Intent(this, DownloadActivity.class));
        });

        testEnter.setOnClickListener(v -> {
            String url   = urlInput.getText().toString();
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("testUrl", url);
            editor.apply();
            HashMap<String, Object> options = new HashMap<String, Object>() {
                {
                    put("disableX5", !checkBox.isChecked());
                    put("testUrl", true);
                }
            };
            LiveCloudActivity.launch(DevActivity.this, url, type.isChecked(), true, new JSONObject(options).toString());
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
