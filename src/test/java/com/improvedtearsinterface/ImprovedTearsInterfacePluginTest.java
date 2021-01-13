package com.improvedtearsinterface;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ImprovedTearsInterfacePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ImprovedTearsInterfacePlugin.class);
		RuneLite.main(args);
	}
}