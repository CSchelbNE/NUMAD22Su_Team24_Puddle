package com.cs5520.assignments.numad22su_team24_puddle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentResultListener;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.cs5520.assignments.numad22su_team24_puddle.Adapter.MyPuddlesAdapter;
import com.cs5520.assignments.numad22su_team24_puddle.Adapter.PuddleListAdapter;
import com.cs5520.assignments.numad22su_team24_puddle.Model.Puddle;
import com.cs5520.assignments.numad22su_team24_puddle.Model.User;
import com.cs5520.assignments.numad22su_team24_puddle.Utils.FirebaseDB;
import com.cs5520.assignments.numad22su_team24_puddle.Utils.LocationPermissionActivity;
import com.cs5520.assignments.numad22su_team24_puddle.Utils.Util;
import com.cs5520.assignments.numad22su_team24_puddle.chatroom_fragments.EventsFragment;
import com.cs5520.assignments.numad22su_team24_puddle.chatroom_fragments.MessageNotification;
import com.cs5520.assignments.numad22su_team24_puddle.chatroom_fragments.adapters.Message;
import com.cs5520.assignments.numad22su_team24_puddle.services.FilterService;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PuddleListActivity extends AppCompatActivity implements View.OnClickListener {

    // Widgets
    RecyclerView puddleListRecyclerView;
    PuddleListAdapter puddleListAdapter;
    ShapeableImageView profileIcon;
    ImageView createIcon;
    ImageView navigationIcon;
    ImageView filterIcon;
    Button nearMeBtn, myPuddlesBtn;
    View currentView;
    SearchView puddleSearch;
    TextView noResultFound;
    boolean justOpened = true;

    public final static Object categoryObject = new Object();

    // Firebase
    FirebaseUser current_user;
    DatabaseReference userRef;
    DatabaseReference imgRef;
    StorageReference storeRef;

    public static final int REQUEST_CODE_LOCATION_FOR_CREATE = 100;
    public static final int REQUEST_CODE_LOCATION_FOR_NEAR_ME = 101;

    private Handler handler = new Handler();
    private HashMap<String, String> userDetails;
    private Uri imageUri;
    private HashMap<String, Puddle> allPuddlesData;
    private HashMap<String, Puddle> myPuddlesData;
    private HashMap<String, Puddle> myPuddlesDataStored;

    private ArrayList<Puddle> allPuddleList;

    private HashMap<Category, List<Puddle>> categoryPuddlesData;
    private ShimmerFrameLayout shimmerFrameLayout;
    private final String FRAGMENT_ID = "5";
    private MessageNotification notification;
    private ValueEventListener valueEventListener;
    private ArrayList<ValueEventListener> valueEventListeners = new ArrayList<>();
    private ArrayList<DatabaseReference> references = new ArrayList<>();


    private EventsFragment.endShimmerEffectCallback callback = new EventsFragment.endShimmerEffectCallback() {
        @Override
        public void onLayoutInflated() {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    shimmerFrameLayout.stopShimmer();
                    shimmerFrameLayout.setVisibility(View.GONE);
                    puddleListRecyclerView.setVisibility(View.VISIBLE);
//                    if (FirebaseDB.getLocalUser().getMy_puddles().size() == 0){
//                        noResultFound.setVisibility(View.VISIBLE);
//                    } else {
//                        noResultFound.setVisibility(View.GONE);
//                    }
                    if (currentView != null && currentView == myPuddlesBtn && myPuddlesData.size() == 0) {
                        noResultFound.setVisibility(TextView.VISIBLE);
                    } else {
                        noResultFound.setVisibility(TextView.GONE);
                    }

                }
            }, 1200);
        }
    };

    public interface endShimmerEffectCallback {
        void onLayoutInflated();
    }

    public static double filteredDistance = 20.0;
    public static List<String> filteredCategories = Category.getCategoryNames();
    public static int filteredMembership = Integer.MAX_VALUE;
    public static boolean filteredGlobal = true;
    public static boolean filteredPrivate = true;
    Handler filterHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_puddle_list);

        if(FirebaseAuth.getInstance().getCurrentUser() == null) {
            Intent intent = new Intent(PuddleListActivity.this, LoginActivity.class);
            intent.putExtra("showToast", true);
            startActivity(intent);
            finish();
            return;
        }
        justOpened = true;
        Util.isForeground = false;
        Util.isPuddleListForeground = true;
        userDetails = new HashMap<>();
        allPuddlesData = new HashMap<>();
        categoryPuddlesData = new HashMap<>();
        myPuddlesData = new HashMap<>();
        myPuddlesDataStored = new HashMap<>();
        allPuddleList = new ArrayList<>();
        notification = new MessageNotification(this);

        noResultFound = findViewById(R.id.no_result_found);
        noResultFound.setVisibility(View.GONE);

        shimmerFrameLayout = findViewById(R.id.events_shimmer_layout);
        profileIcon = findViewById(R.id.puddle_list_header_profile_icon);
        profileIcon.setOnClickListener(this);
        nearMeBtn = findViewById(R.id.header_near_me_btn);
        nearMeBtn.setOnClickListener(this);
        myPuddlesBtn = findViewById(R.id.header_my_puddles_btn);
        myPuddlesBtn.setOnClickListener(this);
        createIcon = findViewById(R.id.header_create_puddle_icon);
        createIcon.setOnClickListener(this);
        filterIcon = findViewById(R.id.header_filter_icon);
        filterIcon.setOnClickListener(this);
        navigationIcon = findViewById(R.id.header_navigation_icon);
        navigationIcon.setOnClickListener(this);
        puddleSearch = findViewById(R.id.search);
        puddleSearch.clearFocus();
        puddleSearch.setIconified(false);
        notification = new MessageNotification(this);

        puddleSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                puddleSearch.setIconified(false);
            }
        });

        puddleSearch.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (currentView == myPuddlesBtn) {
                    // my puddles
                    if (s.equals("")) {
                        myPuddlesData = myPuddlesDataStored;
//                        puddleListRecyclerView.setLayoutManager(new GridLayoutManager(PuddleListActivity.this, 2));
                        setPuddleLayoutManager();
                        puddleListRecyclerView.setAdapter(new MyPuddlesAdapter(PuddleListActivity.this, myPuddlesData));

                        if (myPuddlesData.size() == 0) {
                            noResultFound.setVisibility(TextView.VISIBLE);
                        } else {
                            noResultFound.setVisibility(TextView.GONE);
                        }
                    } else {
                        filterPuddles(s.toLowerCase());
                    }
                } else {
                    // near me
                    if (s.equals("")) {
                        puddleListRecyclerView.setLayoutManager(new LinearLayoutManager(PuddleListActivity.this));
                        puddleListRecyclerView.setAdapter(new PuddleListAdapter(PuddleListActivity.this, categoryPuddlesData));
//            setSelectedButton(nearMeBtn);
//            setUnselectedButton(myPuddlesBtn);
                    } else {
                        filterNearMeData(s.toLowerCase());
                    }
                }
                return false;
            }
        });


        // Register for the filter results
        handleFilterResults();

        // Api Calls
        fetchCurrentUserData();
        fetchAllPuddles();
        FirebaseDB.fetchAllUsers();

        callback.onLayoutInflated();
        // Initializing Widgets
        puddleListRecyclerView = findViewById(R.id.puddle_list_rv);


        currentView = myPuddlesBtn;
        updateRecyclerView();
        LocationPermissionActivity.checkLocationPermission(this);

        handleAppLink(getIntent());
        if (!Util.renderShimmerEffect.containsKey(Util.generateShimmerEffectID("username", "puddle_list", FRAGMENT_ID)) && getIntent().getStringExtra("new_user") == null) {
            noResultFound.setVisibility(View.GONE);
            shimmerFrameLayout.startShimmer();
            shimmerFrameLayout.setVisibility(View.VISIBLE);
            puddleListRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (callback != null) {
                        callback.onLayoutInflated();
                    }
                    puddleListRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });
            Util.renderShimmerEffect.put(Util.generateShimmerEffectID("username", "puddle_list", FRAGMENT_ID), true);
        } else {
            noResultFound.setVisibility(View.GONE);
            shimmerFrameLayout.stopShimmer();
            shimmerFrameLayout.setVisibility(View.GONE);
            puddleListRecyclerView.setVisibility(View.VISIBLE);
        }




        getSupportFragmentManager().setFragmentResultListener("filter_result", this, new FragmentResultListener() {
            @Override
            public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle bundle) {
                filteredDistance = bundle.getDouble("distance");
                filteredCategories = bundle.getStringArrayList("selected_categories");
                filteredMembership = bundle.getInt("membership_filter");
                filteredGlobal = bundle.getBoolean("is_checked");
                filteredPrivate = bundle.getBoolean("private_puddle");
                Log.d("Filters", "filteredCategories: " + filteredCategories);
                Log.d("Filters", "filteredMembership: " + filteredMembership);
                Log.d("Filters", "filteredGlobal: " + filteredGlobal);
                Log.d("Filters", "filteredPrivate: " + filteredPrivate);
                Log.d("Filters", "Distance: " + filteredDistance);
                updateRecyclerView();
            }
        });
    }

    @Override
    protected void onResume() {
        fetchCurrentUserData();
        super.onResume();
    }

    public void filterNearMeData(String txt) {
        HashMap<Category, List<Puddle>> modifiedCategoryPuddlesData = new HashMap<>();

        for (Map.Entry<Category, List<Puddle>> pud : categoryPuddlesData.entrySet()) {
            Category cat = pud.getKey();
            List<Puddle> list = pud.getValue();
            List<Puddle> modifiedList = new ArrayList<>();

            for (Puddle puddle : list) {
                if (puddle.getName().toLowerCase().contains(txt)) {
                    modifiedList.add(puddle);
                }
            }
            if(!modifiedList.isEmpty()) {
                modifiedCategoryPuddlesData.put(cat, modifiedList);
            }

            puddleListRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            puddleListRecyclerView.setAdapter(new PuddleListAdapter(this, modifiedCategoryPuddlesData));

        }
    }

    public void filterPuddles(String txt) {
        HashMap<String, Puddle> modifiedData = new HashMap<>();

        for (Map.Entry<String, Puddle> map : myPuddlesData.entrySet()) {
            Puddle pud = map.getValue();
            if (pud.getName().toLowerCase().contains(txt)) {
                modifiedData.put(map.getKey(), map.getValue());
            }
        }

        setPuddleLayoutManager();
//        puddleListRecyclerView.setLayoutManager(new GridLayoutManager(PuddleListActivity.this, 2));
        puddleListRecyclerView.setAdapter(new MyPuddlesAdapter(PuddleListActivity.this, modifiedData));

        if (modifiedData.size() == 0) {
            noResultFound.setVisibility(TextView.VISIBLE);
        } else {
            noResultFound.setVisibility(TextView.GONE);
        }
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleAppLink(intent);
    }

    private void handleAppLink(Intent intent) {
        String appLinkAction = intent.getAction();
        Uri appLinkData = intent.getData();
        if (Intent.ACTION_VIEW.equals(appLinkAction) && appLinkData != null) {
//
            String puddleId = appLinkData.getLastPathSegment();
            DatabaseReference puddleRef = FirebaseDB.getDataReference("Puddles").child(puddleId);
            puddleRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Puddle puddle = snapshot.getValue(Puddle.class);
                    if (puddle == null) {
                        Toast.makeText(PuddleListActivity.this, "Invalid Puddle Link!", Toast.LENGTH_SHORT).show();
                    } else {
                        showJoinPuddleDialogue(PuddleListActivity.this, puddle);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }
            });

        }
    }


    public void fetchCurrentUserData() {
        FirebaseUser current_user = FirebaseDB.getCurrentUser();

        DatabaseReference userRef = FirebaseDB.getDataReference("Users").child(current_user.getUid());
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                for (DataSnapshot snap : snapshot) {
//                    userDetails.put(snap.getKey(), snap.getValue(String.class));
//                }
                FirebaseDB.currentUser = snapshot.getValue(User.class);

                if(FirebaseDB.currentUser.getMy_puddles().size() == 0){
                    currentView = nearMeBtn;
                    updateRecyclerView();
                }

               if (!Util.listener.isRegistered()){
                    Util.listener.registerListener(notification);
                }
                if (!FirebaseDB.getLocalUser().getProfile_icon().equals("")) {
                    Glide.with(getApplicationContext()).load(FirebaseDB.getLocalUser().getProfile_icon()).into(profileIcon);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });


    }

    private void handleFilterResults() {
        getSupportFragmentManager().setFragmentResultListener("filter_result", this,
                ((requestKey, result) -> {
                    // result extras can be null if user didn't select them
                    if (result.getStringArrayList("selected_categories") != null) {
                        ArrayList<String> selectedCategories = result.getStringArrayList("selected_categories");
                    }
                    if (result.getString("membership_filter") != null) {
                        String membershipFilter = result.getString("membership_filter");
                    }
                    double distance = result.getDouble("distance");
                    Log.d("here", String.valueOf(result.getBoolean("is_checked")));
                }));
    }

    @Override
    public void onClick(View view) {
        if (view.equals(profileIcon)) {
            startActivity(new Intent(this, ProfileActivity.class));
        } else if (view.equals(nearMeBtn) || view.equals(myPuddlesBtn)) {
            currentView = view;
            filteredDistance = 20.0;
            filteredCategories = Category.getCategoryNames();
            filteredMembership = Integer.MAX_VALUE;
            filteredGlobal = true;
            filteredPrivate = true;
            updateRecyclerView();
        } else if (view.equals(createIcon)) {
            if (LocationPermissionActivity.checkLocationPermission(this)) {
                startActivity(new Intent(PuddleListActivity.this, CreatePuddle.class));
            } else {
                LocationPermissionActivity.requestPermission(this, REQUEST_CODE_LOCATION_FOR_CREATE);
            }

        } else if (view.equals(navigationIcon)) {
            if (LocationPermissionActivity.checkMapServices(this)) {
                if (LocationPermissionActivity.locationPermissionGranted) {
                    startActivity(new Intent(this, MapActivity.class));
                } else {
                    LocationPermissionActivity.requestPermission(this, LocationPermissionActivity.REQUEST_CODE_FINE_LOCATION);
                }
            }
        } else if (view.equals(filterIcon)) {
            BottomFilterModal modal = new BottomFilterModal(filteredDistance, filteredCategories, filteredMembership, filteredGlobal, filteredPrivate, currentView == nearMeBtn);
            modal.show(getSupportFragmentManager(), "filter");
        }
    }

    private void updateRecyclerView() {
        puddleSearch.clearFocus();
        puddleSearch.setQuery("", false);
        // Initializing RecyclerView
        if (currentView.equals(nearMeBtn)) {
//            puddleSearch.setVisibility(SearchView.INVISIBLE);
            noResultFound.setVisibility(View.GONE);
            if (LocationPermissionActivity.checkLocationPermission(this)) {
                getLocation();
            } else {
                LocationPermissionActivity.requestPermission(this, REQUEST_CODE_LOCATION_FOR_NEAR_ME);
            }
        } else {
            List<Puddle> filteredPuddles = new ArrayList<>();
            for (String val : myPuddlesData.keySet()) {
                filteredPuddles.add(myPuddlesData.get(val));
            }
            filteredPuddles = FilterService.filteredPuddlesForMyPuddles(filteredPuddles, filteredCategories, filteredGlobal, filteredMembership, filteredPrivate);
            HashMap<String, Puddle> filteredMap = new HashMap<>();
            for (Puddle filteredPuddle : filteredPuddles) {
                filteredMap.put(filteredPuddle.getId(), filteredPuddle);
            }
//            puddleListRecyclerView.setLayoutManager(new GridLayoutManager(PuddleListActivity.this, 2));
            setPuddleLayoutManager();
            puddleListRecyclerView.setAdapter(new MyPuddlesAdapter(PuddleListActivity.this, filteredMap));
            setSelectedButton(myPuddlesBtn);
            setUnselectedButton(nearMeBtn);

            if (myPuddlesData.size() == 0) {
                noResultFound.setVisibility(SearchView.VISIBLE);
            } else {
                noResultFound.setVisibility(SearchView.GONE);
            }


//            fetchMyPuddles();

        }

    }

    private void setPuddleLayoutManager() {
        if (currentView == myPuddlesBtn) {
            if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                puddleListRecyclerView.setLayoutManager(new GridLayoutManager(PuddleListActivity.this, 2));
            } else {
                puddleListRecyclerView.setLayoutManager(new GridLayoutManager(PuddleListActivity.this, 2));
            }
        }
    }

    private void setSelectedButton(Button btn) {
        btn.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_700));
        btn.setTextColor(ContextCompat.getColor(this, R.color.white));
    }

    private void setUnselectedButton(Button btn) {
        btn.setBackgroundColor(ContextCompat.getColor(this, com.google.android.libraries.places.R.color.quantum_grey200));
        btn.setTextColor(ContextCompat.getColor(this, R.color.black));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case LocationPermissionActivity.PERMISSIONS_REQUEST_ENABLE_GPS: {
                if (LocationPermissionActivity.locationPermissionGranted) {
                    startActivity(new Intent(this, MapActivity.class));
                } else {
                    LocationPermissionActivity.requestPermission(this, LocationPermissionActivity.REQUEST_CODE_FINE_LOCATION);
                }
            }
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case LocationPermissionActivity.REQUEST_CODE_FINE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    LocationPermissionActivity.locationPermissionGranted = true;
                    Toast.makeText(this, "Location access successfully granted", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, MapActivity.class));
                } else {
                    Toast.makeText(this, "Location access is not granted", Toast.LENGTH_SHORT).show();
                }
                break;
            case REQUEST_CODE_LOCATION_FOR_CREATE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    LocationPermissionActivity.locationPermissionGranted = true;
                    Toast.makeText(this, "Location access successfully granted", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(PuddleListActivity.this, CreatePuddle.class));
                } else {
                    Toast.makeText(this, "Location access is not granted", Toast.LENGTH_SHORT).show();
                }
                break;
            case REQUEST_CODE_LOCATION_FOR_NEAR_ME:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    LocationPermissionActivity.locationPermissionGranted = true;
                    Toast.makeText(this, "Location access successfully granted", Toast.LENGTH_SHORT).show();
                    currentView = nearMeBtn;
                    updateRecyclerView();
                } else {
                    Toast.makeText(this, "Location access is not granted", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    public void showJoinPuddleDialogue(Context context, Puddle puddle) {
        Util.isPuddleListForeground = false;
        if (FirebaseDB.getLocalUser() == null) {
            FirebaseUser current_user = FirebaseDB.getCurrentUser();
            DatabaseReference userRef = FirebaseDB.getDataReference("Users").child(current_user.getUid());
            userRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    FirebaseDB.currentUser = snapshot.getValue(User.class);
                    if (FirebaseDB.getLocalUser() != null) {
                        showJoinPuddle(context, puddle);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }
            });
        } else {
            showJoinPuddle(context, puddle);
        }

    }

    public void showJoinPuddle(Context context, Puddle puddle) {


        if (FirebaseDB.getLocalUser().getMy_puddles() != null &&
                FirebaseDB.getLocalUser().getMy_puddles().containsValue(puddle.getId())) {
            Intent intent = new Intent(PuddleListActivity.this, PuddleChatroomActivity.class);
            intent.putExtra("puddleID", puddle.getId());
            context.startActivity(intent);
        } else {
            View layoutView = View.inflate(context, R.layout.puddle_modal, null);
            AlertDialog dialog = new MaterialAlertDialogBuilder(context).setTitle(puddle.getName()).setView(layoutView).create();
            TextView tv = layoutView.findViewById(R.id.puddle_modal_name_tv);
            tv.setText(puddle.getBio());
            ShapeableImageView image = layoutView.findViewById(R.id.puddle_modal_item_image);
            Glide.with(context).load(puddle.getBannerUrl()).into(image);
            MaterialButton button = layoutView.findViewById(R.id.puddle_modal_join_btn);
            button.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(context, PuddleChatroomActivity.class);
                intent.putExtra("puddleID", puddle.getId());
                addPuddlesToList(puddle.getId(), puddle); // api call
                context.startActivity(intent);
            });
            dialog.show();
        }
    }

    public void addPuddlesToList(String pud_id, Puddle pud) {
        boolean alreadyJoined = this.myPuddlesData.containsKey(pud_id);

        new Thread(new Runnable() {
            @Override
            public void run() {
                // 1. Update the id in my_puddles for user
                String uid = FirebaseDB.getCurrentUser().getUid();
                DatabaseReference ref = FirebaseDB.getDataReference("Users").child(uid).child("my_puddles");
                ref.push().setValue(pud_id);

                // 2. Add Puddle count
                DatabaseReference pudRef = FirebaseDB.getDataReference("Puddles").child(pud_id).child("count");
                pudRef.setValue(alreadyJoined ? pud.getCount() : pud.getCount() + 1);

                // 3. Update the members child
                HashMap<String, String> userData = new HashMap<>();
                userData.put("username", FirebaseDB.getLocalUser().getUsername());
                userData.put("profile_url", FirebaseDB.getLocalUser().getProfile_icon());
                DatabaseReference memRef = FirebaseDB.getDataReference("Members").child(pud_id);
                memRef.push().setValue(userData);
            }
        }).start();
    }

    // To capture all the puddles
    public void fetchAllPuddles() {
        DatabaseReference pudRef = FirebaseDB.getDataReference("Puddles");
        pudRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allPuddleList = new ArrayList<>();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    Puddle puddle = snap.getValue(Puddle.class);
                    if (puddle != null) {
                        allPuddlesData.put(snap.getKey(), puddle);
                        allPuddleList.add(puddle);
                    }
                }
                fetchMyPuddles();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    // To store the current user puddles
    public void fetchMyPuddles() {
//        puddleListRecyclerView.setLayoutManager(new GridLayoutManager(PuddleListActivity.this, 2));
//        puddleListRecyclerView.setAdapter(new MyPuddlesAdapter(PuddleListActivity.this, myPuddlesData));
//        setSelectedButton(myPuddlesBtn);
//        setUnselectedButton(nearMeBtn);

        DatabaseReference myPuds = FirebaseDB.getDataReference("Users").child(FirebaseDB.getCurrentUser().getUid()).child("my_puddles");
        myPuds.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("snap data", snapshot.toString());
                for (DataSnapshot snap : snapshot.getChildren()) {
                    String key = snap.getValue(String.class);
                    if (key != null) {
                        myPuddlesData.put(key, allPuddlesData.get(key));
                    }
                    myPuddlesDataStored = myPuddlesData;
//                    puddleListRecyclerView.setLayoutManager(new GridLayoutManager(PuddleListActivity.this, 2));
//                    puddleListRecyclerView.setAdapter(new MyPuddlesAdapter(PuddleListActivity.this, myPuddlesData));
//                    setSelectedButton(myPuddlesBtn);
//                    setUnselectedButton(nearMeBtn);
                    updateRecyclerView();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    public void initializePuddles() {
        for (Category category : Category.values()) {
            categoryPuddlesData.put(category, new ArrayList<>());
        }
    }

    // To categorize puddles for near me screen
    public void categorizePuddles(List<Puddle> filteredPuddles) {
        synchronized (categoryObject) {
            initializePuddles();
            for (Puddle puddle : filteredPuddles) {
                categoryPuddlesData.get(Category.valueOf(puddle.getCategory().toUpperCase())).add(puddle);
            }
            for (Category category: Category.values()) {
                if(categoryPuddlesData.get(category).isEmpty()) {
                    categoryPuddlesData.remove(category);
                }
            }
        }
    }

    public void getLocation() {

        if (LocationPermissionActivity.checkMapServices(this)) {
            if (LocationPermissionActivity.locationPermissionGranted) {
                FusedLocationProviderClient fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, location -> {
                    new Thread(() -> {
                        if (location != null) {
                            double lat = location.getLatitude();
                            double lng = location.getLongitude();
                            List<Puddle> filteredPuddles = new ArrayList<>(allPuddleList);
                            filteredPuddles = FilterService.filteredPuddles(filteredPuddles, filteredDistance * 1609.34, lat, lng, filteredCategories, filteredGlobal, filteredMembership);
                            categorizePuddles(filteredPuddles);
                            filterHandler.post(() -> {
                                puddleListRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                                puddleListRecyclerView.setAdapter(new PuddleListAdapter(this, categoryPuddlesData));
                                setSelectedButton(nearMeBtn);
                                setUnselectedButton(myPuddlesBtn);
                            });

                        } else {
                            filterHandler.post(() -> {
                                Toast.makeText(PuddleListActivity.this, "Failed to get user location, Please provide location acccess to continue", Toast.LENGTH_LONG).show();
                            });
                        }
                    }).start();
                });

            } else {
                LocationPermissionActivity.requestPermission(this, LocationPermissionActivity.REQUEST_CODE_FINE_LOCATION);
            }
        }
    }
}