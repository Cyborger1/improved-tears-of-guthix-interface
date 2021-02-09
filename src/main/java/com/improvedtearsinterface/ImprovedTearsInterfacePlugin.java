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

import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.awt.Color;
import java.time.Instant;
import java.util.Set;
import javax.inject.Inject;
import lombok.Getter;
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
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;

@Slf4j
@PluginDescriptor(
	name = "Improved Tears Interface",
	description = "Improves the Tears of Guthix minigame interface with a proper tick timer and current action indicator",
	tags = {"Tears", "Guthix", "Tears of Guthix", "Improved", "Interface", "Minigame"}
)
public class ImprovedTearsInterfacePlugin extends Plugin
{
	private static final int VARBIT_TICKS_LEFT = 5099;
	private static final int VARBIT_TEARS_COLLECTED = 455;
	private static final int VARBIT_COLLECTING = 453;

	private static final int TICKS_FROM_JUNAS_TAIL = 9;
	private static final int TICKS_FOR_START_TIMER = 6;

	private static final int TEARS_WP_PLANE = 2;
	private static final int TEARS_WP_MIN_X = 3251;
	private static final int TEARS_WP_MAX_X = 3260;
	private static final int TEARS_WP_MIN_Y = 9515;
	private static final int TEARS_WP_MAX_Y = 9519;

	static final int TEARS_WIDGET_GROUP_ID = 276;
	static final int TEARS_WIDGET_CHILD_TIME_TEXT = 17;
	static final int TEARS_WIDGET_CHILD_WATER_TEXT = 16;
	static final int TEARS_WIDGET_CHILD_COUNT_TEXT = 19;

	private static final int COLOR_YELLOW = 0xFFFF00;
	private static final int COLOR_LIGHT_ORANGE = 0xFF9900;
	private static final int COLOR_ORANGE = 0xFF6600;
	private static final int COLOR_RED = 0xFF0000;
	private static final int COLOR_LIGHT_BLUE = 0x00BBFF;
	private static final int COLOR_BLUE = 0x0066FF;
	private static final int COLOR_GREEN = 0x00FF00;
	private static final int COLOR_DARK_GREEN = 0x00CC00;

	private static final Set<Integer> NO_TEARS_IDS = ImmutableSet.of(ObjectID.ABSENCE_OF_TEARS, ObjectID.ABSENCE_OF_TEARS_6667);
	private static final Set<Integer> BLUE_TEARS_IDS = ImmutableSet.of(ObjectID.BLUE_TEARS, ObjectID.BLUE_TEARS_6665);
	private static final Set<Integer> GREEN_TEARS_IDS = ImmutableSet.of(ObjectID.GREEN_TEARS, ObjectID.GREEN_TEARS_6666);

	private static final String TICK_LEFT_STRING = ColorUtil.wrapWithColorTag("Ticks Left:", Color.YELLOW) + " %d / %d";
	private static final String NOT_COLLECTING_STRING = "Not Collecting";
	private static final String EMPTY_VEIN_STRING = "Empty Tear Vein!";
	private static final String BLUE_VEIN_STRING = ColorUtil.wrapWithColorTag("Collecting", Color.GREEN)
		+ " Blue " + ColorUtil.wrapWithColorTag("Tears", Color.GREEN);
	private static final String GREEN_VEIN_STRING = ColorUtil.wrapWithColorTag("Collecting", Color.RED)
		+ " Green " + ColorUtil.wrapWithColorTag("Tears", Color.RED);
	private static final String MINIGAME_STARTING_STRING = "Get Ready!";
	private static final String MINIGAME_STARTING_IN_SINGULAR_STRING = "Starting in: %d tick";
	private static final String MINIGAME_STARTING_IN_PLURAL_STRING = "Starting in: %d ticks";
	private static final String MINIGAME_ENDING_STRING = "Time Up!";

	@Inject
	private Client client;

	@Inject
	private ImprovedTearsInterfaceConfig config;

	@Inject
	private TearsPlusMinusOverlay overlay;

	@Inject
	private OverlayManager overlayManager;

	@Getter
	private int maxTicks = 0;
	@Getter
	private int minigameStarting = 0;
	@Getter
	private boolean minigameEnding = false;
	@Getter
	private int ticksLeft = 0;
	@Getter
	private int displayedTicksLeft = 0;
	@Getter
	private int tearsCollected = 0;
	@Getter
	private TearCollectingState collectingState = TearCollectingState.NOT_COLLECTING;
	@Getter
	private EvictingQueue<DeltaInstantPair> deltaInstantQueue = EvictingQueue.create(10);

	private boolean turnedOnDuringMinigame = false;
	private boolean inTearsMinigame = false;

	@Provides
	ImprovedTearsInterfaceConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ImprovedTearsInterfaceConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		reset();
		if (isInTearsMinigameArea() && client.getVarbitValue(VARBIT_TICKS_LEFT) > 0)
		{
			turnedOnDuringMinigame = true;
		}
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		// Reset interface
		if (inTearsMinigame)
		{
			Widget timeLeftWidget = client.getWidget(TEARS_WIDGET_GROUP_ID, TEARS_WIDGET_CHILD_TIME_TEXT);
			if (timeLeftWidget != null)
			{
				timeLeftWidget.setText("Time Left");
				timeLeftWidget.setTextColor(COLOR_YELLOW);
			}
			Widget waterTextWidget = client.getWidget(TEARS_WIDGET_GROUP_ID, TEARS_WIDGET_CHILD_WATER_TEXT);
			if (waterTextWidget != null)
			{
				waterTextWidget.setText("Water Collected");
				waterTextWidget.setTextColor(COLOR_YELLOW);
			}
			Widget tearsCountWidget = client.getWidget(TEARS_WIDGET_GROUP_ID, TEARS_WIDGET_CHILD_COUNT_TEXT);
			if (tearsCountWidget != null)
			{
				tearsCountWidget.setTextColor(COLOR_YELLOW);
			}
		}

		reset();
		overlayManager.remove(overlay);
	}

	private void reset()
	{
		maxTicks = 0;
		minigameStarting = 0;
		minigameEnding = false;
		ticksLeft = 0;
		displayedTicksLeft = 0;
		tearsCollected = 0;
		inTearsMinigame = false;
		turnedOnDuringMinigame = false;
		collectingState = TearCollectingState.NOT_COLLECTING;
		deltaInstantQueue.clear();
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
			if (ticksLeft == 0)
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
			else if (newTicksLeft == ticksLeft)
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
		else if (ticksLeft > 0)
		{
			minigameEnding = true;
			ticksLeft = 0;
			displayedTicksLeft = 0;
		}

		if (inTearsMinigame)
		{
			Widget timeLeftWidget = client.getWidget(TEARS_WIDGET_GROUP_ID, TEARS_WIDGET_CHILD_TIME_TEXT);
			final int newTearsCollected = client.getVarbitValue(VARBIT_TEARS_COLLECTED);
			collectingState = getCurrentCollectingState();

			if (timeLeftWidget != null)
			{
				boolean doFlash = config.getFlashingText() && client.getTickCount() % 2 != 0;

				timeLeftWidget.setText(String.format(TICK_LEFT_STRING, displayedTicksLeft, maxTicks));
				if (displayedTicksLeft > 0 && maxTicks > 0)
				{
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
				}
				else
				{
					timeLeftWidget.setTextColor(COLOR_RED);
				}

				Widget waterTextWidget = client.getWidget(TEARS_WIDGET_GROUP_ID, TEARS_WIDGET_CHILD_WATER_TEXT);
				if (waterTextWidget != null)
				{
					if (minigameStarting > 0)
					{
						if (minigameStarting >= TICKS_FOR_START_TIMER)
						{
							waterTextWidget.setText(MINIGAME_STARTING_STRING);
						}
						else
						{
							if (minigameStarting == 1)
							{
								waterTextWidget.setText(String.format(MINIGAME_STARTING_IN_SINGULAR_STRING, minigameStarting));
							}
							else
							{
								waterTextWidget.setText(String.format(MINIGAME_STARTING_IN_PLURAL_STRING, minigameStarting));
							}
						}

						waterTextWidget.setTextColor(doFlash ? COLOR_DARK_GREEN : COLOR_GREEN);
					}
					else if (minigameEnding)
					{
						waterTextWidget.setText(MINIGAME_ENDING_STRING);
						waterTextWidget.setTextColor(doFlash ? COLOR_ORANGE : COLOR_RED);
					}
					else
					{
						switch (collectingState)
						{
							case BLUE_VEIN:
								waterTextWidget.setText(BLUE_VEIN_STRING);
								waterTextWidget.setTextColor(doFlash ? COLOR_BLUE : COLOR_LIGHT_BLUE);
								break;
							case GREEN_VEIN:
								waterTextWidget.setText(GREEN_VEIN_STRING);
								waterTextWidget.setTextColor(doFlash ? COLOR_DARK_GREEN : COLOR_GREEN);
								break;
							case EMPTY_VEIN:
								waterTextWidget.setText(EMPTY_VEIN_STRING);
								waterTextWidget.setTextColor(doFlash ? COLOR_LIGHT_ORANGE : COLOR_ORANGE);
								break;
							default:
								waterTextWidget.setText(NOT_COLLECTING_STRING);
								waterTextWidget.setTextColor(COLOR_YELLOW);
								break;
						}
					}
				}

				final int tearsDiff = newTearsCollected - tearsCollected;
				deltaInstantQueue.add(DeltaInstantPair.builder().delta(tearsDiff).time(Instant.now()).build());
				Widget tearsCountWidget = client.getWidget(TEARS_WIDGET_GROUP_ID, TEARS_WIDGET_CHILD_COUNT_TEXT);
				if (tearsCountWidget != null)
				{
					if (tearsDiff > 0)
					{
						tearsCountWidget.setTextColor(COLOR_GREEN);
					}
					else if (tearsDiff < 0)
					{
						tearsCountWidget.setTextColor(COLOR_RED);
					}
					else
					{
						tearsCountWidget.setTextColor(COLOR_YELLOW);
					}
				}
			}

			if (minigameEnding && !isInTearsMinigameArea(false))
			{
				reset();
			}
			else
			{
				ticksLeft = newTicksLeft;
				tearsCollected = newTearsCollected;
			}
		}
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

	private TearCollectingState getCurrentCollectingState()
	{
		if (client.getVarbitValue(VARBIT_COLLECTING) == 0)
		{
			return TearCollectingState.NOT_COLLECTING;
		}

		// Only ever one tear thing adjacent to the player so this logic should be fine
		Player lp = client.getLocalPlayer();

		if (lp != null)
		{
			WorldPoint wp = lp.getWorldLocation();

			// Don't need to check dx(-1) as there is no tears on the western side
			WorldPoint[] toCheck = new WorldPoint[3];
			toCheck[0] = wp.dx(1);
			toCheck[1] = wp.dy(1);
			toCheck[2] = wp.dy(-1);

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
							return TearCollectingState.EMPTY_VEIN;
						}
						else if (BLUE_TEARS_IDS.contains(id))
						{
							return TearCollectingState.BLUE_VEIN;
						}
						else if (GREEN_TEARS_IDS.contains(id))
						{
							return TearCollectingState.GREEN_VEIN;
						}
					}
				}
			}
		}

		return TearCollectingState.EMPTY_VEIN;
	}
}
