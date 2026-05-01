package checkers2088.server;

public final class ServerMain {
    private ServerMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        Server server = new Server(line -> System.out.println(line.toString()));
        server.startServer();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stopServer, "checkers-2088-server-shutdown"));

        System.out.println("Checkers 2088 server is live on port 5555.");
        System.out.println("Press Ctrl+C to stop the server.");

        // Keep the accept loop alive.
        while (true) {
            Thread.sleep(1000L);
        }
    }
}
