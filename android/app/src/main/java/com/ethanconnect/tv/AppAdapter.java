package com.ethanconnect.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

    public interface TileListener {
        void onTileFocused(int position, View card);
        void onTileClicked(AppEntry entry);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;

        ViewHolder(View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card);
            banner = itemView.findViewById(R.id.banner);
            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.label);
            name = itemView.findViewById(R.id.name);
        }
    }

    private final Context ctx;
    private final Config config;
    private final TileListener listener;
    private final List<AppEntry> items = new ArrayList<>();
    private final Set<String> leanbackPackages = new HashSet<>();

    AppAdapter(Context ctx, Config config, TileListener listener) {
        this.ctx = ctx;
        this.config = config;
        this.listener = listener;
        setHasStableIds(true);
    }

    void setLeanbackPackages(Set<String> packages) {
        this.leanbackPackages.clear();
        this.leanbackPackages.addAll(packages);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_app, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppEntry entry = items.get(position);
        holder.itemView.setTag(entry);

        // Keep app artwork clean; the name below the card is the single title.
        holder.label.setVisibility(View.GONE);

        if (entry.name != null && !entry.name.isEmpty()) {
            holder.name.setText(entry.name);
            holder.name.setVisibility(View.VISIBLE);
            holder.name.setAlpha(1f);
            holder.name.setTranslationY(0f);
        } else {
            holder.name.setVisibility(View.GONE);
        }

        if (entry.banner != null) {
            holder.banner.setImageDrawable(entry.banner);
            holder.banner.setVisibility(View.VISIBLE);
            holder.icon.setVisibility(View.GONE);
        } else if (entry.icon != null) {
            holder.icon.setImageDrawable(entry.icon);
            holder.icon.setVisibility(View.VISIBLE);
            holder.banner.setVisibility(View.GONE);
        } else {
            holder.banner.setVisibility(View.GONE);
            holder.icon.setVisibility(View.GONE);
        }

        holder.card.setOnClickListener(v -> {
            if (listener != null) listener.onTileClicked(entry);
        });

        holder.card.setOnFocusChangeListener((v, focused) -> {
            float scale = focused ? 1.10f : 1f;
            v.animate().cancel();
            v.animate().scaleX(scale).scaleY(scale).setDuration(180).start();
            if (listener != null && focused) {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) listener.onTileFocused(pos, v);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        AppEntry e = items.get(position);
        return e.component == null ? position : e.component.flattenToShortString().hashCode();
    }

    void reload(List<ResolveInfo> leanback) {
        PackageManager pm = ctx.getPackageManager();
        Set<String> tvPackages = new TreeSet<>();
        for (ResolveInfo ri : leanback) {
            if (ri.activityInfo != null) tvPackages.add(ri.activityInfo.packageName);
        }

        List<AppEntry> fresh = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (ResolveInfo ri : leanback) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (!ri.activityInfo.exported || pkg.equals(ctx.getPackageName())) continue;
            if (config.hiddenApps.contains(pkg)) continue;

            ComponentName component = new ComponentName(pkg, ri.activityInfo.name);
            String label = config.appLabels.containsKey(pkg)
                    ? config.appLabels.get(pkg)
                    : String.valueOf(ri.loadLabel(pm));
            if (!seen.add(pkg + "\n" + label)) continue;

            Drawable banner = loadBanner(pm, ri);
            Drawable icon = banner == null ? ri.loadIcon(pm) : null;
            fresh.add(new AppEntry(AppEntry.APP, label, component, banner, icon, label));
        }

        fresh.sort((a, b) -> a.label.compareToIgnoreCase(b.label));

        List<AppEntry> docked = new ArrayList<>();
        List<AppEntry> rest = new ArrayList<>();
        for (AppEntry e : fresh) {
            if (config.dockApps.contains(e.pkg)) docked.add(e);
            else rest.add(e);
        }

        items.clear();
        items.addAll(docked);
        items.add(new AppEntry(AppEntry.MIRROR, ctx.getString(R.string.mirror), null, null,
                ctx.getDrawable(R.drawable.ic_mirror),
                ctx.getString(R.string.mirror)));
        items.addAll(rest);

        notifyDataSetChanged();
    }

    private Drawable loadBanner(PackageManager pm, ResolveInfo ri) {
        int id = ri.activityInfo.banner;
        if (id == 0) id = ri.activityInfo.applicationInfo.banner;
        if (id == 0) return null;
        try {
            Resources resources = pm.getResourcesForApplication(ri.activityInfo.applicationInfo);
            DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
            int density = Math.max(DisplayMetrics.DENSITY_XHIGH, metrics.densityDpi);
            return resources.getDrawableForDensity(id, density, null);
        } catch (Exception ignored) {
        }
        Drawable banner = ri.activityInfo.loadBanner(pm);
        if (banner != null && isBannerShaped(banner.getIntrinsicWidth(), banner.getIntrinsicHeight())) {
            return banner;
        }
        return null;
    }

    private boolean isBannerShaped(int w, int h) {
        return w > 0 && h > 0 && (float) w / h > 1.5f;
    }

    void launch(AppEntry entry) {
        try {
            if (entry.isMirror()) {
                Intent i = new Intent(ctx, MirrorActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
                return;
            }
            if (entry.isHomeSettings()) {
                Intent i = new Intent(android.provider.Settings.ACTION_HOME_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
                return;
            }
            Intent i = new Intent(Intent.ACTION_MAIN)
                    .setComponent(entry.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            if (leanbackPackages.contains(entry.pkg)) {
                i.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            }
            String action = config.launchActions.get(entry.pkg);
            if (action != null) {
                Intent byAction = new Intent(action).setPackage(entry.pkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                if (ctx.getPackageManager().resolveActivity(byAction, 0) != null) {
                    i = byAction;
                }
            }
            ctx.startActivity(i);
            if (ctx instanceof Activity) {
                ((Activity) ctx).overridePendingTransition(0, 0);
            }
        } catch (Exception ex) {
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, entry.label), Toast.LENGTH_SHORT).show();
        }
    }
}
