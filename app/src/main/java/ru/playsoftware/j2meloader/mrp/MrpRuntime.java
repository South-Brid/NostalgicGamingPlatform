package ru.playsoftware.j2meloader.mrp;

import android.content.Context;
import android.content.res.AssetManager;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import ru.playsoftware.j2meloader.util.IOUtils;

/** The emulated phone file system: files/mrp/ is the shared runtime, each game runs in its own files/mrp/games/<id>/. */
public final class MrpRuntime {
	private static final Pattern LEGACY_GAME = Pattern.compile("[0-9a-f]{12}\\.mrp");
	private static final String ASSETS = "mrp";
	private static final int ASSETS_VERSION = 1;
	private static final String MARKER = ".assets_version";

	private MrpRuntime() {}

	public static File root(Context context) {
		return new File(context.getFilesDir(), "mrp");
	}

	public static File gameRoot(Context context, String id) {
		return new File(new File(root(context), "games"), id);
	}

	private static File legacyGame(Context context, String id) {
		return new File(new File(root(context), "mythroad"), id + ".mrp");
	}

	private static File seededMarker(Context context, String id) {
		return new File(new File(root(context), "legacy_seeded"), id);
	}

	/** Copies the shared runtime out of assets if needed and returns the game's own root (its phone file system). */
	static synchronized File prepare(Context context, String id) throws IOException {
		File root = root(context);
		File marker = new File(root, MARKER);
		if (!marker.isFile() || readInt(marker) != ASSETS_VERSION) {
			copyAssets(context.getAssets(), ASSETS, root);
			try (OutputStream out = new FileOutputStream(marker)) {
				out.write(String.valueOf(ASSETS_VERSION).getBytes());
			}
		}
		File shared = new File(root, "mythroad");
		//noinspection ResultOfMethodCallIgnored
		shared.mkdirs();
		File gameRoot = gameRoot(context, id);
		File gameMythroad = new File(gameRoot, "mythroad");
		//noinspection ResultOfMethodCallIgnored
		gameMythroad.mkdirs();
		link(new File(root, "cfunction.ext"), new File(gameRoot, "cfunction.ext"));
		String[] entries = context.getAssets().list(ASSETS + "/mythroad");
		if (entries == null) entries = new String[0];
		for (String entry : entries) link(new File(shared, entry), new File(gameMythroad, entry));
		if (!seededMarker(context, id).exists()) {
			Set<String> skip = new HashSet<>(Arrays.asList(entries));
			skip.add("games");
			File[] legacy = shared.listFiles();
			if (legacy != null) {
				for (File f : legacy) {
					if (skip.contains(f.getName()) || LEGACY_GAME.matcher(f.getName()).matches()) continue;
					copyMissing(f, new File(gameMythroad, f.getName()));
				}
			}
			//noinspection ResultOfMethodCallIgnored
			legacyGame(context, id).delete();
			markLegacySeeded(context, id);
		}
		return gameRoot;
	}

	/** Puts the game into the game's mythroad/ and returns the name to start it with (relative to mythroad/). */
	static String installGame(Context context, File source, String id) throws IOException {
		String name = id + ".mrp";
		File target = new File(new File(gameRoot(context, id), "mythroad"), name);
		if (!target.isFile() || target.length() != source.length()) {
			try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(target)) {
				IOUtils.copy(in, out);
			}
		}
		return name;
	}

	public static void markLegacySeeded(Context context, String id) {
		File file = seededMarker(context, id);
		//noinspection ResultOfMethodCallIgnored
		file.getParentFile().mkdirs();
		try {
			//noinspection ResultOfMethodCallIgnored
			file.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/** Deletes everything of the game (saves included); symlinks to the shared runtime are removed, never followed. */
	public static void clearGame(Context context, String id) {
		deleteTree(gameRoot(context, id));
		//noinspection ResultOfMethodCallIgnored
		keyboardFile(context, id).delete();
		File[] customs = new File(root(context), "keys").listFiles((d, n) -> n.startsWith(id + "."));
		if (customs != null) {
			//noinspection ResultOfMethodCallIgnored
			for (File f : customs) f.delete();
		}
		//noinspection ResultOfMethodCallIgnored
		legacyGame(context, id).delete();
		markLegacySeeded(context, id);
	}

	private static boolean isSymlink(File file) {
		try {
			return OsConstants.S_ISLNK(Os.lstat(file.getPath()).st_mode);
		} catch (ErrnoException e) {
			return false;
		}
	}

	private static void deleteTree(File file) {
		if (!isSymlink(file) && file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) deleteTree(child);
			}
		}
		//noinspection ResultOfMethodCallIgnored
		file.delete();
	}

	private static void link(File target, File link) throws IOException {
		if (link.exists()) return;
		//noinspection ResultOfMethodCallIgnored
		link.delete();
		try {
			Os.symlink(target.getAbsolutePath(), link.getPath());
		} catch (ErrnoException e) {
			copyMissing(target, link);
		}
	}

	private static void copyMissing(File source, File target) throws IOException {
		if (source.isDirectory()) {
			//noinspection ResultOfMethodCallIgnored
			target.mkdirs();
			File[] children = source.listFiles();
			if (children != null) {
				for (File child : children) copyMissing(child, new File(target, child.getName()));
			}
		} else if (!target.exists()) {
			try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(target)) {
				IOUtils.copy(in, out);
			}
		}
	}

	public static File customFile(Context context, String id, int style) {
		return new File(new File(root(context), "keys"), id + "." + style + ".json");
	}

	private static File keyboardFile(Context context, String id) {
		return new File(new File(root(context), "keys"), id);
	}

	public static int getKeyboard(Context context, String id, int fallback) {
		File file = keyboardFile(context, id);
		if (!file.isFile()) return fallback;
		int v = readInt(file);
		return v >= 0 ? v : fallback;
	}

	public static void setKeyboard(Context context, String id, int type) {
		File file = keyboardFile(context, id);
		//noinspection ResultOfMethodCallIgnored
		file.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(file)) {
			out.write(String.valueOf(type).getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void clearKeyboards(Context context) {
		File[] files = new File(root(context), "keys").listFiles();
		if (files != null) {
			//noinspection ResultOfMethodCallIgnored
			for (File f : files) f.delete();
		}
	}

	private static void copyAssets(AssetManager assets, String path, File target) throws IOException {
		String[] children = assets.list(path);
		if (children != null && children.length > 0) {
			//noinspection ResultOfMethodCallIgnored
			target.mkdirs();
			for (String child : children) copyAssets(assets, path + "/" + child, new File(target, child));
			return;
		}
		try (InputStream in = assets.open(path); OutputStream out = new FileOutputStream(target)) {
			IOUtils.copy(in, out);
		}
	}

	private static int readInt(File file) {
		try (InputStream in = new FileInputStream(file)) {
			return Integer.parseInt(new String(IOUtils.toByteArray(in)).trim());
		} catch (Exception e) {
			return -1;
		}
	}
}
