package checkers2088.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import checkers2088.shared.Message;

public class GuiClient extends Application {
    private static final int PORT = 5555;
    private static final int BOARD_SIZE = 8;
    private static final double CELL_SIZE = 60;

    private enum ChatMode {
        GLOBAL,
        PRIVATE
    }

    private record Coord(int row, int col) {
    }

    private record HowToCard(String title, String body) {
    }

    private Stage stage;
    private Scene welcomeScene;
    private Scene gameScene;
    private Scene howToPlayScene;
    private Scene gameOverScene;
    private String stylesheet;

    private final StackPane[][] boardSquares = new StackPane[BOARD_SIZE][BOARD_SIZE];
    private final Map<Coord, List<Coord>> legalMoves = new LinkedHashMap<>();
    private final StringBuilder globalTranscript = new StringBuilder();
    private final StringBuilder privateTranscript = new StringBuilder();
    private final List<String> onlineUsers = new ArrayList<>();

    private Client clientConnection;

    private HBox titleHero;
    private Label titleLabel;
    private TextField usernameField;
    private TextField hostField;
    private Label welcomeStatusLabel;
    private Button connectButton;

    private GridPane boardGrid;
    private TextArea chatTranscriptArea;
    private TextField chatInputField;
    private Button chatSendButton;
    private Button joinQueueButton;
    private Button leaveQueueButton;
    private ToggleButton globalChatToggle;
    private ToggleButton privateChatToggle;
    private TextArea statsArea;

    private Label playerValueLabel;
    private Label queueValueLabel;
    private Label sideValueLabel;
    private Label turnValueLabel;
    private Label opponentValueLabel;

    private Label gameOverWinnerLabel;
    private Label gameOverBodyLabel;
    private TextArea gameOverStatsArea;

    private int[][] boardState = createOpeningBoardState();
    private boolean myColorBlack;
    private boolean connectedToServer;
    private boolean waitingInQueue;
    private boolean matchActive;
    private boolean serverBlackTurn;
    private String connectedUsername = "";
    private String connectedHost = "127.0.0.1";
    private String opponentName = "";
    private String lastMatchOpponentName = "";
    private String lastStatsText = "";
    private String lastGameReason = "";
    private String pendingRematchFrom = "";
    private ChatMode activeChatMode = ChatMode.GLOBAL;
    private Coord selectedSource;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        clientConnection = new Client(this::handleIncomingMessage, this::handleDisconnect);
        stylesheet = Objects.requireNonNull(getClass().getResource("/styles/style.css")).toExternalForm();

        welcomeScene = buildWelcomeScene();
        gameScene = buildGameScene();
        howToPlayScene = buildHowToPlayScene();
        gameOverScene = buildGameOverScene();
        applyStylesheet(welcomeScene);
        applyStylesheet(gameScene);
        applyStylesheet(howToPlayScene);
        applyStylesheet(gameOverScene);

        stage.setTitle("Checkers 2088");
        stage.setMinWidth(1240);
        stage.setMinHeight(860);
        stage.setScene(welcomeScene);
        stage.show();

        playTitleIntro();
    }

    @Override
    public void stop() {
        if (clientConnection != null) {
            clientConnection.disconnect();
        }
    }

    private Scene buildWelcomeScene() {
        StackPane root = new StackPane();
        root.getStyleClass().addAll("root-dark", "welcome-root");
        root.setPadding(new Insets(36));

        VBox card = new VBox(18);
        card.getStyleClass().add("welcome-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(680);

        Label overline = new Label("Online Checkers");
        overline.getStyleClass().add("scene-caption");

        titleLabel = new Label("CHECKERS");
        titleLabel.getStyleClass().add("app-title");
        titleLabel.setWrapText(true);

        StackPane badge = buildTitleBadge();
        titleHero = new HBox(18, titleLabel, badge);
        titleHero.setAlignment(Pos.CENTER_LEFT);

        Label subtitle = new Label("Sign in, join the queue, and play a live match or review the rules first.");
        subtitle.getStyleClass().add("sub-title");
        subtitle.setWrapText(true);

        Label usernameLabel = new Label("Username");
        usernameLabel.getStyleClass().add("field-label");

        usernameField = new TextField();
        usernameField.getStyleClass().add("input-field");
        usernameField.setPromptText("Enter a unique player name");

        Label hostLabel = new Label("Server IP");
        hostLabel.getStyleClass().add("field-label");

        hostField = new TextField("127.0.0.1");
        hostField.getStyleClass().add("input-field");
        hostField.setPromptText("127.0.0.1");

        HBox actionRow = new HBox(12);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        connectButton = new Button("Connect");
        connectButton.getStyleClass().add("primary-button");
        connectButton.setDefaultButton(true);
        connectButton.setOnAction(event -> handleConnect());

        Button howToPlayButton = new Button("How to Play");
        howToPlayButton.getStyleClass().add("secondary-button");
        howToPlayButton.setOnAction(event -> switchScene(howToPlayScene));

        actionRow.getChildren().addAll(connectButton, howToPlayButton);

        welcomeStatusLabel = new Label("v1.0.0");
        welcomeStatusLabel.getStyleClass().addAll("status-text", "info-text", "version-tag");
        welcomeStatusLabel.setWrapText(true);

        card.getChildren().addAll(
                overline,
                titleHero,
                subtitle,
                usernameLabel,
                usernameField,
                hostLabel,
                hostField,
                actionRow,
                welcomeStatusLabel
        );

        root.getChildren().add(card);
        return new Scene(root, 1360, 900);
    }

    private Scene buildGameScene() {
        BorderPane root = new BorderPane();
        root.getStyleClass().addAll("root-dark", "game-root");
        root.setPadding(new Insets(22));

        initializeBoardSquares();

        playerValueLabel = createHudValue("Offline");
        queueValueLabel = createHudValue("Open lobby");
        sideValueLabel = createHudValue("Unassigned");
        turnValueLabel = createHudValue("Connect to begin");
        opponentValueLabel = createHudValue("No opponent");

        joinQueueButton();
        leaveQueueButton();

        FlowPane statusBar = new FlowPane();
        statusBar.getStyleClass().add("status-bar");
        statusBar.setHgap(12);
        statusBar.setVgap(12);
        statusBar.getChildren().addAll(
                createHudBlock("PLAYER", playerValueLabel),
                createHudBlock("STATUS", turnValueLabel),
                createHudBlock("SIDE", sideValueLabel),
                createHudBlock("RIVAL", opponentValueLabel),
                createQueueCard()
        );
        root.setTop(statusBar);

        StackPane boardFrame = new StackPane(boardGrid);
        boardFrame.getStyleClass().add("board-frame");
        boardFrame.setPadding(new Insets(28));

        Label arenaLabel = new Label("Arena");
        arenaLabel.getStyleClass().add("panel-title");

        HBox arenaActions = new HBox(12);
        arenaActions.setAlignment(Pos.CENTER);

        Button howToPlayButton = new Button("How to Play");
        howToPlayButton.getStyleClass().add("secondary-button");
        howToPlayButton.setOnAction(event -> switchScene(howToPlayScene));

        Button disconnectButton = new Button("Disconnect");
        disconnectButton.getStyleClass().add("secondary-button");
        disconnectButton.setOnAction(event -> {
            clientConnection.disconnect();
            handleDisconnect("Disconnected.");
        });

        arenaActions.getChildren().addAll(howToPlayButton, disconnectButton);

        VBox centerColumn = new VBox(14, arenaLabel, boardFrame, arenaActions);
        centerColumn.setAlignment(Pos.CENTER);
        centerColumn.setPadding(new Insets(8, 16, 0, 8));
        root.setCenter(centerColumn);

        VBox communications = new VBox(14);
        communications.getStyleClass().add("chat-panel");
        communications.setPrefWidth(360);
        communications.setMinWidth(340);
        communications.setMaxWidth(390);

        Label chatTitle = new Label("Chat");
        chatTitle.getStyleClass().add("panel-title");

        ToggleGroup modeGroup = new ToggleGroup();
        globalChatToggle = new ToggleButton("Global Lobby");
        globalChatToggle.getStyleClass().add("chat-toggle");
        privateChatToggle = new ToggleButton("Private Match");
        privateChatToggle.getStyleClass().add("chat-toggle");
        globalChatToggle.setToggleGroup(modeGroup);
        privateChatToggle.setToggleGroup(modeGroup);
        globalChatToggle.setSelected(true);
        privateChatToggle.setDisable(true);

        modeGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                globalChatToggle.setSelected(true);
                return;
            }
            activeChatMode = newValue == privateChatToggle ? ChatMode.PRIVATE : ChatMode.GLOBAL;
            refreshChatTranscript();
        });

        HBox toggleRow = new HBox(10, globalChatToggle, privateChatToggle);

        chatTranscriptArea = new TextArea();
        chatTranscriptArea.getStyleClass().add("chat-area");
        chatTranscriptArea.setEditable(false);
        chatTranscriptArea.setWrapText(true);
        VBox.setVgrow(chatTranscriptArea, Priority.ALWAYS);

        HBox composer = new HBox(10);
        chatInputField = new TextField();
        chatInputField.getStyleClass().add("chat-input");
        chatInputField.setPromptText("Send a message");
        chatInputField.setOnAction(event -> sendChatMessage());
        HBox.setHgrow(chatInputField, Priority.ALWAYS);

        chatSendButton = new Button("Send");
        chatSendButton.getStyleClass().add("primary-button");
        chatSendButton.setOnAction(event -> sendChatMessage());
        composer.getChildren().addAll(chatInputField, chatSendButton);

        Label statsTitle = new Label("Leaderboard");
        statsTitle.getStyleClass().add("section-label");

        statsArea = new TextArea();
        statsArea.getStyleClass().add("stats-area");
        statsArea.setEditable(false);
        statsArea.setWrapText(true);
        statsArea.setPrefRowCount(10);

        communications.getChildren().addAll(
                chatTitle,
                toggleRow,
                chatTranscriptArea,
                composer,
                statsTitle,
                statsArea
        );
        root.setRight(communications);

        redrawBoard();
        refreshChatTranscript();
        updateQueueButtons();
        refreshStatsPanel();
        return new Scene(root, 1500, 940);
    }

    private Scene buildHowToPlayScene() {
        BorderPane root = new BorderPane();
        root.getStyleClass().addAll("root-dark", "howto-root");
        root.setPadding(new Insets(24));

        Button backButton = new Button("Back");
        backButton.getStyleClass().add("secondary-button");
        backButton.setOnAction(event -> switchScene(connectedToServer ? gameScene : welcomeScene));
        root.setTop(backButton);
        BorderPane.setAlignment(backButton, Pos.CENTER_LEFT);

        VBox content = new VBox(16);
        content.setPadding(new Insets(12, 0, 0, 0));

        Label title = new Label("How to Play");
        title.getStyleClass().add("scene-title");

        Label subtitle = new Label("A quick guide to live matches in Checkers 2088.");
        subtitle.getStyleClass().add("sub-title");

        content.getChildren().addAll(title, subtitle);

        List<HowToCard> cards = List.of(
                new HowToCard("1. Matchmaking", "Connect with a unique username, then click Join Queue. The server pairs the first two available players automatically."),
                new HowToCard("2. Movement", "Select one of your highlighted pieces and then click one of the highlighted destination squares to submit the move."),
                new HowToCard("3. Forced Jumps", "If a capture is available, every non-capturing move is rejected. If the same piece can keep jumping, your turn continues until the chain is finished."),
                new HowToCard("4. Kings and Chat", "Pieces promote the moment they land on the far row. Use Global Lobby for open chat and Private Match once the match begins.")
        );

        for (HowToCard card : cards) {
            VBox block = new VBox(8);
            block.getStyleClass().add("instruction-card");
            Label cardTitle = new Label(card.title());
            cardTitle.getStyleClass().add("instruction-title");
            Label cardBody = new Label(card.body());
            cardBody.getStyleClass().add("instruction-body");
            cardBody.setWrapText(true);
            block.getChildren().addAll(cardTitle, cardBody);
            content.getChildren().add(block);
        }

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.getStyleClass().add("howto-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        root.setCenter(scrollPane);

        return new Scene(root, 1360, 900);
    }

    private Scene buildGameOverScene() {
        StackPane root = new StackPane();
        root.getStyleClass().addAll("root-dark", "gameover-root");
        root.setPadding(new Insets(36));

        VBox card = new VBox(16);
        card.getStyleClass().add("gameover-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(760);

        gameOverWinnerLabel = new Label("MATCH OVER");
        gameOverWinnerLabel.getStyleClass().add("winner-neon");

        Label versusLabel = new Label("VS");
        versusLabel.getStyleClass().add("versus-mark");

        gameOverBodyLabel = new Label("Return to the lobby to queue for another match.");
        gameOverBodyLabel.getStyleClass().add("sub-title");
        gameOverBodyLabel.setWrapText(true);
        gameOverBodyLabel.setMaxWidth(580);
        gameOverBodyLabel.setAlignment(Pos.CENTER);

        gameOverStatsArea = new TextArea();
        gameOverStatsArea.getStyleClass().add("stats-area");
        gameOverStatsArea.setEditable(false);
        gameOverStatsArea.setWrapText(true);
        gameOverStatsArea.setPrefRowCount(12);
        gameOverStatsArea.setMaxWidth(560);

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER);

        Button rematchButton = new Button("REQUEST REMATCH");
        rematchButton.getStyleClass().add("primary-button");
        rematchButton.setOnAction(event -> requestRematch());

        Button returnButton = new Button("RETURN TO LOBBY");
        returnButton.getStyleClass().add("secondary-button");
        returnButton.setOnAction(event -> switchScene(gameScene));

        actions.getChildren().addAll(rematchButton, returnButton);
        card.getChildren().addAll(gameOverWinnerLabel, versusLabel, gameOverBodyLabel, gameOverStatsArea, actions);
        root.getChildren().add(card);
        return new Scene(root, 1360, 900);
    }

    private void joinQueueButton() {
        joinQueueButton = new Button("Join Queue");
        joinQueueButton.getStyleClass().addAll("primary-button", "queue-control-button");
        joinQueueButton.setOnAction(event -> sendQueueCommand(true));
    }

    private void leaveQueueButton() {
        leaveQueueButton = new Button("Leave Queue");
        leaveQueueButton.getStyleClass().addAll("secondary-button", "queue-control-button");
        leaveQueueButton.setOnAction(event -> sendQueueCommand(false));
    }

    private VBox createQueueCard() {
        VBox card = new VBox(10);
        card.getStyleClass().addAll("hud-block", "queue-card");
        Label label = new Label("QUEUE");
        label.getStyleClass().add("hud-label");
        HBox buttons = new HBox(10, joinQueueButton, leaveQueueButton);
        buttons.getStyleClass().add("queue-button-row");
        buttons.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(label, queueValueLabel, buttons);
        return card;
    }

    private VBox createHudBlock(String title, Label valueLabel) {
        VBox block = new VBox(6);
        block.getStyleClass().add("hud-block");
        Label blockTitle = new Label(title);
        blockTitle.getStyleClass().add("hud-label");
        block.getChildren().addAll(blockTitle, valueLabel);
        return block;
    }

    private Label createHudValue(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hud-value");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private void initializeBoardSquares() {
        boardGrid = new GridPane();
        boardGrid.getStyleClass().add("board-grid");
        boardGrid.setAlignment(Pos.CENTER);

        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                int boardRow = row;
                int boardCol = col;
                StackPane square = new StackPane();
                square.setMinSize(CELL_SIZE, CELL_SIZE);
                square.setPrefSize(CELL_SIZE, CELL_SIZE);
                square.setMaxSize(CELL_SIZE, CELL_SIZE);
                square.getStyleClass().add((row + col) % 2 == 0 ? "light-square" : "dark-square");
                square.setOnMouseClicked(event -> handleBoardClick(boardRow, boardCol));
                boardSquares[row][col] = square;
            }
        }
    }

    public void handleBoardClick(int row, int col) {
        if (!isMyTurn() || legalMoves.isEmpty()) {
            return;
        }

        Coord clicked = new Coord(row, col);
        if (selectedSource != null && legalMoves.getOrDefault(selectedSource, List.of()).contains(clicked)) {
            Message moveAttempt = new Message(Message.Type.MOVE_ATTEMPT);
            moveAttempt.setSender(connectedUsername);
            moveAttempt.setFromRow(selectedSource.row());
            moveAttempt.setFromCol(selectedSource.col());
            moveAttempt.setToRow(clicked.row());
            moveAttempt.setToCol(clicked.col());
            try {
                clientConnection.send(moveAttempt);
                selectedSource = null;
                legalMoves.clear();
                refreshBoardHighlights();
                setTurnStatus("Sending move");
            } catch (IOException exception) {
                appendGlobalChat("SYSTEM", "Unable to send move: " + exception.getMessage());
            }
            return;
        }

        if (legalMoves.containsKey(clicked)) {
            if (clicked.equals(selectedSource) && legalMoves.size() > 1) {
                selectedSource = null;
            } else {
                selectedSource = clicked;
            }
            refreshBoardHighlights();
        } else if (selectedSource != null && legalMoves.size() > 1) {
            selectedSource = null;
            refreshBoardHighlights();
        }
    }

    public void handleIncomingMessage(Message message) {
        if (message == null || message.getType() == null) {
            return;
        }

        switch (message.getType()) {
            case LOGIN_SUCCESS -> applyLoginSuccess(message);
            case LOGIN_FAIL -> showWelcomeStatus(message.getTextContent(), true);
            case CLIENT_LIST_UPDATE -> applyClientListUpdate(message);
            case GLOBAL_CHAT -> appendGlobalChat(message.getSender(), message.getTextContent());
            case PRIVATE_CHAT -> appendPrivateChat(message.getSender(), message.getTextContent());
            case CHALLENGE_REQUEST -> handleQueueOrChallengeRequest(message);
            case CHALLENGE_ACCEPT -> handleQueueOrChallengeAccept(message);
            case CHALLENGE_DECLINE -> handleQueueOrChallengeDecline(message);
            case GAME_START -> applyGameStart(message);
            case GAME_STATE_UPDATE -> applyGameStateUpdate(message);
            case GAME_OVER -> applyGameOver(message);
            default -> appendGlobalChat("SYSTEM", "Received " + message.getType() + ".");
        }
    }

    public void redrawBoard() {
        Platform.runLater(() -> {
            refreshBoardLayout();
            for (int row = 0; row < BOARD_SIZE; row++) {
                for (int col = 0; col < BOARD_SIZE; col++) {
                    StackPane square = boardSquares[row][col];
                    square.getChildren().clear();
                    int piece = boardState[row][col];
                    if (piece != 0) {
                        square.getChildren().add(createPieceNode(piece, row, col));
                    }
                }
            }
            refreshBoardHighlights();
        });
    }

    private void handleConnect() {
        if (connectedToServer) {
            switchScene(gameScene);
            return;
        }

        String username = usernameField.getText().trim();
        String host = hostField.getText().trim();
        if (host.isBlank()) {
            host = "127.0.0.1";
            hostField.setText(host);
        }

        if (username.isBlank()) {
            showWelcomeStatus("Enter a username first.", true);
            return;
        }

        setWelcomeBusy(true);
        showWelcomeStatus("Connecting...", false);

        String finalHost = host;
        String finalUsername = username;
        Thread connector = new Thread(() -> {
            Client.ConnectResult result = clientConnection.connect(finalHost, PORT, finalUsername);
            if (!result.isSuccess()) {
                showWelcomeStatus(result.getDetail(), true);
                setWelcomeBusy(false);
                return;
            }

            connectedToServer = true;
            connectedUsername = finalUsername;
            connectedHost = finalHost;
            applyLoginSuccess(result.getResponseMessage());
        }, "checkers-2088-connect");
        connector.setDaemon(true);
        connector.start();
    }

    private void sendQueueCommand(boolean join) {
        if (!connectedToServer) {
            return;
        }

        Message queueMessage = new Message(join ? Message.Type.CHALLENGE_REQUEST : Message.Type.CHALLENGE_DECLINE);
        queueMessage.setSender(connectedUsername);
        queueMessage.setTextContent("QUEUE");

        try {
            clientConnection.send(queueMessage);
        } catch (IOException exception) {
            appendGlobalChat("SYSTEM", "Unable to update queue: " + exception.getMessage());
        }
    }

    private void requestRematch() {
        if (!connectedToServer || lastMatchOpponentName.isBlank()) {
            return;
        }

        Message rematchMessage = new Message(Message.Type.CHALLENGE_REQUEST);
        rematchMessage.setSender(connectedUsername);
        rematchMessage.setRecipients(List.of(lastMatchOpponentName));
        rematchMessage.setTextContent("REMATCH");

        try {
            clientConnection.send(rematchMessage);
            appendGlobalChat("SYSTEM", "Rematch request sent to " + lastMatchOpponentName + ".");
            switchScene(gameScene);
        } catch (IOException exception) {
            appendGlobalChat("SYSTEM", "Unable to send rematch request: " + exception.getMessage());
        }
    }

    private void sendChatMessage() {
        if (!connectedToServer) {
            return;
        }

        String body = chatInputField.getText().trim();
        if (body.isEmpty()) {
            return;
        }

        if (activeChatMode == ChatMode.PRIVATE && !matchActive) {
            appendGlobalChat("SYSTEM", "Private chat opens when the match starts.");
            chatInputField.clear();
            return;
        }

        Message message = new Message(activeChatMode == ChatMode.PRIVATE ? Message.Type.PRIVATE_CHAT : Message.Type.GLOBAL_CHAT);
        message.setSender(connectedUsername);
        message.setTextContent(body);
        if (activeChatMode == ChatMode.PRIVATE && !opponentName.isBlank()) {
            message.setRecipients(List.of(opponentName));
        }

        try {
            clientConnection.send(message);
            chatInputField.clear();
        } catch (IOException exception) {
            appendGlobalChat("SYSTEM", "Unable to send chat: " + exception.getMessage());
        }
    }

    private void applyLoginSuccess(Message message) {
        if (message == null) {
            return;
        }

        // Wrapped in runLater for UI thread safety.
        Platform.runLater(() -> {
            connectedToServer = true;
            updateOnlineUsers(message.getOnlineUsers());
            lastStatsText = message.getStatsText();
            setPlayerStatus(connectedUsername);
            setQueueStatus("Open lobby");
            setSideStatus("Unassigned");
            setTurnStatus("Join queue to start");
            setOpponentStatus("No opponent");
            refreshStatsPanel();
            updateQueueButtons();
            appendGlobalChat("SYSTEM", "Connected to " + connectedHost + ":" + PORT + " as " + connectedUsername + ".");
            setWelcomeBusy(false);
            showWelcomeStatus("Connected.", false);
            switchScene(gameScene);
        });
    }

    private void applyClientListUpdate(Message message) {
        Platform.runLater(() -> {
            updateOnlineUsers(message.getOnlineUsers());
            if (!message.getStatsText().isBlank()) {
                lastStatsText = message.getStatsText();
            }
            refreshStatsPanel();
        });
    }

    private void handleQueueOrChallengeRequest(Message message) {
        Platform.runLater(() -> {
            if ("QUEUE_JOINED".equalsIgnoreCase(message.getTextContent())) {
                waitingInQueue = true;
                setQueueStatus("Waiting");
                setTurnStatus("Waiting for opponent");
                updateQueueButtons();
                appendGlobalChat("SYSTEM", "You joined the queue.");
            } else if ("REMATCH_REQUEST".equalsIgnoreCase(message.getTextContent())) {
                pendingRematchFrom = message.getSender();
                lastMatchOpponentName = message.getSender();
                appendGlobalChat("SYSTEM", message.getSender() + " requested a rematch.");
                if (gameOverBodyLabel != null) {
                    gameOverBodyLabel.setText(message.getSender() + " requested a rematch. Press REQUEST REMATCH to accept.");
                }
            }
        });
    }

    private void handleQueueOrChallengeAccept(Message message) {
        Platform.runLater(() -> appendGlobalChat("SYSTEM", message.getTextContent()));
    }

    private void handleQueueOrChallengeDecline(Message message) {
        Platform.runLater(() -> {
            if ("QUEUE_LEFT".equalsIgnoreCase(message.getTextContent())) {
                waitingInQueue = false;
                setQueueStatus(matchActive ? "Match live" : "Open lobby");
                if (!matchActive) {
                    setTurnStatus("Join queue to start");
                }
                updateQueueButtons();
                appendGlobalChat("SYSTEM", "You left the queue.");
            }
        });
    }

    private void applyGameStart(Message message) {
        Platform.runLater(() -> {
            matchActive = true;
            waitingInQueue = false;
            opponentName = message.getTextContent();
            lastMatchOpponentName = opponentName;
            pendingRematchFrom = "";
            myColorBlack = "ONYX".equalsIgnoreCase(message.getSideAssignment());
            boardState = message.getBoardState() == null ? createOpeningBoardState() : message.getBoardState();
            serverBlackTurn = message.isBlackTurn();
            lastStatsText = mergeStatsText(message.getStatsText());
            privateTranscript.setLength(0);
            selectedSource = null;
            legalMoves.clear();
            privateChatToggle.setDisable(false);
            setQueueStatus("Match live");
            setSideStatus(myColorBlack ? "Onyx" : "Ruby");
            setOpponentStatus(opponentName);
            setTurnStatus(describeTurn(serverBlackTurn, false));
            refreshStatsPanel();
            updateQueueButtons();
            appendGlobalChat("SYSTEM", "Matched with " + opponentName + ".");
            appendPrivateChat("SYSTEM", "Private match chat opened.");
            redrawBoard();
        });
    }

    private void applyGameStateUpdate(Message message) {
        Platform.runLater(() -> {
            if (message.getBoardState() != null) {
                boardState = message.getBoardState();
            }
            serverBlackTurn = message.isBlackTurn();
            lastStatsText = mergeStatsText(message.getStatsText());
            setLegalMoves(message.getLegalMoves(), message.getFocusRow(), message.getFocusCol());
            setTurnStatus(describeTurn(serverBlackTurn, message.isJumpChainActive()));
            if (!message.getTextContent().isBlank()) {
                appendSystemNoteIfUseful(message.getTextContent());
            }
            refreshStatsPanel();
            redrawBoard();
        });
    }

    private void applyGameOver(Message message) {
        Platform.runLater(() -> {
            matchActive = false;
            waitingInQueue = false;
            selectedSource = null;
            legalMoves.clear();
            privateChatToggle.setDisable(true);
            globalChatToggle.setSelected(true);
            activeChatMode = ChatMode.GLOBAL;
            lastMatchOpponentName = opponentName.isBlank() ? lastMatchOpponentName : opponentName;
            opponentName = "";
            myColorBlack = false;
            lastGameReason = message.getTextContent();
            lastStatsText = mergeStatsText(message.getStatsText());
            setQueueStatus("Open lobby");
            setSideStatus("Unassigned");
            setTurnStatus("Match finished");
            setOpponentStatus("No opponent");
            refreshStatsPanel();
            refreshChatTranscript();
            updateQueueButtons();
            gameOverWinnerLabel.setText(formatWinnerDisplay(message.getWinner()));
            gameOverBodyLabel.setText(buildGameOverBody(message));
            gameOverStatsArea.setText(refreshStatsText());
            appendGlobalChat("SYSTEM", formatWinnerDisplay(message.getWinner()) + ". " + message.getTextContent());
            appendPrivateChat("SYSTEM", formatWinnerDisplay(message.getWinner()) + ". " + message.getTextContent());
            switchScene(gameOverScene);
        });
    }

    private StackPane createPieceNode(int piece, int row, int col) {
        StackPane shell = new StackPane();
        shell.getStyleClass().add("piece-shell");

        Circle token = new Circle(24);
        token.getStyleClass().add(piece > 0 ? "piece-red" : "piece-black");
        shell.getChildren().add(token);

        if (Math.abs(piece) == 2) {
            Circle ring = new Circle(13);
            ring.getStyleClass().add("king-ring");
            Text glyph = new Text("\u265A");
            glyph.getStyleClass().add("king-glyph");
            shell.getChildren().addAll(ring, glyph);
        }

        shell.setOnMouseEntered(event -> {
            shell.setScaleX(1.1);
            shell.setScaleY(1.1);
        });
        shell.setOnMouseExited(event -> {
            shell.setScaleX(1.0);
            shell.setScaleY(1.0);
        });
        shell.setOnMouseClicked(event -> {
            handleBoardClick(row, col);
            event.consume();
        });
        return shell;
    }

    private void refreshBoardLayout() {
        boardGrid.getChildren().clear();
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                // Flip the board for the Onyx side.
                int displayRow = myColorBlack ? BOARD_SIZE - 1 - row : row;
                int displayCol = myColorBlack ? BOARD_SIZE - 1 - col : col;
                boardGrid.add(boardSquares[row][col], displayCol, displayRow);
            }
        }
    }

    private void refreshBoardHighlights() {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                boardSquares[row][col].getStyleClass().removeAll("valid-target", "selected-square");
            }
        }

        if (selectedSource != null) {
            addStyleClass(boardSquares[selectedSource.row()][selectedSource.col()], "selected-square");
            for (Coord destination : legalMoves.getOrDefault(selectedSource, List.of())) {
                addStyleClass(boardSquares[destination.row()][destination.col()], "valid-target");
            }
        }
    }

    private void setLegalMoves(List<int[]> encodedMoves, int focusRow, int focusCol) {
        legalMoves.clear();
        if (encodedMoves != null) {
            for (int[] move : encodedMoves) {
                if (move.length < 4) {
                    continue;
                }
                Coord from = new Coord(move[0], move[1]);
                Coord to = new Coord(move[2], move[3]);
                legalMoves.computeIfAbsent(from, ignored -> new ArrayList<>()).add(to);
            }
        }

        if (focusRow >= 0 && focusCol >= 0) {
            selectedSource = new Coord(focusRow, focusCol);
        } else if (legalMoves.size() == 1) {
            selectedSource = legalMoves.keySet().iterator().next();
        } else if (selectedSource != null && !legalMoves.containsKey(selectedSource)) {
            selectedSource = null;
        }

        refreshBoardHighlights();
    }

    private boolean isMyTurn() {
        return matchActive && myColorBlack == serverBlackTurn;
    }

    private void appendGlobalChat(String from, String body) {
        Platform.runLater(() -> {
            if (globalTranscript.length() > 0) {
                globalTranscript.append(System.lineSeparator());
            }
            globalTranscript.append(formatTranscriptLine(from, body));
            if (activeChatMode == ChatMode.GLOBAL) {
                refreshChatTranscript();
            }
        });
    }

    private void appendPrivateChat(String from, String body) {
        Platform.runLater(() -> {
            if (privateTranscript.length() > 0) {
                privateTranscript.append(System.lineSeparator());
            }
            privateTranscript.append(formatTranscriptLine(from, body));
            if (activeChatMode == ChatMode.PRIVATE) {
                refreshChatTranscript();
            }
        });
    }

    private void appendSystemNoteIfUseful(String note) {
        if (note == null || note.isBlank()) {
            return;
        }
        if (note.equals(lastGameReason)) {
            return;
        }
        appendGlobalChat("SYSTEM", note);
    }

    private void refreshChatTranscript() {
        String transcript = activeChatMode == ChatMode.PRIVATE ? privateTranscript.toString() : globalTranscript.toString();
        chatTranscriptArea.setText(transcript);
        chatTranscriptArea.positionCaret(chatTranscriptArea.getLength());
    }

    private void refreshStatsPanel() {
        String text = refreshStatsText();
        if (statsArea != null) {
            statsArea.setText(text);
        }
        if (gameOverStatsArea != null) {
            gameOverStatsArea.setText(text);
        }
    }

    private String refreshStatsText() {
        StringBuilder builder = new StringBuilder();
        builder.append("Online Users").append(System.lineSeparator());
        if (onlineUsers.isEmpty()) {
            builder.append("No players connected.");
        } else {
            builder.append(String.join(System.lineSeparator(), onlineUsers));
        }

        if (!lastStatsText.isBlank()) {
            builder.append(System.lineSeparator()).append(System.lineSeparator());
            builder.append(lastStatsText.strip());
        }
        return builder.toString();
    }

    private void updateOnlineUsers(List<String> names) {
        onlineUsers.clear();
        if (names != null) {
            onlineUsers.addAll(names);
        }
    }

    private void updateQueueButtons() {
        Platform.runLater(() -> {
            boolean canJoin = connectedToServer && !waitingInQueue && !matchActive;
            boolean canLeave = connectedToServer && waitingInQueue;
            joinQueueButton.setDisable(!canJoin);
            leaveQueueButton.setDisable(!canLeave);
        });
    }

    private void handleDisconnect(String reason) {
        connectedToServer = false;
        waitingInQueue = false;
        matchActive = false;
        myColorBlack = false;
        serverBlackTurn = false;
        opponentName = "";
        lastMatchOpponentName = "";
        pendingRematchFrom = "";
        boardState = createOpeningBoardState();
        selectedSource = null;
        legalMoves.clear();

        Platform.runLater(() -> {
            setPlayerStatus("Offline");
            setQueueStatus("Offline");
            setSideStatus("Unassigned");
            setTurnStatus("Connect to begin");
            setOpponentStatus("No opponent");
            privateChatToggle.setDisable(true);
            globalChatToggle.setSelected(true);
            activeChatMode = ChatMode.GLOBAL;
            updateQueueButtons();
            redrawBoard();
            refreshBoardHighlights();
            showWelcomeStatus(reason, true);
            setWelcomeBusy(false);
            switchScene(welcomeScene);
        });
    }

    private void setPlayerStatus(String text) {
        Platform.runLater(() -> playerValueLabel.setText(text));
    }

    private void setQueueStatus(String text) {
        Platform.runLater(() -> queueValueLabel.setText(text));
    }

    private void setSideStatus(String text) {
        Platform.runLater(() -> sideValueLabel.setText(text));
    }

    private void setTurnStatus(String text) {
        Platform.runLater(() -> turnValueLabel.setText(text));
    }

    private void setOpponentStatus(String text) {
        Platform.runLater(() -> opponentValueLabel.setText(text));
    }

    private void showWelcomeStatus(String text, boolean error) {
        Platform.runLater(() -> {
            welcomeStatusLabel.setText(text);
            welcomeStatusLabel.getStyleClass().removeAll("error-text", "info-text");
            welcomeStatusLabel.getStyleClass().add(error ? "error-text" : "info-text");
        });
    }

    private void setWelcomeBusy(boolean busy) {
        Platform.runLater(() -> {
            usernameField.setDisable(busy);
            hostField.setDisable(busy);
            connectButton.setDisable(busy);
        });
    }

    private String formatTranscriptLine(String from, String body) {
        return "[" + from + "] " + body;
    }

    private String mergeStatsText(String freshText) {
        return freshText == null || freshText.isBlank() ? lastStatsText : freshText;
    }

    private String describeTurn(boolean blackTurn, boolean jumpChain) {
        if (!matchActive) {
            return waitingInQueue ? "Waiting for opponent" : "Join queue to start";
        }
        if (jumpChain) {
            return myColorBlack == blackTurn ? "Jump again" : "Opponent jumping";
        }
        return myColorBlack == blackTurn ? "Your move" : "Opponent's move";
    }

    private String formatWinnerDisplay(String winner) {
        if (winner == null || winner.isBlank()) {
            return "MATCH OVER";
        }
        if ("DRAW".equalsIgnoreCase(winner)) {
            return "DRAW";
        }
        return winner;
    }

    private String buildGameOverBody(Message message) {
        if ("DRAW".equalsIgnoreCase(message.getWinner())) {
            return "Both players held the line. " + message.getTextContent();
        }
        if (lastMatchOpponentName.isBlank()) {
            return message.getTextContent();
        }
        return connectedUsername + " vs " + lastMatchOpponentName + ". " + message.getTextContent();
    }

    private int[][] createOpeningBoardState() {
        int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if ((row + col) % 2 == 1) {
                    board[row][col] = -1;
                }
            }
        }
        for (int row = 5; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if ((row + col) % 2 == 1) {
                    board[row][col] = 1;
                }
            }
        }
        return board;
    }

    private void playTitleIntro() {
        titleHero.setScaleX(0.58);
        titleHero.setScaleY(0.58);

        ScaleTransition overshoot = new ScaleTransition(Duration.millis(700), titleHero);
        overshoot.setFromX(0.58);
        overshoot.setFromY(0.58);
        overshoot.setToX(1.08);
        overshoot.setToY(1.08);
        overshoot.setInterpolator(Interpolator.SPLINE(0.2, 0.85, 0.18, 1.0));

        ScaleTransition settle = new ScaleTransition(Duration.millis(220), titleHero);
        settle.setFromX(1.08);
        settle.setFromY(1.08);
        settle.setToX(1.0);
        settle.setToY(1.0);
        settle.setInterpolator(Interpolator.EASE_OUT);

        overshoot.setOnFinished(event -> {
            settle.play();
            startTitlePulse();
        });
        overshoot.play();
    }

    private void startTitlePulse() {
        // Keep the title from feeling flat.
        ScaleTransition pulse = new ScaleTransition(Duration.millis(1500), titleHero);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.05);
        pulse.setToY(1.05);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setAutoReverse(true);
        pulse.play();
    }

    private StackPane buildTitleBadge() {
        Circle outerRing = new Circle(72);
        outerRing.getStyleClass().add("title-badge-ring");
        outerRing.setStrokeType(StrokeType.OUTSIDE);

        Circle innerCore = new Circle(58);
        innerCore.getStyleClass().add("title-badge-core");

        Label badgeText = new Label("2088");
        badgeText.getStyleClass().add("title-badge-text");

        return new StackPane(outerRing, innerCore, badgeText);
    }

    private void switchScene(Scene targetScene) {
        Platform.runLater(() -> {
            if (targetScene == null) {
                return;
            }

            // Fade the scene instead of swapping hard.
            targetScene.getRoot().setOpacity(0);
            stage.setScene(targetScene);
            FadeTransition fade = new FadeTransition(Duration.millis(500), targetScene.getRoot());
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();
        });
    }

    private void addStyleClass(Region region, String styleClass) {
        if (!region.getStyleClass().contains(styleClass)) {
            region.getStyleClass().add(styleClass);
        }
    }

    private void applyStylesheet(Scene scene) {
        scene.getStylesheets().add(stylesheet);
    }
}
