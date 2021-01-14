/*
BSD 2-Clause License

Copyright (c) 2021, Cyborger1
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.improvedtearsinterface;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameState;
import net.runelite.api.ObjectID;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Improved Tears Interface",
	description = "Improves the Tears of Guthix minigame interface",
	tags = {"Tears", "Guthix", "Tears of Guthix", "Improved", "Interface"}
)
public class ImprovedTearsInterfacePlugin extends Plugin
{
	private static final int VARBIT_TICKS_LEFT = 5099;
	private static final int VARBIT_TEARS_COLLECTED = 455;
	private static final int VARBIT_COLLECTING = 453;

	private static final int GAME_TICK_LENGTH_MS = 600;
	private static final int CLIENT_TICK_LENGTH_MS = 50;

	@Inject
	private Client client;

	@Inject
	private ImprovedTearsInterfaceConfig config;

	private int maxTicks = 0;
	private boolean minigameStarting = false;
	private boolean minigameEnding = false;
	private int prevTicksLeft = 0;
	private int tickOffset = 0;
	private int prevCollected = 0;
	private boolean prevCollecting = false;

	private boolean turnedOnDuringMinigame = false;

	private boolean inTearsMinigame = false;

	private static final Set<Integer> NO_TEARS_IDS = ImmutableSet.of(ObjectID.ABSENCE_OF_TEARS, ObjectID.ABSENCE_OF_TEARS_6667);
	private static final Set<Integer> BLUE_TEARS_IDS = ImmutableSet.of(ObjectID.BLUE_TEARS, ObjectID.BLUE_TEARS_6665);
	private static final Set<Integer> GREEN_TEARS_IDS = ImmutableSet.of(ObjectID.GREEN_TEARS, ObjectID.GREEN_TEARS_6666);

	@Provides
	ImprovedTearsInterfaceConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ImprovedTearsInterfaceConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		reset();
		if (isInTearsMinigameArea(false))
		{
			turnedOnDuringMinigame = true;
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		reset();
	}

	private void reset()
	{
		maxTicks = 0;
		minigameStarting = false;
		minigameEnding = false;
		prevTicksLeft = 0;
		tickOffset = 0;
		prevCollected = 0;
		prevCollecting = false;
		inTearsMinigame = false;
		turnedOnDuringMinigame = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			reset();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (turnedOnDuringMinigame)
		{
			if (!isInTearsMinigameArea())
			{
				reset();
			}
			return;
		}

		int newTicksLeft = client.getVarbitValue(VARBIT_TICKS_LEFT);
		if (newTicksLeft > 0 && isInTearsMinigameArea())
		{
			inTearsMinigame = true;

			// Going from 0 -> Starting amount
			if (prevTicksLeft == 0)
			{
				maxTicks = newTicksLeft;
				minigameStarting = true;
			}
			else if (minigameStarting)
			{
				if (isOnTearsStartingTile())
				{
					minigameStarting = false;
				}
			}
			else if (newTicksLeft == prevTicksLeft)
			{
				tickOffset++;
				newTicksLeft -= tickOffset;
			}
			else
			{
				tickOffset = 0;
			}
		}
		else if (prevTicksLeft > 0)
		{
			minigameEnding = true;
			prevTicksLeft = 0;
		}

		if (inTearsMinigame)
		{
			boolean doFlash = config.getFlashingText() && client.getTickCount() % 2 != 0;

			Widget timeLeftWidget = client.getWidget(276, 17);
			int newCollected = client.getVarbitValue(VARBIT_TEARS_COLLECTED);
			boolean newCollecting = client.getVarbitValue(VARBIT_COLLECTING) == 1;

			if (timeLeftWidget != null)
			{
				timeLeftWidget.setText("Time Left: " + newTicksLeft + " / " + maxTicks);
				Widget waterTextWidget = client.getWidget(276, 16);
				if (waterTextWidget != null)
				{
					if (newCollecting)
					{
						TearType tear = getAdjacentTearType();
						switch (tear)
						{
							case NONE:
								waterTextWidget.setText("Not Collecting");
								waterTextWidget.setTextColor(doFlash ? 0xFF9900 : 0xFF0000);
								break;
							case BLUE:
								waterTextWidget.setText("<col=00ff00>Collecting</col> Blue <col=00ff00>Tears</col>");
								waterTextWidget.setTextColor(doFlash ? 0x00CCFF : 0x00FFFF);
								break;
							case GREEN:
								waterTextWidget.setText("<col=ff0000>Collecting</col> Green <col=ff0000>Tears</col>");
								waterTextWidget.setTextColor(doFlash ? 0x00CC00 : 0x00FF00);
								break;
						}
					}
					else if (minigameStarting)
					{
						waterTextWidget.setText("Get Ready!");
						waterTextWidget.setTextColor(doFlash ? 0x00FF00 : 0xFFFF00);
					}
					else if (minigameEnding)
					{
						waterTextWidget.setText("Time Up!");
						waterTextWidget.setTextColor(doFlash ? 0xFF0000 : 0xFFFF00);
					}
					else
					{
						waterTextWidget.setText("Not Collecting");
						waterTextWidget.setTextColor(0xFFFF00);
					}
				}
			}

			if (minigameEnding && isOnTearsEndingTile())
			{
				reset();
			}
			else
			{
				/* DEBUG */
				System.out.println();
				System.out.println("current tick: " + client.getTickCount());
				System.out.println("ticks: " + prevTicksLeft + " -> " + newTicksLeft);
				System.out.println("coll: " + prevCollected + " -> " + newCollected);
				System.out.println("collb: " + prevCollecting + " -> " + newCollecting);
				System.out.println("offset: " + tickOffset);

				prevTicksLeft = newTicksLeft;
				prevCollected = newCollected;
				prevCollecting = newCollecting;
			}
		}
	}

	private enum TearType
	{
		NONE,
		GREEN,
		BLUE
	}

	private boolean isInTearsMinigameArea()
	{
		return isInTearsMinigameArea(true);
	}

	private boolean isInTearsMinigameArea(boolean includeExterior)
	{
		Player lp = client.getLocalPlayer();
		if (lp != null)
		{
			WorldPoint wp = lp.getWorldLocation();
			return (wp != null && wp.getPlane() == 2
				&& wp.getX() >= (includeExterior ? 3251 : 3252)
				&& wp.getX() <= 3260 && wp.getY() >= 9515 && wp.getY() <= 9519);
		}
		return false;
	}

	private boolean isOnTearsStartingTile()
	{
		Player lp = client.getLocalPlayer();
		if (lp != null)
		{
			WorldPoint wp = lp.getWorldLocation();
			return (wp != null && wp.getPlane() == 2
				&& wp.getX() == 3257 && wp.getY() == 9517);
		}
		return false;
	}

	private boolean isOnTearsEndingTile()
	{
		Player lp = client.getLocalPlayer();
		if (lp != null)
		{
			WorldPoint wp = lp.getWorldLocation();
			return (wp != null && wp.getPlane() == 2
				&& wp.getX() == 3251 && wp.getY() == 9516);
		}
		return false;
	}

	private TearType getAdjacentTearType()
	{
		Player lp = client.getLocalPlayer();

		if (lp != null)
		{
			WorldPoint wp = lp.getWorldLocation();

			WorldPoint[] toCheck = new WorldPoint[4];
			toCheck[0] = wp.dx(1);
			toCheck[1] = wp.dx(-1);
			toCheck[2] = wp.dy(1);
			toCheck[3] = wp.dy(-1);

			Tile[][] tiles = client.getScene().getTiles()[client.getPlane()];
			for (WorldPoint target : toCheck)
			{
				final LocalPoint localTarget = LocalPoint.fromWorld(client, target);
				if (localTarget != null)
				{
					final DecorativeObject obj = tiles[localTarget.getSceneX()][localTarget.getSceneY()].getDecorativeObject();
					if (obj != null)
					{
						int id = obj.getId();
						if (NO_TEARS_IDS.contains(id))
						{
							return TearType.NONE;
						}
						else if (BLUE_TEARS_IDS.contains(id))
						{
							return TearType.BLUE;
						}
						else if (GREEN_TEARS_IDS.contains(id))
						{
							return TearType.GREEN;
						}
					}
				}
			}
		}

		return TearType.NONE;
	}
}
