import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class Server {

    private int clientCount = 1;
    private final TheServer server;
    private final Consumer<Serializable> callback;

    private final Map<String, ClientThread> activeClients = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, String> pendingChallenges = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, GameEngine.GameState> sessionsByPlayer = Collections.synchronizedMap(new HashMap<>());
    private final Set<ClientThread> unauthedClients = Collections.synchronizedSet(new HashSet<>());

    private final DatabaseHelper databaseHelper = new DatabaseHelper();

    Server(Consumer<Serializable> call) {
        callback = call;
        server = new TheServer();
        server.start();
    }

    public class TheServer extends Thread {
        public void run() {
            try (ServerSocket mySocket = new ServerSocket(5555)) {
                callback.accept("Server listening on port 5555");

                while (true) {
                    ClientThread clientThread = new ClientThread(mySocket.accept(), clientCount++);
                    unauthedClients.add(clientThread);
                    callback.accept("Incoming socket connection: temp-client#" + clientThread.id);
                    clientThread.start();
                }
            } catch (Exception e) {
                callback.accept("Server socket failed: " + e.getMessage());
            }
        }
    }

    class ClientThread extends Thread {

        private final Socket connection;
        private final int id;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private String username;

        ClientThread(Socket socket, int id) {
            this.connection = socket;
            this.id = id;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(connection.getOutputStream());
                in = new ObjectInputStream(connection.getInputStream());
                connection.setTcpNoDelay(true);
            } catch (Exception e) {
                callback.accept("Failed to open streams for temp-client#" + id + ": " + e.getMessage());
                disconnectAndCleanup();
                return;
            }

            while (true) {
                try {
                    Object payload = in.readObject();
                    if (!(payload instanceof Message)) {
                        continue;
                    }
                    Message message = (Message) payload;
                    handleMessage(this, message);
                } catch (Exception e) {
                    callback.accept("Socket closed for " + displayName() + ": " + e.getMessage());
                    disconnectAndCleanup();
                    break;
                }
            }
        }

        String displayName() {
            return username == null ? ("temp-client#" + id) : username;
        }

        synchronized boolean send(Message message) {
            try {
                out.writeObject(message);
                out.flush();
                return true;
            } catch (Exception e) {
                callback.accept("Send failed to " + displayName() + ": " + e.getMessage());
                disconnectAndCleanup();
                return false;
            }
        }

        void disconnectAndCleanup() {
            synchronized (Server.this) {
                unauthedClients.remove(this);
                if (username != null && activeClients.get(username) == this) {
                    callback.accept("Disconnect cleanup for " + username);
                    activeClients.remove(username);
                    endGameIfPresent(username, username + " disconnected");
                }
            }

            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Exception ignored) {
            }
            try {
                connection.close();
            } catch (Exception ignored) {
            }

            broadcastClientList();
        }
    }

    private synchronized void handleMessage(ClientThread source, Message message) {
        if (message.type == null) {
            return;
        }

        callback.accept("RX " + message.type + " from " + source.displayName() + " payload=" + safeText(message.textContent));

        switch (message.type) {
            case LOGIN:
                handleLogin(source, message);
                break;
            case GLOBAL_CHAT:
                handleGlobalChat(source, message);
                break;
            case PRIVATE_CHAT:
                handlePrivateChat(source, message);
                break;
            case CHALLENGE_REQUEST:
                handleChallengeRequest(source, message);
                break;
            case CHALLENGE_ACCEPT:
                handleChallengeAccept(source, message);
                break;
            case CHALLENGE_DECLINE:
                handleChallengeDecline(source, message);
                break;
            case MOVE_ATTEMPT:
                handleMoveAttempt(source, message);
                break;
            default:
                callback.accept("Ignoring unsupported server-side type: " + message.type);
                break;
        }
    }

    private void handleLogin(ClientThread source, Message message) {
        String desired = message.sender == null ? "" : message.sender.trim();
        if (desired.isEmpty()) {
            Message fail = new Message(Message.Type.LOGIN_FAIL, "SERVER", "Username cannot be empty");
            source.send(fail);
            return;
        }

        if (activeClients.containsKey(desired)) {
            Message fail = new Message(Message.Type.LOGIN_FAIL, "SERVER", "Username already in use");
            source.send(fail);
            return;
        }

        source.username = desired;
        unauthedClients.remove(source);
        activeClients.put(desired, source);
        databaseHelper.ensureUser(desired);

        Message success = new Message(Message.Type.LOGIN_SUCCESS, "SERVER", "Welcome " + desired + "");
        success.textContent = success.textContent + " | " + databaseHelper.getStatsText(desired);
        source.send(success);

        Message joinNotice = new Message(Message.Type.GLOBAL_CHAT, "SERVER", desired + " joined the lobby");
        broadcast(joinNotice);
        broadcastClientList();
    }

    private void handleGlobalChat(ClientThread source, Message message) {
        if (!isAuthed(source)) {
            return;
        }

        Message outbound = new Message(Message.Type.GLOBAL_CHAT, source.username, safeText(message.textContent));
        broadcast(outbound);
    }

    private void handlePrivateChat(ClientThread source, Message message) {
        if (!isAuthed(source)) {
            return;
        }

        if (message.recipients == null || message.recipients.isEmpty()) {
            return;
        }

        for (String username : message.recipients) {
            ClientThread target = activeClients.get(username);
            if (target != null) {
                Message outbound = new Message(Message.Type.PRIVATE_CHAT, source.username, safeText(message.textContent));
                outbound.recipients.add(username);
                target.send(outbound);
            }
        }

        Message echo = new Message(Message.Type.PRIVATE_CHAT, source.username, safeText(message.textContent));
        echo.recipients = new ArrayList<>(message.recipients);
        source.send(echo);
    }

    private void handleChallengeRequest(ClientThread source, Message message) {
        if (!isAuthed(source) || message.recipients == null || message.recipients.isEmpty()) {
            return;
        }

        String targetUser = message.recipients.get(0);
        if (targetUser.equals(source.username)) {
            source.send(new Message(Message.Type.CHALLENGE_DECLINE, "SERVER", "You cannot challenge yourself"));
            return;
        }

        ClientThread target = activeClients.get(targetUser);
        if (target == null) {
            source.send(new Message(Message.Type.CHALLENGE_DECLINE, "SERVER", targetUser + " is offline"));
            return;
        }

        if (sessionsByPlayer.containsKey(source.username) || sessionsByPlayer.containsKey(targetUser)) {
            source.send(new Message(Message.Type.CHALLENGE_DECLINE, "SERVER", "One of you is already in a game"));
            return;
        }

        pendingChallenges.put(targetUser, source.username);

        Message request = new Message(Message.Type.CHALLENGE_REQUEST, source.username, source.username + " challenged you to checkers");
        request.recipients.add(targetUser);
        target.send(request);

        source.send(new Message(Message.Type.GLOBAL_CHAT, "SERVER", "Challenge sent to " + targetUser));
    }

    private void handleChallengeAccept(ClientThread source, Message message) {
        if (!isAuthed(source)) {
            return;
        }

        String challenger = pendingChallenges.get(source.username);
        if (challenger == null) {
            source.send(new Message(Message.Type.CHALLENGE_DECLINE, "SERVER", "No pending challenge to accept"));
            return;
        }

        ClientThread challengerThread = activeClients.get(challenger);
        if (challengerThread == null) {
            pendingChallenges.remove(source.username);
            source.send(new Message(Message.Type.CHALLENGE_DECLINE, "SERVER", challenger + " is no longer online"));
            return;
        }

        pendingChallenges.remove(source.username);

        GameEngine.GameState state = new GameEngine.GameState(challenger, source.username);
        sessionsByPlayer.put(challenger, state);
        sessionsByPlayer.put(source.username, state);

        sendGameStart(state.blackPlayer, state.redPlayer, true, state);
        sendGameStart(state.redPlayer, state.blackPlayer, false, state);

        callback.accept("GAME_START black=" + state.blackPlayer + " red=" + state.redPlayer);
    }

    private void handleChallengeDecline(ClientThread source, Message message) {
        if (!isAuthed(source)) {
            return;
        }

        String challenger = pendingChallenges.remove(source.username);
        if (challenger == null) {
            return;
        }

        ClientThread challengerThread = activeClients.get(challenger);
        if (challengerThread != null) {
            Message decline = new Message(Message.Type.CHALLENGE_DECLINE, source.username, source.username + " declined your challenge");
            decline.recipients.add(challenger);
            challengerThread.send(decline);
        }
    }

    private void handleMoveAttempt(ClientThread source, Message message) {
        if (!isAuthed(source)) {
            return;
        }

        GameEngine.GameState state = sessionsByPlayer.get(source.username);
        if (state == null) {
            source.send(new Message(Message.Type.GAME_STATE_UPDATE, "SERVER", "No active game"));
            return;
        }

        GameEngine.MoveResult result = GameEngine.validateAndApplyMove(
            state,
            source.username,
            message.fromRow,
            message.fromCol,
            message.toRow,
            message.toCol
        );

        if (!result.valid) {
            source.send(new Message(Message.Type.GAME_STATE_UPDATE, "SERVER", result.reason));
            return;
        }

        broadcastGameState(state, source.username + " moved " + square(message.fromRow, message.fromCol) + " -> " + square(message.toRow, message.toCol));

        if (result.mustContinueJump) {
            source.send(new Message(Message.Type.GAME_STATE_UPDATE, "SERVER", "You captured. Continue jumping with the same piece."));
        }

        String winner = GameEngine.determineWinner(state);
        if (winner != null) {
            String loser = winner.equals(state.blackPlayer) ? state.redPlayer : state.blackPlayer;
            databaseHelper.recordWinLoss(winner, loser);
            endGame(state, winner + " wins! | " + databaseHelper.getStatsText(winner));
        }
    }

    private void sendGameStart(String recipient, String opponent, boolean isBlack, GameEngine.GameState state) {
        ClientThread target = activeClients.get(recipient);
        if (target == null) {
            return;
        }

        Message start = new Message(Message.Type.GAME_START, "SERVER", "Game started vs " + opponent);
        start.recipients.add(recipient);
        start.recipients.add(opponent);
        start.boardState = GameEngine.copyBoard(state.board);
        start.isBlackTurn = state.blackTurn;
        if (!isBlack) {
            start.textContent = start.textContent + " (you are RED)";
        } else {
            start.textContent = start.textContent + " (you are BLACK)";
        }
        target.send(start);
    }

    private void broadcastGameState(GameEngine.GameState state, String text) {
        Message update = new Message(Message.Type.GAME_STATE_UPDATE, "SERVER", text);
        update.boardState = GameEngine.copyBoard(state.board);
        update.isBlackTurn = state.blackTurn;

        ClientThread black = activeClients.get(state.blackPlayer);
        ClientThread red = activeClients.get(state.redPlayer);

        if (black != null) {
            black.send(update);
        }
        if (red != null) {
            red.send(update);
        }

        callback.accept("GAME_STATE turn=" + (state.blackTurn ? "BLACK" : "RED") + " text=" + text);
    }

    private void endGame(GameEngine.GameState state, String reason) {
        Message over = new Message(Message.Type.GAME_OVER, "SERVER", reason);
        over.boardState = GameEngine.copyBoard(state.board);

        ClientThread black = activeClients.get(state.blackPlayer);
        ClientThread red = activeClients.get(state.redPlayer);

        if (black != null) {
            black.send(over);
        }
        if (red != null) {
            red.send(over);
        }

        sessionsByPlayer.remove(state.blackPlayer);
        sessionsByPlayer.remove(state.redPlayer);

        callback.accept("GAME_OVER " + reason);
    }

    private void endGameIfPresent(String username, String reason) {
        GameEngine.GameState state = sessionsByPlayer.get(username);
        if (state != null) {
            endGame(state, reason);
        }

        pendingChallenges.remove(username);
        pendingChallenges.values().removeIf(challenger -> challenger.equals(username));
    }

    private void broadcastClientList() {
        Message listUpdate = new Message(Message.Type.CLIENT_LIST_UPDATE, "SERVER", "Online users refreshed");
        listUpdate.onlineUsers = new ArrayList<>(activeClients.keySet());
        Collections.sort(listUpdate.onlineUsers);
        broadcast(listUpdate);
    }

    private void broadcast(Message message) {
        ArrayList<String> recipients = new ArrayList<>(activeClients.keySet());
        for (String user : recipients) {
            ClientThread target = activeClients.get(user);
            if (target != null) {
                target.send(message);
            }
        }
    }

    private boolean isAuthed(ClientThread source) {
        if (source.username == null || !activeClients.containsKey(source.username)) {
            Message fail = new Message(Message.Type.LOGIN_FAIL, "SERVER", "Please login first");
            source.send(fail);
            return false;
        }
        return true;
    }

    private String safeText(String input) {
        return input == null ? "" : input;
    }

    private String square(int row, int col) {
        return "(" + row + "," + col + ")";
    }
}
