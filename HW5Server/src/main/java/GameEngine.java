import java.util.ArrayList;
import java.util.List;

public class GameEngine {

    public static final int EMPTY = 0;
    public static final int BLACK_MAN = 1;
    public static final int BLACK_KING = 2;
    public static final int RED_MAN = -1;
    public static final int RED_KING = -2;

    public static class MoveResult {
        public final boolean valid;
        public final String reason;
        public final boolean captured;
        public final boolean mustContinueJump;
        public final boolean becameKing;

        public MoveResult(boolean valid, String reason, boolean captured, boolean mustContinueJump, boolean becameKing) {
            this.valid = valid;
            this.reason = reason;
            this.captured = captured;
            this.mustContinueJump = mustContinueJump;
            this.becameKing = becameKing;
        }

        public static MoveResult invalid(String reason) {
            return new MoveResult(false, reason, false, false, false);
        }
    }

    public static class GameState {
        public final String blackPlayer;
        public final String redPlayer;
        public final int[][] board;
        public boolean blackTurn;

        public GameState(String blackPlayer, String redPlayer) {
            this.blackPlayer = blackPlayer;
            this.redPlayer = redPlayer;
            this.blackTurn = true;
            this.board = initialBoard();
        }

        public String currentPlayer() {
            return blackTurn ? blackPlayer : redPlayer;
        }

        public String waitingPlayer() {
            return blackTurn ? redPlayer : blackPlayer;
        }
    }

    public static int[][] initialBoard() {
        int[][] board = new int[8][8];

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 8; col++) {
                if ((row + col) % 2 == 1) {
                    board[row][col] = BLACK_MAN;
                }
            }
        }

        for (int row = 5; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if ((row + col) % 2 == 1) {
                    board[row][col] = RED_MAN;
                }
            }
        }

        return board;
    }

    // Mandatory jump enforcement happens here: if any jump exists, non-jump moves are rejected.
    public static MoveResult validateAndApplyMove(GameState state, String player, int fromRow, int fromCol, int toRow, int toCol) {
        if (state == null) {
            return MoveResult.invalid("No active game");
        }
        if (!isInBounds(fromRow, fromCol) || !isInBounds(toRow, toCol)) {
            return MoveResult.invalid("Move out of bounds");
        }
        if (!player.equals(state.currentPlayer())) {
            return MoveResult.invalid("It is not your turn");
        }

        int piece = state.board[fromRow][fromCol];
        if (piece == EMPTY) {
            return MoveResult.invalid("No piece at selected square");
        }

        boolean movingBlack = isBlack(piece);
        if ((movingBlack && !player.equals(state.blackPlayer)) || (!movingBlack && !player.equals(state.redPlayer))) {
            return MoveResult.invalid("You can only move your own pieces");
        }
        if (state.board[toRow][toCol] != EMPTY) {
            return MoveResult.invalid("Destination square is not empty");
        }
        if ((toRow + toCol) % 2 == 0) {
            return MoveResult.invalid("Pieces move on dark squares only");
        }

        List<int[]> allCaptures = getAllCaptureMovesForCurrentPlayer(state);
        boolean jumpRequired = !allCaptures.isEmpty();

        int rowDelta = toRow - fromRow;
        int colDelta = toCol - fromCol;
        boolean isKing = isKing(piece);

        int absRow = Math.abs(rowDelta);
        int absCol = Math.abs(colDelta);
        boolean captureMove = absRow == 2 && absCol == 2;
        boolean simpleMove = absRow == 1 && absCol == 1;

        if (!isKing) {
            if (movingBlack && rowDelta <= 0) {
                return MoveResult.invalid("Black pieces move downward");
            }
            if (!movingBlack && rowDelta >= 0) {
                return MoveResult.invalid("Red pieces move upward");
            }
        }

        if (jumpRequired && !captureMove) {
            return MoveResult.invalid("A jump is available and must be taken");
        }

        if (!captureMove && !simpleMove) {
            return MoveResult.invalid("Invalid checker move");
        }

        boolean captured = false;
        if (captureMove) {
            int midRow = (fromRow + toRow) / 2;
            int midCol = (fromCol + toCol) / 2;
            int jumpedPiece = state.board[midRow][midCol];
            if (jumpedPiece == EMPTY || isBlack(jumpedPiece) == movingBlack) {
                return MoveResult.invalid("Jump must capture opponent piece");
            }
            state.board[midRow][midCol] = EMPTY;
            captured = true;
        }

        state.board[toRow][toCol] = piece;
        state.board[fromRow][fromCol] = EMPTY;

        boolean becameKing = maybePromote(state.board, toRow, toCol);

        boolean mustContinueJump = false;
        if (captured) {
            List<int[]> followUp = getCaptureMovesForPiece(state.board, toRow, toCol);
            mustContinueJump = !followUp.isEmpty();
        }

        if (!mustContinueJump) {
            state.blackTurn = !state.blackTurn;
        }

        return new MoveResult(true, "OK", captured, mustContinueJump, becameKing);
    }

    public static String determineWinner(GameState state) {
        int blackPieces = 0;
        int redPieces = 0;

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int p = state.board[row][col];
                if (isBlack(p)) {
                    blackPieces++;
                } else if (isRed(p)) {
                    redPieces++;
                }
            }
        }

        if (blackPieces == 0) {
            return state.redPlayer;
        }
        if (redPieces == 0) {
            return state.blackPlayer;
        }

        List<int[]> legalMoves = getAllLegalMovesForCurrentPlayer(state);
        if (legalMoves.isEmpty()) {
            return state.waitingPlayer();
        }

        return null;
    }

    public static int[][] copyBoard(int[][] source) {
        int[][] copy = new int[source.length][source[0].length];
        for (int i = 0; i < source.length; i++) {
            System.arraycopy(source[i], 0, copy[i], 0, source[i].length);
        }
        return copy;
    }

    private static List<int[]> getAllLegalMovesForCurrentPlayer(GameState state) {
        List<int[]> captures = getAllCaptureMovesForCurrentPlayer(state);
        if (!captures.isEmpty()) {
            return captures;
        }

        List<int[]> moves = new ArrayList<>();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int p = state.board[row][col];
                if (p == EMPTY) {
                    continue;
                }
                if (state.blackTurn && !isBlack(p)) {
                    continue;
                }
                if (!state.blackTurn && !isRed(p)) {
                    continue;
                }
                moves.addAll(getSimpleMovesForPiece(state.board, row, col));
            }
        }
        return moves;
    }

    private static List<int[]> getAllCaptureMovesForCurrentPlayer(GameState state) {
        List<int[]> captures = new ArrayList<>();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int p = state.board[row][col];
                if (p == EMPTY) {
                    continue;
                }
                if (state.blackTurn && !isBlack(p)) {
                    continue;
                }
                if (!state.blackTurn && !isRed(p)) {
                    continue;
                }
                captures.addAll(getCaptureMovesForPiece(state.board, row, col));
            }
        }
        return captures;
    }

    private static List<int[]> getSimpleMovesForPiece(int[][] board, int row, int col) {
        List<int[]> moves = new ArrayList<>();
        int piece = board[row][col];
        for (int[] dir : directionsForPiece(piece)) {
            int nr = row + dir[0];
            int nc = col + dir[1];
            if (isInBounds(nr, nc) && board[nr][nc] == EMPTY) {
                moves.add(new int[]{row, col, nr, nc});
            }
        }
        return moves;
    }

    private static List<int[]> getCaptureMovesForPiece(int[][] board, int row, int col) {
        List<int[]> moves = new ArrayList<>();
        int piece = board[row][col];
        boolean black = isBlack(piece);

        for (int[] dir : directionsForPiece(piece)) {
            int mr = row + dir[0];
            int mc = col + dir[1];
            int tr = row + (dir[0] * 2);
            int tc = col + (dir[1] * 2);

            if (!isInBounds(tr, tc) || !isInBounds(mr, mc)) {
                continue;
            }
            int middle = board[mr][mc];
            if (middle == EMPTY || isBlack(middle) == black) {
                continue;
            }
            if (board[tr][tc] == EMPTY) {
                moves.add(new int[]{row, col, tr, tc});
            }
        }

        return moves;
    }

    private static int[][] directionsForPiece(int piece) {
        if (piece == BLACK_MAN) {
            return new int[][]{{1, -1}, {1, 1}};
        }
        if (piece == RED_MAN) {
            return new int[][]{{-1, -1}, {-1, 1}};
        }
        return new int[][]{{1, -1}, {1, 1}, {-1, -1}, {-1, 1}};
    }

    private static boolean maybePromote(int[][] board, int row, int col) {
        int piece = board[row][col];
        if (piece == BLACK_MAN && row == 7) {
            board[row][col] = BLACK_KING;
            return true;
        }
        if (piece == RED_MAN && row == 0) {
            board[row][col] = RED_KING;
            return true;
        }
        return false;
    }

    private static boolean isInBounds(int row, int col) {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }

    private static boolean isBlack(int piece) {
        return piece == BLACK_MAN || piece == BLACK_KING;
    }

    private static boolean isRed(int piece) {
        return piece == RED_MAN || piece == RED_KING;
    }

    private static boolean isKing(int piece) {
        return piece == BLACK_KING || piece == RED_KING;
    }
}
