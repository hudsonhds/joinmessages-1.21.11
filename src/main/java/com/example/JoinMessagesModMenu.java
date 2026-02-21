package com.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.api.UpdateChannel;
import com.terraformersmc.modmenu.api.UpdateChecker;
import com.terraformersmc.modmenu.api.UpdateInfo;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.SharedConstants;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;

public class JoinMessagesModMenu implements ModMenuApi {
	private static final String MODRINTH_PROJECT_SLUG = "joinleave-messages";
	private static final URI MODRINTH_VERSIONS_URI = URI.create("https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_SLUG + "/version");
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
	private static final UpdateChecker UPDATE_CHECKER = new ModrinthUpdateChecker();

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new JoinMessagesConfigScreen(parent, JoinMessagesConfig.getInstance());
	}

	@Override
	public UpdateChecker getUpdateChecker() {
		return UPDATE_CHECKER;
	}

	private static final class ModrinthUpdateChecker implements UpdateChecker {
		@Override
		public UpdateInfo checkForUpdates() {
			String currentVersion = FabricLoader.getInstance()
				.getModContainer(JoinMessagesMod.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("0.0.0");

			try {
				UpdateChannel preferredChannel = UpdateChannel.getUserPreference();
				String mcVersion = SharedConstants.getCurrentVersion().name();
				URI uri = createVersionUri(mcVersion);
				HttpRequest request = HttpRequest.newBuilder(uri)
					.header("Accept", "application/json")
					.header("User-Agent", JoinMessagesMod.MOD_ID + "/" + currentVersion)
					.GET()
					.build();
				HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() != 200) {
					JoinMessagesMod.LOGGER.warn("Modrinth update check failed with HTTP {}", response.statusCode());
					return null;
				}

				JsonElement parsed = JsonParser.parseString(response.body());
				if (!parsed.isJsonArray()) {
					return null;
				}

				VersionData latest = findLatestCompatibleVersion(parsed.getAsJsonArray(), preferredChannel);
				if (latest == null || !isUpdateAvailable(currentVersion, latest.versionNumber())) {
					return null;
				}

				return new ModrinthUpdateInfo(latest.versionId(), latest.channel());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return null;
			} catch (IOException | IllegalStateException e) {
				JoinMessagesMod.LOGGER.warn("Failed Modrinth update check", e);
				return null;
			}
		}

		private static URI createVersionUri(String mcVersion) {
			String loaders = URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8);
			String gameVersions = URLEncoder.encode("[\"" + mcVersion + "\"]", StandardCharsets.UTF_8);
			return URI.create(MODRINTH_VERSIONS_URI + "?loaders=" + loaders + "&game_versions=" + gameVersions);
		}

		private static VersionData findLatestCompatibleVersion(JsonArray versions, UpdateChannel preferredChannel) {
			VersionData newest = null;
			for (JsonElement element : versions) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject object = element.getAsJsonObject();

				String versionId = getString(object, "id");
				String versionNumber = getString(object, "version_number");
				String versionType = getString(object, "version_type");
				Instant publishedAt = parseInstant(getString(object, "date_published"));
				if (versionId == null || versionNumber == null || publishedAt == null) {
					continue;
				}

				UpdateChannel channel = toUpdateChannel(versionType);
				if (!isAllowedChannel(channel, preferredChannel)) {
					continue;
				}

				VersionData candidate = new VersionData(versionId, versionNumber, channel, publishedAt);
				if (newest == null || candidate.publishedAt().isAfter(newest.publishedAt())) {
					newest = candidate;
				}
			}
			return newest;
		}

		private static String getString(JsonObject object, String key) {
			JsonElement element = object.get(key);
			if (element == null || !element.isJsonPrimitive()) {
				return null;
			}
			return element.getAsString();
		}

		private static Instant parseInstant(String value) {
			if (value == null || value.isBlank()) {
				return null;
			}
			try {
				return Instant.parse(value);
			} catch (DateTimeParseException e) {
				return null;
			}
		}

		private static UpdateChannel toUpdateChannel(String value) {
			if (value == null) {
				return UpdateChannel.RELEASE;
			}
			return switch (value.toLowerCase()) {
				case "alpha" -> UpdateChannel.ALPHA;
				case "beta" -> UpdateChannel.BETA;
				default -> UpdateChannel.RELEASE;
			};
		}

		private static boolean isAllowedChannel(UpdateChannel versionChannel, UpdateChannel preferredChannel) {
			return switch (preferredChannel) {
				case RELEASE -> versionChannel == UpdateChannel.RELEASE;
				case BETA -> versionChannel == UpdateChannel.BETA || versionChannel == UpdateChannel.RELEASE;
				case ALPHA -> true;
			};
		}

		private static boolean isUpdateAvailable(String currentVersion, String latestVersion) {
			SemanticVersion currentSemantic = parseSemanticVersion(currentVersion);
			SemanticVersion latestSemantic = parseSemanticVersion(latestVersion);
			if (currentSemantic != null && latestSemantic != null) {
				return latestSemantic.compareTo(currentSemantic) > 0;
			}
			return !normalizeVersion(currentVersion).equals(normalizeVersion(latestVersion));
		}

		private static SemanticVersion parseSemanticVersion(String version) {
			if (version == null || version.isBlank()) {
				return null;
			}
			String cleaned = version.trim();
			if (cleaned.startsWith("v") || cleaned.startsWith("V")) {
				cleaned = cleaned.substring(1);
			}
			try {
				return SemanticVersion.parse(cleaned);
			} catch (VersionParsingException e) {
				return null;
			}
		}

		private static String normalizeVersion(String version) {
			if (version == null) {
				return "";
			}
			String normalized = version.trim().toLowerCase();
			if (normalized.startsWith("v")) {
				return normalized.substring(1);
			}
			return normalized;
		}
	}

	private record VersionData(String versionId, String versionNumber, UpdateChannel channel, Instant publishedAt) {
	}

	private record ModrinthUpdateInfo(String versionId, UpdateChannel channel) implements UpdateInfo {
		@Override
		public boolean isUpdateAvailable() {
			return true;
		}

		@Override
		public String getDownloadLink() {
			return "https://modrinth.com/mod/" + MODRINTH_PROJECT_SLUG + "/version/" + versionId;
		}

		@Override
		public UpdateChannel getUpdateChannel() {
			return channel;
		}
	}
}
