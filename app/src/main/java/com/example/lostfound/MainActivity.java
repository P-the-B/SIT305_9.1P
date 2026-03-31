package com.example.lostfound;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.lostfound.database.DBHelper;
import com.example.lostfound.model.LostItem;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

/*
    MainActivity
    - Embedded Google Map (primary view)
    - Floating radius SeekBar filters visible markers
    - Stats strip: lost count / found count / latest date
    - FAB re-centres camera to user location
    - Buttons: Create Advert, Show All Items (list)
*/
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION = 1001;
    private static final int DEFAULT_RADIUS_KM   = 10;

    private GoogleMap                   mMap;
    private FusedLocationProviderClient fusedClient;
    private DBHelper                    dbHelper;

    private double userLat = 0;
    private double userLng = 0;
    private int    radiusKm = DEFAULT_RADIUS_KM;

    private TextView             txtLostCount, txtFoundCount, txtLatestDate, txtRadius;
    private SeekBar              seekRadius;
    private FloatingActionButton fabMyLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper    = new DBHelper(this);
        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        // Init Places SDK using key from manifest meta-data
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getApiKey());
        }

        bindViews();
        setupRadiusSeekBar();
        setupButtons();

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.homeMap);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStats();
        if (mMap != null) refreshMarkers();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbHelper.close();
    }

    // ─── BIND ──────────────────────────────────────────────────────────────────

    private void bindViews() {
        txtLostCount  = findViewById(R.id.lostCount);
        txtFoundCount = findViewById(R.id.foundCount);
        txtLatestDate = findViewById(R.id.latestDate);
        txtRadius     = findViewById(R.id.txtRadius);
        seekRadius    = findViewById(R.id.seekRadius);
        fabMyLocation = findViewById(R.id.fabMyLocation);
    }

    // ─── SEEKBAR ───────────────────────────────────────────────────────────────

    private void setupRadiusSeekBar() {
        seekRadius.setProgress(DEFAULT_RADIUS_KM - 1);
        txtRadius.setText(DEFAULT_RADIUS_KM + " km");

        seekRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                radiusKm = progress + 1;
                txtRadius.setText(radiusKm + " km");
                if (mMap != null) refreshMarkers();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar)  {}
        });
    }

    // ─── BUTTONS ───────────────────────────────────────────────────────────────

    private void setupButtons() {
        findViewById(R.id.btnCreate).setOnClickListener(v ->
                startActivity(new Intent(this, AddItemActivity.class)));

        findViewById(R.id.btnShowAll).setOnClickListener(v ->
                startActivity(new Intent(this, ItemListActivity.class)));

        // FAB: re-centre camera to user location
        fabMyLocation.setOnClickListener(v -> {
            if (userLat != 0 || userLng != 0) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(userLat, userLng), 14f));
            } else {
                Toast.makeText(this, "Location not available yet", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ─── MAP READY ─────────────────────────────────────────────────────────────

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(false);
        requestLocationAndLoadMap();
    }

    // ─── LOCATION ──────────────────────────────────────────────────────────────

    private void requestLocationAndLoadMap() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION);
            return;
        }

        try { mMap.setMyLocationEnabled(true); } catch (SecurityException ignored) {}

        fusedClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                userLat = location.getLatitude();
                userLng = location.getLongitude();
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(userLat, userLng), 12f));
            } else {
                // No fix — default camera to Sydney
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(-33.8688, 151.2093), 10f));
            }
            refreshMarkers();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestLocationAndLoadMap();
        } else {
            refreshMarkers();
        }
    }

    // ─── MARKERS ───────────────────────────────────────────────────────────────

    private void refreshMarkers() {
        mMap.clear();

        // Draw the radius circle if we have a location
        if (userLat != 0 || userLng != 0) {
            mMap.addCircle(new com.google.android.gms.maps.model.CircleOptions()
                    .center(new LatLng(userLat, userLng))
                    .radius(radiusKm * 1000.0) // metres
                    .strokeColor(0xFF1A73E8)   // blue border
                    .fillColor(0x221A73E8)     // very faint blue fill
                    .strokeWidth(3f)
            );
        }

        List<LostItem> items = (userLat != 0 || userLng != 0)
                ? dbHelper.getItemsWithinRadius(userLat, userLng, radiusKm)
                : dbHelper.getAllItems();

        for (LostItem item : items) {
            if (item.getLatitude() == 0 && item.getLongitude() == 0) continue;

            float colour = item.isLost()
                    ? BitmapDescriptorFactory.HUE_RED
                    : BitmapDescriptorFactory.HUE_GREEN;

            mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(item.getLatitude(), item.getLongitude()))
                    .title(item.getTitle())
                    .snippet((item.isLost() ? "Lost" : "Found") + " · " + item.getLocationText())
                    .icon(BitmapDescriptorFactory.defaultMarker(colour))
            );
        }
    }

    // ─── STATS ─────────────────────────────────────────────────────────────────

    private void updateStats() {
        txtLostCount.setText(String.valueOf(dbHelper.getLostCount()));
        txtFoundCount.setText(String.valueOf(dbHelper.getFoundCount()));
        String latest = dbHelper.getLatestDate();
        txtLatestDate.setText(latest != null ? latest : "—");
    }

    // ─── API KEY ───────────────────────────────────────────────────────────────

    /**
     * Reads the Maps API key injected into the manifest by secrets-gradle-plugin.
     * Avoids relying on BuildConfig generation which can be flaky on first sync.
     */
    private String getApiKey() {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            return ai.metaData.getString("com.google.android.geo.API_KEY");
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }
}