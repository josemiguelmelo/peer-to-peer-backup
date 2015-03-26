package Peer.Client;

import java.io.IOException;

public class MulticastClient {

    public MulticastClient() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        new MulticastClientThread("224.0.0.3", 8888).start();
    }
}