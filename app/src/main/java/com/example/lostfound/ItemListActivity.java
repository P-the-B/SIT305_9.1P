package com.example.lostfound;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lostfound.adapter.ItemAdapter;
import com.example.lostfound.database.DBHelper;
import com.example.lostfound.model.LostItem;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

/*
    ItemListActivity
    - Shows lost/found adverts in a RecyclerView
    - Filter buttons: All / Lost / Found
    - Search bar filters by title
    - Distance toggle + SeekBar — off by default, filters by radius from user location
*/
public class ItemListActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION = 1001;

    private RecyclerView recyclerView;
    private ItemAdapter  adapter;

    private List<LostItem> allItems     = new ArrayList<>();
    private List<LostItem> displayList  = new ArrayList<>();

    private DBHelper                    dbHelper;
    private FusedLocationProviderClient fusedClient;

    // Filter state
    private int     currentFilter   = 0;   // 0=All, 1=Lost, 2=Found
    private String  currentSearch   = "";
    private boolean distanceEnabled = false;
    private int     radiusKm        = 10;

    // User location — populated when toggle is switched on
    private double userLat = 0;
    private double userLng = 0;

    // Views
    private Button       btnAll, btnLost, btnFound;
    private EditText     etSearch;
    private SwitchMaterial switchDistance;
    private View         distanceSliderRow;
    private SeekBar      seekListRadius;
    private TextView     txtListRadius;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_list);

        dbHelper    = new DBHelper(this);
        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        bindViews();
        setupRecycler();
        setupFilterButtons();
        setupSearch();
        setupDistanceFilter();
        loadItems();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadItems();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbHelper.close();
    }

    // ─── BIND ──────────────────────────────────────────────────────────────────

    private void bindViews() {
        recyclerView      = findViewById(R.id.recyclerView);
        btnAll            = findViewById(R.id.btnAll);
        btnLost           = findViewById(R.id.btnLost);
        btnFound          = findViewById(R.id.btnFound);
        etSearch          = findViewById(R.id.etSearch);
        switchDistance    = findViewById(R.id.switchDistance);
        distanceSliderRow = findViewById(R.id.distanceSliderRow);
        seekListRadius    = findViewById(R.id.seekListRadius);
        txtListRadius     = findViewById(R.id.txtListRadius);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
    }

    // ─── RECYCLER ──────────────────────────────────────────────────────────────

    private void setupRecycler() {
        adapter = new ItemAdapter(displayList, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    // ─── LOAD ──────────────────────────────────────────────────────────────────

    private void loadItems() {
        allItems = dbHelper.getAllItems();
        applyFilterAndSearch();
        updateButtonLabels();
    }

    // ─── FILTER + SEARCH + DISTANCE ────────────────────────────────────────────

    private void applyFilterAndSearch() {
        displayList.clear();

        for (LostItem item : allItems) {

            // Type filter
            boolean passesType = (currentFilter == 0)
                    || (currentFilter == 1 && item.isLost())
                    || (currentFilter == 2 && !item.isLost());

            // Title search
            boolean passesSearch = currentSearch.isEmpty()
                    || item.getTitle().toLowerCase().contains(currentSearch.toLowerCase());

            // Distance filter — only applied when toggle is on AND we have a location
            boolean passesDistance = true;
            if (distanceEnabled && (userLat != 0 || userLng != 0)) {
                if (item.getLatitude() == 0 && item.getLongitude() == 0) {
                    // Item has no location saved — exclude from distance filter
                    passesDistance = false;
                } else {
                    passesDistance = dbHelper.withinRadius(
                            userLat, userLng,
                            item.getLatitude(), item.getLongitude(),
                            radiusKm);
                }
            }

            if (passesType && passesSearch && passesDistance) displayList.add(item);
        }

        adapter.notifyDataSetChanged();
    }

    // ─── FILTER BUTTONS ────────────────────────────────────────────────────────

    private void setupFilterButtons() {
        btnAll.setOnClickListener(v -> { currentFilter = 0; applyFilterAndSearch(); updateButtonLabels(); });
        btnLost.setOnClickListener(v -> { currentFilter = 1; applyFilterAndSearch(); updateButtonLabels(); });
        btnFound.setOnClickListener(v -> { currentFilter = 2; applyFilterAndSearch(); updateButtonLabels(); });
    }

    private void updateButtonLabels() {
        int lostCount = 0, foundCount = 0;
        for (LostItem i : allItems) {
            if (i.isLost()) lostCount++; else foundCount++;
        }
        btnAll.setText("ALL ("    + allItems.size() + ")");
        btnLost.setText("LOST ("  + lostCount       + ")");
        btnFound.setText("FOUND ("+ foundCount      + ")");
    }

    // ─── SEARCH ────────────────────────────────────────────────────────────────

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                currentSearch = s.toString();
                applyFilterAndSearch();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    // ─── DISTANCE FILTER ───────────────────────────────────────────────────────

    private void setupDistanceFilter() {
        // SeekBar label update
        seekListRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                radiusKm = progress + 1;
                txtListRadius.setText(radiusKm + " km");
                if (distanceEnabled) applyFilterAndSearch();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar)  {}
        });

        switchDistance.setOnCheckedChangeListener((buttonView, isChecked) -> {
            distanceEnabled = isChecked;
            distanceSliderRow.setVisibility(isChecked ? View.VISIBLE : View.GONE);

            if (isChecked) {
                // Request location the first time the toggle is switched on
                fetchUserLocation();
            } else {
                // Toggle off — immediately re-show all items
                applyFilterAndSearch();
            }
        });
    }

    private void fetchUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION);
            return;
        }

        fusedClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                userLat = location.getLatitude();
                userLng = location.getLongitude();
            }
            // Re-apply even if location is null — filter will pass all items gracefully
            applyFilterAndSearch();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchUserLocation();
        } else {
            // Permission denied — disable the toggle, can't filter without location
            switchDistance.setChecked(false);
        }
    }
}