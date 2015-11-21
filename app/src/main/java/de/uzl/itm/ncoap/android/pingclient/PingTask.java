package de.uzl.itm.ncoap.android.pingclient;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import java.net.URI;

import de.uniluebeck.itm.ncoap.communication.dispatching.client.ClientCallback;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.MessageCode;
import de.uniluebeck.itm.ncoap.message.MessageType;

/**
 * Created by olli on 27.05.15.
 */
public class PingTask extends AsyncTask<Void, Void, Void> {

    private PingClient pingClient;
    private Handler handler;

    public PingTask(PingClient pingClient, Handler handler) {
        this.pingClient = pingClient;
        this.handler = handler;
    }


    @Override
    protected Void doInBackground(Void... params) {
        try {

            PongCallback pongCallback = pingClient.getPongCallback();

            if (pongCallback != null) {
                pongCallback.setCanceled();
                pingClient.setPongCallback(null);
            }

            //New timer starting from 0...
            pingClient.restartTimer();

            //create and send request request
            URI uri = new URI("coap", null, pingClient.getServer(), pingClient.getPort(), "/ping", "delay=" + pingClient.getCurrentDelay(), null);
            CoapRequest coapRequest = new CoapRequest(MessageType.Name.NON, MessageCode.Name.GET, uri);

            //Set new PONG callback
            pongCallback = new PongCallback();
            pingClient.setPongCallback(pongCallback);
            pingClient.getCoapClient().sendCoapRequest(coapRequest, pongCallback, pingClient.getServerSocket());

            Log.d("...", "Sent request to " + pingClient.getServerSocket());
            return null;
        } catch (Exception e) {
            pingClient.showToast("Exception in PingTask: " + e.getMessage());
            return null;
        }
    }

    public class PongCallback extends ClientCallback {

        private boolean canceled;

        public PongCallback() {
            this.canceled = false;
        }

        public void setCanceled() {
            this.canceled = true;
        }

        public boolean isCanceled() {
            return this.canceled;
        }

        @Override
        public void processCoapResponse(final CoapResponse coapResponse) {
            if(!canceled) {
                Log.d("...", "Response received!");
                pingClient.appendResponse(coapResponse);
                pingClient.resetAttempts();

                PongCallback pongCallback = pingClient.getPongCallback();

                if (pongCallback != null && !pongCallback.isCanceled()) {
                    pingClient.increaseDelay();
                    new PingTask(pingClient, handler).execute();
                }
            }
        }
    }
}
