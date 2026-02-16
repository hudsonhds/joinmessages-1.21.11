package com.example;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class JoinMessagesModClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger(JoinMessagesMod.MOD_ID + "-client");

	private final Set<String> knownPlayers = new HashSet<>();
	private final JoinMessagesConfig config = JoinMessagesConfig.getInstance();
	private boolean seededForCurrentServer = false;
	private KeyMapping openConfigKey;

	@Override
	public void onInitializeClient() {
		String version = FabricLoader.getInstance()
			.getModContainer(JoinMessagesMod.MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
		String codeSource = "unknown";
		try {
			if (JoinMessagesModClient.class.getProtectionDomain().getCodeSource() != null) {
				codeSource = JoinMessagesModClient.class.getProtectionDomain().getCodeSource().getLocation().toString();
			}
		} catch (SecurityException ignored) {
		}
		LOGGER.info("Loaded {} client v{} from {}", JoinMessagesMod.MOD_ID, version, codeSource);

		this.openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.joinmessages-mod.open_config",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_J,
			KeyMapping.Category.MISC
		));

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
		while (openConfigKey.consumeClick()) {
			client.setScreen(new JoinMessagesConfigScreen(client.screen, config));
		}

		if (client.level == null || client.getConnection() == null || client.player == null) {
			return;
		}

		Set<String> currentPlayers = new HashSet<>();
		for (PlayerInfo entry : client.getConnection().getListedOnlinePlayers()) {
			currentPlayers.add(entry.getProfile().name());
		}

		if (!seededForCurrentServer) {
			knownPlayers.clear();
			knownPlayers.addAll(currentPlayers);
			seededForCurrentServer = true;
			return;
		}

		Set<String> joinedPlayers = currentPlayers.stream()
			.filter(name -> !containsIgnoreCase(knownPlayers, name))
			.collect(Collectors.toSet());
		Set<String> leftPlayers = knownPlayers.stream()
			.filter(name -> !containsIgnoreCase(currentPlayers, name))
			.collect(Collectors.toSet());

		if (config.enabled()) {
			for (String joined : joinedPlayers) {
				sendModMessage(client, joined + " joined the server.");
			}
			for (String left : leftPlayers) {
				sendModMessage(client, left + " left the server.");
			}
		}

		knownPlayers.clear();
		knownPlayers.addAll(currentPlayers);
	}

	private void sendModMessage(Minecraft client, String message) {
		String prefix = config.showPrefix() ? "[JoinMessages] " : "";
		Component text = Component.literal(prefix + message).withStyle(config.messageColor().formatting());
		client.player.displayClientMessage(text, false);
	}

	private static boolean containsIgnoreCase(Set<String> names, String target) {
		return names.stream().anyMatch(name -> name.equalsIgnoreCase(target));
	}
}
