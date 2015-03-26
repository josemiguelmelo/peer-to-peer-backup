package Peer.Server;

import Peer.Protocol.Chunk;
import Peer.Protocol.KMPMatch;
import Peer.Protocol.Protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.Charset;

import org.json.JSONException;
import org.json.JSONObject;

public class MulticastServerThread {
    int port;
    MulticastSocket socket;
    InetAddress address;
    int maximumSpace;
    String savePath;

    public MulticastServerThread(String ip, int port) throws IOException {
        this.port = port;
        this.socket = new MulticastSocket(port);
        this.address = InetAddress.getByName(ip);
        socket.joinGroup(address);
        this.loadSettings();
    }

    public void loadSettings() throws IOException {
        String configString = Protocol.readFile(Protocol.settingsPath, Charset.defaultCharset());

        JSONObject config = new JSONObject(configString.trim());

        this.maximumSpace = config.getInt("maximumSpace");
        System.out.println("Max Space: " + this.maximumSpace);

        this.savePath = config.getString("relativeSavePath");
        System.out.println("Save Path: " + this.savePath);
    }

    public void run() throws IOException {

        boolean running = true;
        DatagramPacket receivedPacket;

        while (running) {
            try {
                byte[] buf = new byte[65000];

                receivedPacket = new DatagramPacket(buf, buf.length);
                socket.receive(receivedPacket);

                String receivedMessage = new String(receivedPacket.getData(), 0, receivedPacket.getLength());
                byte[] receivedMessageBytes = java.util.Arrays.copyOf(receivedPacket.getData(), receivedPacket.getLength());

                this.parseMessage(receivedMessage, receivedMessageBytes);

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

    public void parseMessage(String message, byte[] messageBytes) {
        String[] messageParts = message.split(" ");
        byte[] body;
        switch(messageParts[0]) {
            case "PUTCHUNK":
                System.out.println("PUTCHUNK received...");

                if(messageParts.length < 4)
                {
                    System.out.println("An error has occurred");
                    return;
                }

                body = this.getBodyFromMessage(messageBytes);

                System.out.println("Body size: " + body.length);

                this.storeChunk(
                        messageParts[1],
                        messageParts[2],
                        Integer.parseInt(messageParts[3]),
                        body
                );
                break;

            case "GETCHUNK":
                System.out.println("GETCHUNK received...");

                if(messageParts.length < 4)
                {
                    System.out.println("An error has occurred");
                    return;
                }

                String fileId = messageParts[2];
                String chunkNo = messageParts[3];

                Chunk chunk = new Chunk();
                chunk.loadChunk(Integer.parseInt(chunkNo), fileId, savePath);

                System.out.println("Chunk size = " + chunk.getBody().length);

                this.sendMessage("CHUNK " + Protocol.VERSION + " " + fileId + " " + chunkNo + " " + Protocol.crlf() + Protocol.crlf(), chunk);


                break;
            default:
                System.out.println("Received message that couldn't be parsed.");
                break;
        }
    }

    public byte[] getBodyFromMessage(byte[] messageBytes)
    {
        Peer.Protocol.KMPMatch kmpMatch = new KMPMatch();

        String stringPattern = Protocol.crlf() + Protocol.crlf();

        byte[] pattern = stringPattern.getBytes();

        int patternPosition = kmpMatch.indexOf(messageBytes, pattern);

        patternPosition = patternPosition + 4;

        byte[] body = new byte[messageBytes.length - patternPosition];

        java.lang.System.arraycopy(messageBytes, patternPosition, body, 0, messageBytes.length - patternPosition);

        return body;
    }

    public void sendMessage(String message)
    {
        byte[] buf;

        buf = message.getBytes();

        DatagramPacket packet;
        packet = new DatagramPacket(buf, buf.length, address, port);

        try {
            this.socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void sendMessage(String message, Chunk chunk) {
        byte[] buf = new byte[message.getBytes().length + chunk.getBody().length];
        System.arraycopy(message.getBytes(), 0, buf, 0, message.getBytes().length);
        System.arraycopy(chunk.getBody(), 0, buf, message.getBytes().length, chunk.getBody().length);

        DatagramPacket packet;
        packet = new DatagramPacket(buf, buf.length, address, port);

        try {
            this.socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void storeChunk(String version, String fileId, Integer chunkNumber, byte[] body)
    {
        try {
            ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();

            bodyStream.write(body);
            Chunk chunk = new Chunk(fileId, chunkNumber, bodyStream.size(), bodyStream);

            if(chunk.save(savePath)) {
                Thread.sleep(Protocol.random.nextInt(400));

                this.sendMessage("STORED " + version + " " + fileId + " " + chunkNumber + Protocol.crlf() + Protocol.crlf());
            }

        } catch (InterruptedException | IOException e)
        {
            e.printStackTrace();
        }

    }


}
