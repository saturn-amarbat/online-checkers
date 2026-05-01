package checkers2088.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import checkers2088.shared.Message;

public class Server {
    private static final int DEFAULT_PORT = 5555;
    private static final DateTimeFormatter LOG_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

    private final Map<String, ClientThread> activeClients = new ConcurrentHashMap<>();
    private final Map<String, String> pendingChallenges = new ConcurrentHashMap<>();
    private final Map<String, GameEngine.GameState> sessionsByPlayer = new ConcurrentHashMap<>();
    private final DatabaseHelper db;
    private final Consumer<Serializable> callback;
    private final Deque<ClientThread> matchQueue = new ArrayDeque<>();
    private final GameEngine gameEngine = new GameEngine();

    private TheServer serverThread;
    private ServerSocket listeningSocket;
    private volatile boolean running;

    public Server(Consumer<Serializable> callback) {
        this.callback = callback;
        this.db = new DatabaseHelper(Paths.get("checkers-2088.db"));
    }

    public synchronized void startServer() {
        if (running) {
            return;
        }
        running = true;
        serverThread = new TheServer();
        serverThread.start();
    }

    public synchronized void stopServer() {
        running = false;
        try {
            if (listeningSocket != null) {
                listeningSocket.close();
            }
        } catch (IOException ignored) {
        }

        for (ClientThread client : activeClients.values()) {
            client.disconnectAndCleanup();
        }
    }

    private synchronized void handleMessage(ClientThread client, Message message) {
        if (message == null || message.getType() == null) {
            return;
        }

        switch (message.getType()) {
            case LOGIN -> handleLogin(client, message);
            case GLOBAL_CHAT -> handleGlobalChat(client, message);
            case PRIVATE_CHAT -> handlePrivateChat(client, message);
            case CHALLENGE_REQUEST -> handleChallengeRequest(client, message);
            case CHALLENGE_ACCEPT -> handleChallengeAccept(client, message);
            case CHALLENGE_DECLINE -> handleChallengeDecline(client, message);
            case MOVE_ATTEMPT -> handleMoveAttempt(client, message);
            default -> log("SERVER", "Ignoring unsupported message from " + safeName(client) + ": " + message.getType());
        }
    }

    private synchronized void broadcast(Message message) {
        for (ClientThread client : activeClients.values()) {
            client.send(message);
        }
    }

    private synchronized void handleLogin(ClientThread client, Message message) {
        String requestedName = message.getSender().trim();
        if (requestedName.isBlank()) {
            client.send(simpleMessage(Message.Type.LOGIN_FAIL, "SYSTEM", "Username is required."));
            return;
        }
        if (activeClients.containsKey(requestedName)) {
            client.send(simpleMessage(Message.Type.LOGIN_FAIL, "SYSTEM", "That username is already in use."));
            return;
        }

        client.username = requestedName;
        activeClients.put(requestedName, client);
        db.ensureUser(requestedName);

        Message success = simpleMessage(Message.Type.LOGIN_SUCCESS, "SYSTEM", "Connected.");
        success.setSender(requestedName);
        success.setOnlineUsers(sortedUsernames());
        success.setStatsText(db.getStatsText(requestedName) + System.lineSeparator() + System.lineSeparator() + db.getLeaderboardText());
        client.send(success);

        log("LOGIN", requestedName + " joined from " + client.connection.getRemoteSocketAddress() + ".");
        sendClientListUpdate();
        broadcastSystemMessage(requestedName + " connected to the server.");
    }

    private synchronized void handleGlobalChat(ClientThread client, Message message) {
        if (!client.isLoggedIn()) {
            return;
        }

        Message outbound = simpleMessage(Message.Type.GLOBAL_CHAT, client.username, message.getTextContent().trim());
        broadcast(outbound);
        log("CHAT", client.username + " -> ALL: " + message.getTextContent().trim());
    }

    private synchronized void handlePrivateChat(ClientThread client, Message message) {
        if (!client.isLoggedIn()) {
            return;
        }

        String opponent = message.getRecipients().isEmpty() ? findOpponentUsername(client.username) : message.getRecipients().get(0);
        if (opponent == null || opponent.isBlank()) {
            client.send(simpleMessage(Message.Type.PRIVATE_CHAT, "SYSTEM", "No private channel is active."));
            return;
        }

        ClientThread target = activeClients.get(opponent);
        if (target == null) {
            client.send(simpleMessage(Message.Type.PRIVATE_CHAT, "SYSTEM", opponent + " is no longer online."));
            return;
        }

        Message outbound = simpleMessage(Message.Type.PRIVATE_CHAT, client.username, message.getTextContent().trim());
        outbound.setRecipients(List.of(opponent));
        client.send(outbound);
        if (target != client) {
            target.send(outbound);
        }
        log("CHAT", client.username + " -> " + opponent + ": " + message.getTextContent().trim());
    }

    private synchronized void handleChallengeRequest(ClientThread client, Message message) {
        String mode = message.getTextContent().trim();
        if ("QUEUE".equalsIgnoreCase(mode)) {
            joinQueue(client);
            return;
        }

        if (!message.getRecipients().isEmpty()) {
            String challengedUser = message.getRecipients().get(0);
            if ("REMATCH".equalsIgnoreCase(mode)) {
                handleRematchRequest(client, challengedUser);
                return;
            }

            pendingChallenges.put(client.username, challengedUser);
            ClientThread challengedClient = activeClients.get(challengedUser);
            if (challengedClient != null) {
                Message outbound = simpleMessage(Message.Type.CHALLENGE_REQUEST, client.username, "Challenge request");
                outbound.setRecipients(List.of(challengedUser));
                challengedClient.send(outbound);
            }
        }
    }

    private synchronized void handleChallengeAccept(ClientThread client, Message message) {
        String requestText = message.getTextContent().trim();
        if ("QUEUE".equalsIgnoreCase(requestText)) {
            joinQueue(client);
            return;
        }
        if ("REMATCH".equalsIgnoreCase(requestText) && !message.getRecipients().isEmpty()) {
            String challenger = message.getRecipients().get(0);
            ClientThread challengerClient = activeClients.get(challenger);
            if (challengerClient != null) {
                pendingChallenges.remove(challenger);
                startMatch(challengerClient, client);
            }
            return;
        }

        String challenger = pendingChallenges.entrySet().stream()
                .filter(entry -> entry.getValue().equals(client.username))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("");
        if (!challenger.isBlank()) {
            pendingChallenges.remove(challenger);
            ClientThread challengerClient = activeClients.get(challenger);
            if (challengerClient != null) {
                startMatch(challengerClient, client);
            }
        }
    }

    private synchronized void handleChallengeDecline(ClientThread client, Message message) {
        String requestText = message.getTextContent().trim();
        if ("QUEUE".equalsIgnoreCase(requestText)) {
            leaveQueue(client);
            return;
        }

        pendingChallenges.entrySet().removeIf(entry -> entry.getValue().equals(client.username));
    }

    private synchronized void handleMoveAttempt(ClientThread client, Message message) {
        GameEngine.GameState state = sessionsByPlayer.get(client.username);
        if (state == null) {
            client.send(simpleMessage(Message.Type.GAME_STATE_UPDATE, "SYSTEM", "You are not in an active match."));
            return;
        }

        boolean movingBlack = state.getOnyxPlayer().equals(client.username);
        if (movingBlack != state.isBlackTurn()) {
            client.send(buildStateMessage(state, client.username, "Wait for your turn."));
            return;
        }

        GameEngine.MoveResult result = gameEngine.validateAndApplyMove(
                state,
                message.getFromRow(),
                message.getFromCol(),
                message.getToRow(),
                message.getToCol()
        );

        if (!result.isValid()) {
            client.send(buildStateMessage(state, client.username, result.getErrorText()));
            return;
        }

        log("MOVE", client.username + " moved " + message.getFromRow() + "," + message.getFromCol()
                + " to " + message.getToRow() + "," + message.getToCol() + ".");

        pushGameState(state, result.isJumpChainActive() ? "Jump again with the same piece." : state.getStatusText());

        if (result.isGameOver()) {
            finishMatch(state, result.getWinner(), "Match complete.");
        }
    }

    private synchronized void joinQueue(ClientThread client) {
        if (!client.isLoggedIn()) {
            return;
        }
        if (sessionsByPlayer.containsKey(client.username) || matchQueue.contains(client)) {
            return;
        }

        matchQueue.addLast(client);
        client.send(simpleMessage(Message.Type.CHALLENGE_REQUEST, "SYSTEM", "QUEUE_JOINED"));
        log("QUEUE", client.username + " joined the queue (" + matchQueue.size() + " waiting).");

        while (matchQueue.size() >= 2) {
            ClientThread ruby = matchQueue.pollFirst();
            ClientThread onyx = matchQueue.pollFirst();
            if (ruby == null || onyx == null) {
                return;
            }
            if (!ruby.isLoggedIn() || !onyx.isLoggedIn()) {
                continue;
            }
            startMatch(ruby, onyx);
        }
    }

    private synchronized void leaveQueue(ClientThread client) {
        if (matchQueue.remove(client)) {
            client.send(simpleMessage(Message.Type.CHALLENGE_DECLINE, "SYSTEM", "QUEUE_LEFT"));
            log("QUEUE", client.username + " left the queue.");
        }
    }

    private synchronized void startMatch(ClientThread rubyClient, ClientThread onyxClient) {
        GameEngine.GameState state = gameEngine.createNewGame(rubyClient.username, onyxClient.username);
        sessionsByPlayer.put(rubyClient.username, state);
        sessionsByPlayer.put(onyxClient.username, state);
        pendingChallenges.remove(rubyClient.username);
        pendingChallenges.remove(onyxClient.username);

        Message rubyStart = simpleMessage(Message.Type.GAME_START, "SYSTEM", onyxClient.username);
        rubyStart.setSideAssignment("RUBY");
        rubyStart.setBoardState(gameEngine.copyBoard(state.getBoardState()));
        rubyStart.setBlackTurn(false);
        rubyStart.setStatsText(db.getStatsText(rubyClient.username));
        rubyClient.send(rubyStart);

        Message onyxStart = simpleMessage(Message.Type.GAME_START, "SYSTEM", rubyClient.username);
        onyxStart.setSideAssignment("ONYX");
        onyxStart.setBoardState(gameEngine.copyBoard(state.getBoardState()));
        onyxStart.setBlackTurn(false);
        onyxStart.setStatsText(db.getStatsText(onyxClient.username));
        onyxClient.send(onyxStart);

        log("MATCH", rubyClient.username + " (RUBY) vs " + onyxClient.username + " (ONYX).");
        pushGameState(state, "Match started.");
    }

    private synchronized void pushGameState(GameEngine.GameState state, String statusText) {
        Message rubyMessage = buildStateMessage(state, state.getRubyPlayer(), statusText);
        Message onyxMessage = buildStateMessage(state, state.getOnyxPlayer(), statusText);

        ClientThread rubyClient = activeClients.get(state.getRubyPlayer());
        ClientThread onyxClient = activeClients.get(state.getOnyxPlayer());
        if (rubyClient != null) {
            rubyClient.send(rubyMessage);
        }
        if (onyxClient != null) {
            onyxClient.send(onyxMessage);
        }
    }

    private Message buildStateMessage(GameEngine.GameState state, String username, String statusText) {
        Message message = simpleMessage(Message.Type.GAME_STATE_UPDATE, "SYSTEM", statusText);
        message.setBoardState(gameEngine.copyBoard(state.getBoardState()));
        message.setBlackTurn(state.isBlackTurn());
        message.setJumpChainActive(state.getForcedRow() >= 0);
        message.setFocusRow(state.getForcedRow());
        message.setFocusCol(state.getForcedCol());
        message.setStatsText(db.getStatsText(username) + System.lineSeparator() + System.lineSeparator() + db.getLeaderboardText());

        boolean isCurrentPlayer = (!state.isBlackTurn() && state.getRubyPlayer().equals(username))
                || (state.isBlackTurn() && state.getOnyxPlayer().equals(username));
        message.setLegalMoves(isCurrentPlayer ? gameEngine.getLegalMovesForCurrentTurn(state) : List.of());
        return message;
    }

    private synchronized void finishMatch(GameEngine.GameState state, String winner, String reason) {
        String rubyPlayer = state.getRubyPlayer();
        String onyxPlayer = state.getOnyxPlayer();
        String loser = "";

        if (!"DRAW".equalsIgnoreCase(winner)) {
            loser = winner.equals(rubyPlayer) ? onyxPlayer : rubyPlayer;
            db.recordWinLoss(winner, loser);
        }

        Message gameOver = simpleMessage(Message.Type.GAME_OVER, "SYSTEM", reason);
        gameOver.setWinner(winner);
        gameOver.setStatsText(buildMatchSummary(rubyPlayer, onyxPlayer));

        ClientThread rubyClient = activeClients.get(rubyPlayer);
        ClientThread onyxClient = activeClients.get(onyxPlayer);
        if (rubyClient != null) {
            rubyClient.send(gameOver);
        }
        if (onyxClient != null) {
            onyxClient.send(gameOver);
        }

        sessionsByPlayer.remove(rubyPlayer);
        sessionsByPlayer.remove(onyxPlayer);
        sendClientListUpdate();
        broadcastSystemMessage("Match ended: " + winner + (loser.isBlank() ? "" : " defeated " + loser) + ".");
        log("RESULT", rubyPlayer + " vs " + onyxPlayer + " -> " + winner + ".");
    }

    private synchronized void handleDisconnect(ClientThread client) {
        if (client.username == null || client.username.isBlank()) {
            return;
        }

        activeClients.remove(client.username);
        pendingChallenges.entrySet().removeIf(entry -> entry.getKey().equals(client.username) || entry.getValue().equals(client.username));
        matchQueue.remove(client);

        GameEngine.GameState state = sessionsByPlayer.get(client.username);
        if (state != null) {
            String winner = state.getRubyPlayer().equals(client.username) ? state.getOnyxPlayer() : state.getRubyPlayer();
            finishMatch(state, winner, client.username + " disconnected.");
        }

        sendClientListUpdate();
        broadcastSystemMessage(client.username + " disconnected.");
        log("LOGOUT", client.username + " disconnected.");
    }

    private void handleRematchRequest(ClientThread client, String challengedUser) {
        ClientThread challengedClient = activeClients.get(challengedUser);
        if (challengedClient == null) {
            client.send(simpleMessage(Message.Type.CHALLENGE_DECLINE, "SYSTEM", challengedUser + " is offline."));
            return;
        }

        if (client.username.equals(pendingChallenges.get(challengedUser))) {
            pendingChallenges.remove(challengedUser);
            startMatch(challengedClient, client);
            log("MATCH", "Rematch started between " + challengedClient.username + " and " + client.username + ".");
            return;
        }

        pendingChallenges.put(client.username, challengedUser);
        Message outbound = simpleMessage(Message.Type.CHALLENGE_REQUEST, client.username, "REMATCH_REQUEST");
        outbound.setRecipients(List.of(challengedUser));
        challengedClient.send(outbound);
        client.send(simpleMessage(Message.Type.CHALLENGE_ACCEPT, "SYSTEM", "Rematch request sent."));
        log("MATCH", client.username + " requested a rematch with " + challengedUser + ".");
    }

    private synchronized void sendClientListUpdate() {
        Message update = new Message(Message.Type.CLIENT_LIST_UPDATE);
        update.setSender("SYSTEM");
        update.setOnlineUsers(sortedUsernames());
        update.setStatsText(db.getLeaderboardText());
        broadcast(update);
    }

    private void broadcastSystemMessage(String text) {
        Message message = simpleMessage(Message.Type.GLOBAL_CHAT, "SYSTEM", text);
        broadcast(message);
    }

    private Message simpleMessage(Message.Type type, String sender, String text) {
        Message message = new Message(type);
        message.setSender(sender);
        message.setTextContent(text);
        return message;
    }

    private List<String> sortedUsernames() {
        return activeClients.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String findOpponentUsername(String username) {
        GameEngine.GameState state = sessionsByPlayer.get(username);
        if (state == null) {
            return "";
        }
        return state.getRubyPlayer().equals(username) ? state.getOnyxPlayer() : state.getRubyPlayer();
    }

    private String buildMatchSummary(String rubyPlayer, String onyxPlayer) {
        return db.getStatsText(rubyPlayer)
                + System.lineSeparator()
                + db.getStatsText(onyxPlayer)
                + System.lineSeparator()
                + System.lineSeparator()
                + db.getLeaderboardText();
    }

    private String safeName(ClientThread client) {
        return client.username == null || client.username.isBlank() ? "anonymous client" : client.username;
    }

    private void log(String type, String line) {
        String message = "[" + LocalDateTime.now().format(LOG_FORMAT) + "] [" + type + "] " + line;
        callback.accept(message);
    }

    private final class TheServer extends Thread {
        @Override
        public void run() {
            try (ServerSocket serverSocket = new ServerSocket(DEFAULT_PORT)) {
                listeningSocket = serverSocket;
                log("SERVER", "Server started on port " + DEFAULT_PORT + ".");
                while (running) {
                    Socket connection = serverSocket.accept();
                    ClientThread thread = new ClientThread(connection);
                    thread.start();
                }
            } catch (IOException exception) {
                if (running) {
                    log("SERVER", "Server stopped unexpectedly: " + exception.getMessage());
                }
            }
        }
    }

    public final class ClientThread extends Thread {
        private final Socket connection;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private String username;
        private volatile boolean connected = true;

        public ClientThread(Socket connection) {
            this.connection = connection;
            setName("client-thread-" + connection.getPort());
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(connection.getOutputStream());
                out.flush();
                in = new ObjectInputStream(connection.getInputStream());
                connection.setTcpNoDelay(true);

                while (connected) {
                    Object inbound = in.readObject();
                    if (inbound instanceof Message message) {
                        handleMessage(this, message);
                    }
                }
            } catch (IOException | ClassNotFoundException exception) {
                log("SERVER", "Connection dropped: " + exception.getMessage());
            } finally {
                disconnectAndCleanup();
            }
        }

        public synchronized boolean send(Message message) {
            if (!connected || out == null) {
                return false;
            }

            try {
                out.writeObject(message);
                out.flush();
                return true;
            } catch (IOException exception) {
                disconnectAndCleanup();
                return false;
            }
        }

        public synchronized void disconnectAndCleanup() {
            if (!connected) {
                return;
            }
            connected = false;

            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (IOException ignored) {
            }

            handleDisconnect(this);
        }

        private boolean isLoggedIn() {
            return username != null && !username.isBlank();
        }
    }
}
