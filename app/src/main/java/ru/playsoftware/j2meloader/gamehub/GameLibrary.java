package ru.playsoftware.j2meloader.gamehub;

import android.content.Context;
import android.icu.text.Transliterator;
import android.os.Build;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.mrp.MrpRuntime;

final class GameLibrary {
	static final String ALL = "全部";
	private static final String PREF_RECENT = "gamehub_recent";
	private static final int MAX_RECENT = 12;
	private static final List<String> ORDER = Arrays.asList(
			"仙侠修真", "武侠江湖", "三国历史", "西幻冒险", "角色扮演", "动作射击", "策略经营", "赛车体育",
			"休闲益智", "棋牌", "其他");

	private GameLibrary() {}

	static List<GameEntry> load(Context context) throws IOException {
		List<GameEntry> games = new ArrayList<>();
		try (Reader reader = new InputStreamReader(
				context.getAssets().open("games/games.json"), StandardCharsets.UTF_8)) {
			games.addAll(new Gson().<List<GameEntry>>fromJson(reader, new TypeToken<List<GameEntry>>() {}.getType()));
		} catch (IOException ignored) {
		}
		Set<String> ids = new HashSet<>();
		for (GameEntry g : games) ids.add(g.id);
		for (GameEntry g : GameSources.loadAll(context)) {
			if (ids.add(g.id)) games.add(g);
		}
		Collections.sort(games, (a, b) -> a.name.compareTo(b.name));
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			Transliterator t = Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower");
			for (GameEntry g : games) {
				String latin = t.transliterate(g.name);
				g.pinyin = latin.replaceAll("[^a-z0-9]", "");
				StringBuilder sb = new StringBuilder();
				for (String s : latin.split("[^a-z0-9]+")) {
					if (!s.isEmpty()) sb.append(s.charAt(0));
				}
				g.initials = sb.toString();
			}
		}
		return games;
	}

	static List<String> categories(List<GameEntry> games) {
		Set<String> present = new HashSet<>();
		for (GameEntry g : games) present.add(g.category);
		Set<String> result = new LinkedHashSet<>();
		result.add(ALL);
		for (String c : ORDER) if (present.contains(c)) result.add(c);
		result.addAll(present);
		return new ArrayList<>(result);
	}

	static List<String> recentIds(Context context) {
		String saved = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_RECENT, "");
		List<String> ids = new ArrayList<>();
		for (String id : saved.split(",")) if (!id.isEmpty()) ids.add(id);
		return ids;
	}

	static void addRecent(Context context, String id) {
		List<String> ids = recentIds(context);
		ids.remove(id);
		ids.add(0, id);
		while (ids.size() > MAX_RECENT) ids.remove(ids.size() - 1);
		PreferenceManager.getDefaultSharedPreferences(context).edit()
				.putString(PREF_RECENT, TextUtils.join(",", ids)).apply();
	}

	static Set<String> installedIds(Context context) {
		Set<String> ids = new HashSet<>();
		File[] dirs = new File(Config.getAppDir()).listFiles();
		if (dirs != null) {
			for (File d : dirs) {
				if (new File(d, Config.MIDLET_DEX_FILE).isFile()) ids.add(d.getName());
			}
		}
		File[] mrps = new File(MrpRuntime.root(context), "mythroad").listFiles((d, n) -> n.endsWith(".mrp"));
		if (mrps != null) {
			for (File f : mrps) ids.add(f.getName().substring(0, f.getName().length() - 4));
		}
		return ids;
	}
}
