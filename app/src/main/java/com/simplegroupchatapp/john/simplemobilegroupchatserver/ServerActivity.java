package com.simplegroupchatapp.john.simplemobilegroupchatserver;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import org.apache.http.conn.util.InetAddressUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Created by John on 4/24/2015.
 */
public class ServerActivity extends Activity {

    private static final String TAG = ServerActivity.class.getSimpleName();

    //The port for the server
    private static final int SERVERSOCKETPORT = 9090;

    private ServerSocket serverSocket;

    private HashMap<Integer, ArrayList<ChatClient>> clientLists;
    private ArrayList<ChatClient> clientList;

    //Widgets
    private TextView serverPortText;
    private TextView serverIPAddressText;
    private TextView chatMessageText;
    private TextView numberOfUsersText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);

        //The list to hold the clients
        clientList = new ArrayList<ChatClient>();

        //Initialize widgets
        serverPortText = (TextView) findViewById(R.id.serverPortText);
        serverIPAddressText = (TextView) findViewById(R.id.serverIPAddressText);
        chatMessageText = (TextView) findViewById(R.id.chatMessageText);
        numberOfUsersText = (TextView) findViewById(R.id.numberOfUsersText);

        serverIPAddressText.setText("Server IP Address: " + getIPAddress(true));
        numberOfUsersText.setText("Current number of users: " + clientList.size());

        //Start the server thread
        Thread serverConnectionThread = new Thread(new ServerThread());
        serverConnectionThread.start();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * Accepts client connections, store them in the client list
     * and start another thread to handle the connection with the client
     */
    private class ServerThread implements Runnable {

        @Override
        public void run() {
            Log.d(TAG, "Before Try");
            try {
                serverSocket = new ServerSocket(SERVERSOCKETPORT);

                ServerActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        serverPortText.setText("Server Port Number: " + serverSocket.getLocalPort());
                    }
                });


                while(true) {
                    Log.d(TAG, "Before accept");
                    //Wait for a new client to connect
                    Socket socket = serverSocket.accept();
                    Log.d(TAG, "Client connected!");
                    //Create a new client
                    ChatClient client = new ChatClient();
                    //Add the new client to the client list
                    clientList.add(client);

                    //Start a new client thread for the new client
                    Thread clientThread = new Thread(new ClientConnectThread(client, socket));
                    clientThread.start();
                }

            } catch (IOException e) {
                    e.printStackTrace();
                }
        } //End of run
    } //End of ServerThread class

    /**
     * A class that extends Runnable and is used to connect to the server
     * and handles responses that are sent and received to and from the connected clients
     */
    private class ClientConnectThread implements Runnable {

        private Socket socket;
        private ChatClient client;

        //The output and input stream
        private DataOutputStream dataOut;
        private DataInputStream dataIn;
        
        //Flags to determine the type of response
        private final String FLAG_NEW = "new";
        private final String FLAG_MESSAGE = "message";
        private final String FLAG_EXIT = "exit";

        //Constructor
        public ClientConnectThread(ChatClient client, Socket socket) {
            this.client = client;
            this.socket = socket;
            this.client.socket = socket;
            this.client.clientConnectThread = this;
            this.client.isConnected = true;
        }

        @Override
        public void run() {
            Log.d(TAG, "Client thread started!");

            try {
                //Initialize the data streams
                dataOut = new DataOutputStream(socket.getOutputStream());
                dataIn = new DataInputStream(socket.getInputStream());

                while (client.isConnected) {
                    if (dataIn.available() > 0) {
                        String receivedMessage = dataIn.readUTF();
                        Log.d(TAG, "Client Response: " + receivedMessage);
                        //Parse the message
                        parseMessage(receivedMessage);

                    }


                } //End of while loop

            } catch (IOException e) {
                e.printStackTrace();
            } finally {

                //Remove the client from the client list
                clientList.remove(client);
                broadcastClientExit();

                //If the output stream is not null, close the stream
                if (dataOut != null) {
                    try {
                        dataOut.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                //If the input stream is not null, close the stream
                if (dataIn != null) {
                    try {
                        dataIn.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                //Update the UI
                ServerActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        chatMessageText.setText(client.name + " has left the room.");
                        numberOfUsersText.setText("Current number of users: " + clientList.size());
                    }
                });


            } //End of finally
        } //End of run method


        private void parseMessage(String receivedMessage) {
            try {
                JSONObject jsonObj = new JSONObject(receivedMessage);

                if (jsonObj.getString("flag").equals(FLAG_NEW)) {
                    client.name = jsonObj.getString("name");

                    //Update the UI
                    ServerActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            chatMessageText.setText(client.name + " has entered the room.");
                            numberOfUsersText.setText("Current number of users: " + clientList.size());
                        }
                    });

                    broadcastNewClient();
                }

                else if (jsonObj.getString("flag").equals(FLAG_MESSAGE)) {
                    broadcastMessage(jsonObj.toString());
                }

                //Disconnect the user
                else if (jsonObj.getString("flag").equals(FLAG_EXIT)) {
                    client.isConnected = false;
                    //broadcastClientExit();

                }

            } catch (JSONException e) {
                e.printStackTrace();
            }


        } //End of parseMessage


        /**
         * Broadcast to all connected clients that a new client has entered
         * and also broadcast the current number of clients in the room.
         */
        private void broadcastNewClient() {
            try {
                JSONObject jsonObjToSend = new JSONObject();
                jsonObjToSend.put("name", client.name);
                jsonObjToSend.put("message", "");
                jsonObjToSend.put("flag", FLAG_NEW);
                jsonObjToSend.put("count", clientList.size());

                for (int i = 0; i < clientList.size(); i++) {
                    clientList.get(i).clientConnectThread.sendMessage(jsonObjToSend.toString());
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

        /**
         * Broadcast to all connected clients that a client has logout
         */
        private void broadcastClientExit() {
            try {
                JSONObject jsonObjToSend = new JSONObject();
                jsonObjToSend.put("name", client.name);
                jsonObjToSend.put("message", "");
                jsonObjToSend.put("flag", FLAG_EXIT);
                jsonObjToSend.put("count", clientList.size());

                for (int i = 0; i < clientList.size(); i++) {
                    clientList.get(i).clientConnectThread.sendMessage(jsonObjToSend.toString());
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        /**
         * Broadcast the message sent from a client to all the clients that are connected
         * @param messageToBroadcast is the message to be broadcasted to all connected clients
         */
        private void broadcastMessage(String messageToBroadcast) {
            for (int i = 0; i < clientList.size(); i++) {
                clientList.get(i).clientConnectThread.sendMessage(messageToBroadcast);
            }
        }

        /**
         * Sends the message to the client
         * @param messageToSend is the message to be sent to the client
         */
        private void sendMessage(String messageToSend) {
            try {
                dataOut.writeUTF(messageToSend);
                dataOut.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    } //End of ClientConnectThread class

    /**
     * Client class
     */
    private class ChatClient {
        private String name;
        private Socket socket;
        private ClientConnectThread clientConnectThread;
        private boolean isConnected;
    } //End of ChatClient class


    /**
     * Taken from stackoverflow, Original Author: Whome
     * Get IP address from first non-localhost interface
     * @param useIPv4 true=return ipv4, false=return ipv6
     * @return  address or empty string
     */
    public static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress().toUpperCase();
                        boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 port suffix
                                return delim<0 ? sAddr : sAddr.substring(0, delim);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) { } // for now eat exceptions
        return "";
    } //End of getIPAddress

}
