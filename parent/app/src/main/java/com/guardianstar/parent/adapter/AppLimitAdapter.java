package com.guardianstar.parent.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.guardianstar.parent.R;
import com.guardianstar.parent.data.AppLimit;

import java.util.List;

public class AppLimitAdapter extends RecyclerView.Adapter<AppLimitAdapter.ViewHolder> {

    private Context context;
    private List<AppLimit> limitList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(int position);
        void onToggleClick(int position, boolean isChecked);
        void onDeleteClick(int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public AppLimitAdapter(Context context, List<AppLimit> limitList) {
        this.context = context;
        this.limitList = limitList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_app_limit, parent, false);
        return new ViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppLimit limit = limitList.get(position);

        holder.tvAppName.setText(limit.getAppName());
        holder.switchLimit.setChecked(limit.isLimitEnabled());

        long used = limit.getUsedMinutesToday();
        long limitTime = limit.getDailyLimitMinutes();
        int percent = (int) (used * 100 / Math.max(1, limitTime));

        holder.tvUsage.setText(String.format("%d/%d 分钟", used, limitTime));
        holder.progressBar.setProgress(Math.min(percent, 100));

        if (percent >= 100) {
            holder.tvUsage.setTextColor(ContextCompat.getColor(context, R.color.error));
            holder.progressBar.setProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.error)));
        } else if (percent >= 80) {
            holder.tvUsage.setTextColor(ContextCompat.getColor(context, R.color.warning));
            holder.progressBar.setProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.warning)));
        } else {
            holder.tvUsage.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            holder.progressBar.setProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.success)));
        }

        if (limit.isWhitelist()) {
            holder.tvWhitelist.setVisibility(View.VISIBLE);
        } else {
            holder.tvWhitelist.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return limitList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAppName;
        TextView tvUsage;
        TextView tvWhitelist;
        ProgressBar progressBar;
        Switch switchLimit;
        ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView, final OnItemClickListener listener) {
            super(itemView);
            tvAppName = itemView.findViewById(R.id.tvAppName);
            tvUsage = itemView.findViewById(R.id.tvUsage);
            tvWhitelist = itemView.findViewById(R.id.tvWhitelist);
            progressBar = itemView.findViewById(R.id.progressBar);
            switchLimit = itemView.findViewById(R.id.switchLimit);
            btnDelete = itemView.findViewById(R.id.btnDelete);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onItemClick(position);
                    }
                }
            });

            switchLimit.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onToggleClick(position, isChecked);
                    }
                }
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onDeleteClick(position);
                    }
                }
            });
        }
    }
}
