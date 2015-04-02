package Peer.Server;

import Peer.Protocol.Chunk;
import Peer.Protocol.File;
import Peer.Protocol.KMPMatch;
import Peer.Protocol.Protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

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

        loadChunksReplication();
    }

    private void loadChunksReplication() {
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
                        body,
                        Integer.parseInt(messageParts[4])
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

                if(!chunkFromAnotherServer(fileId, chunkNo))
                {
                    this.sendMessage("CHUNK " + Protocol.VERSION + " " + fileId + " " + chunkNo + " " + Protocol.crlf() + Protocol.crlf(), chunk, mdrSocket, mdrAddress, mdrPort);
                } else {
                    System.out.println("The chunk was already sent by another peer.");
                }


                break;
            case "STORED":
                System.out.println("STORED received...");
                appendChunkReplication(messageParts[2], messageParts[3].replace(Protocol.crlf(), ""), 1, 0);
                break;

            case "DELETE":
                System.out.println("DELETE received");
                try {
                    deleteAllChunks(messageParts[1], messageParts[2].replace(Protocol.crlf(), ""));
                } catch(IOException e)
                {

                }
            case "REMOVED":

                System.out.println("REMOVED received...");

                if(messageParts.length < 4)
                {
                    System.out.println("Invalid REMOVED message.");
                    return;
                }


                fileId = messageParts[2];
                chunkNo = messageParts[3];

                System.out.println("CHUNK RECEIVED = " + fileId + " " + chunkNo) ;
                chunk = new Chunk();

                if(chunk.loadChunk(Integer.parseInt(chunkNo), fileId, savePath)){
                    /** send chunk if count drop below the desired replication degree **/
                    int currentChunkReplications = decrementChunkReplications(chunk);

                    if(currentChunkReplications == -1)
                    {
                        System.out.println("Chunk "+ chunk.getFileId() + " " + chunk.getNumber() +  " not found in " + Protocol.chunksReplication);
                    } else{
                        if(currentChunkReplications < Protocol.minReplicationDegree){
                            try {
                                putChunk(chunk);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                        }
                    }

                }


                break;

            default:
                System.out.println("Received message that couldn't be parsed.");
                break;
        }
    }


    public void sendMessage(String message, Chunk chunk) {
        byte[] buf = new byte[message.getBytes().length + chunk.getBody().length];
        System.arraycopy(message.getBytes(), 0, buf, 0, message.getBytes().length);
        System.arraycopy(chunk.getBody(), 0, buf, message.getBytes().length, chunk.getBody().length);

        DatagramPacket packet;
        packet = new DatagramPacket(buf, buf.length, this.mdbAddress, this.mdbPort);

        try {
            this.mdbSocket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }




    private Integer sendChunk(String messageToSend, Integer timeout, Chunk chunk) {
        DatagramPacket receivedPacket;
        sendMessage(messageToSend, chunk);

        long t = System.currentTimeMillis();
        long endTry = t + timeout;

        Integer chunksStored = 0;

        while(System.currentTimeMillis() < endTry)
        {
            try {

                byte[] buf = new byte[256];

                receivedPacket = new DatagramPacket(buf, buf.length);
                this.mcSocket.setSoTimeout(timeout);
                this.mcSocket.receive(receivedPacket);

                String received = new String(receivedPacket.getData(), 0, receivedPacket.getLength());

                received = received.replace(Protocol.crlf(), "");

                String[] messageParts = received.split(" ");

                if(        messageParts[0].equals("STORED")
                        && messageParts[1].equals(Protocol.VERSION)
                        && messageParts[2].equals(chunk.getFileId())
                        && messageParts[3].equals(chunk.getNumber().toString())
                        )
                {
                    System.out.println("Chunk stored.");
                    appendChunkReplication(messageParts[2], messageParts[3].replace(Protocol.crlf(), ""), 1, 0);
                    chunksStored++;
                }

            } catch (SocketTimeoutException e)
            {
                System.out.println("Socket timed out.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return chunksStored;
    }


    public void putChunk(Chunk chunk) throws InterruptedException {
        String messageToSend;

        Integer retries;
        Integer chunkReplications;
        Integer timeout;

            retries = 1;
            timeout = 500;
            chunkReplications = 0;

            System.out.println("\n =========== \nSending chunk no." + chunk.getNumber() + "\n =========== \n");

            messageToSend = "PUTCHUNK"
                    + " " + Protocol.VERSION
                    + " " + chunk.getFileId()
                    + " " + chunk.getNumber()
                    + " " + 1
                    + " " + Protocol.crlf() + Protocol.crlf();

            while(retries <= Protocol.maxRetries && chunkReplications < 1) {
                System.out.println("Retry #" + retries);

                chunkReplications += sendChunk(messageToSend, timeout, chunk);

                System.out.println("Retry Ended. Chunks received: " + chunkReplications);

                retries++;
                timeout = timeout * 2;
            }

            if(chunkReplications < Protocol.minReplicationDegree)
            {
                System.out.println("Couldn't replicate chunk #" + chunk.getNumber() + " at least " + 1 + " times.");
                return;
            }

        }



    private int decrementChunkReplications(Chunk chunk)
    {
        int currentChunkReplications = -1;
        for (int i = 0; i < chunksReplication.length(); i++) {
            if (chunksReplication.getJSONObject(i).getString("chunkNo").equals(chunk.getNumber().toString())
                    &&
                    chunksReplication.getJSONObject(i).getString("fileId").equals(chunk.getFileId())) {
                currentChunkReplications = chunksReplication.getJSONObject(i).getInt("replication");
                currentChunkReplications--;
                chunksReplication.getJSONObject(i).put("replication", currentChunkReplications);

            }
        }

        JSONObject filesObject = new JSONObject();
        filesObject.put("chunks", chunksReplication);

        Protocol.writeFile(Protocol.chunksReplication, filesObject.toString());

        return currentChunkReplications;
    }

    private void deleteAllChunks(String version, String fileId) throws IOException {
        if(! version.equals(Protocol.VERSION))
            return;

        java.io.File folder = new java.io.File(savePath);
        java.io.File[] listOfFiles = folder.listFiles();

        for (java.io.File file : listOfFiles) {
            if (file.isFile()) {
                if(file.getName().endsWith(fileId))
                {
                    file.delete();
                }
            }
        }
    }

    private Boolean chunkFromAnotherServer(String fileId, String chunkNo)
    {
        long t = System.currentTimeMillis();
        int randomWait = Protocol.random.nextInt(400);
        long endTry = t + randomWait;

        byte[] buf = new byte[65000];

        DatagramPacket receivedPacket;

        while(System.currentTimeMillis() < endTry)
        {
            receivedPacket = new DatagramPacket(buf, buf.length);
            try {
                mdrSocket.setSoTimeout(randomWait);
                mdrSocket.receive(receivedPacket);
            } catch (IOException e) {
            }

            String receivedMessage = new String(receivedPacket.getData(), 0, receivedPacket.getLength());

            receivedMessage = receivedMessage.replace(Protocol.crlf(), "");

            String[] receivedMessageParts = receivedMessage.split(" ");

            if(receivedMessageParts[0].equals("CHUNK") &&
                    receivedMessageParts[1].equals(Protocol.VERSION) &&
                    receivedMessageParts[2].equals(fileId) &&
                    receivedMessageParts[3].equals(chunkNo))
            {
                return true;
            }
        }

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

    private void appendChunkReplication(String fileId, String chunkNo, int replication, int minReplication) {
        loadChunksReplication();
        Boolean chunkExisted = false;

        System.out.println("chunkNo=" + chunkNo + " - MIN REP: " + minReplication);

        for (int i = 0; i < chunksReplication.length(); i++) {
            if (chunksReplication.getJSONObject(i).getString("chunkNo").equals(chunkNo)
                    &&
                    chunksReplication.getJSONObject(i).getString("fileId").equals(fileId)) {
                replication += chunksReplication.getJSONObject(i).getInt("replication");
                chunksReplication.getJSONObject(i).put("replication", replication);
                if(minReplication != 0)
                {
                    System.out.println("updating min rep to " + minReplication);
                    chunksReplication.getJSONObject(i).put("minReplication", minReplication);
                }
                chunkExisted = true;
            }
        }

        if(!chunkExisted) {
            System.out.println("chunk doesn't exist json");
            JSONObject newChunk = new JSONObject();
            newChunk.put("fileId", fileId);
            newChunk.put("chunkNo", chunkNo);
            newChunk.put("replication", replication);
            newChunk.put("minReplication", minReplication);
            chunksReplication.put(newChunk);
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

    public void storeChunk(String version, String fileId, Integer chunkNumber, byte[] body, Integer chunkReplicationMin)
    {
        try {
            ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();

            bodyStream.write(body);
            Chunk chunk = new Chunk(fileId, chunkNumber, bodyStream.size(), bodyStream);

            appendChunkReplication(fileId, chunkNumber.toString(), 0, chunkReplicationMin);

            chunk.save(savePath);
            Thread.sleep(Protocol.random.nextInt(400));
            this.sendMessage("STORED " + version + " " + fileId + " " + chunkNumber + Protocol.crlf() + Protocol.crlf());

        } catch (InterruptedException | IOException e)
        {
            e.printStackTrace();
        }

    }


}
