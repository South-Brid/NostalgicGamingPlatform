package ru.playsoftware.j2meloader.gamehub;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.AppListModel;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.databinding.DialogGameLoadingBinding;
import ru.playsoftware.j2meloader.databinding.DialogImportProgressBinding;
import ru.playsoftware.j2meloader.databinding.FragmentGameHubBinding;
import ru.playsoftware.j2meloader.databinding.ItemHubNavBinding;
import ru.playsoftware.j2meloader.databinding.ItemHubSideBinding;
import ru.playsoftware.j2meloader.databinding.ViewPlatformFilterBinding;
import ru.playsoftware.j2meloader.info.AboutDialogFragment;
import ru.playsoftware.j2meloader.mrp.MrpRuntime;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import ru.playsoftware.j2meloader.util.FileUtils;

public class GameHubFragment extends Fragment implements GameAdapter.Listener {
	private static final int TAB_HOME = 0;
	private static final int TAB_CATEGORY = 1;
	private static final int TAB_SEARCH = 2;
	private static final int TAB_MINE = 3;
	private static final String PREF_PLATFORM = "gamehub_platform";

	private final CompositeDisposable disposables = new CompositeDisposable();
	private final GameAdapter homeAdapter = new GameAdapter(this);
	private final GameAdapter categoryAdapter = new GameAdapter(this);
	private final GameAdapter searchAdapter = new GameAdapter(this);
	private final GameAdapter mineAdapter = new GameAdapter(this);
	private final Map<String, View> sideItems = new HashMap<>();
	private final Map<String, GameEntry> byId = new HashMap<>();
	private final ActivityResultLauncher<String> importLauncher =
			registerForActivityResult(FileUtils.getFilePicker(), this::onImportPicked);
	private final OnBackPressedCallback backToHome = new OnBackPressedCallback(false) {
		@Override
		public void handleOnBackPressed() {
			selectTab(TAB_HOME);
		}
	};

	private FragmentGameHubBinding binding;
	private AppRepository appRepository;
	private List<GameEntry> allGames = Collections.emptyList();
	private List<GameEntry> games = Collections.emptyList();
	private List<String> categories = Collections.emptyList();
	private String platform;
	private Set<String> installed = new HashSet<>();
	private int tab = TAB_HOME;
	private String category;
	private String query = "";
	private Drawable savedDecorBackground;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		appRepository = new ViewModelProvider(requireActivity()).get(AppListModel.class).getAppRepository();
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		binding = FragmentGameHubBinding.inflate(inflater, container, false);
		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		int widthDp = getResources().getConfiguration().screenWidthDp;
		int span = Math.max(3, widthDp / 100);
		setupGrid(binding.homeList, homeAdapter, span);
		setupGrid(binding.categoryList, categoryAdapter, Math.max(3, (widthDp - 92) / 100));
		setupGrid(binding.searchList, searchAdapter, span);
		setupGrid(binding.mineList, mineAdapter, span);

		setupNav(binding.navHome, R.drawable.ic_nav_home, "首页", TAB_HOME);
		setupNav(binding.navCategory, R.drawable.ic_nav_category, "分类", TAB_CATEGORY);
		setupNav(binding.navSearch, R.drawable.ic_nav_search, "搜索", TAB_SEARCH);
		setupNav(binding.navMine, R.drawable.ic_nav_mine, "我的", TAB_MINE);
		platform = PreferenceManager.getDefaultSharedPreferences(requireContext())
				.getString(PREF_PLATFORM, GameEntry.PLATFORM_JAVA);
		for (ViewPlatformFilterBinding f : filters()) {
			f.chipJava.setOnClickListener(v -> setPlatform(GameEntry.PLATFORM_JAVA));
			f.chipMrp.setOnClickListener(v -> setPlatform(GameEntry.PLATFORM_MRP));
		}

		binding.search.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}

			@Override
			public void afterTextChanged(Editable s) {
				query = s.toString().trim().toLowerCase();
				binding.clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
				renderSearch();
				binding.searchList.scrollToPosition(0);
			}
		});
		binding.search.setOnEditorActionListener((v, actionId, event) -> {
			if (actionId == EditorInfo.IME_ACTION_SEARCH) hideKeyboard();
			return false;
		});
		binding.clear.setOnClickListener(v -> binding.search.setText(""));
		binding.homeAdd.setOnClickListener(v -> showAddGameDialog());
		binding.homeImport.setSelected(true);
		binding.homeImport.setOnClickListener(v -> importLocal());

		requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backToHome);
		selectTab(tab);
		reload();
	}

	private void setupGrid(RecyclerView list, GameAdapter adapter, int span) {
		GridLayoutManager lm = new GridLayoutManager(requireContext(), span);
		lm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
			@Override
			public int getSpanSize(int position) {
				return adapter.isFullSpan(position) ? span : 1;
			}
		});
		list.setLayoutManager(lm);
		list.setAdapter(adapter);
	}

	private void setupNav(ItemHubNavBinding nav, int icon, String label, int index) {
		nav.icon.setImageResource(icon);
		nav.label.setText(label);
		nav.getRoot().setOnClickListener(v -> {
			if (tab == index) scrollToTop(index);
			else selectTab(index);
		});
	}

	private void selectTab(int index) {
		tab = index;
		binding.pageHome.setVisibility(index == TAB_HOME ? View.VISIBLE : View.GONE);
		binding.pageCategory.setVisibility(index == TAB_CATEGORY ? View.VISIBLE : View.GONE);
		binding.pageSearch.setVisibility(index == TAB_SEARCH ? View.VISIBLE : View.GONE);
		binding.pageMine.setVisibility(index == TAB_MINE ? View.VISIBLE : View.GONE);
		binding.navHome.getRoot().setSelected(index == TAB_HOME);
		binding.navCategory.getRoot().setSelected(index == TAB_CATEGORY);
		binding.navSearch.getRoot().setSelected(index == TAB_SEARCH);
		binding.navMine.getRoot().setSelected(index == TAB_MINE);
		backToHome.setEnabled(index != TAB_HOME);
		if (index == TAB_SEARCH && query.isEmpty()) {
			binding.search.requestFocus();
			binding.search.post(() -> {
				InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.showSoftInput(binding.search, InputMethodManager.SHOW_IMPLICIT);
			});
		} else {
			hideKeyboard();
		}
	}

	private void scrollToTop(int index) {
		RecyclerView[] lists = {binding.homeList, binding.categoryList, binding.searchList, binding.mineList};
		lists[index].smoothScrollToPosition(0);
	}

	@Override
	public void onResume() {
		super.onResume();
		AppCompatActivity activity = (AppCompatActivity) requireActivity();
		ActionBar actionBar = activity.getSupportActionBar();
		if (actionBar != null) actionBar.hide();
		Window window = activity.getWindow();
		savedDecorBackground = window.getDecorView().getBackground();
		window.getDecorView().setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{
				ContextCompat.getColor(activity, R.color.hub_header_top),
				ContextCompat.getColor(activity, R.color.hub_bg),
				ContextCompat.getColor(activity, R.color.hub_surface)}));
		setStatusBarColor(window, ContextCompat.getColor(activity, R.color.hub_header_top));
		if (!games.isEmpty()) refreshInstalled();
	}

	@Override
	public void onPause() {
		super.onPause();
		AppCompatActivity activity = (AppCompatActivity) requireActivity();
		ActionBar actionBar = activity.getSupportActionBar();
		if (actionBar != null) actionBar.show();
		activity.getWindow().getDecorView().setBackground(savedDecorBackground);
		setStatusBarColor(activity.getWindow(), ContextCompat.getColor(activity, R.color.primary));
	}

	@SuppressWarnings("deprecation")
	private static void setStatusBarColor(Window window, int color) {
		window.setStatusBarColor(color);
	}

	private void reload() {
		Context context = requireContext().getApplicationContext();
		disposables.add(Single.fromCallable(() -> GameLibrary.load(context))
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(this::onGamesLoaded, e -> {
					e.printStackTrace();
					Toast.makeText(context, "游戏列表加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
				}));
	}

	private void refreshInstalled() {
		Context context = requireContext().getApplicationContext();
		disposables.add(Single.fromCallable(() -> GameLibrary.installedIds(context))
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(ids -> {
					ids.retainAll(byId.keySet());
					installed = ids;
					renderAll();
				}, Throwable::printStackTrace));
	}

	private void onGamesLoaded(List<GameEntry> list) {
		if (binding == null) return;
		allGames = list;
		byId.clear();
		for (GameEntry g : list) byId.put(g.id, g);
		applyPlatform();
		refreshInstalled();
	}

	private ViewPlatformFilterBinding[] filters() {
		return new ViewPlatformFilterBinding[]{binding.filterHome, binding.filterCategory, binding.filterSearch};
	}

	private void setPlatform(String p) {
		if (Objects.equals(platform, p)) return;
		platform = p;
		PreferenceManager.getDefaultSharedPreferences(requireContext()).edit().putString(PREF_PLATFORM, p).apply();
		category = null;
		applyPlatform();
		RecyclerView[] lists = {binding.homeList, binding.categoryList, binding.searchList, binding.mineList};
		for (RecyclerView l : lists) l.scrollToPosition(0);
	}

	private boolean isMrpHall() {
		return GameEntry.PLATFORM_MRP.equals(platform);
	}

	private String hallName() {
		return isMrpHall() ? "MRP" : "Java";
	}

	private boolean visible(GameEntry g) {
		return platform.equals(g.platformKey());
	}

	private void applyPlatform() {
		int mrp = 0;
		for (GameEntry g : allGames) if (g.isMrp()) mrp++;
		List<GameEntry> list = new ArrayList<>();
		for (GameEntry g : allGames) if (visible(g)) list.add(g);
		games = list;
		for (ViewPlatformFilterBinding f : filters()) {
			f.chipJava.setSelected(!isMrpHall());
			f.chipMrp.setSelected(isMrpHall());
			f.chipJavaCount.setText((allGames.size() - mrp) + " 款");
			f.chipMrpCount.setText(mrp + " 款");
		}
		binding.homeEmptyTitle.setText(isMrpHall() ? "MRP 游戏厅还是空的" : "Java 游戏厅还是空的");
		binding.homeEmptyDesc.setText(isMrpHall()
				? "MRP 是斯凯「冒泡」平台的手机游戏（.mrp 文件）。\n把 MRP 游戏包（zip）或 mrp 文件拷到手机里，\n点下面的按钮选择它，支持多选。"
				: "把 Java 游戏包（zip）拷到手机里，点下面的按钮选择它，\n游戏会自动识别、分类、放进游戏厅。\n也可以直接选 jar 文件，支持多选。");
		binding.homeImport.setText(isMrpHall() ? "导入 MRP 游戏包" : "导入 Java 游戏包");
		categories = new ArrayList<>(GameLibrary.categories(list));
		categories.remove(GameLibrary.ALL);
		if (category == null || !categories.contains(category)) {
			category = categories.isEmpty() ? null : categories.get(0);
		}
		buildSideList();
		renderAll();
	}

	private int playedCount() {
		int n = 0;
		for (GameEntry g : games) if (installed.contains(g.id)) n++;
		return n;
	}

	private String subtitle() {
		return games.size() + " 款经典 " + hallName() + " 手机游戏 · 已玩过 " + playedCount() + " 款";
	}

	private void renderAll() {
		if (binding == null) return;
		binding.homeSubtitle.setText(subtitle());
		renderHome();
		renderCategory();
		renderSearch();
		renderMine();
	}

	private List<GameEntry> inCategory(String c) {
		List<GameEntry> result = new ArrayList<>();
		for (GameEntry g : games) if (c.equals(g.category)) result.add(g);
		return result;
	}

	private static List<GameEntry> shuffledToday(List<GameEntry> list, int salt) {
		Calendar c = Calendar.getInstance();
		List<GameEntry> copy = new ArrayList<>(list);
		Collections.shuffle(copy, new Random(c.get(Calendar.YEAR) * 1000L + c.get(Calendar.DAY_OF_YEAR) + salt * 7919L));
		return copy;
	}

	private static <T> List<T> take(List<T> list, int n) {
		return new ArrayList<>(list.subList(0, Math.min(n, list.size())));
	}

	private void renderHome() {
		List<Object> items = new ArrayList<>();
		List<GameEntry> recent = new ArrayList<>();
		for (String id : GameLibrary.recentIds(requireContext())) {
			GameEntry g = byId.get(id);
			if (g != null && visible(g)) recent.add(g);
		}
		if (!recent.isEmpty()) {
			items.add(new GameAdapter.Section("继续游戏", ""));
			items.add(new GameAdapter.Row(recent));
		}
		binding.homeEmpty.setVisibility(games.isEmpty() ? View.VISIBLE : View.GONE);
		if (!games.isEmpty()) {
			items.add(new GameAdapter.Section("今日推荐", "每天换一批"));
			items.add(new GameAdapter.Row(take(shuffledToday(games, 0), 12)));
		}
		for (int i = 0; i < categories.size(); i++) {
			String c = categories.get(i);
			List<GameEntry> list = inCategory(c);
			items.add(new GameAdapter.Section(c, list.size() + " 款", "更多 ›", () -> openCategory(c)));
			items.add(new GameAdapter.Row(take(shuffledToday(list, i + 1), 12)));
		}
		homeAdapter.setItems(items, installed);
	}

	private void buildSideList() {
		binding.sideList.removeAllViews();
		sideItems.clear();
		for (String c : categories) {
			ItemHubSideBinding item = ItemHubSideBinding.inflate(getLayoutInflater(), binding.sideList, false);
			item.label.setText(c);
			item.getRoot().setOnClickListener(v -> {
				category = c;
				updateSideSelection();
				renderCategory();
				binding.categoryList.scrollToPosition(0);
			});
			binding.sideList.addView(item.getRoot());
			sideItems.put(c, item.getRoot());
		}
		updateSideSelection();
	}

	private void updateSideSelection() {
		for (Map.Entry<String, View> e : sideItems.entrySet()) e.getValue().setSelected(e.getKey().equals(category));
	}

	private void openCategory(String c) {
		category = c;
		updateSideSelection();
		renderCategory();
		binding.categoryList.scrollToPosition(0);
		selectTab(TAB_CATEGORY);
	}

	private void renderCategory() {
		if (category == null) return;
		List<GameEntry> list = inCategory(category);
		List<Object> items = new ArrayList<>();
		items.add(new GameAdapter.Section(category, list.size() + " 款"));
		items.addAll(list);
		categoryAdapter.setItems(items, installed);
	}

	private void renderSearch() {
		List<Object> items = new ArrayList<>();
		if (query.isEmpty()) {
			items.add(new GameAdapter.Section("大家都在玩", "支持输入拼音首字母，如 qxwy"));
			items.addAll(take(shuffledToday(games, 99), 15));
			binding.empty.setVisibility(View.GONE);
		} else {
			List<GameEntry> result = new ArrayList<>();
			for (GameEntry g : games) if (g.matches(query)) result.add(g);
			items.add(new GameAdapter.Section("搜索结果", result.size() + " 款"));
			items.addAll(result);
			binding.empty.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
		}
		searchAdapter.setItems(items, installed);
	}

	private void renderMine() {
		Context context = requireContext();
		List<Object> items = new ArrayList<>();
		items.add(new GameAdapter.Stats(games.size(), playedCount(), categories.size()));
		items.add(new GameAdapter.Section("添加游戏", ""));
		items.add(new GameAdapter.Menu(R.drawable.ic_hub_folder, "导入游戏包",
				isMrpHall() ? "选择 MRP 游戏包（zip）或 mrp 文件，支持多选" : "选择 Java 游戏包（zip）或 jar 文件，支持多选",
				"", this::importLocal));
		items.add(new GameAdapter.Menu(R.drawable.ic_nav_category, "游戏源", "添加别人分享的游戏清单网址",
				GameSources.getSources(context).size() + " 个", this::showSources));
		items.add(new GameAdapter.Section(hallName() + " 游戏设置", ""));
		int keyboard = isMrpHall() ? KeyboardStyles.getMrpDefault(context) : KeyboardStyles.getDefault(context);
		items.add(new GameAdapter.Menu(R.drawable.ic_hub_keyboard, hallName() + " 默认键盘样式",
				"所有 " + hallName() + " 游戏的虚拟键盘布局", KeyboardStyles.shortName(keyboard), this::pickDefaultKeyboard));
		items.add(new GameAdapter.Menu(R.drawable.ic_hub_settings, "模拟器设置", "声音、振动、屏幕常亮等", "",
				() -> startActivity(new Intent(requireActivity(), SettingsActivity.class))));
		items.add(new GameAdapter.Menu(R.drawable.ic_hub_info, "关于", "基于开源项目 J2ME Loader", "",
				() -> new AboutDialogFragment().show(getChildFragmentManager(), "about")));
		List<GameEntry> played = new ArrayList<>();
		for (GameEntry g : games) if (installed.contains(g.id)) played.add(g);
		if (!played.isEmpty()) {
			items.add(new GameAdapter.Section("玩过的游戏", played.size() + " 款"));
			items.addAll(played);
		}
		mineAdapter.setItems(items, installed);
	}

	@Override
	public void onGameClick(GameEntry game) {
		hideKeyboard();
		boolean mrpReady = game.isMrp() && GameSources.jarFile(requireContext(), game).isFile();
		if (installed.contains(game.id) || mrpReady) {
			GameLauncher.start(requireContext(), game, false);
			return;
		}
		DialogGameLoadingBinding loading = DialogGameLoadingBinding.inflate(getLayoutInflater());
		GameIcons.bind(loading.icon, loading.letter, game);
		loading.name.setText(game.name);
		loading.category.setText(game.isMrp() ? game.category + " · MRP 游戏"
				: game.category + " · 键盘样式：" + KeyboardStyles.shortName(GameLauncher.getKeyboard(requireContext(), game)));
		AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.HubDialog_Bare)
				.setView(loading.getRoot())
				.setCancelable(false)
				.show();
		Handler main = new Handler(Looper.getMainLooper());
		Application app = requireActivity().getApplication();
		disposables.add(Completable.fromAction(() -> GameLauncher.install(app, appRepository, game,
						percent -> main.post(() -> loading.status.setText("正在下载游戏… " + percent + "%"))))
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(() -> {
					dialog.dismiss();
					installed.add(game.id);
					if (isAdded()) GameLauncher.start(requireContext(), game, false);
				}, e -> {
					e.printStackTrace();
					dialog.dismiss();
					if (!isAdded()) return;
					new AlertDialog.Builder(requireContext(), R.style.HubDialog)
							.setTitle("游戏准备失败")
							.setMessage(game.name + "\n\n" + (e.getMessage() != null ? e.getMessage() : e.toString()))
							.setPositiveButton("知道了", null)
							.show();
				}));
	}

	@Override
	public void onGameLongClick(GameEntry game) {
		Context context = requireContext();
		if (game.isMrp()) {
			int mrpKeyboard = GameLauncher.getKeyboard(context, game);
			List<String> options = new ArrayList<>();
			options.add("▶  开始游戏");
			options.add("⌨  键盘样式：" + KeyboardStyles.shortName(mrpKeyboard));
			if (!game.isBuiltin()) options.add("🗑  删除这个游戏");
			new AlertDialog.Builder(context, R.style.HubDialog)
					.setTitle(game.name + "（MRP）")
					.setItems(options.toArray(new String[0]), (d, which) -> {
						if (which == 0) {
							onGameClick(game);
						} else if (which == 1) {
							KeyboardStyles.pick(context, "「" + game.name + "」的键盘样式", "只对这个游戏生效", mrpKeyboard, true,
									type -> {
										GameLauncher.setKeyboard(context, game, type);
										Toast.makeText(context, "已切换为「" + KeyboardStyles.title(type) + "」", Toast.LENGTH_SHORT).show();
									});
						} else {
							confirmDelete(game);
						}
					})
					.show();
			return;
		}
		int keyboard = GameLauncher.getKeyboard(context, game);
		List<String> options = new ArrayList<>();
		options.add("▶  开始游戏");
		options.add("⌨  键盘样式：" + KeyboardStyles.shortName(keyboard));
		options.add("⚙  画面与按键高级设置");
		if (!game.isBuiltin()) options.add("🗑  删除这个游戏");
		new AlertDialog.Builder(context, R.style.HubDialog)
				.setTitle(game.name)
				.setItems(options.toArray(new String[0]), (d, which) -> {
					if (which == 0) {
						onGameClick(game);
					} else if (which == 1) {
						KeyboardStyles.pick(context, "「" + game.name + "」的键盘样式", "只对这个游戏生效", keyboard, type -> {
							GameLauncher.setKeyboard(context, game, type);
							Toast.makeText(context, "已切换为「" + KeyboardStyles.title(type) + "」", Toast.LENGTH_SHORT).show();
						});
					} else if (which == 2) {
						if (installed.contains(game.id)) GameLauncher.start(context, game, true);
						else Toast.makeText(context, "请先打开一次游戏，再进行高级设置", Toast.LENGTH_SHORT).show();
					} else {
						confirmDelete(game);
					}
				})
				.show();
	}

	private void confirmDelete(GameEntry game) {
		Context context = requireContext();
		String message = game.source == GameEntry.SOURCE_LOCAL
				? "游戏文件、存档和按键设置会全部删除，无法恢复。"
				: "游戏文件、存档和按键设置会全部删除，无法恢复；游戏仍在游戏源里，可以重新下载。";
		new AlertDialog.Builder(context, R.style.HubDialog)
				.setTitle("删除「" + game.name + "」？")
				.setMessage(message)
				.setNegativeButton("取消", null)
				.setPositiveButton("删除", (d, w) -> disposables.add(Completable.fromAction(() -> {
							GameLauncher.uninstall(context, game);
							GameSources.deleteGame(context, game);
						})
						.subscribeOn(Schedulers.io())
						.observeOn(AndroidSchedulers.mainThread())
						.subscribe(() -> {
							Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show();
							reload();
						}, Throwable::printStackTrace)))
				.show();
	}

	private void showAddGameDialog() {
		String[] options = {"📦  导入游戏包（zip / jar）", "🌐  添加游戏源（网址）", "📋  管理游戏源"};
		new AlertDialog.Builder(requireContext(), R.style.HubDialog)
				.setTitle("添加游戏")
				.setItems(options, (d, which) -> {
					if (which == 0) importLocal();
					else if (which == 1) promptAddSource();
					else showSources();
				})
				.show();
	}

	private void importLocal() {
		try {
			importLauncher.launch(null);
		} catch (Exception e) {
			Toast.makeText(requireContext(), R.string.error_no_picker, Toast.LENGTH_SHORT).show();
		}
	}

	private void onImportPicked(List<Uri> uris) {
		if (uris == null || uris.isEmpty()) return;
		Context context = requireContext().getApplicationContext();
		DialogImportProgressBinding view = DialogImportProgressBinding.inflate(getLayoutInflater());
		view.imported.setText("0");
		view.progress.setIndeterminate(true);
		view.current.setText("正在读取游戏包…");
		AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.HubDialog_Bare)
				.setView(view.getRoot())
				.setCancelable(false)
				.show();
		if (dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		AtomicBoolean cancelled = new AtomicBoolean();
		view.action.setOnClickListener(v -> {
			cancelled.set(true);
			view.action.setEnabled(false);
			view.action.setText("正在停止…");
		});
		Set<String> existing = new HashSet<>();
		for (GameEntry g : allGames) existing.add(GameEntry.dedupKey(g.platform, g.name));
		Handler main = new Handler(Looper.getMainLooper());
		GameSources.ImportListener listener = (percent, r, current) -> {
			int imported = r.imported, duplicates = r.duplicates, invalid = r.invalid;
			main.post(() -> {
				view.imported.setText(String.valueOf(imported));
				if (percent >= 0) {
					view.progress.setIndeterminate(false);
					view.progress.setProgress(percent);
					view.percent.setText(percent + "%");
				}
				view.current.setText("正在识别：" + current);
				view.skipped.setText("跳过重复 " + duplicates + " 个 · 无效 " + invalid + " 个");
			});
		};
		disposables.add(Single.fromCallable(() -> new GameSources.Importer(context, existing, listener, cancelled).run(uris))
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(r -> {
					main.removeCallbacksAndMessages(null);
					view.title.setText(cancelled.get() ? "已停止导入" : "导入完成 🎉");
					view.hint.setText(r.imported > 0 ? "游戏已经放进游戏厅，去首页看看吧" : "没有新的游戏需要导入");
					view.imported.setText(String.valueOf(r.imported));
					view.progress.setIndeterminate(false);
					view.progress.setProgress(100);
					view.percent.setText(cancelled.get() ? "" : "100%");
					view.current.setText("新增 " + r.imported + " 款游戏"
							+ (r.repaired > 0 ? "（其中修复了 " + r.repaired + " 款之前名字乱码的）" : ""));
					view.skipped.setText("跳过重复 " + r.duplicates + " 个 · 无效 " + r.invalid + " 个");
					view.action.setEnabled(true);
					view.action.setText("完成");
					view.action.setOnClickListener(v -> {
						dialog.dismiss();
						selectTab(TAB_HOME);
					});
					reload();
				}, e -> {
					e.printStackTrace();
					dialog.dismiss();
					reload();
					if (!isAdded()) return;
					new AlertDialog.Builder(requireContext(), R.style.HubDialog)
							.setTitle("导入出错")
							.setMessage("已导入的游戏会保留。\n\n" + e.getMessage())
							.setPositiveButton("知道了", null)
							.show();
				}));
	}

	private void showSources() {
		Context context = requireContext();
		float dp = getResources().getDisplayMetrics().density;
		List<GameSources.Source> sources = GameSources.getSources(context);
		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (20 * dp);
		root.setPadding(pad, (int) (8 * dp), pad, 0);
		AlertDialog dialog = new AlertDialog.Builder(context, R.style.HubDialog)
				.setTitle("游戏源")
				.setView(root)
				.setPositiveButton("添加游戏源", (d, w) -> promptAddSource())
				.setNegativeButton("关闭", null)
				.create();
		if (sources.isEmpty()) {
			root.addView(text(context, "还没有添加游戏源。\n\n游戏源是一个网址，指向一份 JSON 格式的游戏清单。"
					+ "添加后清单里的游戏会按分类出现在首页和分类页里，点击时自动下载。", 14, R.color.hub_text_secondary));
		}
		for (GameSources.Source s : sources) {
			LinearLayout row = new LinearLayout(context);
			row.setOrientation(LinearLayout.VERTICAL);
			row.setPadding(0, (int) (10 * dp), 0, (int) (10 * dp));
			row.addView(text(context, s.name + "  ·  " + s.count + " 款", 15, R.color.hub_text));
			row.addView(text(context, s.url, 11, R.color.hub_text_secondary));
			LinearLayout actions = new LinearLayout(context);
			actions.setGravity(Gravity.END);
			TextView refresh = text(context, "刷新", 13, R.color.hub_accent);
			TextView remove = text(context, "删除", 13, R.color.hub_accent);
			refresh.setPadding((int) (12 * dp), (int) (6 * dp), (int) (12 * dp), 0);
			remove.setPadding((int) (12 * dp), (int) (6 * dp), 0, 0);
			refresh.setOnClickListener(v -> {
				dialog.dismiss();
				loadSource(s.url);
			});
			remove.setOnClickListener(v -> {
				dialog.dismiss();
				new AlertDialog.Builder(context, R.style.HubDialog)
						.setTitle("删除游戏源「" + s.name + "」？")
						.setMessage("会同时删除从这个源下载的游戏文件，存档会保留。")
						.setNegativeButton("取消", null)
						.setPositiveButton("删除", (d, w) -> disposables.add(Completable.fromAction(() -> GameSources.removeSource(context, s.url))
								.subscribeOn(Schedulers.io())
								.observeOn(AndroidSchedulers.mainThread())
								.subscribe(this::reload, Throwable::printStackTrace)))
						.show();
			});
			actions.addView(refresh);
			actions.addView(remove);
			row.addView(actions);
			root.addView(row);
		}
		dialog.show();
	}

	private void promptAddSource() {
		Context context = requireContext();
		float dp = getResources().getDisplayMetrics().density;
		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (20 * dp);
		root.setPadding(pad, (int) (8 * dp), pad, 0);
		root.addView(text(context, "输入游戏清单（catalog.json）的网址：", 13, R.color.hub_text_secondary));
		EditText input = new EditText(context);
		input.setHint("https://example.com/games/catalog.json");
		input.setSingleLine(true);
		input.setTextColor(ContextCompat.getColor(context, R.color.hub_text));
		input.setHintTextColor(ContextCompat.getColor(context, R.color.hub_text_secondary));
		input.setTextSize(14);
		root.addView(input);
		new AlertDialog.Builder(context, R.style.HubDialog)
				.setTitle("添加游戏源")
				.setView(root)
				.setNegativeButton("取消", null)
				.setPositiveButton("添加", (d, w) -> {
					String url = input.getText().toString().trim();
					if (!url.startsWith("http://") && !url.startsWith("https://")) {
						Toast.makeText(context, "请输入以 http:// 或 https:// 开头的网址", Toast.LENGTH_SHORT).show();
						return;
					}
					loadSource(url);
				})
				.show();
	}

	private void loadSource(String url) {
		Context context = requireContext().getApplicationContext();
		AlertDialog busy = showBusy("正在读取游戏清单…");
		disposables.add(Single.fromCallable(() -> GameSources.refreshSource(context, url))
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(source -> {
					busy.dismiss();
					Toast.makeText(context, "「" + source.name + "」已更新，共 " + source.count + " 款游戏", Toast.LENGTH_LONG).show();
					reload();
				}, e -> {
					busy.dismiss();
					if (!isAdded()) return;
					new AlertDialog.Builder(requireContext(), R.style.HubDialog)
							.setTitle("读取游戏源失败")
							.setMessage(url + "\n\n" + e.getMessage())
							.setPositiveButton("知道了", null)
							.show();
				}));
	}

	private AlertDialog showBusy(String message) {
		Context context = requireContext();
		float dp = getResources().getDisplayMetrics().density;
		LinearLayout root = new LinearLayout(context);
		root.setGravity(Gravity.CENTER_VERTICAL);
		int pad = (int) (24 * dp);
		root.setPadding(pad, pad, pad, pad);
		ProgressBar bar = new ProgressBar(context);
		bar.setIndeterminateTintList(ContextCompat.getColorStateList(context, R.color.hub_accent));
		root.addView(bar, new LinearLayout.LayoutParams((int) (36 * dp), (int) (36 * dp)));
		TextView tv = text(context, message, 15, R.color.hub_text);
		tv.setPadding((int) (16 * dp), 0, 0, 0);
		root.addView(tv);
		return new AlertDialog.Builder(context, R.style.HubDialog).setView(root).setCancelable(false).show();
	}

	private static TextView text(Context context, String s, float size, int color) {
		TextView tv = new TextView(context);
		tv.setText(s);
		tv.setTextSize(size);
		tv.setTextColor(ContextCompat.getColor(context, color));
		return tv;
	}

	private void pickDefaultKeyboard() {
		Context context = requireContext();
		if (isMrpHall()) {
			KeyboardStyles.pick(context, "MRP 默认键盘样式", "应用到所有 MRP 游戏，单个游戏可长按单独设置",
					KeyboardStyles.getMrpDefault(context), true, type -> {
						KeyboardStyles.setMrpDefault(context, type);
						MrpRuntime.clearKeyboards(context);
						renderMine();
						Toast.makeText(context, "所有 MRP 游戏已切换为「" + KeyboardStyles.title(type) + "」",
								Toast.LENGTH_SHORT).show();
					});
			return;
		}
		KeyboardStyles.pick(context, "Java 默认键盘样式", "应用到所有 Java 游戏，单个游戏可长按单独设置",
				KeyboardStyles.getDefault(context), type -> {
					KeyboardStyles.setDefault(context, type);
					renderMine();
					List<GameEntry> all = games;
					disposables.add(Completable.fromAction(() -> GameLauncher.setKeyboardForAll(all, type))
							.subscribeOn(Schedulers.io())
							.observeOn(AndroidSchedulers.mainThread())
							.subscribe(() -> Toast.makeText(context, "所有游戏已切换为「" + KeyboardStyles.title(type) + "」",
									Toast.LENGTH_SHORT).show(), Throwable::printStackTrace));
				});
	}

	private void hideKeyboard() {
		if (binding == null) return;
		InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(binding.search.getWindowToken(), 0);
		binding.search.clearFocus();
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		binding = null;
	}

	@Override
	public void onDestroy() {
		disposables.dispose();
		super.onDestroy();
	}
}
