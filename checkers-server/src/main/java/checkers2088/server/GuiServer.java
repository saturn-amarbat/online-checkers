package checkers2088.server;

import java.io.Serializable;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class GuiServer extends Application {
    private Server serverConnection;
    private ListView<String> listItems;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        listItems = new ListView<>();
        serverConnection = new Server(this::appendLogLine);
        serverConnection.startServer();

        stage.setTitle("Checkers 2088 Server");
        stage.setScene(createServerGui());
        stage.setOnCloseRequest(event -> serverConnection.stopServer());
        stage.show();
    }

    public Scene createServerGui() {
        Label title = new Label("Server Monitor");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        VBox header = new VBox(8, title, new Label("Live connection and match log"));
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));
        root.setTop(header);
        root.setCenter(listItems);
        root.setStyle("-fx-background-color: #f4f6f8;");

        return new Scene(root, 780, 520);
    }

    private void appendLogLine(Serializable line) {
        // Keep server logs on the FX thread.
        Platform.runLater(() -> listItems.getItems().add(line.toString()));
    }
}
