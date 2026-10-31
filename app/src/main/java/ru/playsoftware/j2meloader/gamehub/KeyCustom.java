package ru.playsoftware.j2meloader.gamehub;

import android.graphics.RectF;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Per-game, per-style key layout tweaks: group offsets/scales and overall opacity. */
public final class KeyCustom {
	public static final float MIN_SCALE = 0.6f;
	public static final float MAX_SCALE = 1.6f;
	public static final float MIN_ALPHA = 0.2f;

	public float alpha = 1f;
	public boolean hasAlpha;
	public final Map<String, float[]> groups = new HashMap<>();

	public float[] group(String name) {
		float[] g = groups.get(name);
		if (g == null) {
			g = new float[]{0, 0, 1};
			groups.put(name, g);
		}
		return g;
	}

	public void setAlpha(float value) {
		alpha = Math.max(MIN_ALPHA, Math.min(1f, value));
		hasAlpha = true;
	}

	public void reset() {
		alpha = 1f;
		hasAlpha = false;
		groups.clear();
	}

	public KeyCustom copy() {
		KeyCustom c = new KeyCustom();
		c.alpha = alpha;
		c.hasAlpha = hasAlpha;
		for (Map.Entry<String, float[]> e : groups.entrySet()) c.groups.put(e.getKey(), e.getValue().clone());
		return c;
	}

	public void apply(List<KeyLayouts.Key> keys, float w, float h) {
		float u = Math.min(w, h);
		Map<String, RectF> boxes = new HashMap<>();
		for (KeyLayouts.Key k : keys) {
			RectF box = boxes.get(k.group);
			if (box == null) boxes.put(k.group, new RectF(k.rect));
			else box.union(k.rect);
		}
		for (Map.Entry<String, RectF> e : boxes.entrySet()) {
			float[] g = groups.get(e.getKey());
			if (g == null) continue;
			float scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, g[2]));
			float cx = e.getValue().centerX(), cy = e.getValue().centerY();
			RectF result = null;
			for (KeyLayouts.Key k : keys) {
				if (!k.group.equals(e.getKey())) continue;
				float kx = cx + (k.rect.centerX() - cx) * scale + g[0] * u;
				float ky = cy + (k.rect.centerY() - cy) * scale + g[1] * u;
				float kw = k.rect.width() * scale, kh = k.rect.height() * scale;
				k.rect.set(kx - kw / 2, ky - kh / 2, kx + kw / 2, ky + kh / 2);
				if (result == null) result = new RectF(k.rect);
				else result.union(k.rect);
			}
			if (result == null) continue;
			float shiftX = 0, shiftY = 0;
			if (result.left < 0) shiftX = -result.left;
			else if (result.right > w) shiftX = w - result.right;
			if (result.top < 0) shiftY = -result.top;
			else if (result.bottom > h) shiftY = h - result.bottom;
			if (shiftX != 0 || shiftY != 0) {
				for (KeyLayouts.Key k : keys) {
					if (k.group.equals(e.getKey())) k.rect.offset(shiftX, shiftY);
				}
			}
		}
	}

	public static KeyCustom load(File file) {
		KeyCustom c = new KeyCustom();
		if (file == null || !file.isFile()) return c;
		try (InputStream in = new FileInputStream(file)) {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			int n;
			while ((n = in.read(buf)) > 0) bytes.write(buf, 0, n);
			JSONObject json = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
			if (json.has("alpha")) c.setAlpha((float) json.getDouble("alpha"));
			JSONObject groups = json.optJSONObject("groups");
			if (groups != null) {
				Iterator<String> names = groups.keys();
				while (names.hasNext()) {
					String name = names.next();
					JSONArray a = groups.getJSONArray(name);
					c.groups.put(name, new float[]{(float) a.getDouble(0), (float) a.getDouble(1), (float) a.getDouble(2)});
				}
			}
			return c;
		} catch (Exception e) {
			return new KeyCustom();
		}
	}

	public void save(File file) {
		try {
			JSONObject json = new JSONObject();
			json.put("v", 1);
			if (hasAlpha) json.put("alpha", alpha);
			JSONObject groups = new JSONObject();
			for (Map.Entry<String, float[]> e : this.groups.entrySet()) {
				float[] g = e.getValue();
				if (g[0] == 0 && g[1] == 0 && g[2] == 1) continue;
				groups.put(e.getKey(), new JSONArray().put(g[0]).put(g[1]).put(g[2]));
			}
			json.put("groups", groups);
			File parent = file.getParentFile();
			if (parent != null) {
				//noinspection ResultOfMethodCallIgnored
				parent.mkdirs();
			}
			try (FileOutputStream out = new FileOutputStream(file)) {
				out.write(json.toString().getBytes(StandardCharsets.UTF_8));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
