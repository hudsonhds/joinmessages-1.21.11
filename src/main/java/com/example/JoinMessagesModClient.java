package com.example;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JoinMessagesModClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger(JoinMessagesMod.MOD_ID + "-client");
	private static final Pattern JOIN_PATTERN = Pattern.compile("\\b([A-Za-z0-9_]{3,16})\\b\\s+(joined|connected)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern LEAVE_PATTERN = Pattern.compile("\\b([A-Za-z0-9_]{3,16})\\b\\s+(left|quit|disconnected)\\b", Pattern.CASE_INSENSITIVE);
	private static final long SERVER_ANNOUNCEMENT_WINDOW_MS = 5000L;
	private static final long PENDING_MESSAGE_DELAY_MS = 1200L;
	private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
		Identifier.parse(JoinMessagesMod.MOD_ID + ":general")
	);

	private final Set<String> knownPlayers = new HashSet<>();
	private final Map<String, Long> recentServerJoinAnnouncements = new HashMap<>();
	private final Map<String, Long> recentServerLeaveAnnouncements = new HashMap<>();
	private final Map<String, PendingEvent> pendingJoinMessages = new HashMap<>();
	private final Map<String, PendingEvent> pendingLeaveMessages = new HashMap<>();
	private int pendingServerJoinSignals = 0;
	private int pendingServerLeaveSignals = 0;
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
			KEY_CATEGORY
		));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			knownPlayers.clear();
			recentServerJoinAnnouncements.clear();
			recentServerLeaveAnnouncements.clear();
			pendingJoinMessages.clear();
			pendingLeaveMessages.clear();
			pendingServerJoinSignals = 0;
			pendingServerLeaveSignals = 0;
			seededForCurrentServer = false;
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			knownPlayers.clear();
			recentServerJoinAnnouncements.clear();
			recentServerLeaveAnnouncements.clear();
			pendingJoinMessages.clear();
			pendingLeaveMessages.clear();
			pendingServerJoinSignals = 0;
			pendingServerLeaveSignals = 0;
			seededForCurrentServer = false;
		});

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			recordServerJoinLeaveAnnouncement(message);
		});
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
			recordServerJoinLeaveAnnouncement(message);
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
		pruneOldAnnouncements();
		flushPendingMessages(client);

		Set<String> currentPlayers = new HashSet<>();
		for (PlayerInfo entry : client.getConnection().getListedOnlinePlayers()) {
			String profileName = getProfileName(entry);
			if (profileName != null && !profileName.isBlank()) {
				currentPlayers.add(profileName);
			}
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
				handlePlayerEvent(client, joined, true);
			}
			for (String left : leftPlayers) {
				handlePlayerEvent(client, left, false);
			}
		} else {
			pendingJoinMessages.clear();
			pendingLeaveMessages.clear();
			pendingServerJoinSignals = 0;
			pendingServerLeaveSignals = 0;
		}

		knownPlayers.clear();
		knownPlayers.addAll(currentPlayers);
	}

	private void sendModMessage(Minecraft client, String message) {
		String prefix = config.showPrefix() ? "[JoinMessages] " : "";
		Component text = Component.literal(prefix + message).withStyle(config.messageColor().formatting());
		client.player.displayClientMessage(text, false);
	}

	private void handlePlayerEvent(Minecraft client, String playerName, boolean joining) {
		if (!config.suppressIfServerMessage()) {
			sendModMessage(client, playerName + (joining ? " joined the game" : " left the game"));
			return;
		}

		if (shouldSuppressForServerAnnouncement(playerName, joining)) {
			return;
		}

		Map<String, PendingEvent> pending = joining ? pendingJoinMessages : pendingLeaveMessages;
		pending.put(normalizePlayerName(playerName), new PendingEvent(playerName, System.currentTimeMillis()));
	}

	private void flushPendingMessages(Minecraft client) {
		long now = System.currentTimeMillis();

		flushPendingMap(client, pendingJoinMessages, true, now);
		flushPendingMap(client, pendingLeaveMessages, false, now);
	}

	private void flushPendingMap(Minecraft client, Map<String, PendingEvent> pending, boolean joining, long now) {
		Set<String> keys = new HashSet<>(pending.keySet());
		for (String key : keys) {
			PendingEvent event = pending.get(key);
			if (event == null) {
				continue;
			}

			if (config.suppressIfServerMessage() && shouldSuppressForServerAnnouncement(event.playerName(), joining)) {
				pending.remove(key);
				continue;
			}

			if (config.suppressIfServerMessage() && consumeGenericServerSignal(joining)) {
				pending.remove(key);
				continue;
			}

			if (!config.suppressIfServerMessage() || (now - event.detectedAtMillis()) >= PENDING_MESSAGE_DELAY_MS) {
				sendModMessage(client, event.playerName() + (joining ? " joined the game." : " left the game."));
				pending.remove(key);
			}
		}
	}

	private boolean consumeGenericServerSignal(boolean joining) {
		if (joining) {
			if (pendingServerJoinSignals > 0) {
				pendingServerJoinSignals--;
				return true;
			}
			return false;
		}

		if (pendingServerLeaveSignals > 0) {
			pendingServerLeaveSignals--;
			return true;
		}
		return false;
	}

	private boolean shouldSuppressForServerAnnouncement(String playerName, boolean joining) {
		if (!config.suppressIfServerMessage()) {
			return false;
		}
		String normalizedName = normalizePlayerName(playerName);
		Map<String, Long> source = joining ? recentServerJoinAnnouncements : recentServerLeaveAnnouncements;
		Long when = source.get(normalizedName);
		return when != null && (System.currentTimeMillis() - when) <= SERVER_ANNOUNCEMENT_WINDOW_MS;
	}

	private void recordServerJoinLeaveAnnouncement(Component message) {
		String content = message.getString();
		if (content == null || content.isBlank()) {
			return;
		}

		long now = System.currentTimeMillis();
		String normalizedContent = content.toLowerCase(Locale.ROOT);
		Matcher joinMatcher = JOIN_PATTERN.matcher(content);
		int joinMatches = 0;
		while (joinMatcher.find()) {
			joinMatches++;
			String playerName = normalizePlayerName(joinMatcher.group(1));
			recentServerJoinAnnouncements.put(playerName, now);
			pendingJoinMessages.remove(playerName);
		}
		if (joinMatches == 0 && containsJoinPhrase(normalizedContent)) {
			joinMatches = 1;
		}
		pendingServerJoinSignals += joinMatches;

		Matcher leaveMatcher = LEAVE_PATTERN.matcher(content);
		int leaveMatches = 0;
		while (leaveMatcher.find()) {
			leaveMatches++;
			String playerName = normalizePlayerName(leaveMatcher.group(1));
			recentServerLeaveAnnouncements.put(playerName, now);
			pendingLeaveMessages.remove(playerName);
		}
		if (leaveMatches == 0 && containsLeavePhrase(normalizedContent)) {
			leaveMatches = 1;
		}
		pendingServerLeaveSignals += leaveMatches;
	}

	private void pruneOldAnnouncements() {
		long threshold = System.currentTimeMillis() - SERVER_ANNOUNCEMENT_WINDOW_MS;
		recentServerJoinAnnouncements.entrySet().removeIf(entry -> entry.getValue() < threshold);
		recentServerLeaveAnnouncements.entrySet().removeIf(entry -> entry.getValue() < threshold);
	}

	private static String normalizePlayerName(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	private static boolean containsJoinPhrase(String content) {
		return content.contains(" joined the game")
			|| content.contains(" joined the server")
			|| content.contains(" connected");
	}

	private static boolean containsLeavePhrase(String content) {
		return content.contains(" left the game")
			|| content.contains(" left the server")
			|| content.contains(" disconnected")
			|| content.contains(" quit");
	}

	private record PendingEvent(String playerName, long detectedAtMillis) {
	}

	private static boolean containsIgnoreCase(Set<String> names, String target) {
		return names.stream().anyMatch(name -> name.equalsIgnoreCase(target));
	}

	private String getProfileName(PlayerInfo entry) {
		Object profile = entry.getProfile();
		if (profile == null) {
			return null;
		}

		try {
			Method nameMethod = profile.getClass().getMethod("name");
			Object name = nameMethod.invoke(profile);
			if (name instanceof String) {
				return (String) name;
			}
		} catch (NoSuchMethodException ignored) {
		} catch (IllegalAccessException | InvocationTargetException e) {
			LOGGER.debug("Could not call GameProfile.name()", e);
		}

		try {
			Method getNameMethod = profile.getClass().getMethod("getName");
			Object name = getNameMethod.invoke(profile);
			if (name instanceof String) {
				return (String) name;
			}
		} catch (NoSuchMethodException ignored) {
		} catch (IllegalAccessException | InvocationTargetException e) {
			LOGGER.debug("Could not call GameProfile.getName()", e);
		}

		return null;
	}
}
