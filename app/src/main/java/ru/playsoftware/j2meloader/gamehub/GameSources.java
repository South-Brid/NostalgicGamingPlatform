package ru.playsoftware.j2meloader.gamehub;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.core.util.Consumer;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.model.LocalFileHeader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import ru.playsoftware.j2meloader.util.FileUtils;
import ru.playsoftware.j2meloader.util.IOUtils;

/**
 * Extra game sources besides the games bundled in the APK:
 * - local: jar files imported from the phone
 * - remote: a JSON catalog published at some URL (see AGENTS.md for the format)
 */
final class GameSources {
	private static final String PREF_SOURCES = "gamehub_sources";
	private static final Gson GSON = new Gson();
	private static final Pattern ICON = Pattern.compile("^MIDlet-Icon\\s*:\\s*(.+)$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
	private static final Pattern MIDLET1 = Pattern.compile("^MIDlet-1\\s*:\\s*[^,]*,\\s*([^,]*),", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

	static final class Source {
		String url;
		String name;
		int count;
		long updated;
	}

	static final class Catalog {
		String name;
		List<CatalogGame> games;
	}

	static final class CatalogGame {
		String id;
		String name;
		String category;
		String url;
		String icon;
		long size;
	}

	private GameSources() {}

	private static File dir(Context context, String name) {
		File dir = new File(context.getFilesDir(), "gamehub/" + name);
		//noinspection ResultOfMethodCallIgnored
		dir.mkdirs();
		return dir;
	}

	private static File localIndex(Context context) {
		return new File(dir(context, "local"), "index.json");
	}

	private static File catalogFile(Context context, String url) {
		return new File(dir(context, "sources"), md5(url) + ".json");
	}


	static List<GameEntry> loadAll(Context context) {
		List<GameEntry> games = new ArrayList<>(loadLocal(context));
		for (Source s : getSources(context)) games.addAll(loadRemote(context, s.url));
		return games;
	}

	private static List<GameEntry> loadLocal(Context context) {
		File index = localIndex(context);
		if (!index.isFile()) return new ArrayList<>();
		try {
			List<GameEntry> list = GSON.fromJson(FileUtils.getText(index.getPath()),
					new TypeToken<List<GameEntry>>() {}.getType());
			return list != null ? list : new ArrayList<>();
		} catch (Exception e) {
			e.printStackTrace();
			return new ArrayList<>();
		}
	}

	private static List<GameEntry> loadRemote(Context context, String sourceUrl) {
		List<GameEntry> result = new ArrayList<>();
		File file = catalogFile(context, sourceUrl);
		if (!file.isFile()) return result;
		Catalog catalog;
		try {
			catalog = GSON.fromJson(FileUtils.getText(file.getPath()), Catalog.class);
		} catch (Exception e) {
			e.printStackTrace();
			return result;
		}
		if (catalog == null || catalog.games == null) return result;
		File icons = dir(context, "icons");
		for (CatalogGame g : catalog.games) {
			if (g.name == null || g.url == null) continue;
			GameEntry e = new GameEntry();
			e.source = GameEntry.SOURCE_REMOTE;
			e.sourceUrl = sourceUrl;
			e.id = "r_" + md5(sourceUrl + "|" + (g.id != null ? g.id : g.url)).substring(0, 16);
			e.name = g.name.trim();
			e.category = g.category != null && !g.category.isEmpty() ? g.category : GameCategorizer.classify(e.name);
			e.url = resolve(sourceUrl, g.url);
			e.platform = e.url.toLowerCase().endsWith(".mrp") ? GameEntry.PLATFORM_MRP : GameEntry.PLATFORM_JAVA;
			e.size = g.size;
			File icon = new File(icons, e.id + ".png");
			e.iconFile = icon.getPath();
			e.icon = icon.isFile();
			result.add(e);
		}
		return result;
	}


	interface ImportListener {
		void onProgress(int percent, ImportResult result, String current);
	}

	static final class ImportResult {
		int imported;
		int duplicates;
		int invalid;
		int repaired;
	}

	/** Imports jar files and zip packs; in a zip the folder a jar sits in is used as its category. */
	static final class Importer {
		private final Context context;
		private final ImportListener listener;
		private final AtomicBoolean cancelled;
		private final List<GameEntry> local;
		private final Set<String> names = new HashSet<>();
		private final Set<String> hashes = new HashSet<>();
		private final ImportResult result = new ImportResult();
		private final File localDir;
		private final File iconDir;
		private final File tmp;
		private long totalBytes;
		private CountingInputStream counter;
		private long doneBytes;
		private int lastPercent = -1;
		private int unsaved;

		Importer(Context context, Set<String> existingKeys, ImportListener listener, AtomicBoolean cancelled) {
			this.context = context;
			this.listener = listener;
			this.cancelled = cancelled;
			this.local = loadLocal(context);
			names.addAll(existingKeys);
			for (GameEntry g : local) {
				names.add(GameEntry.dedupKey(g.platform, g.name));
				if (g.hash != null) hashes.add(g.hash);
			}
			localDir = dir(context, "local");
			iconDir = dir(context, "icons");
			tmp = new File(context.getCacheDir(), "import.jar");
		}

		ImportResult run(List<Uri> uris) throws IOException {
			for (Uri uri : uris) totalBytes += size(uri);
			try {
				for (Uri uri : uris) {
					if (cancelled.get()) break;
					String name = displayName(context, uri);
					try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
						if (raw == null) continue;
						counter = new CountingInputStream(new BufferedInputStream(raw, 64 * 1024));
						String lower = name.toLowerCase();
						if (lower.endsWith(".zip")) {
							importZip(counter);
						} else {
							importGame(counter, GameCategorizer.cleanName(stripExt(name)), null, lower.endsWith(".mrp"));
						}
					}
					doneBytes += counter.count;
					counter = null;
				}
			} finally {
				saveLocal(context, local);
				//noinspection ResultOfMethodCallIgnored
				tmp.delete();
			}
			return result;
		}

		private void importZip(InputStream in) throws IOException {
			ZipInputStream zip = new ZipInputStream(in, StandardCharsets.ISO_8859_1);
			LocalFileHeader header;
			while (!cancelled.get() && (header = zip.getNextEntry()) != null) {
				if (header.isDirectory()) continue;
				String path = decodeName(header);
				String lower = path.toLowerCase();
				if (lower.endsWith(".jar") || lower.endsWith(".mrp")) {
					String name = GameCategorizer.cleanName(stripExt(baseName(path)));
					importGame(zip, name, path, lower.endsWith(".mrp"));
				} else if (lower.endsWith(".zip")) {
					importZip(new NonClosingInputStream(zip));
				}
			}
		}

		private void importGame(InputStream in, String name, String path, boolean mrp) throws IOException {
			MessageDigest digest = md5Digest();
			try (OutputStream out = new FileOutputStream(tmp)) {
				byte[] buf = new byte[32 * 1024];
				int n;
				while ((n = in.read(buf)) > 0) {
					out.write(buf, 0, n);
					digest.update(buf, 0, n);
				}
			}
			byte[] icon = null;
			if (mrp) {
				String title = readMrpTitle(tmp);
				if (title == null) {
					result.invalid++;
					report(name);
					return;
				}
				if (!title.isEmpty()) name = title;
			}
			String platform = mrp ? GameEntry.PLATFORM_MRP : GameEntry.PLATFORM_JAVA;
			String hash = String.format("%032x", new BigInteger(1, digest.digest()));
			String key = GameEntry.dedupKey(platform, name);
			GameEntry sameFile = null;
			if (hashes.contains(hash)) {
				for (GameEntry g : local) if (hash.equals(g.hash)) sameFile = g;
			}
			if (names.contains(key) || (hashes.contains(hash)
					&& (sameFile == null || GameEntry.dedupKey(sameFile.platform, sameFile.name).equals(key)))) {
				result.duplicates++;
				report(name);
				return;
			}
			if (sameFile != null) {
				removeGameFiles(sameFile);
				local.remove(sameFile);
				names.remove(GameEntry.dedupKey(sameFile.platform, sameFile.name));
				result.repaired++;
			}
			if (!mrp) {
				try {
					icon = readMidlet(tmp);
				} catch (IOException e) {
					result.invalid++;
					report(name);
					return;
				}
			}
			String id = md5(mrp ? "mrp:" + name : name).substring(0, 12);
			removeById(local, id);
			File jar = new File(localDir, id + (mrp ? ".mrp" : ".jar"));
			if (!tmp.renameTo(jar)) FileUtils.copyFileUsingChannel(tmp, jar);
			GameEntry e = new GameEntry();
			e.id = id;
			e.name = name;
			e.platform = platform;
			e.category = path != null ? categoryFor(name, path) : GameCategorizer.classify(name);
			e.source = GameEntry.SOURCE_LOCAL;
			e.url = jar.getPath();
			e.size = jar.length();
			e.hash = hash;
			File iconFile = new File(iconDir, id + ".png");
			if (icon != null) writeBytes(iconFile, icon);
			e.iconFile = iconFile.getPath();
			e.icon = icon != null;
			local.add(e);
			names.add(key);
			hashes.add(hash);
			result.imported++;
			if (++unsaved >= 200) {
				saveLocal(context, local);
				unsaved = 0;
			}
			report(name);
		}

		private void report(String current) {
			long done = doneBytes + (counter != null ? counter.count : 0);
			int percent = totalBytes > 0 ? (int) Math.min(100, done * 100 / totalBytes) : -1;
			if (percent != lastPercent || (result.imported + result.duplicates) % 10 == 0) {
				lastPercent = percent;
				listener.onProgress(percent, result, current);
			}
		}

		private long size(Uri uri) {
			if ("content".equals(uri.getScheme())) {
				try (Cursor c = context.getContentResolver().query(uri,
						new String[]{OpenableColumns.SIZE}, null, null, null)) {
					if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
				} catch (Exception ignored) {
				}
			} else if (uri.getPath() != null) {
				return new File(uri.getPath()).length();
			}
			return 0;
		}
	}

	private static final String[][] FOLDER_CATEGORIES = {
			{"动作", "动作射击"}, {"射击", "动作射击"}, {"策略", "策略经营"}, {"赛车", "赛车体育"}, {"体育", "赛车体育"},
			{"益智", "休闲益智"}, {"休闲", "休闲益智"}, {"棋牌", "棋牌"}, {"rpg", "角色扮演"}, {"角色扮演", "角色扮演"}};

	/**
	 * The folder a jar sits in decides its category: our own category names and custom folder names are
	 * used as-is, common names like "RPG"/"动作" are mapped, collection folders ("xxx合集") fall back to
	 * guessing from the game name.
	 */
	private static String categoryFor(String name, String path) {
		String[] parts = path.split("/");
		String folder = parts.length > 1 ? parts[parts.length - 2].trim() : "";
		if (GameCategorizer.KNOWN.contains(folder)) return folder;
		for (String[] fc : FOLDER_CATEGORIES) {
			if (folder.equalsIgnoreCase(fc[0])) {
				if (!fc[1].equals("角色扮演")) return fc[1];
				String guess = GameCategorizer.classify(name);
				return guess.equals("其他") ? fc[1] : guess;
			}
		}
		if (folder.isEmpty() || folder.contains("合集") || folder.contains("游戏") || folder.equals("汉化")) {
			return GameCategorizer.classify(name);
		}
		return folder;
	}

	private static String decodeName(LocalFileHeader header) {
		byte[] raw = header.getFileName().getBytes(StandardCharsets.ISO_8859_1);
		if (header.isFileNameUTF8Encoded()) return new String(raw, StandardCharsets.UTF_8);
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(raw)).toString();
		} catch (CharacterCodingException e) {
			return new String(raw, Charset.forName("GBK"));
		}
	}

	private static void removeGameFiles(GameEntry game) {
		if (game.url != null) {
			//noinspection ResultOfMethodCallIgnored
			new File(game.url).delete();
		}
		if (game.iconFile != null) {
			//noinspection ResultOfMethodCallIgnored
			new File(game.iconFile).delete();
		}
	}

	static String normalize(String name) {
		return name.trim().toLowerCase().replaceAll("\\s*\\(\\d+\\)$", "").replaceAll("\\s+", "");
	}

	private static String baseName(String path) {
		int slash = path.lastIndexOf('/');
		return slash >= 0 ? path.substring(slash + 1) : path;
	}

	private static String stripExt(String name) {
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	private static MessageDigest md5Digest() {
		try {
			return MessageDigest.getInstance("MD5");
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static final class CountingInputStream extends FilterInputStream {
		long count;

		CountingInputStream(InputStream in) {
			super(in);
		}

		@Override
		public int read() throws IOException {
			int b = super.read();
			if (b >= 0) count++;
			return b;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int n = super.read(b, off, len);
			if (n > 0) count += n;
			return n;
		}

		@Override
		public long skip(long n) throws IOException {
			long s = super.skip(n);
			count += s;
			return s;
		}
	}

	private static final class NonClosingInputStream extends FilterInputStream {
		NonClosingInputStream(InputStream in) {
			super(in);
		}

		@Override
		public void close() {
		}
	}

	static void deleteGame(Context context, GameEntry game) {
		//noinspection ResultOfMethodCallIgnored
		jarFile(context, game).delete();
		if (game.source == GameEntry.SOURCE_LOCAL) {
			List<GameEntry> local = loadLocal(context);
			removeById(local, game.id);
			saveLocal(context, local);
			if (game.iconFile != null) {
				//noinspection ResultOfMethodCallIgnored
				new File(game.iconFile).delete();
			}
		}
	}

	private static void saveLocal(Context context, List<GameEntry> local) {
		writeBytes(localIndex(context), GSON.toJson(local).getBytes(StandardCharsets.UTF_8));
	}

	private static void removeById(List<GameEntry> list, String id) {
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.get(i).id.equals(id)) list.remove(i);
		}
	}

	private static String displayName(Context context, Uri uri) {
		if ("content".equals(uri.getScheme())) {
			try (Cursor c = context.getContentResolver().query(uri,
					new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
				if (c != null && c.moveToFirst() && c.getString(0) != null) return c.getString(0);
			} catch (Exception ignored) {
			}
		}
		String last = uri.getLastPathSegment();
		if (last == null) return "未命名游戏";
		int slash = last.lastIndexOf('/');
		return slash >= 0 ? last.substring(slash + 1) : last;
	}

	static String readMrpTitle(File file) {
		byte[] head = new byte[0x34];
		try (InputStream in = new FileInputStream(file)) {
			if (in.read(head) != head.length) return null;
		} catch (IOException e) {
			return null;
		}
		if (head[0] != 'M' || head[1] != 'R' || head[2] != 'P' || head[3] != 'G') return null;
		int end = 0x1C;
		while (end < head.length && head[end] != 0) end++;
		String title = new String(head, 0x1C, end - 0x1C, Charset.forName("GBK"))
				.replaceAll("[\\p{Cntrl}\\uFFFD]", "").trim();
		return GameCategorizer.cleanName(title);
	}

	/** Validates a MIDlet jar and returns its icon (PNG bytes) or null */
	private static byte[] readMidlet(File jar) throws IOException {
		try (ZipFile zip = new ZipFile(jar)) {
			ZipEntry mfEntry = zip.getEntry("META-INF/MANIFEST.MF");
			if (mfEntry == null) throw new IOException("不是有效的 J2ME 游戏（没有 MANIFEST）");
			String mf;
			try (InputStream in = zip.getInputStream(mfEntry)) {
				mf = new String(IOUtils.toByteArray(in), StandardCharsets.UTF_8);
			}
			if (!Pattern.compile("^MIDlet-1\\s*:", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE).matcher(mf).find()) {
				throw new IOException("不是有效的 J2ME 游戏（没有 MIDlet-1）");
			}
			Matcher m = ICON.matcher(mf);
			if (!m.find()) {
				m = MIDLET1.matcher(mf);
				if (!m.find()) return null;
			}
			String path = m.group(1).trim();
			while (path.startsWith("/")) path = path.substring(1);
			ZipEntry iconEntry = path.isEmpty() ? null : zip.getEntry(path);
			if (iconEntry == null) return null;
			try (InputStream in = zip.getInputStream(iconEntry)) {
				byte[] data = IOUtils.toByteArray(in);
				return data.length > 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' ? data : null;
			}
		}
	}


	static List<Source> getSources(Context context) {
		String json = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_SOURCES, "[]");
		try {
			List<Source> list = GSON.fromJson(json, new TypeToken<List<Source>>() {}.getType());
			return list != null ? list : new ArrayList<>();
		} catch (Exception e) {
			return new ArrayList<>();
		}
	}

	private static void saveSources(Context context, List<Source> sources) {
		PreferenceManager.getDefaultSharedPreferences(context).edit()
				.putString(PREF_SOURCES, GSON.toJson(sources)).apply();
	}

	/** Downloads (or re-downloads) a catalog and its icons. Slow, call off the main thread. */
	static Source refreshSource(Context context, String url) throws IOException {
		url = url.trim();
		byte[] data = download(url, null);
		Catalog catalog;
		try {
			catalog = GSON.fromJson(new String(data, StandardCharsets.UTF_8), Catalog.class);
		} catch (Exception e) {
			throw new IOException("游戏清单格式不正确：" + e.getMessage());
		}
		if (catalog == null || catalog.games == null || catalog.games.isEmpty()) {
			throw new IOException("游戏清单里没有游戏");
		}
		writeBytes(catalogFile(context, url), data);

		List<Source> sources = getSources(context);
		Source source = null;
		for (Source s : sources) if (s.url.equals(url)) source = s;
		if (source == null) {
			source = new Source();
			source.url = url;
			sources.add(source);
		}
		source.name = catalog.name != null && !catalog.name.isEmpty() ? catalog.name : Uri.parse(url).getHost();
		source.count = catalog.games.size();
		source.updated = System.currentTimeMillis();
		saveSources(context, sources);

		File icons = dir(context, "icons");
		for (GameEntry g : loadRemote(context, url)) {
			CatalogGame raw = null;
			for (CatalogGame c : catalog.games) {
				if (g.url.equals(resolve(url, c.url))) raw = c;
			}
			if (raw == null || raw.icon == null || raw.icon.isEmpty()) continue;
			File icon = new File(icons, g.id + ".png");
			if (icon.isFile()) continue;
			try {
				writeBytes(icon, download(resolve(url, raw.icon), null));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return source;
	}

	static void removeSource(Context context, String url) {
		for (GameEntry g : loadRemote(context, url)) {
			//noinspection ResultOfMethodCallIgnored
			jarFile(context, g).delete();
		}
		//noinspection ResultOfMethodCallIgnored
		catalogFile(context, url).delete();
		List<Source> sources = getSources(context);
		for (int i = sources.size() - 1; i >= 0; i--) {
			if (sources.get(i).url.equals(url)) sources.remove(i);
		}
		saveSources(context, sources);
	}


	static File jarFile(Context context, GameEntry game) {
		if (game.source == GameEntry.SOURCE_LOCAL) return new File(game.url);
		return new File(dir(context, "remote"), game.id + (game.isMrp() ? ".mrp" : ".jar"));
	}

	/**
	 * Returns a jar file for the game, downloading it first for remote games.
	 * Builtin games are copied from assets into a temp file which the caller should delete.
	 */
	static File obtainJar(Context context, GameEntry game, Consumer<Integer> progress) throws IOException {
		if (game.isBuiltin()) {
			File tmp = new File(context.getCacheDir(), "gamehub_" + game.id + ".jar");
			try (InputStream in = context.getAssets().open("games/" + game.id + ".jar");
				 OutputStream out = new FileOutputStream(tmp)) {
				IOUtils.copy(in, out);
			}
			return tmp;
		}
		File jar = jarFile(context, game);
		if (game.source == GameEntry.SOURCE_REMOTE && !jar.isFile()) {
			File part = new File(jar.getPath() + ".part");
			writeBytes(part, download(game.url, progress));
			if (!part.renameTo(jar)) throw new IOException("无法保存游戏文件");
		}
		if (!jar.isFile()) throw new IOException("游戏文件不存在：" + jar.getName());
		return jar;
	}


	private static byte[] download(String url, Consumer<Integer> progress) throws IOException {
		HttpURLConnection conn = null;
		try {
			for (int redirects = 0; ; redirects++) {
				conn = (HttpURLConnection) new URL(url).openConnection();
				conn.setConnectTimeout(15000);
				conn.setReadTimeout(60000);
				conn.setInstanceFollowRedirects(true);
				int code = conn.getResponseCode();
				if ((code == 301 || code == 302 || code == 303 || code == 307 || code == 308) && redirects < 5) {
					url = new URL(new URL(url), conn.getHeaderField("Location")).toString();
					conn.disconnect();
					continue;
				}
				if (code != HttpURLConnection.HTTP_OK) throw new IOException("下载失败（HTTP " + code + "）：" + url);
				break;
			}
			long total = conn.getContentLength();
			try (InputStream in = conn.getInputStream();
				 java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
				byte[] buf = new byte[16 * 1024];
				int n, lastPercent = -1;
				while ((n = in.read(buf)) > 0) {
					out.write(buf, 0, n);
					if (progress != null && total > 0) {
						int percent = (int) (out.size() * 100 / total);
						if (percent != lastPercent) progress.accept(lastPercent = percent);
					}
				}
				return out.toByteArray();
			}
		} finally {
			if (conn != null) conn.disconnect();
		}
	}

	private static String resolve(String base, String ref) {
		try {
			return new URL(new URL(base), ref).toString();
		} catch (IOException e) {
			return ref;
		}
	}

	private static void writeBytes(File file, byte[] data) {
		try (OutputStream out = new FileOutputStream(file)) {
			out.write(data);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static String md5(String s) {
		try {
			byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
			return String.format("%032x", new BigInteger(1, d));
		} catch (Exception e) {
			return Integer.toHexString(s.hashCode());
		}
	}
}
