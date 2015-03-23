package Peer.Client;

public class MulticastClient {

    public MulticastClient() {
    }

    public static void main(String[] args) {
        new MulticastClientThread("224.0.0.3", 8888).start();
    }
}