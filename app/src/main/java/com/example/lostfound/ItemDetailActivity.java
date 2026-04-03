package com.example.lostfound;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.lostfound.database.DBHelper;
import com.example.lostfound.util.TimeUtils;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

/*
    ItemDetailActivity
    - Full details for a single lost/found advert
    - Embedded map with zoom controls shows exact pin location
    - "Get Directions" button shown only for Found items — opens Google Maps nav
    - Remove button pinned to bottom of screen
*/
public class ItemDetailActivity extends AppCompatActivity implements OnMapReadyCallback {

    // Intent extra keys
    public static final String EXTRA_ID          = "id";
    public static final String EXTRA_TITLE       = "title";
    public static final String EXTRA_TYPE        = "type";
    public static final String EXTRA_PHONE       = "phone";
    public static final String EXTRA_DESCRIPTION = "description";
    public static final String EXTRA_DATE        = "date";
    public static final String EXTRA_LOCATION    = "location";
    public static final String EXTRA_IMAGE_URI   = "imageUri";
    public static final String EXTRA_TIMESTAMP   = "timestamp";
    public static final String EXTRA_LATITUDE    = "latitude";
    public static final String EXTRA_LONGITUDE   = "longitude";
    public static final String EXTRA_IS_LOST     = "isLost";

    private TextView  txtName, txtType, txtPhone, txtDescription,
            txtDate, txtLocation, txtTimestamp;
    private ImageView imageViewItem, btnBack;
    private Button    btnRemove, btnDirections;

    private DBHelper dbHelper;
    private int      itemId    = -1;
    private double   latitude  = 0;
    private double   longitude = 0;
    private String   itemTitle = "";
    private boolean  isLost    = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_detail);

        dbHelper = new DBHelper(this);

        bindViews();
        loadData();
        setupActions();
        setupMap();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbHelper.close();
    }

    // ─── BIND ──────────────────────────────────────────────────────────────────

    private void bindViews() {
        txtName        = findViewById(R.id.txtName);
        txtType        = findViewById(R.id.txtType);
        txtPhone       = findViewById(R.id.txtPhone);
        txtDescription = findViewById(R.id.txtDescription);
        txtDate        = findViewById(R.id.txtDate);
        txtLocation    = findViewById(R.id.txtLocation);
        txtTimestamp   = findViewById(R.id.txtTimestamp);
        imageViewItem  = findViewById(R.id.imageViewItem);
        btnBack        = findViewById(R.id.btnBack);
        btnRemove      = findViewById(R.id.btnRemove);
        btnDirections  = findViewById(R.id.btnDirections);
    }

    // ─── LOAD ──────────────────────────────────────────────────────────────────

    private void loadData() {
        itemId    = getIntent().getIntExtra(EXTRA_ID, -1);
        latitude  = getIntent().getDoubleExtra(EXTRA_LATITUDE, 0);
        longitude = getIntent().getDoubleExtra(EXTRA_LONGITUDE, 0);
        isLost    = getIntent().getBooleanExtra(EXTRA_IS_LOST, true);

        itemTitle          = safe(getIntent().getStringExtra(EXTRA_TITLE));
        String type        = safe(getIntent().getStringExtra(EXTRA_TYPE));
        String phone       = safe(getIntent().getStringExtra(EXTRA_PHONE));
        String description = safe(getIntent().getStringExtra(EXTRA_DESCRIPTION));
        String date        = safe(getIntent().getStringExtra(EXTRA_DATE));
        String location    = safe(getIntent().getStringExtra(EXTRA_LOCATION));
        String timestamp   = safe(getIntent().getStringExtra(EXTRA_TIMESTAMP));
        String imageUri    = safe(getIntent().getStringExtra(EXTRA_IMAGE_URI));

        txtName.setText(itemTitle);
        txtType.setText(type);
        txtLocation.setText(location);
        txtDate.setText(date);
        txtDescription.setText(description);
        txtPhone.setText(phone);
        txtTimestamp.setText(TimeUtils.getTimeAgo(timestamp));

        // Badge colour matches list row
        txtType.setBackgroundColor(isLost
                ? Color.parseColor("#E53935")
                : Color.parseColor("#43A047"));

        // Directions button — always visible.
        // Greyed out only when both lat/lng are zero AND location text is empty (no address at all).
        boolean hasCoords   = latitude != 0 || longitude != 0;
        boolean hasLocation = !location.isEmpty();
        btnDirections.setVisibility(View.VISIBLE);
        if (!hasCoords && !hasLocation) {
            btnDirections.setAlpha(0.4f);
            btnDirections.setEnabled(false);
        }

        // Hide map card if no coordinates saved for this item
        if (latitude == 0 && longitude == 0) {
            findViewById(R.id.mapCard).setVisibility(View.GONE);
        }

        if (!imageUri.isEmpty()) {
            try {
                imageViewItem.setImageURI(Uri.parse(imageUri));
            } catch (Exception ignored) {
                imageViewItem.setImageDrawable(null);
            }
        }
    }

    // ─── MAP ───────────────────────────────────────────────────────────────────

    private void setupMap() {
        if (latitude == 0 && longitude == 0) return;

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.detailMap);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        // Keep zoom controls but disable pan/tilt — user can zoom in
        // to confirm exact location without accidentally navigating away
        googleMap.getUiSettings().setScrollGesturesEnabled(false);
        googleMap.getUiSettings().setRotateGesturesEnabled(false);
        googleMap.getUiSettings().setTiltGesturesEnabled(false);
        googleMap.getUiSettings().setZoomGesturesEnabled(true);   // pinch zoom allowed
        googleMap.getUiSettings().setZoomControlsEnabled(true);   // +/- buttons
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);

        LatLng position = new LatLng(latitude, longitude);

        float pinColour = isLost
                ? BitmapDescriptorFactory.HUE_RED
                : BitmapDescriptorFactory.HUE_GREEN;

        googleMap.addMarker(new MarkerOptions()
                .position(position)
                .title(itemTitle)
                .icon(BitmapDescriptorFactory.defaultMarker(pinColour))
        );

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15f));
    }

    // ─── ACTIONS ───────────────────────────────────────────────────────────────

    private void setupActions() {
        btnBack.setOnClickListener(v -> finish());
        btnRemove.setOnClickListener(v -> showDeleteConfirmation());

        // Directions button — uses exact coords when available, falls back to location text search
        btnDirections.setOnClickListener(v -> {
            Intent mapIntent;
            if (latitude != 0 || longitude != 0) {
                // Precise coordinates — navigate directly
                Uri gmmUri = Uri.parse(
                        "google.navigation:q=" + latitude + "," + longitude + "&mode=d"
                );
                mapIntent = new Intent(Intent.ACTION_VIEW, gmmUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                if (mapIntent.resolveActivity(getPackageManager()) == null) {
                    // Fallback: geo URI for any map app
                    Uri geoUri = Uri.parse("geo:" + latitude + "," + longitude
                            + "?q=" + latitude + "," + longitude
                            + "(" + Uri.encode(itemTitle) + ")");
                    mapIntent = new Intent(Intent.ACTION_VIEW, geoUri);
                }
            } else {
                // No coords — search by location text (suburb name etc.)
                String query = itemTitle.isEmpty()
                        ? safe(getIntent().getStringExtra(EXTRA_LOCATION))
                        : itemTitle + " " + safe(getIntent().getStringExtra(EXTRA_LOCATION));
                Uri searchUri = Uri.parse("geo:0,0?q=" + Uri.encode(query));
                mapIntent = new Intent(Intent.ACTION_VIEW, searchUri);
            }
            startActivity(mapIntent);
        });
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Remove Post")
                .setMessage("Are you sure you want to remove this advert?")
                .setPositiveButton("Remove", (dialog, which) -> deleteItem())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteItem() {
        if (itemId == -1) return;
        int result = dbHelper.deleteItem(itemId);
        if (result > 0) {
            Toast.makeText(this, "Advert removed", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Remove failed — try again", Toast.LENGTH_SHORT).show();
        }
    }

    // ─── UTIL ──────────────────────────────────────────────────────────────────

    private String safe(String value) {
        return value == null ? "" : value;
    }
}