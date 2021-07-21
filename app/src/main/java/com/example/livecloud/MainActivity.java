package com.example.livecloud;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.codeages.livecloudsdk.LiveCloudActivity;
import com.google.android.material.textfield.TextInputLayout;

public class MainActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button enter = findViewById(R.id.button);
        enter.setOnClickListener(view -> {
            String api = ((TextInputLayout)findViewById(R.id.apiText)).getEditText().getText().toString();
            String room = ((TextInputLayout)findViewById(R.id.roomText)).getEditText().getText().toString();
            String token  = ((TextInputLayout)findViewById(R.id.tokenText)).getEditText().getText().toString();
            if (TextUtils.isEmpty(api) || TextUtils.isEmpty(room) || TextUtils.isEmpty(token)) {
                return;
            }
            if (!api.startsWith("http")) {
                api = "https://" + api;
            }
            if (api.endsWith("/")) {
                api = api.substring(0, api.length() - 1);
            }
            String url = api + "/h5/room/" + room + "/enter?inapp=1&token=" + token;
            LiveCloudActivity.launch(MainActivity.this, url, true, null);
        });
    }


}