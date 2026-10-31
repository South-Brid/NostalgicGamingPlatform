package ru.playsoftware.j2meloader.gamehub;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import ru.playsoftware.j2meloader.R;

/** Floating panel shown while dragging key groups: size of the selected group, overall opacity, reset/cancel/save. */
public class KeyEditPanel extends LinearLayout {
	public interface Callback {
		void onScale(float scale);

		void onAlpha(float alpha);

		void onReset();

		void onCancel();

		void onSave();
	}

	private static final int SCALE_MIN = 60;
	private static final int ALPHA_MIN = 20;

	private final TextView selected;
	private final SeekBar scaleBar;
	private final SeekBar alphaBar;
	private final TextView scaleValue;
	private final TextView alphaValue;
	private Callback callback;
	private boolean silent;

	public KeyEditPanel(Context context) {
		super(context);
		float dp = context.getResources().getDisplayMetrics().density;
		int accent = ContextCompat.getColor(context, R.color.hub_accent);
		int text = ContextCompat.getColor(context, R.color.hub_text);
		int text2 = ContextCompat.getColor(context, R.color.hub_text_secondary);
		setOrientation(VERTICAL);
		int pad = (int) (14 * dp);
		setPadding(pad, (int) (10 * dp), pad, (int) (10 * dp));
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(0xE6141622);
		bg.setCornerRadius(16 * dp);
		bg.setStroke((int) dp, 0xFF3A3E58);
		setBackground(bg);
		setClickable(true);

		TextView hint = label(context, "拖动按键调整位置，点按选中一组", 12, text2);
		addView(hint);
		selected = label(context, "未选中", 15, accent);
		selected.getPaint().setFakeBoldText(true);
		selected.setPadding(0, (int) (4 * dp), 0, 0);
		addView(selected);

		scaleValue = label(context, "100%", 12, text);
		scaleBar = seekRow(context, "大小", scaleValue, 100, text, dp);
		scaleBar.setEnabled(false);
		scaleBar.setOnSeekBarChangeListener(new Listener(true));
		alphaValue = label(context, "100%", 12, text);
		alphaBar = seekRow(context, "透明度", alphaValue, 80, text, dp);
		alphaBar.setOnSeekBarChangeListener(new Listener(false));

		LinearLayout buttons = new LinearLayout(context);
		buttons.setOrientation(HORIZONTAL);
		buttons.setPadding(0, (int) (6 * dp), 0, 0);
		buttons.addView(button(context, "恢复默认", text, dp, v -> {
			if (callback != null) callback.onReset();
		}), buttonParams(dp));
		buttons.addView(button(context, "取消", text, dp, v -> {
			if (callback != null) callback.onCancel();
		}), buttonParams(dp));
		buttons.addView(button(context, "保存", accent, dp, v -> {
			if (callback != null) callback.onSave();
		}), buttonParams(dp));
		addView(buttons);
	}

	private static TextView label(Context context, String s, float size, int color) {
		TextView t = new TextView(context);
		t.setText(s);
		t.setTextSize(size);
		t.setTextColor(color);
		return t;
	}

	private SeekBar seekRow(Context context, String name, TextView value, int max, int color, float dp) {
		LinearLayout row = new LinearLayout(context);
		row.setOrientation(HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		TextView title = label(context, name, 13, color);
		row.addView(title, new LayoutParams((int) (52 * dp), ViewGroup.LayoutParams.WRAP_CONTENT));
		SeekBar bar = new SeekBar(context);
		bar.setMax(max);
		row.addView(bar, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		row.addView(value, new LayoutParams((int) (44 * dp), ViewGroup.LayoutParams.WRAP_CONTENT));
		value.setGravity(Gravity.END);
		addView(row);
		return bar;
	}

	private static TextView button(Context context, String s, int color, float dp, OnClickListener l) {
		TextView b = label(context, s, 14, color);
		b.setGravity(Gravity.CENTER);
		b.getPaint().setFakeBoldText(true);
		b.setPadding(0, (int) (8 * dp), 0, (int) (8 * dp));
		b.setOnClickListener(l);
		return b;
	}

	private static LayoutParams buttonParams(float dp) {
		return new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
	}

	public void attach(Activity activity, Callback callback) {
		this.callback = callback;
		float dp = getResources().getDisplayMetrics().density;
		int width = Math.min((int) (360 * dp), getResources().getDisplayMetrics().widthPixels - (int) (16 * dp));
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT,
				Gravity.TOP | Gravity.CENTER_HORIZONTAL);
		lp.topMargin = (int) (24 * dp);
		if (getParent() == null) activity.addContentView(this, lp);
		setVisibility(View.VISIBLE);
	}

	public void detach() {
		callback = null;
		if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
	}

	public void setSelected(String groupName, float scale) {
		silent = true;
		selected.setText(groupName == null ? "未选中" : groupName);
		scaleBar.setEnabled(groupName != null);
		int p = Math.round(scale * 100) - SCALE_MIN;
		scaleBar.setProgress(Math.max(0, Math.min(scaleBar.getMax(), p)));
		scaleValue.setText(Math.round(scale * 100) + "%");
		silent = false;
	}

	public void setKeyAlpha(float alpha) {
		silent = true;
		int p = Math.round(alpha * 100) - ALPHA_MIN;
		alphaBar.setProgress(Math.max(0, Math.min(alphaBar.getMax(), p)));
		alphaValue.setText(Math.round(alpha * 100) + "%");
		silent = false;
	}

	private final class Listener implements SeekBar.OnSeekBarChangeListener {
		private final boolean isScale;

		Listener(boolean isScale) {
			this.isScale = isScale;
		}

		@Override
		public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
			if (silent || !fromUser || callback == null) return;
			if (isScale) {
				float v = (progress + SCALE_MIN) / 100f;
				scaleValue.setText(Math.round(v * 100) + "%");
				callback.onScale(v);
			} else {
				float v = (progress + ALPHA_MIN) / 100f;
				alphaValue.setText(Math.round(v * 100) + "%");
				callback.onAlpha(v);
			}
		}

		@Override
		public void onStartTrackingTouch(SeekBar bar) {
		}

		@Override
		public void onStopTrackingTouch(SeekBar bar) {
		}
	}
}
