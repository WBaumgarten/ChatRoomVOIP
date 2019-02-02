import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

public class Client {

    private final String notif = " *** ";
    private ObjectOutputStream clientOut;
    private ObjectInputStream clientIn;
    private Socket socket;
    private final String server;
    private String username;
    private String hostname;
    private final int port;
    private static byte audioData[];
    private int VNID = 0;
    boolean stopaudioCapture = false;
    boolean stopVNCapture = false;
    int gramSocket;
    ByteArrayOutputStream byteOutputStream;
    AudioFormat adFormat;
    TargetDataLine targetDataLine;
    AudioInputStream InputStream;
    SourceDataLine sourceLine;
    DatagramSocket serverSocket;
    String destAddress;
    int destPort;
    ArrayList<String> destAddressList = new ArrayList<>();
    ArrayList<Integer> destPortList = new ArrayList<>();
    ArrayList<VoiceNote> voiceNotes = new ArrayList<>();
    private final int SIZE = 20000;
    private boolean isInCall = false;

    public boolean isCalling() {
        return isInCall;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String name) {
        this.username = name;
    }

    public boolean checkConnection() {
        return socket.isConnected();
    }

    public Client(String server, int port, String username, int gramSocket, String hostname) {
        this.server = server;
        this.hostname = hostname;
        this.username = username;
        this.port = port;
        this.gramSocket = gramSocket;
    }

    /**
     * Start running the client.
     *
     * @return
     */
    public boolean start() {
        try {
            socket = new Socket(server, port);
        } catch (IOException e) {
            ClientUI.printToOut(notif + "Error connecting to server on port number: " + port + "\nTry checking if the port number is correct." + notif);
            return false;
        }

        try {
            clientIn = new ObjectInputStream(socket.getInputStream());
            clientOut = new ObjectOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            ClientUI.printToOut(notif + "Exception creating new Input/output Streams" + notif);
            return false;
        }

        try {
            Properties initialProps = new Properties(username, hostname, 0);
            clientOut.writeObject(initialProps);
            this.gramSocket = clientIn.readInt();
            System.out.println("Gramsocket received: " + gramSocket);
            serverSocket = new DatagramSocket(gramSocket);
        } catch (IOException e) {
            ClientUI.printToOut(notif + "Error during login" + notif);
            disconnect();
            return false;
        }

        //new ReceiveAudio().start();
        return true;
    }

    public void startListeningFromServer() {
        new ListenFromServer().start();
    }

    /**
     * Send a message as a ChatMesage object
     *
     * @param msg
     */
    public void sendMessage(ChatMessage msg) {
        try {
            clientOut.writeObject(msg);
        } catch (IOException e) {
            ClientUI.printToOut(notif + "Exception writing to server." + notif);
        }
    }

    /**
     * Read message in from stream.
     *
     * @return
     */
    public String readMessage() {
        try {
            String msg = (String) clientIn.readObject();
            return msg;
        } catch (IOException | ClassNotFoundException ex) {
            return "Error reading in messages.";
        }
    }

    /*
	 * When something goes wrong
	 * Close the Input/Output streams and disconnect
     */
    public void disconnect() {

        sendMessage(new ChatMessage(ChatMessage.LOGOUT, ""));
        try {
            if (clientIn != null) {
                clientIn.close();
            }
        } catch (IOException e) {
            System.err.println(e);
        }
        try {
            if (clientOut != null) {
                clientOut.close();
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

    /*
	 * a class that waits for the message from the server
     */
    class ListenFromServer extends Thread {

        // add mutex locks for threads to have sole acces to sending stream
        @Override
        public void run() {
            while (true) {
                try {
                    Object o = clientIn.readObject();
                    if (o instanceof String) {
                        String msg = (String) o;
                        if (msg.startsWith("***UPDATE***")) {
                            ClientUI.txaConnected.setText(msg.substring(12));
                            if (isInCall) {
                                ClientUI.txfCall.setBackground(Color.green);
                            } else {
                                ClientUI.txfCall.setBackground(Color.red);
                            }
                        } else {
                            ClientUI.printToOut(msg);
                        }
                    } else if (o instanceof Properties) {
                        Properties p = (Properties) o;
                        if (p.getType() == 0) {
                            destPort = p.getGramSocket();
                            destAddress = p.getHostname();
                            System.out.println(username + " dest:" + destPort);
                            captureAudio(p.getGramSocket(), p.getHostname());
                        } else {
                            Properties props = (Properties) o;
                            captureAudio(props.getGramSocketList(), props.getHostnameList());
                        }
                    } else if (o instanceof ChatMessage) {
                        ChatMessage m = (ChatMessage) o;
                        if (m.getType() == ChatMessage.STOPCALL) {
                            ClientUI.printToOut("Call ended");
                            stopAudioCapture();
                        }
                    } else if (o instanceof VoiceNote) {
                        VoiceNote vn = (VoiceNote) o;
                        vn.setId(VNID++);
                        voiceNotes.add(vn);
                        ClientUI.printToOut("VN received from " + vn.getSrc() + " with id " + vn.getId());
                    }
                } catch (IOException e) {
                    ClientUI.printToOut(notif + "Server has closed the connection." + notif);
                    break;
                } catch (ClassNotFoundException e2) {

                }
            }
        }
    }

    /**
     * Start capture audio for a direct call.
     *
     * @param destSocket
     * @param destAddress
     */
    public void captureAudio(int destSocket, String destAddress) {
        try {
            ClientUI.printToOut("Call being started");
            new ReceiveAudio().start();
            isInCall = true;
            adFormat = getAudioFormat();
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, adFormat);
            targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
            targetDataLine.open(adFormat);
            targetDataLine.start();

            CaptureThread captureThread = new CaptureThread(gramSocket, destSocket, destAddress);
            captureThread.start();
        } catch (Exception e) {
            StackTraceElement stackEle[] = e.getStackTrace();
            for (StackTraceElement val : stackEle) {
                System.out.println(val);
            }
            System.exit(0);
        }
    }

    /*
    * Use this method to start a call to specified users(conference calling)
     */
    public void captureAudio(ArrayList<Integer> destSocketList, ArrayList<String> destAddressList) {
        try {
            ClientUI.printToOut("Conference call being started");
            new ReceiveAudio().start();
            isInCall = true;
            adFormat = getAudioFormat();
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, adFormat);
            targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
            targetDataLine.open(adFormat);
            targetDataLine.start();
            CaptureThreadConference captureThread = new CaptureThreadConference(gramSocket, destSocketList, destAddressList);
            captureThread.start();
        } catch (Exception e) {
            StackTraceElement stackEle[] = e.getStackTrace();
            for (StackTraceElement val : stackEle) {
                System.out.println(val);
            }
            System.exit(0);
        }
    }

    /**
     * Stop the thread from capturing audio.
     */
    public void stopAudioCapture() {
        stopaudioCapture = true;
        try {
            targetDataLine.close();
            isInCall = false;
        } catch (Exception e) {
            ClientUI.txaClientOut.append("\nThere is no call to end.\n");
        }
    }

    /**
     * Start recording a voice note.
     */
    public void recordVN() {
        try {
            ClientUI.printToOut("VN record starting");
            adFormat = getAudioFormat();
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, adFormat);
            targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
            targetDataLine.open(adFormat);
            targetDataLine.start();

            RecordVNThread recordThread = new RecordVNThread();
            recordThread.start();
        } catch (Exception e) {
            StackTraceElement stackEle[] = e.getStackTrace();
            for (StackTraceElement val : stackEle) {
                System.out.println(val);
            }
            System.exit(0);
        }
    }

    /**
     * Send the latest recorded voice note.
     *
     * @param destUser
     */
    public void sendVN(String destUser) {
        try {
            audioData = byteOutputStream.toByteArray();
        } catch (Exception e) {
            ClientUI.txaClientOut.append("> Record a VoiceNote first!\n");
            return;
        }
        stopVNCapture = true;
        VoiceNote vn = new VoiceNote(audioData, destUser, username);
        try {
            clientOut.writeObject(vn);
        } catch (IOException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Play audio received from UDP.
     */
    public void playAudio() {
        try {
            byte audioData[] = byteOutputStream.toByteArray();
            InputStream byteInputStream = new ByteArrayInputStream(audioData);
            AudioFormat format = getAudioFormat();
            InputStream = new AudioInputStream(byteInputStream, format, audioData.length / format.getFrameSize());
            DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, format);
            sourceLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
            sourceLine.open(format);
            sourceLine.start();
            Thread playThread = new Thread(new PlayThread());
            playThread.start();
        } catch (Exception e) {
            System.out.println(e);
            System.exit(0);
        }
    }

    /**
     * Play audio received as a voice note.
     *
     * @param audioData
     */
    public void playAudio(byte audioData[]) {
        try {
            InputStream byteInputStream = new ByteArrayInputStream(audioData);
            AudioFormat format = getAudioFormat();
            InputStream = new AudioInputStream(byteInputStream, format, audioData.length / format.getFrameSize());
            DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, format);
            sourceLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
            sourceLine.open(format);
            sourceLine.start();
            Thread playThread = new Thread(new PlayThreadVoiceNote());
            playThread.start();
        } catch (Exception e) {
            System.out.println(e);
            System.exit(0);
        }
    }

    public void playVN(int id) {
        for (VoiceNote voiceNote : voiceNotes) {
            if (voiceNote.getId() == id) {
                playAudio(voiceNote.getAudioData());
                return;
            }
        }
        ClientUI.txaClientOut.append("There is no VoiceNote with the id " + id + "\n");
    }

    private AudioFormat getAudioFormat() {
        float sampleRate = 16000.0F;
        int sampleInbits = 16;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = false;
        return new AudioFormat(sampleRate, sampleInbits, channels, signed, bigEndian);
    }

    class CaptureThread extends Thread {

        byte tempBuffer[] = new byte[SIZE];
        int socketSender;
        int socketRecv;
        String address;

        public CaptureThread(int socketSender, int socketRecv, String destAddress) {
            this.socketSender = socketSender;
            this.socketRecv = socketRecv;
            this.address = destAddress;
        }

        @Override
        public void run() {
            stopaudioCapture = false;
            try {
                System.out.println("Capture audio");
                InetAddress IPAddress = InetAddress.getByName(address);
                while (!stopaudioCapture) {
                    int cnt = targetDataLine.read(tempBuffer, 0, tempBuffer.length);
                    if (cnt > 0) {
                        DatagramPacket sendPacket = new DatagramPacket(tempBuffer, tempBuffer.length, IPAddress, socketRecv);
                        serverSocket.send(sendPacket);
                    }
                }
                sourceLine.drain();
                sourceLine.close();
            } catch (IOException e) {
                ClientUI.printToOut("CaptureThread::run()" + e);
            }
        }
    }

    class CaptureThreadConference extends Thread {

        byte tempBuffer[] = new byte[SIZE];
        int socketSender;
        ArrayList<Integer> socketRecv;
        ArrayList<String> address;
        ArrayList<InetAddress> ipAddresses = new ArrayList<>();

        public CaptureThreadConference(int socketSender, ArrayList<Integer> socketRecv, ArrayList<String> address) {
            this.socketSender = socketSender;
            this.socketRecv = socketRecv;
            this.address = address;
            for (String cur : address) {
                try {
                    ipAddresses.add(InetAddress.getByName(cur));
                } catch (UnknownHostException ex) {
                    Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        @Override
        public void run() {
            stopaudioCapture = false;
            try {
                System.out.println("Capture audio");
                while (!stopaudioCapture) {
                    int cnt = targetDataLine.read(tempBuffer, 0, tempBuffer.length);
                    if (cnt > 0) {
                        for (int i = 0; i < ipAddresses.size(); i++) {
                            DatagramPacket sendPacket = new DatagramPacket(tempBuffer, tempBuffer.length, ipAddresses.get(i), socketRecv.get(i));
                            serverSocket.send(sendPacket);
                        }
                    }
                }
                sourceLine.drain();
                sourceLine.close();
            } catch (IOException e) {
                ClientUI.printToOut("CaptureThread::run()" + e);
            }
        }
    }

    class RecordVNThread extends Thread {

        byte tempBuffer[] = new byte[SIZE];

        @Override
        public void run() {
            byteOutputStream = new ByteArrayOutputStream();
            stopVNCapture = false;
            try {
                System.out.println("Recording audio");
                while (!stopVNCapture) {
                    int cnt = targetDataLine.read(tempBuffer, 0, tempBuffer.length);
                    if (cnt > 0) {
                        byteOutputStream.write(tempBuffer, 0, cnt);
                    }
                }
                byteOutputStream.close();
            } catch (Exception e) {
                ClientUI.printToOut("RecordVNThread::run()" + e);
            }
        }
    }

    class PlayThread extends Thread {

        byte tempBuffer[] = new byte[SIZE];

        @Override
        public void run() {
            try {
                int cnt;
                while ((cnt = InputStream.read(tempBuffer, 0, tempBuffer.length)) != -1) {
                    if (cnt > 0) {
                        sourceLine.write(tempBuffer, 0, cnt);
                    }
                }

            } catch (IOException e) {
                System.out.println(e);
                System.exit(0);
            }
        }
    }

    class PlayThreadVoiceNote extends Thread {

        byte tempBuffer[] = new byte[SIZE];

        @Override
        public void run() {
            try {
                int cnt;
                while ((cnt = InputStream.read(tempBuffer, 0, tempBuffer.length)) != -1) {
                    if (cnt > 0) {
                        sourceLine.write(tempBuffer, 0, cnt);
                    }
                }
                sourceLine.drain();
                sourceLine.close();
            } catch (IOException e) {
                System.out.println(e);
                System.exit(0);
            }
        }
    }

    class ReceiveAudio extends Thread {

        @Override
        public void run() {
            try {
                byte[] receiveData = new byte[SIZE];
                while (true) {
                    System.out.println("Waiting for audio packets");
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    serverSocket.receive(receivePacket);
                    System.out.println("RECEIVED: " + receivePacket.getAddress().getHostAddress() + " " + receivePacket.getPort());
                    try {
                        byte audioData[] = receivePacket.getData();
                        InputStream byteInputStream = new ByteArrayInputStream(audioData);
                        AudioFormat adFormat = getAudioFormat();
                        InputStream = new AudioInputStream(byteInputStream, adFormat, audioData.length / adFormat.getFrameSize());
                        DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, adFormat);
                        sourceLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
                        sourceLine.open(adFormat);
                        sourceLine.start();
                        Thread playThread = new Thread(new PlayThread());
                        playThread.start();
                    } catch (Exception e) {
                        System.out.println(e);
                        System.exit(0);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
