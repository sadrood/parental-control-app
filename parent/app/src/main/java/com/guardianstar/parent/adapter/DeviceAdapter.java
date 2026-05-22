package com.guardianstar.parent.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.guardianstar.parent.R;
import com.guardianstar.parent.data.Device;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

    private Context context;
    private List<Device> deviceList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public DeviceAdapter(Context context, List<Device> deviceList) {
        this.context = context;
        this.deviceList = deviceList;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_device, parent, false);
        return new DeviceViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        Device device = deviceList.get(position);
        holder.deviceName.setText(device.getDeviceName());
        holder.deviceModel.setText(device.getDeviceModel());

        if (device.isOnline()) {
            holder.statusIndicator.setColorFilter(ContextCompat.getColor(context, R.color.online));
            holder.statusText.setText("在线");
            holder.statusText.setTextColor(ContextCompat.getColor(context, R.color.online));
        } else {
            holder.statusIndicator.setColorFilter(ContextCompat.getColor(context, R.color.offline));
            holder.statusText.setText("离线");
            holder.statusText.setTextColor(ContextCompat.getColor(context, R.color.offline));
        }

        holder.batteryText.setText("电量: " + device.getBatteryLevel() + "%");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        holder.bindTime.setText("绑定时间: " + sdf.format(new Date(device.getBindTime())));
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    public static class DeviceViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView deviceName;
        TextView deviceModel;
        ImageView statusIndicator;
        TextView statusText;
        TextView batteryText;
        TextView bindTime;

        public DeviceViewHolder(@NonNull View itemView, final OnItemClickListener listener) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            deviceName = itemView.findViewById(R.id.deviceName);
            deviceModel = itemView.findViewById(R.id.deviceModel);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
            statusText = itemView.findViewById(R.id.statusText);
            batteryText = itemView.findViewById(R.id.batteryText);
            bindTime = itemView.findViewById(R.id.bindTime);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onItemClick(position);
                    }
                }
            });
        }
    }
}
