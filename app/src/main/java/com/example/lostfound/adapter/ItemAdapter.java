package com.example.lostfound.adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lostfound.ItemDetailActivity;
import com.example.lostfound.R;
import com.example.lostfound.model.LostItem;
import com.example.lostfound.util.TimeUtils;

import java.util.List;

// Binds LostItem data to each RecyclerView row
public class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ViewHolder> {

    private final List<LostItem> list;
    private final Context        context;

    public ItemAdapter(List<LostItem> list, Context context) {
        this.list    = list;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LostItem item = list.get(position);

        String title     = safe(item.getTitle(),       "Unnamed item");
        String type      = safe(item.getType(),        "");
        String location  = safe(item.getLocation(),    "");
        String phone     = safe(item.getPhone(),       "");
        String desc      = safe(item.getDescription(), "");
        String date      = safe(item.getDate(),        "");
        String imageUri  = safe(item.getImageUri(),    "");
        String timestamp = safe(item.getTimestamp(),   "");

        holder.txtTitle.setText(title);
        holder.txtType.setText(type);
        holder.txtLocation.setText(location);
        holder.txtTime.setText(TimeUtils.getTimeAgo(timestamp));

        // Red badge for Lost, green for Found
        holder.txtType.setBackgroundColor(
                item.isLost() ? Color.parseColor("#E53935") : Color.parseColor("#43A047")
        );

        // Clear before reuse — avoids stale images on recycled rows
        holder.imgThumb.setImageDrawable(null);
        holder.imgThumb.setBackgroundColor(Color.parseColor("#EEEEEE"));

        if (!imageUri.isEmpty()) {
            try {
                holder.imgThumb.setImageURI(Uri.parse(imageUri));
            } catch (Exception ignored) {}
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ItemDetailActivity.class);
            intent.putExtra(ItemDetailActivity.EXTRA_ID,          item.getId());
            intent.putExtra(ItemDetailActivity.EXTRA_TITLE,       title);
            intent.putExtra(ItemDetailActivity.EXTRA_TYPE,        type);
            intent.putExtra(ItemDetailActivity.EXTRA_PHONE,       phone);
            intent.putExtra(ItemDetailActivity.EXTRA_DESCRIPTION, desc);
            intent.putExtra(ItemDetailActivity.EXTRA_DATE,        date);
            intent.putExtra(ItemDetailActivity.EXTRA_LOCATION,    location);
            intent.putExtra(ItemDetailActivity.EXTRA_IMAGE_URI,   imageUri);
            intent.putExtra(ItemDetailActivity.EXTRA_TIMESTAMP,   timestamp);
            // Pass coordinates so detail screen can show the map pin
            intent.putExtra(ItemDetailActivity.EXTRA_LATITUDE,    item.getLatitude());
            intent.putExtra(ItemDetailActivity.EXTRA_LONGITUDE,   item.getLongitude());
            intent.putExtra(ItemDetailActivity.EXTRA_IS_LOST,     item.isLost());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgThumb;
        TextView  txtTitle, txtType, txtLocation, txtTime;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgThumb    = itemView.findViewById(R.id.imgThumb);
            txtTitle    = itemView.findViewById(R.id.txtTitle);
            txtType     = itemView.findViewById(R.id.txtType);
            txtLocation = itemView.findViewById(R.id.txtLocation);
            txtTime     = itemView.findViewById(R.id.txtTime);
        }
    }

    private String safe(String value, String fallback) {
        return (value == null || value.isEmpty()) ? fallback : value;
    }
}