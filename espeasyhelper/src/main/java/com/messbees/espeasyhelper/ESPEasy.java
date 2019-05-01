package com.messbees.espeasyhelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.CountDownTimer;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.content.Context.WIFI_SERVICE;

public class ESPEasy {

    public interface ConnectionListener {
        void onConnect();
        void onFail();
    }
    public interface SetupListener {
        void onSuccess();
        void onCheckTick();
        void onFail(String errorMessage);
    }
    public interface UploadListener {
        void onUpload();
        void onFail(String message);
    }
    public interface JsonListener {
        void onResponse(JSONObject jsonObject);
        void onFail(String message);
    }

    private Context context;
    private String ip;
    private WifiManager wifiManager;
    private String SSID;
    private RequestQueue queue;


    public ESPEasy(Context context) {
        this.context = context;
        this.ip = "192.168.4.1";
        this.SSID = "ESP_Easy_0";
        wifiManager = (WifiManager)
                context.getApplicationContext().getSystemService(WIFI_SERVICE);
        queue = Volley.newRequestQueue(context);
    }


    /**
     * Sets IP address
     * @param ip IP address
     */
    public void setIp(String ip) {
        this.ip = ip;
    }


    /**
     * @return currently defined IP address
     */
    public String getIp() {
        return ip;
    }


    /**
     * Uploads config.dat file to ESP
     * @param data config.dat as byte[]
     * @param listener Upload listener
     */
    public void uploadConfig(byte[] data, final UploadListener listener) {
        String fileName = "config.dat";
        uploadFile(data, fileName, listener);
    }


    /**
     * Uploads rules file to ESP
     * @param data rules1.txt as byte[]
     * @param listener Upload listener
     */
    public void uploadRules(byte[] data, final UploadListener listener) {
        String fileName = "rules1.txt";
        uploadFile(data, fileName, listener);
    }


    /**
     * Uploads firmware file to ESP
     * @param data firmware.bin as byte[]
     * @param listener Upload listener
     */
    public void uploadFirmware(byte[] data, final UploadListener listener) {
        String fileName = "firmware.bin";
        uploadFile(data, fileName, listener);
    }


    /**
     * Gets ESP data
     * @param listener Response listener
     */
    void getJson(final JsonListener listener) {
        String url = "http:/" + "/" + ip + "/json";

        StringRequest stringRequest = new StringRequest(Request.Method.POST,
                url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONObject json = new JSONObject(response);
                            listener.onResponse(json);
                        } catch (JSONException e) {
                            listener.onFail(e.getMessage());
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        if (error.getMessage() == null) {
                            String errorString = "";
                            if(error instanceof TimeoutError) {
                                errorString = "Connection time out";
                                listener.onFail(errorString);
                            }
                        }
                    }
                }) {

        };
        queue.add(stringRequest);
    }


    /**
     * Connects device to ESPEasy AP
     * @param listener Connection listener
     */
    public void connectToEsp(ConnectionListener listener) {
        WifiConfiguration wifiConfiguration = new WifiConfiguration();


        wifiConfiguration.SSID = "\"" + SSID + "\"";
        wifiConfiguration.preSharedKey = "\"" + "configesp" + "\"";
        int networkID = wifiManager.addNetwork(wifiConfiguration);

        if (networkID != -1) {
            connectToWifi(networkID, listener);
        } else {
            int newId = getExistingNetworkId(SSID);
            connectToWifi(newId, listener);
        }
    }


    /**
     * Performs ESPEasy WiFi setup
     *
     * @param SSID SSID
     * @param password WPA key
     * @param listener Setup listener
     */
    public void setupEsp(final String SSID, final String password, final SetupListener listener) {
        String url = "http:/" + "/" + ip + "/setup";
        StringRequest stringRequest = new StringRequest(Request.Method.POST,
                url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        CountDownTimer timer = new CountDownTimer(30000, 1000) {
                            @Override public void onTick(long millisUntilFinished) { listener.onCheckTick(); }
                            @Override public void onFinish() { checkSetup(listener); }
                        };
                        timer.start();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        if (error.networkResponse == null) {
                            String errorString = "";
                            if(error instanceof TimeoutError) {
                                errorString = "Connection time out";
                            }
                            else if (error instanceof ServerError)
                                errorString = "Server error";
                            else
                                errorString = "Unknown error";
                            listener.onFail(errorString);
                        } else {
                            String responseCode = Integer.toString(error.networkResponse.statusCode);
                            listener.onFail("HTTP code is " + responseCode);
                        }
                    }
                }) {
            @Override
            public String getBodyContentType() {
                return "application/x-www-form-urlencoded; charset=UTF-8";
            }

            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("ssid", "other");
                params.put("other", SSID);
                params.put("pass", password);
                return params;
            }
        };
        queue.add(stringRequest);
    }

    /**
     * Uploads file to ESP
     * @param data byte[]
     * @param fileName file name (must be config.dat, rules1.txt or firmware.bin)
     * @param listener Response listener
     */
    private void uploadFile(byte[] data, String fileName, final UploadListener listener) {
        String url = "http:/" + "/" + ip + "/upload";
        String twoHyphens = "--";
        String lineEnd = "\r\n";
        String boundary = "apiclient-" + System.currentTimeMillis();
        final String mimeType = "multipart/form-data;boundary=" + boundary;
        final byte[] multipartBody;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(bos);
        try {
            dataOutputStream.writeBytes(twoHyphens + boundary + lineEnd);
            dataOutputStream.writeBytes(
                    "Content-Disposition: form-data; name=\"uploaded_file\"; filename=\""
                            + fileName + "\"" + lineEnd);
            dataOutputStream.writeBytes(lineEnd);

            ByteArrayInputStream fileInputStream = new ByteArrayInputStream(data);
            int bytesAvailable = fileInputStream.available();

            int maxBufferSize = 1024 * 1024;
            int bufferSize = Math.min(bytesAvailable, maxBufferSize);
            byte[] buffer = new byte[bufferSize];
            int bytesRead = fileInputStream.read(buffer, 0, bufferSize);
            while (bytesRead > 0) {
                dataOutputStream.write(buffer, 0, bufferSize);
                bytesAvailable = fileInputStream.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);
            }
            dataOutputStream.writeBytes(lineEnd);
            dataOutputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

            multipartBody = bos.toByteArray();

            StringRequest request = new StringRequest(
                    Request.Method.POST,
                    url,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            if (response.contains("Upload OK!")) {
                                listener.onUpload();
                            } else {
                                listener.onFail("Error loading config");
                            }
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    if (error.networkResponse == null) {
                        String errorString = "";
                        if(error instanceof TimeoutError) {
                            errorString = "Time out";
                        }
                        else if (error instanceof ServerError)
                            errorString = "Server error";
                        else
                            errorString = "Unknown error";
                        listener.onFail(errorString);
                    } else {
                        listener.onFail(error.getMessage());
                    }
                }
            }) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    return super.getHeaders();
                }

                @Override
                public String getBodyContentType() {
                    return mimeType;
                }

                @Override
                public byte[] getBody() {
                    return multipartBody;
                }
            };
            queue.add(request);
        } catch (IOException e) {
            listener.onFail(e.getMessage());
        }
    }


    /**
     * Gets AP default gateway
     * @return IP address of default gateway
     */
    private String getDefaultGateway() {
        int ipAddress = wifiManager.getDhcpInfo().gateway;
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray)
                    .getHostAddress();
        } catch (UnknownHostException ex) {
            ipAddressString = "NaN";
        }
        return ipAddressString;
    }


    /**
     * Checks if ESP connected to network
     *
     * @param listener response listener
     */
    private void checkSetup(final SetupListener listener) {
        String url = "http:/" + "/" + ip + "/setup";

        StringRequest stringRequest = new StringRequest(Request.Method.POST,
                url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        if (response.contains("ESP is connected")) {
                            listener.onSuccess();
                            ip = getDefaultGateway();
                        } else {
                            listener.onFail("Wrong SSID or password!");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        if (error.networkResponse == null) {
                            String errorString = "";
                            if(error instanceof TimeoutError) {
                                errorString = "Connection time out";
                            }
                            else if (error instanceof ServerError)
                                errorString = "Server error";
                            else
                                errorString = "Unknown error";
                            listener.onFail(errorString);
                        } else {
                            String responseCode = Integer.toString(error.networkResponse.statusCode);
                            listener.onFail("HTTP code is " + responseCode);
                        }
                    }
                });
        queue.add(stringRequest);
    }


    /**
     * Returns networkId of network that was previously configured.
     *
     * @param SSID SSID
     * @return networkId
     */
    private int getExistingNetworkId(String SSID) {
        List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
        if (configuredNetworks != null) {
            for (WifiConfiguration existingConfig : configuredNetworks) {
                if (SSID.contains(existingConfig.SSID) || existingConfig.SSID.contains(SSID)) {
                    return existingConfig.networkId;
                }
            }
        }
        return -1;
    }


    /**
     * Applies connection to WiFi network.
     *
     * @param networkID networkId
     * @param listener connection listener
     */
    private void connectToWifi(int networkID, final ConnectionListener listener) {
        BroadcastReceiver wifiChangedReceiver = new BroadcastReceiver() {
            private boolean firstTime = true;

            @Override
            public void onReceive(Context context, Intent intent) {
                if(firstTime) {
                    firstTime = false;
                    return;
                }
                if (intent.getAction() == null) {
                    return;
                }
                if(intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                    WifiManager wifiManager =
                            (WifiManager) context.getApplicationContext()
                                    .getSystemService (Context.WIFI_SERVICE);
                    NetworkInfo networkInfo =
                            intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    if(networkInfo.isConnected()) {
                        WifiInfo info = wifiManager.getConnectionInfo ();
                        String ssid  = info.getSSID();
                        if ((ssid != null) && (ssid.contains(SSID)))
                            listener.onConnect();
                        else
                            listener.onFail();
                        context.unregisterReceiver(this);
                    }
                }
            }
        };
        context.registerReceiver(
                wifiChangedReceiver,
                new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
        wifiManager.disconnect();
        wifiManager.enableNetwork(networkID, true);
        wifiManager.reconnect();
    }
}
