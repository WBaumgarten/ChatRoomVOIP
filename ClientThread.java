import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientThread extends Thread implements Comparable {

    Socket socket;
    ObjectInputStream inputStream;
    ObjectOutputStream outputStream;
    int id;
    String username;
    ChatMessage chatMsg;
    int gramSocket;
    String hostname;
    String destAddress = "";
    int destGramSocket = 0;
    ArrayList<String> destAddressList = new ArrayList<>();
    ArrayList<Integer> destPortList = new ArrayList<>();

    public ClientThread(Socket socket, int uniqueID) {
        id = uniqueID;
        this.socket = socket;
        try {
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            inputStream = new ObjectInputStream(socket.getInputStream());
            Properties initial = (Properties) inputStream.readObject();
            username = initial.getUsername();
            hostname = initial.getHostname();
            gramSocket = ServerUI.getPort();
            outputStream.writeInt(gramSocket);
        } catch (IOException | ClassNotFoundException e) {
            ServerUI.printToOutput("Exception creating new Input/Output streams: " + e);
        }
    }

    @Override
    public int compareTo(Object o) {
        ClientThread compareClient = (ClientThread) o;
        String compareName = ((ClientThread) compareClient).getUsername();
        /* For Ascending order*/
        return this.username.compareTo(compareName);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public void run() {
        boolean stillRunning = true;

        while (stillRunning) {
            try {
                Object o = inputStream.readObject();
                if (o instanceof VoiceNote) {
                    sendVoiceNoteToThread(o);
                } else {
                    chatMsg = (ChatMessage) o;
                    String msg = chatMsg.getMessage();
                    switch (chatMsg.getType()) {
                        case ChatMessage.MESSAGE:
                            boolean confirmation = ServerUI.broadcast(username + ": " + msg);
                            if (confirmation == false) {
                                String errMsg = " *** Sorry. No such user exists. *** ";
                                writeMsg(errMsg);
                            }
                            break;

                        case ChatMessage.LOGOUT:
                            stillRunning = false;
                            break;

                        case ChatMessage.WHOISIN:
                            writeMsg("List of users connected: \n");
                            int i = 1;
                            for (ClientThread curClient : ServerUI.clientList) {
                                writeMsg("- " + curClient.username);
                            }
                            break;

                        case ChatMessage.CALL:
                            directCall(msg);
                            break;

                        case ChatMessage.STOPCALL:
                            if (destAddress.length() > 0) {
                                ChatMessage m = new ChatMessage(ChatMessage.STOPCALL, "");
                                for (ClientThread curClient : ServerUI.clientList) {
                                    if (curClient.hostname.equals(destAddress)) {
                                        curClient.writeObject(m);
                                    }
                                }
                            }
                            break;

                        case ChatMessage.CONF:
                            conferenceCall();
                            break;

                        case ChatMessage.CONFSTOP:
                            for (int j = 0; j < destAddressList.size(); j++) {
                                String curDestAddress = destAddressList.get(j);
                                int curDestPort = destPortList.get(j);
                                ChatMessage m = new ChatMessage(ChatMessage.STOPCALL, "");
                                for (ClientThread curClient : ServerUI.clientList) {
                                    if (curClient.gramSocket == (curDestPort)) {
                                        curClient.writeObject(m);
                                        break;
                                    }
                                }
                            }
                            break;

                        case ChatMessage.CHAN:
                            createChannelRequest();
                            break;

                        case ChatMessage.CHANMSG:
                            String chanName = chatMsg.getMessage().split(" ")[0].trim();
                            String txt = chatMsg.getMessage().substring(chanName.length() + 1);
                            ServerUI.sendChanMsg(this, chanName, txt);
                            break;

                        case ChatMessage.CHANLEAVE:
                            String chanName2 = chatMsg.getMessage();
                            ServerUI.leaveChannel(this, chanName2);
                            break;
                    }
                }
            } catch (ClassNotFoundException e) {
                ServerUI.printToOutput(username + "Class not found: " + e);
                break;
            } catch (IOException ex) {
                Logger.getLogger(ClientThread.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
        ServerUI.removeClient(id);
        close();
    }

    public void createChannelRequest() {
        Channel chan = new Channel(chatMsg.getMessage());
        ArrayList<ClientThread> subList = new ArrayList<>();
        for (String curUsername : chatMsg.getUserList()) {
            for (ClientThread curClient : ServerUI.clientList) {
                if (curClient.getUsername().equals(curUsername)) {
                    subList.add(curClient);
                    break;
                }
            }
        }
        chan.setUsers(subList);
        boolean create = true;
        for (Channel channelListEntry : ServerUI.channelList) {
            if (channelListEntry.getName().equals(chan.getName())) {
                writeMsg("A channel \"" + chan.getName() + "\" already exists.");
                create = false;
                break;
            }
        }
        if (create) {
            ServerUI.channelList.add(chan);
        }
    }

    public void conferenceCall() {
        ArrayList<String> userList = chatMsg.getUserList();
        ArrayList<String> destAddressesTemp = new ArrayList<>();
        ArrayList<Integer> destPortTemp = new ArrayList<>();

        for (String curUsername : userList) {
            for (ClientThread curClient : ServerUI.clientList) {
                if (curClient.getUsername().equals(curUsername)) {
                    destAddressesTemp.add(curClient.hostname);
                    destPortTemp.add(curClient.gramSocket);
                }
            }
        }

        for (int j = 0; j < userList.size(); j++) {
            String curUser = userList.get(j);
            for (ClientThread curClient : ServerUI.clientList) {
                if (curClient.getUsername().equals(curUser)) {
                    ArrayList<String> t1 = new ArrayList<>(destAddressesTemp);
                    ArrayList<Integer> t2 = new ArrayList<>(destPortTemp);
                    t1.remove(destAddressesTemp.get(j));
                    t2.remove(destPortTemp.get(j));
                    curClient.destAddressList = t1;
                    curClient.destPortList = t2;
                    break;
                }
            }
        }

        for (String curUsername : userList) {
            for (ClientThread curClient : ServerUI.clientList) {
                if (curUsername.equals(curClient.getUsername())) {
                    Properties p = new Properties(curClient.destAddressList, curClient.destPortList, 1);
                    curClient.writeObject(p);
                }
            }
        }
    }

    public void directCall(String msg) {
        String destUser = msg.trim();
        // from the message, check if the user is still connected
        boolean stillConnected = false;

        String destUsername = "";
        for (ClientThread curClient : ServerUI.clientList) {
            if (curClient.getUsername().equals(destUser)) {
                destUsername = curClient.getUsername();
                stillConnected = true;
                // retrieve the users address and gramSocketPort
                destAddress = curClient.hostname;
                destGramSocket = curClient.gramSocket;
                System.out.println(username + " dest: " + destGramSocket);

                Properties p = new Properties(this.username, this.hostname, this.gramSocket);
                curClient.destAddress = this.hostname;
                curClient.destGramSocket = this.gramSocket;
                curClient.writeObject(p);
                break;
            }
        }

        if (stillConnected) {
            // send back to the source client who will start audioCapture
            Properties p = new Properties("", destAddress, destGramSocket);
            writeObject(p);
            ServerUI.printToOutput(this.username + " is making a call to " + destUsername);
        } else {
            //send message to source client that the client is not connected.
            writeMsg("The user you are trying to call is not connected.");
        }
    }

    public void sendVoiceNoteToThread(Object o) {
        VoiceNote vn = (VoiceNote) o;
        // from the message, check if the user is still connected
        boolean stillConnectedV = false;

        String destUsernameV = "";
        for (ClientThread curClient : ServerUI.clientList) {
            if (curClient.getUsername().equals(vn.getDest())) {
                destUsernameV = curClient.getUsername();
                stillConnectedV = true;
                // retrieve the users address and gramSocketPort
                destAddress = curClient.hostname;
                destGramSocket = curClient.gramSocket;
                System.out.println(username + " dest: " + destGramSocket);

                curClient.destAddress = this.hostname;
                curClient.destGramSocket = this.gramSocket;
                curClient.writeObject(vn);
                break;
            }
        }

        if (stillConnectedV) {
            // send back to the source client who will start audioCapture
            ServerUI.printToOutput(this.username + " sent a VoiceNote to " + destUsernameV);
            writeMsg("VoiceNote sent!\n");
        } else {
            //send message to source client that the client is not connected.
            writeMsg("The recepient user is not connected.");
        }
    }

    public boolean writeMsg(String msg) {
        if (!socket.isConnected()) {
            close();
            return false;
        }

        try {
            outputStream.writeObject(msg);
        } catch (IOException e) {
            ServerUI.printToOutput("Error sending message to " + username + "\n" + e.toString());
        }
        return true;
    }

    public boolean writeObject(Object msg) {
        if (!socket.isConnected()) {
            close();
            return false;
        }

        try {
            outputStream.writeObject(msg);
        } catch (IOException e) {
            ServerUI.printToOutput("Error sending message to " + username + "\n" + e.toString());
        }
        return true;
    }

    public void close() {
        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException e) {
            System.err.println(e);
        }

        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            System.err.println(e);
        }

        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println(e);
        }
    }

}
