package com.example;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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

	public JoinMessagesConfigScreen(Screen parent, JoinMessagesConfig config) {
		super(Component.literal("Join Messages Config"));
		this.parent = parent;
		this.config = config;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int startY = this.height / 4 + 24;

		this.enabledButton = Button.builder(Component.empty(), button -> {
			config.setEnabled(!config.enabled());
			updateEnabledButtonText();
		}).bounds(centerX - 100, startY, 200, 20).build();
		this.addRenderableWidget(this.enabledButton);
		updateEnabledButtonText();

		this.prefixButton = Button.builder(Component.empty(), button -> {
			config.setShowPrefix(!config.showPrefix());
			updatePrefixButtonText();
		}).bounds(centerX - 100, startY + 24, 200, 20).build();
		this.addRenderableWidget(this.prefixButton);
		updatePrefixButtonText();

		this.suppressButton = Button.builder(Component.empty(), button -> {
			config.setSuppressIfServerMessage(!config.suppressIfServerMessage());
			updateSuppressButtonText();
		}).bounds(centerX - 100, startY + 48, 200, 20).build();
		this.addRenderableWidget(this.suppressButton);
		updateSuppressButtonText();

		this.colorButton = Button.builder(Component.empty(), button -> {
			config.setMessageColor(nextColor(config.messageColor()));
			updateColorButtonText();
		}).bounds(centerX - 100, startY + 72, 200, 20).build();
		this.addRenderableWidget(this.colorButton);
		updateColorButtonText();

		this.gamemodeMessagesButton = Button.builder(Component.empty(), button -> {
			config.setGameModeMessagesMode(config.gameModeMessagesMode().next());
			updateGamemodeMessagesButtonText();
		}).bounds(centerX - 100, startY + 96, 200, 20).build();
		this.addRenderableWidget(this.gamemodeMessagesButton);
		updateGamemodeMessagesButtonText();

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
		}).bounds(centerX - 100, startY + 128, 97, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
			if (this.minecraft != null) {
				this.minecraft.setScreen(parent);
			}
		}).bounds(centerX + 3, startY + 128, 97, 20).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.setScreen(parent);
		}
	}

	private void updateEnabledButtonText() {
		this.enabledButton.setMessage(Component.literal("Messages enabled: " + (config.enabled() ? "ON" : "OFF")));
	}

	private void updatePrefixButtonText() {
		this.prefixButton.setMessage(Component.literal("Show [JoinMessages] prefix: " + (config.showPrefix() ? "ON" : "OFF")));
	}

	private void updateSuppressButtonText() {
		this.suppressButton.setMessage(Component.literal("Suppress if server sent join/leave: " + (config.suppressIfServerMessage() ? "ON" : "OFF")));
	}

	private void updateColorButtonText() {
		this.colorButton.setMessage(Component.literal("Message color: " + config.messageColor().label()));
	}

	private void updateGamemodeMessagesButtonText() {
		this.gamemodeMessagesButton.setMessage(Component.literal("Gamemode messages: " + config.gameModeMessagesMode().label()));
	}

	private static JoinMessagesConfig.MessageColor nextColor(JoinMessagesConfig.MessageColor current) {
		JoinMessagesConfig.MessageColor[] colors = JoinMessagesConfig.MessageColor.values();
		int nextIndex = (current.ordinal() + 1) % colors.length;
		return colors[nextIndex];
	}
}
