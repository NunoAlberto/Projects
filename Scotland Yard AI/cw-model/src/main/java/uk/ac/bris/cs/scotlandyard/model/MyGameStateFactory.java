package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.*;

import java.util.*;
import javax.annotation.Nonnull;

import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.Move.*;
import uk.ac.bris.cs.scotlandyard.model.Piece.*;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;
import java.util.List;

/**
 * A factory used to create a game state of the ScotlandYard game
 */
public final class MyGameStateFactory implements Factory<GameState> {

	/**
	 * Creates an instance of GameState given the parameters required for a ScotlandYard game
	 *
	 * @param setup the game setup
	 * @param mrX MrX player
	 * @param detectives detective players
	 * @return an instance of GameState
	 */
	@Nonnull @Override
	public GameState build(
			GameSetup setup,
			Player mrX,
			ImmutableList<Player> detectives) {
		return new MyGameState(setup, ImmutableSet.of(MrX.MRX), ImmutableList.of(), mrX, detectives);
	}

	/**
	 * A class that holds all the information and methods necessary to represent a ScotlandYard game state
	 */
	private static final class MyGameState implements GameState {

		/**
		 * The game setup
		 */
		private final GameSetup setup;
		/**
		 * The pieces that can still move in the current round
		 */
		private final ImmutableSet<Piece> remaining;
		/**
		 * MrX's travel log
		 */
		private final ImmutableList<LogEntry> log;
		/**
		 * MrX player
		 */
		private final Player mrX;
		/**
		 * Detective players
		 */
		private final List<Player> detectives;
		/**
		 * MrX and detective players (everyone in the game)
		 */
		private final ImmutableList<Player> everyone;
		/**
		 * The current available moves of the game
		 */
		private final ImmutableSet<Move> moves;
		/**
		 * The winner(s) of this game, if any
		 */
		public final ImmutableSet<Piece> winner;

		/**
		 * MyGameState constructor
		 *
		 * @param setup the game setup
		 * @param remaining the pieces that can still move in the current round
		 * @param log MrX's travel log
		 * @param mrX MrX player
		 * @param detectives detective players
		 * @throws NullPointerException if any of the arguments is null
		 * @throws IllegalArgumentException if any of the arguments is not valid
		 */
		@SuppressWarnings("UnstableApiUsage")
		private MyGameState(
				final GameSetup setup,
				final ImmutableSet<Piece> remaining,
				final ImmutableList<LogEntry> log,
				final Player mrX,
				final List<Player> detectives) {

			// A set that helps checking if we have two or more detectives in the same location
			Set<Integer> detectivesLocation = new HashSet<>();
			boolean duplicate;
			List<Player> players = new ArrayList<>();

			if (setup == null) throw new NullPointerException("Setup is null!");
			if (remaining == null) throw new NullPointerException("Remaining is null!");
			if (log == null) throw new NullPointerException("Log is null!");
			if (mrX == null) throw new NullPointerException("mrX is null!");
			if (detectives == null) throw new NullPointerException("Detectives is null!");
			if (setup.rounds.isEmpty()) throw new IllegalArgumentException("Rounds is empty!");
			if (setup.graph.edges().isEmpty()) throw new IllegalArgumentException("Graph is empty!");
			players.add(mrX);
			for (Player player : detectives) {
				if (player.has(Ticket.SECRET)) throw new IllegalArgumentException("Detective have secret ticket(s)!");
				if (player.has(Ticket.DOUBLE)) throw new IllegalArgumentException("Detective have double ticket(s)!");
				if (player.isMrX()) throw new IllegalArgumentException("More than one MrX!");
				/* The method add from the Set class returns true if the value we are adding
				is not in the Set, and false if it is already in it */
				duplicate = !detectivesLocation.add(player.location());
				if (duplicate) throw new IllegalArgumentException("Location overlap between detectives!");
				players.add(player);
			}
			this.setup = setup;
			this.remaining = remaining;
			this.log = log;
			this.mrX = mrX;
			this.detectives = detectives;
			this.everyone = ImmutableList.copyOf(players);
			this.winner = getWinner();
			this.moves = getAvailableMoves();
		}

		//-------------------- Core Functions --------------------//

		/**
		 * @return the current game setup
		 */
		@Override @Nonnull
		public GameSetup getSetup() { return setup; }

		/**
		 * @return the pieces of all players in the game
		 */
		@Override @Nonnull
		public ImmutableSet<Piece> getPlayers() {
			List<Piece> piecesList = new ArrayList<>();
			for (Player player : everyone) { piecesList.add(player.piece()); }
			return ImmutableSet.copyOf(piecesList);
		}

		/**
		 * @param detective the detective
		 * @return the location of the given detective; empty if the detective is not part of the game
		 */
		@Override @Nonnull
		public Optional<Integer> getDetectiveLocation(Piece.Detective detective) {
			Player player = getPlayerFromPiece(detective);
			if (player != null) return Optional.of(player.location());
			else return Optional.empty();
		}

		/**
		 * @param piece the player piece
		 * @return the ticket board of the given player; empty if the player is not part of the game
		 */
		@Override @Nonnull
		public Optional<Board.TicketBoard> getPlayerTickets (Piece piece) {
			Player player = getPlayerFromPiece(piece);
			if (player != null) return Optional.of(new TicketBoardImplementation(player));
			else return Optional.empty();
		}

		/**
		 * @return MrX's travel log as a immutableList of {@link LogEntry}s
		 */
		@Override @Nonnull
		public ImmutableList<LogEntry> getMrXTravelLog() { return log; }

		/**
		 * @return the winner of this game; empty if the game has no winners yet
		 * This is mutually exclusive with {@link #getAvailableMoves()}
		 */
		@Override @Nonnull
		public ImmutableSet<Piece> getWinner() {
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
			for (int destination : setup.graph.adjacentNodes(mrX.location())) {
				if (detectivesLocation.contains(destination)) blockCount += 1;
			}
			for (Player detective : this.detectives) {
				if (detective.location() == this.mrX.location()) return ImmutableSet.copyOf(detectivePiecesList);
				if (makeSingleMoves(this.setup, this.detectives, detective, detective.location()).isEmpty()) {
					noMovesCount += 1;
				}
			}
			if (noMovesCount == this.detectives.size()) return ImmutableSet.of(this.mrX.piece());
			if ((makeSingleMoves(this.setup, this.detectives, this.mrX, this.mrX.location()).isEmpty() &&
					makeDoubleMoves(this.setup, this.detectives, this.mrX, this.mrX.location(), this.log).isEmpty()) &&
					(blockCount != 0 || mrXTurn == 1)) return ImmutableSet.copyOf(detectivePiecesList);
			if (this.setup.rounds.size() == this.getMrXTravelLog().size() &&
					mrXTurn == 1) return ImmutableSet.of(this.mrX.piece());
			return ImmutableSet.of();
		}

		/**
		 * @return the current available moves of the game
		 * This is mutually exclusive with {@link #getWinner()}
		 */
		@Override @Nonnull
		public ImmutableSet<Move> getAvailableMoves() {
			if (!getWinner().isEmpty()) { return ImmutableSet.of(); }
			List<SingleMove> singleMoves = new ArrayList<>();
			List<DoubleMove> doubleMoves = new ArrayList<>();
			List<Move> moves = new ArrayList<>();
			for (Player player : everyone) {
				if (remaining.contains(player.piece())) {
					singleMoves.addAll(makeSingleMoves(setup, detectives, player, player.location()));
					doubleMoves.addAll(makeDoubleMoves(setup, detectives, player, player.location(), this.log));
				}
			}
			moves.addAll(singleMoves);
			moves.addAll(doubleMoves);
			return ImmutableSet.copyOf(moves);
		}

		/**
		 * Computes the next game state given a move from {@link #getAvailableMoves()}
		 *
		 * @param move the move to make
		 * @return the game state of which the given move has been made
		 * @throws IllegalArgumentException if the move was not a move from {@link #getAvailableMoves()}
		 */
		@Override @Nonnull
		public GameState advance (Move move) {
			List<Piece> oldRemaining = new ArrayList<>(this.remaining);
            List<Piece> newRemaining = new ArrayList<>();
            List<LogEntry> newLog = new ArrayList<>(this.log);
			Player newMrX = new Player(this.mrX.piece(), this.mrX.tickets(), this.mrX.location());
			List<Player> newDetectives = new ArrayList<>(this.detectives);
			Piece pieceMoving = move.commencedBy();
			Player playerMoving = getPlayerFromPiece(pieceMoving);
			Iterable<Ticket> requiredTickets = move.tickets();
			if (playerMoving == null) return this;
			if (!this.moves.contains(move)) throw new IllegalArgumentException("Illegal move: " + move);
			if (this.remaining.contains(pieceMoving)) {
			    SpecVisitor visitor = new SpecVisitor();
			    move.visit(visitor);
			    if (playerMoving.isMrX()) {
			        // Checks if the move is a single move
			        if (visitor.destination != -1) {
			            newMrX = newMrX.use(requiredTickets);
			            newMrX = newMrX.at(visitor.destination);
			            if (setup.rounds.get(this.log.size())) {
			                newLog.add(LogEntry.reveal(visitor.ticket, visitor.destination));
			            }
			            else newLog.add(LogEntry.hidden(visitor.ticket));
			        }
			        // If it is not a single move, then it must be a double one
			        else {
			            newMrX = newMrX.use(requiredTickets);
			            newMrX = newMrX.at(visitor.destination2);
			            if (setup.rounds.get(this.log.size())) {
			                newLog.add(LogEntry.reveal(visitor.ticket1, visitor.destination1));
			            }
			            else newLog.add(LogEntry.hidden(visitor.ticket1));
			            /* Checks once again if we are in a reveal round because we need
                        to add two separate entries, one for each move within the double move */
			            if (setup.rounds.get(newLog.size())) {
			                newLog.add(LogEntry.reveal(visitor.ticket2, visitor.destination2));
			            }
			            else newLog.add(LogEntry.hidden(visitor.ticket2));
			        }
			        oldRemaining.remove(pieceMoving);
			        if (oldRemaining.isEmpty()) {
			            for (Player player : this.detectives) {
			                oldRemaining.add(player.piece());
			            }
			        }
			    }
			    // If it is not MrX, then it must be a detective
			    else {
					Player newDetective;
					newDetective = playerMoving.use(requiredTickets);
					newDetective = newDetective.at(visitor.destination);
			        newMrX = newMrX.give(requiredTickets);
                    newDetectives.set(newDetectives.indexOf(playerMoving), newDetective);
			        oldRemaining.remove(pieceMoving);
			        if (oldRemaining.isEmpty()) oldRemaining.add(this.mrX.piece());
			    }
			}
			for (Piece piece : oldRemaining) {
				Player player = getPlayerFromPiece(piece);
				if (player == null) continue;
				/* Checks if the piece(s) that can still move in the current round have indeed
                available moves; if they do, then they can play in this round */
				if (!makeSingleMoves(this.setup, newDetectives, player, player.location()).isEmpty()) {
					newRemaining.add(piece);
				}
			}
			if (newRemaining.isEmpty()) newRemaining.add(newMrX.piece());
			return new MyGameState(this.setup, ImmutableSet.copyOf(newRemaining), ImmutableList.copyOf(newLog), newMrX, newDetectives);
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
		 * @param setup the game setup
		 * @param detectives the detective players
		 * @param player the player
		 * @param source the source of the player
		 * @return all the available single moves for the player
		 */
		public ImmutableSet<SingleMove> makeSingleMoves(
				GameSetup setup,
				@Nonnull List<Player> detectives,
				Player player,
				int source) {

			List<SingleMove> singleMoves = new ArrayList<>();
			Set<Integer> detectivesLocation = new HashSet<>();

			for (Player detective : detectives) { detectivesLocation.add(detective.location()); }
			for (int destination : setup.graph.adjacentNodes(source)) {
				if (detectivesLocation.contains(destination)) continue;
				for (Transport t : Objects.requireNonNull(setup.graph.edgeValueOrDefault(source, destination, ImmutableSet.of()))) {
					if (player.has(t.requiredTicket())) {
						SingleMove singleMove = new SingleMove(player.piece(), source, t.requiredTicket(), destination);
						singleMoves.add(singleMove);
					}
				}
				if (player.has(Ticket.SECRET)) {
					SingleMove singleMove = new SingleMove(player.piece(), source, Ticket.SECRET, destination);
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
		 * @param log MrX's travel log
		 * @return all the available double moves for the player
		 */
		public ImmutableSet<DoubleMove> makeDoubleMoves(
				GameSetup setup,
				List<Player> detectives,
				Player player,
				int source,
				ImmutableList<LogEntry> log) {

			List<DoubleMove> doubleMoves = new ArrayList<>();
			Set<Integer> detectivesLocation = new HashSet<>();

			for (Player detective : detectives) detectivesLocation.add(detective.location());
			if (player.has(Ticket.DOUBLE) && (setup.rounds.size() - log.size() >= 2)) {
				for (int destination1 : setup.graph.adjacentNodes(source)) {
					if (detectivesLocation.contains(destination1)) continue;
					for (Transport t1 : Objects.requireNonNull(setup.graph.edgeValueOrDefault(source, destination1, ImmutableSet.of()))) {
						if (player.has(t1.requiredTicket())) {
							for (int destination2 : setup.graph.adjacentNodes(destination1)) {
								if (detectivesLocation.contains(destination2)) continue;
								for (Transport t2 : Objects.requireNonNull(setup.graph.edgeValueOrDefault(destination1, destination2, ImmutableSet.of()))) {
									/* Checks if the required ticket for the first and for the
									second move within the double move are the same */
									if (t2.requiredTicket() == t1.requiredTicket()) {
										if (player.hasAtLeast(t2.requiredTicket(), 2)) {
											DoubleMove doubleMove = new DoubleMove(player.piece(), source, t2.requiredTicket(), destination1, t2.requiredTicket(), destination2);
											doubleMoves.add(doubleMove);
										}
									}
									else if (player.has(t2.requiredTicket())) {
										DoubleMove doubleMove = new DoubleMove(player.piece(), source, t1.requiredTicket(), destination1, t2.requiredTicket(), destination2);
										doubleMoves.add(doubleMove);
									}
								}
								if (player.has(Ticket.SECRET)) {
									DoubleMove doubleMove = new DoubleMove(player.piece(), source, t1.requiredTicket(), destination1, Ticket.SECRET, destination2);
									doubleMoves.add(doubleMove);
								}
							}
						}
					}
					if (player.has(Ticket.SECRET)) {
						for (int destination2 : setup.graph.adjacentNodes(destination1)) {
							if (detectivesLocation.contains(destination2)) continue;
							for (Transport t2 : Objects.requireNonNull(setup.graph.edgeValueOrDefault(destination1, destination2, ImmutableSet.of()))) {
								if (player.has(t2.requiredTicket())) {
									DoubleMove doubleMove = new DoubleMove(player.piece(), source, Ticket.SECRET, destination1, t2.requiredTicket(), destination2);
									doubleMoves.add(doubleMove);
								}
							}
							if (player.hasAtLeast(Ticket.SECRET, 2)) {
								DoubleMove doubleMove = new DoubleMove(player.piece(), source, Ticket.SECRET, destination1, Ticket.SECRET, destination2);
								doubleMoves.add(doubleMove);
							}
						}
					}
				}
			}
			return ImmutableSet.copyOf(doubleMoves);
		}
	}

	//-------------------- Auxiliary Classes --------------------//

	/**
	 * Represents the ScotlandYard ticket board for each player
	 */
	private static class TicketBoardImplementation implements Board.TicketBoard {
		/**
		 * The player associated with this ticket board
		 */
		private final Player player;

		/**
		 * TicketBoardImplementation constructor
		 *
		 * @param player the player we want to create a ticket board from
		 */
		public TicketBoardImplementation (Player player) { this.player = player; }

		/**
		 * @param ticket the ticket to check count for
		 * @return the number of tickets of the type specified that this player has
		 */
		@Override
		public int getCount(@Nonnull ScotlandYard.Ticket ticket) {
			return player.tickets().get(ticket);
		}
	}

	/**
	 * A visitor for use with the {@link Move#visit(Visitor)} method
	 */
	private static class SpecVisitor implements Visitor<Void> {
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
		 * Holds the ticket required to execute a single move
		 */
		public Ticket ticket;
		/**
		 * Holds the ticket required to execute the first move of a double move
		 */
		public Ticket ticket1;
		/**
		 * Holds the ticket required to execute the second move of a double move
		 */
		public Ticket ticket2;

		/**
		 * Extracts information from a single move
		 *
		 * @param move the single move we are using and trying to extract information from
		 * @return null; we only update the corresponding fields inside this class
		 */
		@Override
		public Void visit (SingleMove move) {
			this.destination = move.destination;
			this.ticket = move.ticket;
			return null;
		}

		/**
		 * Extracts information from a double move
		 *
		 * @param move the double move we are using and trying to extract information from
		 * @return null; we only update the corresponding fields inside this class
		 */
		@Override
		public Void visit (DoubleMove move) {
			this.destination1 = move.destination1;
			this.destination2 = move.destination2;
			this.ticket1 = move.ticket1;
			this.ticket2 = move.ticket2;
			return null;
		}
	}
}
