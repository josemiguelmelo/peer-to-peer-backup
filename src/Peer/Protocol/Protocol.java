package Peer.Protocol;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class Protocol {

    public static String VERSION = "1.0";

    public static Random random = new Random();

    public static Integer maxRetries = 5;

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
}
