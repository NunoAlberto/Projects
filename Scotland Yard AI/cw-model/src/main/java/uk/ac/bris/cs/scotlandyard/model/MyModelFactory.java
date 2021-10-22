package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;

import java.util.ArrayList;
import java.util.List;

/**
 * A factory used to create a game model of the ScotlandYard game
 */
public final class MyModelFactory implements Factory<Model>{

	/**
	 * Creates an instance of Model given the parameters required for a ScotlandYard game
	 *
	 * @param setup the game setup
	 * @param mrX MrX player
	 * @param detectives detective players
	 * @return an instance of Model
	 */
	@Nonnull @Override
	public Model build(GameSetup setup, Player mrX, ImmutableList<Player> detectives) {
		return new MyModel(setup, mrX, detectives);
	}

	/**
	 * A class that holds all the information and methods necessary to represent a ScotlandYard game model
	 */
	private static class MyModel implements Model, Model.Observer {

		/**
		 * The game setup
		 */
		GameSetup setup;
		/**
		 * MrX player
		 */
		Player mrX;
		/**
		 * Detective players
		 */
		ImmutableList<Player> detectives;
		/**
		 * The observers
		 */
		List<Observer> observers = new ArrayList<>();
		/**
		 * The game state
		 */
		Board.GameState gameState;

		/**
		 * MyModel constructor
		 *
		 * @param setup the game setup
		 * @param mrX MrX player
		 * @param detectives detective players
		 */
		public MyModel(GameSetup setup, Player mrX, ImmutableList<Player> detectives) {
			this.setup = setup;
			this.mrX = mrX;
			this.detectives = detectives;
			this.gameState = new MyGameStateFactory().build(setup, mrX, detectives);
		}

		/**
		 * @return the current game board
		 */
		@Nonnull
		public Board getCurrentBoard() { return gameState; }

		/**
		 * Registers an observer to the model
		 *
		 * @param observer the observer to register
		 * @throws NullPointerException if the observer is null
		 * @throws IllegalArgumentException if the observer is already registered
		 */
		public void registerObserver(@Nonnull Observer observer) {
			if (observer == null) throw new NullPointerException("Observer is null!");
			if (observers.contains(observer)) throw new IllegalArgumentException("Observer is already registered!");
			observers.add(observer);
		}

		/**
		 * Unregisters an observer from the model
		 *
		 * @param observer the observer to unregister
		 * @throws NullPointerException if the observer is null
		 * @throws IllegalArgumentException if the observer is not registered
		 */
		public void unregisterObserver(@Nonnull Observer observer) {
			if (observer == null) throw new NullPointerException("Observer is null!");
			if (!observers.contains(observer)) throw new IllegalArgumentException("Observer is not registered!");
			observers.remove(observer);
		}

		/**
		 * @return all currently registered observers of the model
		 */
		@Nonnull
		public ImmutableSet<Observer> getObservers() {
			return ImmutableSet.copyOf(observers);
		}

		/**
		 * Executes the move passed as argument and notifies all the observers
		 *
		 * @param move the move to make
		 */
		public void chooseMove(@Nonnull Move move) {
			try {
				gameState = gameState.advance(move);
				if (getCurrentBoard().getWinner().isEmpty()) {
					for (Observer observer : observers) observer.onModelChanged(gameState, Event.MOVE_MADE);
				}
				else {
					for (Observer observer : observers) {
						observer.onModelChanged(gameState, Event.GAME_OVER);
					}
				}
			} catch (IllegalArgumentException i) {
				for (Observer observer : observers) {
					observer.onModelChanged(gameState, Event.GAME_OVER);
				}
			}
		}
	}
}
