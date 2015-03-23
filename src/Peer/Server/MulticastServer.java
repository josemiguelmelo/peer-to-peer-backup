package Peer.Server;

import java.io.IOException;

public class MulticastServer {

    public static void main(String[] args) throws IOException {

        new MulticastServerThread("224.0.0.3", 8888).run();

    }
}
