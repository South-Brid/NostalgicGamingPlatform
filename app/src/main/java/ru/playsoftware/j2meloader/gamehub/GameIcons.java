package ru.playsoftware.j2meloader.gamehub;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;

final class GameIcons {
	private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
		@Override
		protected int sizeOf(String key, Bitmap value) {
			return value.getByteCount();
		}
	};

	private GameIcons() {}

	static void bind(ImageView icon, TextView letter, GameEntry game) {
		Bitmap bitmap = game.icon ? load(icon.getContext(), game) : null;
		if (bitmap != null) {
			BitmapDrawable drawable = new BitmapDrawable(icon.getResources(), bitmap);
			drawable.setFilterBitmap(false);
			icon.setImageDrawable(drawable);
			icon.setVisibility(View.VISIBLE);
			letter.setVisibility(View.GONE);
			return;
		}
		icon.setVisibility(View.GONE);
		letter.setVisibility(View.VISIBLE);
		letter.setText(firstChar(game.name));
		int[] colors = gradient(game.category);
		GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
		bg.setCornerRadius(20 * letter.getResources().getDisplayMetrics().density);
		letter.setBackground(bg);
	}

	private static Bitmap load(Context context, GameEntry game) {
		Bitmap bitmap = CACHE.get(game.id);
		if (bitmap != null) return bitmap;
		if (game.isBuiltin()) {
			try (InputStream in = context.getAssets().open("games/icons/" + game.id + ".png")) {
				bitmap = BitmapFactory.decodeStream(in);
			} catch (IOException e) {
				return null;
			}
		} else if (game.iconFile != null) {
			bitmap = BitmapFactory.decodeFile(game.iconFile);
		}
		if (bitmap != null) CACHE.put(game.id, bitmap);
		return bitmap;
	}

	private static String firstChar(String name) {
		String s = name.replaceAll("^[\\(（\\[【].*?[\\)）\\]】]", "");
		if (s.isEmpty()) s = name;
		return s.substring(0, 1);
	}

	static int[] gradient(String category) {
		switch (category) {
			case "仙侠修真": return new int[]{0xFF8B5CF6, 0xFF4C1D95};
			case "武侠江湖": return new int[]{0xFFEF4444, 0xFF7F1D1D};
			case "三国历史": return new int[]{0xFFF59E0B, 0xFF92400E};
			case "西幻冒险": return new int[]{0xFF3B82F6, 0xFF1E3A8A};
			case "角色扮演": return new int[]{0xFFEC4899, 0xFF831843};
			case "动作射击": return new int[]{0xFFF97316, 0xFF7C2D12};
			case "策略经营": return new int[]{0xFF84CC16, 0xFF365314};
			case "赛车体育": return new int[]{0xFF06B6D4, 0xFF164E63};
			case "休闲益智": return new int[]{0xFF22C55E, 0xFF14532D};
			case "棋牌": return new int[]{0xFF14B8A6, 0xFF134E4A};
			default: return new int[]{0xFF64748B, 0xFF1E293B};
		}
	}
}
