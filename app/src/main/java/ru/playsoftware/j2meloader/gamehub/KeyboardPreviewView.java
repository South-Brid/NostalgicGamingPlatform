package ru.playsoftware.j2meloader.gamehub;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.List;

/** Schematic preview of a virtual keyboard layout (types match VirtualKeyboard.TYPE_*) */
class KeyboardPreviewView extends View {
	private static final int KEY = 0x99D0D4E8;
	private static final int ARROW = 0xFFFFC24B;
	private static final int SOFT = 0xFF7C8BFF;
	private static final int FIRE = 0xFFFF7A59;

	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF rect = new RectF();
	private final int type;

	KeyboardPreviewView(Context context, int type) {
		super(context);
		this.type = type;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (KeyboardStyles.isLandscape(type)) {
			drawLandscape(canvas, type);
			return;
		}
		float h = getHeight();
		float w = h * 0.56f;
		float left = (getWidth() - w) / 2f;
		float r = w * 0.12f;

		paint.setShader(null);
		paint.setColor(0xFF0E0F16);
		rect.set(left, 0, left + w, h);
		canvas.drawRoundRect(rect, r, r, paint);

		float pad = w * 0.07f;
		float bodyL = left + pad, bodyR = left + w - pad, bodyT = pad * 1.4f, bodyB = h - pad * 1.4f;
		if (type == 2 || type == KeyboardStyles.ARROWS_NUMBERS) {
			drawPortrait(canvas, left, bodyL, bodyT, bodyR, bodyB, w, h);
			return;
		}
		boolean phone = type == 1;
		float screenB = phone ? bodyT + (bodyB - bodyT) * 0.5f : bodyB;
		rect.set(bodyL, bodyT, bodyR, screenB);
		paint.setShader(new LinearGradient(0, bodyT, 0, screenB, 0xFF3D4F8F, 0xFF1F7A6B, Shader.TileMode.CLAMP));
		canvas.drawRoundRect(rect, r * 0.4f, r * 0.4f, paint);
		paint.setShader(null);

		float bw = bodyR - bodyL;
		if (phone) {
			float gridT = screenB + pad * 0.8f;
			float cellW = bw / 3f, cellH = (bodyB - gridT) / 5f;
			for (int row = 0; row < 5; row++) {
				for (int col = 0; col < 3; col++) {
					int color = row == 0 ? (col == 1 ? FIRE : SOFT) : KEY;
					key(canvas, bodyL + col * cellW, gridT + row * cellH, cellW, cellH, color);
				}
			}
			return;
		}
		float cell = bw / 8f;
		float gridB = bodyB - pad * 0.4f;
		switch (type) {
			case 3:
				numbers(canvas, bodyL, gridB, cell);
				dpad(canvas, bodyR - cell * 3, gridB, cell);
				break;
			case 4:
				dpad(canvas, bodyL, gridB, cell);
				numbers(canvas, bodyR - cell * 3, gridB, cell);
				break;
			case 5:
				numbers(canvas, bodyL + (bw - cell * 3) / 2f, gridB, cell);
				break;
			case 6:
				dpad(canvas, bodyL + (bw - cell * 3) / 2f, gridB, cell);
				break;
		}
		soft(canvas, bodyL, bodyR, gridB - cell * (type == 6 ? 3 : 4) - cell * 0.9f, cell);
	}

	private void drawPortrait(Canvas canvas, float left, float bodyL, float bodyT, float bodyR, float bodyB, float w, float h) {
		float pad = bodyL - left;
		float portrait = KeyLayouts.portraitHeight(type, w, h);
		float screenB = h - portrait - 0.2f * KeyLayouts.rowHeight(w, h);
		rect.set(bodyL, bodyT, bodyR, Math.max(bodyT + pad, screenB));
		paint.setShader(new LinearGradient(0, bodyT, 0, rect.bottom, 0xFF3D4F8F, 0xFF1F7A6B, Shader.TileMode.CLAMP));
		float r = w * 0.12f;
		canvas.drawRoundRect(rect, r * 0.4f, r * 0.4f, paint);
		paint.setShader(null);
		drawKeys(canvas, KeyLayouts.layout(type, w, h, null), left, 0);
	}

	private void drawLandscape(Canvas canvas, int type) {
		float w = Math.min(getWidth() * 0.96f, getHeight() * 1.9f);
		float h = w * 0.5f;
		float left = (getWidth() - w) / 2f, top = (getHeight() - h) / 2f;
		float r = h * 0.12f;
		paint.setShader(null);
		paint.setColor(0xFF0E0F16);
		rect.set(left, top, left + w, top + h);
		canvas.drawRoundRect(rect, r, r, paint);

		float u = Math.min(w, h);
		float screenH = h - 2 * 0.05f * u, screenW = screenH * 0.75f;
		float screenL = (w - screenW) / 2f, screenT = 0.05f * u;
		rect.set(left + screenL, top + screenT, left + screenL + screenW, top + screenT + screenH);
		paint.setShader(new LinearGradient(0, top + screenT, 0, top + screenT + screenH, 0xFF3D4F8F, 0xFF1F7A6B, Shader.TileMode.CLAMP));
		canvas.drawRoundRect(rect, r * 0.3f, r * 0.3f, paint);
		paint.setShader(null);

		RectF screen = new RectF(screenL, screenT, screenL + screenW, screenT + screenH);
		drawKeys(canvas, KeyLayouts.layout(type, w, h, screen), left, top);
	}

	private void drawKeys(Canvas canvas, List<KeyLayouts.Key> keys, float dx, float dy) {
		for (KeyLayouts.Key k : keys) {
			int color = k.style == KeyLayouts.STYLE_SOFT ? SOFT : k.style == KeyLayouts.STYLE_ARROW ? ARROW
					: k.style == KeyLayouts.STYLE_FIRE ? FIRE : KEY;
			key(canvas, k.rect.left + dx, k.rect.top + dy, k.rect.width(), k.rect.height(), color);
		}
	}
	private void numbers(Canvas c, float x, float bottom, float cell) {
		for (int row = 0; row < 4; row++) {
			for (int col = 0; col < 3; col++) {
				key(c, x + col * cell, bottom - (4 - row) * cell, cell, cell, KEY);
			}
		}
	}

	private void dpad(Canvas c, float x, float bottom, float cell) {
		float top = bottom - cell * 3;
		key(c, x + cell, top, cell, cell, ARROW);
		key(c, x, top + cell, cell, cell, ARROW);
		key(c, x + cell, top + cell, cell, cell, FIRE);
		key(c, x + cell * 2, top + cell, cell, cell, ARROW);
		key(c, x + cell, top + cell * 2, cell, cell, ARROW);
	}

	private void soft(Canvas c, float l, float r, float y, float cell) {
		key(c, l, y, cell * 1.4f, cell * 0.8f, SOFT);
		key(c, r - cell * 1.4f, y, cell * 1.4f, cell * 0.8f, SOFT);
	}

	private void key(Canvas c, float x, float y, float w, float h, int color) {
		float inset = Math.min(w, h) * 0.14f;
		paint.setColor(color);
		rect.set(x + inset, y + inset, x + w - inset, y + h - inset);
		float radius = Math.min(w, h) * 0.25f;
		c.drawRoundRect(rect, radius, radius, paint);
	}
}
