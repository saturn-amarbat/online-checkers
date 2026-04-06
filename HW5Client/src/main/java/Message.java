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
        PRIVATE_CHAT,
        CHALLENGE_REQUEST,
        CHALLENGE_ACCEPT,
        CHALLENGE_DECLINE,
        GAME_START,
        MOVE_ATTEMPT,
        GAME_STATE_UPDATE,
        GAME_OVER
    }

    public Type type;
    public String sender;
    public ArrayList<String> recipients;
    public String textContent;
    public ArrayList<String> onlineUsers;

    public int[][] boardState;
    public int fromRow = -1;
    public int fromCol = -1;
    public int toRow = -1;
    public int toCol = -1;
    public boolean isBlackTurn;

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
