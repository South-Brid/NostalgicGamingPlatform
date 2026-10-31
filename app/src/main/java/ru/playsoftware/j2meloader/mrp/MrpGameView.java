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
import java.util.List;

import ru.playsoftware.j2meloader.gamehub.KeyboardStyles;

/** Draws the 240x320 MRP screen plus an on-screen keypad laid out like the Java keyboard styles (see KeyboardStyles). */
@SuppressLint("ViewConstructor")
class MrpGameView extends View {
	static final int KEY_MENU = -100;
	private static final int STYLE_NUM = 0;
	private static final int STYLE_SOFT = 1;
	private static final int STYLE_ARROW = 2;
	private static final int STYLE_FIRE = 3;
	private static final int[] BG = {0xFF272A3F, 0xFF3B4A8C, 0xFF2E3550, 0xFFE89A1C};
	private static final int[] FG = {0xFFF2F3F8, 0xFFFFFFFF, 0xFFFFC24B, 0xFF1A1408};
	private static final float PORTRAIT_EDGE_X = 0.04f;
	private static final float PORTRAIT_EDGE_Y = 0.03f;
	private static final float LANDSCAPE_EDGE_X = 0.06f;
	private static final float LANDSCAPE_EDGE_Y = 0.08f;

	interface Listener {
		void onMenuKey();
	}

	private static final class Key {
		final int code;
		final String label;
		final String sub;
		final int style;
		final RectF rect;
		boolean pressed;

		Key(int code, String label, String sub, int style, RectF rect) {
			this.code = code;
			this.label = label;
			this.sub = sub;
			this.style = style;
			this.rect = rect;
		}
	}

	private final Bitmap screen;
	private final Listener listener;
	private final List<Key> keys = new ArrayList<>();
	private final SparseArray<Key> pointerKeys = new SparseArray<>();
	private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint screenPaint = new Paint();
	private final Rect src = new Rect(0, 0, MrpNative.SCREEN_WIDTH, MrpNative.SCREEN_HEIGHT);
	private final RectF screenRect = new RectF();
	private final RectF tmp = new RectF();
	private int style = KeyboardStyles.PHONE_ARROWS;
	private int touchPointer = -1;
	private float textSize;

	MrpGameView(Context context, Bitmap screen, Listener listener) {
		super(context);
		this.screen = screen;
		this.listener = listener;
		screenPaint.setFilterBitmap(false);
		textPaint.setTextAlign(Paint.Align.CENTER);
		textPaint.setFakeBoldText(true);
	}

	void setStyle(int style) {
		releaseAll();
		this.style = style;
		layoutKeys(getWidth(), getHeight());
		invalidate();
	}

	int getStyle() {
		return style;
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		layoutKeys(w, h);
	}

	private void add(int code, String label, int style, float x, float y, float w, float h) {
		add(code, label, null, style, x, y, w, h);
	}

	private void add(int code, String label, String sub, int style, float x, float y, float w, float h) {
		keys.add(new Key(code, label, sub, style, new RectF(x, y, x + w, y + h)));
	}

	private void layoutKeys(float w, float h) {
		releaseAll();
		keys.clear();
		if (w <= 0 || h <= 0) return;
		boolean landscape = KeyboardStyles.isLandscape(style) && w > h;
		if (landscape) {
			layoutLandscape(w, h);
		} else if (style == KeyboardStyles.ARROWS_NUMBERS) {
			layoutArrowsNumbers(w, h);
		} else {
			layoutPhone(w, h);
		}
	}

	private void layoutPhone(float w, float h) {
		float ex = Math.min(w, h) * PORTRAIT_EDGE_X, ey = Math.min(w, h) * PORTRAIT_EDGE_Y;
		float colW = (w - ex * 2) / 3;
		float rowH = Math.min(colW * 0.6f, h * 0.5f / 7.3f);
		float softH = rowH * 0.75f, padH = rowH * 0.85f;
		float top = h - ey - (rowH * 4 + padH * 3 + softH);
		fitScreen(0, 0, w, top - rowH * 0.2f);
		float x1 = ex + colW, x2 = ex + colW * 2;
		float padTop = top + softH;
		float numTop = padTop + padH * 3;
		add(MrpNative.KEY_SOFT_LEFT, "左键", STYLE_SOFT, ex, top, colW, softH);
		add(KEY_MENU, "菜单", STYLE_SOFT, x1, top, colW, softH);
		add(MrpNative.KEY_SOFT_RIGHT, "右键", STYLE_SOFT, x2, top, colW, softH);
		add(MrpNative.KEY_UP, "↑", STYLE_ARROW, x1, padTop, colW, padH);
		add(MrpNative.KEY_LEFT, "←", STYLE_ARROW, ex, padTop + padH, colW, padH);
		add(MrpNative.KEY_SELECT, "确定", STYLE_FIRE, x1, padTop + padH, colW, padH);
		add(MrpNative.KEY_RIGHT, "→", STYLE_ARROW, x2, padTop + padH, colW, padH);
		add(MrpNative.KEY_DOWN, "↓", STYLE_ARROW, x1, padTop + padH * 2, colW, padH);
		for (int i = 0; i < 12; i++) {
			int code = i < 9 ? MrpNative.KEY_0 + i + 1 : i == 9 ? MrpNative.KEY_STAR : i == 10 ? MrpNative.KEY_0 : MrpNative.KEY_POUND;
			String label = i < 9 ? String.valueOf(i + 1) : i == 9 ? "*" : i == 10 ? "0" : "#";
			add(code, label, STYLE_NUM, ex + (i % 3) * colW, numTop + (i / 3) * rowH, colW, rowH);
		}
		textSize = rowH * 0.36f;
	}

	private void layoutArrowsNumbers(float w, float h) {
		float ex = Math.min(w, h) * PORTRAIT_EDGE_X, ey = Math.min(w, h) * PORTRAIT_EDGE_Y;
		float cell = Math.min((w - ex * 2) / 6.3f, h * 0.45f / 5.2f);
		float top = h - ey - cell * 5.1f;
		fitScreen(0, 0, w, top - cell * 0.2f);
		float softW = cell * 1.9f, softH = cell * 0.85f;
		add(MrpNative.KEY_SOFT_LEFT, "左键", STYLE_SOFT, ex, top, softW, softH);
		add(KEY_MENU, "菜单", STYLE_SOFT, (w - softW) / 2, top, softW, softH);
		add(MrpNative.KEY_SOFT_RIGHT, "右键", STYLE_SOFT, w - ex - softW, top, softW, softH);
		float bodyTop = top + cell * 1.1f;
		float half = (w - ex * 2) / 2;
		dpad(ex + (half - cell * 3) / 2, bodyTop + cell * 0.5f, cell, true);
		numbers(ex + half + (half - cell * 3) / 2, bodyTop, cell, true);
		textSize = cell * 0.36f;
	}

	private void layoutLandscape(float w, float h) {
		float min = Math.min(w, h);
		float ex = min * LANDSCAPE_EDGE_X, ey = min * LANDSCAPE_EDGE_Y;
		float screenH = h - ey * 2, screenW = screenH * MrpNative.SCREEN_WIDTH / MrpNative.SCREEN_HEIGHT;
		fitScreen((w - screenW) / 2, ey, (w + screenW) / 2, h - ey);
		float gap = screenRect.left;
		int rightRows = style == KeyboardStyles.LANDSCAPE_SIMPLE ? 4 : 5;
		float cell = Math.min((gap - ex) / 3.15f, (h - ey * 2) / (rightRows + 0.2f));
		float blockW = cell * 3;
		float leftX = Math.max(ex, ex + (gap - ex - blockW) / 2);
		float rightX = Math.min(w - ex - blockW, screenRect.right + (w - ex - screenRect.right - blockW) / 2);

		float rightTop = (h - cell * rightRows) / 2;
		boolean menuRight = style != KeyboardStyles.LANDSCAPE;
		add(MrpNative.KEY_SOFT_LEFT, "左键", STYLE_SOFT, rightX, rightTop, cell, cell);
		if (menuRight) add(KEY_MENU, "菜单", STYLE_SOFT, rightX + cell, rightTop, cell, cell);
		add(MrpNative.KEY_SOFT_RIGHT, "右键", STYLE_SOFT, rightX + cell * 2, rightTop, cell, cell);
		float numTop = rightTop + cell;
		if (style == KeyboardStyles.LANDSCAPE_SIMPLE) {
			add(MrpNative.KEY_0 + 1, "1", STYLE_NUM, rightX, numTop, cell, cell);
			add(MrpNative.KEY_0 + 3, "3", STYLE_NUM, rightX + cell * 2, numTop, cell, cell);
			add(MrpNative.KEY_0 + 5, "5", STYLE_NUM, rightX + cell, numTop + cell * 0.5f, cell, cell);
			add(MrpNative.KEY_0 + 7, "7", STYLE_NUM, rightX, numTop + cell, cell, cell);
			add(MrpNative.KEY_0 + 9, "9", STYLE_NUM, rightX + cell * 2, numTop + cell, cell, cell);
			add(MrpNative.KEY_STAR, "*", STYLE_NUM, rightX, numTop + cell * 2, cell, cell);
			add(MrpNative.KEY_0, "0", STYLE_NUM, rightX + cell, numTop + cell * 2, cell, cell);
			add(MrpNative.KEY_POUND, "#", STYLE_NUM, rightX + cell * 2, numTop + cell * 2, cell, cell);
		} else {
			numbers(rightX, numTop, cell, style == KeyboardStyles.LANDSCAPE);
		}

		boolean split = style == KeyboardStyles.LANDSCAPE_SPLIT;
		float padTop = (h - cell * 3) / 2;
		if (!menuRight) {
			padTop = (h - cell * 4) / 2 + cell;
			add(KEY_MENU, "菜单", STYLE_SOFT, leftX, padTop - cell, cell, cell * 0.85f);
		}
		if (style == KeyboardStyles.LANDSCAPE_SIMPLE) {
			add(MrpNative.KEY_0 + 2, "2", "↑", STYLE_NUM, leftX + cell, padTop, cell, cell);
			add(MrpNative.KEY_0 + 4, "4", "←", STYLE_NUM, leftX, padTop + cell, cell, cell);
			add(MrpNative.KEY_0 + 6, "6", "→", STYLE_NUM, leftX + cell * 2, padTop + cell, cell, cell);
			add(MrpNative.KEY_0 + 8, "8", "↓", STYLE_NUM, leftX + cell, padTop + cell * 2, cell, cell);
		} else {
			dpad(leftX, padTop, cell, !split);
		}
		textSize = cell * 0.34f;
	}

	private void dpad(float x, float y, float cell, boolean withSelect) {
		add(MrpNative.KEY_UP, "↑", STYLE_ARROW, x + cell, y, cell, cell);
		add(MrpNative.KEY_LEFT, "←", STYLE_ARROW, x, y + cell, cell, cell);
		if (withSelect) add(MrpNative.KEY_SELECT, "确定", STYLE_FIRE, x + cell, y + cell, cell, cell);
		add(MrpNative.KEY_RIGHT, "→", STYLE_ARROW, x + cell * 2, y + cell, cell, cell);
		add(MrpNative.KEY_DOWN, "↓", STYLE_ARROW, x + cell, y + cell * 2, cell, cell);
	}

	private void numbers(float x, float y, float cell, boolean plain5) {
		for (int i = 0; i < 12; i++) {
			float kx = x + (i % 3) * cell, ky = y + (i / 3) * cell;
			if (i < 9) {
				if (i == 4 && !plain5) add(MrpNative.KEY_SELECT, "5", "确定", STYLE_FIRE, kx, ky, cell, cell);
				else add(MrpNative.KEY_0 + i + 1, String.valueOf(i + 1), STYLE_NUM, kx, ky, cell, cell);
			} else {
				int code = i == 9 ? MrpNative.KEY_STAR : i == 10 ? MrpNative.KEY_0 : MrpNative.KEY_POUND;
				add(code, i == 9 ? "*" : i == 10 ? "0" : "#", STYLE_NUM, kx, ky, cell, cell);
			}
		}
	}

	private void fitScreen(float l, float t, float r, float b) {
		float scale = Math.min((r - l) / MrpNative.SCREEN_WIDTH, (b - t) / MrpNative.SCREEN_HEIGHT);
		float sw = MrpNative.SCREEN_WIDTH * scale, sh = MrpNative.SCREEN_HEIGHT * scale;
		float left = l + (r - l - sw) / 2, top = t + (b - t - sh) / 2;
		screenRect.set(left, top, left + sw, top + sh);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		canvas.drawColor(0xFF000000);
		canvas.drawBitmap(screen, src, screenRect, screenPaint);
		for (Key k : keys) {
			float inset = Math.min(k.rect.width(), k.rect.height()) * 0.06f;
			tmp.set(k.rect.left + inset, k.rect.top + inset, k.rect.right - inset, k.rect.bottom - inset);
			float corner = Math.min(tmp.width(), tmp.height()) * 0.25f;
			keyPaint.setColor(k.pressed ? 0xFFFFC24B : BG[k.style]);
			canvas.drawRoundRect(tmp, corner, corner, keyPaint);
			textPaint.setColor(k.pressed ? 0xFF1A1408 : FG[k.style]);
			textPaint.setTextSize(textSize);
			if (k.sub != null) {
				drawCentered(canvas, k.label, tmp.centerX(), tmp.centerY() - tmp.height() * 0.13f);
				textPaint.setTextSize(textSize * 0.62f);
				drawCentered(canvas, k.sub, tmp.centerX(), tmp.centerY() + tmp.height() * 0.22f);
			} else {
				drawCentered(canvas, k.label, tmp.centerX(), tmp.centerY());
			}
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

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent e) {
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
