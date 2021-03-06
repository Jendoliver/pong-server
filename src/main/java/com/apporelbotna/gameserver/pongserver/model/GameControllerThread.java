package com.apporelbotna.gameserver.pongserver.model;

import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;

import com.apporelbotna.gameserver.pongserver.stubs.model.PongGame;
import com.apporelbotna.gameserver.pongserver.stubs.net.PlayerMovementMessage;

import lombok.extern.java.Log;

/**
 * This is the Server's Game Controller. It manages the connection with both players, modifying
 * the PongGame instance (thus affecting game logic) when a player intent is received and notifying
 * them constantly about any change in the model.
 *
 * When a GameControllerThread is started, two communication threads are started as well. These
 * threads will notify GameControllerThread when a player message is received via Observer pattern.
 *
 * When PongGame ends, GameControllerThread will access the PersistenceWS to update player points
 * and die just after that.
 *
 * @author Jendoliver
 *
 */
@Log
public class GameControllerThread implements Runnable, Observer
{
	private static final long MS_BETWEEN_PHYSICS_UPDATE = 5;

	private PlayerCommunicationChannel playerCommunicationChannel1;
	private PlayerCommunicationChannel playerCommunicationChannel2;
	private PongGame pongGame;
	// private GameDAO gameDAO; TODO add when WS Client is finished

	public GameControllerThread(PlayerConnection playerConn1, PlayerConnection playerConn2)
	{
		pongGame = new PongGame(playerConn1.getPlayer(), playerConn2.getPlayer());
		playerCommunicationChannel1 = new PlayerCommunicationChannel(playerConn1, this);
		playerCommunicationChannel2 = new PlayerCommunicationChannel(playerConn2, this);
	}

	@Override
	public void run()
	{
		new Thread(playerCommunicationChannel1).start();
		new Thread(playerCommunicationChannel2).start();

		boolean isFirstPlayerConnectionOk;
		boolean isSecondPlayerConnectionOk;

		while (!pongGame.hasGameEnded())
		{
			try
			{
				Thread.sleep(MS_BETWEEN_PHYSICS_UPDATE);
			}
			catch (InterruptedException e)
			{
				log.log(Level.FINER, Arrays.toString(e.getStackTrace()), e);
				Thread.currentThread().interrupt();
				break;
			}
			pongGame.updatePhysics();

			isFirstPlayerConnectionOk = playerCommunicationChannel1.sendGameInfo(
					pongGame.hasGameEnded(),
					pongGame.getBall(),
					pongGame.getPlayer2());
			pongGame.setBall(pongGame.getBall().mirrorPositionX());

			isSecondPlayerConnectionOk = playerCommunicationChannel2.sendGameInfo(
					pongGame.hasGameEnded(),
					pongGame.getBall(),
					pongGame.getPlayer1());
			pongGame.setBall(pongGame.getBall().mirrorPositionX());

			if(!isFirstPlayerConnectionOk || !isSecondPlayerConnectionOk)
				break;
		}

		log.log(Level.INFO, pongGame.getWinner().toString());

	}

	@Override
	public void update(Observable o, Object arg)
	{
		if (o.getClass().equals(PlayerCommunicationChannel.MessageReceivedEvent.class)) // On player
																																										// intent
		{
			PlayerMessage playerMessage = (PlayerMessage)arg;
			if (playerMessage.getMessage().equals(PlayerMovementMessage.GO_UP))
				playerMessage.getPlayer().setGoingUp(true);
			else if (playerMessage.getMessage().equals(PlayerMovementMessage.GO_DOWN))
				playerMessage.getPlayer().setGoingUp(false);
		}
	}
}
