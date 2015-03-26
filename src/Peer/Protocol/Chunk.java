package Peer.Protocol;

import java.io.*;
import java.io.File;
import java.nio.file.Files;

public class Chunk {

    public static int MAX_SIZE = 64 * 1000; //64KB

    private int number;

    private int chunkSize;

    private String fileId;
    private ByteArrayOutputStream content;

    public Chunk(){
        content = new ByteArrayOutputStream();
    }

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

    public ByteArrayOutputStream getBodyOutputStream() { return this.content; }


    public void loadChunk(Integer number, String fileId, String path){
        java.io.File file = new File(path + number + "-" + fileId);

        if(file.isFile()){

            System.out.println(file.toPath());

            try {
                byte[] data = Files.readAllBytes(file.toPath());

                this.content.write(data, 0, data.length);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
