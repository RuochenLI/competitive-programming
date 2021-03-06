package competitive.programming.gametheory.minimax;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import competitive.programming.common.Constants;
import competitive.programming.gametheory.IGame;
import competitive.programming.gametheory.IMove;
import competitive.programming.gametheory.IMoveGenerator;
import competitive.programming.timemanagement.TimeoutException;
import competitive.programming.timemanagement.Timer;

/**
 * @author Manwe
 *
 *         Minimax class allows to find the best move a player can do
 *         in a zero sum game considering the other player will be playing his best move at
 *         each iteration.
 *         It includes the alpha beta prunning optimisation in order to explore less branches.
 *         It also stores the current best "killer" move in order to explore the best branches first and enhance the pruning rate
 * @see <a href="https://en.wikipedia.org/wiki/Minimax">Minimax</a> and <a href="https://en.wikipedia.org/wiki/Alpha%E2%80%93beta_pruning">Alpha-beta pruning</a>
 * 
 * @param <M>
 *            The class that model a move in the game tree
 * @param <G>
 *            The class that model the Game state
 */
public class Minimax<M extends IMove<G>, G extends IGame> {

    private static class AlphaBetaPrunningException extends Exception {
        private static final long serialVersionUID = 4338636523317720681L;
    }

    private class MinMaxEvaluatedMove implements Comparable<MinMaxEvaluatedMove> {
        private final M move;
        private final double value;
        private final MinMaxEvaluatedMove bestSubMove;

        public MinMaxEvaluatedMove(M move, double value, MinMaxEvaluatedMove bestSubMove) {
            this.move = move;
            this.value = value;
            this.bestSubMove = bestSubMove;
        }

        @Override
        public int compareTo(MinMaxEvaluatedMove o) {
            if (value > o.value) {
                return 1;
            } else if (value < o.value) {
                return -1;
            }
            return 0;
        }

        public MinMaxEvaluatedMove getBestSubMove() {
            return bestSubMove;
        }

        public M getMove() {
            return move;
        }

        public double getValue() {
            return value;
        }

        @Override
        public String toString() {
            String str = "M[" + value + ",[";
            if (move != null) {
                str += move.toString() + ",";
            }
            MinMaxEvaluatedMove subMove = bestSubMove;
            while (subMove != null) {
                if (subMove.move != null) {
                    str += subMove.toString() + ",";
                }
                subMove = subMove.bestSubMove != null ? subMove.bestSubMove : null;
            }
            return str + "]";
        }
    }

    private int depthmax;
    private MinMaxEvaluatedMove killer;

    private final Timer timer;

    /**
     * Minimax constructor
     * 
     * @param timer
	 *            timer instance in order to cancel the search of the best move
	 *            if we are running out of time
     */
    public Minimax(Timer timer) {
        this.timer = timer;
    }

    private List<MinMaxEvaluatedMove> evaluateSubPossibilities(G game, IMoveGenerator<M, G> generator, int depth, double alpha, double beta, boolean player,
            boolean alphaBetaAtThisLevel, MinMaxEvaluatedMove previousAnalysisBest) throws AlphaBetaPrunningException, TimeoutException {
        final List<MinMaxEvaluatedMove> moves = new LinkedList<MinMaxEvaluatedMove>();

        List<M> orderedMoves;
        final List<M> generatedMoves = generator.generateMoves(game);

        // killer first
        if (previousAnalysisBest != null && generatedMoves.contains(previousAnalysisBest.getMove())) {
            orderedMoves = new ArrayList<>();
            final M killerMove = generatedMoves.remove(generatedMoves.indexOf(previousAnalysisBest.getMove()));
            orderedMoves.add(killerMove);
            orderedMoves.addAll(generatedMoves);
        } else {
            orderedMoves = generatedMoves;
        }

        for (final M move : orderedMoves) {
            timer.timeCheck();
            final G movedGame = move.execute(game);
            MinMaxEvaluatedMove child = null;
            try {
                final MinMaxEvaluatedMove bestSubChild = minimax(movedGame, generator, depth - 1, alpha, beta, !player, previousAnalysisBest == null ? null
                        : previousAnalysisBest.getBestSubMove());
                child = new MinMaxEvaluatedMove(move, bestSubChild.getValue(), bestSubChild);
            } catch (final AlphaBetaPrunningException e) {
                move.cancel(game);
            }
            if (child != null) {
                // Alpha beta prunning
                if (alphaBetaAtThisLevel) {
                    if (player) {
                        alpha = Math.max(alpha, child.getValue());
                        if (beta <= alpha) {
                            move.cancel(game);
                            throw new AlphaBetaPrunningException();
                        }
                    } else {
                        beta = Math.min(beta, child.getValue());
                        if (beta <= alpha) {
                            move.cancel(game);
                            throw new AlphaBetaPrunningException();
                        }
                    }
                }
                moves.add(child);
                move.cancel(game);
            }
        }
        return moves;
    }

    private MinMaxEvaluatedMove minimax(G game, IMoveGenerator<M, G> generator, int depth, double alpha, double beta, boolean player,
            MinMaxEvaluatedMove previousAnalysisBest) throws AlphaBetaPrunningException, TimeoutException {
        if (depth == 0) {
            return new MinMaxEvaluatedMove(null, scoreFromEvaluatedGame(game.evaluate(depth), game), null);// Evaluated game status
        }
        final List<MinMaxEvaluatedMove> moves = evaluateSubPossibilities(game, generator, depth, alpha, beta, player, true, previousAnalysisBest);
        if (moves.size() > 0) {
            Collections.sort(moves);
            if (depth == depthmax && Constants.TRACES) {
                System.err.println("Moves:" + moves);
            }
            return moves.get(player ? (moves.size() - 1) : 0);
        } else {
            return new MinMaxEvaluatedMove(null, scoreFromEvaluatedGame(game.evaluate(depth), game), null);// Real end game status
        }
    }

    /**
     * Search in the game tree the best move using minimax with alpha beta pruning
     * 
     * @param game
	 *            The current state of the game
	 * @param generator
	 *            The move generator that will generate all the possible move of
	 *            the playing player at each turn
	 * @param depthmax
	 *            the fixed depth up to which the game tree will be expanded
	 * @return the best move you can play considering the other player is selecting
	 *         the best move for him at each turn
	 * @throws TimeoutException
     */
    public M best(final G game, final IMoveGenerator<M, G> generator, int depthmax) throws TimeoutException {
        try {
            this.depthmax = depthmax;
            final MinMaxEvaluatedMove best = minimax(game, generator, depthmax, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                    game.currentPlayer() == 0, killer);
            killer = best;
            return best.getMove();
        } catch (final AlphaBetaPrunningException e) {
            // Should never happen
            throw new RuntimeException("evaluated move found with value not between + infinity and - infinity...");
        }
    }

    private double scoreFromEvaluatedGame(double[] scores, G game) {
        return scores[0] - scores[1];
    }
}
