package ru.playsoftware.j2meloader.gamehub;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.preference.PreferenceManager;

import ru.playsoftware.j2meloader.R;

final class KeyboardStyles {
	static final int PHONE = 1;
	private static final String PREF_DEFAULT = "gamehub_vk_type";
	private static final int[] CHOICES = {1, 2, 7, 8, 3, 4, 5, 6};
	private static final String[] SHORT = {"自定义", "上下", "上下·方向", "左右", "左右·反转", "数字键", "方向键", "横屏",
			"横屏·精简"};
	private static final String[] TITLES = {"自定义", "上下 · 手机键盘", "上下 · 方向键版", "左右 · 数字在左",
			"左右 · 方向在左", "仅数字键", "仅方向键", "横屏 · 画面居中", "横屏 · 精简版"};
	private static final String[] DESCS = {"", "画面在上，九宫格在下\n最像原来的手机", "九宫格中间换成方向键\n适合动作类游戏",
			"按键在画面两侧\n画面最大", "方向键在左手边\n适合习惯左手操作", "只显示九宫格\n适合棋牌、益智", "只显示方向键\n界面最简洁",
			"横着拿手机，画面在中间\n左边方向键，右边功能数字键",
			"左边只有方向键和确定\n右边去掉 2/4/5/6/8，按键更大"};

	private KeyboardStyles() {}

	static int getDefault(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getInt(PREF_DEFAULT, PHONE);
	}

	static void setDefault(Context context, int type) {
		PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(PREF_DEFAULT, type).apply();
	}

	static String shortName(int type) {
		return type >= 0 && type < SHORT.length ? SHORT[type] : SHORT[PHONE];
	}

	static String title(int type) {
		return type >= 0 && type < TITLES.length ? TITLES[type] : TITLES[PHONE];
	}

	static void pick(Context context, String title, String subtitle, int current, Consumer<Integer> onPicked) {
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
		subView.setPadding(0, (int) (4 * dp), 0, (int) (12 * dp));
		root.addView(subView);

		AlertDialog dialog = new AlertDialog.Builder(context, R.style.HubDialog)
				.setNegativeButton("取消", null)
				.create();

		LinearLayout row = null;
		for (int i = 0; i < CHOICES.length; i++) {
			int type = CHOICES[i];
			if (i % 2 == 0) {
				row = new LinearLayout(context);
				row.setOrientation(LinearLayout.HORIZONTAL);
				root.addView(row);
			}
			LinearLayout option = new LinearLayout(context);
			option.setOrientation(LinearLayout.VERTICAL);
			option.setGravity(Gravity.CENTER_HORIZONTAL);
			int p = (int) (10 * dp);
			option.setPadding(p, p, p, p);
			GradientDrawable bg = new GradientDrawable();
			bg.setCornerRadius(16 * dp);
			bg.setColor(surface);
			boolean selected = type == current;
			bg.setStroke((int) ((selected ? 2 : 1) * dp), selected ? accent : stroke);
			option.setBackground(bg);

			option.addView(new KeyboardPreviewView(context, type),
					new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (120 * dp)));

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
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
			int m = (int) (5 * dp);
			lp.setMargins(m, m, m, m);
			row.addView(option, lp);
		}

		ScrollView scroll = new ScrollView(context);
		scroll.addView(root);
		dialog.setView(scroll);
		dialog.show();
	}
}
