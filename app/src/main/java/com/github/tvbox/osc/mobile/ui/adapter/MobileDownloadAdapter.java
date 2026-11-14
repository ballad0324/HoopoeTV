package com.github.tvbox.osc.mobile.ui.adapter;

import com.github.tvbox.osc.mobile.ui.drawable.UiSkin;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 下载文件夹列表：文件名 + 大小 / 修改时间。
 */
public class MobileDownloadAdapter extends RecyclerView.Adapter<MobileDownloadAdapter.Holder> {

    public interface OnItemAction {
        void onClick(File file);

        void onLongClick(File file);
    }

    private final List<File> data = new ArrayList<>();
    private OnItemAction action;

    public void setOnItemAction(OnItemAction action) {
        this.action = action;
    }

    public void setData(List<File> files) {
        data.clear();
        if (files != null) data.addAll(files);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mobile_download, parent, false);
        UiSkin.row(itemView);
        return new Holder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, int position) {
        final File file = data.get(position);
        holder.tvFileName.setText(file.getName());
        holder.tvFileInfo.setText(size(file.length()) + " · "
                + new SimpleDateFormat("MM-dd HH:mm", Locale.US)
                .format(new Date(file.lastModified())));
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (action != null) action.onClick(file);
            }
        });
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (action != null) action.onLongClick(file);
                return true;
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    private String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        float kb = bytes / 1024f;
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb);
        float mb = kb / 1024f;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024f);
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tvFileName;
        TextView tvFileInfo;

        Holder(View view) {
            super(view);
            tvFileName = view.findViewById(R.id.tvFileName);
            tvFileInfo = view.findViewById(R.id.tvFileInfo);
        }
    }
}
