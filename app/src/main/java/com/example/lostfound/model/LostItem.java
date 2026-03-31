package com.example.lostfound.model;

/*
    LostItem
    - Full model for a lost/found advert
    - Includes lat/lng for map markers
    - Includes timestamp (ISO string) for "time ago" display
*/
public class LostItem {

    private int id;
    private String title;
    private String description;
    private String date;
    private String locationText;
    private String name;
    private String phone;
    private boolean isLost;
    private String imageUri;
    private double latitude;
    private double longitude;
    private String timestamp; // ISO-8601 string, e.g. "2026-03-31T14:22:00"

    public LostItem() {}

    public LostItem(int id,
                    String title,
                    String description,
                    String date,
                    String locationText,
                    String name,
                    String phone,
                    boolean isLost,
                    String imageUri,
                    double latitude,
                    double longitude,
                    String timestamp) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.date = date;
        this.locationText = locationText;
        this.name = name;
        this.phone = phone;
        this.isLost = isLost;
        this.imageUri = imageUri;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }

    // ─── Getters ───────────────────────────────────────────

    public int getId()              { return id; }
    public String getTitle()        { return title; }
    public String getDescription()  { return description; }
    public String getDate()         { return date; }
    public String getLocationText() { return locationText; }
    public String getName()         { return name; }
    public String getPhone()        { return phone; }
    public boolean isLost()         { return isLost; }
    public String getImageUri()     { return imageUri; }
    public double getLatitude()     { return latitude; }
    public double getLongitude()    { return longitude; }
    public String getTimestamp()    { return timestamp; }

    // Convenience aliases used by ItemAdapter / ItemDetailActivity
    /** Returns "Lost" or "Found" as a display string */
    public String getType()     { return isLost ? "Lost" : "Found"; }

    /** Alias for locationText — used by adapter */
    public String getLocation() { return locationText; }

    // ─── Setters ───────────────────────────────────────────

    public void setId(int id)                       { this.id = id; }
    public void setTitle(String title)              { this.title = title; }
    public void setDescription(String description)  { this.description = description; }
    public void setDate(String date)                { this.date = date; }
    public void setLocationText(String locationText){ this.locationText = locationText; }
    public void setName(String name)                { this.name = name; }
    public void setPhone(String phone)              { this.phone = phone; }
    public void setLost(boolean lost)               { isLost = lost; }
    public void setImageUri(String imageUri)        { this.imageUri = imageUri; }
    public void setLatitude(double latitude)        { this.latitude = latitude; }
    public void setLongitude(double longitude)      { this.longitude = longitude; }
    public void setTimestamp(String timestamp)      { this.timestamp = timestamp; }
}