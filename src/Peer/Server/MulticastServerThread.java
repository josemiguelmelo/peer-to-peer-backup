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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MulticastServerThread {
    int port;
    MulticastSocket socket;
    InetAddress address;
    int maximumSpace;
    String savePath;
    JSONArray chunksReplication;

    public MulticastServerThread(String ip, int port) throws IOException {
        this.port = port;
        this.socket = new MulticastSocket(port);
        this.address = InetAddress.getByName(ip);
        socket.joinGroup(address);
        this.loadSettings();

        String chunkReplicationString = Protocol.readFile(Protocol.chunksReplication, Charset.defaultCharset());

        if(!chunkReplicationString.equals("")) {
            JSONObject files = new JSONObject(chunkReplicationString.trim());
            this.chunksReplication = files.getJSONArray("chunks");
        } else {
            this.chunksReplication = new JSONArray();
        }
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

        switch(messageParts[0]) {
            case "PUTCHUNK":
                System.out.println("PUTCHUNK received...");

                if(messageParts.length < 4)
                {
                    System.out.println("Invalid PUTCHUNK message.");
                    return;
                }

                byte[] body = this.getBodyFromMessage(messageBytes);

                System.out.println("Body size: " + body.length);

                this.storeChunk(
                        messageParts[1],
                        messageParts[2],
                        Integer.parseInt(messageParts[3]),
                        body
                );
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

    private void appendChunkReplication(String chunk, int replication)
    {
        for(int i = 0; i < chunksReplication.length(); i++)
        {
            if(chunksReplication.getJSONObject(i).has(chunk))
            {
                replication += chunksReplication.getJSONObject(i).getInt(chunk);
                chunksReplication.getJSONObject(i).put(chunk, replication);
            }
        }

        JSONObject filesObject = new JSONObject();
        filesObject.put("chunks", chunksReplication);

        Protocol.writeFile(Protocol.chunksReplication, filesObject.toString());
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
