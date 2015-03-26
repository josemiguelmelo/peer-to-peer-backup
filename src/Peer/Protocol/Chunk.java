package Peer.Protocol;

import java.io.*;

public class Chunk {

    public static int MAX_SIZE = 64 * 1000; //64KB

    private int number;

    private int chunkSize;

    private String fileId;
    private ByteArrayOutputStream content;

    public Chunk(int number, int chunkSize, ByteArrayOutputStream content) {
        this.number = number;
        this.chunkSize = chunkSize;
        this.content = content;
        this.fileId = "";
        System.out.println("Chunk created. Chunk Size: " + content.size());
    }

    public Chunk(String fileId, int number, int chunkSize, ByteArrayOutputStream content) {
        this.number = number;
        this.chunkSize = chunkSize;
        this.content = content;
        this.fileId = fileId;
        System.out.println("Chunk created. Chunk Size: " + content.size());
    }

    public Integer getNumber() {
        return number;
    }

    public byte[] getBody() {
        return content.toByteArray();
    }

    public void save()
    {
        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(number + "-" + fileId);
            this.content.writeTo(outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
