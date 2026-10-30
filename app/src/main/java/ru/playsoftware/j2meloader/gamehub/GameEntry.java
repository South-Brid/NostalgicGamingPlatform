package ru.playsoftware.j2meloader.gamehub;

public class GameEntry {
	static final int SOURCE_BUILTIN = 0;
	static final int SOURCE_LOCAL = 1;
	static final int SOURCE_REMOTE = 2;
	static final String PLATFORM_JAVA = "java";
	static final String PLATFORM_MRP = "mrp";

	public String id;
	public String name;
	public String category;
	public boolean icon;
	public int source;
	public String url;
	public String iconFile;
	public String sourceUrl;
	public long size;
	public String hash;
	public String platform;
	transient String pinyin = "";
	transient String initials = "";

	boolean matches(String query) {
		return query.isEmpty() || name.toLowerCase().contains(query)
				|| pinyin.contains(query) || initials.contains(query);
	}

	boolean isBuiltin() {
		return source == SOURCE_BUILTIN;
	}

	boolean isMrp() {
		return PLATFORM_MRP.equals(platform);
	}

	String platformKey() {
		return isMrp() ? PLATFORM_MRP : PLATFORM_JAVA;
	}

	static String dedupKey(String platform, String name) {
		return (PLATFORM_MRP.equals(platform) ? "mrp:" : "") + GameSources.normalize(name);
	}
}
