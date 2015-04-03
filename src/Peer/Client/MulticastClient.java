package Peer.Client;

import Peer.Server.MulticastServerThread;

import java.io.IOException;

public class MulticastClient {

    public MulticastClient() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        new MulticastServerThread(args[0], Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]), args[4], Integer.parseInt(args[5]), "mdbSocket").start();
        new MulticastServerThread(args[0], Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]), args[4], Integer.parseInt(args[5]), "mcSocket").start();

        new MulticastClientThread(args[0], Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]), args[4], Integer.parseInt(args[5])).start();


    }
}