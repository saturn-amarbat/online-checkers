import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class DatabaseHelper {

    private static final String DB_URL = "jdbc:sqlite:checkers_stats.db";

    public DatabaseHelper() {
        initialize();
    }

    private void initialize() {
        String sql = "CREATE TABLE IF NOT EXISTS user_stats ("
            + "username TEXT PRIMARY KEY,"
            + "wins INTEGER NOT NULL DEFAULT 0,"
            + "losses INTEGER NOT NULL DEFAULT 0"
            + ")";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (Exception e) {
            System.err.println("Database initialization failed: " + e.getMessage());
        }
    }

    public void ensureUser(String username) {
        String sql = "INSERT OR IGNORE INTO user_stats(username, wins, losses) VALUES(?, 0, 0)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("ensureUser failed for " + username + ": " + e.getMessage());
        }
    }

    public void recordWinLoss(String winner, String loser) {
        String winSql = "UPDATE user_stats SET wins = wins + 1 WHERE username = ?";
        String lossSql = "UPDATE user_stats SET losses = losses + 1 WHERE username = ?";

        ensureUser(winner);
        ensureUser(loser);

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement winStmt = conn.prepareStatement(winSql);
             PreparedStatement lossStmt = conn.prepareStatement(lossSql)) {
            winStmt.setString(1, winner);
            winStmt.executeUpdate();

            lossStmt.setString(1, loser);
            lossStmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("recordWinLoss failed: " + e.getMessage());
        }
    }

    public String getStatsText(String username) {
        ensureUser(username);
        String sql = "SELECT wins, losses FROM user_stats WHERE username = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int wins = rs.getInt("wins");
                    int losses = rs.getInt("losses");
                    return "Stats for " + username + " -> Wins: " + wins + ", Losses: " + losses;
                }
            }
        } catch (Exception e) {
            System.err.println("getStatsText failed: " + e.getMessage());
        }

        return "Stats unavailable for " + username;
    }
}
