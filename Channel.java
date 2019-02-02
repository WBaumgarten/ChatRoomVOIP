import java.util.ArrayList;

public class Channel {
    private String name;
    private ArrayList<ClientThread> users;

    public Channel(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addUser(ClientThread user) {
        users.add(user);
    }
    
    public ArrayList<ClientThread> getUsers() {
        return users;
    }

    public void setUsers(ArrayList<ClientThread> users) {
        this.users = users;
    }
}
