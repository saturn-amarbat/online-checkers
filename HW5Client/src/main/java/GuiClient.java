import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.util.ArrayList;

public class GuiClient extends Application {

    private Client clientConnection;
    private Stage primaryStage;

    private String username;
    private String opponent;
    private boolean myColorBlack;
    private int[][] boardState;

    private int selectedRow = -1;
    private int selectedCol = -1;

    private final ObservableList<String> onlineUsers = FXCollections.observableArrayList();

    private Scene loginScene;
    private Scene lobbyScene;
    private Scene gameScene;

    private TextField hostField;
    private TextField portField;
    private TextField usernameField;
    private Label loginStatusLabel;

    private ListView<String> lobbyUsersList;
    private TextArea globalChatArea;
    private TextField globalChatInput;

    private GridPane boardGrid;
    private final Button[][] boardButtons = new Button[8][8];
    private Label gameStatusLabel;
    private TextArea gameChatArea;
    private TextField gameChatInput;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        clientConnection = new Client(
            msg -> Platform.runLater(() -> handleIncomingMessage(msg)),
            status -> Platform.runLater(() -> updateStatus(status))
        );

        loginScene = buildLoginScene();
        lobbyScene = buildLobbyScene();
        gameScene = buildGameScene();

        stage.setOnCloseRequest(this::onClose);
        stage.setTitle("HW5 + Project3 Checkers Client");
        stage.setScene(loginScene);
        stage.show();
    }

    private void onClose(WindowEvent event) {
        if (clientConnection != null) {
            clientConnection.close();
        }
        Platform.exit();
        System.exit(0);
    }

    private Scene buildLoginScene() {
        Label title = new Label("Login");
        title.setStyle("-fx-font-size: 24; -fx-font-weight: bold;");

        hostField = new TextField("127.0.0.1");
        portField = new TextField("5555");
        usernameField = new TextField();
        loginStatusLabel = new Label("Enter server and username");

        Button loginButton = new Button("Connect + Login");
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setOnAction(e -> attemptLogin());

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label("Server IP:"), 0, 0);
        form.add(hostField, 1, 0);
        form.add(new Label("Port:"), 0, 1);
        form.add(portField, 1, 1);
        form.add(new Label("Username:"), 0, 2);
        form.add(usernameField, 1, 2);

        VBox root = new VBox(14, title, form, loginButton, loginStatusLabel);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER_LEFT);

        return new Scene(root, 460, 280);
    }

    private Scene buildLobbyScene() {
        Label title = new Label("Lobby");
        title.setStyle("-fx-font-size: 22; -fx-font-weight: bold;");

        lobbyUsersList = new ListView<>(onlineUsers);
        lobbyUsersList.setCellFactory(list -> new ListCell<String>() {
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

        Button challengeButton = new Button("Challenge to Checkers");
        challengeButton.setMaxWidth(Double.MAX_VALUE);
        challengeButton.setOnAction(e -> sendChallenge());

        VBox usersPane = new VBox(10, new Label("Online Users"), lobbyUsersList, challengeButton);
        usersPane.setPadding(new Insets(10));
        usersPane.setPrefWidth(250);
        VBox.setVgrow(lobbyUsersList, Priority.ALWAYS);

        globalChatArea = new TextArea();
        globalChatArea.setEditable(false);
        globalChatArea.setWrapText(true);

        globalChatInput = new TextField();
        globalChatInput.setPromptText("Type global message and press Enter");
        globalChatInput.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                sendGlobalChat();
            }
        });

        Button sendButton = new Button("Send");
        sendButton.setOnAction(e -> sendGlobalChat());

        HBox inputBar = new HBox(8, globalChatInput, sendButton);
        HBox.setHgrow(globalChatInput, Priority.ALWAYS);

        VBox chatPane = new VBox(10, new Label("Global Chat"), globalChatArea, inputBar);
        chatPane.setPadding(new Insets(10));
        VBox.setVgrow(globalChatArea, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setTop(new VBox(6, title, new Label("Select a user to challenge.")));
        BorderPane.setMargin(root.getTop(), new Insets(10));
        root.setLeft(usersPane);
        root.setCenter(chatPane);

        return new Scene(root, 900, 550);
    }

    private Scene buildGameScene() {
        gameStatusLabel = new Label("Game status");
        gameStatusLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");

        boardGrid = new GridPane();
        boardGrid.setHgap(1);
        boardGrid.setVgap(1);
        boardGrid.setStyle("-fx-background-color: #222;");

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Button square = new Button();
                square.setMinSize(64, 64);
                square.setMaxSize(64, 64);
                final int r = row;
                final int c = col;
                square.setOnAction(e -> handleBoardClick(r, c));
                boardButtons[row][col] = square;
                boardGrid.add(square, col, row);
            }
        }

        gameChatArea = new TextArea();
        gameChatArea.setEditable(false);
        gameChatArea.setWrapText(true);

        gameChatInput = new TextField();
        gameChatInput.setPromptText("Direct message your opponent");
        gameChatInput.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                sendGameDirectMessage();
            }
        });

        Button sendDmButton = new Button("Send DM");
        sendDmButton.setOnAction(e -> sendGameDirectMessage());

        Button backToLobby = new Button("Back to Lobby");
        backToLobby.setOnAction(e -> {
            opponent = null;
            myColorBlack = false;
            selectedRow = -1;
            selectedCol = -1;
            primaryStage.setScene(lobbyScene);
        });

        HBox dmBar = new HBox(8, gameChatInput, sendDmButton);
        HBox.setHgrow(gameChatInput, Priority.ALWAYS);

        VBox rightPane = new VBox(10,
            new Label("Game Chat"),
            gameChatArea,
            dmBar,
            backToLobby
        );
        rightPane.setPadding(new Insets(10));
        rightPane.setPrefWidth(320);
        VBox.setVgrow(gameChatArea, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setTop(gameStatusLabel);
        BorderPane.setMargin(gameStatusLabel, new Insets(0, 0, 10, 0));
        root.setCenter(boardGrid);
        root.setRight(rightPane);

        return new Scene(root, 1020, 620);
    }

    private void attemptLogin() {
        String host = hostField.getText().trim();
        String portText = portField.getText().trim();
        String desiredUser = usernameField.getText().trim();

        if (desiredUser.isEmpty()) {
            updateStatus("Username is required");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            updateStatus("Port must be a number");
            return;
        }

        boolean connected = clientConnection.connect(host, port);
        if (!connected) {
            return;
        }

        username = desiredUser;

        Message login = new Message(Message.Type.LOGIN, desiredUser, "Login request");
        clientConnection.send(login);
    }

    private void sendGlobalChat() {
        String text = globalChatInput.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        Message msg = new Message(Message.Type.GLOBAL_CHAT, username, text);
        clientConnection.send(msg);
        globalChatInput.clear();
    }

    private void sendChallenge() {
        String target = lobbyUsersList.getSelectionModel().getSelectedItem();
        if (target == null || target.equals(username)) {
            appendGlobalChat("[System] Select another online user to challenge.");
            return;
        }

        Message msg = new Message(Message.Type.CHALLENGE_REQUEST, username, "Challenge request");
        msg.recipients.add(target);
        clientConnection.send(msg);
    }

    private void sendGameDirectMessage() {
        String text = gameChatInput.getText().trim();
        if (text.isEmpty() || opponent == null) {
            return;
        }

        Message msg = new Message(Message.Type.PRIVATE_CHAT, username, text);
        msg.recipients.add(opponent);
        clientConnection.send(msg);
        gameChatInput.clear();
    }

    private void handleIncomingMessage(Message msg) {
        if (msg == null || msg.type == null) {
            return;
        }

        switch (msg.type) {
            case LOGIN_SUCCESS:
                loginStatusLabel.setText(msg.textContent);
                appendGlobalChat("[System] " + msg.textContent);
                primaryStage.setScene(lobbyScene);
                break;
            case LOGIN_FAIL:
                loginStatusLabel.setText(msg.textContent);
                appendGlobalChat("[System] " + msg.textContent);
                break;
            case CLIENT_LIST_UPDATE:
                onlineUsers.setAll(msg.onlineUsers == null ? new ArrayList<>() : msg.onlineUsers);
                break;
            case GLOBAL_CHAT:
                appendGlobalChat("[" + msg.sender + "] " + msg.textContent);
                break;
            case PRIVATE_CHAT:
                appendGameChat("[DM " + msg.sender + "] " + msg.textContent);
                break;
            case CHALLENGE_REQUEST:
                appendGlobalChat("[System] " + msg.textContent);
                handleChallengePrompt(msg.sender);
                break;
            case CHALLENGE_DECLINE:
                appendGlobalChat("[System] " + msg.textContent);
                break;
            case GAME_START:
                opponent = findOpponent(msg.recipients);
                myColorBlack = msg.textContent != null && msg.textContent.contains("you are BLACK");
                boardState = msg.boardState;
                gameStatusLabel.setText(msg.textContent + " | Turn: BLACK");
                selectedRow = -1;
                selectedCol = -1;
                redrawBoard();
                appendGameChat("[System] " + msg.textContent);
                primaryStage.setScene(gameScene);
                break;
            case GAME_STATE_UPDATE:
                if (msg.boardState != null) {
                    boardState = msg.boardState;
                    redrawBoard();
                }
                gameStatusLabel.setText("Game vs " + optionalOpponent() + " | Turn: " + (msg.isBlackTurn ? "BLACK" : "RED"));
                if (msg.textContent != null && !msg.textContent.isEmpty()) {
                    appendGameChat("[System] " + msg.textContent);
                }
                break;
            case GAME_OVER:
                if (msg.boardState != null) {
                    boardState = msg.boardState;
                    redrawBoard();
                }
                appendGameChat("[System] " + msg.textContent);
                gameStatusLabel.setText("Game Over");
                break;
            default:
                appendGlobalChat("[System] Unhandled message: " + msg.type);
                break;
        }
    }

    private void handleChallengePrompt(String challenger) {
        Message response;
        if (primaryStage.getScene() != lobbyScene) {
            response = new Message(Message.Type.CHALLENGE_DECLINE, username, "Busy");
            response.recipients.add(challenger);
            clientConnection.send(response);
            return;
        }

        if (challenger == null || challenger.isEmpty()) {
            return;
        }

        boolean accept = true;
        if (!lobbyUsersList.getSelectionModel().isSelected(lobbyUsersList.getItems().indexOf(challenger))) {
            appendGlobalChat("[System] Auto-accepting challenge from " + challenger + ".");
        }

        if (accept) {
            response = new Message(Message.Type.CHALLENGE_ACCEPT, username, "Accepted");
        } else {
            response = new Message(Message.Type.CHALLENGE_DECLINE, username, "Declined");
        }
        response.recipients.add(challenger);
        clientConnection.send(response);
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

    private String optionalOpponent() {
        return opponent == null ? "?" : opponent;
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
                appendGameChat("[System] Select one of your black pieces.");
                return;
            }
            if (!myColorBlack && piece > 0) {
                appendGameChat("[System] Select one of your red pieces.");
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
                String base = dark ? "#5f3f2f" : "#e6d3b3";

                if (row == selectedRow && col == selectedCol) {
                    base = "#cfa93c";
                }

                String text = pieceSymbol(boardState[row][col]);
                square.setText(text);
                square.setStyle("-fx-background-color: " + base + "; -fx-font-size: 22; -fx-font-weight: bold;");
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

    private void appendGlobalChat(String line) {
        globalChatArea.appendText(line + "\n");
    }

    private void appendGameChat(String line) {
        gameChatArea.appendText(line + "\n");
    }

    private void updateStatus(String status) {
        if (loginStatusLabel != null) {
            loginStatusLabel.setText(status);
        }
        if (globalChatArea != null) {
            appendGlobalChat("[Status] " + status);
        }
    }
}
