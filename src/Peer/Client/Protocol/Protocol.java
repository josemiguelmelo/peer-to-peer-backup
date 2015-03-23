package Peer.Client.Protocol;

/**
 * Created by ruigomes on 23/03/15.
 */
public class Protocol {

    public static void main(String[] args)
    {
        File file = new File("/Users/ruigomes/Desktop/image.gif", 10);
        System.out.println(file.getChunks().size());
    }
}
