/*
 * Copyright (c) 2021, Cyborger1
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.improvedtearsinterface;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TextComponent;

class TearsPlusMinusOverlay extends Overlay
{
	private static final double HEIGHTS = 4.0;
	private static final long DURATION = 2000;
	private static final int START_OFFSET_X = 10;
	private static final int START_OFFSET_Y = 0;

	private final Client client;
	private final ImprovedTearsInterfacePlugin plugin;
	private final ImprovedTearsInterfaceConfig config;

	private final TextComponent textComponent = new TextComponent();

	private final DecimalFormat fmt = new DecimalFormat("+#;-#");

	@Inject
	TearsPlusMinusOverlay(Client client, ImprovedTearsInterfacePlugin plugin, ImprovedTearsInterfaceConfig config)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		this.client = client;
		this.plugin = plugin;
		this.config = config;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showPlusMinusOnCollect() || plugin.getDeltaInstantQueue().isEmpty())
		{
			return null;
		}

		Widget counter = client.getWidget(ImprovedTearsInterfacePlugin.TEARS_WIDGET_GROUP_ID,
			ImprovedTearsInterfacePlugin.TEARS_WIDGET_CHILD_COUNT_TEXT);

		if (counter != null)
		{
			final Rectangle bounds = counter.getBounds();
			final int x = bounds.x + bounds.width + START_OFFSET_X;
			final int y = bounds.y + bounds.height + START_OFFSET_Y;
			final int h = bounds.height;

			Instant now = Instant.now();

			for (DeltaInstantPair dip : plugin.getDeltaInstantQueue())
			{
				int delta = dip.getDelta();
				if (delta == 0)
				{
					continue;
				}

				final double ratio = (double)Duration.between(dip.getTime(), now).toMillis() / DURATION;
				if (ratio < 0 || ratio > 1)
				{
					continue;
				}

				textComponent.setText(fmt.format(delta));
				textComponent.setColor(delta > 0 ? Color.GREEN : Color.RED);
				textComponent.setOutline(false);
				textComponent.setPosition(
					new java.awt.Point(x, y - (int)(ratio * h * HEIGHTS)));
				textComponent.render(graphics);
			}
		}
		return null;
	}
}
