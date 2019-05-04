# Welcome to ESPEasyHelper!

This library will help you to communicate with your ESPEasy device.
It can:
  - Connect to ESPEasy device
  - Do WiFi setup
  - Upload files such as rules/config/firmware
  - Get Json data
  - Do Factory reset

# Installation

**Step 1.** Add the JitPack repository to your build file
```
allprojects {
	repositories {
		...
		maven { url 'https://jitpack.io' }
	}
}
```
**Step 2.**  Add the dependency

```
dependencies {
        implementation 'com.github.messbees:ESPEasyAndroid:v1.1.0'
}
```
## Connect your Android to ESPEasy device
Use this to connect to ESPEasy:
```java
ESPEasy mESPEasy = new ESPEasy(getContext());  
mESPEasy.connectToEsp(new ConnectionListener() {  
    @Override  
    public void onConnect() {  
        Toast.makeText(getContext(), "Successfully connected!", Toast.LENGTH_LONG).show();  
        // do something  
    }  
  
    @Override  
    public void onFail() {  
        Toast.makeText(getContext(), "Can't connect!", Toast.LENGTH_LONG).show();  
    }  
});
```
## Setup WiFi
This method will send setup request to ESPEasy with SSID and key of your specified network and will check connection after 30 seconds (like ESPEasy web-interface do).
```java
String SSID = "Keenetic Lite";  
String key = "12345678";  
  
final ProgressBar progressBar = findViewById(R.id.setup_progress_bar);  
  
mESPEasy.setupEsp(SSID, key, new SetupListener() {  
    @Override  
	public void onSuccess() {  
        Toast.makeText(getContext(), "setup sent!", Toast.LENGTH_LONG).show();  
    }  
  
    @Override  
    public void onCheckTick() {  
        progressBar.incrementProgressBy(1);  
    }   
  
    @Override  
    public void onFail(String errorMessage) {  
        Toast.makeText(getContext(), String.format("setup failed: %s", errorMessage), Toast.LENGTH_LONG).show();  
    }  
});
```
After successful setup method getIp() will return IP address of ESPEasy device in new network.

## Get JSON

```java
mESPEasy.getJson(new JsonListener() {
            @Override
            public void onResponse(JSONObject jsonObject) {
                try {
                    String ip = jsonObject
                            .getJSONObject("WiFi")
                            .getString("IP");
					// do something
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFail(Exception e) {
                Log.e(TAG, e.getMessage());
            }
        });
```

## Factory Reset

```java
mESPEasy.factoryReset(new ResponseListener() {
            @Override
            public void onResponse() {
				Log.d(TAG, "Successful reset");
            }

            @Override
            public void onFail(Exception e) {
                Log.e(TAG, e.getMessage());
            }
        });
```

## Upload files

You can upload firmware, config or rules to ESPEasy using this methods:
```java
public void uploadConfig(byte[] data, final UploadListener listener)
public void uploadRules(byte[] data, final UploadListener listener) 
public void uploadFirmware(byte[] data, final UploadListener listener)
```
