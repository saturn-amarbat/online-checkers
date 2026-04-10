import java.io.Serializable;
import java.util.ArrayList;

public class Message implements Serializable {
    private static final long serialVersionUID = 42L;

    public enum Type {
        LOGIN,
        LOGIN_SUCCESS,
        LOGIN_FAIL,
        CLIENT_LIST_UPDATE,
        GLOBAL_CHAT,
        PRIVATE_CHAT
    }

    public Type type;
    public String sender;
    public ArrayList<String> recipients;
    public String textContent;
    public ArrayList<String> onlineUsers;

    public Message() {
        this.recipients = new ArrayList<>();
        this.onlineUsers = new ArrayList<>();
    }

    public Message(Type type, String sender, String textContent) {
        this();
        this.type = type;
        this.sender = sender;
        this.textContent = textContent;
    }
}
