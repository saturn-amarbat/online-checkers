package checkers2088.server;

import java.io.Serializable;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String jdbcUrl;

    public DatabaseHelper(Path databasePath) {
        this.jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
        initialize();
    }

    public synchronized void ensureUser(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR IGNORE INTO player_stats (username, wins, losses) VALUES (?, 0, 0)")) {
            statement.setString(1, username.trim());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to create a player record.", exception);
        }
    }

    public synchronized void recordWinLoss(String winner, String loser) {
        ensureUser(winner);
        ensureUser(loser);

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            if (winner != null && !winner.isBlank()) {
                updateCounter(connection, "UPDATE player_stats SET wins = wins + 1 WHERE username = ?", winner);
            }
            if (loser != null && !loser.isBlank()) {
                updateCounter(connection, "UPDATE player_stats SET losses = losses + 1 WHERE username = ?", loser);
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to record win/loss data.", exception);
        }
    }

    public synchronized String getStatsText(String username) {
        ensureUser(username);

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT wins, losses FROM player_stats WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return username + " | W " + resultSet.getInt("wins") + " | L " + resultSet.getInt("losses");
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to read player stats.", exception);
        }

        return username + " | W 0 | L 0";
    }

    public synchronized String getLeaderboardText() {
        List<String> lines = new ArrayList<>();
        lines.add("Leaderboard");

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT username, wins, losses FROM player_stats ORDER BY wins DESC, losses ASC, username ASC LIMIT 5");
             ResultSet resultSet = statement.executeQuery()) {
            int rank = 1;
            while (resultSet.next()) {
                lines.add(rank + ". " + resultSet.getString("username")
                        + "  W " + resultSet.getInt("wins")
                        + "  L " + resultSet.getInt("losses"));
                rank++;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to read leaderboard data.", exception);
        }

        if (lines.size() == 1) {
            lines.add("No completed matches yet.");
        }

        return String.join(System.lineSeparator(), lines);
    }

    private void initialize() {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_stats (
                        username TEXT PRIMARY KEY,
                        wins INTEGER NOT NULL DEFAULT 0,
                        losses INTEGER NOT NULL DEFAULT 0
                    )
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to initialize the stats database.", exception);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void updateCounter(Connection connection, String sql, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.executeUpdate();
        }
    }
}
