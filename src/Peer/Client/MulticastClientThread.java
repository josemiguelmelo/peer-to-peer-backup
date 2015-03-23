package Peer.Client;

import java.io.IOException;
import java.net.*;

public class MulticastClientThread extends Thread {

    String ip;
    int port;

    MulticastSocket socket = null;

    InetAddress address = null;

    public MulticastClientThread(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public void run() {

        String ip = "224.0.0.3";
        Integer port = 8888;

        try {
            this.socket = new MulticastSocket(8888);
            this.address = InetAddress.getByName(ip);
            this.socket.joinGroup(address);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.putChunk(socket);

    }

    public void putChunk(MulticastSocket socket) {
        DatagramPacket sendPacket, receivedPacket;
        Boolean running = true;

        while(running)
        {
            String dString = "PUTCHUNK";

            byte[] buf = new byte[256];

            buf = dString.getBytes();

            InetAddress group = null;
            try {
                group = InetAddress.getByName(this.ip);

                sendPacket = new DatagramPacket(buf, buf.length, group, port);

                socket.send(sendPacket);

                //Receive confirmation
                receivedPacket = new DatagramPacket(buf, buf.length);
                socket.setSoTimeout(2000);
                socket.receive(receivedPacket);

                String received = new String(receivedPacket.getData(), 0, receivedPacket.getLength());
                System.out.println(received);

                Thread.sleep(1000);

            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
