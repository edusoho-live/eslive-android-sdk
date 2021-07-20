# LiveCloudSDK-Android
[![](https://jitpack.io/v/codeages/livecloud-android-sdk.svg)](https://jitpack.io/#codeages/livecloud-android-sdk)

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
3. 在 app 的 `build.gradle` 文件添加如下依赖
    ```
    dependencies {
        ...

        implementation 'com.github.codeages.livecloud-android-sdk:final:0.1.6'
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

在 app/proguard-rules.pro 文件中添加如下行，防止混淆 LiveCloud SDK 的代码：

```
-keep class com.edusoho.livecloudsdk.**{*;}

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
import com.edusoho.livecloudsdk.LiveCloudActivity;

    ......

    // 通过接口获取直播课堂 url
    LiveCloudActivity.launch(this, url, null);

    ......
    
```
