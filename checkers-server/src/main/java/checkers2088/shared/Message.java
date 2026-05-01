package checkers2088.shared;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Message implements Serializable {
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

    private static final long serialVersionUID = 1L;

    private Type type;
    private String sender = "";
    private ArrayList<String> recipients = new ArrayList<>();
    private String textContent = "";
    private ArrayList<String> onlineUsers = new ArrayList<>();
    private int[][] boardState;
    private int fromRow = -1;
    private int fromCol = -1;
    private int toRow = -1;
    private int toCol = -1;
    private boolean blackTurn;

    private ArrayList<int[]> legalMoves = new ArrayList<>();
    private String sideAssignment = "";
    private String statsText = "";
    private String winner = "";
    private boolean jumpChainActive;
    private int focusRow = -1;
    private int focusCol = -1;

    public Message() {
    }

    public Message(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = safeString(sender);
    }

    public ArrayList<String> getRecipients() {
        return new ArrayList<>(recipients);
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = new ArrayList<>(recipients == null ? List.of() : recipients);
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = safeString(textContent);
    }

    public ArrayList<String> getOnlineUsers() {
        return new ArrayList<>(onlineUsers);
    }

    public void setOnlineUsers(List<String> onlineUsers) {
        this.onlineUsers = new ArrayList<>(onlineUsers == null ? List.of() : onlineUsers);
    }

    public int[][] getBoardState() {
        return copyBoard(boardState);
    }

    public void setBoardState(int[][] boardState) {
        this.boardState = copyBoard(boardState);
    }

    public int getFromRow() {
        return fromRow;
    }

    public void setFromRow(int fromRow) {
        this.fromRow = fromRow;
    }

    public int getFromCol() {
        return fromCol;
    }

    public void setFromCol(int fromCol) {
        this.fromCol = fromCol;
    }

    public int getToRow() {
        return toRow;
    }

    public void setToRow(int toRow) {
        this.toRow = toRow;
    }

    public int getToCol() {
        return toCol;
    }

    public void setToCol(int toCol) {
        this.toCol = toCol;
    }

    public boolean isBlackTurn() {
        return blackTurn;
    }

    public void setBlackTurn(boolean blackTurn) {
        this.blackTurn = blackTurn;
    }

    public ArrayList<int[]> getLegalMoves() {
        ArrayList<int[]> copy = new ArrayList<>();
        for (int[] move : legalMoves) {
            copy.add(move.clone());
        }
        return copy;
    }

    public void setLegalMoves(List<int[]> legalMoves) {
        this.legalMoves = new ArrayList<>();
        if (legalMoves == null) {
            return;
        }
        for (int[] move : legalMoves) {
            this.legalMoves.add(move.clone());
        }
    }

    public String getSideAssignment() {
        return sideAssignment;
    }

    public void setSideAssignment(String sideAssignment) {
        this.sideAssignment = safeString(sideAssignment);
    }

    public String getStatsText() {
        return statsText;
    }

    public void setStatsText(String statsText) {
        this.statsText = safeString(statsText);
    }

    public String getWinner() {
        return winner;
    }

    public void setWinner(String winner) {
        this.winner = safeString(winner);
    }

    public boolean isJumpChainActive() {
        return jumpChainActive;
    }

    public void setJumpChainActive(boolean jumpChainActive) {
        this.jumpChainActive = jumpChainActive;
    }

    public int getFocusRow() {
        return focusRow;
    }

    public void setFocusRow(int focusRow) {
        this.focusRow = focusRow;
    }

    public int getFocusCol() {
        return focusCol;
    }

    public void setFocusCol(int focusCol) {
        this.focusCol = focusCol;
    }

    private int[][] copyBoard(int[][] boardState) {
        if (boardState == null) {
            return null;
        }
        int[][] copy = new int[boardState.length][];
        for (int row = 0; row < boardState.length; row++) {
            copy[row] = boardState[row] == null ? null : boardState[row].clone();
        }
        return copy;
    }

    private String safeString(String text) {
        return text == null ? "" : text;
    }
}
