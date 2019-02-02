import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.DefaultCaret;

public class ServerUI extends javax.swing.JFrame {

    private final int port = 1500;
    public static ArrayList<ClientThread> clientList;
    public static ArrayList<Channel> channelList;
    ObjectInputStream inputStream;
    ObjectOutputStream outputStream;
    ServerSocket serverSocket;
    int uniqueID = 0;
    
    static int newPort = 8786;
    
    public static int getPort() {
        return newPort++;        
    }

    /**
     * Creates new form Server
     */
    public ServerUI() {
        initComponents();
        clientList = new ArrayList<>();
        channelList = new ArrayList<>();
        new Start().start();
        new UpdateConnected().start();
    }

    class UpdateConnected extends Thread {

        @Override
        public void run() {
            while (true) {
                updateConnected();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(ServerUI.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    class Start extends Thread {

        @Override
        public void run() {
            boolean stillRunning = true;
            DefaultCaret caret = (DefaultCaret) txaOutput.getCaret();
            caret.setUpdatePolicy(DefaultCaret.OUT_BOTTOM);
            DefaultCaret caret2 = (DefaultCaret) txaConnected.getCaret();
            caret2.setUpdatePolicy(DefaultCaret.OUT_BOTTOM);
            try {
                serverSocket = new ServerSocket(port);
                while (stillRunning) {
                    printToOutput("Waiting for clients to connect.");
                    Socket socket = serverSocket.accept();
                    ClientThread clientThread = new ClientThread(socket, uniqueID++);
                    boolean nameExists = false;
                    for (ClientThread curClient : clientList) {
                        if (curClient.getUsername().equals(clientThread.getUsername())) {
                            nameExists = true;
                            break;
                        }
                    }

                    if (nameExists) {
                        clientThread.writeMsg("false");
                        clientThread.close();
                    } else {
                        clientList.add(clientThread);
                        clientThread.writeMsg("true");
                        broadcast(" *** " + clientThread.getUsername() + " has joined the chatroom. *** ");
                        updateConnected();
                        clientThread.start();
                    }

                }

                try {
                    serverSocket.close();
                    for (ClientThread curClient : clientList) {
                        curClient.close();
                    }
                } catch (IOException e) {
                    printToOutput("Error closing the server and clients.");
                }
            } catch (IOException ex) {
                Logger.getLogger(ServerUI.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
    }

    public static void printToOutput(String s) {
        txaOutput.append(s + "\n");
    }

    public static void printToConnected(String s) {
        txaConnected.append(s + "\n");
    }

    synchronized static void removeClient(int id) {
        String disconnectedClient = "";
        for (int i = 0; i < clientList.size(); i++) {
            ClientThread curClient = clientList.get(i);
            if (curClient.id == id) {
                disconnectedClient = curClient.getUsername();
                for (int ch = 0; ch < channelList.size(); ch++) {
                    Channel channelListEntry = channelList.get(ch);
                    for (int j = 0; j < channelListEntry.getUsers().size(); j++) {
                        ClientThread ct = channelListEntry.getUsers().get(j);
                        if (ct.getUsername().equals(curClient.getUsername())) {
                            channelListEntry.getUsers().remove(j);
                        }
                    }
                    if (channelListEntry.getUsers().isEmpty()) {
                        channelList.remove(ch);
                        break;
                    }
                }
                clientList.remove(i);
                break;
            }
        }
        broadcast("*** " + disconnectedClient + " has left the chatroom. ***");
    }

    void updateConnected() {
        txaConnected.setText("");
        String out = "";
        Collections.sort(clientList);
        for (ClientThread clientThread : clientList) {
            printToConnected(clientThread.getUsername());
            out = out + clientThread.getUsername() + "\n";
        }

        out = out + "\nGROUPS\n";
        for (Channel channelListEntry : channelList) {
            Collections.sort(channelListEntry.getUsers());
            out = out + "--------------------------------\n" + channelListEntry.getName() + ":\n";
            for (ClientThread ct : channelListEntry.getUsers()) {
                out = out + ct.username + "\n";
            }
        }
        out = out + "--------------------------------\n";

        broadcast("***UPDATE***".concat(out));
    }

    public static synchronized void sendChanMsg(ClientThread sourceUser, String chanName, String message) {
        boolean channelFound = false;
        boolean senderFound = false;
        String finalMsg = chanName + ": " + sourceUser.username + ": " + message;
        for (Channel channelListEntry : channelList) {
            if (channelListEntry.getName().equals(chanName)) {
                channelFound = true;
                for (ClientThread ct : channelListEntry.getUsers()) {
                    if (ct.username.equals(sourceUser.username)) {
                        senderFound = true;
                        break;
                    }
                }
                if (senderFound) {
                    for (ClientThread ct : channelListEntry.getUsers()) {
                        if (!ct.writeMsg(finalMsg)) {
                            clientList.remove(ct);
                            printToOutput("Disconnected Client " + ct.username + " removed from list.\n");
                        }
                    }
                    break;
                } else {
                    sourceUser.writeMsg("You are not in this channel!\n");
                    break;
                }
            }
        }

        if (!channelFound) {
            sourceUser.writeMsg("Channel " + chanName + " does not exist.\n");
        }
    }

    public static void leaveChannel(ClientThread sourceUser, String chanName) {
        boolean chanFound = false;
        for (int ch = 0; ch < channelList.size(); ch++) {
            //for (Channel channelListEntry : channelList) {
            Channel channelListEntry = channelList.get(ch);
            if (channelListEntry.getName().equals(chanName)) {
                chanFound = true;
                for (int i = 0; i < channelListEntry.getUsers().size(); i++) {
                    //for (ClientThread ct : channelListEntry.getUsers()) {
                    ClientThread ct = channelListEntry.getUsers().get(i);
                    if (ct.getUsername().equals(sourceUser.getUsername())) {
                        channelListEntry.getUsers().remove(i);
                        if (channelListEntry.getUsers().isEmpty()) {
                            channelList.remove(ch);
                            break;
                        }
                    }
                }
                break;
            }
        }

        if (!chanFound) {
            sourceUser.writeMsg("There is no channel \"" + chanName + "\".\n");
        }
    }

    public static synchronized boolean broadcast(String message) {
        // check whether message us an UPDATE control
        if (message.startsWith("***UPDATE***")) {
            // we loop in reverse order in case we would have to remove a Client
            // because it has disconnected
            for (int i = clientList.size(); --i >= 0;) {
                ClientThread ct = clientList.get(i);
                // try to write to the Client if it fails remove it from the list
                if (!ct.writeMsg(message)) {
                    clientList.remove(i);
                    printToOutput("Disconnected Client " + ct.username + " removed from list.\n");
                }
            }
            return true;
        }

        // to check if message is private i.e. client to client message
        String[] w = message.split(" ", 3);

        boolean isPrivate = false;
        if (w[1].charAt(0) == '@') {
            isPrivate = true;
        }

        // if private message, send message to mentioned username only
        if (isPrivate == true) {
            String sourceUser = w[0].substring(0, w[0].length() - 1);

            String tocheck = w[1].substring(1, w[1].length());
            int sent = 0;
            message = w[0] + w[2];
            String messageLf = message + "\n";
            boolean found = false;
            for (int y = clientList.size(); --y >= 0;) {
                ClientThread ct1 = clientList.get(y);

                String check = ct1.getUsername();

                if (check.equals(sourceUser)) {
                    if (!ct1.writeMsg(messageLf)) {
                        clientList.remove(y);
                        printToOutput("Disconnected Client " + ct1.username + " removed from list.\n");
                    }
                    sent++;
                    if (sent == 2) {
                        break;
                    }
                }

                if (check.equals(tocheck)) {
                    if (!ct1.writeMsg(messageLf)) {
                        clientList.remove(y);
                        printToOutput("Disconnected Client " + ct1.username + " removed from list.\n");
                    }

                    found = true;
                    sent++;
                    if (sent == 2) {
                        break;
                    }
                }

            }
            // mentioned user not found, return false
            if (found != true) {
                return false;
            }
        } // if message is a broadcast message
        else {
            String messageLf = message + "\n";
            printToOutput(messageLf);
            // we loop in reverse order in case we would have to remove a Client
            // because it has disconnected
            for (int i = clientList.size(); --i >= 0;) {
                ClientThread ct = clientList.get(i);
                // try to write to the Client if it fails remove it from the list
                if (!ct.writeMsg(messageLf)) {
                    clientList.remove(i);
                    printToOutput("Disconnected Client " + ct.username + " removed from list.\n");
                }
            }
        }
        return true;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        pnlBackground = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        txaOutput = new javax.swing.JTextArea();
        jScrollPane2 = new javax.swing.JScrollPane();
        txaConnected = new javax.swing.JTextArea();
        jLabel3 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setResizable(false);

        pnlBackground.setBackground(new java.awt.Color(57, 186, 199));

        txaOutput.setEditable(false);
        txaOutput.setBackground(new java.awt.Color(254, 254, 254));
        txaOutput.setColumns(20);
        txaOutput.setFont(new java.awt.Font("Monospaced", 0, 15)); // NOI18N
        txaOutput.setForeground(new java.awt.Color(1, 1, 1));
        txaOutput.setRows(5);
        txaOutput.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(99, 99, 99), 5, true));
        jScrollPane1.setViewportView(txaOutput);

        txaConnected.setEditable(false);
        txaConnected.setBackground(new java.awt.Color(254, 254, 254));
        txaConnected.setColumns(20);
        txaConnected.setFont(new java.awt.Font("Monospaced", 0, 15)); // NOI18N
        txaConnected.setForeground(new java.awt.Color(1, 1, 1));
        txaConnected.setRows(5);
        txaConnected.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(99, 99, 99), 5, true));
        jScrollPane2.setViewportView(txaConnected);

        jLabel3.setFont(new java.awt.Font("Ubuntu", 1, 36)); // NOI18N
        jLabel3.setText("VOIP server");

        jLabel1.setFont(new java.awt.Font("Ubuntu", 1, 18)); // NOI18N
        jLabel1.setText("Connected users");

        javax.swing.GroupLayout pnlBackgroundLayout = new javax.swing.GroupLayout(pnlBackground);
        pnlBackground.setLayout(pnlBackgroundLayout);
        pnlBackgroundLayout.setHorizontalGroup(
            pnlBackgroundLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlBackgroundLayout.createSequentialGroup()
                .addContainerGap(19, Short.MAX_VALUE)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 356, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 231, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(30, 30, 30))
            .addGroup(pnlBackgroundLayout.createSequentialGroup()
                .addGap(88, 88, 88)
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel1)
                .addGap(71, 71, 71))
        );
        pnlBackgroundLayout.setVerticalGroup(
            pnlBackgroundLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlBackgroundLayout.createSequentialGroup()
                .addGap(10, 10, 10)
                .addGroup(pnlBackgroundLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(pnlBackgroundLayout.createSequentialGroup()
                        .addGroup(pnlBackgroundLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel1)
                            .addComponent(jLabel3))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 420, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 420, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(24, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pnlBackground, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pnlBackground, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(ServerUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(ServerUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(ServerUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(ServerUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new ServerUI().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JPanel pnlBackground;
    private static javax.swing.JTextArea txaConnected;
    private static javax.swing.JTextArea txaOutput;
    // End of variables declaration//GEN-END:variables
}
