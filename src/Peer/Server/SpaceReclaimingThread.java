package Peer.Server;


import Peer.Protocol.Protocol;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.Charset;

public class SpaceReclaimingThread extends Thread{

    int mcPort;
    MulticastSocket mcSocket;
    InetAddress mcAddress;

    int mdbPort;
    MulticastSocket mdbSocket;
    InetAddress mdbAddress;

    int mdrPort;
    MulticastSocket mdrSocket;
    InetAddress mdrAddress;

    MulticastSocket activeSocket;


    int maximumSpace;
    String savePath;
    JSONArray chunksReplication;

    File directory;




    public SpaceReclaimingThread(String mcIp, int mcPort, String mdbIp, int mdbPort, String mdrIp, int mdrPort, String socket) throws IOException{
        this.loadSettings();
    }

    public void loadSettings() throws IOException {
        String configString = Protocol.readFile(Protocol.settingsPath, Charset.defaultCharset());

        JSONObject config = new JSONObject(configString.trim());

        this.maximumSpace = config.getInt("maximumSpace");
        System.out.println("Max Space: " + this.maximumSpace);

        this.savePath = config.getString("relativeSavePath");
        System.out.println("Save Path: " + this.savePath);
    }


    public static long getFolderSize(File dir) {
        long size = 0;
        for (File file : dir.listFiles()) {
            if (file.isFile()) {
                System.out.println(file.getName() + " " + file.length());
                size += file.length();
            }
            else
                size += getFolderSize(file);
        }
        return size;
    }

    /** Returns the biggest file in folder **/
    public File getBiggestFile(){
        File biggestFile = null;
        for (File file : directory.listFiles()) {

            if(biggestFile == null)
                biggestFile = file;

            if(biggestFile.length() < file.length())
                biggestFile = file;

        }

        return biggestFile;
    }


    /** Removes the biggest file **/
    public void reduceSpaceUsed(){
        File fileToDelete = getBiggestFile();

        String filename = fileToDelete.getName();

        String[] fileInformation = filename.split("-");

        String fileId = fileInformation[1];
        String chunkNo = fileInformation[0];


        if(fileToDelete.delete())
        {
            this.sendMessage("REMOVED " + Protocol.VERSION + " " + fileId + " " + chunkNo + " " + Protocol.crlf() + Protocol.crlf());
        }
    }


    public void sendMessage(String message)
    {
        byte[] buf;

        buf = message.getBytes();

        DatagramPacket packet;
        packet = new DatagramPacket(buf, buf.length, mcAddress, mcPort);

        try {
            this.mcSocket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run()
    {
        this.directory = new File(savePath);

            long size = getFolderSize(this.directory);

            if(size >= maximumSpace)
            {
                reduceSpaceUsed();
            }else
            {
                System.out.println("Size less than maximum");
            }



    }

}
