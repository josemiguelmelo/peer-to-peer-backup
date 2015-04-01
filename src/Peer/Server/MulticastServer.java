package Peer.Server;

import java.io.IOException;

public class MulticastServer {

    public static void main(String[] args) throws IOException {

        SpaceReclaimingThread spaceThread = new SpaceReclaimingThread();
        spaceThread.run();


    }
}
