# Compilation

This project can be compiled with the Java v1.8 compiler. To compile all the Java files in one go, you can use the following command within the src/ directory:

```find . -name "*.java" | xargs javac```

# Running

You should run the Peer.Client.MulticastClient class which will launch all the required threads in order to get this project working. To do so, run the following command:

```java Peer.Client.MulticastClient 224.0.0.3 8888 224.0.0.7 8888 224.0.0.8 8888```

You can then write any of the following commands in the console to use the backup system:

```backup /path/to/file.txt <replication_degree>```

```restore file.txt```

```delete file.txt```

```free <space_in_bytes>```