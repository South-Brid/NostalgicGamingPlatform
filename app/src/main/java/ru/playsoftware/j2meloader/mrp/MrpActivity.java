package ru.playsoftware.j2meloader.mrp;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputFilter;
import android.text.InputType;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.databinding.ViewGameLaunchBinding;
import ru.playsoftware.j2meloader.gamehub.KeyboardStyles;

public class MrpActivity extends AppCompatActivity implements MrpNative.Host {
	public static final String EXTRA_PATH = "mrp_path";
	public static final String EXTRA_NAME = "mrp_name";
	public static final String EXTRA_ID = "mrp_id";
	public static final String EXTRA_ICON = "mrp_icon";
	public static final String EXTRA_KEYBOARD = "mrp_keyboard";
	private static final String[] SOUND_EXT = {"mid", "wav", "mp3", "amr", "pcm"};
	private static final Charset UCS2 = Charset.forName("UTF-16BE");

	private final Handler main = new Handler(Looper.getMainLooper());
	private final MediaPlayer[] players = new MediaPlayer[SOUND_EXT.length];
	private final SparseArray<AlertDialog> dialogs = new SparseArray<>();
	private final Runnable hideLaunch = this::hideLaunchOverlay;
	private MrpGameView gameView;
	private ViewGameLaunchBinding launch;
	private String gameId;
	private boolean started;
	private boolean firstFrame = true;
	private boolean exiting;
	private long launchShownAt;
	private int nextHandle = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		gameId = getIntent().getStringExtra(EXTRA_ID);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
		hideSystemUi();

		Bitmap screen = Bitmap.createBitmap(MrpNative.SCREEN_WIDTH, MrpNative.SCREEN_HEIGHT, Bitmap.Config.RGB_565);
		gameView = new MrpGameView(this, screen, this::showMenu);
		int fallback = getIntent().getIntExtra(EXTRA_KEYBOARD, KeyboardStyles.PHONE_ARROWS);
		applyKeyboard(MrpRuntime.getKeyboard(this, gameId, fallback));

		FrameLayout root = new FrameLayout(this);
		root.setBackgroundColor(0xFF000000);
		root.addView(gameView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		launch = ViewGameLaunchBinding.inflate(getLayoutInflater(), root, false);
		root.addView(launch.getRoot());
		setContentView(root);
		showLaunchOverlay();

		if (!MrpNative.load()) {
			fail("这台设备暂不支持 MRP 游戏（缺少运行库）");
			return;
		}
		String path = getIntent().getStringExtra(EXTRA_PATH);
		String id = gameId;
		Context app = getApplicationContext();
		new Thread(() -> {
			try {
				File rootDir = MrpRuntime.prepare(app);
				String name = MrpRuntime.installGame(app, new File(path), id);
				main.post(() -> {
					if (isFinishing()) return;
					started = MrpNative.start(this, rootDir.getPath(), name, screen);
					if (!started) fail("MRP 模拟器启动失败");
				});
			} catch (IOException e) {
				e.printStackTrace();
				main.post(() -> fail("游戏文件读取失败：" + e.getMessage()));
			}
		}, "MrpPrepare").start();
	}

	private void showLaunchOverlay() {
		String name = getIntent().getStringExtra(EXTRA_NAME);
		if (name == null) name = "";
		launch.name.setText(name);
		String iconPath = getIntent().getStringExtra(EXTRA_ICON);
		Bitmap icon = iconPath != null ? BitmapFactory.decodeFile(iconPath) : null;
		if (icon != null) {
			launch.icon.setImageBitmap(icon);
		} else if (!name.isEmpty()) {
			launch.icon.setVisibility(View.GONE);
			launch.letter.setVisibility(View.VISIBLE);
			launch.letter.setText(name.substring(0, 1));
			GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFFF8A3D, 0xFFB7410E});
			bg.setCornerRadius(20 * getResources().getDisplayMetrics().density);
			launch.letter.setBackground(bg);
		}
		launch.status.setText("正在启动 MRP 游戏…");
		launch.tip.setText("按「菜单」键或手机返回键打开菜单（切换键盘、退出）");
		View content = launch.content;
		content.setAlpha(0f);
		content.setTranslationY(24 * getResources().getDisplayMetrics().density);
		content.animate().alpha(1f).translationY(0).setDuration(320).setStartDelay(80).start();
		launchShownAt = SystemClock.uptimeMillis();
		main.postDelayed(hideLaunch, 12000);
	}

	private void hideLaunchOverlay() {
		View overlay = launch.getRoot();
		if (exiting || overlay.getVisibility() != View.VISIBLE) return;
		overlay.animate().alpha(0f).setDuration(280).withEndAction(() -> overlay.setVisibility(View.GONE)).start();
	}

	private void showExitOverlay() {
		exiting = true;
		main.removeCallbacks(hideLaunch);
		View overlay = launch.getRoot();
		launch.status.setText("正在退出…");
		launch.tip.setVisibility(View.GONE);
		overlay.animate().cancel();
		if (overlay.getVisibility() != View.VISIBLE) {
			overlay.setAlpha(0f);
			overlay.setVisibility(View.VISIBLE);
		}
		overlay.animate().alpha(1f).setDuration(180).start();
	}

	private void hideSystemUi() {
		WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
		controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
		controller.hide(WindowInsetsCompat.Type.systemBars());
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) hideSystemUi();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (started) {
			gameView.releaseAll();
			MrpNative.nativePause();
		}
		for (MediaPlayer p : players) {
			if (p != null && p.isPlaying()) p.pause();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (started) MrpNative.nativeResume();
	}

	private void fail(String message) {
		if (isFinishing()) return;
		new AlertDialog.Builder(this, R.style.HubDialog)
				.setTitle("游戏无法运行")
				.setMessage(message)
				.setCancelable(false)
				.setPositiveButton("返回", (d, w) -> exit())
				.show();
	}

	private void exit() {
		if (exiting && isFinishing()) return;
		showExitOverlay();
		main.postDelayed(this::finish, 200);
	}

	@Override
	public void finish() {
		super.finish();
		overridePendingTransition(R.anim.hub_hold, R.anim.game_exit);
		new Thread(() -> {
			SystemClock.sleep(450);
			Process.killProcess(Process.myPid());
		}, "MrpExit").start();
	}

	@Override
	public void onBackPressed() {
		showMenu();
	}

	private void applyKeyboard(int type) {
		gameView.setStyle(type);
		setRequestedOrientation(KeyboardStyles.isLandscape(type) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
				: ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
	}

	private void showMenu() {
		if (exiting) return;
		int current = gameView.getStyle();
		String[] items = {"▶  继续游戏", "⌨  切换键盘样式（" + KeyboardStyles.title(current) + "）", "⏻  退出游戏"};
		new AlertDialog.Builder(this, R.style.HubDialog)
				.setTitle(getIntent().getStringExtra(EXTRA_NAME))
				.setItems(items, (d, which) -> {
					if (which == 1) {
						KeyboardStyles.pick(this, "切换键盘样式", "只对这个游戏生效", current, true, type -> {
							MrpRuntime.setKeyboard(this, gameId, type);
							applyKeyboard(type);
						});
					} else if (which == 2) {
						exit();
					}
				})
				.show();
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		int key = mapKey(event.getKeyCode());
		if (key < 0 || !started) return super.dispatchKeyEvent(event);
		if (event.getRepeatCount() == 0) {
			if (event.getAction() == KeyEvent.ACTION_DOWN) MrpNative.key(key, true);
			else if (event.getAction() == KeyEvent.ACTION_UP) MrpNative.key(key, false);
		}
		return true;
	}

	private static int mapKey(int code) {
		if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) return MrpNative.KEY_0 + code - KeyEvent.KEYCODE_0;
		switch (code) {
			case KeyEvent.KEYCODE_DPAD_UP: return MrpNative.KEY_UP;
			case KeyEvent.KEYCODE_DPAD_DOWN: return MrpNative.KEY_DOWN;
			case KeyEvent.KEYCODE_DPAD_LEFT: return MrpNative.KEY_LEFT;
			case KeyEvent.KEYCODE_DPAD_RIGHT: return MrpNative.KEY_RIGHT;
			case KeyEvent.KEYCODE_DPAD_CENTER:
			case KeyEvent.KEYCODE_ENTER: return MrpNative.KEY_SELECT;
			case KeyEvent.KEYCODE_STAR: return MrpNative.KEY_STAR;
			case KeyEvent.KEYCODE_POUND: return MrpNative.KEY_POUND;
			case KeyEvent.KEYCODE_SOFT_LEFT: return MrpNative.KEY_SOFT_LEFT;
			case KeyEvent.KEYCODE_SOFT_RIGHT: return MrpNative.KEY_SOFT_RIGHT;
			default: return -1;
		}
	}

	@Override
	public void onFrame() {
		gameView.postInvalidate();
		if (firstFrame) {
			firstFrame = false;
			main.post(() -> {
				main.removeCallbacks(hideLaunch);
				main.postDelayed(hideLaunch, Math.max(200, 900 - (SystemClock.uptimeMillis() - launchShownAt)));
			});
		}
	}

	@Override
	public void onExit(int code) {
		main.post(() -> {
			if (code == 0) exit();
			else fail(code == 1 ? "游戏启动失败，可能是不支持的 MRP 文件" : "游戏运行出错，已停止");
		});
	}

	@Override
	public int playSound(int type, byte[] data, boolean loop) {
		if (type < 0 || type >= players.length || data == null) return -1;
		stopSound(type);
		try {
			File file = new File(getCacheDir(), "mrp_sound_" + type + "." + SOUND_EXT[type]);
			try (FileOutputStream out = new FileOutputStream(file)) {
				out.write(data);
			}
			MediaPlayer player = new MediaPlayer();
			player.setDataSource(file.getPath());
			player.setLooping(loop);
			player.prepare();
			player.start();
			players[type] = player;
			return 0;
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}

	@Override
	public int stopSound(int type) {
		if (type < 0 || type >= players.length) return -1;
		MediaPlayer player = players[type];
		players[type] = null;
		if (player != null) {
			try {
				player.stop();
			} catch (IllegalStateException ignored) {
			}
			player.release();
		}
		return 0;
	}

	@Override
	public void vibrate(int ms) {
		Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
		if (v == null || ms <= 0) return;
		if (android.os.Build.VERSION.SDK_INT >= 26) {
			v.vibrate(VibrationEffect.createOneShot(Math.min(ms, 1000), VibrationEffect.DEFAULT_AMPLITUDE));
		} else {
			v.vibrate(Math.min(ms, 1000));
		}
	}

	private static String ucs2(byte[] data) {
		return data == null ? "" : new String(data, UCS2);
	}

	private static String utf8(byte[] data) {
		return data == null ? "" : new String(data, StandardCharsets.UTF_8);
	}

	@Override
	public int dialogCreate(byte[] title, byte[] text, int type, boolean isText) {
		int handle;
		synchronized (dialogs) {
			handle = nextHandle++;
		}
		String t = ucs2(title), s = ucs2(text);
		main.post(() -> {
			if (isFinishing()) return;
			AlertDialog.Builder b = new AlertDialog.Builder(this, R.style.HubDialog)
					.setTitle(t)
					.setMessage(s)
					.setCancelable(false);
			if (type == 0 || type == 1) b.setPositiveButton("确定", (d, w) -> MrpNative.dialogResult(true));
			if (type == 1) b.setNegativeButton("取消", (d, w) -> MrpNative.dialogResult(false));
			if (type == 2) b.setNegativeButton("返回", (d, w) -> MrpNative.dialogResult(false));
			dialogs.put(handle, b.show());
		});
		return handle;
	}

	@Override
	public void dialogRelease(int handle) {
		main.post(() -> {
			AlertDialog d = dialogs.get(handle);
			dialogs.remove(handle);
			if (d != null) d.dismiss();
		});
	}

	@Override
	public void dialogRefresh(int handle, byte[] title, byte[] text, int type) {
		String t = ucs2(title), s = ucs2(text);
		main.post(() -> {
			AlertDialog d = dialogs.get(handle);
			if (d == null) return;
			d.setTitle(t);
			d.setMessage(s);
		});
	}

	@Override
	public int editCreate(byte[] title, byte[] text, int type, int maxSize) {
		int handle;
		synchronized (dialogs) {
			handle = nextHandle++;
		}
		String t = utf8(title), s = utf8(text);
		main.post(() -> {
			if (isFinishing()) return;
			EditText input = new EditText(this);
			input.setText(s);
			input.setSelection(input.getText().length());
			input.setTextColor(getColor(R.color.hub_text));
			input.setInputType(type == 1 ? InputType.TYPE_CLASS_NUMBER
					: type == 2 ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
			if (maxSize > 0) input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxSize)});
			int pad = (int) (20 * getResources().getDisplayMetrics().density);
			FrameLayout box = new FrameLayout(this);
			box.setPadding(pad, pad / 2, pad, 0);
			box.addView(input);
			AlertDialog d = new AlertDialog.Builder(this, R.style.HubDialog)
					.setTitle(t)
					.setView(box)
					.setCancelable(false)
					.setPositiveButton("确定", (di, w) ->
							MrpNative.nativeEditDone(input.getText().toString().getBytes(StandardCharsets.UTF_8)))
					.setNegativeButton("取消", (di, w) -> MrpNative.nativeEditDone(null))
					.show();
			dialogs.put(handle, d);
		});
		return handle;
	}

	@Override
	public void editRelease(int handle) {
		dialogRelease(handle);
	}

	public static Intent intent(Context context, String path, String name, String id, String icon, int keyboard) {
		return new Intent(context, MrpActivity.class)
				.putExtra(EXTRA_PATH, path)
				.putExtra(EXTRA_NAME, name)
				.putExtra(EXTRA_ID, id)
				.putExtra(EXTRA_ICON, icon)
				.putExtra(EXTRA_KEYBOARD, keyboard);
	}
}
