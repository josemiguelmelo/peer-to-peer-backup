package Peer.Client.Protocol;

import java.io.ByteArrayOutputStream;

public class Chunk {

    public static int MAX_SIZE = 64 * 1000; //64KB

    private int number;
    private int chunkSize;

    private ByteArrayOutputStream content;

    public Chunk(int number, int chunkSize, ByteArrayOutputStream content) {
        this.number = number;
        this.chunkSize = chunkSize;
        this.content = content;
    }


}
