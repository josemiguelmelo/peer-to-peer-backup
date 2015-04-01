package Peer.Server;

import Peer.Protocol.Chunk;
import Peer.Protocol.KMPMatch;
import Peer.Protocol.Protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.charset.Charset;

import org.json.JSONArray;
import org.json.JSONObject;

public class MulticastServerThread extends Thread {
    int mcPort;
    MulticastSocket mcSocket;
    InetAddress mcAddress;

    int mdbPort;
    MulticastSocket mdbSocket;
    InetAddress mdbAddress;

    int mdrPort;
    MulticastSocket mdrSocket;
    InetAddress mdrAddress;

    MulticastSocket activeSocket;

    int maximumSpace;
    String savePath;
    JSONArray chunksReplication;

    public MulticastServerThread(String mcIp, int mcPort, String mdbIp, int mdbPort, String mdrIp, int mdrPort, String socket) throws IOException, InterruptedException {
        this.mcPort = mcPort;
        this.mcAddress = InetAddress.getByName(mcIp);
        this.mcSocket = new MulticastSocket(mcPort);
        this.mcSocket.joinGroup(mcAddress);

        this.mdbPort = mdbPort;
        this.mdbAddress = InetAddress.getByName(mdbIp);
        this.mdbSocket = new MulticastSocket(mdbPort);
        this.mdbSocket.joinGroup(mdbAddress);

        this.mdrPort = mdrPort;
        this.mdrAddress = InetAddress.getByName(mdrIp);
        this.mdrSocket = new MulticastSocket(mdrPort);
        this.mdrSocket.joinGroup(mdrAddress);

        Field field = null;
        try {
            field = this.getClass().getDeclaredField(socket);
            field.setAccessible(true);
            this.activeSocket = (MulticastSocket) field.get(this);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }

        this.loadSettings();

        String chunkReplicationString = Protocol.readFile(Protocol.chunksReplication, Charset.defaultCharset());

        if(!chunkReplicationString.equals("")) {
            JSONObject files = new JSONObject(chunkReplicationString.trim());
            this.chunksReplication = files.getJSONArray("chunks");
        } else {
            this.chunksReplication = new JSONArray();
        }

        // initiate thread responsible for space reclaiming
        new SpaceReclaimingThread(mcIp, mcPort, mdbIp, mdbPort, mdrIp, mdrPort, socket);

    }

    public void loadSettings() throws IOException {
        String configString = Protocol.readFile(Protocol.settingsPath, Charset.defaultCharset());

        JSONObject config = new JSONObject(configString.trim());

        this.maximumSpace = config.getInt("maximumSpace");
        System.out.println("Max Space: " + this.maximumSpace);

        this.savePath = config.getString("relativeSavePath");
        System.out.println("Save Path: " + this.savePath);
    }

    public void run() {

        boolean running = true;
        DatagramPacket receivedPacket;

        while (running) {
            try {
                byte[] buf = new byte[65000];

                receivedPacket = new DatagramPacket(buf, buf.length);
                activeSocket.receive(receivedPacket);

                String receivedMessage = new String(receivedPacket.getData(), 0, receivedPacket.getLength());
                byte[] receivedMessageBytes = java.util.Arrays.copyOf(receivedPacket.getData(), receivedPacket.getLength());

                this.parseMessage(receivedMessage, receivedMessageBytes);

            }
            catch (IOException e) {
                e.printStackTrace();
                running = false;
            }
        }

        mcSocket.close();
    }

    public void parseMessage(String message, byte[] messageBytes) {
        String[] messageParts = message.split(" ");
        byte[] body;

        String fileId;
        String chunkNo;

        Chunk chunk;

        switch(messageParts[0]) {
            case "PUTCHUNK":
                System.out.println("PUTCHUNK received...");

                if(messageParts.length < 4)
                {
                    System.out.println("Invalid PUTCHUNK message.");
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

                fileId = messageParts[2];
                chunkNo = messageParts[3];

                chunk = new Chunk();
                chunk.loadChunk(Integer.parseInt(chunkNo), fileId, savePath);

                System.out.println("Chunk size = " + chunk.getBody().length);

                this.sendMessage("CHUNK " + Protocol.VERSION + " " + fileId + " " + chunkNo + " " + Protocol.crlf() + Protocol.crlf(), chunk, mdrSocket, mdrAddress, mdrPort);


                break;

            case "REMOVED":

                System.out.println("REMOVED received...");

                if(messageParts.length < 4)
                {
                    System.out.println("Invalid REMOVED message.");
                    return;
                }


                fileId = messageParts[2];
                chunkNo = messageParts[3];

                chunk = new Chunk();
                chunk.loadChunk(Integer.parseInt(chunkNo), fileId, savePath);


                /** send chunk if count drop below the desired replication degree **/

                break;



            default:
                System.out.println("Received message that couldn't be parsed.");
                break;
        }
    }

    private Boolean chunkFromAnotherServer()
    {
        byte[] buf = new byte[65000];


        DatagramPacket receivedPacket;

        receivedPacket = new DatagramPacket(buf, buf.length);
        try {
            mdrSocket.setSoTimeout(Protocol.random.nextInt(400));
            mdrSocket.receive(receivedPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String receivedMessage = new String(receivedPacket.getData(), 0, receivedPacket.getLength());
        return false;
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
        packet = new DatagramPacket(buf, buf.length, mcAddress, mcPort);

        try {
            this.mcSocket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void appendChunkReplication(String chunk, int replication) {
        for (int i = 0; i < chunksReplication.length(); i++) {
            if (chunksReplication.getJSONObject(i).has(chunk)) {
                replication += chunksReplication.getJSONObject(i).getInt(chunk);
                chunksReplication.getJSONObject(i).put(chunk, replication);
            }
        }

        JSONObject filesObject = new JSONObject();
        filesObject.put("chunks", chunksReplication);

        Protocol.writeFile(Protocol.chunksReplication, filesObject.toString());
    }

    public void sendMessage(String message, Chunk chunk, MulticastSocket socket, InetAddress address, int port) {
        byte[] buf = new byte[message.getBytes().length + chunk.getBody().length];
        System.arraycopy(message.getBytes(), 0, buf, 0, message.getBytes().length);
        System.arraycopy(chunk.getBody(), 0, buf, message.getBytes().length, chunk.getBody().length);

        DatagramPacket packet;
        packet = new DatagramPacket(buf, buf.length, address, port);

        try {
            socket.send(packet);
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
            } else {
                System.out.println("This chunk already exists.");
            }

        } catch (InterruptedException | IOException e)
        {
            e.printStackTrace();
        }

    }


}
