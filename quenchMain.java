package com.quench;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import androidx.annotation.NonNull;
import android.app.Activity;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdCallback;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.stats.GCoreWakefulBroadcastReceiver;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.w3c.dom.Text;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class quenchMain extends Activity {

    // Local storage for when internet is not available.
    private SharedPreferences sharedPrefs;
    private Context mainContext;

    // Indexes for water goal split (stored as 2 L)
    private final int UNIT_INDEX = 1;
    private final int AMT_INDEX = 0;

    // Constants for drop animation.
    private final double DEFAULT_MARGIN_CONSTANT = 150;
    private final double MARGIN_MULTIPLIER = 2.5;

    // Variable for checking if the goal was updated in settings.
    private boolean goalUpdated;

    // Only allow one point claim per day.
    private boolean claimed;
    private boolean newDay;

    // Firebase configuration.
    private FirebaseFirestore database;
    private FirebaseAuth userAuth;
    private FirebaseUser user;

    // Notification configuration.
    private Timer notifTimer;
    private NotificationManagerCompat notificationManager;

    // Ad configuration.
    private RewardedAd rewardedAd;

    // Billing and purchasing configuration.
    private BillingClient billingClient;
    private boolean billingConnected;
    private List<SkuDetails> skuDetailsList;

    // Purchase listener for in-app purchases.
    private PurchasesUpdatedListener purchasesUpdatedListener = new PurchasesUpdatedListener() {
        @Override
        public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> list) {
            if(billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && list != null) {
                for (Purchase purchase : list) {
                    handlePurchase(purchase);
                }
            } else {
                Toast.makeText(quenchMain.this, "Purchase failed.", Toast.LENGTH_SHORT).show();
            }
        }
    };

    // Purchase acknowledgement listener.
    AcknowledgePurchaseResponseListener acknowledgePurchaseResponseListener = new AcknowledgePurchaseResponseListener() {
        @Override
        public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
            // Purchase successfully acknowledged
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quench_main);

        // Firebase configuration
        FirebaseApp.initializeApp(this);
        userAuth = FirebaseAuth.getInstance();
        user = userAuth.getCurrentUser();
        database = FirebaseFirestore.getInstance();

        // Load local storage.
        mainContext = quenchMain.this.getApplicationContext();
        sharedPrefs = mainContext.getSharedPreferences(user.getUid(), Context.MODE_PRIVATE);

        // Setup notifications.
        notificationManager = NotificationManagerCompat.from(this);

        // Check if the goal was updated in settings.
        goalUpdated = getIntent().getBooleanExtra(getString(R.string.goalUpdate), false);

        // Display user information.
        readFromDB();
        display();

        // Check if its a new day.
        String savedDate = sharedPrefs.getString(getString(R.string.date_id), getString(R.string.default_date));
        SimpleDateFormat dateFormater = new SimpleDateFormat("yyyy-MM-dd");
        try {
            String today = dateFormater.format(new Date());
            if(!savedDate.equals(today)) {
                newDay = true;
                claimed = false;
                SharedPreferences.Editor editDate = sharedPrefs.edit();
                editDate.putString(getString(R.string.date_id), today);
                editDate.apply();
            } else {
                newDay = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Cancel all previously setup notifications.
        notificationManager.cancelAll();

        // Request a rewarded ad.
//        MobileAds.initialize(this, new OnInitializationCompleteListener() {
//            @Override
//            public void onInitializationComplete(InitializationStatus initializationStatus) {
//            }
//        });

        rewardedAd = createAndLoadRewardedAd(getString(R.string.test_ad));

        // Billing client setup.
        billingClient = BillingClient.newBuilder(this)
                .enablePendingPurchases()
                .setListener(purchasesUpdatedListener)
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if(billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    billingConnected = true;
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                billingConnected = false;
                billingClient.startConnection(this);
            }
        });

        List<String> skuList = new ArrayList<>();
        skuList.add("quench_premium");
        SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
        params.setSkusList(skuList).setType(BillingClient.SkuType.INAPP);

        billingClient.querySkuDetailsAsync(params.build(), new SkuDetailsResponseListener() {
            @Override
            public void onSkuDetailsResponse(@NonNull BillingResult billingResult, @Nullable List<SkuDetails> list) {
                skuDetailsList = list;
            }
        });

        List<Purchase> purchases = billingClient.queryPurchases(BillingClient.SkuType.INAPP).getPurchasesList();
        if(purchases != null) {
            for(Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        }

        // Setup rest of app.
        createNotificationChannel();
        setUpSpinner();
        setUpTimer();
        privacyPolicy();

        // Button listener for "Log". Logs water.
        (findViewById(R.id.log)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Spinner unit = (Spinner) findViewById(R.id.unit);
                String unitToLog = unit.getSelectedItem().toString();

                EditText log = (EditText) findViewById(R.id.toLog);
                if(!(log.getText().toString().equals(""))) {
                    double amtToLog = Double.parseDouble(log.getText().toString());
                    log(amtToLog, unitToLog);
                    display();
                }
            }
        });

        // Button listener for settings. Wrench image. Launches settings activity.
        (findViewById(R.id.settings)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                notifTimer.cancel();

                Intent settings = new Intent(quenchMain.this, settings.class);
                startActivity(settings);
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensure there are no purchases waiting to be processed.
        List<Purchase> purchases = billingClient.queryPurchases(BillingClient.SkuType.INAPP).getPurchasesList();
        if(purchases != null) {
            for(Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        }
    }

    private RewardedAd createAndLoadRewardedAd(String adUnitId) {
        RewardedAd rewardedAd = new RewardedAd(this, adUnitId);
        RewardedAdLoadCallback adLoadCallback = new RewardedAdLoadCallback() {
            @Override
            public void onRewardedAdLoaded() {
                // Ad successfully loaded.
            }

            @Override
            public void onRewardedAdFailedToLoad(LoadAdError adError) {
                // Ad failed to load.
            }
        };
        rewardedAd.loadAd(new AdRequest.Builder().build(), adLoadCallback);
        return rewardedAd;
    }

    // Displays privacy policy prompt. User must accept or be logged out.
    //
    // Takes no parameters and returns nothing.
    private void privacyPolicy() {
        boolean acceptedTerms = sharedPrefs.getBoolean(getString(R.string.accepted_terms), false);

        if(!acceptedTerms) {
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            final View popupView = inflater.inflate(R.layout.terms_privacy_popup, null);

            int width = LinearLayout.LayoutParams.WRAP_CONTENT;
            int height = LinearLayout.LayoutParams.WRAP_CONTENT;
            boolean focusable = true;
            final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);
            popupWindow.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            popupWindow.setElevation(20);

            final ConstraintLayout token = findViewById(R.id.quenchMainLayout);
            final ViewTreeObserver tokenDrawn = token.getViewTreeObserver();
            tokenDrawn.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    ((TextView) popupView.findViewById(R.id.termsLink)).setMovementMethod(LinkMovementMethod.getInstance());
                    popupWindow.showAtLocation(findViewById(R.id.quenchMainLayout), Gravity.CENTER, 0, 0);
                    token.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });

            (popupView.findViewById(R.id.agree)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    database.collection("users").document(user.getEmail())
                            .update("terms", true);

                    SharedPreferences.Editor editTerms = sharedPrefs.edit();
                    editTerms.putBoolean(getString(R.string.accepted_terms), true);
                    editTerms.apply();

                    popupWindow.dismiss();
                }
            });

            (popupView.findViewById(R.id.decline)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    logout(findViewById(R.id.logout));
                }
            });
        }
    }

    // Read all user values from the database.
    //
    // Takes no parameters and returns nothing.
    private void readFromDB() {
        DocumentReference userInfo = database.collection("users").document(user.getEmail());
        userInfo.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        String name = document.getString("name");

                        SharedPreferences.Editor editName = sharedPrefs.edit();
                        editName.putString(getString(R.string.name_id), name);
                        editName.apply();

                        String userGoal = document.get("goal") + " " + document.get("goalUnit");

                        if(goalUpdated || newDay) {
                            SharedPreferences.Editor editGoal = sharedPrefs.edit();
                            editGoal.putString(getString(R.string.goal_id), userGoal);
                            editGoal.apply();
                        }

                        SharedPreferences.Editor editBaseline = sharedPrefs.edit();
                        editBaseline.putString(getString(R.string.goal_baseline), userGoal);
                        editBaseline.apply();

                        boolean terms = document.getBoolean("terms");

                        SharedPreferences.Editor editTerms = sharedPrefs.edit();
                        editTerms.putBoolean(getString(R.string.accepted_terms), terms);
                        editTerms.apply();

                        boolean premium = false;
                        int points = 0;

                        try {
                            premium = document.getBoolean("premium");
                            points = ((Number) document.get("points")).intValue();

                        } catch (Exception e) {
                            Toast.makeText(quenchMain.this, "Error:" + e, Toast.LENGTH_SHORT).show();

                            // Added parameters. May not exist with longstanding users.
                        }

                        //Toast.makeText(quenchMain.this, "Points:" + points, Toast.LENGTH_SHORT).show();

                        SharedPreferences.Editor editPremium = sharedPrefs.edit();
                        editPremium.putBoolean(getString(R.string.premium), premium);
                        editPremium.apply();

                        SharedPreferences.Editor editPoints = sharedPrefs.edit();
                        editPoints.putInt(getString(R.string.points), points);
                        editPoints.apply();

                        display();
                    } else {
                        Toast.makeText(quenchMain.this, getString(R.string.connection_error),
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(quenchMain.this, task.getException().getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            }

        });
    }

    // Update and display the user information.
    //
    // Takes no parameters and returns nothing.
    private void display() {
        String[] userGoal;

        userGoal = sharedPrefs.getString(getString(R.string.goal_id), getString(R.string.default_goal)).split(" ");

        TextView goal = findViewById(R.id.goalMsg);
        goal.setText(getString(R.string.waterLeft,
                (Math.round(Double.parseDouble(userGoal[AMT_INDEX]) * 100.0) / 100.0)
                + " " + userGoal[UNIT_INDEX]));

        int points = sharedPrefs.getInt(getString(R.string.points), 0);
        TextView pointAmt = findViewById(R.id.pointAmount);
        pointAmt.setText(String.valueOf(points));

        animateDrop();
    }

    // Setup the water unit spinner.
    //
    // Takes no parameters and returns nothing.
    private void setUpSpinner() {
        Spinner spinner = (Spinner) findViewById(R.id.unit);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.units, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    // Animate the drop to fill based on amount of water logged.
    //
    // Takes no parameters and returns nothing.
    private void animateDrop() {
        ImageView dropOutline = findViewById(R.id.backgroundDrop);
        ImageView dropFill = findViewById(R.id.backgroundColor);

        double goal = Double.parseDouble(sharedPrefs
                .getString(getString(R.string.goal_id), getString(R.string.default_goal))
                .split(" ")[AMT_INDEX]);

        double baseline = Double.parseDouble(sharedPrefs
                .getString(getString(R.string.goal_baseline), getString(R.string.default_goal))
                .split(" ")[AMT_INDEX]);

        double percentLogged = ((baseline - goal) / baseline);

        if(dropOutline.getHeight() * percentLogged <= 0) {
            dropFill.getLayoutParams().height = 10;
        } else {
            dropFill.getLayoutParams().height = (int)(dropOutline.getHeight() * percentLogged);
        }
        dropFill.requestLayout();
    }

    // Log the entered water. Converts entered unit automatically.
    //
    // Takes no parameters and returns nothing.
    private void log(double amtToLog, String unitToLog) {
        String goal = sharedPrefs.getString(getString(R.string.goal_id), getString(R.string.default_goal));
        String goalUnit = goal.split(" ")[UNIT_INDEX];
        double goalAmt = Double.parseDouble(goal.split(" ")[AMT_INDEX]);

        if(goalUnit.equals("Liters")) {
            if(unitToLog.equals("L") || unitToLog.equals("Liters")){
                goalAmt -= amtToLog;
            } else if(unitToLog.equals("Ml")){
                goalAmt -= (amtToLog/1000);
            } else if(unitToLog.equals("Oz") || unitToLog.equals("Ounces")){
                goalAmt -= (amtToLog/33.814);
            } else if(unitToLog.equals("Gal")){
                goalAmt -= (amtToLog*3.78541);
            }
        } else if(goalUnit.equals("Ounces")) {
            if(unitToLog.equals("L") || unitToLog.equals("Liters")){
                goalAmt -= (amtToLog*33.814);
            } else if(unitToLog.equals("Ml")){
                goalAmt -= ((amtToLog/1000)*33.814);
            } else if(unitToLog.equals("Oz") || unitToLog.equals("Ounces")){
                goalAmt -= amtToLog;
            } else if(unitToLog.equals("Gal")){
                goalAmt -= (amtToLog*128);
            }
        }

        if(goalAmt <= 0) {
            goalAmt = 0;
            notificationManager.cancelAll();

            if(!claimed) {
                goalReached();
            }

            claimed = true;
        }

        SharedPreferences.Editor editGoal = sharedPrefs.edit();
        editGoal.putString(getString(R.string.goal_id), goalAmt + " " + goalUnit);
        editGoal.apply();

    }

    // Creates goal reached popup with google ads. Adds streak point to streaks.
    //
    // Takes no parameters and returns nothing.
    private void goalReached() {
        final boolean premium = sharedPrefs.getBoolean(getString(R.string.premium), false);

        if(premium) {
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            final View premiumPopup = inflater.inflate(R.layout.premium_claim_popup, null);

            int width = LinearLayout.LayoutParams.WRAP_CONTENT;
            int height = LinearLayout.LayoutParams.WRAP_CONTENT;
            boolean focusable = true;
            final PopupWindow popupWindow = new PopupWindow(premiumPopup, width, height, focusable);
            popupWindow.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            popupWindow.setElevation(20);

            final ConstraintLayout token = findViewById(R.id.quenchMainLayout);
            final ViewTreeObserver tokenDrawn = token.getViewTreeObserver();
            tokenDrawn.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    popupWindow.showAtLocation(findViewById(R.id.quenchMainLayout), Gravity.CENTER, 0, 0);
                    token.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });

            (premiumPopup.findViewById(R.id.claimPremium)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int points = sharedPrefs.getInt(getString(R.string.points), 0) + 1;

                    database.collection("users").document(user.getEmail())
                            .update("points", points);

                    SharedPreferences.Editor editPoints = sharedPrefs.edit();
                    editPoints.putInt(getString(R.string.points), points);
                    editPoints.apply();

                    popupWindow.dismiss();

                    display();
                }
            });
        } else {
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            final View freePopup = inflater.inflate(R.layout.free_claim_popup, null);

            int width = LinearLayout.LayoutParams.WRAP_CONTENT;
            int height = LinearLayout.LayoutParams.WRAP_CONTENT;
            final PopupWindow popupWindow = new PopupWindow(freePopup, width, height, true);
            popupWindow.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            popupWindow.setElevation(20);

            final ConstraintLayout token = findViewById(R.id.quenchMainLayout);
            final ViewTreeObserver tokenDrawn = token.getViewTreeObserver();
            tokenDrawn.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    popupWindow.showAtLocation(findViewById(R.id.quenchMainLayout), Gravity.CENTER, 0, 0);
                    token.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });

            (freePopup.findViewById(R.id.claimFree)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(rewardedAd.isLoaded()) {
                        RewardedAdCallback adCallback = new RewardedAdCallback() {
                            @Override
                            public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                                int points = sharedPrefs.getInt(getString(R.string.points), 0) + 1;

                                database.collection("users").document(user.getEmail())
                                        .update("points", points);

                                SharedPreferences.Editor editPoints = sharedPrefs.edit();
                                editPoints.putInt(getString(R.string.points), points);
                                editPoints.apply();

                                popupWindow.dismiss();

                                display();
                            }

                            @Override
                            public void onRewardedAdClosed() {
                                rewardedAd = createAndLoadRewardedAd(getString(R.string.test_ad));
                            }

                            @Override
                            public void onRewardedAdFailedToShow(AdError adError) {
                                Toast.makeText(quenchMain.this, "Could not load ad.", Toast.LENGTH_SHORT).show();
                            }

                        };
                        rewardedAd.show(quenchMain.this, adCallback);
                    } else {
                        Toast.makeText(quenchMain.this, "Could not load ad.", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            (freePopup.findViewById(R.id.premium)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(payment()) {
                        database.collection("users").document(user.getEmail())
                                .update("premium", true);

                        SharedPreferences.Editor editPremium = sharedPrefs.edit();
                        editPremium.putBoolean(getString(R.string.premium), true);
                        editPremium.apply();

                        goalReached();
                    }
                }
            });
        }
    }

    // Handles the payment logic of purchasing premium.
    //
    // Takes no parameters.
    //
    // Returns true or false depending on valid purchase.
    private boolean payment() {
        if(billingConnected && skuDetailsList != null && skuDetailsList.size() > 0) {
            BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                    .setSkuDetails(skuDetailsList.get(0))
                    .build();
            int responseCode = billingClient.launchBillingFlow(this, billingFlowParams).getResponseCode();
            return responseCode == BillingClient.BillingResponseCode.OK;
        } else {
            Toast.makeText(this, "Could not connect to Google Play.", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void handlePurchase(Purchase purchase) {
        if(purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            if(!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        .build();
                billingClient.acknowledgePurchase(acknowledgePurchaseParams, acknowledgePurchaseResponseListener);
            }
        }
    }

    // Setup the timed notifications based on user settings.
    //
    // Takes no parameters and returns nothing.
    public void setUpTimer() {
        class NotifTask extends TimerTask {
            @Override
            public void run() {
                String interval = sharedPrefs.getString(getString(R.string.interval_id), getString(R.string.default_interval));

                String goalUnit = sharedPrefs.getString(getString(R.string.goal_id), getString(R.string.default_goal)).split(" ")[1];
                double goalAmt = Double.parseDouble(sharedPrefs.getString(getString(R.string.goal_id), getString(R.string.default_goal)).split(" ")[0]);

                long[] times = calculateNotifTimes();

                int i = 0;
                while(times[i] != 0) {
                    i++;
                }
                int endIndex = i - 1;

                double amtToDrink = ((goalAmt/((double)(times[endIndex]-times[0])/(double)(Long.parseLong(interval) * 60 * 1000))));

                notifyUser(amtToDrink, goalUnit);
            }
        }

        long[] times = calculateNotifTimes();

        String currentDateTime = LocalDateTime.now().toString().split("T")[1];
        String[] currentHourMin = currentDateTime.split(":");
        int currentHour = Integer.parseInt(currentHourMin[0]);
        int currentMin = Integer.parseInt(currentHourMin[1]);
        long currentTime = (currentHour * 60 * 60 * 1000) + (currentMin * 60 * 1000);

        long nextTime = times[0];
        int j = 0;
        while(currentTime >= nextTime) {
            try {
                nextTime = times[j];
            } catch (Exception e) {
                Log.d("Timer", e.getLocalizedMessage());
            }
            j++;
        }

        long delay = nextTime - currentTime;
        long intervalTime = Long.parseLong(sharedPrefs.getString(getString(R.string.interval_id), getString(R.string.default_interval))) * 60 * 1000;

        NotifTask notifTask = new NotifTask();
        notifTimer = new Timer("notifTimer", true);
        notifTimer.scheduleAtFixedRate(notifTask, delay, intervalTime);
    }

    // Display the notification to the user.
    //
    // Takes a double amount to drink and a string unit (L or G expected).
    //
    // Returns nothing.
    public void notifyUser(double amtToDrink, String goalUnit) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getString(R.string.notificationChannelId))
                .setSmallIcon(R.drawable.quench_notif)
                .setContentTitle("Get Quenched!")
                .setContentText("Drink " + (Math.round(amtToDrink * 100.0) / 100.0) + " " + goalUnit + " now.")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Drink " + (Math.round(amtToDrink * 100.0) / 100.0) + " " + goalUnit + " now."))
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        notificationManager.notify(1, builder.build());


    }

    // Creates the Quench notification channel.
    //
    // Takes no parameters and returns nothing.
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.notificationChannelName);
            String description = "Notifications for goal reminders";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(getString(R.string.notificationChannelId), name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // Calculate when to send the user a notification based on their preferences.
    //
    // Takes no parameters and returns nothing.
    private long[] calculateNotifTimes() {
        String start = sharedPrefs.getString(getString(R.string.start_id), getString(R.string.default_start));
        String startUnit = sharedPrefs.getString(getString(R.string.start_unit_id), getString(R.string.default_start_unit));
        String[] startHourMin = start.split(":");
        int startHour = Integer.parseInt(startHourMin[0]);
        int startMinute = Integer.parseInt(startHourMin[1]);
        if(startUnit.equals("PM")) {
            startHour += 12;
            if (startHour == 24) {
                startHour = 0;
            }
        }
        long startTime = (startHour * 60 * 60 * 1000) + (startMinute * 60 * 1000);

        String end = sharedPrefs.getString(getString(R.string.end_id), getString(R.string.default_end));
        String endUnit = sharedPrefs.getString(getString(R.string.end_unit_id), getString(R.string.default_end_unit));
        String[] endHourMin = end.split(":");
        int endHour = Integer.parseInt(endHourMin[0]);
        int endMinute = Integer.parseInt(endHourMin[1]);
        if(endUnit.equals("PM")) {
            endHour += 12;
            if (endHour == 24) {
                endHour = 0;
            }
        }
        long endTime = (endHour * 60 * 60 * 1000) + (endMinute * 60 * 1000);

        String interval = sharedPrefs.getString(getString(R.string.interval_id), getString(R.string.default_interval));
        long intervalTime = Long.parseLong(interval) * 60 * 1000;

        long milisInDay = 86400000;
        long[] times = new long[(int)(milisInDay/intervalTime) + 2];
        long step = startTime;
        int i = 1;
        times[0] = startTime;
        while (step <= endTime) {
            step += intervalTime;
            times[i] = step;
            i++;
        }
        times[i] = endTime;

        return times;
    }

    // Log the user out. Takes user back to chooseLogin activity.
    //
    // Takes no parameters and returns nothing.
    public void logout(View view) {
        userAuth.signOut();
        Intent chooseLogin = new Intent(quenchMain.this, chooseLogin.class);
        chooseLogin.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(chooseLogin);
        finish();
    }
}
