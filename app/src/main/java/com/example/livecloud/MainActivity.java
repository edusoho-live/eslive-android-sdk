package com.example.livecloud;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.codeages.eslivesdk.LiveCloudActivity;
import com.codeages.eslivesdk.LiveCloudHttpClient;
import com.codeages.eslivesdk.LiveCloudUtils;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().setTitle("EduSoho大班课测试入口");

        Button enter        = findViewById(R.id.enter);
        TextView btnDev     = findViewById(R.id.dev);
        TextView sdkVersion = findViewById(R.id.sdkVersion);
        sdkVersion.setText("SDK." + LiveCloudUtils.SDKVersion);

        EditText roomText = ((TextInputLayout) findViewById(R.id.roomId)).getEditText();
        EditText userText = ((TextInputLayout) findViewById(R.id.userName)).getEditText();

        SharedPreferences sharedPref = getSharedPreferences("LiveCloudDemoPref", MODE_PRIVATE);
        String roomId = sharedPref.getString("roomId", null);
        String userName = sharedPref.getString("userName", null);
        Objects.requireNonNull(roomText).setText(roomId);
        Objects.requireNonNull(userText).setText(userName);

        enter.setOnClickListener(view -> {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        });

        btnDev.setOnClickListener(v -> {
            startActivity(new Intent(this, DevActivity.class));
        });
    }

    private void enterAction() {
        String room  = ((TextInputLayout) findViewById(R.id.roomId)).getEditText().getText().toString().trim();
        String user  = ((TextInputLayout) findViewById(R.id.userName)).getEditText().getText().toString().trim();
        if (TextUtils.isEmpty(room) || TextUtils.isEmpty(user)) {
            return;
        }
        SharedPreferences sharedPref = getSharedPreferences("LiveCloudDemoPref", MODE_PRIVATE);
        Long userId = sharedPref.getLong("userId", Math.round(Math.random() * 900000000000L) + 100000000000L);
        String api = sharedPref.getString("api", "https://live-dev.edusoho.cn");

        if (!api.startsWith("http")) {
            api = "https://" + api;
        }
        if (api.endsWith("/")) {
            api = api.substring(0, api.length() - 1);
        }
        Map<String, Object> payload = new HashMap<String, Object>() {
            {
                put("roomId", room);
                put("userId", userId);
                put("userName", user);
                put("role", "viewer");
                put("accessKey", "flv_self_aliyun");
            }
        };
        String finalApi = api;
        LiveCloudHttpClient.post(api + "/api/dev/createRoomToken", new JSONObject(payload).toString(), 6000, (successMsg, errorMsg) -> {
            runOnUiThread(() -> {
                if (successMsg != null) {
                    try {
                        String token = (String) new JSONObject(successMsg).get("token");
                        String url = finalApi + "/h5/room/" + room + "/enter?inapp=1&token=" + token;
                        CheckBox enableX5   = findViewById(R.id.checkBox);
                        HashMap<String, Object> options = new HashMap<String, Object>() {
                            {
                                put("disableX5", !enableX5.isChecked());
                            }
                        };
                        LiveCloudActivity.launch(MainActivity.this, url, true, true, options);

                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putString("roomId", room);
                        editor.putString("userName", user);
                        editor.putLong("userId", userId);
                        editor.apply();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> runOnUiThread(() -> {
                enterAction();
            }));

}