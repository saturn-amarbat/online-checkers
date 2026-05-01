package checkers2088.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GameEngineTest {
    @Test
    void forcedJumpRejectsSimpleMove() {
        GameEngine engine = new GameEngine();
        GameEngine.GameState state = engine.createNewGame("Ruby", "Onyx");
        clearBoard(state);
        state.getBoardState()[5][0] = GameEngine.RUBY_MAN;
        state.getBoardState()[4][1] = GameEngine.ONYX_MAN;

        GameEngine.MoveResult result = engine.validateAndApplyMove(state, 5, 0, 4, 1);

        assertFalse(result.isValid());
    }

    @Test
    void multiJumpKeepsTurnWithSamePiece() {
        GameEngine engine = new GameEngine();
        GameEngine.GameState state = engine.createNewGame("Ruby", "Onyx");
        clearBoard(state);
        state.getBoardState()[5][0] = GameEngine.RUBY_MAN;
        state.getBoardState()[4][1] = GameEngine.ONYX_MAN;
        state.getBoardState()[2][3] = GameEngine.ONYX_MAN;

        GameEngine.MoveResult result = engine.validateAndApplyMove(state, 5, 0, 3, 2);

        assertTrue(result.isValid());
        assertTrue(result.isJumpChainActive());
        assertFalse(result.isBlackTurnNext());
        assertEquals(3, result.getFocusRow());
        assertEquals(2, result.getFocusCol());
    }

    @Test
    void immediateKingingPromotesWhenPieceLandsOnBackRow() {
        GameEngine engine = new GameEngine();
        GameEngine.GameState state = engine.createNewGame("Ruby", "Onyx");
        clearBoard(state);
        state.getBoardState()[1][2] = GameEngine.RUBY_MAN;

        GameEngine.MoveResult result = engine.validateAndApplyMove(state, 1, 2, 0, 1);

        assertTrue(result.isValid());
        assertTrue(result.isPromotionOccurred());
        assertEquals(GameEngine.RUBY_KING, state.getBoardState()[0][1]);
    }

    private void clearBoard(GameEngine.GameState state) {
        for (int row = 0; row < GameEngine.BOARD_SIZE; row++) {
            for (int col = 0; col < GameEngine.BOARD_SIZE; col++) {
                state.getBoardState()[row][col] = GameEngine.EMPTY;
            }
        }
        state.setBlackTurn(false);
        state.setForcedRow(-1);
        state.setForcedCol(-1);
        state.setGameOver(false);
    }
}
