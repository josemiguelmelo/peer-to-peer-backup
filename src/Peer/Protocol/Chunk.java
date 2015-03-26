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

    public Boolean save(String pathToSave)
    {
        java.io.File f = new java.io.File(pathToSave + number + "-" + fileId);
        if(f.isFile())
        {
            return false;
        }

        java.io.File saveDirectory = new java.io.File(pathToSave);
        if (!saveDirectory.exists()) saveDirectory.mkdir();

        OutputStream outputStream = null;
        try {
            f.createNewFile();
            outputStream = new FileOutputStream(pathToSave + number + "-" + fileId);
            this.content.writeTo(outputStream);
            System.out.println("Chunk saved");
        } catch (IOException e) {
            e.printStackTrace();
        }

        return true;
    }


}
