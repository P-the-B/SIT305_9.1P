package com.example.lostfound.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// Shared time formatting — used by ItemAdapter and ItemDetailActivity
public class TimeUtils {

    private TimeUtils() {}

    public static String getTimeAgo(String timestamp) {
        if (timestamp == null || timestamp.trim().isEmpty()) return "";

        try {
            // DBHelper stores as ISO-8601: yyyy-MM-dd'T'HH:mm:ss
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date past = sdf.parse(timestamp);
            Date now  = new Date();

            if (past == null) return timestamp;

            long diff  = now.getTime() - past.getTime();
            long mins  = diff / (60 * 1000);
            long hours = diff / (60 * 60 * 1000);
            long days  = diff / (24 * 60 * 60 * 1000);

            if (mins  < 1)  return "Just now";
            if (mins  < 60) return mins  + " mins ago";
            if (hours < 24) return hours + " hours ago";
            return days + " days ago";

        } catch (Exception e) {
            return timestamp; // fallback — show raw string rather than crash
        }
    }
}