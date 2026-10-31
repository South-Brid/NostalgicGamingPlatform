package ru.playsoftware.j2meloader.mrp;

import android.graphics.Bitmap;

/** JNI bridge to the vmrp core (app/src/main/cpp/vmrp). Callbacks arrive on the emulator thread. */
public final class MrpNative {
	public static final int KEY_0 = 0;
	public static final int KEY_STAR = 10;
	public static final int KEY_POUND = 11;
	public static final int KEY_UP = 12;
	public static final int KEY_DOWN = 13;
	public static final int KEY_LEFT = 14;
	public static final int KEY_RIGHT = 15;
	public static final int KEY_POWER = 16;
	public static final int KEY_SOFT_LEFT = 17;
	public static final int KEY_SOFT_RIGHT = 18;
	public static final int KEY_SEND = 19;
	public static final int KEY_SELECT = 20;

	static final int KEY_PRESS = 0;
	static final int KEY_RELEASE = 1;
	static final int MOUSE_DOWN = 2;
	static final int MOUSE_UP = 3;
	static final int DIALOG_EVENT = 6;
	static final int MOUSE_MOVE = 12;
	static final int DIALOG_KEY_OK = 0;
	static final int DIALOG_KEY_CANCEL = 1;

	public static final int SCREEN_WIDTH = 240;
	public static final int SCREEN_HEIGHT = 320;

	interface Host {
		void onFrame();

		void onExit(int code, byte[] detail);

		int playSound(int type, byte[] data, boolean loop);

		int stopSound(int type);

		void vibrate(int ms);

		int dialogCreate(byte[] title, byte[] text, int type, boolean isText);

		void dialogRelease(int handle);

		void dialogRefresh(int handle, byte[] title, byte[] text, int type);

		int editCreate(byte[] title, byte[] text, int type, int maxSize);

		void editRelease(int handle);
	}

	private static Host host;
	private static boolean loaded;

	private MrpNative() {}

	static boolean load() {
		if (!loaded) {
			try {
				System.loadLibrary("vmrp");
				loaded = true;
			} catch (UnsatisfiedLinkError e) {
				e.printStackTrace();
			}
		}
		return loaded;
	}

	static boolean start(Host h, String root, String mrpName, Bitmap screen) {
		host = h;
		return nativeStart(root, mrpName, screen);
	}

	static void key(int key, boolean down) {
		nativeEvent(down ? KEY_PRESS : KEY_RELEASE, key, 0);
	}

	static void touch(int type, int x, int y) {
		nativeEvent(type, x, y);
	}

	static void dialogResult(boolean ok) {
		nativeEvent(DIALOG_EVENT, ok ? DIALOG_KEY_OK : DIALOG_KEY_CANCEL, 0);
	}

	private static native boolean nativeStart(String root, String mrpName, Bitmap screen);

	static native void nativeEvent(int code, int p1, int p2);

	static native void nativePause();

	static native void nativeResume();

	static native void nativeEditDone(byte[] utf8);

	@SuppressWarnings("unused")
	private static void onFrame() {
		host.onFrame();
	}

	@SuppressWarnings("unused")
	private static void onExit(int code, byte[] detail) {
		host.onExit(code, detail);
	}

	@SuppressWarnings("unused")
	private static int playSound(int type, byte[] data, int loop) {
		return host.playSound(type, data, loop != 0);
	}

	@SuppressWarnings("unused")
	private static int stopSound(int type) {
		return host.stopSound(type);
	}

	@SuppressWarnings("unused")
	private static void vibrate(int ms) {
		host.vibrate(ms);
	}

	@SuppressWarnings("unused")
	private static int dialogCreate(byte[] title, byte[] text, int type, int isText) {
		return host.dialogCreate(title, text, type, isText != 0);
	}

	@SuppressWarnings("unused")
	private static void dialogRelease(int handle) {
		host.dialogRelease(handle);
	}

	@SuppressWarnings("unused")
	private static void dialogRefresh(int handle, byte[] title, byte[] text, int type) {
		host.dialogRefresh(handle, title, text, type);
	}

	@SuppressWarnings("unused")
	private static int editCreate(byte[] title, byte[] text, int type, int maxSize) {
		return host.editCreate(title, text, type, maxSize);
	}

	@SuppressWarnings("unused")
	private static void editRelease(int handle) {
		host.editRelease(handle);
	}
}
