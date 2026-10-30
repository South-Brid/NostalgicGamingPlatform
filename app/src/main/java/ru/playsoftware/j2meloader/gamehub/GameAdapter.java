package ru.playsoftware.j2meloader.gamehub;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import ru.playsoftware.j2meloader.databinding.ItemGameCardBinding;
import ru.playsoftware.j2meloader.databinding.ItemHubMenuBinding;
import ru.playsoftware.j2meloader.databinding.ItemHubRecentBinding;
import ru.playsoftware.j2meloader.databinding.ItemHubSectionBinding;
import ru.playsoftware.j2meloader.databinding.ItemHubStatsBinding;

class GameAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	private static final int TYPE_SECTION = 0;
	private static final int TYPE_ROW = 1;
	private static final int TYPE_GAME = 2;
	private static final int TYPE_MENU = 3;
	private static final int TYPE_STATS = 4;

	interface Listener {
		void onGameClick(GameEntry game);

		void onGameLongClick(GameEntry game);
	}

	static final class Section {
		final String title;
		final String count;
		final String action;
		final Runnable onAction;

		Section(String title, String count) {
			this(title, count, null, null);
		}

		Section(String title, String count, String action, Runnable onAction) {
			this.title = title;
			this.count = count;
			this.action = action;
			this.onAction = onAction;
		}
	}

	static final class Row {
		final List<GameEntry> games;

		Row(List<GameEntry> games) {
			this.games = games;
		}
	}

	static final class Menu {
		final int icon;
		final String title;
		final String desc;
		final String value;
		final Runnable onClick;

		Menu(int icon, String title, String desc, String value, Runnable onClick) {
			this.icon = icon;
			this.title = title;
			this.desc = desc;
			this.value = value;
			this.onClick = onClick;
		}
	}

	static final class Stats {
		final int total;
		final int played;
		final int added;

		Stats(int total, int played, int added) {
			this.total = total;
			this.played = played;
			this.added = added;
		}
	}

	private final Listener listener;
	private List<Object> items = new ArrayList<>();
	private Set<String> installed = Collections.emptySet();

	GameAdapter(Listener listener) {
		this.listener = listener;
	}

	@SuppressLint("NotifyDataSetChanged")
	void setItems(List<Object> items, Set<String> installed) {
		this.items = items;
		this.installed = installed;
		notifyDataSetChanged();
	}

	boolean isFullSpan(int position) {
		return getItemViewType(position) != TYPE_GAME;
	}

	@Override
	public int getItemViewType(int position) {
		Object item = items.get(position);
		if (item instanceof Section) return TYPE_SECTION;
		if (item instanceof Row) return TYPE_ROW;
		if (item instanceof Menu) return TYPE_MENU;
		if (item instanceof Stats) return TYPE_STATS;
		return TYPE_GAME;
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		switch (viewType) {
			case TYPE_SECTION:
				return new Holder<>(ItemHubSectionBinding.inflate(inflater, parent, false));
			case TYPE_ROW:
				return new RowHolder(ItemHubRecentBinding.inflate(inflater, parent, false));
			case TYPE_MENU:
				return new Holder<>(ItemHubMenuBinding.inflate(inflater, parent, false));
			case TYPE_STATS:
				return new Holder<>(ItemHubStatsBinding.inflate(inflater, parent, false));
			default:
				return new Holder<>(ItemGameCardBinding.inflate(inflater, parent, false));
		}
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		Object item = items.get(position);
		if (holder instanceof RowHolder) {
			((RowHolder) holder).cards.setGames(((Row) item).games);
			return;
		}
		Object binding = ((Holder<?>) holder).binding;
		if (item instanceof Section) {
			Section s = (Section) item;
			ItemHubSectionBinding b = (ItemHubSectionBinding) binding;
			b.title.setText(s.title);
			b.count.setText(s.count);
			b.action.setVisibility(s.action != null ? View.VISIBLE : View.GONE);
			b.action.setText(s.action);
			b.action.setOnClickListener(s.onAction != null ? v -> s.onAction.run() : null);
		} else if (item instanceof Menu) {
			Menu m = (Menu) item;
			ItemHubMenuBinding b = (ItemHubMenuBinding) binding;
			b.icon.setImageResource(m.icon);
			b.title.setText(m.title);
			b.desc.setText(m.desc);
			b.desc.setVisibility(m.desc != null ? View.VISIBLE : View.GONE);
			b.value.setText(m.value);
			b.getRoot().setOnClickListener(v -> m.onClick.run());
		} else if (item instanceof Stats) {
			Stats s = (Stats) item;
			ItemHubStatsBinding b = (ItemHubStatsBinding) binding;
			b.total.setText(String.valueOf(s.total));
			b.played.setText(String.valueOf(s.played));
			b.added.setText(String.valueOf(s.added));
		} else {
			bindCard((ItemGameCardBinding) binding, (GameEntry) item);
		}
	}

	@Override
	public int getItemCount() {
		return items.size();
	}

	private void bindCard(ItemGameCardBinding b, GameEntry game) {
		GameIcons.bind(b.icon, b.letter, game);
		b.name.setText(game.name);
		b.category.setText(game.category);
		b.ready.setVisibility(installed.contains(game.id) ? View.VISIBLE : View.GONE);
		b.getRoot().setOnClickListener(v -> listener.onGameClick(game));
		b.getRoot().setOnLongClickListener(v -> {
			listener.onGameLongClick(game);
			return true;
		});
	}

	static class Holder<B extends androidx.viewbinding.ViewBinding> extends RecyclerView.ViewHolder {
		final B binding;

		Holder(B binding) {
			super(binding.getRoot());
			this.binding = binding;
		}
	}

	class RowHolder extends RecyclerView.ViewHolder {
		final RowCards cards = new RowCards();

		RowHolder(ItemHubRecentBinding binding) {
			super(binding.getRoot());
			binding.recentList.setLayoutManager(
					new LinearLayoutManager(binding.getRoot().getContext(), RecyclerView.HORIZONTAL, false));
			binding.recentList.setAdapter(cards);
		}
	}

	private class RowCards extends RecyclerView.Adapter<Holder<ItemGameCardBinding>> {
		private List<GameEntry> games = Collections.emptyList();

		@SuppressLint("NotifyDataSetChanged")
		void setGames(List<GameEntry> games) {
			this.games = games;
			notifyDataSetChanged();
		}

		@NonNull
		@Override
		public Holder<ItemGameCardBinding> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			ItemGameCardBinding b = ItemGameCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
			b.getRoot().getLayoutParams().width = (int) (92 * parent.getResources().getDisplayMetrics().density);
			return new Holder<>(b);
		}

		@Override
		public void onBindViewHolder(@NonNull Holder<ItemGameCardBinding> holder, int position) {
			bindCard(holder.binding, games.get(position));
		}

		@Override
		public int getItemCount() {
			return games.size();
		}
	}
}
