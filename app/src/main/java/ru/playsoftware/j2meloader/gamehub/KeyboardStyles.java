package ru.playsoftware.j2meloader.gamehub;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.preference.PreferenceManager;

import ru.playsoftware.j2meloader.R;

public final class KeyboardStyles {
	static final int PHONE = 1;
	public static final int PHONE_ARROWS = 2;
	public static final int LANDSCAPE = 7;
	public static final int LANDSCAPE_SIMPLE = 8;
	public static final int LANDSCAPE_SPLIT = 9;
	public static final int ARROWS_NUMBERS = 10;
	private static final String PREF_DEFAULT = "gamehub_vk_type";
	private static final String PREF_MRP_DEFAULT = "gamehub_mrp_vk_type";
	private static final String[] GROUP_TITLES = {"竖屏", "横屏", "悬浮按键"};
	private static final String[] GROUP_DESCS = {"竖着拿手机，画面在上，按键在下", "横着拿手机，画面在中间，按键在两侧",
			"按键半透明盖在画面上，跟随游戏方向"};
	private static final int[][] GROUPS = {{1, 2}, {7, 9, 8}, {3, 4, 5, 6}};
	private static final int[][] MRP_GROUPS = {{2, 10}, {7, 9, 8}};
	private static final String[] SHORT = {"自定义", "竖屏·手机", "竖屏·方向", "悬浮·数字左", "悬浮·方向左", "悬浮·数字",
			"悬浮·方向", "横屏·标准", "横屏·精简", "横屏·分离", "竖屏·方向+数字"};
	private static final String[] TITLES = {"自定义", "手机键盘", "手机键盘 · 方向键版", "数字在左 · 方向在右",
			"方向在左 · 数字在右", "仅数字键", "仅方向键", "标准", "精简", "方向键分离", "方向 + 数字"};
	private static final String[] DESCS = {"", "九宫格在下\n最像原来的手机", "左右键在 ↑ 两侧\n下面完整九宫格",
			"按键在画面两侧\n画面最大", "方向键在左手边\n适合习惯左手操作", "只显示九宫格\n适合棋牌、益智", "只显示方向键\n界面最简洁",
			"方向键和数字键在拇指位置\n功能键在上方",
			"左边 2/4/6/8，右边 1/3/5/7/9\n功能键和 *0# 在上方",
			"左边方向键，右边九宫格 5 当确定\n功能键在上方",
			"左边方向键和确定\n右边完整九宫格"};

	private KeyboardStyles() {}

	static int getDefault(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getInt(PREF_DEFAULT, PHONE);
	}

	static void setDefault(Context context, int type) {
		PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(PREF_DEFAULT, type).apply();
	}

	static int getMrpDefault(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getInt(PREF_MRP_DEFAULT, PHONE_ARROWS);
	}

	static void setMrpDefault(Context context, int type) {
		PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(PREF_MRP_DEFAULT, type).apply();
	}

	public static boolean isLandscape(int type) {
		return type == LANDSCAPE || type == LANDSCAPE_SIMPLE || type == LANDSCAPE_SPLIT;
	}

	static String shortName(int type) {
		return type >= 0 && type < SHORT.length ? SHORT[type] : SHORT[PHONE];
	}

	public static String title(int type) {
		if (type < 0 || type >= TITLES.length) type = PHONE;
		if (type == ARROWS_NUMBERS) return GROUP_TITLES[0] + " · " + TITLES[type];
		for (int g = 0; g < GROUPS.length; g++) {
			for (int t : GROUPS[g]) if (t == type) return GROUP_TITLES[g] + " · " + TITLES[type];
		}
		return TITLES[type];
	}

	public static void pick(Context context, String title, String subtitle, int current, Consumer<Integer> onPicked) {
		pick(context, title, subtitle, current, false, onPicked);
	}

	public static void pick(Context context, String title, String subtitle, int current, boolean mrp,
							Consumer<Integer> onPicked) {
		int[][] groups = mrp ? MRP_GROUPS : GROUPS;
		float dp = context.getResources().getDisplayMetrics().density;
		int accent = ContextCompat.getColor(context, R.color.hub_accent);
		int surface = ContextCompat.getColor(context, R.color.hub_surface_high);
		int stroke = ContextCompat.getColor(context, R.color.hub_stroke);
		int text = ContextCompat.getColor(context, R.color.hub_text);
		int text2 = ContextCompat.getColor(context, R.color.hub_text_secondary);

		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (20 * dp);
		root.setPadding(pad, pad, pad, (int) (12 * dp));

		TextView titleView = new TextView(context);
		titleView.setText(title);
		titleView.setTextColor(text);
		titleView.setTextSize(19);
		titleView.getPaint().setFakeBoldText(true);
		root.addView(titleView);

		TextView subView = new TextView(context);
		subView.setText(subtitle);
		subView.setTextColor(text2);
		subView.setTextSize(13);
		subView.setPadding(0, (int) (4 * dp), 0, (int) (4 * dp));
		root.addView(subView);

		AlertDialog dialog = new AlertDialog.Builder(context, R.style.HubDialog)
				.setNegativeButton("取消", null)
				.create();

		for (int g = 0; g < groups.length; g++) {
			LinearLayout header = new LinearLayout(context);
			header.setOrientation(LinearLayout.HORIZONTAL);
			header.setGravity(Gravity.CENTER_VERTICAL);
			header.setPadding((int) (5 * dp), (int) (16 * dp), 0, (int) (4 * dp));
			View bar = new View(context);
			GradientDrawable barBg = new GradientDrawable();
			barBg.setCornerRadius(2 * dp);
			barBg.setColor(accent);
			bar.setBackground(barBg);
			header.addView(bar, new LinearLayout.LayoutParams((int) (4 * dp), (int) (16 * dp)));
			TextView groupTitle = new TextView(context);
			groupTitle.setText(GROUP_TITLES[g]);
			groupTitle.setTextColor(text);
			groupTitle.setTextSize(16);
			groupTitle.getPaint().setFakeBoldText(true);
			groupTitle.setPadding((int) (8 * dp), 0, (int) (8 * dp), 0);
			header.addView(groupTitle);
			TextView groupDesc = new TextView(context);
			groupDesc.setText(GROUP_DESCS[g]);
			groupDesc.setTextColor(text2);
			groupDesc.setTextSize(11.5f);
			header.addView(groupDesc);
			root.addView(header);

			int[] types = groups[g];
			LinearLayout row = null;
			for (int i = 0; i < types.length; i++) {
				if (i % 2 == 0) {
					row = new LinearLayout(context);
					row.setOrientation(LinearLayout.HORIZONTAL);
					root.addView(row);
				}
				row.addView(option(context, dialog, types[i], types[i] == current, onPicked, accent, surface, stroke, text, text2, dp),
						cellParams(dp));
			}
			if (types.length % 2 == 1) row.addView(new View(context), cellParams(dp));
		}

		ScrollView scroll = new ScrollView(context);
		scroll.addView(root);
		dialog.setView(scroll);
		dialog.show();
	}

	private static LinearLayout.LayoutParams cellParams(float dp) {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		int m = (int) (5 * dp);
		lp.setMargins(m, m, m, m);
		return lp;
	}

	private static View option(Context context, AlertDialog dialog, int type, boolean selected, Consumer<Integer> onPicked,
							   int accent, int surface, int stroke, int text, int text2, float dp) {
		LinearLayout option = new LinearLayout(context);
		option.setOrientation(LinearLayout.VERTICAL);
		option.setGravity(Gravity.CENTER_HORIZONTAL);
		int p = (int) (10 * dp);
		option.setPadding(p, p, p, p);
		GradientDrawable bg = new GradientDrawable();
		bg.setCornerRadius(16 * dp);
		bg.setColor(surface);
		bg.setStroke((int) ((selected ? 2 : 1) * dp), selected ? accent : stroke);
		option.setBackground(bg);

		option.addView(new KeyboardPreviewView(context, type),
				new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) ((isLandscape(type) ? 90 : 120) * dp)));

		TextView name = new TextView(context);
		name.setText(selected ? "✓ " + TITLES[type] : TITLES[type]);
		name.setTextColor(selected ? accent : text);
		name.setTextSize(13.5f);
		name.getPaint().setFakeBoldText(true);
		name.setGravity(Gravity.CENTER);
		name.setPadding(0, (int) (8 * dp), 0, 0);
		option.addView(name);

		TextView desc = new TextView(context);
		desc.setText(DESCS[type]);
		desc.setTextColor(text2);
		desc.setTextSize(11);
		desc.setGravity(Gravity.CENTER);
		desc.setPadding(0, (int) (2 * dp), 0, 0);
		option.addView(desc);

		option.setOnClickListener(v -> {
			dialog.dismiss();
			onPicked.accept(type);
		});
		return option;
	}
}
