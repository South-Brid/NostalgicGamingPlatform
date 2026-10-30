package ru.playsoftware.j2meloader.mrp;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import ru.playsoftware.j2meloader.util.IOUtils;

/** The emulated phone file system: files/mrp/ holds cfunction.ext and mythroad/ (fonts, plugins, games, saves). */
public final class MrpRuntime {
	private static final String ASSETS = "mrp";
	private static final int ASSETS_VERSION = 1;
	private static final String MARKER = ".assets_version";

	private MrpRuntime() {}

	public static File root(Context context) {
		return new File(context.getFilesDir(), "mrp");
	}

	static synchronized File prepare(Context context) throws IOException {
		File root = root(context);
		File marker = new File(root, MARKER);
		if (!marker.isFile() || readInt(marker) != ASSETS_VERSION) {
			copyAssets(context.getAssets(), ASSETS, root);
			try (OutputStream out = new FileOutputStream(marker)) {
				out.write(String.valueOf(ASSETS_VERSION).getBytes());
			}
		}
		//noinspection ResultOfMethodCallIgnored
		new File(root, "mythroad").mkdirs();
		return root;
	}

	/** Puts the game into mythroad/ and returns the name to start it with (relative to mythroad/). */
	static String installGame(Context context, File source, String id) throws IOException {
		String name = id + ".mrp";
		File target = new File(new File(root(context), "mythroad"), name);
		if (!target.isFile() || target.length() != source.length()) {
			try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(target)) {
				IOUtils.copy(in, out);
			}
		}
		return name;
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
