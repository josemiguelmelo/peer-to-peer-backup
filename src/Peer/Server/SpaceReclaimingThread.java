package Peer.Server;


import Peer.Protocol.Protocol;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

public class SpaceReclaimingThread extends Thread{


    int maximumSpace;
    String savePath;
    JSONArray chunksReplication;



    public SpaceReclaimingThread() throws IOException{
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


    public void run()
    {
        File folder = new File(savePath);
        long size = getFolderSize(folder);

        if(size >= maximumSpace)
        {
            System.out.println("Size exceeds maximum.");
        }else
        {
            System.out.println("Size less than maximum");
        }

    }

}
