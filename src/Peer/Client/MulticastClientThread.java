package Peer.Client;

import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;

import Peer.Protocol.Chunk;
import Peer.Protocol.File;
import Peer.Protocol.Protocol;
import org.json.JSONArray;
import org.json.JSONObject;

public class MulticastClientThread extends Thread {

    int port;
    MulticastSocket socket;
    InetAddress address;
    JSONArray filesBackedUp;

    public MulticastClientThread(String ip, int port) throws IOException, InterruptedException {
        this.port = port;
        this.address = InetAddress.getByName(ip);
        this.socket = new MulticastSocket(port);
        socket.joinGroup(address);

        String filesBackedUpString = Protocol.readFile(Protocol.filesBackedUp, Charset.defaultCharset());

        if(!filesBackedUpString.equals("")) {
            JSONObject files = new JSONObject(filesBackedUpString.trim());
            this.filesBackedUp = files.getJSONArray("files");
        } else {
            this.filesBackedUp = new JSONArray();
        }
    }

    public void run() {

        String ip = "224.0.0.3";
        Integer port = 8888;

        try {
            this.socket = new MulticastSocket(port);
            this.address = InetAddress.getByName(ip);
            this.socket.joinGroup(address);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            this.putChunk();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public void getChunk() throws InterruptedException {
        String filename = "image.gif";
        filesBackedUp.length();

        String fileId = "";

        for(int i=0; i<filesBackedUp.length(); i++) {
            if(filesBackedUp.getJSONObject(i).has(filename))
            {
                fileId = filesBackedUp.getJSONObject(i).getString(filename);
            }
        }

        if(fileId.equals(""))
        {
            System.out.println("File " + filename + " was not backed up.");
            return;
        }

        System.out.println("File ID: " + fileId);
    }

    public void putChunk() throws InterruptedException {
        DatagramPacket receivedPacket;

        File file = new File("/Users/ruigomes/Desktop/image.gif", 1);

        String messageToSend;

        Integer retries;
        Integer chunkReplications;
        Integer timeout;

        System.out.println("File Info. Chunks: " + file.getChunks().size());

        for (Chunk chunk : file.getChunks()) {
            retries = 1;
            timeout = 500;
            chunkReplications = 0;

            System.out.println("\n =========== \nSending chunk no." + chunk.getNumber() + "\n =========== \n");

            messageToSend = "PUTCHUNK"
                    + " " + Protocol.VERSION
                    + " " + file.getId()
                    + " " + chunk.getNumber()
                    + " " + file.getReplicationDegree()
                    + " " + Protocol.crlf() + Protocol.crlf();

            while(retries <= Protocol.maxRetries && chunkReplications < file.getReplicationDegree()) {
                System.out.println("Retry #" + retries);

                chunkReplications += sendChunk(file, messageToSend, timeout, chunk);

                System.out.println("Retry Ended. Chunks received: " + chunkReplications);

                retries++;
                timeout = timeout * 2;
            }

            if(chunkReplications < file.getReplicationDegree())
            {
                System.out.println("Couldn't replicate chunk no." + chunk.getNumber() + " " + chunkReplications + " times.");
                return;
            }

        }

        appendFileId(file.getName(), file.getId());

    }

    private void appendFileId(String filename, String fileId)
    {
        JSONObject fileJson = new JSONObject();
        fileJson.put(filename, fileId);

        filesBackedUp.put(fileJson);

        JSONObject filesObject = new JSONObject();
        filesObject.put("files", filesBackedUp);

        Protocol.writeFile(Protocol.filesBackedUp, filesObject.toString());
    }

    private Integer sendChunk(File file, String messageToSend, Integer timeout, Chunk chunk) {
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
                this.socket.setSoTimeout(timeout);
                this.socket.receive(receivedPacket);

                String received = new String(receivedPacket.getData(), 0, receivedPacket.getLength());

                received = received.replace(Protocol.crlf(), "");

                String[] messageParts = received.split(" ");

                if(        messageParts[0].equals("STORED")
                        && messageParts[1].equals(Protocol.VERSION)
                        && messageParts[2].equals(file.getId())
                        && messageParts[3].equals(chunk.getNumber().toString())
                        )
                {
                    System.out.println("Chunk stored.");
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
}
