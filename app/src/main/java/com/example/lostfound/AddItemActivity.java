package com.example.lostfound;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.lostfound.database.DBHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/*
    AddItemActivity
    - Lost/Found advert form with full inline validation
    - setErrorEnabled(false) collapses error space when cleared — no layout shifting
    - Location: tap field → Places autocomplete OR GPS icon → reverse geocode
    - Date: tap field → DatePickerDialog
    - Image: ACTION_OPEN_DOCUMENT with persistable URI permission
*/
public class AddItemActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST   = 1;
    private static final int AUTOCOMPLETE_REQUEST = 2;
    private static final int LOCATION_PERMISSION  = 1001;

    // UI
    private ImageView         imagePreview;
    private Button            btnSelectImage, btnSave;
    private ImageButton       btnGetCurrentLocation;
    private ImageView         btnBack;

    private TextInputLayout   tilTitle, tilName, tilPhone, tilDescription, tilDate, tilLocation;
    private TextInputEditText etTitle, etName, etPhone, etDescription, etDate, etLocation;
    private RadioButton       radioLost;

    // State
    private Uri    imageUri;
    private double latitude  = 0;
    private double longitude = 0;

    private DBHelper                    dbHelper;
    private FusedLocationProviderClient fusedClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_item);

        dbHelper    = new DBHelper(this);
        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getApiKey());
        }

        bindViews();
        setListeners();
    }

    // ─── BIND ──────────────────────────────────────────────────────────────────

    private void bindViews() {
        imagePreview          = findViewById(R.id.imagePreview);
        btnSelectImage        = findViewById(R.id.btnSelectImage);
        btnSave               = findViewById(R.id.btnSave);
        btnGetCurrentLocation = findViewById(R.id.btnGetCurrentLocation);
        btnBack               = findViewById(R.id.btnBack);

        tilTitle       = findViewById(R.id.tilTitle);
        tilName        = findViewById(R.id.tilName);
        tilPhone       = findViewById(R.id.tilPhone);
        tilDescription = findViewById(R.id.tilDescription);
        tilDate        = findViewById(R.id.tilDate);
        tilLocation    = findViewById(R.id.tilLocation);

        etTitle       = findViewById(R.id.etTitle);
        etName        = findViewById(R.id.etName);
        etPhone       = findViewById(R.id.etPhone);
        etDescription = findViewById(R.id.etDescription);
        etDate        = findViewById(R.id.etDate);
        etLocation    = findViewById(R.id.etLocation);

        RadioGroup radioGroupType = findViewById(R.id.radioGroupType);
        radioLost = findViewById(R.id.radioLost);
    }

    // ─── LISTENERS ─────────────────────────────────────────────────────────────

    private void setListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnSelectImage.setOnClickListener(v -> openImagePicker());
        btnSave.setOnClickListener(v -> attemptSave());
        btnGetCurrentLocation.setOnClickListener(v -> getCurrentLocation());

        // Date and location are non-focusable — listen on both wrapper and child
        tilDate.setOnClickListener(v -> showDatePicker());
        etDate.setOnClickListener(v -> showDatePicker());
        tilLocation.setOnClickListener(v -> launchAutocomplete());
        etLocation.setOnClickListener(v -> launchAutocomplete());

        // Inline validation watchers
        etTitle.addTextChangedListener(simpleWatcher(tilTitle));
        etName.addTextChangedListener(nameWatcher(tilName));
        etPhone.addTextChangedListener(phoneWatcher(tilPhone));
        etDescription.addTextChangedListener(simpleWatcher(tilDescription));
    }

    // ─── INLINE VALIDATION WATCHERS ────────────────────────────────────────────

    /**
     * Collapses the error space entirely when the user types anything.
     * setErrorEnabled(false) removes the reserved bottom padding — no ghost space left behind.
     */
    private TextWatcher simpleWatcher(TextInputLayout til) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                til.setErrorEnabled(false);
            }
            @Override public void afterTextChanged(Editable s) {}
        };
    }

    /** Validates name has no digits — re-enables error space only if invalid */
    private TextWatcher nameWatcher(TextInputLayout til) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (s.toString().matches(".*\\d.*")) {
                    til.setErrorEnabled(true);
                    til.setError("Name cannot contain numbers");
                } else {
                    til.setErrorEnabled(false);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        };
    }

    /** Validates phone contains only digits, spaces, +, - */
    private TextWatcher phoneWatcher(TextInputLayout til) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (s.length() > 0 && !s.toString().matches("[0-9+\\-\\s]+")) {
                    til.setErrorEnabled(true);
                    til.setError("Phone number cannot contain letters");
                } else {
                    til.setErrorEnabled(false);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        };
    }

    // ─── DATE PICKER ───────────────────────────────────────────────────────────

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    etDate.setText(String.format(Locale.getDefault(),
                            "%02d/%02d/%04d", day, month + 1, year));
                    tilDate.setErrorEnabled(false);
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        // Block future dates — user cannot select a date after today
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    // ─── PLACES AUTOCOMPLETE ───────────────────────────────────────────────────

    private void launchAutocomplete() {
        try {
            List<Place.Field> fields = Arrays.asList(
                    Place.Field.NAME,
                    Place.Field.ADDRESS,
                    Place.Field.LAT_LNG
            );
            Intent intent = new Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
                    .build(this);
            startActivityForResult(intent, AUTOCOMPLETE_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this,
                    "Autocomplete unavailable — type location or use GPS button",
                    Toast.LENGTH_LONG).show();
            etLocation.setFocusable(true);
            etLocation.setFocusableInTouchMode(true);
            etLocation.requestFocus();
        }
    }

    // ─── GPS LOCATION ──────────────────────────────────────────────────────────

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION);
            return;
        }

        fusedClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                applyLocation(location.getLatitude(), location.getLongitude());
            } else {
                // No cached fix — request a fresh one-shot update
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    LocationRequest req = new LocationRequest.Builder(
                            Priority.PRIORITY_HIGH_ACCURACY, 1000)
                            .setMaxUpdates(1)
                            .build();
                    fusedClient.requestLocationUpdates(req, new LocationCallback() {
                        @Override
                        public void onLocationResult(@NonNull LocationResult result) {
                            fusedClient.removeLocationUpdates(this);
                            if (!result.getLocations().isEmpty()) {
                                android.location.Location loc = result.getLocations().get(0);
                                applyLocation(loc.getLatitude(), loc.getLongitude());
                            } else {
                                Toast.makeText(AddItemActivity.this,
                                        "Couldn't get location — try moving outside or using autocomplete",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }, getMainLooper());
                } else {
                    Toast.makeText(this,
                            "Couldn't get location — try moving outside or using autocomplete",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void applyLocation(double lat, double lng) {
        latitude  = lat;
        longitude = lng;
        tilLocation.setErrorEnabled(false); // collapse error space once location is set

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                StringBuilder sb = new StringBuilder();
                if (addr.getSubLocality() != null) sb.append(addr.getSubLocality()).append(", ");
                if (addr.getLocality()    != null) sb.append(addr.getLocality()).append(", ");
                if (addr.getAdminArea()   != null) sb.append(addr.getAdminArea());
                String label = sb.toString().trim().replaceAll(",$", "");
                etLocation.setText(label.isEmpty() ? addr.getAddressLine(0) : label);
            } else {
                etLocation.setText(String.format(Locale.getDefault(), "%.5f, %.5f", lat, lng));
            }
        } catch (IOException e) {
            etLocation.setText(String.format(Locale.getDefault(), "%.5f, %.5f", lat, lng));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation();
        }
    }

    // ─── IMAGE PICKER ──────────────────────────────────────────────────────────

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    // ─── ACTIVITY RESULTS ──────────────────────────────────────────────────────

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            imageUri = data.getData();
            if (imageUri != null) {
                getContentResolver().takePersistableUriPermission(
                        imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                    imagePreview.setImageBitmap(bitmap);
                } catch (IOException e) {
                    Toast.makeText(this, "Couldn't load image", Toast.LENGTH_SHORT).show();
                }
            }

        } else if (requestCode == AUTOCOMPLETE_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                Place place = Autocomplete.getPlaceFromIntent(data);
                if (place.getLatLng() != null) {
                    latitude  = place.getLatLng().latitude;
                    longitude = place.getLatLng().longitude;
                }
                String label = place.getName() != null ? place.getName() : place.getAddress();
                etLocation.setText(label);
                tilLocation.setErrorEnabled(false); // collapse error space on valid selection

            } else if (resultCode == AutocompleteActivity.RESULT_ERROR && data != null) {
                Toast.makeText(this,
                        "Autocomplete error — check Places API is enabled on your key",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    // ─── VALIDATION + SAVE ─────────────────────────────────────────────────────

    private void attemptSave() {
        String title       = safeText(etTitle);
        String name        = safeText(etName);
        String phone       = safeText(etPhone);
        String description = safeText(etDescription);
        String date        = safeText(etDate);
        String locationStr = safeText(etLocation);
        boolean isLost     = radioLost.isChecked();

        boolean valid = true;

        // Title — required
        if (title.isEmpty()) {
            tilTitle.setErrorEnabled(true);
            tilTitle.setError("Title is required");
            if (valid) etTitle.requestFocus();
            valid = false;
        }

        // Name — required, no digits
        if (name.isEmpty()) {
            tilName.setErrorEnabled(true);
            tilName.setError("Name is required");
            if (valid) etName.requestFocus();
            valid = false;
        } else if (name.matches(".*\\d.*")) {
            tilName.setErrorEnabled(true);
            tilName.setError("Name cannot contain numbers");
            if (valid) etName.requestFocus();
            valid = false;
        }

        // Phone — optional, but if provided must be numeric and at least 6 digits
        if (!phone.isEmpty()) {
            if (!phone.matches("[0-9+\\-\\s]+")) {
                tilPhone.setErrorEnabled(true);
                tilPhone.setError("Phone number cannot contain letters");
                if (valid) etPhone.requestFocus();
                valid = false;
            } else if (phone.replaceAll("[^0-9]", "").length() < 6) {
                tilPhone.setErrorEnabled(true);
                tilPhone.setError("Enter a valid phone number");
                if (valid) etPhone.requestFocus();
                valid = false;
            }
        }

        // Description — required
        if (description.isEmpty()) {
            tilDescription.setErrorEnabled(true);
            tilDescription.setError("Description is required");
            if (valid) etDescription.requestFocus();
            valid = false;
        }

        // Date — required
        if (date.isEmpty()) {
            tilDate.setErrorEnabled(true);
            tilDate.setError("Please select a date");
            valid = false;
        }

        // Location — required
        if (locationStr.isEmpty()) {
            tilLocation.setErrorEnabled(true);
            tilLocation.setError("Location is required — use search or GPS button");
            valid = false;
        }

        if (!valid) return;

        long result = dbHelper.insertItem(
                title, description, date, locationStr,
                name, phone, isLost,
                imageUri != null ? imageUri.toString() : null,
                latitude, longitude
        );

        if (result != -1) {
            Toast.makeText(this, "Advert saved", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Failed to save — try again", Toast.LENGTH_SHORT).show();
        }
    }

    // ─── HELPERS ───────────────────────────────────────────────────────────────

    private String safeText(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private String getApiKey() {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            return ai.metaData.getString("com.google.android.geo.API_KEY");
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbHelper.close();
    }
}