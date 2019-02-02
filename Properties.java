import java.util.ArrayList;
import java.io.Serializable;

public class Properties implements Serializable {
     private ArrayList<String> usernameList;
    private ArrayList<String> hostnameList;
    private ArrayList<Integer> gramSocketList;  
    private String username;
    private String hostname;
    private int gramSocket;
    private int type = 0;

    public Properties(String username, String hostname, int gramSocket) {
        this.username = username;
        this.hostname = hostname;
        this.gramSocket = gramSocket;
    }
      public Properties( ArrayList<String> hostname, ArrayList<Integer> gramSocket, int type) {        
        this.hostnameList = hostname;
        this.gramSocketList = gramSocket;
        this.type = type;
    }
    public int getType() {
        return type;
    }


    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getGramSocket() {
        return gramSocket;
    }

    public void setGramSocket(int gramSocket) {
        this.gramSocket = gramSocket;
    }
     public ArrayList<String> getUsernameList() {
        return usernameList;
    }

    public void setUsernameList(ArrayList<String> usernameList) {
        this.usernameList = usernameList;
    }

    public ArrayList<String> getHostnameList() {
        return hostnameList;
    }

    public void setHostnameList(ArrayList<String> hostnameList) {
        this.hostnameList = hostnameList;
    }

    public ArrayList<Integer> getGramSocketList() {
        return gramSocketList;
    }

    public void setGramSocketList(ArrayList<Integer> gramSocketList) {
        this.gramSocketList = gramSocketList;
    }
    
    
}
