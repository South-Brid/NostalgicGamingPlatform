package ru.playsoftware.j2meloader.gamehub;

import static ru.playsoftware.j2meloader.util.Constants.PREF_DEFAULT_PROFILE;

import android.app.Application;
import android.content.Context;

import androidx.preference.PreferenceManager;

import androidx.core.util.Consumer;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import ru.playsoftware.j2meloader.util.FileUtils;
import ru.woesss.j2me.installer.AppInstaller;

final class GameLauncher {
	private static final int LAYOUT_SIGNATURE = 0x564B4C00;
	private static final int LAYOUT_TYPE = 3;

	private GameLauncher() {}

	static File appDir(GameEntry game) {
		return new File(Config.getAppDir(), game.id);
	}

	private static File configDir(GameEntry game) {
		return new File(Config.getConfigsDir(), game.id);
	}

	/** Convert the bundled jar into a runnable app (slow, call off the main thread) */
	static void install(Application app, AppRepository repository, GameEntry game,
						Consumer<Integer> downloadProgress) throws IOException {
		File jar = GameSources.obtainJar(app, game, downloadProgress);
		try {
			new AppInstaller(-1, app, repository).installLocalJar(jar, game.id, game.name);
		} finally {
			if (game.isBuiltin()) {
				//noinspection ResultOfMethodCallIgnored
				jar.delete();
			}
		}
	}

	static void uninstall(GameEntry game) {
		FileUtils.deleteDirectory(appDir(game));
		FileUtils.deleteDirectory(configDir(game));
	}

	static void start(Context context, GameEntry game, boolean showSettings) {
		ensureConfig(context, game);
		GameLibrary.addRecent(context, game.id);
		Config.startApp(context, game.name, appDir(game).getPath(), showSettings);
	}

	/** Create a default config so the game starts directly instead of opening the settings screen */
	private static File ensureConfig(Context context, GameEntry game) {
		File configDir = configDir(game);
		if (new File(configDir, Config.MIDLET_CONFIG_FILE).exists()) {
			ProfileModel old = ProfilesManager.loadConfig(configDir);
			if (old != null && old.vkBgColor == 0xD0D0D0 && old.screenBackgroundColor == 0xD0D0D0) {
				applyDarkColors(old);
				ProfilesManager.saveConfig(old);
			}
			return configDir;
		}
		//noinspection ResultOfMethodCallIgnored
		configDir.mkdirs();
		//noinspection ResultOfMethodCallIgnored
		new File(Config.getDataDir(), game.id).mkdirs();
		String defProfile = PreferenceManager.getDefaultSharedPreferences(context)
				.getString(PREF_DEFAULT_PROFILE, null);
		if (defProfile != null) {
			FileUtils.copyFiles(new File(Config.getProfilesDir(), defProfile), configDir, null);
		}
		if (ProfilesManager.loadConfig(configDir) == null) {
			ProfileModel params = new ProfileModel(configDir);
			params.vkType = KeyboardStyles.getDefault(context);
			applyDarkColors(params);
			ProfilesManager.saveConfig(params);
		}
		return configDir;
	}

	private static void applyDarkColors(ProfileModel params) {
		params.screenBackgroundColor = 0x000000;
		params.vkBgColor = 0x272A3F;
		params.vkFgColor = 0xF2F3F8;
		params.vkBgColorSelected = 0xFFC24B;
		params.vkFgColorSelected = 0x1A1408;
		params.vkOutlineColor = 0x3A3E58;
	}

	static int getKeyboard(Context context, GameEntry game) {
		File configDir = configDir(game);
		File layout = new File(configDir, Config.MIDLET_KEY_LAYOUT_FILE);
		if (layout.isFile()) {
			try (DataInputStream in = new DataInputStream(new FileInputStream(layout))) {
				if (in.readInt() == LAYOUT_SIGNATURE) {
					in.readInt();
					if (in.readInt() == LAYOUT_TYPE) {
						in.readInt();
						return in.readUnsignedByte();
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		ProfileModel params = ProfilesManager.loadConfig(configDir);
		return params != null ? params.vkType : KeyboardStyles.getDefault(context);
	}

	static void setKeyboard(Context context, GameEntry game, int type) {
		applyKeyboard(ensureConfig(context, game), type);
	}

	/** Apply to every game that already has a config; the others pick up the default on first start */
	static void setKeyboardForAll(List<GameEntry> games, int type) {
		for (GameEntry game : games) {
			File configDir = configDir(game);
			if (new File(configDir, Config.MIDLET_CONFIG_FILE).exists()) applyKeyboard(configDir, type);
		}
	}

	private static void applyKeyboard(File configDir, int type) {
		ProfileModel params = ProfilesManager.loadConfig(configDir);
		if (params == null) return;
		params.vkType = type;
		ProfilesManager.saveConfig(params);
		//noinspection ResultOfMethodCallIgnored
		new File(configDir, Config.MIDLET_KEY_LAYOUT_FILE).delete();
	}
}
