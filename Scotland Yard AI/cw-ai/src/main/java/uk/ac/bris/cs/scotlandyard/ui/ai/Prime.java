package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

/**
 * A class that is used to automate moves for MrX in a ScotlandYard game
 */
@SuppressWarnings("UnstableApiUsage")
public class Prime implements Ai {

	/**
	 * The game setup
	 */
	private GameSetup setup;

	//-------------------- Core Functions --------------------//

	/**
	 * @return the name of this AI
	 */
	@Nonnull
	@Override
	public String name() {
		return "Prime";
	}

	/**
	 * Picks the best move for MrX in the current game
	 *
	 * @param board the current board
	 * @param timeoutPair the timeout pair
	 * @return the best move for MrX
	 */
	@Nonnull
	@Override
	public Move pickMove(@Nonnull Board board, Pair<Long, TimeUnit> timeoutPair) {
		this.setup = board.getSetup();
		GameStateSubstitute gameStateSubstitute = new GameStateSubstitute(board);
		Integer destination;
		// The destinations already checked
		List<Integer> mrxDestinations = new ArrayList<>();
		// The best value for the maximizing player (MrX) so far, i.e., the largest value so far
		int alpha = Integer.MIN_VALUE;
		// The value of the current evaluation
		int eval;
		// The best move for MrX so far
		Move bestMove = gameStateSubstitute.moves.asList().get(0);

		for (Move mrxMove : gameStateSubstitute.moves) {
			destination = getDestination(mrxMove);
			if (!mrxDestinations.contains(destination)) {
				GameStateSubstitute helper = new GameStateSubstitute(gameStateSubstitute);
				Iterable<ScotlandYard.Ticket> requiredTickets = mrxMove.tickets();
				Integer secretCount = -1;
				for (ScotlandYard.Ticket ticket : requiredTickets) {
					if (secretCount.equals(-1) && ticket.equals(ScotlandYard.Ticket.SECRET)) {
						secretCount = 1;
					}
					else if (!secretCount.equals(-1) && ticket.equals(ScotlandYard.Ticket.SECRET)) {
						secretCount += 1;
					}
				}
				if (secretCount.equals(-1) ||
						gameStateSubstitute.mrX.hasAtLeast(ScotlandYard.Ticket.SECRET, secretCount)) {
					helper = helper.advance(mrxMove);
					mrxDestinations.add(destination);
				}
				else continue;
				// It is the detectives' turn, so we must call minimax to go down the game tree
				eval = minimax(helper, 2, alpha, Integer.MAX_VALUE, false, destination);
				// Checks if we found a game state where MrX wins
				if (eval == 2000000) return mrxMove;
				if (eval > alpha) {
					alpha = eval;
					bestMove = mrxMove;
				}
			}
		}
		return bestMove;
	}

	//-------------------- Auxiliary Functions --------------------//

	/**
	 * @param move the move
	 * @return the destination of the move
	 */
	private Integer getDestination (Move move) {
		SpecVisitor visitor = new SpecVisitor();
		move.visit(visitor);
		// Checks if the move is a single move
		if (visitor.destination != -1) return visitor.destination;
			// If it is not, then it is a double move
		else return visitor.destination2;
	}

	/**
	 * @param gameStateSubstitute the substitute for the current game state
	 * @return the location of all detectives
	 */
	private Set<Integer> getDetectivesLocations (GameStateSubstitute gameStateSubstitute){
		Set<Integer> detectivesLocation = new HashSet<>();
		ImmutableSet<Piece> piecesInTheGame = gameStateSubstitute.getPlayers();
		for (Piece piece : piecesInTheGame) {
			if (piece.isDetective()) {
				if (gameStateSubstitute.getDetectiveLocation((Piece.Detective) piece).isPresent()) {
					detectivesLocation.add(gameStateSubstitute.getDetectiveLocation((Piece.Detective) piece).get());
				}
			}
		}
		return detectivesLocation;
	}

	/**
	 * Lets us build a game tree
	 *
	 * @param gameStateSubstitute the substitute for the current game state
	 * @param depth the depth we want to search
	 * @param alpha the best value for the maximizing player (MrX) so far, i.e., the largest value
	 * @param beta the best value for the minimizing player (detectives) so far, i.e., the lowest value
	 * @param maximizingPlayer true if we are the maximizing player, false otherwise
	 * @param mrxLocation MrX's location
	 * @return the best possible score
	 */
	private Integer minimax (GameStateSubstitute gameStateSubstitute, Integer depth, Integer alpha, Integer beta,
						   Boolean maximizingPlayer, Integer mrxLocation) {
		// The value of the current evaluation
		int eval;

		// Checks if we are in a leaf
		if (depth == 0) {
			Set<Integer> detectivesLocation = getDetectivesLocations(gameStateSubstitute);
			// Returns the heuristic/static evaluation of the current game state
			return score(detectivesLocation, gameStateSubstitute.mrX);
		}
		// Checks if we are in a level of the game tree where we want to maximize the score
		int maxEval;
		if (maximizingPlayer) {
			/* The best value for the maximizing player so far in
			this level of this branch in the game tree (the largest value so far) */
			maxEval = Integer.MIN_VALUE;
			// The destinations already checked
			List<Integer> mrxDestinations = new ArrayList<>();
			/* Checks if MrX can move
			 If not, return a low score that will not be picked by MrX in the top level */
			if (gameStateSubstitute.moves.isEmpty()) return 0;
			for (Move mrxMove : gameStateSubstitute.moves) {
				mrxLocation = getDestination(mrxMove);
				if (!mrxDestinations.contains(mrxLocation)) {
					GameStateSubstitute helper = new GameStateSubstitute(gameStateSubstitute);
					Iterable<ScotlandYard.Ticket> requiredTickets = mrxMove.tickets();
					Integer secretCount = -1;
					for (ScotlandYard.Ticket ticket : requiredTickets) {
						if (secretCount.equals(-1) && ticket.equals(ScotlandYard.Ticket.SECRET)) {
							secretCount = 1;
						}
						else if (!secretCount.equals(-1) && ticket.equals(ScotlandYard.Ticket.SECRET)) {
							secretCount += 1;
						}
					}
					if (secretCount.equals(-1) ||
							gameStateSubstitute.mrX.hasAtLeast(ScotlandYard.Ticket.SECRET, secretCount)) {
						helper = helper.advance(mrxMove);
						mrxDestinations.add(mrxLocation);
					}
					else continue;
					if (helper.winner.contains(mrxMove.commencedBy())) return 2000000;
					else {
						// It is the detectives' turn, so we must call minimax to go down the game tree
						eval = minimax(helper, depth - 1, alpha, beta, false, mrxLocation);
						maxEval = Math.max(maxEval, eval);
						alpha = Math.max(alpha, eval);
						// Checks if the lowest value so far is less or equal than the largest
						if (beta <= alpha) return maxEval;
					}
				}
			}
		}
		else {
			/* The best value for the minimizing player so far in
			this level of this branch in the game tree (the lowest value so far) */
			maxEval = Integer.MAX_VALUE;
			List<List<Move.SingleMove>> allDetectivesMovesCombinations = getAllDetectivesMovesCombinations(gameStateSubstitute);
			/* Checks if the detectives can move
			 If not, return a low score that will not be picked by MrX in the top level */
			if (allDetectivesMovesCombinations.isEmpty()) return 0;
			// Each combination is a list of moves that we can execute before it is MrX's turn
			for (List<Move.SingleMove> combination : allDetectivesMovesCombinations) {
				GameStateSubstitute helper = new GameStateSubstitute(gameStateSubstitute);
				for (Move.SingleMove move : combination) {
					helper = helper.advance(move);
					if (helper.winner.contains(move.commencedBy())) return -2000000;
				}
				// It is MrX's turn, so we must call minimax to go down the game tree
				eval = minimax(helper, depth - 1, alpha, beta, true, mrxLocation);
				maxEval = Math.min(maxEval, eval);
				beta = Math.min(beta, eval);
				// Checks if the lowest value so far is less or equal than the largest
				if (beta <= alpha) return maxEval;
			}
		}
		return maxEval;
	}

	/**
	 * The largest the better for MrX; the lowest the better for the detectives
	 *
	 * @param detectivesLocation the location of the detectives
	 * @param mrx MrX player
	 * @return the score of the given "board"
	 */
	private Integer score (Set<Integer> detectivesLocation, Player mrx) {
		Integer mrxLocation = mrx.location();
		Integer numberOfDoubleTickets = mrx.tickets().get(ScotlandYard.Ticket.DOUBLE);
		// The distance from MrX to every detective
		List<Integer> distances = new ArrayList<>();
		Integer minimumDistance;
		// The number of nodes adjacent to MrX that are not occupied by detectives
		int freeNodes = 0;
		// The number of nodes adjacent to MrX's adjacent nodes that are not occupied by detectives
		int freeSecondaryNodes = 0;
		int score = 0, sumRemainingDistances = 0;

		for (Integer detectiveLocation : detectivesLocation) {
			distances.add( bfs(mrxLocation, detectiveLocation) );
		}
		minimumDistance = Collections.min(distances);
		if (numberOfDoubleTickets > 0) {
			for (int adjacentNode : this.setup.graph.adjacentNodes(mrxLocation)) {
				if (!detectivesLocation.contains(adjacentNode)) {
					freeNodes += 1;
					for (int adjacentNode2 : this.setup.graph.adjacentNodes(adjacentNode)) {
						if (!detectivesLocation.contains(adjacentNode2)) freeSecondaryNodes += 1;
					}
				}
			}
			if (minimumDistance == 1) {
				score -= 5000;
				score += freeNodes * 50;
				score += freeSecondaryNodes / 10;
			}
			else {
				score += minimumDistance * 50;
				score += freeNodes * 20;
				score += freeSecondaryNodes * 8;
			}
		}
		else {
			for (int adjacentNode : this.setup.graph.adjacentNodes(mrxLocation)) {
				if (!detectivesLocation.contains(adjacentNode)) freeNodes += 1;
			}
			if (minimumDistance == 1) {
				score -= 5000;
				score += freeNodes * 50;
			}
			else {
				score += minimumDistance * 50;
				score += freeNodes * 20;
			}
		}
		distances.remove(minimumDistance);
		for (Integer distance : distances) sumRemainingDistances += distance;
		score += (sumRemainingDistances / distances.size());
		return score;
	}

	/**
	 * @param source MrX's location
	 * @param destination detective's location
	 * @return the distance from the source to the destination; null if the destination is not in our graph
	 */
	public Integer bfs (Integer source, Integer destination) {
		LinkedList<Integer> queue = new LinkedList<>();
		// Distance[i] stores the distance between the node i and the source
		List<Integer> distance = Arrays.asList(new Integer[200]);
		// Visited[i] stores if the node i has been visited or not
		List<Boolean> visited = Arrays.asList(new Boolean[200]);

		if (source.equals(destination)) return 0;

		// Goes through all the nodes in our graph (1 to 199)
		for (int i = 1; i < 200; ++i) {
			visited.set(i, false);
			distance.set(i, Integer.MAX_VALUE);
		}

		visited.set(source, true);
		distance.set(source, 0);
		queue.add(source);

		while (queue.size() != 0) {
			int u = queue.remove();

			for (Integer adjacentNode : this.setup.graph.adjacentNodes(u)) {
				if (!visited.get(adjacentNode)) {
					visited.set(adjacentNode, true);
					/* Sets the distance between the adjacent node and the source to the result of
					adding 1 to the distance between the dequeued node and the source
					(because we are one "level" further in the graph) */
					distance.set(adjacentNode, distance.get(u) + 1);
					queue.add(adjacentNode);
					if (adjacentNode.equals(destination)) return distance.get(destination);
				}
			}
		}
		return null;
	}

	/**
	 * @param gameStateSubstitute the substitute for the current game state
	 * @return all the possible combinations of moves from the detectives in the current game state
	 */
	private List<List<Move.SingleMove>> getAllDetectivesMovesCombinations (GameStateSubstitute gameStateSubstitute) {
		boolean redIsOutOfMoves = false;
		boolean greenIsOutOfMoves = false;
		boolean blueIsOutOfMoves = false;
		boolean whiteIsOutOfMoves = false;
		boolean yellowIsOutOfMoves = false;
		// The corresponding player's destinations already checked
		List<Integer> redDestinations = new ArrayList<>();
		List<Integer> greenDestinations = new ArrayList<>();
		List<Integer> blueDestinations = new ArrayList<>();
		List<Integer> whiteDestinations = new ArrayList<>();
		List<Integer> yellowDestinations = new ArrayList<>();
		// The corresponding player's discarded destinations already checked
		List<Integer> redDestinationsDiscarded = new ArrayList<>();
		List<Integer> greenDestinationsDiscarded = new ArrayList<>();
		List<Integer> blueDestinationsDiscarded = new ArrayList<>();
		List<Integer> whiteDestinationsDiscarded = new ArrayList<>();
		List<Integer> yellowDestinationsDiscarded = new ArrayList<>();
		/* The corresponding player's available moves that will not get the player further away from MrX */
		List<Move.SingleMove> redMoves = new ArrayList<>();
		List<Move.SingleMove> greenMoves = new ArrayList<>();
		List<Move.SingleMove> blueMoves = new ArrayList<>();
		List<Move.SingleMove> whiteMoves = new ArrayList<>();
		List<Move.SingleMove> yellowMoves = new ArrayList<>();
		/* The corresponding player's available moves that will get the player further away from MrX */
		List<Move.SingleMove> redMovesDiscarded = new ArrayList<>();
		List<Move.SingleMove> greenMovesDiscarded = new ArrayList<>();
		List<Move.SingleMove> blueMovesDiscarded = new ArrayList<>();
		List<Move.SingleMove> whiteMovesDiscarded = new ArrayList<>();
		List<Move.SingleMove> yellowMovesDiscarded = new ArrayList<>();
		List<List<Move.SingleMove>> result = new ArrayList<>();
		Integer mrxLocation = gameStateSubstitute.mrX.location();
		Integer destination;

		for (Move detectiveMove : gameStateSubstitute.moves) {
			Piece.Detective detectiveMoving = (Piece.Detective) detectiveMove.commencedBy();
			Move.SingleMove single = (Move.SingleMove) detectiveMove;
			destination = single.destination;
			switch (detectiveMoving) {
				case RED:
					if (!redDestinations.contains(destination) && !redDestinationsDiscarded.contains(destination)) {
						if (detectiveMoving.webColour().equals("#f00")) {
							if (bfs(mrxLocation, destination) <= bfs(mrxLocation, detectiveMove.source())) {
								redDestinations.add(destination);
								redMoves.add(single);
								break;
							}
							redDestinationsDiscarded.add(destination);
							redMovesDiscarded.add(single);
						}
					}
					break;
				case GREEN:
					if (!greenDestinations.contains(destination) && !greenDestinationsDiscarded.contains(destination)) {
						if (detectiveMoving.webColour().equals("#0f0")) {
							if (bfs(mrxLocation, destination) <= bfs(mrxLocation, detectiveMove.source())) {
								greenDestinations.add(destination);
								greenMoves.add(single);
								break;
							}
							greenDestinationsDiscarded.add(destination);
							greenMovesDiscarded.add(single);
						}
					}
					break;
				case BLUE:
					if (!blueDestinations.contains(destination) && !blueDestinationsDiscarded.contains(destination)) {
						if (detectiveMoving.webColour().equals("#00f")) {
							if (bfs(mrxLocation, destination) <= bfs(mrxLocation, detectiveMove.source())) {
								blueDestinations.add(destination);
								blueMoves.add(single);
								break;
							}
							blueDestinationsDiscarded.add(destination);
							blueMovesDiscarded.add(single);
						}
					}
					break;
				case WHITE:
					if (!whiteDestinations.contains(destination) && !whiteDestinationsDiscarded.contains(destination)) {
						if (detectiveMoving.webColour().equals("#fff")) {
							if (bfs(mrxLocation, destination) <= bfs(mrxLocation, detectiveMove.source())) {
								whiteDestinations.add(destination);
								whiteMoves.add(single);
								break;
							}
							whiteDestinationsDiscarded.add(destination);
							whiteMovesDiscarded.add(single);
						}
					}
					break;
				case YELLOW:
					if (!yellowDestinations.contains(destination) && !yellowDestinationsDiscarded.contains(destination)) {
						if (detectiveMoving.webColour().equals("#ff0")) {
							if (bfs(mrxLocation, destination) <= bfs(mrxLocation, detectiveMove.source())) {
								yellowDestinations.add(destination);
								yellowMoves.add(single);
								break;
							}
							yellowDestinationsDiscarded.add(destination);
							yellowMovesDiscarded.add(single);
						}
					}
					break;
			}
		}

		/* A chain of if-else statements that helps us address problems that would come up further
		 ahead in the for loops; we avoid empty return values when we actually want to move some
		 detectives (even considering that these moves are presumably bad) */
		if (redMoves.isEmpty()) {
			if (redMovesDiscarded.isEmpty()) redIsOutOfMoves = true;
			else if (redMovesDiscarded.size() == 1) {
				redMoves.add(redMovesDiscarded.get(0));
				redDestinations.add(redDestinationsDiscarded.get(0));
			}
			else {
				redMoves.add(redMovesDiscarded.get(0));
				redDestinations.add(redDestinationsDiscarded.get(0));
				redMoves.add(redMovesDiscarded.get(1));
				redDestinations.add(redDestinationsDiscarded.get(1));
			}
		}
		else if (redMoves.size() == 1) {
			if (!redMovesDiscarded.isEmpty()) {
				redMoves.add(redMovesDiscarded.get(0));
				redDestinations.add(redDestinationsDiscarded.get(0));
			}
		}

		if (greenMoves.isEmpty()) {
			if (greenMovesDiscarded.isEmpty()) greenIsOutOfMoves = true;
			else if (greenMovesDiscarded.size() == 1) {
				greenMoves.add(greenMovesDiscarded.get(0));
				greenDestinations.add(greenDestinationsDiscarded.get(0));
				if (redDestinations.contains(greenDestinations.get(0))) greenIsOutOfMoves = true;
			}
			else {
				greenMoves.add(greenMovesDiscarded.get(0));
				greenDestinations.add(greenDestinationsDiscarded.get(0));
				greenMoves.add(greenMovesDiscarded.get(1));
				greenDestinations.add(greenDestinationsDiscarded.get(1));
			}
		}
		else if (greenMoves.size() == 1) {
			if (!greenMovesDiscarded.isEmpty()) {
				greenMoves.add(greenMovesDiscarded.get(0));
				greenDestinations.add(greenDestinationsDiscarded.get(0));
			}
			else if (redDestinations.contains(greenDestinations.get(0))) greenIsOutOfMoves = true;
		}

		if (blueMoves.isEmpty()) {
			if (blueMovesDiscarded.isEmpty()) blueIsOutOfMoves = true;
			else if (blueMovesDiscarded.size() == 1) {
				blueMoves.add(blueMovesDiscarded.get(0));
				blueDestinations.add(blueDestinationsDiscarded.get(0));
				if (redDestinations.contains(blueDestinations.get(0)) ||
					greenDestinations.contains(blueDestinations.get(0))) blueIsOutOfMoves = true;
			}
			else {
				blueMoves.add(blueMovesDiscarded.get(0));
				blueDestinations.add(blueDestinationsDiscarded.get(0));
				blueMoves.add(blueMovesDiscarded.get(1));
				blueDestinations.add(blueDestinationsDiscarded.get(1));
			}
		}
		else if (blueMoves.size() == 1) {
			if (!blueMovesDiscarded.isEmpty()) {
				blueMoves.add(blueMovesDiscarded.get(0));
				blueDestinations.add(blueDestinationsDiscarded.get(0));
			}
			else if (redDestinations.contains(blueDestinations.get(0)) ||
			         greenDestinations.contains(blueDestinations.get(0))) blueIsOutOfMoves = true;
		}

		if (whiteMoves.isEmpty()) {
			if (whiteMovesDiscarded.isEmpty()) whiteIsOutOfMoves = true;
			else if (whiteMovesDiscarded.size() == 1) {
				whiteMoves.add(whiteMovesDiscarded.get(0));
				whiteDestinations.add(whiteDestinationsDiscarded.get(0));
				if (redDestinations.contains(whiteDestinations.get(0)) ||
					greenDestinations.contains(whiteDestinations.get(0)) ||
					blueDestinations.contains(whiteDestinations.get(0))) whiteIsOutOfMoves = true;
			}
			else {
				whiteMoves.add(whiteMovesDiscarded.get(0));
				whiteDestinations.add(whiteDestinationsDiscarded.get(0));
				whiteMoves.add(whiteMovesDiscarded.get(1));
				whiteDestinations.add(whiteDestinationsDiscarded.get(1));
			}
		}
		else if (whiteMoves.size() == 1) {
			if (!whiteMovesDiscarded.isEmpty()) {
				whiteMoves.add(whiteMovesDiscarded.get(0));
				whiteDestinations.add(whiteDestinationsDiscarded.get(0));
			}
			else if (redDestinations.contains(whiteDestinations.get(0)) ||
					greenDestinations.contains(whiteDestinations.get(0)) ||
					blueDestinations.contains(whiteDestinations.get(0))) whiteIsOutOfMoves = true;
		}

		if (yellowMoves.isEmpty()) {
			if (yellowMovesDiscarded.isEmpty()) yellowIsOutOfMoves = true;
			else if (yellowMovesDiscarded.size() == 1) {
				yellowMoves.add(yellowMovesDiscarded.get(0));
				yellowDestinations.add(yellowDestinationsDiscarded.get(0));
				if (redDestinations.contains(yellowDestinations.get(0)) ||
					greenDestinations.contains(yellowDestinations.get(0)) ||
					blueDestinations.contains(yellowDestinations.get(0)) ||
					whiteDestinations.contains(yellowDestinations.get(0))) yellowIsOutOfMoves = true;
			}
			else {
				yellowMoves.add(yellowMovesDiscarded.get(0));
				yellowDestinations.add(yellowDestinationsDiscarded.get(0));
				yellowMoves.add(yellowMovesDiscarded.get(1));
				yellowDestinations.add(yellowDestinationsDiscarded.get(1));
			}
		}
		else if (yellowMoves.size() == 1) {
			if (!yellowMovesDiscarded.isEmpty()) {
				yellowMoves.add(yellowMovesDiscarded.get(0));
				yellowDestinations.add(yellowDestinationsDiscarded.get(0));
			}
			else if (redDestinations.contains(yellowDestinations.get(0)) ||
					greenDestinations.contains(yellowDestinations.get(0)) ||
					blueDestinations.contains(yellowDestinations.get(0)) ||
					whiteDestinations.contains(yellowDestinations.get(0))) yellowIsOutOfMoves = true;
		}

		/* A massive chain of if-else statements and for loops that allows us to create all the correct
		 combinations of available moves and address some possible problems:
		 If one or more of the detectives do not have any move, we still return combinations
		 using the rest of the detectives;
		 If we have moves with the same destination inside the same combination, we skip that combination */
		if (!redIsOutOfMoves) {
			if (!greenIsOutOfMoves) {
				if (!blueIsOutOfMoves) {
					if (!whiteIsOutOfMoves) {
						if (!yellowIsOutOfMoves) {
							for (Move.SingleMove redMove : redMoves) {
								for (Move.SingleMove greenMove : greenMoves) {
									if (redMove.destination == greenMove.destination) continue;
									for (Move.SingleMove blueMove : blueMoves) {
										if (redMove.destination == blueMove.destination ||
												greenMove.destination == blueMove.destination) continue;
										for (Move.SingleMove whiteMove : whiteMoves) {
											if (redMove.destination == whiteMove.destination ||
													greenMove.destination == whiteMove.destination ||
													blueMove.destination == whiteMove.destination) continue;
											for (Move.SingleMove yellowMove : yellowMoves) {
												if (redMove.destination == yellowMove.destination ||
														greenMove.destination == yellowMove.destination ||
														blueMove.destination == yellowMove.destination ||
														whiteMove.destination == yellowMove.destination) continue;
												List<Move.SingleMove> combination = new ArrayList<>();
												combination.add(redMove);
												combination.add(greenMove);
												combination.add(blueMove);
												combination.add(whiteMove);
												combination.add(yellowMove);
												result.add(combination);
											}
										}
									}
								}
							}
						}
						else {
							for (Move.SingleMove redMove : redMoves) {
								for (Move.SingleMove greenMove : greenMoves) {
									if (redMove.destination == greenMove.destination) continue;
									for (Move.SingleMove blueMove : blueMoves) {
										if (redMove.destination == blueMove.destination ||
												greenMove.destination == blueMove.destination) continue;
										for (Move.SingleMove whiteMove : whiteMoves) {
											if (redMove.destination == whiteMove.destination ||
													greenMove.destination == whiteMove.destination ||
													blueMove.destination == whiteMove.destination) continue;
											List<Move.SingleMove> combination = new ArrayList<>();
											combination.add(redMove);
											combination.add(greenMove);
											combination.add(blueMove);
											combination.add(whiteMove);
											result.add(combination);
										}
									}
								}
							}
						}
					}
					else if (!yellowIsOutOfMoves) {
						for (Move.SingleMove redMove : redMoves) {
							for (Move.SingleMove greenMove : greenMoves) {
								if (redMove.destination == greenMove.destination) continue;
								for (Move.SingleMove blueMove : blueMoves) {
									if (redMove.destination == blueMove.destination ||
											greenMove.destination == blueMove.destination) continue;
									for (Move.SingleMove yellowMove : yellowMoves) {
										if (redMove.destination == yellowMove.destination ||
												greenMove.destination == yellowMove.destination ||
												blueMove.destination == yellowMove.destination) continue;
										List<Move.SingleMove> combination = new ArrayList<>();
										combination.add(redMove);
										combination.add(greenMove);
										combination.add(blueMove);
										combination.add(yellowMove);
										result.add(combination);
									}
								}
							}
						}
					}
					else {
						for (Move.SingleMove redMove : redMoves) {
							for (Move.SingleMove greenMove : greenMoves) {
								if (redMove.destination == greenMove.destination) continue;
								for (Move.SingleMove blueMove : blueMoves) {
									if (redMove.destination == blueMove.destination ||
											greenMove.destination == blueMove.destination) continue;
									List<Move.SingleMove> combination = new ArrayList<>();
									combination.add(redMove);
									combination.add(greenMove);
									combination.add(blueMove);
									result.add(combination);
								}
							}
						}
					}
				}
				else if (!whiteIsOutOfMoves) {
					if (!yellowIsOutOfMoves) {
						for (Move.SingleMove redMove : redMoves) {
							for (Move.SingleMove greenMove : greenMoves) {
								if (redMove.destination == greenMove.destination) continue;
								for (Move.SingleMove whiteMove : whiteMoves) {
									if (redMove.destination == whiteMove.destination ||
											greenMove.destination == whiteMove.destination) continue;
									for (Move.SingleMove yellowMove : yellowMoves) {
										if (redMove.destination == yellowMove.destination ||
												greenMove.destination == yellowMove.destination ||
												whiteMove.destination == yellowMove.destination) continue;
										List<Move.SingleMove> combination = new ArrayList<>();
										combination.add(redMove);
										combination.add(greenMove);
										combination.add(whiteMove);
										combination.add(yellowMove);
										result.add(combination);
									}
								}
							}
						}
					}
					else {
						for (Move.SingleMove redMove : redMoves) {
							for (Move.SingleMove greenMove : greenMoves) {
								if (redMove.destination == greenMove.destination) continue;
								for (Move.SingleMove whiteMove : whiteMoves) {
									if (redMove.destination == whiteMove.destination ||
											greenMove.destination == whiteMove.destination) continue;
									List<Move.SingleMove> combination = new ArrayList<>();
									combination.add(redMove);
									combination.add(greenMove);
									combination.add(whiteMove);
									result.add(combination);
								}
							}
						}
					}
				}
				else if (!yellowIsOutOfMoves) {
					for (Move.SingleMove redMove : redMoves) {
						for (Move.SingleMove greenMove : greenMoves) {
							if (redMove.destination == greenMove.destination) continue;
							for (Move.SingleMove yellowMove : yellowMoves) {
								if (redMove.destination == yellowMove.destination ||
										greenMove.destination == yellowMove.destination) continue;
								List<Move.SingleMove> combination = new ArrayList<>();
								combination.add(redMove);
								combination.add(greenMove);
								combination.add(yellowMove);
								result.add(combination);
							}
						}
					}
				}
				else {
					for (Move.SingleMove redMove : redMoves) {
						for (Move.SingleMove greenMove : greenMoves) {
							if (redMove.destination == greenMove.destination) continue;
							List<Move.SingleMove> combination = new ArrayList<>();
							combination.add(redMove);
							combination.add(greenMove);
							result.add(combination);
						}
					}
				}
			}
			else if (!blueIsOutOfMoves) {
				if (!whiteIsOutOfMoves) {
					if (!yellowIsOutOfMoves) {
						for (Move.SingleMove redMove : redMoves) {
							for (Move.SingleMove blueMove : blueMoves) {
								if (redMove.destination == blueMove.destination) continue;
								for (Move.SingleMove whiteMove : whiteMoves) {
									if (redMove.destination == whiteMove.destination ||
											blueMove.destination == whiteMove.destination) continue;
									for (Move.SingleMove yellowMove : yellowMoves) {
										if (redMove.destination == yellowMove.destination ||
												blueMove.destination == yellowMove.destination ||
												whiteMove.destination == yellowMove.destination) continue;
										List<Move.SingleMove> combination = new ArrayList<>();
										combination.add(redMove);
										combination.add(blueMove);
										combination.add(whiteMove);
										combination.add(yellowMove);
										result.add(combination);
									}
								}
							}
						}
					}
					else {
						for (Move.SingleMove redMove : redMoves) {
							for (Move.SingleMove blueMove : blueMoves) {
								if (redMove.destination == blueMove.destination) continue;
								for (Move.SingleMove whiteMove : whiteMoves) {
									if (redMove.destination == whiteMove.destination ||
											blueMove.destination == whiteMove.destination) continue;
									List<Move.SingleMove> combination = new ArrayList<>();
									combination.add(redMove);
									combination.add(blueMove);
									combination.add(whiteMove);
									result.add(combination);
								}
							}
						}
					}
				}
				else if (!yellowIsOutOfMoves) {
					for (Move.SingleMove redMove : redMoves) {
						for (Move.SingleMove blueMove : blueMoves) {
							if (redMove.destination == blueMove.destination) continue;
							for (Move.SingleMove yellowMove : yellowMoves) {
								if (redMove.destination == yellowMove.destination ||
										blueMove.destination == yellowMove.destination) continue;
								List<Move.SingleMove> combination = new ArrayList<>();
								combination.add(redMove);
								combination.add(blueMove);
								combination.add(yellowMove);
								result.add(combination);
							}
						}
					}
				}
				else {
					for (Move.SingleMove redMove : redMoves) {
						for (Move.SingleMove blueMove : blueMoves) {
							if (redMove.destination == blueMove.destination) continue;
							List<Move.SingleMove> combination = new ArrayList<>();
							combination.add(redMove);
							combination.add(blueMove);
							result.add(combination);
						}
					}
				}
			}
			else if (!whiteIsOutOfMoves) {
				if (!yellowIsOutOfMoves) {
					for (Move.SingleMove redMove : redMoves) {
						for (Move.SingleMove whiteMove : whiteMoves) {
							if (redMove.destination == whiteMove.destination) continue;
							for (Move.SingleMove yellowMove : yellowMoves) {
								if (redMove.destination == yellowMove.destination ||
										whiteMove.destination == yellowMove.destination) continue;
								List<Move.SingleMove> combination = new ArrayList<>();
								combination.add(redMove);
								combination.add(whiteMove);
								combination.add(yellowMove);
								result.add(combination);
							}
						}
					}
				}
				else {
					for (Move.SingleMove redMove : redMoves) {
						for (Move.SingleMove whiteMove : whiteMoves) {
							if (redMove.destination == whiteMove.destination) continue;
							List<Move.SingleMove> combination = new ArrayList<>();
							combination.add(redMove);
							combination.add(whiteMove);
							result.add(combination);
						}
					}
				}
			}
			else if (!yellowIsOutOfMoves) {
				for (Move.SingleMove redMove : redMoves) {
					for (Move.SingleMove yellowMove : yellowMoves) {
						if (redMove.destination == yellowMove.destination) continue;
						List<Move.SingleMove> combination = new ArrayList<>();
						combination.add(redMove);
						combination.add(yellowMove);
						result.add(combination);
					}
				}
			}
			else {
				for (Move.SingleMove redMove : redMoves) {
					List<Move.SingleMove> combination = new ArrayList<>();
					combination.add(redMove);
					result.add(combination);
				}
			}
		}
		else if (!greenIsOutOfMoves) {
			if (!blueIsOutOfMoves) {
				if (!whiteIsOutOfMoves) {
					if (!yellowIsOutOfMoves) {
						for (Move.SingleMove greenMove : greenMoves) {
							for (Move.SingleMove blueMove : blueMoves) {
								if (greenMove.destination == blueMove.destination) continue;
								for (Move.SingleMove whiteMove : whiteMoves) {
									if (greenMove.destination == whiteMove.destination ||
											blueMove.destination == whiteMove.destination) continue;
									for (Move.SingleMove yellowMove : yellowMoves) {
										if (greenMove.destination == yellowMove.destination ||
												blueMove.destination == yellowMove.destination ||
												whiteMove.destination == yellowMove.destination) continue;
										List<Move.SingleMove> combination = new ArrayList<>();
										combination.add(greenMove);
										combination.add(blueMove);
										combination.add(whiteMove);
										combination.add(yellowMove);
										result.add(combination);
									}
								}
							}
						}
					}
					else {
						for (Move.SingleMove greenMove : greenMoves) {
							for (Move.SingleMove blueMove : blueMoves) {
								if (greenMove.destination == blueMove.destination) continue;
								for (Move.SingleMove whiteMove : whiteMoves) {
									if (greenMove.destination == whiteMove.destination ||
											blueMove.destination == whiteMove.destination) continue;
									List<Move.SingleMove> combination = new ArrayList<>();
									combination.add(greenMove);
									combination.add(blueMove);
									combination.add(whiteMove);
									result.add(combination);
								}
							}
						}
					}
				}
				else if (!yellowIsOutOfMoves) {
					for (Move.SingleMove greenMove : greenMoves) {
						for (Move.SingleMove blueMove : blueMoves) {
							if (greenMove.destination == blueMove.destination) continue;
							for (Move.SingleMove yellowMove : yellowMoves) {
								if (greenMove.destination == yellowMove.destination ||
										blueMove.destination == yellowMove.destination) continue;
								List<Move.SingleMove> combination = new ArrayList<>();
								combination.add(greenMove);
								combination.add(blueMove);
								combination.add(yellowMove);
								result.add(combination);
							}
						}
					}
				}
				else {
					for (Move.SingleMove greenMove : greenMoves) {
						for (Move.SingleMove blueMove : blueMoves) {
							if (greenMove.destination == blueMove.destination) continue;
							List<Move.SingleMove> combination = new ArrayList<>();
							combination.add(greenMove);
							combination.add(blueMove);
							result.add(combination);
						}
					}
				}
			}
			else if (!whiteIsOutOfMoves) {
				if (!yellowIsOutOfMoves) {
					for (Move.SingleMove greenMove : greenMoves) {
						for (Move.SingleMove whiteMove : whiteMoves) {
							if (greenMove.destination == whiteMove.destination) continue;
							for (Move.SingleMove yellowMove : yellowMoves) {
								if (greenMove.destination == yellowMove.destination ||
										whiteMove.destination == yellowMove.destination) continue;
								List<Move.SingleMove> combination = new ArrayList<>();
								combination.add(greenMove);
								combination.add(whiteMove);
								combination.add(yellowMove);
								result.add(combination);
							}
						}
					}
				}
				else {
					for (Move.SingleMove greenMove : greenMoves) {
						for (Move.SingleMove whiteMove : whiteMoves) {
							if (greenMove.destination == whiteMove.destination) continue;
							List<Move.SingleMove> combination = new ArrayList<>();
							combination.add(greenMove);
							combination.add(whiteMove);
							result.add(combination);
						}
					}
				}
			}
			else if (!yellowIsOutOfMoves) {
				for (Move.SingleMove greenMove : greenMoves) {
					for (Move.SingleMove yellowMove : yellowMoves) {
						if (greenMove.destination == yellowMove.destination) continue;
						List<Move.SingleMove> combination = new ArrayList<>();
						combination.add(greenMove);
						combination.add(yellowMove);
						result.add(combination);
					}
				}
			}
			else {
				for (Move.SingleMove greenMove : greenMoves) {
					List<Move.SingleMove> combination = new ArrayList<>();
					combination.add(greenMove);
					result.add(combination);
				}
			}
		}
		else if (!blueIsOutOfMoves) {
			if (!whiteIsOutOfMoves) {
				if (!yellowIsOutOfMoves) {
					for (Move.SingleMove blueMove : blueMoves) {
						for (Move.SingleMove whiteMove : whiteMoves) {
							if (blueMove.destination == whiteMove.destination) continue;
							for (Move.SingleMove yellowMove : yellowMoves) {
								if (blueMove.destination == yellowMove.destination ||
										whiteMove.destination == yellowMove.destination) continue;
								List<Move.SingleMove> combination = new ArrayList<>();
								combination.add(blueMove);
								combination.add(whiteMove);
								combination.add(yellowMove);
								result.add(combination);
							}
						}
					}
				}
				else {
					for (Move.SingleMove blueMove : blueMoves) {
						for (Move.SingleMove whiteMove : whiteMoves) {
							if (blueMove.destination == whiteMove.destination) continue;
							List<Move.SingleMove> combination = new ArrayList<>();
							combination.add(blueMove);
							combination.add(whiteMove);
							result.add(combination);
						}
					}
				}
			}
			else if (!yellowIsOutOfMoves) {
				for (Move.SingleMove blueMove : blueMoves) {
					for (Move.SingleMove yellowMove : yellowMoves) {
						if (blueMove.destination == yellowMove.destination) continue;
						List<Move.SingleMove> combination = new ArrayList<>();
						combination.add(blueMove);
						combination.add(yellowMove);
						result.add(combination);
					}
				}
			}
			else {
				for (Move.SingleMove blueMove : blueMoves) {
					List<Move.SingleMove> combination = new ArrayList<>();
					combination.add(blueMove);
					result.add(combination);
				}
			}
		}
		else if (!whiteIsOutOfMoves) {
			if (!yellowIsOutOfMoves) {
				for (Move.SingleMove whiteMove : whiteMoves) {
					for (Move.SingleMove yellowMove : yellowMoves) {
						if (whiteMove.destination == yellowMove.destination) continue;
						List<Move.SingleMove> combination = new ArrayList<>();
						combination.add(whiteMove);
						combination.add(yellowMove);
						result.add(combination);
					}
				}
			}
			else {
				for (Move.SingleMove whiteMove : whiteMoves) {
					List<Move.SingleMove> combination = new ArrayList<>();
					combination.add(whiteMove);
					result.add(combination);
				}
			}
		}
		else if (!yellowIsOutOfMoves) {
			for (Move.SingleMove yellowMove : yellowMoves) {
				List<Move.SingleMove> combination = new ArrayList<>();
				combination.add(yellowMove);
				result.add(combination);
			}
		}
		return result;
	}

	//-------------------- Auxiliary Classes --------------------//

	/**
	 * A substitute class for Board.GameState; we get more flexibility and speed by using it
	 */
	public class GameStateSubstitute {

		/**
		 * The pieces that can still move in the current round
		 */
		public Set<Piece> remaining;
		/**
		 * The current round
		 */
		public Integer roundCount;
		/**
		 * MrX player
		 */
		public Player mrX;
		/**
		 * Detective players
		 */
		public List<Player> detectives;
		/**
		 * The current available moves of the game
		 */
		public ImmutableSet<Move> moves;
		/**
		 * The winner(s) of this game, if any
		 */
		public ImmutableSet<Piece> winner;

		/**
		 * GameStateSubstitute constructor
		 *
		 * @param board the original game board
		 */
		public GameStateSubstitute (Board board) {
			List<Player> detectives = new ArrayList<>();
			Player mrX = null, red, green, blue, white, yellow;
			Piece mrxPiece = null, redPiece = null, greenPiece = null, bluePiece = null, whitePiece = null, yellowPiece = null;
			Integer mrxLocation = null;
			List<Map<String, Map<ScotlandYard.Ticket, Integer>>> allTickets = getTickets(board);
			ImmutableSet<Piece> piecesInTheGame = board.getPlayers();
			Set<Piece> remaining = new HashSet<>();
			ImmutableSet<Move> moves = board.getAvailableMoves();
			for (Move move : moves) {
				if (move.commencedBy().isMrX()) {
					mrxLocation = move.source();
					mrxPiece = move.commencedBy();
					remaining.add(mrxPiece);
					break;
				}
			}
			for (Piece piece : piecesInTheGame) {
				String colour = piece.webColour();
				switch (colour) {
					case "#f00":
						redPiece = piece;
						break;
					case "#0f0":
						greenPiece = piece;
						break;
					case "#00f":
						bluePiece = piece;
						break;
					case "#fff":
						whitePiece = piece;
						break;
					case "#ff0":
						yellowPiece = piece;
						break;
				}
			}

			for (Map<String, Map<ScotlandYard.Ticket, Integer>> tickets : allTickets) {
				if (tickets.containsKey("#000")) {
					mrX = new Player(mrxPiece, ImmutableMap.copyOf(tickets.get("#000")), mrxLocation);
				}
				else if (tickets.containsKey("#f00")) {
					if (board.getDetectiveLocation((Piece.Detective) redPiece).isPresent()){
						red = new Player(redPiece, ImmutableMap.copyOf(tickets.get("#f00")), (board.getDetectiveLocation((Piece.Detective) redPiece)).get());
						detectives.add(red);
					}
				}
				else if (tickets.containsKey("#0f0")) {
					if (board.getDetectiveLocation((Piece.Detective) greenPiece).isPresent()){
						green = new Player(greenPiece, ImmutableMap.copyOf(tickets.get("#0f0")), (board.getDetectiveLocation((Piece.Detective) greenPiece)).get());
						detectives.add(green);
					}
				}
				else if (tickets.containsKey("#00f")) {
					if (board.getDetectiveLocation((Piece.Detective) bluePiece).isPresent()){
						blue = new Player(bluePiece, ImmutableMap.copyOf(tickets.get("#00f")), (board.getDetectiveLocation((Piece.Detective) bluePiece)).get());
						detectives.add(blue);
					}
				}
				else if (tickets.containsKey("#fff")) {
					if (board.getDetectiveLocation((Piece.Detective) whitePiece).isPresent()){
						white = new Player(whitePiece, ImmutableMap.copyOf(tickets.get("#fff")), (board.getDetectiveLocation((Piece.Detective) whitePiece)).get());
						detectives.add(white);
					}
				}
				else if (tickets.containsKey("#ff0")) {
					if (board.getDetectiveLocation((Piece.Detective) yellowPiece).isPresent()){
						yellow = new Player(yellowPiece, ImmutableMap.copyOf(tickets.get("#ff0")), (board.getDetectiveLocation((Piece.Detective) yellowPiece)).get());
						detectives.add(yellow);
					}
				}
			}
			this.remaining = remaining;
			this.roundCount = board.getMrXTravelLog().size();
			this.mrX = mrX;
			this.detectives = detectives;
			this.winner = getWinner();
			this.moves = getAvailableMoves();
		}

		/**
		 * Copy constructor
		 *
		 * @param gameStateSubstitute the original game state substitute
		 */
		public GameStateSubstitute (GameStateSubstitute gameStateSubstitute) {
			List<Player> detectives = new ArrayList<>();
			Player mrX = gameStateSubstitute.mrX;
			Player mrX1 = new Player(mrX.piece(), mrX.tickets(), mrX.location());
			for (Player detective1 : gameStateSubstitute.detectives) {
				Player detective = new Player(detective1.piece(), detective1.tickets(), detective1.location());
				detectives.add(detective);
			}
			this.remaining = new HashSet<>(gameStateSubstitute.remaining);
			this.roundCount = gameStateSubstitute.roundCount;
			this.mrX = mrX1;
			this.detectives = detectives;
			this.winner = gameStateSubstitute.winner;
			this.moves = gameStateSubstitute.moves;
		}

		//-------------------- Core Functions --------------------//

		/**
		 * @return the pieces of all players in the game
		 */
		@Nonnull
		private ImmutableSet<Piece> getPlayers() {
			List<Piece> piecesList = new ArrayList<>();
			piecesList.add(mrX.piece());
			for (Player player : detectives) piecesList.add(player.piece());
			return ImmutableSet.copyOf(piecesList);
		}

		/**
		 * @param detective the detective
		 * @return the location of the given detective; empty if the detective is not part of the game
		 */
		@Nonnull
		private Optional<Integer> getDetectiveLocation(Piece.Detective detective) {
			Player player = getPlayerFromPiece(detective);
			if (player != null) return Optional.of(player.location());
			else return Optional.empty();
		}

		/**
		 * @return the winner of this game; empty if the game has no winners yet
		 */
		@Nonnull
		private ImmutableSet<Piece> getWinner() {
			int mrXTurn = 0;
			List<Piece> detectivePiecesList = new ArrayList<>();
			List<Integer> detectivesLocation = new ArrayList<>();
			// Holds the number of detectives that ran out of moves
			int noMovesCount = 0;
			// Holds the number of adjacent nodes to MrX that are occupied by detectives
			int blockCount = 0;
			if (this.remaining.contains(mrX.piece())) mrXTurn = 1;
			for (Player detective : this.detectives) {
				detectivePiecesList.add(detective.piece());
				detectivesLocation.add(detective.location());
			}
			for (int destination : Prime.this.setup.graph.adjacentNodes(mrX.location())) {
				if (detectivesLocation.contains(destination)) blockCount += 1;
			}
			for (Player detective : this.detectives) {
				if (detective.location() == this.mrX.location()) return ImmutableSet.copyOf(detectivePiecesList);
				if (makeSingleMoves(Prime.this.setup, this.detectives, detective, detective.location()).isEmpty()) {
					noMovesCount += 1;
				}
			}
			if (noMovesCount == this.detectives.size()) return ImmutableSet.of(this.mrX.piece());
			if ((makeSingleMoves(Prime.this.setup, this.detectives, this.mrX, this.mrX.location()).isEmpty() &&
					makeDoubleMoves(Prime.this.setup, this.detectives, this.mrX, this.mrX.location(), this.roundCount).isEmpty()) &&
					(blockCount != 0 || mrXTurn == 1)) return ImmutableSet.copyOf(detectivePiecesList);
			if (Prime.this.setup.rounds.size() == this.roundCount &&
					mrXTurn == 1) return ImmutableSet.of(this.mrX.piece());
			return ImmutableSet.of();
		}

		/**
		 * @return the current available moves of the game
		 */
		@Nonnull
		private ImmutableSet<Move> getAvailableMoves() {
			List<Move.SingleMove> singleMoves = new ArrayList<>();
			List<Move.DoubleMove> doubleMoves = new ArrayList<>();
			List<Move> moves = new ArrayList<>();
			if (remaining.contains(mrX.piece())) {
				singleMoves.addAll(makeSingleMoves(Prime.this.setup, detectives, mrX, mrX.location()));
				doubleMoves.addAll(makeDoubleMoves(Prime.this.setup, detectives, mrX, mrX.location(), this.roundCount));
			}
			else {
				for (Player player : detectives) {
					if (remaining.contains(player.piece())) {
						singleMoves.addAll(makeSingleMoves(Prime.this.setup, detectives, player, player.location()));
					}
				}
			}
			moves.addAll(singleMoves);
			moves.addAll(doubleMoves);
			return ImmutableSet.copyOf(moves);
		}

		/**
		 * Computes the next game state substitute given a move from {@link #getAvailableMoves()}
		 *
		 * @param move the move to make
		 * @return the same game state substitute but with changes that reflect the move made
		 * @throws IllegalArgumentException if the move was not a move from {@link #getAvailableMoves()}
		 */
		@Nonnull
		public GameStateSubstitute advance (Move move) {
			Player detective;
			Piece pieceMoving = move.commencedBy();
			Player playerMoving = getPlayerFromPiece(pieceMoving);
			Iterable<ScotlandYard.Ticket> requiredTickets = move.tickets();
			if (playerMoving == null) return this;
			if (!this.moves.contains(move)) throw new IllegalArgumentException("Illegal move: " + move);
			if (this.remaining.contains(pieceMoving)) {
				SpecVisitor visitor = new SpecVisitor();
				move.visit(visitor);
				if (playerMoving.isMrX()) {
					// Checks if the move is a single move
					if (visitor.destination != -1) {
						this.mrX = this.mrX.use(requiredTickets);
						this.mrX = this.mrX.at(visitor.destination);
						++this.roundCount;
					}
					// If it is not, then it must be a double move
					else {
						this.mrX = this.mrX.use(requiredTickets);
						this.mrX = this.mrX.at(visitor.destination2);
						/* Add 2 to the round count because we would need to add two separate entries
						to MrX's travel log, one for each move within the double move */
						this.roundCount += 2;
					}
					this.remaining.remove(pieceMoving);
					if (this.remaining.isEmpty()) {
						for (Player player : this.detectives) {
							this.remaining.add(player.piece());
						}
					}
				}
				// If it is not MrX, then it must be a detective
				else {
					detective = playerMoving.use(requiredTickets);
					detective = detective.at(visitor.destination);
					this.mrX = this.mrX.give(requiredTickets);
					this.detectives.set(this.detectives.indexOf(playerMoving), detective);
					this.remaining.remove(pieceMoving);
					if (this.remaining.isEmpty()) {
						this.remaining.add(this.mrX.piece());
					}
				}
			}
			Set<Piece> copyRemaining = new HashSet<>(this.remaining);
			for (Piece piece1 : copyRemaining) {
				Player player1 = getPlayerFromPiece(piece1);
				if (player1 == null) continue;
				/* Checks if the piece(s) that can still move in the current round have indeed
                available moves; if they do not, then they cannot play in this round */
				if (makeSingleMoves(Prime.this.setup, this.detectives, player1, player1.location()).isEmpty()) {
					this.remaining.remove(piece1);
				}
			}
			if (this.remaining.isEmpty()) this.remaining.add(this.mrX.piece());
			this.winner = getWinner();
			this.moves = getAvailableMoves();
			return this;
		}

		//-------------------- Auxiliary Functions --------------------//

		/**
		 * Gets the player from its piece (in order to have more methods and information available)
		 *
		 * @param piece the piece that we want to work on
		 * @return the player of the piece; null if the piece does not belong to the game
		 */
		private Player getPlayerFromPiece (Piece piece) {
			if (mrX.piece().equals(piece)) return mrX;
			for (Player player : detectives) {
				if (player.piece().equals(piece)) return player;
			}
			return null;
		}

		/**
		 * @param board the current game board
		 * @return a list where each entry is a map that maps the colour of a piece to its available tickets
		 */
		private List<Map<String, Map<ScotlandYard.Ticket, Integer>>> getTickets (Board board) {
			List<Map<String, Map<ScotlandYard.Ticket, Integer>>> tickets = new ArrayList<>();
			// Each map maps the corresponding colour of its piece to its available tickets
			Map<String, Map<ScotlandYard.Ticket, Integer>> mrxFinal = new HashMap<>();
			Map<String, Map<ScotlandYard.Ticket, Integer>> redFinal = new HashMap<>();
			Map<String, Map<ScotlandYard.Ticket, Integer>> greenFinal = new HashMap<>();
			Map<String, Map<ScotlandYard.Ticket, Integer>> blueFinal = new HashMap<>();
			Map<String, Map<ScotlandYard.Ticket, Integer>> whiteFinal = new HashMap<>();
			Map<String, Map<ScotlandYard.Ticket, Integer>> yellowFinal = new HashMap<>();
			/* Each map maps a type of ticket to the number of tickets of this type that the
			corresponding player has */
			Map<ScotlandYard.Ticket, Integer> mrxTickets = new HashMap<>();
			Map<ScotlandYard.Ticket, Integer> redTickets = new HashMap<>();
			Map<ScotlandYard.Ticket, Integer> greenTickets = new HashMap<>();
			Map<ScotlandYard.Ticket, Integer> blueTickets = new HashMap<>();
			Map<ScotlandYard.Ticket, Integer> whiteTickets = new HashMap<>();
			Map<ScotlandYard.Ticket, Integer> yellowTickets = new HashMap<>();
			ImmutableSet<Piece> piecesInTheGame = board.getPlayers();
			for (Piece piece : piecesInTheGame) {
				if (board.getPlayerTickets(piece).isPresent()) {
					Board.TicketBoard ticketBoard = board.getPlayerTickets(piece).get();
					switch (piece.webColour()) {
						case "#000":
							mrxTickets.put(ScotlandYard.Ticket.TAXI, ticketBoard.getCount(ScotlandYard.Ticket.TAXI));
							mrxTickets.put(ScotlandYard.Ticket.BUS, ticketBoard.getCount(ScotlandYard.Ticket.BUS));
							mrxTickets.put(ScotlandYard.Ticket.UNDERGROUND, ticketBoard.getCount(ScotlandYard.Ticket.UNDERGROUND));
							mrxTickets.put(ScotlandYard.Ticket.DOUBLE, ticketBoard.getCount(ScotlandYard.Ticket.DOUBLE));
							mrxTickets.put(ScotlandYard.Ticket.SECRET, ticketBoard.getCount(ScotlandYard.Ticket.SECRET));
							mrxFinal.put("#000", mrxTickets);
							tickets.add(mrxFinal);
							break;
						case "#f00":
							redTickets.put(ScotlandYard.Ticket.TAXI, ticketBoard.getCount(ScotlandYard.Ticket.TAXI));
							redTickets.put(ScotlandYard.Ticket.BUS, ticketBoard.getCount(ScotlandYard.Ticket.BUS));
							redTickets.put(ScotlandYard.Ticket.UNDERGROUND, ticketBoard.getCount(ScotlandYard.Ticket.UNDERGROUND));
							redFinal.put("#f00", redTickets);
							tickets.add(redFinal);
							break;
						case "#0f0":
							greenTickets.put(ScotlandYard.Ticket.TAXI, ticketBoard.getCount(ScotlandYard.Ticket.TAXI));
							greenTickets.put(ScotlandYard.Ticket.BUS, ticketBoard.getCount(ScotlandYard.Ticket.BUS));
							greenTickets.put(ScotlandYard.Ticket.UNDERGROUND, ticketBoard.getCount(ScotlandYard.Ticket.UNDERGROUND));
							greenFinal.put("#0f0", greenTickets);
							tickets.add(greenFinal);
							break;
						case "#00f":
							blueTickets.put(ScotlandYard.Ticket.TAXI, ticketBoard.getCount(ScotlandYard.Ticket.TAXI));
							blueTickets.put(ScotlandYard.Ticket.BUS, ticketBoard.getCount(ScotlandYard.Ticket.BUS));
							blueTickets.put(ScotlandYard.Ticket.UNDERGROUND, ticketBoard.getCount(ScotlandYard.Ticket.UNDERGROUND));
							blueFinal.put("#00f", blueTickets);
							tickets.add(blueFinal);
							break;
						case "#fff":
							whiteTickets.put(ScotlandYard.Ticket.TAXI, ticketBoard.getCount(ScotlandYard.Ticket.TAXI));
							whiteTickets.put(ScotlandYard.Ticket.BUS, ticketBoard.getCount(ScotlandYard.Ticket.BUS));
							whiteTickets.put(ScotlandYard.Ticket.UNDERGROUND, ticketBoard.getCount(ScotlandYard.Ticket.UNDERGROUND));
							whiteFinal.put("#fff", whiteTickets);
							tickets.add(whiteFinal);
							break;
						case "#ff0":
							yellowTickets.put(ScotlandYard.Ticket.TAXI, ticketBoard.getCount(ScotlandYard.Ticket.TAXI));
							yellowTickets.put(ScotlandYard.Ticket.BUS, ticketBoard.getCount(ScotlandYard.Ticket.BUS));
							yellowTickets.put(ScotlandYard.Ticket.UNDERGROUND, ticketBoard.getCount(ScotlandYard.Ticket.UNDERGROUND));
							yellowFinal.put("#ff0", yellowTickets);
							tickets.add(yellowFinal);
							break;
					}
				}
			}
			return tickets;
		}

		/**
		 * @param setup the game setup
		 * @param detectives the detective players
		 * @param player the player
		 * @param source the source of the player
		 * @return all the available single moves for the player
		 */
		private ImmutableSet<Move.SingleMove> makeSingleMoves(
				GameSetup setup,
				@Nonnull List<Player> detectives,
				Player player,
				int source) {

			List<Move.SingleMove> singleMoves = new ArrayList<>();
			Set<Integer> detectivesLocation = new HashSet<>();

			for (Player detective : detectives) { detectivesLocation.add(detective.location()); }
			for (int destination : setup.graph.adjacentNodes(source)) {
				if (detectivesLocation.contains(destination)) continue;
				for (ScotlandYard.Transport t : Objects.requireNonNull(setup.graph.edgeValueOrDefault(source, destination, ImmutableSet.of()))) {
					if (player.has(t.requiredTicket())) {
						Move.SingleMove singleMove = new Move.SingleMove(player.piece(), source, t.requiredTicket(), destination);
						singleMoves.add(singleMove);
					}
				}
				if (player.has(ScotlandYard.Ticket.SECRET)) {
					Move.SingleMove singleMove = new Move.SingleMove(player.piece(), source, ScotlandYard.Ticket.SECRET, destination);
					singleMoves.add(singleMove);
				}
			}
			return ImmutableSet.copyOf(singleMoves);
		}

		/**
		 * @param setup the game setup
		 * @param detectives the detective players
		 * @param player the player
		 * @param source the source of the player
		 * @param roundCount the round count
		 * @return all the available double moves for the player
		 */
		private ImmutableSet<Move.DoubleMove> makeDoubleMoves(
				GameSetup setup,
				List<Player> detectives,
				Player player,
				int source,
				Integer roundCount) {

			List<Move.DoubleMove> doubleMoves = new ArrayList<>();
			Set<Integer> detectivesLocation = new HashSet<>();

			for (Player detective : detectives) detectivesLocation.add(detective.location());
			if (player.has(ScotlandYard.Ticket.DOUBLE) && (setup.rounds.size() - roundCount >= 2)) {
				for (int destination1 : setup.graph.adjacentNodes(source)) {
					if (detectivesLocation.contains(destination1)) continue;
					for (ScotlandYard.Transport t1 : Objects.requireNonNull(setup.graph.edgeValueOrDefault(source, destination1, ImmutableSet.of()))) {
						if (player.has(t1.requiredTicket())) {
							for (int destination2 : setup.graph.adjacentNodes(destination1)) {
								if (detectivesLocation.contains(destination2)) continue;
								for (ScotlandYard.Transport t2 : Objects.requireNonNull(setup.graph.edgeValueOrDefault(destination1, destination2, ImmutableSet.of()))) {
									/* Checks if the required ticket for the first and for the
									second move within the double move are the same */
									if (t2.requiredTicket() == t1.requiredTicket()) {
										if (player.hasAtLeast(t2.requiredTicket(), 2)) {
											Move.DoubleMove doubleMove = new Move.DoubleMove(player.piece(), source, t2.requiredTicket(), destination1, t2.requiredTicket(), destination2);
											doubleMoves.add(doubleMove);
										}
									}
									else if (player.has(t2.requiredTicket())) {
										Move.DoubleMove doubleMove = new Move.DoubleMove(player.piece(), source, t1.requiredTicket(), destination1, t2.requiredTicket(), destination2);
										doubleMoves.add(doubleMove);
									}
								}
								if (player.has(ScotlandYard.Ticket.SECRET)) {
									Move.DoubleMove doubleMove = new Move.DoubleMove(player.piece(), source, t1.requiredTicket(), destination1, ScotlandYard.Ticket.SECRET, destination2);
									doubleMoves.add(doubleMove);
								}
							}
						}
					}
					if (player.has(ScotlandYard.Ticket.SECRET)) {
						for (int destination2 : setup.graph.adjacentNodes(destination1)) {
							if (detectivesLocation.contains(destination2)) continue;
							for (ScotlandYard.Transport t2 : Objects.requireNonNull(setup.graph.edgeValueOrDefault(destination1, destination2, ImmutableSet.of()))) {
								if (player.has(t2.requiredTicket())) {
									Move.DoubleMove doubleMove = new Move.DoubleMove(player.piece(), source, ScotlandYard.Ticket.SECRET, destination1, t2.requiredTicket(), destination2);
									doubleMoves.add(doubleMove);
								}
							}
							if (player.hasAtLeast(ScotlandYard.Ticket.SECRET, 2)) {
								Move.DoubleMove doubleMove = new Move.DoubleMove(player.piece(), source, ScotlandYard.Ticket.SECRET, destination1, ScotlandYard.Ticket.SECRET, destination2);
								doubleMoves.add(doubleMove);
							}
						}
					}
				}
			}
			return ImmutableSet.copyOf(doubleMoves);
		}
	}

	/**
	 * A visitor for use with the {@link Move#visit(Move.Visitor)} method
	 */
	private static class SpecVisitor implements Move.Visitor<Void> {
		/**
		 * Holds the destination of a single move
		 */
		public int destination = -1;
		/**
		 * Holds the intermediate destination of a double move
		 */
		public int destination1 = -1;
		/**
		 * Holds the final destination of a double move
		 */
		public int destination2;

		/**
		 * Extracts information from a single move
		 *
		 * @param move the single move we are using and trying to extract information from
		 * @return null; we only update the corresponding field inside this class
		 */
		@Override
		public Void visit (Move.SingleMove move) {
			this.destination = move.destination;
			return null;
		}

		/**
		 * Extracts information from a double move
		 *
		 * @param move the double move we are using and trying to extract information from
		 * @return null; we only update the corresponding fields inside this class
		 */
		@Override
		public Void visit (Move.DoubleMove move) {
			this.destination1 = move.destination1;
			this.destination2 = move.destination2;
			return null;
		}
	}
}
