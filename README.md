# ESLive-Android-SDK

[![](https://jitpack.io/v/edusoho-live/eslive-android-sdk.svg)](https://jitpack.io/#edusoho-live/eslive-android-sdk)

## 集成 SDK

1. 在项目根目录的 build.gradle 文件中，添加如下行：
   ```
   ...
   allprojects {
       repositories {
           ...
           maven { url 'https://www.jitpack.io' }
       }
   }
   ```
2. 在 app 的 `build.gradle` 文件添加如下依赖

   ```
   dependencies {
       ...

       implementation 'com.github.edusoho-live.eslive-android-sdk:final:1.0.0'
   }
   ```

## 添加项目权限

```java
//网络权限
<uses-permission android:name="android.permission.INTERNET" />
//开启连麦功能需授予权限
//targetSdkVersion 为23及以上时，需动态申请CAMERA、RECORD_AUDIO权限！！！
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 防止代码混淆

在 app/proguard-rules.pro 文件中添加如下行，防止混淆 ESLive SDK 的代码：

```
-keep class com.codeages.eslivesdk.**{*;}

-dontwarn dalvik.**
-dontwarn com.tencent.smtt.**

-keep class com.tencent.smtt.** {
    *;
}

-keep class com.tencent.tbs.** {
    *;
}
```

### 代码示例

```java
import com.codeages.eslivesdk.LiveCloudActivity;

    ......

    // 通过接口获取直播课堂 url
    String url = "";

    // 获取读写文件权限
    Boolean isGrantedPermission = true;

    // 直播
    LiveCloudActivity.launch(this, url, true, isGrantedPermission, null);

    // 回放
    LiveCloudActivity.launch(this, url, false, isGrantedPermission, null);
    ......

```
