package Peer.Client;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.util.ArrayList;

import Peer.Protocol.Chunk;
import Peer.Protocol.File;
import Peer.Protocol.KMPMatch;
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
            this.getChunk();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }




    public void getChunk() throws InterruptedException {
        String filename = "uml_diagram.png";
        filesBackedUp.length();

        String fileId = "";

        String messageToSend;
        Integer retries;
        Integer chunkReplications;
        Integer timeout;



        File file = new File(filename, 1);


        ArrayList<Chunk> chunksList = new ArrayList<Chunk>();
        boolean allChunksReceived = false;



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


        int chunkNumber = 0;

        while(!allChunksReceived){
            retries = 1;
            timeout = 500;

            messageToSend = "GETCHUNK"
                    + " " + Protocol.VERSION
                    + " " + fileId
                    + " " + chunkNumber
                    + " " + Protocol.crlf() + Protocol.crlf();


            while(retries <= Protocol.maxRetries) {
                System.out.println("Retry #" + retries);

                // send message to get chunks
                sendMessage(messageToSend);

                // receive chunk from server
                byte[] chunkByte = receiveChunk(fileId, chunkNumber, timeout);


                if(chunkByte!=null) {
                    try {
                        ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();


                        bodyStream.write(chunkByte);
                        Chunk chunk = new Chunk(fileId, chunkNumber, bodyStream.size(), bodyStream);

                        if (chunkByte.length > 0) {

                            // add chunk to file
                            file.addChunk(chunk);

                            System.out.println("chunk byte size = " + chunkByte.length);
                            if(chunkByte.length < Chunk.MAX_SIZE){
                                allChunksReceived = true;
                            }
                            break;
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                retries++;
                timeout = timeout * 2;
            }


            chunkNumber++;
        }


        file.save(filename);
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

        System.out.println("Total message size: " + messageBytes.length);
        System.out.println("Body size: " + body.length);
        return body;
    }


    public byte[] receiveChunk(String fileId, Integer chunkNo, Integer timeout){
        DatagramPacket receivedPacket;

        long t = System.currentTimeMillis();
        long endTry = t + timeout;

        while(System.currentTimeMillis() < endTry)
        {

            try {

                byte[] buf = new byte[65000];

                receivedPacket = new DatagramPacket(buf, buf.length);
                this.socket.setSoTimeout(timeout);
                this.socket.receive(receivedPacket);

                String received = new String(receivedPacket.getData(), 0, receivedPacket.getLength());

                received = received.replace(Protocol.crlf(), "");

                String[] messageParts = received.split(" ");

                if(        messageParts[0].equals("CHUNK")
                        && messageParts[1].equals(Protocol.VERSION)
                        && messageParts[2].equals(fileId)
                        && messageParts[3].equals(chunkNo.toString())
                        )
                {

                    System.out.println("Chunk stored.");

                    byte[] receivedMessageBytes = java.util.Arrays.copyOf(receivedPacket.getData(), receivedPacket.getLength());

                    return getBodyFromMessage(receivedMessageBytes);
                }

            } catch (SocketTimeoutException e)
            {
                System.out.println("Socket timed out.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }



    public void putChunk() throws InterruptedException {
        DatagramPacket receivedPacket;

        File file = new File("/Users/josemiguelmelo/Desktop/uml_diagram.png", 1);

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

    public void sendMessage(String message)
    {
        byte[] buf = new byte[message.getBytes().length];
        System.arraycopy(message.getBytes(), 0, buf, 0, message.getBytes().length);

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
}
