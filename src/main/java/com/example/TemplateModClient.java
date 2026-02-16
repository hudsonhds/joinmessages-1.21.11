package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;

public class TemplateModClient implements ClientModInitializer {
	// Change this to the exact username you want to watch for.
	private static final String TARGET_USERNAME = "Notch";

	private final Set<String> knownPlayers = new HashSet<>();
	private boolean seededForCurrentServer = false;

	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			knownPlayers.clear();
			seededForCurrentServer = false;
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			knownPlayers.clear();
			seededForCurrentServer = false;
		});

		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
	}

	private void onClientTick(Minecraft client) {
		if (client.level == null || client.getConnection() == null) {
			return;
		}

		Set<String> currentPlayers = new HashSet<>();
		for (PlayerInfo entry : client.getConnection().getOnlinePlayers()) {
			currentPlayers.add(entry.getProfile().name());
		}

		// Seed once per connection so existing online players are not treated as "just joined".
		if (!seededForCurrentServer) {
			knownPlayers.clear();
			knownPlayers.addAll(currentPlayers);
			seededForCurrentServer = true;
			return;
		}

		boolean targetIsNowOnline = currentPlayers.stream()
			.anyMatch(name -> name.equalsIgnoreCase(TARGET_USERNAME));
		boolean targetWasOnline = knownPlayers.stream()
			.anyMatch(name -> name.equalsIgnoreCase(TARGET_USERNAME));

		if (targetIsNowOnline && !targetWasOnline && client.player != null) {
			client.player.displayClientMessage(Component.literal("[Template Mod] " + TARGET_USERNAME + " joined the server."), false);
		}

		knownPlayers.clear();
		knownPlayers.addAll(currentPlayers);
	}
}
