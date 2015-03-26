package Peer.Protocol;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class File {

    private String name;
    private String path;
    private String id;
    private int replicationDegree;

    private ArrayList<Chunk> chunks;

    public File(String path, int replicationDegree) {
        this.path = path;
        this.replicationDegree = replicationDegree;

        this.chunks = new ArrayList<Chunk>();

        java.io.File f = new java.io.File(path);
        this.name = f.getName();

        this.generateId();
        this.createChunks();
    }

    public String getId()
    {
        return this.id;
    }

    public String getName() {
        return name;
    }

    public int getReplicationDegree() {
        return replicationDegree;
    }

    public ArrayList<Chunk> getChunks() {
        return this.chunks;
    }

    private void generateId()
    {
        java.io.File file = new java.io.File(this.path);
        try {
            this.id = Protocol.sha256(file.getName() + file.lastModified());
        } catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }
    }

    private void createChunks()
    {
        int chunkNumber = 0;

        int sizeOfFiles = Chunk.MAX_SIZE;
        byte[] buffer = new byte[sizeOfFiles];

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(this.path))) {

            int chunkSize = 0;
            while ((chunkSize = bis.read(buffer)) > 0) {
                try (ByteArrayOutputStream content = new ByteArrayOutputStream()) {
                    content.write(buffer, 0, chunkSize);//tmp is chunk size
                    Chunk chunk = new Chunk(chunkNumber, chunkSize, content);
                    chunks.add(chunk);
                    chunkNumber++;
                }
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
