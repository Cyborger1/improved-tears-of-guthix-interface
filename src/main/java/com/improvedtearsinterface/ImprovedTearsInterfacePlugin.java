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
import java.awt.Color;
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
import net.runelite.client.util.ColorUtil;

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

	private static final int TICKS_FROM_JUNAS_TAIL = 9;

	private static final int TEARS_WP_PLANE = 2;
	private static final int TEARS_WP_MIN_X = 3251;
	private static final int TEARS_WP_MAX_X = 3260;
	private static final int TEARS_WP_MIN_Y = 9515;
	private static final int TEARS_WP_MAX_Y = 9519;
	private static final int TEARS_WP_START_X = 3257;

	private static final int TEARS_WIDGET_GROUP_ID = 276;
	private static final int TEARS_WIDGET_CHILD_TIME_TEXT = 17;
	private static final int TEARS_WIDGET_CHILD_WATER_TEXT = 16;
	private static final int TEARS_WIDGET_CHILD_COUNT_TEXT = 19;

	private static final int COLOR_YELLOW = 0xFFFF00;
	private static final int COLOR_LIGHT_ORANGE = 0xFF9900;
	private static final int COLOR_ORANGE = 0xFF6600;
	private static final int COLOR_RED = 0xFF0000;
	private static final int COLOR_CYAN = 0x00FFFF;
	private static final int COLOR_AQUA = 0x00CCFF;
	private static final int COLOR_GREEN = 0x00FF00;
	private static final int COLOR_DARK_GREEN = 0x00CC00;

	@Inject
	private Client client;

	@Inject
	private ImprovedTearsInterfaceConfig config;

	private int maxTicks = 0;
	private int minigameStarting = 0;
	private boolean minigameEnding = false;
	private int prevTicksLeft = 0;
	private int displayedTicksLeft = 0;
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
		minigameStarting = 0;
		minigameEnding = false;
		prevTicksLeft = 0;
		displayedTicksLeft = 0;
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
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (turnedOnDuringMinigame)
		{
			if (!isInTearsMinigameArea(false))
			{
				reset();
			}
			else
			{
				return;
			}
		}

		int newTicksLeft = client.getVarbitValue(VARBIT_TICKS_LEFT);
		if (newTicksLeft > 0 && isInTearsMinigameArea())
		{
			inTearsMinigame = true;

			// Going from 0 -> Starting amount
			if (prevTicksLeft == 0)
			{
				maxTicks = newTicksLeft;
				displayedTicksLeft = newTicksLeft;
				minigameStarting = TICKS_FROM_JUNAS_TAIL;
			}
			else if (minigameStarting > 0)
			{
				if (isInTearsMinigameArea(false))
				{
					minigameStarting--;
				}
			}
			else if (newTicksLeft == prevTicksLeft)
			{
				if (displayedTicksLeft > 0)
				{
					displayedTicksLeft--;
				}
			}
			else
			{
				displayedTicksLeft = newTicksLeft;
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

			Widget timeLeftWidget = client.getWidget(TEARS_WIDGET_GROUP_ID, TEARS_WIDGET_CHILD_TIME_TEXT);
			int newCollected = client.getVarbitValue(VARBIT_TEARS_COLLECTED);
			boolean newCollecting = client.getVarbitValue(VARBIT_COLLECTING) == 1;

			if (timeLeftWidget != null)
			{
				timeLeftWidget.setText(ColorUtil.wrapWithColorTag("Ticks Left: ", Color.YELLOW)
					+ displayedTicksLeft + " / " + maxTicks);
				double part = displayedTicksLeft / (double) maxTicks;
				if (part < 0.15)
				{
					timeLeftWidget.setTextColor(doFlash ? COLOR_ORANGE : COLOR_RED);
				}
				else if (part < 0.3)
				{
					timeLeftWidget.setTextColor(COLOR_LIGHT_ORANGE);
				}
				else if (part < 0.6)
				{
					timeLeftWidget.setTextColor(COLOR_YELLOW);
				}
				else
				{
					timeLeftWidget.setTextColor(COLOR_GREEN);
				}

				Widget waterTextWidget = client.getWidget(TEARS_WIDGET_GROUP_ID, TEARS_WIDGET_CHILD_WATER_TEXT);
				if (waterTextWidget != null)
				{
					if (newCollecting)
					{
						TearType tear = getAdjacentTearType();
						switch (tear)
						{
							case NONE:
								waterTextWidget.setText("Empty Tear Vein!");
								waterTextWidget.setTextColor(doFlash ? COLOR_LIGHT_ORANGE : COLOR_ORANGE);
								break;
							case BLUE:
								waterTextWidget.setText(ColorUtil.wrapWithColorTag("Collecting ", Color.GREEN)
									+ "Blue" + ColorUtil.wrapWithColorTag(" Tears", Color.GREEN));
								waterTextWidget.setTextColor(doFlash ? COLOR_AQUA : COLOR_CYAN);
								break;
							case GREEN:
								waterTextWidget.setText(ColorUtil.wrapWithColorTag("Collecting ", Color.RED)
									+ "Green" + ColorUtil.wrapWithColorTag(" Tears", Color.RED));
								waterTextWidget.setTextColor(doFlash ? COLOR_DARK_GREEN : COLOR_GREEN);
								break;
						}
					}
					else if (minigameStarting > 0)
					{
						waterTextWidget.setText("Get Ready!");
						waterTextWidget.setTextColor(doFlash ? COLOR_DARK_GREEN : COLOR_GREEN);
					}
					else if (minigameEnding)
					{
						waterTextWidget.setText("Time Up!");
						waterTextWidget.setTextColor(doFlash ? COLOR_ORANGE : COLOR_RED);
					}
					else
					{
						waterTextWidget.setText("Not Collecting");
						waterTextWidget.setTextColor(COLOR_YELLOW);
					}
				}
			}

			if (minigameEnding && !isInTearsMinigameArea(false))
			{
				reset();
			}
			else
			{
				/* DEBUG */
				System.out.println();
				System.out.println("current tick: " + client.getTickCount());
				System.out.println("ticks: " + prevTicksLeft + " -> " + newTicksLeft + " / " + maxTicks);
				System.out.println("coll: " + prevCollected + " -> " + newCollected);
				System.out.println("collb: " + prevCollecting + " -> " + newCollecting);
				System.out.println("disp ticks: " + displayedTicksLeft);
				if (minigameStarting > 0)
				{
					System.out.println("minigame starting... (" + minigameStarting + ")");
				}
				if (minigameEnding)
				{
					System.out.println("minigame ending...");
				}

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
			return (wp != null && wp.getPlane() == TEARS_WP_PLANE
				&& wp.getX() >= TEARS_WP_MIN_X + (includeExterior ? 0 : 1)
				&& wp.getX() <= TEARS_WP_MAX_X
				&& wp.getY() >= TEARS_WP_MIN_Y && wp.getY() <= TEARS_WP_MAX_Y);
		}
		return false;
	}

	private boolean isPastTearsStartingTile()
	{
		Player lp = client.getLocalPlayer();
		if (lp != null)
		{
			WorldPoint wp = lp.getWorldLocation();
			return (wp != null && wp.getPlane() == TEARS_WP_PLANE
				&& wp.getX() >= TEARS_WP_START_X && wp.getX() <= TEARS_WP_MAX_X
				&& wp.getY() >= TEARS_WP_MIN_Y && wp.getY() <= TEARS_WP_MAX_Y);
		}
		return false;
	}

	private TearType getAdjacentTearType()
	{
		// Only ever one tear thing adjacent to the player so this logic should be fine
		Player lp = client.getLocalPlayer();

		if (lp != null)
		{
			WorldPoint wp = lp.getWorldLocation();

			WorldPoint[] toCheck = new WorldPoint[4];
			toCheck[0] = wp.dx(1);
			toCheck[1] = wp.dx(-1);
			toCheck[2] = wp.dy(1);
			toCheck[3] = wp.dy(-1);

			Tile[][] tiles = client.getScene().getTiles()[wp.getPlane()];
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
