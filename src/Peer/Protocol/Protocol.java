package Peer.Protocol;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class Protocol {

    public static String VERSION = "1.0";

    public static Random random = new Random();

    public static Integer maxRetries = 5;

    public static String settingsPath = "config.json";

    public static String filesBackedUp = "files.json";

    public static String chunksReplication = "chunks.json";

    public static Integer minReplicationDegree = 3;

    public static String sha256(String string) throws NoSuchAlgorithmException
    {
        MessageDigest md;

        md = MessageDigest.getInstance("SHA-256");

        md.update(string.getBytes());

        byte[] shaBytes = md.digest();

        return byteArrayToHex(shaBytes);
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a)
            sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    public static String crlf()
    {
        return "" + '\r' + '\n';
    }

    public static String readFile(String path, Charset encoding)
    {
        try {
            byte[] encoded = Files.readAllBytes(Paths.get(path));
            return new String(encoded, encoding);
        } catch(IOException e) {
            System.out.println("Could not find " + path + ".");
        }

        return "";
    }

    public static void writeFile(String path, String string)
    {
        try {
            Files.write(Paths.get(path), string.getBytes());
        } catch (IOException e)
        {
            System.out.println("Could not write " + string + " to " + path);
        }
    }
}
