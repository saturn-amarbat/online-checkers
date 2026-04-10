import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.util.ArrayList;

public class GuiClient extends Application {

    private static final String APP_CSS = "/styles/style.css";

    private Client clientConnection;
    private Stage primaryStage;

    private String username;
    private String opponent;
    private boolean myColorBlack;
    private int[][] boardState;

    private int selectedRow = -1;
    private int selectedCol = -1;

    private final ObservableList<String> onlineUsers = FXCollections.observableArrayList();
    private final ObservableList<String> moveHistory = FXCollections.observableArrayList();

    private Scene welcomeScene;
    private Scene loginScene;
    private Scene instructionsScene;
    private Scene lobbyScene;
    private Scene arenaScene;
    private Scene gameOverScene;

    private TextField hostField;
    private TextField usernameField;
    private Label loginStatusLabel;

    private Label profileNameLabel;
    private Label lobbyStatusLabel;
    private ListView<String> leaderboardList;
    private ListView<String> friendsList;
    private TextArea lobbyChatArea;
    private TextField lobbyChatInput;

    private Label arenaTitleLabel;
    private Label playerOneTimerLabel;
    private Label playerTwoTimerLabel;
    private GridPane boardGrid;
    private final Button[][] boardButtons = new Button[8][8];
    private ListView<String> moveListView;
    private TextArea arenaChatArea;
    private TextField arenaChatInput;

    private Label gameOverWinnerLabel;
    private Label gameOverVersusLabel;
    private CheckBox accessibilityToggle;
    private boolean accessibilityModeEnabled;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        // Network callback already marshals every network event onto the JavaFX thread.
        clientConnection = new Client(
            msg -> Platform.runLater(() -> handleIncomingMessage(msg)),
            status -> Platform.runLater(() -> updateStatus(status))
        );

        welcomeScene = buildWelcomeScreen();
        loginScene = buildLoginScreen();
        instructionsScene = buildInstructionsScreen();
        lobbyScene = buildLobbyScreen();
        arenaScene = buildArenaScreen();
        gameOverScene = buildGameOverScreen("Winner Pending");

        stage.setOnCloseRequest(this::onClose);
        stage.setTitle("Checkers Pro");
        stage.setScene(welcomeScene);
        stage.show();
    }

    private void onClose(WindowEvent event) {
        if (clientConnection != null) {
            clientConnection.close();
        }
        Platform.exit();
        System.exit(0);
    }

    private Scene buildWelcomeScreen() {
        VBox root = new VBox(24);
        root.getStyleClass().addAll("screen-root", "welcome-root");
        root.setAlignment(Pos.CENTER);

        Label title = new Label("CHECKERS PRO");
        title.getStyleClass().add("title-hero");

        Label subtitle = new Label("Networked strategy. Competitive polish.");
        subtitle.getStyleClass().add("subtitle-muted");

        StackPane animationBox = new StackPane();
        animationBox.getStyleClass().add("card-panel");
        animationBox.setPrefSize(420, 230);
        Label animationLabel = new Label("Startup Animation / GIF Placeholder");
        animationLabel.getStyleClass().add("placeholder-text");
        animationBox.getChildren().add(animationLabel);

        Button startButton = new Button("Start Game");
        startButton.getStyleClass().add("cta-button");
        startButton.setOnAction(e -> primaryStage.setScene(loginScene));

        Button howToPlayButton = new Button("How To Play");
        howToPlayButton.getStyleClass().add("primary-button");
        howToPlayButton.setOnAction(e -> primaryStage.setScene(instructionsScene));

        root.getChildren().addAll(title, subtitle, animationBox, startButton, howToPlayButton);
        return createStyledScene(root);
    }

    private Scene buildLoginScreen() {
        VBox root = new VBox(16);
        root.getStyleClass().addAll("screen-root", "login-root");
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));

        StackPane logoBox = new StackPane();
        logoBox.getStyleClass().add("logo-placeholder");
        logoBox.setPrefSize(520, 160);
        logoBox.getChildren().add(new Label("Your Checkers Pro Logo"));

        Label heading = new Label("Connect To Server");
        heading.getStyleClass().add("screen-heading");

        hostField = new TextField("127.0.0.1");
        hostField.setPromptText("Server IP");
        hostField.getStyleClass().add("modern-input");
        hostField.setMaxWidth(320);

        usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.getStyleClass().add("modern-input");
        usernameField.setMaxWidth(320);

        Button connectButton = new Button("Connect");
        connectButton.getStyleClass().add("primary-button");
        connectButton.setOnAction(e -> attemptLogin());

        loginStatusLabel = new Label("Enter server IP and username");
        loginStatusLabel.getStyleClass().add("status-line");

        root.getChildren().addAll(logoBox, heading, hostField, usernameField, connectButton, loginStatusLabel);
        return createStyledScene(root);
    }

    private Scene buildInstructionsScreen() {
        VBox root = new VBox(16);
        root.getStyleClass().addAll("screen-root", "instructions-root");
        root.setPadding(new Insets(18));

        Button returnButton = new Button("<- Return");
        returnButton.getStyleClass().add("back-button");
        returnButton.setOnAction(e -> {
            if (username == null || username.isEmpty()) {
                primaryStage.setScene(welcomeScene);
            } else {
                primaryStage.setScene(lobbyScene);
            }
        });

        HBox topBar = new HBox(returnButton);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("How To Play Checkers");
        title.getStyleClass().add("screen-heading");

        VBox instructionsList = new VBox(14);
        instructionsList.setFillWidth(true);

        String[] rules = new String[] {
            "Pieces move diagonally on dark squares.",
            "You may move one square forward unless capturing.",
            "Jumps are mandatory when an opponent piece can be captured.",
            "A jump captures the opponent piece you leap over.",
            "Reach the opposite end to become a King and move both directions."
        };

        for (int i = 0; i < rules.length; i++) {
            StackPane card = new StackPane();
            card.getStyleClass().add("instruction-card");
            card.setPrefHeight(86);

            Label ruleText = new Label(rules[i]);
            ruleText.getStyleClass().add("instruction-text");
            card.getChildren().add(ruleText);

            instructionsList.getChildren().add(card);
        }

        root.getChildren().addAll(topBar, title, instructionsList);
        return createStyledScene(root);
    }

    private Scene buildLobbyScreen() {
        BorderPane root = new BorderPane();
        root.getStyleClass().addAll("screen-root", "lobby-root");
        root.setPadding(new Insets(16));

        Button returnButton = new Button("<- Return");
        returnButton.getStyleClass().add("back-button");
        returnButton.setOnAction(e -> primaryStage.setScene(loginScene));
        HBox topBar = new HBox(returnButton);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        root.setTop(topBar);
        BorderPane.setMargin(topBar, new Insets(0, 0, 10, 0));

        VBox left = new VBox(14);
        left.setPrefWidth(250);

        VBox profileCard = new VBox(8);
        profileCard.getStyleClass().add("card-panel");
        Label profileTitle = new Label("Player Profile");
        profileTitle.getStyleClass().add("panel-title");
        profileNameLabel = new Label("Not Connected");
        profileNameLabel.getStyleClass().add("profile-name");
        lobbyStatusLabel = new Label("Idle");
        lobbyStatusLabel.getStyleClass().add("status-line");
        profileCard.getChildren().addAll(profileTitle, profileNameLabel, lobbyStatusLabel);

        VBox modeCard = new VBox(10);
        modeCard.getStyleClass().add("card-panel");
        Label modeTitle = new Label("Game Mode");
        modeTitle.getStyleClass().add("panel-title");

        Button pvpButton = new Button("PvP");
        pvpButton.getStyleClass().add("secondary-button");
        pvpButton.setOnAction(e -> appendLobbyChat("[System] PvP selected. Challenge a player from the list."));

        Button aiButton = new Button("vs AI");
        aiButton.getStyleClass().add("secondary-button");
        aiButton.setOnAction(e -> appendLobbyChat("[System] AI mode is not networked in this build."));

        modeCard.getChildren().addAll(modeTitle, pvpButton, aiButton);

        Button howToPlayButton = new Button("How To Play");
        howToPlayButton.getStyleClass().add("primary-button");
        howToPlayButton.setOnAction(e -> primaryStage.setScene(instructionsScene));

        modeCard.getChildren().add(howToPlayButton);
        left.getChildren().addAll(profileCard, modeCard);

        VBox center = new VBox(14);
        center.setAlignment(Pos.CENTER);
        center.setPadding(new Insets(8));

        Label centerTitle = new Label("Board Skin Preview");
        centerTitle.getStyleClass().add("panel-title");

        StackPane preview = new StackPane();
        preview.getStyleClass().add("preview-panel");
        preview.setPrefSize(460, 360);
        preview.getChildren().add(new Label("3D Rotating Board Preview Placeholder"));

        HBox skins = new HBox(12);
        skins.setAlignment(Pos.CENTER);
        Button leftArrow = new Button("<");
        leftArrow.getStyleClass().add("arrow-button");
        Label skinLabel = new Label("Skin 1");
        skinLabel.getStyleClass().add("skin-label");
        Button rightArrow = new Button(">");
        rightArrow.getStyleClass().add("arrow-button");

        leftArrow.setOnAction(e -> appendLobbyChat("[System] Skin switched left"));
        rightArrow.setOnAction(e -> appendLobbyChat("[System] Skin switched right"));

        skins.getChildren().addAll(leftArrow, skinLabel, rightArrow);

        center.getChildren().addAll(centerTitle, preview, skins);

        VBox right = new VBox(14);
        right.setPrefWidth(300);

        VBox leaderboardCard = new VBox(8);
        leaderboardCard.getStyleClass().add("card-panel");
        Label leaderboardTitle = new Label("Leaderboard / Online Users");
        leaderboardTitle.getStyleClass().add("panel-title");

        leaderboardList = new ListView<>(onlineUsers);
        leaderboardList.setCellFactory(list -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else if (item.equals(username)) {
                    setText(item + " (you)");
                } else {
                    setText(item);
                }
            }
        });
        VBox.setVgrow(leaderboardList, Priority.ALWAYS);

        Button challengeButton = new Button("Challenge Selected");
        challengeButton.getStyleClass().add("primary-button");
        challengeButton.setOnAction(e -> sendChallenge());

        leaderboardCard.getChildren().addAll(leaderboardTitle, leaderboardList, challengeButton);

        VBox friendsCard = new VBox(8);
        friendsCard.getStyleClass().add("card-panel");
        Label friendsTitle = new Label("Online Friends");
        friendsTitle.getStyleClass().add("panel-title");

        friendsList = new ListView<>(onlineUsers);
        friendsList.setPrefHeight(120);

        lobbyChatArea = new TextArea();
        lobbyChatArea.setEditable(false);
        lobbyChatArea.setWrapText(true);
        lobbyChatArea.setPrefRowCount(6);

        lobbyChatInput = new TextField();
        lobbyChatInput.setPromptText("Global chat");
        lobbyChatInput.getStyleClass().add("modern-input");
        lobbyChatInput.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                sendGlobalChat();
            }
        });

        Button lobbySendButton = new Button("Send");
        lobbySendButton.getStyleClass().add("secondary-button");
        lobbySendButton.setOnAction(e -> sendGlobalChat());

        HBox chatBar = new HBox(8, lobbyChatInput, lobbySendButton);
        HBox.setHgrow(lobbyChatInput, Priority.ALWAYS);

        friendsCard.getChildren().addAll(friendsTitle, friendsList, new Label("Lobby Chat"), lobbyChatArea, chatBar);

        right.getChildren().addAll(leaderboardCard, friendsCard);
        VBox.setVgrow(leaderboardCard, Priority.ALWAYS);

        root.setLeft(left);
        root.setCenter(center);
        root.setRight(right);

        return createStyledScene(root);
    }

    private Scene buildArenaScreen() {
        BorderPane root = new BorderPane();
        root.getStyleClass().addAll("screen-root", "arena-root");
        root.setPadding(new Insets(16));

        Button returnButton = new Button("<- Return");
        returnButton.getStyleClass().add("back-button");
        returnButton.setOnAction(e -> primaryStage.setScene(lobbyScene));

        accessibilityToggle = new CheckBox("Accessibility mode");
        accessibilityToggle.getStyleClass().add("accessibility-toggle");
        accessibilityToggle.setSelected(accessibilityModeEnabled);
        accessibilityToggle.setOnAction(e -> setAccessibilityMode(accessibilityToggle.isSelected()));

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        HBox topBar = new HBox(10, returnButton, topSpacer, accessibilityToggle);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        arenaTitleLabel = new Label("Match Arena");
        arenaTitleLabel.getStyleClass().add("screen-heading");

        VBox topContainer = new VBox(8, topBar, arenaTitleLabel);
        root.setTop(topContainer);
        BorderPane.setMargin(topContainer, new Insets(0, 0, 12, 0));

        VBox left = new VBox(18);
        left.setPrefWidth(220);

        VBox timerOneCard = new VBox(8);
        timerOneCard.getStyleClass().add("card-panel");
        Label timerOneTitle = new Label("Player 1 Timer");
        timerOneTitle.getStyleClass().add("panel-title");
        playerOneTimerLabel = new Label("05:00");
        playerOneTimerLabel.getStyleClass().add("timer-value");
        timerOneCard.getChildren().addAll(timerOneTitle, playerOneTimerLabel);

        VBox timerTwoCard = new VBox(8);
        timerTwoCard.getStyleClass().add("card-panel");
        Label timerTwoTitle = new Label("Player 2 Timer");
        timerTwoTitle.getStyleClass().add("panel-title");
        playerTwoTimerLabel = new Label("05:00");
        playerTwoTimerLabel.getStyleClass().add("timer-value");
        timerTwoCard.getChildren().addAll(timerTwoTitle, playerTwoTimerLabel);

        left.getChildren().addAll(timerOneCard, timerTwoCard);

        StackPane center = new StackPane();
        center.getStyleClass().add("board-shell");

        boardGrid = new GridPane();
        boardGrid.getStyleClass().add("board-grid");
        boardGrid.setHgap(2);
        boardGrid.setVgap(2);

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Button square = new Button();
                square.getStyleClass().add("board-square");
                square.setPrefSize(62, 62);
                final int r = row;
                final int c = col;
                square.setOnAction(e -> handleBoardClick(r, c));
                boardButtons[row][col] = square;
                boardGrid.add(square, col, row);
            }
        }

        center.getChildren().add(boardGrid);

        VBox right = new VBox(14);
        right.setPrefWidth(300);

        VBox historyCard = new VBox(8);
        historyCard.getStyleClass().add("card-panel");
        Label historyTitle = new Label("Past Moves");
        historyTitle.getStyleClass().add("panel-title");

        moveListView = new ListView<>(moveHistory);
        moveListView.setPrefHeight(180);
        historyCard.getChildren().addAll(historyTitle, moveListView);

        VBox chatCard = new VBox(8);
        chatCard.getStyleClass().add("card-panel");
        Label chatTitle = new Label("Direct Chat");
        chatTitle.getStyleClass().add("panel-title");

        arenaChatArea = new TextArea();
        arenaChatArea.setEditable(false);
        arenaChatArea.setWrapText(true);
        VBox.setVgrow(arenaChatArea, Priority.ALWAYS);

        arenaChatInput = new TextField();
        arenaChatInput.setPromptText("Message opponent");
        arenaChatInput.getStyleClass().add("modern-input");
        arenaChatInput.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                sendGameDirectMessage();
            }
        });

        Button sendButton = new Button("Send");
        sendButton.getStyleClass().add("secondary-button");
        sendButton.setOnAction(e -> sendGameDirectMessage());

        HBox inputBar = new HBox(8, arenaChatInput, sendButton);
        HBox.setHgrow(arenaChatInput, Priority.ALWAYS);

        chatCard.getChildren().addAll(chatTitle, arenaChatArea, inputBar);

        right.getChildren().addAll(historyCard, chatCard);
        VBox.setVgrow(chatCard, Priority.ALWAYS);

        root.setLeft(left);
        root.setCenter(center);
        root.setRight(right);

        return createStyledScene(root);
    }

    private Scene buildGameOverScreen(String winnerText) {
        BorderPane root = new BorderPane();
        root.getStyleClass().addAll("screen-root", "game-over-root");
        root.setPadding(new Insets(20));

        Button returnButton = new Button("<- Return");
        returnButton.getStyleClass().add("back-button");
        returnButton.setOnAction(e -> primaryStage.setScene(lobbyScene));
        HBox topBar = new HBox(returnButton);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        root.setTop(topBar);

        VBox content = new VBox(16);
        content.setAlignment(Pos.CENTER);

        gameOverWinnerLabel = new Label(winnerText);
        gameOverWinnerLabel.getStyleClass().add("game-over-winner");

        gameOverVersusLabel = new Label("P1 vs P2");
        gameOverVersusLabel.getStyleClass().add("versus-line");

        HBox avatars = new HBox(18);
        avatars.setAlignment(Pos.CENTER);
        Region avatarOne = new Region();
        avatarOne.getStyleClass().add("avatar-placeholder");
        Region avatarTwo = new Region();
        avatarTwo.getStyleClass().add("avatar-placeholder");
        Label versus = new Label("VS");
        versus.getStyleClass().add("versus-badge");
        avatars.getChildren().addAll(avatarOne, versus, avatarTwo);

        HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER);

        Button rematchButton = new Button("Play Again (Rematch)");
        rematchButton.getStyleClass().add("primary-button");
        rematchButton.setOnAction(e -> requestRematch());

        Button backLobbyButton = new Button("Back To Lobby");
        backLobbyButton.getStyleClass().add("secondary-button");
        backLobbyButton.setOnAction(e -> primaryStage.setScene(lobbyScene));

        buttons.getChildren().addAll(rematchButton, backLobbyButton);
        content.getChildren().addAll(gameOverWinnerLabel, gameOverVersusLabel, avatars, buttons);

        root.setCenter(content);
        return createStyledScene(root);
    }

    private Scene createStyledScene(Region root) {
        Scene scene = new Scene(root, 1024, 600);
        scene.getStylesheets().add(getClass().getResource(APP_CSS).toExternalForm());
        return scene;
    }

    private void setAccessibilityMode(boolean enabled) {
        accessibilityModeEnabled = enabled;

        applyAccessibilityClass(welcomeScene, enabled);
        applyAccessibilityClass(loginScene, enabled);
        applyAccessibilityClass(lobbyScene, enabled);
        applyAccessibilityClass(arenaScene, enabled);
        applyAccessibilityClass(gameOverScene, enabled);
    }

    private void applyAccessibilityClass(Scene scene, boolean enabled) {
        if (scene == null || scene.getRoot() == null) {
            return;
        }

        if (enabled) {
            if (!scene.getRoot().getStyleClass().contains("accessibility-mode")) {
                scene.getRoot().getStyleClass().add("accessibility-mode");
            }
        } else {
            scene.getRoot().getStyleClass().remove("accessibility-mode");
        }
    }

    private void attemptLogin() {
        String host = hostField.getText().trim();
        String desiredUser = usernameField.getText().trim();

        if (desiredUser.isEmpty()) {
            updateStatus("Username is required");
            return;
        }

        boolean connected = clientConnection.connect(host, 5555);
        if (!connected) {
            return;
        }

        username = desiredUser;
        profileNameLabel.setText("Player: " + username);

        Message login = new Message(Message.Type.LOGIN, desiredUser, "Login request");
        clientConnection.send(login);
    }

    private void sendGlobalChat() {
        String text = lobbyChatInput.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        Message msg = new Message(Message.Type.GLOBAL_CHAT, username, text);
        clientConnection.send(msg);
        lobbyChatInput.clear();
    }

    private void sendChallenge() {
        String target = leaderboardList.getSelectionModel().getSelectedItem();
        if (target == null || target.equals(username)) {
            appendLobbyChat("[System] Select another online user to challenge.");
            return;
        }

        Message msg = new Message(Message.Type.CHALLENGE_REQUEST, username, "Challenge request");
        msg.recipients.add(target);
        clientConnection.send(msg);
    }

    private void sendGameDirectMessage() {
        String text = arenaChatInput.getText().trim();
        if (text.isEmpty() || opponent == null) {
            return;
        }

        Message msg = new Message(Message.Type.PRIVATE_CHAT, username, text);
        msg.recipients.add(opponent);
        clientConnection.send(msg);

        arenaChatInput.clear();
    }

    private void requestRematch() {
        if (opponent == null || opponent.isEmpty()) {
            primaryStage.setScene(lobbyScene);
            return;
        }

        Message msg = new Message(Message.Type.CHALLENGE_REQUEST, username, "Rematch request");
        msg.recipients.add(opponent);
        clientConnection.send(msg);
        primaryStage.setScene(lobbyScene);
        appendLobbyChat("[System] Rematch request sent to " + opponent);
    }

    private void handleIncomingMessage(Message msg) {
        if (msg == null || msg.type == null) {
            return;
        }

        switch (msg.type) {
            case LOGIN_SUCCESS:
                loginStatusLabel.setText(msg.textContent);
                lobbyStatusLabel.setText("Connected");
                appendLobbyChat("[System] " + msg.textContent);
                primaryStage.setScene(lobbyScene);
                break;
            case LOGIN_FAIL:
                loginStatusLabel.setText(msg.textContent);
                lobbyStatusLabel.setText("Login failed");
                break;
            case CLIENT_LIST_UPDATE:
                onlineUsers.setAll(msg.onlineUsers == null ? new ArrayList<>() : msg.onlineUsers);
                break;
            case GLOBAL_CHAT:
                appendLobbyChat("[" + msg.sender + "] " + msg.textContent);
                break;
            case PRIVATE_CHAT:
                appendArenaChat("[DM " + msg.sender + "] " + msg.textContent);
                break;
            case CHALLENGE_REQUEST:
                appendLobbyChat("[System] " + msg.textContent);
                handleChallengePrompt(msg.sender);
                break;
            case CHALLENGE_DECLINE:
                appendLobbyChat("[System] " + msg.textContent);
                break;
            case GAME_START:
                opponent = findOpponent(msg.recipients);
                myColorBlack = msg.textContent != null && msg.textContent.contains("BLACK");
                boardState = msg.boardState;
                selectedRow = -1;
                selectedCol = -1;
                moveHistory.clear();
                redrawBoard();
                updateArenaHeader(msg.isBlackTurn);
                appendArenaChat("[System] " + msg.textContent);
                primaryStage.setScene(arenaScene);
                break;
            case GAME_STATE_UPDATE:
                if (msg.boardState != null) {
                    boardState = msg.boardState;
                    redrawBoard();
                }
                if (msg.textContent != null && !msg.textContent.isEmpty()) {
                    moveHistory.add(msg.textContent);
                    appendArenaChat("[System] " + msg.textContent);
                }
                updateArenaHeader(msg.isBlackTurn);
                break;
            case GAME_OVER:
                if (msg.boardState != null) {
                    boardState = msg.boardState;
                    redrawBoard();
                }
                showGameOver(msg.textContent);
                break;
            default:
                appendLobbyChat("[System] Unhandled message: " + msg.type);
                break;
        }
    }

    private void handleChallengePrompt(String challenger) {
        if (challenger == null || challenger.isEmpty()) {
            return;
        }

        if (primaryStage.getScene() != lobbyScene) {
            Message decline = new Message(Message.Type.CHALLENGE_DECLINE, username, "Busy");
            decline.recipients.add(challenger);
            clientConnection.send(decline);
            return;
        }

        Message accept = new Message(Message.Type.CHALLENGE_ACCEPT, username, "Accepted");
        accept.recipients.add(challenger);
        clientConnection.send(accept);

        appendLobbyChat("[System] Auto-accepted challenge from " + challenger + ".");
    }

    private void handleBoardClick(int row, int col) {
        if (boardState == null) {
            return;
        }

        if (selectedRow == -1) {
            int piece = boardState[row][col];
            if (piece == 0) {
                return;
            }
            if (myColorBlack && piece < 0) {
                appendArenaChat("[System] Select one of your black pieces.");
                return;
            }
            if (!myColorBlack && piece > 0) {
                appendArenaChat("[System] Select one of your red pieces.");
                return;
            }
            selectedRow = row;
            selectedCol = col;
            redrawBoard();
            return;
        }

        Message move = new Message(Message.Type.MOVE_ATTEMPT, username, "Move attempt");
        move.fromRow = selectedRow;
        move.fromCol = selectedCol;
        move.toRow = row;
        move.toCol = col;
        clientConnection.send(move);

        selectedRow = -1;
        selectedCol = -1;
        redrawBoard();
    }

    private void redrawBoard() {
        if (boardState == null) {
            return;
        }

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Button square = boardButtons[row][col];

                boolean dark = (row + col) % 2 == 1;
                square.getStyleClass().removeAll("square-light", "square-dark", "square-selected");
                square.getStyleClass().removeAll("piece-red", "piece-black", "piece-king");
                square.getStyleClass().add(dark ? "square-dark" : "square-light");
                if (row == selectedRow && col == selectedCol) {
                    square.getStyleClass().add("square-selected");
                }

                int piece = boardState[row][col];
                if (piece == -1 || piece == -2) {
                    square.getStyleClass().add("piece-red");
                }
                if (piece == 1 || piece == 2) {
                    square.getStyleClass().add("piece-black");
                }
                if (piece == 2 || piece == -2) {
                    square.getStyleClass().add("piece-king");
                }

                square.setText(pieceSymbol(piece));
            }
        }
    }

    private String pieceSymbol(int piece) {
        switch (piece) {
            case 1:
                return "b";
            case 2:
                return "B";
            case -1:
                return "r";
            case -2:
                return "R";
            default:
                return "";
        }
    }

    private void updateArenaHeader(boolean blackTurn) {
        String playerColor = myColorBlack ? "BLACK" : "RED";
        String turnColor = blackTurn ? "BLACK" : "RED";
        arenaTitleLabel.setText("Match Arena | You: " + playerColor + " | Turn: " + turnColor);

        if (blackTurn) {
            playerOneTimerLabel.setText("Your Turn");
            playerTwoTimerLabel.setText("Waiting");
        } else {
            playerOneTimerLabel.setText("Waiting");
            playerTwoTimerLabel.setText("Your Turn");
        }
    }

    private void showGameOver(String winnerText) {
        String winner = (winnerText == null || winnerText.isEmpty()) ? "Game Over" : winnerText;
        gameOverWinnerLabel.setText(winner);
        gameOverVersusLabel.setText((username == null ? "P1" : username) + " vs " + (opponent == null ? "P2" : opponent));
        appendArenaChat("[System] " + winner);
        primaryStage.setScene(gameOverScene);
    }

    private String findOpponent(ArrayList<String> recipients) {
        if (recipients == null) {
            return null;
        }
        for (String user : recipients) {
            if (!user.equals(username)) {
                return user;
            }
        }
        return null;
    }

    private void appendLobbyChat(String line) {
        if (lobbyChatArea != null) {
            lobbyChatArea.appendText(line + "\n");
        }
    }

    private void appendArenaChat(String line) {
        if (arenaChatArea != null) {
            arenaChatArea.appendText(line + "\n");
        }
    }

    private void updateStatus(String status) {
        if (loginStatusLabel != null) {
            loginStatusLabel.setText(status);
        }
        if (lobbyStatusLabel != null) {
            lobbyStatusLabel.setText(status);
        }
        appendLobbyChat("[Status] " + status);
    }
}
