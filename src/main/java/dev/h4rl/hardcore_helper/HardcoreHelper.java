package dev.h4rl.hardcore_helper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;
import org.quiltmc.qsl.command.api.CommandRegistrationCallback;
import org.quiltmc.qsl.entity.event.api.LivingEntityDeathCallback;
import org.quiltmc.qsl.lifecycle.api.event.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

public class HardcoreHelper implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("Hardcore Helper");
	private static final int COUNTDOWN_TIME = 30 * 20; // 30 seconds in ticks (20 ticks per second)
	private static long remainingTime = COUNTDOWN_TIME;

	@Override
	public void onInitialize(ModContainer mod) {

		String configDirPath = QuiltLoader.getConfigDir().toAbsolutePath().toString();
		String modConfigDir = mod.metadata().id();
		Path completeDir = Paths.get(configDirPath, modConfigDir);
		Path saviorDir = Paths.get(completeDir.toString(), "SPARETHEWORLD");

		File file = new File(saviorDir.toString());

		if (file.exists()) {
			if (!file.delete()) {
				LOGGER.error("[hardcore_helper] Failed to delete Savior file.");
			}
		}

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("spare-world")
					.requires(source -> source.hasPermissionLevel(4))
					.executes(context -> {
						ServerCommandSource source = context.getSource();
						source.sendFeedback(Text.literal("§9§lSparing the world..§f"), true);
						writeSave(completeDir);
						return 1; // Return value indicating success
					}));
		});

		ServerLifecycleEvents.READY.register(server -> {
			MinecraftServer minecraftServer = server;
			LivingEntityDeathCallback.EVENT.register((entity, damage_source) -> {
				if (entity instanceof PlayerEntity player) {
					writeToChat(String.format("%s died! Deleting world and closing server..",
							player.getName().getString()), minecraftServer);
					countDown(minecraftServer, saviorDir);
					LOGGER.info("saviorDir: {}", saviorDir.toString());

					writeDeath(player.getName().getString(), completeDir);
					return;
				}
			});
		});
	}

	static public void writeToChat(String message, MinecraftServer server) {
		Text chat_message = Text.literal(message);
		server.getPlayerManager().broadcastSystemMessage(chat_message, false);
	}

	static public void writeSave(Path file_path) {
		if (!file_path.toFile().exists()) {
			// Attempt to create the folder if it doesn't exist
			boolean wasCreated = file_path.toFile().mkdirs();
			if (!wasCreated) {
				LOGGER.error("[hardcore_helper] Failed to create path: {}", file_path.toString());
				return;
			}
		}

		// Specify the path for the new file within the config directory
		File spareFile = new File(file_path.toString(), "SPARETHEWORLD");

		if (!spareFile.exists()) {
			try (FileWriter writer = new FileWriter(spareFile)) {
				writer.write("Spare us our lord and saviour!");

			} catch (IOException e) {
				LOGGER.error("[hardcore_helper] Failed to write to file: {}", e.getMessage());
			}
		} else {
			LOGGER.error("[hardcore_helper] Something went wrong, is hardcore_helper_d not running?");
			return;
		}

		LOGGER.info("[hardcore_helper] Successfully wrote to: {}!", spareFile.toString());

	}

	static public void writeDeath(String playerName, Path completeDir) {
		String nameFormat = String.format("This stupid idiot: %s. DIED, what a fucking loser retard!", playerName);

		if (!completeDir.toFile().exists()) {
			// Attempt to create the folder if it doesn't exist
			boolean wasCreated = completeDir.toFile().mkdirs();
			if (!wasCreated) {
				LOGGER.error("[hardcore_helper] Failed to create path: {}", completeDir.toString());
				return;
			}
		}

		// Specify the path for the new file within the config directory
		File configFile = new File(completeDir.toString(), "BitchassWhoDied");

		if (!configFile.exists()) {
			try (FileWriter writer = new FileWriter(configFile)) {
				writer.write(nameFormat);

			} catch (IOException e) {
				LOGGER.error("[hardcore_helper] Failed to write to file: {}", e.getMessage());
			}
		} else {
			LOGGER.error("[hardcore_helper] Something went wrong, is hardcore_helper_d not running?");
			return;
		}

		LOGGER.info("[hardcore_helper] Successfully wrote to: {}!", configFile.toString());

	}

	public void countDown(MinecraftServer server, Path savior_path) {
		writeToChat("§c§lThis is your shot at stopping the world from being deleted!§f", server);

		File file = new File(savior_path.toString());

		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		executor.scheduleAtFixedRate(() -> {
			if (remainingTime <= 0) {
				executor.shutdown(); // Stop the executor when countdown is over
				if (file.exists()) {
					try {
						String content = Files.readString(savior_path);
						if (content == "Spare us our lord and saviour!") {
							LOGGER.info("Spared the world.");
							return;
						}
					} catch (IOException e) {
						LOGGER.error("[hardcore_helper] Can't read file: {}", e.getMessage());
					}
				}
				server.close();
				return;
			}
			writeToChat(String.format("§9Counting down: §f%d", remainingTime / 20), server);
			remainingTime -= 20; // Decrement the countdown by one tick
		}, 0, 1, TimeUnit.SECONDS); // Start immediately, run every 20 seconds

	}
}
//
