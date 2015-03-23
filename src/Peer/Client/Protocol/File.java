package Peer.Client.Protocol;

import java.io.*;
import java.util.ArrayList;

public class File {

    private String path;

    private String id;
    private int replicationDegree;

    private ArrayList<Chunk> chunks;

    public File(String path, int replicationDegree) {

        this.path = path;
        this.replicationDegree = replicationDegree;

        this.chunks = new ArrayList<Chunk>();

        this.createChunks();
    }

    public ArrayList<Chunk> getChunks() {
        return this.chunks;
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
