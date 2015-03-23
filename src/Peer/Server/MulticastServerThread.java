package Peer.Server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class MulticastServerThread {
    String ip;
    int port;

    public MulticastServerThread(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public void run() throws IOException {

        MulticastSocket socket = new MulticastSocket(8888);
        InetAddress address = InetAddress.getByName(ip);
        socket.joinGroup(address);

        boolean running = true;
        DatagramPacket receivedPacket;

        while (running) {
            try {
                byte[] buf = new byte[256];

                receivedPacket = new DatagramPacket(buf, buf.length);
                socket.receive(receivedPacket);

                String received = new String(receivedPacket.getData(), 0, receivedPacket.getLength());
                System.out.println(received);

                String dString = "CONFIRMATION";

                buf = dString.getBytes();

                InetAddress group = InetAddress.getByName(ip);
                DatagramPacket packet;
                packet = new DatagramPacket(buf, buf.length, group, port);

                socket.send(packet);

                Thread.sleep(1000);

            }
            catch (IOException e) {
                e.printStackTrace();
                running = false;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        socket.close();
    }
}
