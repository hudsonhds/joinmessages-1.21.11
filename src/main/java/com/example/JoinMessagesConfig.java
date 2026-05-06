package com.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JoinMessagesConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(JoinMessagesMod.MOD_ID + ".json");
	private static final JoinMessagesConfig INSTANCE = load();

	private boolean enabled;
	private boolean showPrefix;
	private boolean suppressIfServerMessage;
	private boolean autoWelcomeEnabled;
	private String autoWelcomeMessage;
	private MessageColor messageColor;
	private GameModeMessagesMode gameModeMessagesMode;
	private boolean limitGamemodeTrackingByPlayerCount;
	private int gamemodeTrackingMaxPlayers;
	private JoinGamemodeNotifyMode joinGamemodeNotifyMode;

	private JoinMessagesConfig(
		boolean enabled,
		boolean showPrefix,
		boolean suppressIfServerMessage,
		boolean autoWelcomeEnabled,
		String autoWelcomeMessage,
		MessageColor messageColor,
		GameModeMessagesMode gameModeMessagesMode,
		boolean limitGamemodeTrackingByPlayerCount,
		int gamemodeTrackingMaxPlayers,
		JoinGamemodeNotifyMode joinGamemodeNotifyMode
	) {
		this.enabled = enabled;
		this.showPrefix = showPrefix;
		this.suppressIfServerMessage = suppressIfServerMessage;
		this.autoWelcomeEnabled = autoWelcomeEnabled;
		this.autoWelcomeMessage = autoWelcomeMessage;
		this.messageColor = messageColor;
		this.gameModeMessagesMode = gameModeMessagesMode;
		this.limitGamemodeTrackingByPlayerCount = limitGamemodeTrackingByPlayerCount;
		this.gamemodeTrackingMaxPlayers = sanitizeMaxPlayers(gamemodeTrackingMaxPlayers);
		this.joinGamemodeNotifyMode = joinGamemodeNotifyMode;
	}

	public static JoinMessagesConfig getInstance() {
		return INSTANCE;
	}

	public static JoinMessagesConfig load() {
		if (!Files.exists(CONFIG_PATH)) {
			JoinMessagesConfig config = defaults();
			config.save();
			return config;
		}

		try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
			SerializedConfig data = GSON.fromJson(reader, SerializedConfig.class);
			if (data == null) {
				JoinMessagesConfig config = defaults();
				config.save();
				return config;
			}

			MessageColor color = MessageColor.fromName(data.messageColor);
			GameModeMessagesMode gameModeMode = GameModeMessagesMode.fromName(data.gameModeMessagesMode);
			String autoWelcomeMessage = sanitizeAutoWelcomeMessage(data.autoWelcomeMessage);
			return new JoinMessagesConfig(
				data.enabled,
				data.showPrefix,
				data.suppressIfServerMessage,
				data.autoWelcomeEnabled,
				autoWelcomeMessage,
				color,
				gameModeMode,
				data.limitGamemodeTrackingByPlayerCount,
				sanitizeMaxPlayers(data.gamemodeTrackingMaxPlayers),
				JoinGamemodeNotifyMode.fromName(data.joinGamemodeNotifyMode)
			);
		} catch (IOException | JsonParseException e) {
			JoinMessagesMod.LOGGER.warn("Failed to read config at {}. Using defaults.", CONFIG_PATH, e);
			return defaults();
		}
	}

	public void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			SerializedConfig data = new SerializedConfig();
			data.enabled = this.enabled;
			data.showPrefix = this.showPrefix;
			data.suppressIfServerMessage = this.suppressIfServerMessage;
			data.autoWelcomeEnabled = this.autoWelcomeEnabled;
			data.autoWelcomeMessage = sanitizeAutoWelcomeMessage(this.autoWelcomeMessage);
			data.messageColor = this.messageColor.name();
			data.gameModeMessagesMode = this.gameModeMessagesMode.name();
			data.limitGamemodeTrackingByPlayerCount = this.limitGamemodeTrackingByPlayerCount;
			data.gamemodeTrackingMaxPlayers = sanitizeMaxPlayers(this.gamemodeTrackingMaxPlayers);
			data.joinGamemodeNotifyMode = this.joinGamemodeNotifyMode.name();

			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			JoinMessagesMod.LOGGER.warn("Failed to write config at {}", CONFIG_PATH, e);
		}
	}

	public MessageColor messageColor() {
		return messageColor;
	}

	public void setMessageColor(MessageColor messageColor) {
		if (this.messageColor == messageColor) {
			return;
		}
		this.messageColor = messageColor;
		save();
	}

	public boolean enabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		save();
	}

	public boolean showPrefix() {
		return showPrefix;
	}

	public void setShowPrefix(boolean showPrefix) {
		if (this.showPrefix == showPrefix) {
			return;
		}
		this.showPrefix = showPrefix;
		save();
	}

	public boolean suppressIfServerMessage() {
		return suppressIfServerMessage;
	}

	public void setSuppressIfServerMessage(boolean suppressIfServerMessage) {
		if (this.suppressIfServerMessage == suppressIfServerMessage) {
			return;
		}
		this.suppressIfServerMessage = suppressIfServerMessage;
		save();
	}

	public GameModeMessagesMode gameModeMessagesMode() {
		return gameModeMessagesMode;
	}

	public void setGameModeMessagesMode(GameModeMessagesMode gameModeMessagesMode) {
		if (this.gameModeMessagesMode == gameModeMessagesMode) {
			return;
		}
		this.gameModeMessagesMode = gameModeMessagesMode;
		save();
	}

	public boolean autoWelcomeEnabled() {
		return autoWelcomeEnabled;
	}

	public void setAutoWelcomeEnabled(boolean autoWelcomeEnabled) {
		if (this.autoWelcomeEnabled == autoWelcomeEnabled) {
			return;
		}
		this.autoWelcomeEnabled = autoWelcomeEnabled;
		save();
	}

	public String autoWelcomeMessage() {
		return sanitizeAutoWelcomeMessage(autoWelcomeMessage);
	}

	public void setAutoWelcomeMessage(String autoWelcomeMessage) {
		String sanitized = sanitizeAutoWelcomeMessage(autoWelcomeMessage);
		if (this.autoWelcomeMessage.equals(sanitized)) {
			return;
		}
		this.autoWelcomeMessage = sanitized;
		save();
	}

	public static JoinMessagesConfig defaults() {
		return new JoinMessagesConfig(
			true,
			true,
			true,
			false,
			"Welcome {player}!",
			MessageColor.YELLOW,
			GameModeMessagesMode.OFF,
			true,
			40,
			JoinGamemodeNotifyMode.OFF
		);
	}

	private static String sanitizeAutoWelcomeMessage(String message) {
		if (message == null || message.isBlank()) {
			return "Welcome {player}!";
		}
		return message.trim();
	}

	private static int sanitizeMaxPlayers(int value) {
		return Math.max(2, Math.min(500, value));
	}

	public boolean limitGamemodeTrackingByPlayerCount() {
		return limitGamemodeTrackingByPlayerCount;
	}

	public void setLimitGamemodeTrackingByPlayerCount(boolean limitGamemodeTrackingByPlayerCount) {
		if (this.limitGamemodeTrackingByPlayerCount == limitGamemodeTrackingByPlayerCount) {
			return;
		}
		this.limitGamemodeTrackingByPlayerCount = limitGamemodeTrackingByPlayerCount;
		save();
	}

	public int gamemodeTrackingMaxPlayers() {
		return gamemodeTrackingMaxPlayers;
	}

	public void setGamemodeTrackingMaxPlayers(int gamemodeTrackingMaxPlayers) {
		int sanitized = sanitizeMaxPlayers(gamemodeTrackingMaxPlayers);
		if (this.gamemodeTrackingMaxPlayers == sanitized) {
			return;
		}
		this.gamemodeTrackingMaxPlayers = sanitized;
		save();
	}

	public JoinGamemodeNotifyMode joinGamemodeNotifyMode() {
		return joinGamemodeNotifyMode;
	}

	public void setJoinGamemodeNotifyMode(JoinGamemodeNotifyMode joinGamemodeNotifyMode) {
		if (this.joinGamemodeNotifyMode == joinGamemodeNotifyMode) {
			return;
		}
		this.joinGamemodeNotifyMode = joinGamemodeNotifyMode;
		save();
	}

	public enum MessageColor {
		WHITE(ChatFormatting.WHITE, "White"),
		YELLOW(ChatFormatting.YELLOW, "Yellow"),
		GREEN(ChatFormatting.GREEN, "Green"),
		AQUA(ChatFormatting.AQUA, "Aqua"),
		RED(ChatFormatting.RED, "Red"),
		GOLD(ChatFormatting.GOLD, "Gold"),
		LIGHT_PURPLE(ChatFormatting.LIGHT_PURPLE, "Light Purple");

		private final ChatFormatting formatting;
		private final String label;

		MessageColor(ChatFormatting formatting, String label) {
			this.formatting = formatting;
			this.label = label;
		}

		public ChatFormatting formatting() {
			return formatting;
		}

		public String label() {
			return label;
		}

		public static MessageColor fromName(String name) {
			if (name == null || name.isBlank()) {
				return YELLOW;
			}
			for (MessageColor color : values()) {
				if (color.name().equalsIgnoreCase(name)) {
					return color;
				}
			}
			return YELLOW;
		}
	}

	public enum GameModeMessagesMode {
		OFF("Off"),
		SPECTATOR_ONLY("Only Spectator Messages"),
		ALL("All Gamemode Messages");

		private final String label;

		GameModeMessagesMode(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

		public GameModeMessagesMode next() {
			GameModeMessagesMode[] values = values();
			int nextIndex = (this.ordinal() + 1) % values.length;
			return values[nextIndex];
		}

		public static GameModeMessagesMode fromName(String name) {
			if (name == null || name.isBlank()) {
				return OFF;
			}
			for (GameModeMessagesMode mode : values()) {
				if (mode.name().equalsIgnoreCase(name)) {
					return mode;
				}
			}
			return OFF;
		}
	}

	public enum JoinGamemodeNotifyMode {
		OFF("Off"),
		CREATIVE("Creative"),
		SURVIVAL("Survival"),
		ADVENTURE("Adventure"),
		SPECTATOR("Spectator"),
		ALL("All");

		private final String label;

		JoinGamemodeNotifyMode(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

		public JoinGamemodeNotifyMode next() {
			JoinGamemodeNotifyMode[] values = values();
			int nextIndex = (this.ordinal() + 1) % values.length;
			return values[nextIndex];
		}

		public static JoinGamemodeNotifyMode fromName(String name) {
			if (name == null || name.isBlank()) {
				return ALL;
			}
			for (JoinGamemodeNotifyMode mode : values()) {
				if (mode.name().equalsIgnoreCase(name)) {
					return mode;
				}
			}
			return ALL;
		}
	}

	private static final class SerializedConfig {
		boolean enabled = true;
		boolean showPrefix = true;
		boolean suppressIfServerMessage = true;
		boolean autoWelcomeEnabled = false;
		String autoWelcomeMessage = "Welcome {player}!";
		String messageColor = MessageColor.YELLOW.name();
		String gameModeMessagesMode = GameModeMessagesMode.OFF.name();
		boolean limitGamemodeTrackingByPlayerCount = true;
		int gamemodeTrackingMaxPlayers = 40;
		String joinGamemodeNotifyMode = JoinGamemodeNotifyMode.ALL.name();
	}
}
