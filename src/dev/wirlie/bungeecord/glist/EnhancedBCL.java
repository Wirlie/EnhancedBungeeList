package dev.wirlie.bungeecord.glist;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import dev.wirlie.bungeecord.glist.executor.GlistCommand;
import dev.wirlie.bungeecord.glist.executor.EBLCommand;
import dev.wirlie.bungeecord.glist.groups.GroupManager;
import dev.wirlie.bungeecord.glist.updater.UpdateNotifyListener;
import dev.wirlie.bungeecord.glist.updater.UpdateChecker;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public class EnhancedBCL extends Plugin {
	private Configuration config = null;
	private ConfigurationProvider yamlProvider = null;
	private File configFile = null;
	private GlistCommand commandExecutor = null;
	private GroupManager groupManager;
	private UpdateChecker updateChecker = null;

	public void onEnable() {
		//declaration of commons variables
		Logger logger = getLogger();
		PluginManager pm = BungeeCord.getInstance().getPluginManager();
        yamlProvider = ConfigurationProvider.getProvider(YamlConfiguration.class);

		logger.info("Enabling plugin ...");
		configFile = new File(this.getDataFolder() + File.separator + "Config.yml");

		getLogger().info("Validating Config.yml ...");
		ConfigurationValidator.validate(this);

		logger.info("Registering /ebl command ...");
		pm.registerCommand(this, new EBLCommand(this));

		registerListExecutor(true);
		groupManager = new GroupManager(this);

		if(getConfig().getBoolean("updates.check-updates", DefaultValues.getDefaultBoolean("updates.check-updates"))) {
			updateChecker = new UpdateChecker(this);
			updateChecker.getSpigotVersion(v -> {}, Throwable::printStackTrace);

			pm.registerListener(this, new UpdateNotifyListener(this));
		}
	}

	public GroupManager getGroupManager() {
		return groupManager;
	}

	public Configuration getConfig() {
		if (config == null) {
			loadConfig();
		}

		return this.config;
	}

	public void reloadConfig() {
		config = null;

		registerListExecutor(false);
		getGroupManager().loadGroups();
	}

	@SuppressWarnings("unused")
	public void saveConfig() {
		if (config != null) {
			try {
				yamlProvider.save(config, new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	private void loadConfig() {
		if (!configFile.exists()) {
			if (!configFile.getParentFile().mkdirs()) {
				getLogger().warning("Cannot make parent dir: " + configFile.getParentFile().getAbsolutePath());
			}

			try (InputStream in = getResourceAsStream("Config.yml")) {
				Files.copy(in, configFile.toPath());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		try {
			config = yamlProvider.load(new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public Configuration loadConfiguration(File file) throws IOException {
		return yamlProvider.load(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
	}

	private void registerListExecutor(boolean firstRegister) {
		if (commandExecutor != null) {
			BungeeCord.getInstance().getPluginManager().unregisterCommand(commandExecutor);
		}

		//read configuration
		String label = this.getConfig().getString("command.global-list.label", "glist");
		getLogger().info("Loading /" + label + " command ...");
		String permission = this.getConfig().getString("command.global-list.permission", null);
		List<String> aliasesList = this.getConfig().getStringList("command.global-list.aliases");
		String[] aliases = aliasesList.toArray(new String[0]);

		//prepare enhanced list executor
		commandExecutor = new GlistCommand(this, label, permission, aliases);

		if (firstRegister && label.equalsIgnoreCase("glist")) {
			//TODO: Probably this can be removed if we declare the cmd_glist plugin as soft dependency, so cmd_glist should be loaded before EnhancedBungeeList...
			//we need to wait some seconds so the original /glist can be replaced.
			BungeeCord.getInstance().getScheduler().schedule(this, () -> {
				BungeeCord.getInstance().getPluginManager().registerCommand(this, commandExecutor);
				getLogger().info("Command /glist registered...");
			}, 3, TimeUnit.SECONDS);
		} else {
			//otherwise, custom commands should no clash to other commands (except if the user defines a command of other plugin)
			BungeeCord.getInstance().getPluginManager().registerCommand(this, commandExecutor);
			getLogger().info("Command /" + label + " registered...");
		}
	}

}
