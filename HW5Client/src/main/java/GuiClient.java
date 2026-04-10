import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
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

    private Scene loginScene;
    private Scene chatScene;

    private TextField ipField;
    private TextField portField;
    private TextField usernameField;
    private Label loginStatusLabel;

    private final ObservableList<String> onlineUsers = FXCollections.observableArrayList();
    private ListView<String> onlineUsersList;
    private TextArea chatLogArea;
    private TextField chatInputField;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        // Network callbacks are routed to JavaFX thread for safe UI updates.
        clientConnection = new Client(
            msg -> Platform.runLater(() -> handleIncomingMessage(msg)),
            status -> Platform.runLater(() -> updateStatus(status))
        );

        loginScene = buildLoginScene();
        chatScene = buildChatScene();

        stage.setOnCloseRequest(this::onClose);
        stage.setTitle("HW5 Chat Client");
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
        VBox root = new VBox(10);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Login");

        ipField = new TextField("127.0.0.1");
        ipField.setPromptText("Server IP");

        portField = new TextField("5555");
        portField.setPromptText("Port");

        usernameField = new TextField();
        usernameField.setPromptText("Username");

        Button connectButton = new Button("Connect");
        connectButton.setOnAction(e -> attemptLogin());

        loginStatusLabel = new Label("Enter IP, port, and username");

        root.getChildren().addAll(title, ipField, portField, usernameField, connectButton, loginStatusLabel);
        return new Scene(root, 420, 280);
    }

    private Scene buildChatScene() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        onlineUsersList = new ListView<>(onlineUsers);
        onlineUsersList.setPrefWidth(180);
        root.setLeft(onlineUsersList);

        chatLogArea = new TextArea();
        chatLogArea.setEditable(false);
        chatLogArea.setWrapText(true);
        root.setCenter(chatLogArea);

        chatInputField = new TextField();
        chatInputField.setPromptText("Type message");
        chatInputField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                sendGlobalMessage();
            }
        });

        Button sendGlobalButton = new Button("Send Global");
        sendGlobalButton.setOnAction(e -> sendGlobalMessage());

        Button sendPrivateButton = new Button("Send Private");
        sendPrivateButton.setOnAction(e -> sendPrivateMessage());

        HBox bottomBar = new HBox(8, chatInputField, sendGlobalButton, sendPrivateButton);
        HBox.setHgrow(chatInputField, Priority.ALWAYS);

        VBox centerWrapper = new VBox(8, chatLogArea, bottomBar);
        VBox.setVgrow(chatLogArea, Priority.ALWAYS);
        root.setCenter(centerWrapper);

        return new Scene(root, 760, 480);
    }

    private void attemptLogin() {
        String host = ipField.getText().trim();
        String portText = portField.getText().trim();
        String desiredUser = usernameField.getText().trim();

        if (desiredUser.isEmpty()) {
            updateStatus("Username is required");
            return;
        }

        int port = 5555;
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

        Message login = new Message(Message.Type.LOGIN, username, "Login request");
        clientConnection.send(login);
    }

    private void sendGlobalMessage() {
        String text = chatInputField.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        Message msg = new Message(Message.Type.GLOBAL_CHAT, username, text);
        clientConnection.send(msg);
        chatInputField.clear();
    }

    private void sendPrivateMessage() {
        String text = chatInputField.getText().trim();
        String selectedUser = onlineUsersList.getSelectionModel().getSelectedItem();

        if (text.isEmpty()) {
            return;
        }
        if (selectedUser == null || selectedUser.equals(username)) {
            appendChat("[System] Select another user for private chat.");
            return;
        }

        Message msg = new Message(Message.Type.PRIVATE_CHAT, username, text);
        msg.recipients.add(selectedUser);
        clientConnection.send(msg);
        chatInputField.clear();
    }

    private void handleIncomingMessage(Message msg) {
        if (msg == null || msg.type == null) {
            return;
        }

        switch (msg.type) {
            case LOGIN_SUCCESS:
                updateStatus(msg.textContent);
                primaryStage.setScene(chatScene);
                appendChat("[System] " + msg.textContent);
                break;
            case LOGIN_FAIL:
                updateStatus(msg.textContent);
                break;
            case CLIENT_LIST_UPDATE:
                if (msg.onlineUsers == null) {
                    onlineUsers.setAll(new ArrayList<>());
                } else {
                    onlineUsers.setAll(msg.onlineUsers);
                }
                break;
            case GLOBAL_CHAT:
                appendChat("[" + msg.sender + "] " + msg.textContent);
                break;
            case PRIVATE_CHAT:
                appendChat("[Private " + msg.sender + "] " + msg.textContent);
                break;
            default:
                break;
        }
    }

    private void updateStatus(String status) {
        if (loginStatusLabel != null) {
            loginStatusLabel.setText(status);
        }
    }

    private void appendChat(String line) {
        if (chatLogArea != null) {
            chatLogArea.appendText(line + "\n");
        }
    }
}
