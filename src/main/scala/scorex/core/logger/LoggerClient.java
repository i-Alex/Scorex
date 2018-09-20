package scorex.core.logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.*;
import java.nio.charset.Charset;


public class LoggerClient {
    private static LoggerClient ourInstance = new LoggerClient();

    public static LoggerClient getInstance() {
        return ourInstance;
    }

    private Socket clientSocket = null;
    private PrintStream outToServer = null;

    private String nodeName = "";
    private InetSocketAddress address = null;

    private LoggerClient() {
        clientSocket = new Socket();
    }

    private boolean tryToConnect() {
        try {
            if(address != null)
                clientSocket.connect(address);
            else {
                InetAddress addr = InetAddress.getByName("127.0.0.1");
                int port = 7879;
                clientSocket.connect(new InetSocketAddress(addr, port));
            }
        } catch (IOException e) {
            System.out.println("!!! Failed to connect to Logger Server.");
            return false;
        }

        try {
            outToServer = new PrintStream(clientSocket.getOutputStream());
        } catch (IOException e) {
            System.out.println("!!! Failed to get output stream to Logger Server.");
            return false;
        }
        return true;
    }

    public boolean logToServer(String logMessage) {
        if(!clientSocket.isConnected()) {
            if(!tryToConnect()) {
                System.out.println("!!! Failed to send logs to Logger Server: can't connect.");
                return false;
            }
        }

        outToServer.print(nodeName + "," + logMessage);
        outToServer.flush();
        return true;
    }

    public void setNodeName(String name) {
        nodeName = name;
    }

    public void applySettings(InetSocketAddress addr) {
        address = addr;
    }
}

