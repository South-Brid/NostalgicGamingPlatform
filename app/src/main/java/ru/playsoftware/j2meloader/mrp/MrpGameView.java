package ru.playsoftware.j2meloader.mrp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.playsoftware.j2meloader.gamehub.KeyCustom;
import ru.playsoftware.j2meloader.gamehub.KeyLayouts;
import ru.playsoftware.j2meloader.gamehub.KeyboardStyles;

/** Draws the 240x320 MRP screen plus an on-screen keypad laid out like the Java keyboard styles (see KeyLayouts). */
@SuppressLint("ViewConstructor")
class MrpGameView extends View {
	static final int KEY_MENU = -100;
	private static final int[] BG = {0xFF272A3F, 0xFF3B4A8C, 0xFF2E3550, 0xFFE89A1C};
	private static final int[] FG = {0xFFF2F3F8, 0xFFFFFFFF, 0xFFFFC24B, 0xFF1A1408};

	interface Listener {
		void onMenuKey();
	}

	interface EditListener {
		void onGroupSelected(String group);
	}

	private static final class Key {
		final int code;
		final String group;
		final String label;
		final String sub;
		final int style;
		final RectF rect;
		boolean pressed;

		Key(int code, KeyLayouts.Key k) {
			this.code = code;
			this.group = k.group;
			this.label = k.label;
			this.sub = k.sub;
			this.style = k.style;
			this.rect = k.rect;
		}
	}

	private final Bitmap screen;
	private final Listener listener;
	private final List<Key> keys = new ArrayList<>();
	private final SparseArray<Key> pointerKeys = new SparseArray<>();
	private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint screenPaint = new Paint();
	private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Rect src = new Rect(0, 0, MrpNative.SCREEN_WIDTH, MrpNative.SCREEN_HEIGHT);
	private final RectF screenRect = new RectF();
	private final RectF tmp = new RectF();
	private int style = KeyboardStyles.PHONE_ARROWS;
	private KeyCustom custom = new KeyCustom();
	private int touchPointer = -1;
	private boolean editing;
	private EditListener editListener;
	private String selectedGroup;
	private float dragStartX, dragStartY, dragStartDx, dragStartDy;
	private final Map<String, RectF> baseBoxes = new HashMap<>();

	MrpGameView(Context context, Bitmap screen, Listener listener) {
		super(context);
		this.screen = screen;
		this.listener = listener;
		screenPaint.setFilterBitmap(false);
		textPaint.setTextAlign(Paint.Align.CENTER);
		textPaint.setFakeBoldText(true);
		outlinePaint.setStyle(Paint.Style.STROKE);
	}

	void setStyle(int style, KeyCustom custom) {
		releaseAll();
		this.style = style;
		this.custom = custom;
		selectedGroup = null;
		layoutKeys(getWidth(), getHeight());
		invalidate();
	}

	int getStyle() {
		return style;
	}

	KeyCustom getCustom() {
		return custom;
	}

	void setCustom(KeyCustom custom) {
		this.custom = custom;
		relayout();
	}

	void relayout() {
		layoutKeys(getWidth(), getHeight());
		invalidate();
	}

	void setEditing(boolean editing, EditListener listener) {
		releaseAll();
		this.editing = editing;
		this.editListener = listener;
		selectedGroup = null;
		invalidate();
	}

	String getSelectedGroup() {
		return selectedGroup;
	}

	boolean isEditing() {
		return editing;
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		layoutKeys(w, h);
	}

	private int codeOf(String id) {
		switch (id) {
			case "soft_l":
				return MrpNative.KEY_SOFT_LEFT;
			case "soft_r":
				return MrpNative.KEY_SOFT_RIGHT;
			case "menu":
				return KEY_MENU;
			case "up":
				return MrpNative.KEY_UP;
			case "down":
				return MrpNative.KEY_DOWN;
			case "left":
				return MrpNative.KEY_LEFT;
			case "right":
				return MrpNative.KEY_RIGHT;
			case "ok":
				return MrpNative.KEY_SELECT;
			case "*":
				return MrpNative.KEY_STAR;
			case "#":
				return MrpNative.KEY_POUND;
			case "5":
				return style == KeyboardStyles.LANDSCAPE_SPLIT ? MrpNative.KEY_SELECT : MrpNative.KEY_0 + 5;
			default:
				return MrpNative.KEY_0 + (id.charAt(0) - '0');
		}
	}

	private void layoutKeys(float w, float h) {
		releaseAll();
		keys.clear();
		baseBoxes.clear();
		if (w <= 0 || h <= 0) return;
		float u = Math.min(w, h);
		List<KeyLayouts.Key> layout;
		if (KeyboardStyles.isLandscape(style) && w > h) {
			float ey = u * 0.08f;
			float screenH = h - ey * 2, screenW = screenH * MrpNative.SCREEN_WIDTH / MrpNative.SCREEN_HEIGHT;
			fitScreen((w - screenW) / 2, ey, (w + screenW) / 2, h - ey);
			layout = KeyLayouts.layout(style, w, h, screenRect);
		} else {
			int s = KeyboardStyles.isLandscape(style) ? KeyboardStyles.PHONE_ARROWS : style;
			float portrait = KeyLayouts.portraitHeight(s, w, h);
			fitScreen(0, 0, w, h - portrait - 0.2f * KeyLayouts.rowHeight(w, h));
			layout = KeyLayouts.layout(s, w, h, null);
		}
		for (KeyLayouts.Key k : layout) {
			RectF box = baseBoxes.get(k.group);
			if (box == null) baseBoxes.put(k.group, new RectF(k.rect));
			else box.union(k.rect);
		}
		custom.apply(layout, w, h);
		for (KeyLayouts.Key k : layout) keys.add(new Key(codeOf(k.id), k));
	}

	private void fitScreen(float l, float t, float r, float b) {
		float scale = Math.min((r - l) / MrpNative.SCREEN_WIDTH, (b - t) / MrpNative.SCREEN_HEIGHT);
		float sw = MrpNative.SCREEN_WIDTH * scale, sh = MrpNative.SCREEN_HEIGHT * scale;
		float left = l + (r - l - sw) / 2, top = t + (b - t - sh) / 2;
		screenRect.set(left, top, left + sw, top + sh);
	}

	private RectF groupBox(String group) {
		RectF box = null;
		for (Key k : keys) {
			if (!k.group.equals(group)) continue;
			if (box == null) box = new RectF(k.rect);
			else box.union(k.rect);
		}
		return box;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		canvas.drawColor(0xFF000000);
		canvas.drawBitmap(screen, src, screenRect, screenPaint);
		int alpha = Math.round((custom.hasAlpha ? custom.alpha : 1f) * 255);
		for (Key k : keys) {
			float inset = Math.min(k.rect.width(), k.rect.height()) * 0.06f;
			tmp.set(k.rect.left + inset, k.rect.top + inset, k.rect.right - inset, k.rect.bottom - inset);
			float corner = Math.min(tmp.width(), tmp.height()) * 0.25f;
			float textSize = Math.min(k.rect.width(), k.rect.height()) * 0.36f;
			keyPaint.setColor(k.pressed ? 0xFFFFC24B : BG[k.style]);
			keyPaint.setAlpha(alpha);
			canvas.drawRoundRect(tmp, corner, corner, keyPaint);
			textPaint.setColor(k.pressed ? 0xFF1A1408 : FG[k.style]);
			textPaint.setAlpha(alpha);
			textPaint.setTextSize(textSize);
			if (k.sub != null) {
				drawCentered(canvas, k.label, tmp.centerX(), tmp.centerY() - tmp.height() * 0.13f);
				textPaint.setTextSize(textSize * 0.62f);
				drawCentered(canvas, k.sub, tmp.centerX(), tmp.centerY() + tmp.height() * 0.22f);
			} else {
				drawCentered(canvas, k.label, tmp.centerX(), tmp.centerY());
			}
		}
		if (editing) drawGroupOutlines(canvas);
	}

	private void drawGroupOutlines(Canvas canvas) {
		float dp = getResources().getDisplayMetrics().density;
		List<String> seen = new ArrayList<>();
		for (Key k : keys) if (!seen.contains(k.group)) seen.add(k.group);
		for (String group : seen) {
			RectF box = groupBox(group);
			if (box == null) continue;
			boolean selected = group.equals(selectedGroup);
			outlinePaint.setStrokeWidth((selected ? 2 : 1) * dp);
			outlinePaint.setColor(selected ? 0xFFFFC24B : 0x80FFFFFF);
			canvas.drawRoundRect(box, 6 * dp, 6 * dp, outlinePaint);
		}
	}

	private void drawCentered(Canvas canvas, String text, float x, float y) {
		Paint.FontMetrics fm = textPaint.getFontMetrics();
		canvas.drawText(text, x, y - (fm.ascent + fm.descent) / 2, textPaint);
	}

	private Key keyAt(float x, float y) {
		for (Key k : keys) if (k.rect.contains(x, y)) return k;
		return null;
	}

	private void press(int pointer, Key k) {
		Key old = pointerKeys.get(pointer);
		if (old == k) return;
		if (old != null) {
			old.pressed = false;
			if (old.code != KEY_MENU) MrpNative.key(old.code, false);
			pointerKeys.remove(pointer);
		}
		if (k != null) {
			k.pressed = true;
			pointerKeys.put(pointer, k);
			if (k.code != KEY_MENU) MrpNative.key(k.code, true);
			performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
		}
		invalidate();
	}

	private int screenX(float x) {
		return (int) ((x - screenRect.left) * MrpNative.SCREEN_WIDTH / screenRect.width());
	}

	private int screenY(float y) {
		return (int) ((y - screenRect.top) * MrpNative.SCREEN_HEIGHT / screenRect.height());
	}

	private boolean onEditTouch(MotionEvent e) {
		float u = Math.min(getWidth(), getHeight());
		switch (e.getActionMasked()) {
			case MotionEvent.ACTION_DOWN: {
				Key k = keyAt(e.getX(), e.getY());
				if (k != null) {
					selectedGroup = k.group;
					float[] g = custom.group(k.group);
					dragStartX = e.getX();
					dragStartY = e.getY();
					dragStartDx = g[0];
					dragStartDy = g[1];
					if (editListener != null) editListener.onGroupSelected(k.group);
					invalidate();
				} else {
					selectedGroup = null;
					if (editListener != null) editListener.onGroupSelected(null);
					invalidate();
				}
				break;
			}
			case MotionEvent.ACTION_MOVE:
				if (selectedGroup != null && e.getPointerCount() == 1) {
					float[] g = custom.group(selectedGroup);
					g[0] = dragStartDx + (e.getX() - dragStartX) / u;
					g[1] = dragStartDy + (e.getY() - dragStartY) / u;
					layoutKeys(getWidth(), getHeight());
					RectF base = baseBoxes.get(selectedGroup), now = groupBox(selectedGroup);
					if (base != null && now != null) {
						g[0] = (now.centerX() - base.centerX()) / u;
						g[1] = (now.centerY() - base.centerY()) / u;
					}
					invalidate();
				}
				break;
		}
		return true;
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent e) {
		if (editing) return onEditTouch(e);
		int action = e.getActionMasked();
		int index = e.getActionIndex();
		switch (action) {
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_POINTER_DOWN: {
				int id = e.getPointerId(index);
				float x = e.getX(index), y = e.getY(index);
				Key k = keyAt(x, y);
				if (k != null) {
					press(id, k);
				} else if (touchPointer == -1 && screenRect.contains(x, y)) {
					touchPointer = id;
					MrpNative.touch(MrpNative.MOUSE_DOWN, screenX(x), screenY(y));
				}
				break;
			}
			case MotionEvent.ACTION_MOVE:
				for (int i = 0; i < e.getPointerCount(); i++) {
					int id = e.getPointerId(i);
					float x = e.getX(i), y = e.getY(i);
					if (id == touchPointer) {
						MrpNative.touch(MrpNative.MOUSE_MOVE, screenX(x), screenY(y));
					} else {
						Key current = pointerKeys.get(id);
						if (current != null && current.code != KEY_MENU) {
							Key k = keyAt(x, y);
							if (k != null && k.code != KEY_MENU) press(id, k);
						}
					}
				}
				break;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_POINTER_UP:
			case MotionEvent.ACTION_CANCEL: {
				int id = e.getPointerId(index);
				if (id == touchPointer) {
					MrpNative.touch(MrpNative.MOUSE_UP, screenX(e.getX(index)), screenY(e.getY(index)));
					touchPointer = -1;
				}
				Key k = pointerKeys.get(id);
				press(id, null);
				if (k != null && k.code == KEY_MENU && action != MotionEvent.ACTION_CANCEL) listener.onMenuKey();
				if (action == MotionEvent.ACTION_CANCEL) releaseAll();
				break;
			}
		}
		return true;
	}

	void releaseAll() {
		for (int i = 0; i < pointerKeys.size(); i++) {
			Key k = pointerKeys.valueAt(i);
			k.pressed = false;
			if (k.code != KEY_MENU) MrpNative.key(k.code, false);
		}
		pointerKeys.clear();
		touchPointer = -1;
		invalidate();
	}
}
