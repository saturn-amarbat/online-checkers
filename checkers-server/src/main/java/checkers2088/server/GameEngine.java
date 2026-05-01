package checkers2088.server;

import java.util.ArrayList;
import java.util.List;

public class GameEngine {
    public static final int BOARD_SIZE = 8;

    public static final int EMPTY = 0;
    public static final int RUBY_MAN = 1;
    public static final int RUBY_KING = 2;
    public static final int ONYX_MAN = -1;
    public static final int ONYX_KING = -2;

    public GameState createNewGame(String rubyPlayer, String onyxPlayer) {
        GameState state = new GameState(rubyPlayer, onyxPlayer);
        populateOpeningBoard(state.getBoardState());
        state.setBlackTurn(false);
        state.setStatusText("Ruby moves first.");
        return state;
    }

    public MoveResult validateAndApplyMove(GameState state, int fromRow, int fromCol, int toRow, int toCol) {
        if (state.isGameOver()) {
            return MoveResult.invalid("This match has already ended.");
        }
        if (!isInside(fromRow, fromCol) || !isInside(toRow, toCol)) {
            return MoveResult.invalid("Move coordinates are outside the board.");
        }

        List<int[]> legalMoves = getLegalMovesForCurrentTurn(state);
        int[] chosenMove = findMove(legalMoves, fromRow, fromCol, toRow, toCol);
        if (chosenMove == null) {
            return MoveResult.invalid("That move is not legal right now.");
        }

        int[][] board = state.getBoardState();
        int movingPiece = board[fromRow][fromCol];
        board[fromRow][fromCol] = EMPTY;
        board[toRow][toCol] = movingPiece;

        boolean captured = Math.abs(toRow - fromRow) == 2;
        if (captured) {
            int capturedRow = (fromRow + toRow) / 2;
            int capturedCol = (fromCol + toCol) / 2;
            board[capturedRow][capturedCol] = EMPTY;
        }

        boolean promoted = false;
        if (movingPiece == RUBY_MAN && toRow == 0) {
            board[toRow][toCol] = RUBY_KING;
            promoted = true;
        } else if (movingPiece == ONYX_MAN && toRow == BOARD_SIZE - 1) {
            board[toRow][toCol] = ONYX_KING;
            promoted = true;
        }

        if (captured) {
            List<int[]> chainMoves = getJumpsForPiece(state, toRow, toCol);
            if (!chainMoves.isEmpty()) {
                state.setForcedRow(toRow);
                state.setForcedCol(toCol);
                state.setStatusText("Jump again with the same piece.");
                return MoveResult.chain(chainMoves, toRow, toCol, promoted, state.isBlackTurn());
            }
        }

        state.setForcedRow(-1);
        state.setForcedCol(-1);
        state.setBlackTurn(!state.isBlackTurn());

        String winner = determineWinner(state);
        if (!winner.isBlank()) {
            state.setGameOver(true);
            state.setWinner(winner);
            state.setStatusText("Match complete.");
            return MoveResult.finished(winner, promoted, state.isBlackTurn(), getLegalMovesForCurrentTurn(state));
        }

        state.setStatusText(state.isBlackTurn() ? "Onyx to move." : "Ruby to move.");
        return MoveResult.success(promoted, state.isBlackTurn(), getLegalMovesForCurrentTurn(state));
    }

    public String determineWinner(GameState state) {
        int rubyPieces = 0;
        int onyxPieces = 0;
        for (int[] row : state.getBoardState()) {
            for (int piece : row) {
                if (piece > 0) {
                    rubyPieces++;
                } else if (piece < 0) {
                    onyxPieces++;
                }
            }
        }

        if (rubyPieces == 0 && onyxPieces == 0) {
            return "DRAW";
        }
        if (rubyPieces == 0) {
            return state.getOnyxPlayer();
        }
        if (onyxPieces == 0) {
            return state.getRubyPlayer();
        }

        boolean rememberedTurn = state.isBlackTurn();
        int rememberedForcedRow = state.getForcedRow();
        int rememberedForcedCol = state.getForcedCol();
        state.setForcedRow(-1);
        state.setForcedCol(-1);

        state.setBlackTurn(false);
        boolean rubyCanMove = !getLegalMovesForCurrentTurn(state).isEmpty();

        state.setBlackTurn(true);
        boolean onyxCanMove = !getLegalMovesForCurrentTurn(state).isEmpty();

        state.setBlackTurn(rememberedTurn);
        state.setForcedRow(rememberedForcedRow);
        state.setForcedCol(rememberedForcedCol);

        if (!rubyCanMove && !onyxCanMove) {
            return "DRAW";
        }
        if (!rubyCanMove) {
            return state.getOnyxPlayer();
        }
        if (!onyxCanMove) {
            return state.getRubyPlayer();
        }

        return "";
    }

    public ArrayList<int[]> getLegalMovesForCurrentTurn(GameState state) {
        ArrayList<int[]> jumps = new ArrayList<>();
        ArrayList<int[]> steps = new ArrayList<>();
        int[][] board = state.getBoardState();

        if (state.getForcedRow() >= 0 && state.getForcedCol() >= 0) {
            collectMovesForPiece(board, state.isBlackTurn(), state.getForcedRow(), state.getForcedCol(), jumps, steps);
            return jumps;
        }

        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                collectMovesForPiece(board, state.isBlackTurn(), row, col, jumps, steps);
            }
        }

        return jumps.isEmpty() ? steps : jumps;
    }

    public int[][] copyBoard(int[][] boardState) {
        int[][] copy = new int[BOARD_SIZE][BOARD_SIZE];
        for (int row = 0; row < BOARD_SIZE; row++) {
            System.arraycopy(boardState[row], 0, copy[row], 0, BOARD_SIZE);
        }
        return copy;
    }

    private void populateOpeningBoard(int[][] board) {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                board[row][col] = EMPTY;
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if ((row + col) % 2 == 1) {
                    board[row][col] = ONYX_MAN;
                }
            }
        }

        for (int row = BOARD_SIZE - 3; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if ((row + col) % 2 == 1) {
                    board[row][col] = RUBY_MAN;
                }
            }
        }
    }

    private List<int[]> getJumpsForPiece(GameState state, int row, int col) {
        ArrayList<int[]> jumps = new ArrayList<>();
        ArrayList<int[]> ignoredSteps = new ArrayList<>();
        collectMovesForPiece(state.getBoardState(), state.isBlackTurn(), row, col, jumps, ignoredSteps);
        return jumps;
    }

    private void collectMovesForPiece(int[][] board, boolean blackTurn, int row, int col,
                                      List<int[]> jumps, List<int[]> steps) {
        int piece = board[row][col];
        if (piece == EMPTY || isBlackPiece(piece) != blackTurn) {
            return;
        }

        for (int[] direction : directionsForPiece(piece)) {
            int nextRow = row + direction[0];
            int nextCol = col + direction[1];
            int jumpRow = row + (direction[0] * 2);
            int jumpCol = col + (direction[1] * 2);

            if (isInside(jumpRow, jumpCol)
                    && board[nextRow][nextCol] != EMPTY
                    && isBlackPiece(board[nextRow][nextCol]) != blackTurn
                    && board[jumpRow][jumpCol] == EMPTY) {
                jumps.add(new int[]{row, col, jumpRow, jumpCol});
            } else if (isInside(nextRow, nextCol) && board[nextRow][nextCol] == EMPTY) {
                steps.add(new int[]{row, col, nextRow, nextCol});
            }
        }
    }

    private int[] findMove(List<int[]> legalMoves, int fromRow, int fromCol, int toRow, int toCol) {
        for (int[] move : legalMoves) {
            if (move[0] == fromRow && move[1] == fromCol && move[2] == toRow && move[3] == toCol) {
                return move;
            }
        }
        return null;
    }

    private int[][] directionsForPiece(int piece) {
        if (piece == RUBY_KING || piece == ONYX_KING) {
            return new int[][]{
                    {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
            };
        }
        if (piece == RUBY_MAN) {
            return new int[][]{
                    {-1, -1}, {-1, 1}
            };
        }
        return new int[][]{
                {1, -1}, {1, 1}
        };
    }

    private boolean isInside(int row, int col) {
        return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
    }

    private boolean isBlackPiece(int piece) {
        return piece < 0;
    }

    public static final class GameState {
        private final String rubyPlayer;
        private final String onyxPlayer;
        private final int[][] boardState = new int[BOARD_SIZE][BOARD_SIZE];
        private boolean blackTurn;
        private int forcedRow = -1;
        private int forcedCol = -1;
        private boolean gameOver;
        private String winner = "";
        private String statusText = "";

        private GameState(String rubyPlayer, String onyxPlayer) {
            this.rubyPlayer = rubyPlayer;
            this.onyxPlayer = onyxPlayer;
        }

        public String getRubyPlayer() {
            return rubyPlayer;
        }

        public String getOnyxPlayer() {
            return onyxPlayer;
        }

        public int[][] getBoardState() {
            return boardState;
        }

        public boolean isBlackTurn() {
            return blackTurn;
        }

        public void setBlackTurn(boolean blackTurn) {
            this.blackTurn = blackTurn;
        }

        public int getForcedRow() {
            return forcedRow;
        }

        public void setForcedRow(int forcedRow) {
            this.forcedRow = forcedRow;
        }

        public int getForcedCol() {
            return forcedCol;
        }

        public void setForcedCol(int forcedCol) {
            this.forcedCol = forcedCol;
        }

        public boolean isGameOver() {
            return gameOver;
        }

        public void setGameOver(boolean gameOver) {
            this.gameOver = gameOver;
        }

        public String getWinner() {
            return winner;
        }

        public void setWinner(String winner) {
            this.winner = winner;
        }

        public String getStatusText() {
            return statusText;
        }

        public void setStatusText(String statusText) {
            this.statusText = statusText;
        }
    }

    public static final class MoveResult {
        private final boolean valid;
        private final String errorText;
        private final boolean jumpChainActive;
        private final boolean promotionOccurred;
        private final boolean gameOver;
        private final String winner;
        private final boolean blackTurnNext;
        private final ArrayList<int[]> legalMoves;
        private final int focusRow;
        private final int focusCol;

        private MoveResult(boolean valid, String errorText, boolean jumpChainActive,
                           boolean promotionOccurred, boolean gameOver, String winner,
                           boolean blackTurnNext, List<int[]> legalMoves, int focusRow, int focusCol) {
            this.valid = valid;
            this.errorText = errorText;
            this.jumpChainActive = jumpChainActive;
            this.promotionOccurred = promotionOccurred;
            this.gameOver = gameOver;
            this.winner = winner;
            this.blackTurnNext = blackTurnNext;
            this.legalMoves = new ArrayList<>();
            if (legalMoves != null) {
                for (int[] move : legalMoves) {
                    this.legalMoves.add(move.clone());
                }
            }
            this.focusRow = focusRow;
            this.focusCol = focusCol;
        }

        public static MoveResult invalid(String errorText) {
            return new MoveResult(false, errorText, false, false, false, "", false, List.of(), -1, -1);
        }

        public static MoveResult chain(List<int[]> legalMoves, int focusRow, int focusCol,
                                       boolean promotionOccurred, boolean blackTurnNext) {
            return new MoveResult(true, "", true, promotionOccurred, false, "", blackTurnNext, legalMoves, focusRow, focusCol);
        }

        public static MoveResult success(boolean promotionOccurred, boolean blackTurnNext, List<int[]> legalMoves) {
            return new MoveResult(true, "", false, promotionOccurred, false, "", blackTurnNext, legalMoves, -1, -1);
        }

        public static MoveResult finished(String winner, boolean promotionOccurred, boolean blackTurnNext, List<int[]> legalMoves) {
            return new MoveResult(true, "", false, promotionOccurred, true, winner, blackTurnNext, legalMoves, -1, -1);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorText() {
            return errorText;
        }

        public boolean isJumpChainActive() {
            return jumpChainActive;
        }

        public boolean isPromotionOccurred() {
            return promotionOccurred;
        }

        public boolean isGameOver() {
            return gameOver;
        }

        public String getWinner() {
            return winner;
        }

        public boolean isBlackTurnNext() {
            return blackTurnNext;
        }

        public ArrayList<int[]> getLegalMoves() {
            ArrayList<int[]> copy = new ArrayList<>();
            for (int[] move : legalMoves) {
                copy.add(move.clone());
            }
            return copy;
        }

        public int getFocusRow() {
            return focusRow;
        }

        public int getFocusCol() {
            return focusCol;
        }
    }
}
