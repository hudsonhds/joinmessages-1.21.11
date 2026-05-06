package com.example;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

public class JoinMessagesConfigScreen extends Screen {
	private final Screen parent;
	private final JoinMessagesConfig config;

	private Button enabledButton;
	private Button prefixButton;
	private Button suppressButton;
	private Button colorButton;
	private Button gamemodeMessagesButton;
	private Button limitGamemodeTrackingButton;
	private Button joinGamemodeNotifyButton;
	private Button autoWelcomeEnabledButton;
	private EditBox autoWelcomeMessageField;
	private EditBox gamemodeTrackingMaxPlayersField;

	public JoinMessagesConfigScreen(Screen parent, JoinMessagesConfig config) {
		super(Component.literal("Join Messages Config"));
		this.parent = parent;
		this.config = config;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int startY = this.height / 4 + 24;
		int columnWidth = 200;
		int columnGap = 12;
		int rowHeight = 24;
		int leftX = centerX - columnWidth - (columnGap / 2);
		int rightX = centerX + (columnGap / 2);

		this.enabledButton = Button.builder(Component.empty(), button -> {
			config.setEnabled(!config.enabled());
			updateEnabledButtonText();
		}).bounds(leftX, startY, columnWidth, 20).build();
		this.addRenderableWidget(this.enabledButton);
		updateEnabledButtonText();

		this.prefixButton = Button.builder(Component.empty(), button -> {
			config.setShowPrefix(!config.showPrefix());
			updatePrefixButtonText();
		}).bounds(leftX, startY + rowHeight, columnWidth, 20).build();
		this.addRenderableWidget(this.prefixButton);
		updatePrefixButtonText();

		this.suppressButton = Button.builder(Component.empty(), button -> {
			config.setSuppressIfServerMessage(!config.suppressIfServerMessage());
			updateSuppressButtonText();
		}).bounds(leftX, startY + (rowHeight * 2), columnWidth, 20).build();
		this.addRenderableWidget(this.suppressButton);
		updateSuppressButtonText();

		this.colorButton = Button.builder(Component.empty(), button -> {
			config.setMessageColor(nextColor(config.messageColor()));
			updateColorButtonText();
		}).bounds(leftX, startY + (rowHeight * 3), columnWidth, 20).build();
		this.addRenderableWidget(this.colorButton);
		updateColorButtonText();

		this.gamemodeMessagesButton = Button.builder(Component.empty(), button -> {
			config.setGameModeMessagesMode(config.gameModeMessagesMode().next());
			updateGamemodeMessagesButtonText();
		}).bounds(leftX, startY + (rowHeight * 4), columnWidth, 20).build();
		this.addRenderableWidget(this.gamemodeMessagesButton);
		updateGamemodeMessagesButtonText();

		this.limitGamemodeTrackingButton = Button.builder(Component.empty(), button -> {
			config.setLimitGamemodeTrackingByPlayerCount(!config.limitGamemodeTrackingByPlayerCount());
			updateLimitGamemodeTrackingButtonText();
		}).bounds(rightX, startY, columnWidth, 20).build();
		this.addRenderableWidget(this.limitGamemodeTrackingButton);
		updateLimitGamemodeTrackingButtonText();

		this.gamemodeTrackingMaxPlayersField = new EditBox(this.font, rightX, startY + rowHeight, columnWidth, 20, Component.literal("Max players for tracking"));
		this.gamemodeTrackingMaxPlayersField.setMaxLength(3);
		this.gamemodeTrackingMaxPlayersField.setValue(Integer.toString(config.gamemodeTrackingMaxPlayers()));
		this.gamemodeTrackingMaxPlayersField.setResponder(value -> {
			if (value == null || value.isBlank()) {
				return;
			}
			try {
				config.setGamemodeTrackingMaxPlayers(Integer.parseInt(value.trim()));
			} catch (NumberFormatException ignored) {
			}
		});
		this.addRenderableWidget(this.gamemodeTrackingMaxPlayersField);

		this.joinGamemodeNotifyButton = Button.builder(Component.empty(), button -> {
			config.setJoinGamemodeNotifyMode(config.joinGamemodeNotifyMode().next());
			updateJoinGamemodeNotifyButtonText();
		}).bounds(rightX, startY + (rowHeight * 2), columnWidth, 20).build();
		this.addRenderableWidget(this.joinGamemodeNotifyButton);
		updateJoinGamemodeNotifyButtonText();

		this.autoWelcomeEnabledButton = Button.builder(Component.empty(), button -> {
			config.setAutoWelcomeEnabled(!config.autoWelcomeEnabled());
			updateAutoWelcomeEnabledButtonText();
		}).bounds(rightX, startY + (rowHeight * 3), columnWidth, 20).build();
		this.addRenderableWidget(this.autoWelcomeEnabledButton);
		updateAutoWelcomeEnabledButtonText();

		this.autoWelcomeMessageField = new EditBox(this.font, rightX, startY + (rowHeight * 4), columnWidth, 20, Component.literal("Auto welcome message"));
		this.autoWelcomeMessageField.setMaxLength(256);
		this.autoWelcomeMessageField.setValue(config.autoWelcomeMessage());
		this.autoWelcomeMessageField.setResponder(config::setAutoWelcomeMessage);
		this.addRenderableWidget(this.autoWelcomeMessageField);

		int bottomRowY = startY + (rowHeight * 6);

		this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
			config.save();
			if (this.minecraft != null) {
				SystemToast.addOrUpdate(
					this.minecraft.getToastManager(),
					SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
					Component.literal("JoinMessages"),
					Component.literal("Settings saved.")
				);
				this.minecraft.setScreen(parent);
			}
		}).bounds(centerX - 100, bottomRowY, 97, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
			if (this.minecraft != null) {
				this.minecraft.setScreen(parent);
			}
		}).bounds(centerX + 3, bottomRowY, 97, 20).build());
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.setScreen(parent);
		}
	}

	private void updateEnabledButtonText() {
		this.enabledButton.setMessage(Component.literal(padded("Messages enabled: " + (config.enabled() ? "ON" : "OFF"))));
	}

	private void updatePrefixButtonText() {
		this.prefixButton.setMessage(Component.literal(padded("Show [JoinMessages] prefix: " + (config.showPrefix() ? "ON" : "OFF"))));
	}

	private void updateSuppressButtonText() {
		this.suppressButton.setMessage(Component.literal(padded("Suppress if server sent join/leave: " + (config.suppressIfServerMessage() ? "ON" : "OFF"))));
	}

	private void updateColorButtonText() {
		this.colorButton.setMessage(Component.literal(padded("Message color: " + config.messageColor().label())));
	}

	private void updateGamemodeMessagesButtonText() {
		this.gamemodeMessagesButton.setMessage(Component.literal(padded("Gamemode messages: " + config.gameModeMessagesMode().label())));
	}

	private void updateLimitGamemodeTrackingButtonText() {
		this.limitGamemodeTrackingButton.setMessage(Component.literal(padded("Track gamemodes below player cap: " + (config.limitGamemodeTrackingByPlayerCount() ? "ON" : "OFF"))));
	}

	private void updateJoinGamemodeNotifyButtonText() {
		this.joinGamemodeNotifyButton.setMessage(Component.literal(padded("Join notify gamemode: " + config.joinGamemodeNotifyMode().label())));
	}

	private void updateAutoWelcomeEnabledButtonText() {
		this.autoWelcomeEnabledButton.setMessage(Component.literal(padded("Auto-welcome new players: " + (config.autoWelcomeEnabled() ? "ON" : "OFF"))));
	}

	private static String padded(String text) {
		return "  " + text + "  ";
	}

	private static JoinMessagesConfig.MessageColor nextColor(JoinMessagesConfig.MessageColor current) {
		JoinMessagesConfig.MessageColor[] colors = JoinMessagesConfig.MessageColor.values();
		int nextIndex = (current.ordinal() + 1) % colors.length;
		return colors[nextIndex];
	}
}
