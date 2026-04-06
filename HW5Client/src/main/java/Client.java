import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.function.Consumer;

public class Client {

    private Socket socketClient;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Thread networkThread;
    private volatile boolean running;

    private final Consumer<Message> messageCallback;
    private final Consumer<String> statusCallback;

    Client(Consumer<Message> messageCallback, Consumer<String> statusCallback) {
        this.messageCallback = messageCallback;
        this.statusCallback = statusCallback;
    }

    public synchronized boolean connect(String host, int port) {
        if (running) {
            return true;
        }
        try {
            socketClient = new Socket(host, port);
            socketClient.setTcpNoDelay(true);
            out = new ObjectOutputStream(socketClient.getOutputStream());
            in = new ObjectInputStream(socketClient.getInputStream());

            running = true;
            networkThread = new Thread(this::readLoop, "Client-NetworkThread");
            networkThread.setDaemon(true);
            networkThread.start();
            statusCallback.accept("Connected to " + host + ":" + port);
            return true;
        } catch (Exception e) {
            statusCallback.accept("Connection failed: " + e.getMessage());
            close();
            return false;
        }
    }

    // This dedicated network thread receives server messages and forwards them to JavaFX.
    private void readLoop() {
        while (running) {
            try {
                Object incoming = in.readObject();
                if (incoming instanceof Message) {
                    messageCallback.accept((Message) incoming);
                }
            } catch (Exception e) {
                if (running) {
                    statusCallback.accept("Disconnected: " + e.getMessage());
                }
                close();
            }
        }
    }

    public synchronized void send(Message message) {
        if (!running || out == null || message == null) {
            return;
        }
        try {
            out.writeObject(message);
            out.flush();
        } catch (Exception e) {
            statusCallback.accept("Send failed: " + e.getMessage());
            close();
        }
    }

    public synchronized void close() {
        running = false;
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
            if (socketClient != null) {
                socketClient.close();
            }
        } catch (Exception ignored) {
        }
    }
}
