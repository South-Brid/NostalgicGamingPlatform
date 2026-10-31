package ru.playsoftware.j2meloader.gamehub;

import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/** Default geometry of the GameHub virtual keyboard styles (2, 7, 8, 9, 10); shared by the Java keyboard, MRP and previews. */
public final class KeyLayouts {
	public static final int STYLE_NUM = 0;
	public static final int STYLE_SOFT = 1;
	public static final int STYLE_ARROW = 2;
	public static final int STYLE_FIRE = 3;

	public static final String G_DPAD = "dpad";
	public static final String G_MENU = "menu";
	public static final String G_NUM = "num";
	public static final String G_SOFT = "soft";
	public static final String G_STAR = "star";
	public static final String G_NAV = "nav";

	private static final float PORTRAIT_EDGE_X = 0.04f;
	private static final float PORTRAIT_BOTTOM = 0.05f;
	private static final float NAV_SCALE = 0.9f;
	private static final float NAV_GAP = 0.15f;

	public static final class Key {
		public final String id;
		public final String group;
		public final String label;
		public final String sub;
		public final int style;
		public final RectF rect;

		Key(String id, String group, String label, String sub, int style, float cx, float cy, float w, float h) {
			this.id = id;
			this.group = group;
			this.label = label;
			this.sub = sub;
			this.style = style;
			this.rect = new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
		}
	}

	private KeyLayouts() {}

	public static boolean supports(int style) {
		return style == 2 || style == 7 || style == 8 || style == 9 || style == 10;
	}

	public static boolean isLandscape(int style) {
		return style == 7 || style == 8 || style == 9;
	}

	public static String groupName(String group) {
		switch (group) {
			case G_DPAD:
				return "方向键";
			case G_NUM:
				return "数字键";
			case G_SOFT:
				return "左右键";
			case G_STAR:
				return "*0#";
			case G_MENU:
				return "菜单";
			case G_NAV:
				return "导航键";
			default:
				return group;
		}
	}

	public static float portraitHeight(int style, float w, float h) {
		float u = Math.min(w, h);
		float bottom = u * PORTRAIT_BOTTOM;
		if (style == 10) return arrowsNumbersCell(w, h) * 5.1f + bottom;
		float rowH = rowHeight(w, h);
		return 3 * rowH * NAV_SCALE + rowH * NAV_GAP + 4 * rowH + bottom;
	}

	public static float rowHeight(float w, float h) {
		float u = Math.min(w, h);
		float colW = (w - 2 * u * PORTRAIT_EDGE_X) / 3;
		return Math.min(colW * 0.62f, (0.52f * h - u * PORTRAIT_BOTTOM) / (3 * NAV_SCALE + NAV_GAP + 4));
	}

	private static float arrowsNumbersCell(float w, float h) {
		float u = Math.min(w, h);
		return Math.min((w - 2 * u * PORTRAIT_EDGE_X) / 6.3f, h * 0.45f / 5.2f);
	}

	public static List<Key> layout(int style, float w, float h, RectF screen) {
		List<Key> keys = new ArrayList<>();
		if (style == 10) {
			layoutArrowsNumbers(keys, w, h);
		} else if (isLandscape(style) && screen != null) {
			layoutLandscape(keys, style, w, h, screen);
		} else {
			layoutPhone(keys, w, h);
		}
		return keys;
	}

	private static void layoutPhone(List<Key> keys, float w, float h) {
		float u = Math.min(w, h);
		float edgeX = u * PORTRAIT_EDGE_X;
		float colW = (w - 2 * edgeX) / 3;
		float rowH = rowHeight(w, h);
		float navH = rowH * NAV_SCALE;
		float top = h - portraitHeight(2, w, h);
		float c0 = edgeX + colW / 2, c1 = c0 + colW, c2 = c1 + colW;
		float r0 = top + navH / 2, r1 = r0 + navH, r2 = r1 + navH;
		keys.add(new Key("soft_l", G_NAV, "左键", null, STYLE_SOFT, c0, r0, colW, navH));
		keys.add(new Key("up", G_NAV, "↑", null, STYLE_ARROW, c1, r0, colW, navH));
		keys.add(new Key("soft_r", G_NAV, "右键", null, STYLE_SOFT, c2, r0, colW, navH));
		keys.add(new Key("left", G_NAV, "←", null, STYLE_ARROW, c0, r1, colW, navH));
		keys.add(new Key("ok", G_NAV, "确定", null, STYLE_FIRE, c1, r1, colW, navH));
		keys.add(new Key("right", G_NAV, "→", null, STYLE_ARROW, c2, r1, colW, navH));
		keys.add(new Key("menu", G_NAV, "菜单", null, STYLE_SOFT, c0, r2, colW, navH));
		keys.add(new Key("down", G_NAV, "↓", null, STYLE_ARROW, c1, r2, colW, navH));
		float numTop = top + 3 * navH + rowH * NAV_GAP;
		String[] ids = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
		for (int i = 0; i < 12; i++) {
			float cx = edgeX + colW * (i % 3) + colW / 2;
			float cy = numTop + rowH * (i / 3) + rowH / 2;
			keys.add(new Key(ids[i], G_NUM, ids[i], null, STYLE_NUM, cx, cy, colW, rowH));
		}
	}

	private static void layoutArrowsNumbers(List<Key> keys, float w, float h) {
		float u = Math.min(w, h);
		float ex = u * PORTRAIT_EDGE_X, bottom = u * PORTRAIT_BOTTOM;
		float cell = arrowsNumbersCell(w, h);
		float top = h - bottom - cell * 5.1f;
		float softW = cell * 1.9f, softH = cell * 0.85f;
		keys.add(new Key("soft_l", G_SOFT, "左键", null, STYLE_SOFT, ex + softW / 2, top + softH / 2, softW, softH));
		keys.add(new Key("menu", G_MENU, "菜单", null, STYLE_SOFT, w / 2, top + softH / 2, softW, softH));
		keys.add(new Key("soft_r", G_SOFT, "右键", null, STYLE_SOFT, w - ex - softW / 2, top + softH / 2, softW, softH));
		float bodyTop = top + cell * 1.1f;
		float half = (w - ex * 2) / 2;
		float dpadX = ex + (half - cell * 3) / 2, dpadY = bodyTop + cell * 0.5f;
		addDpad(keys, 7, dpadX + 1.5f * cell, dpadY + 1.5f * cell, cell);
		float numX = ex + half + (half - cell * 3) / 2;
		addNumbers(keys, 7, numX + 1.5f * cell, bodyTop + 1.5f * cell, cell);
		addStarRow(keys, G_NUM, numX + 1.5f * cell, bodyTop + 3.5f * cell, cell, cell);
	}

	private static void layoutLandscape(List<Key> keys, int style, float w, float h, RectF screen) {
		float u = Math.min(w, h);
		float edgeX = u * 0.06f, top = u * 0.05f, bottom = u * 0.08f;
		float leftGap = screen.left, rightGap = w - screen.right;
		float g = Math.min(leftGap, rightGap);
		float c = Math.min((g - edgeX) / 3.2f, (h - top - bottom) / 4.66f);
		c = Math.max(c, 0.12f * h);
		float s = 0.72f * c;
		float ya = h - bottom - 1.5f * c;
		float lLo = edgeX + 1.5f * c, lHi = leftGap - 1.5f * c;
		float lx = (edgeX + leftGap) / 2;
		lx = lLo > lHi ? lLo : Math.max(lLo, Math.min(lHi, lx));
		float rLo = screen.right + 1.5f * c, rHi = w - edgeX - 1.5f * c;
		float rx = (screen.right + w - edgeX) / 2;
		rx = rLo > rHi ? rHi : Math.max(rLo, Math.min(rHi, rx));

		if (style == 8) {
			keys.add(new Key("2", G_DPAD, "2", "↑", STYLE_NUM, lx, ya - c, c, c));
			keys.add(new Key("4", G_DPAD, "4", "←", STYLE_NUM, lx - c, ya, c, c));
			keys.add(new Key("6", G_DPAD, "6", "→", STYLE_NUM, lx + c, ya, c, c));
			keys.add(new Key("8", G_DPAD, "8", "↓", STYLE_NUM, lx, ya + c, c, c));
		} else {
			addDpad(keys, style == 7 ? 7 : 9, lx, ya, c);
		}
		keys.add(new Key("menu", G_MENU, "菜单", null, STYLE_SOFT, lx - c, top + s / 2, c, s));

		if (style == 8) {
			keys.add(new Key("1", G_NUM, "1", null, STYLE_NUM, rx - c, ya - c, c, c));
			keys.add(new Key("3", G_NUM, "3", null, STYLE_NUM, rx + c, ya - c, c, c));
			keys.add(new Key("7", G_NUM, "7", null, STYLE_NUM, rx - c, ya + c, c, c));
			keys.add(new Key("9", G_NUM, "9", null, STYLE_NUM, rx + c, ya + c, c, c));
			keys.add(new Key("5", G_NUM, "5", null, STYLE_NUM, rx, ya, 1.15f * c, 1.15f * c));
		} else {
			addNumbers(keys, style, rx, ya, c);
		}
		keys.add(new Key("soft_l", G_SOFT, "左键", null, STYLE_SOFT, rx - c, top + s / 2, c, s));
		keys.add(new Key("soft_r", G_SOFT, "右键", null, STYLE_SOFT, rx + c, top + s / 2, c, s));
		addStarRow(keys, G_STAR, rx, top + s + 0.12f * c + s / 2, c, s);
	}

	private static void addDpad(List<Key> keys, int style, float cx, float cy, float c) {
		keys.add(new Key("up", G_DPAD, "↑", null, STYLE_ARROW, cx, cy - c, c, c));
		keys.add(new Key("left", G_DPAD, "←", null, STYLE_ARROW, cx - c, cy, c, c));
		if (style == 7) keys.add(new Key("ok", G_DPAD, "确定", null, STYLE_FIRE, cx, cy, c, c));
		keys.add(new Key("right", G_DPAD, "→", null, STYLE_ARROW, cx + c, cy, c, c));
		keys.add(new Key("down", G_DPAD, "↓", null, STYLE_ARROW, cx, cy + c, c, c));
	}

	private static void addNumbers(List<Key> keys, int style, float cx, float cy, float c) {
		for (int i = 0; i < 9; i++) {
			String id = String.valueOf(i + 1);
			float x = cx + (i % 3 - 1) * c, y = cy + (i / 3 - 1) * c;
			if (i == 4 && style == 9) keys.add(new Key(id, G_NUM, id, "确定", STYLE_FIRE, x, y, c, c));
			else keys.add(new Key(id, G_NUM, id, null, STYLE_NUM, x, y, c, c));
		}
	}

	private static void addStarRow(List<Key> keys, String group, float cx, float cy, float w, float h) {
		keys.add(new Key("*", group, "*", null, STYLE_NUM, cx - w, cy, w, h));
		keys.add(new Key("0", group, "0", null, STYLE_NUM, cx, cy, w, h));
		keys.add(new Key("#", group, "#", null, STYLE_NUM, cx + w, cy, w, h));
	}
}
