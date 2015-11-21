package de.uzl.itm.ncoap.android.pingclient;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.message.CoapMessage;
import de.uniluebeck.itm.ncoap.message.CoapResponse;


public class PingClient extends Activity implements View.OnClickListener{

    private static final int STEP = 10;

    private boolean started;

    //Network Info
    private ConnectionData connectionData;

    //Text Views
    private EditText txtServer;
    private EditText txtPort;
    private TextView txtTime;
    private EditText txtResponses;

    private TextView txtNetwork;
    private TextView txtClientIP;

    //Buttons
    private Button btnStart;
    private Button btnStop;


    private CoapClientApplication coapClient;
    private Handler handler;
    private TimerTask timerTask;
    private PingTask.PongCallback pongCallback;
    private int time;

    private int currentDelay;
    private int attempts;

    //Server data
    private String server;
    private int port;
    private InetSocketAddress serverSocket;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ping_client);

        this.handler = new Handler();
        this.coapClient = new CoapClientApplication();
        ConnectivityChangeReceiver connectivityChangeReceiver = new ConnectivityChangeReceiver();

        // register the receiver
        registerReceiver(connectivityChangeReceiver, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));


        //Views
        this.txtServer = (EditText) findViewById(R.id.txt_server);
        this.txtPort = (EditText) findViewById(R.id.txt_port);
        this.txtTime = (TextView) findViewById(R.id.txt_time);
        this.txtResponses = (EditText) findViewById(R.id.txt_responses);
        this.btnStart = (Button) findViewById(R.id.btn_start);
        this.btnStop = (Button) findViewById(R.id.btn_stop);

        this.txtNetwork = (TextView) findViewById(R.id.txt_network);
        this.txtClientIP = (TextView) findViewById(R.id.txt_client_ip);

        //Register button listeners
        this.btnStart.setOnClickListener(this);
        this.btnStop.setOnClickListener(this);

        //Keep the screen always on...
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        updateConnectionData();
        this.started = false;

        txtResponses.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                if(v.getId() == R.id.txt_responses) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("NAPT Results", txtResponses.getText().toString());
                    clipboard.setPrimaryClip(clip);

                    showToast("Copied results to clipboard...");
                    return true;
                }

                return false;
            }
        });

    }

    public ConnectionData updateConnectionData(){
        try {
            this.connectionData = getConnectionData();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    txtNetwork.setText(connectionData.getNetworkName());
                    txtClientIP.setText(connectionData.getClientIP());
                }
            });

            return connectionData;
        }
        catch (Exception ex){
            showToast(ex.getMessage());
            return null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_ping_client, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        try {
            //start button
            if (v.getId() == R.id.btn_start) {
                Log.d("...", "Start clicked!");

                this.started = true;

                //Check if connected
                updateConnectionData();

                String clientIP = connectionData.getClientIP();
                if(!(clientIP.startsWith("192.168.") || clientIP.startsWith("10.") || clientIP.startsWith("100.") || clientIP.startsWith("172.") )){
                    showToast("Client IP is not behind NAT!");
                    return;
                }

                this.currentDelay = 0;
                this.attempts = 0;

                //Read server socket from UI
                this.server = this.txtServer.getText().toString();
                this.port = Integer.valueOf(this.txtPort.getText().toString());
                this.serverSocket = new InetSocketAddress(InetAddress.getByName(this.server), port);

                //Start new test...
                this.txtResponses.setText("");
                new PingTask(this, handler).execute();
            }

            //stop button
            else if(v.getId() == R.id.btn_stop){
                this.started = false;

                if(this.pongCallback != null){
                    pongCallback.setCanceled();
                    pongCallback = null;
                }

                this.handler.removeCallbacks(timerTask);
            }
        }
        catch(Exception ex){
            showToast("Exception: " + ex.getMessage());
        }
    }

    public void showToast(final String text){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(PingClient.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void resetAttempts(){
        this.attempts = 0;
    }

    public void appendResponse(final CoapResponse coapResponse){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtResponses.append(coapResponse.getContent().toString(CoapMessage.CHARSET) + "\n");
            }
        });
    }


    public PingTask.PongCallback getPongCallback(){
        return this.pongCallback;
    }

    public void setPongCallback(PingTask.PongCallback pongCallback){
        this.pongCallback = pongCallback;
    }

    public void restartTimer(){
        handler.removeCallbacks(timerTask);
        time = 0;
        timerTask = new TimerTask(this);
        handler.postDelayed(timerTask, 1000);
    }

    public String getServer(){
        return this.server;
    }

    public int getPort(){
        return this.port;
    }

    public InetSocketAddress getServerSocket(){
        return this.serverSocket;
    }

    public void increaseDelay(){
        currentDelay += STEP;
    }

    public int getCurrentDelay(){
        return this.currentDelay;
    }

    public CoapClientApplication getCoapClient(){
        return this.coapClient;
    }

    public void increaseTime(){
        this.time += 1;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(time >= currentDelay + STEP){
                    attempts += 1;
                    if(attempts < 2){
                        txtResponses.append("No PONG... Try again (only once).\n");
                        new PingTask(PingClient.this, handler).execute();
                    }
                    else {
                        txtResponses.append("NAT Timeout: <" + (currentDelay) + "s\n");
                        if(pongCallback != null){
                            pongCallback.setCanceled();
                            pongCallback = null;
                        }
                        handler.removeCallbacks(timerTask);
                        timerTask = null;
                    }
                }
                else {
                    txtTime.setText(time + "/" + currentDelay + "sec");
                }
            }
        });

        if(timerTask != null){
            handler.postDelayed(timerTask, 1000);
        }
    }


    private ConnectionData getConnectionData() throws Exception{
        ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        // connected to the internet
        if (activeNetwork != null) {
            // connected to wifi
            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                return getWifiData();
            }
            // connected to the mobile internet
            else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                return getMobileConnectionData();
            }
            //connected to something else (should never happen...)
            else{
                return new ConnectionData("unknown type", getIPAddress());
            }
        }

        // not connected to the internet
        return new ConnectionData("not connected", getIPAddress());
    }

    private ConnectionData getWifiData(){
        String networkName;
        String clientIP;

        WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);

        //SSID
        String wifiName = wifiManager.getConnectionInfo().getSSID();
        if(wifiName != null){
            networkName = wifiName;
        }
        else{
            networkName = "Unknown WiFi";
        }

        //IP
        int ip = wifiManager.getConnectionInfo().getIpAddress();
        if(ip == 0){
            clientIP = "no";
        }
        else {
            clientIP = String.format("%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
        }

        return new ConnectionData(networkName, clientIP);
    }

    private ConnectionData getMobileConnectionData() throws Exception{
        String networkName;

        TelephonyManager tm = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String tmpName = tm.getNetworkOperatorName();
        if (tmpName != null){
            networkName = tmpName;
        }
        else {
            networkName = "Unknown Mobile";
        }

        return new ConnectionData(networkName, getIPAddress());
    }


    private class ConnectionData {

        private String networkName;
        private String clientIP;

        private ConnectionData(String networkName, String clientIP) {
            this.networkName = networkName;
            this.clientIP = clientIP;
        }

        public String getNetworkName() {
            return networkName;
        }

        public String getClientIP() {
            return clientIP;
        }

        public boolean equals(Object object){
            if(!(object instanceof ConnectionData)){
                return false;
            }

            ConnectionData other = (ConnectionData) object;
            return (this.clientIP.equals(other.clientIP) && this.networkName.equals(other.networkName));
        }
    }


    private String getIPAddress() throws Exception {
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        NetworkInterface networkInterface;

        while(networkInterfaces.hasMoreElements()){
            networkInterface = networkInterfaces.nextElement();

            for (Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();inetAddresses.hasMoreElements();){
                InetAddress inetAddress = inetAddresses.nextElement();

                if(!inetAddress.isLoopbackAddress()){
                    String address = inetAddress.getHostAddress();
                    //Very dirty for IPv6
                    if(!address.contains(":")) {
                        return (inetAddress.getHostAddress());
                    }
                }
            }
        }

        return "No IP Address";
    }


    private class ConnectivityChangeReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                ConnectionData connectionData = getConnectionData();

                if (!PingClient.this.connectionData.equals(connectionData)) {
                    if (started){
                        showToast("Restart due to network change!");
                        btnStop.callOnClick();
                        btnStart.callOnClick();
                    }

                    updateConnectionData();
                }
            }
            catch(Exception ex){
                showToast("Exception: " + ex.getMessage());
            }
        }
    }
}
