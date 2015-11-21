package de.uzl.itm.ncoap.android.pingclient;

/**
 * Created by olli on 27.05.15.
 */
public class TimerTask implements Runnable{

    private PingClient pingClient;

    public TimerTask(PingClient pingClient) {
        this.pingClient = pingClient;
    }


    @Override
    public void run() {
        pingClient.increaseTime();
    }
}
