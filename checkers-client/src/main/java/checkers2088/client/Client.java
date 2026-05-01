package checkers2088.client;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.function.Consumer;

import checkers2088.shared.Message;

public class Client implements Closeable {
    public static final class ConnectResult {
        private final boolean success;
        private final String detail;
        private final Message responseMessage;

        public ConnectResult(boolean success, String detail, Message responseMessage) {
            this.success = success;
            this.detail = detail;
            this.responseMessage = responseMessage;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getDetail() {
            return detail;
        }

        public Message getResponseMessage() {
            return responseMessage;
        }
    }

    private final Consumer<Message> inboundHandler;
    private final Consumer<String> disconnectHandler;

    private Socket socketClient;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private volatile boolean running;
    private volatile boolean intentionalClose;
    private Thread readerThread;

    public Client(Consumer<Message> inboundHandler, Consumer<String> disconnectHandler) {
        this.inboundHandler = inboundHandler;
        this.disconnectHandler = disconnectHandler;
    }

    public synchronized boolean connect(String host, int port) {
        try {
            openSocket(host, port);
            return true;
        } catch (IOException exception) {
            closeQuietly();
            return false;
        }
    }

    public synchronized ConnectResult connect(String host, int port, String username) {
        closeQuietly();

        try {
            openSocket(host, port);

            Message login = new Message(Message.Type.LOGIN);
            login.setSender(username);
            send(login);

            Object inbound = in.readObject();
            if (!(inbound instanceof Message response)) {
                closeQuietly();
                return new ConnectResult(false, "The server returned an unexpected handshake.", null);
            }

            if (response.getType() == Message.Type.LOGIN_SUCCESS) {
                running = true;
                intentionalClose = false;
                readerThread = new Thread(this::readLoop, "checkers-2088-client-reader");
                readerThread.setDaemon(true);
                readerThread.start();
                return new ConnectResult(true, "Connected.", response);
            }

            closeQuietly();
            return new ConnectResult(false, response.getTextContent(), response);
        } catch (IOException exception) {
            closeQuietly();
            return new ConnectResult(false, "Unable to reach " + host + ":" + port + " (" + exception.getMessage() + ").", null);
        } catch (ClassNotFoundException exception) {
            closeQuietly();
            return new ConnectResult(false, "The server returned data the client could not read.", null);
        }
    }

    public void readLoop() {
        try {
            while (running) {
                Object inbound = in.readObject();
                if (inbound instanceof Message message) {
                    inboundHandler.accept(message);
                }
            }
        } catch (SocketException exception) {
            notifyDisconnectIfNeeded("Connection closed.");
        } catch (IOException exception) {
            notifyDisconnectIfNeeded("Connection lost: " + exception.getMessage());
        } catch (ClassNotFoundException exception) {
            notifyDisconnectIfNeeded("The server sent unreadable data.");
        } finally {
            closeQuietly();
        }
    }

    public synchronized void send(Message message) throws IOException {
        if (!running && message.getType() != Message.Type.LOGIN) {
            throw new IOException("The client is not connected.");
        }
        if (out == null) {
            throw new IOException("The output stream is not ready.");
        }

        out.writeObject(message);
        out.flush();
        out.reset();
    }

    public synchronized boolean isConnected() {
        return running && socketClient != null && socketClient.isConnected() && !socketClient.isClosed();
    }

    public synchronized void disconnect() {
        intentionalClose = true;
        running = false;
        closeQuietly();
    }

    @Override
    public void close() {
        disconnect();
    }

    private void openSocket(String host, int port) throws IOException {
        socketClient = new Socket();
        socketClient.connect(new InetSocketAddress(host, port), 5000);
        socketClient.setTcpNoDelay(true);
        out = new ObjectOutputStream(socketClient.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socketClient.getInputStream());
    }

    private synchronized void closeQuietly() {
        running = false;

        try {
            if (socketClient != null && !socketClient.isClosed()) {
                socketClient.close();
            }
        } catch (IOException ignored) {
        }

        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignored) {
        }

        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException ignored) {
        }

        socketClient = null;
        in = null;
        out = null;
        readerThread = null;
    }

    private void notifyDisconnectIfNeeded(String reason) {
        if (!intentionalClose) {
            disconnectHandler.accept(reason);
        }
    }
}
