package com.cs5520.assignments.numad22su_team24_puddle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.cs5520.assignments.numad22su_team24_puddle.Model.ApiLoaderBar;
import com.cs5520.assignments.numad22su_team24_puddle.Utils.FirebaseDB;
import com.cs5520.assignments.numad22su_team24_puddle.Utils.LocationPermissionActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.HashMap;
import java.util.Locale;

public class CreatePuddle extends AppCompatActivity {


    private double lat =0.0 , lng = 0.0;

//    FusedLocationProviderClient fusedLocationProviderClient;

    // Widgets
    TextInputEditText puddleName;
    TextInputEditText puddleBio;
    SwitchMaterial isPrivate;
    Slider range;
    Button createPuddle;
    RelativeLayout addBanner;
    AutoCompleteTextView menu;
    ImageView selectedImg;
    TextView rangeVal;

    Uri imgUri = null;
    String bannerUrl = "";
    Handler apiHandler = new Handler();
    double selectedRange = 0.0;
    final ApiLoaderBar apiBar = new ApiLoaderBar(CreatePuddle.this);

    ActivityResultLauncher<Intent> startActivityForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == AppCompatActivity.RESULT_OK) {
                    Intent data = result.getData();
                    imgUri = data.getData();
                    selectedImg.setImageURI(imgUri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_puddle);

        // Initializing Widgets
        puddleName = findViewById(R.id.puddle_name_txt);
        puddleBio = findViewById(R.id.puddle_bio_txt);
        isPrivate = findViewById(R.id.private_pud);
        range = findViewById(R.id.range_filter);
        createPuddle = findViewById(R.id.create);
        addBanner = findViewById(R.id.add_banner);
        menu = findViewById(R.id.category_menu);
        selectedImg = findViewById(R.id.selected_pud_img);
        rangeVal = findViewById(R.id.range_val);

        // Options for the menu in the dropdown
        String[] options = {
                Category.SELECT.toString(),
                Category.MUSIC.toString(),
                Category.TRAVEL.toString(),
                Category.FINANCE.toString(),
                Category.EDUCATION.toString()
        };

        ArrayAdapter arrayAdapter = new ArrayAdapter(this, R.layout.category_options, options);
        menu.setText(arrayAdapter.getItem(0).toString(), false);
        menu.setAdapter(arrayAdapter);

        addBanner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent gallery = new Intent();
                gallery.setAction(Intent.ACTION_GET_CONTENT);
                gallery.setType("image/*");
                startActivityForResult.launch(gallery);
            }
        });

        range.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {

            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                Log.i("chanegd value", String.valueOf(slider.getValue()));
                selectedRange = slider.getValue();
                rangeVal.setText(String.valueOf(slider.getValue()) + "m");
            }
        });
    }

    public void makeApiCalls(View view){
        if(checkValues()){
            apiBar.showDialog();
            getLocation();
        } else {
            Toast.makeText(this, "Please provide all values to proceed", Toast.LENGTH_SHORT).show();
        }
    }

    public void uploadImageToStore(Uri uri){
        StorageReference ref = FirebaseDB.storageRef.child(System.currentTimeMillis() + "." + getFileExtension(uri));
        ref.putFile(uri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {

                ref.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                bannerUrl = uri.toString();

                                if(bannerUrl != null || bannerUrl != ""){
                                    sendPuddleToFirebase(); // 3. Send the collected data to Firebase
                                } else {
                                    apiHandler.post(()->{
                                        apiBar.dismissBar();
                                    });
                                    Toast.makeText(CreatePuddle.this, "Error sending image to Store", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }).start();
                    }
                });
            }
        }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(@NonNull UploadTask.TaskSnapshot snapshot) {

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                apiBar.dismissBar();
                Toast.makeText(CreatePuddle.this, "Failed to upload banner", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public String getFileExtension(Uri muri){
        ContentResolver cr = getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        return mime.getExtensionFromMimeType(cr.getType(muri));
    }

    public void sendPuddleToFirebase(){
        DatabaseReference ref = FirebaseDB.getDataReference("Puddles");
        DatabaseReference ref2 = FirebaseDB.getDataReference("Members");

        String pud_key = ref.push().getKey();
        HashMap<String, Object> puddleMap = new HashMap<>();
        puddleMap.put("name", puddleName.getText().toString());
        puddleMap.put("bio", puddleBio.getText().toString());
        puddleMap.put("isPrivate", String.valueOf(isPrivate.isChecked()));
        puddleMap.put("bannerUrl", bannerUrl);
        puddleMap.put("range", String.valueOf(range.getValue()));
        puddleMap.put("category", menu.getText().toString());
        puddleMap.put("count", 1);

        HashMap<String, String> location = new HashMap<>();
        location.put("latitude", String.valueOf(lat));
        location.put("longitude", String.valueOf(lng));

        puddleMap.put("Location", location);

        HashMap<String, String> members = new HashMap<>();
        members.put("profile_url", "https://firebasestorage.googleapis.com/v0/b/android-chat-85561.appspot.com/o/1659461577945.jpg?alt=media&token=a6433924-64d3-4b1e-9e3f-b52140976eb3");
        members.put("username", "HarshitG24");

        ref.child(pud_key).setValue(puddleMap);
        ref2.child(pud_key).push().setValue(members);

        // update the puddles in my list
        updateMyPuddles(pud_key);

        apiHandler.post(() -> {
            apiBar.dismissBar();
            Intent intent = new Intent(CreatePuddle.this, PuddleChatroomActivity.class);
            CreatePuddle.this.startActivity(intent);
        });

    }

    public void getLocation(){

        if (LocationPermissionActivity.checkMapServices(this)) {
            if (LocationPermissionActivity.locationPermissionGranted) {
                FusedLocationProviderClient fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    apiBar.dismissBar();
                    return;
                }
                fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, location -> {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (location != null) {
                                lat = location.getLatitude();
                                lng = location.getLongitude();
                                uploadImageToStore(imgUri); // 2. Upload image to firestore if location fetch success
                            } else {
                                apiHandler.post(() -> {
                                    apiBar.dismissBar();
                                });
                                Toast.makeText(CreatePuddle.this, "Failed to get user location, Please provide location acccess to continue", Toast.LENGTH_LONG).show();
                            }
                        }
                    }).start();
                });

            }
            else{
                apiBar.dismissBar();
                LocationPermissionActivity.requestPermission(this, LocationPermissionActivity.REQUEST_CODE_FINE_LOCATION);
            }

        } else {
            apiBar.dismissBar();
        }
    }

    public void updateMyPuddles(String key){
        DatabaseReference myPuddles = FirebaseDB.getDataReference("Users").child(FirebaseDB.getCurrentUser().getUid());
        myPuddles.child("my_puddles").push().setValue(key);
    }

    public boolean checkValues(){
        boolean allValues = false;

        if(
           puddleName.getText().toString() != "" &&
           puddleBio.getText().toString() != "" && imgUri != null &&
           selectedRange > 0.0 && menu.getText().toString().toLowerCase() != "select"
        ) {
            allValues = true;
        }

        return allValues;
    }
}