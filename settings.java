package com.quench;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import android.app.Activity;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public class settings extends Activity {

    // Local storage for user data.
    private SharedPreferences sharedPrefs;
    private Context mainContext;

    // Firebase configuration.
    private FirebaseFirestore database;
    private FirebaseAuth userAuth;
    private FirebaseUser user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Firebase configuration.
        FirebaseApp.initializeApp(this);
        userAuth = FirebaseAuth.getInstance();
        user = userAuth.getCurrentUser();
        database = FirebaseFirestore.getInstance();

        // Get local storage.
        mainContext = settings.this.getApplicationContext();
        sharedPrefs = mainContext.getSharedPreferences(user.getUid(), Context.MODE_PRIVATE);

        // Setup up button in toolbar.


        // Setup rest of activity.
        setUpSpinners();
        populateFields();

        // Button listener for "Update" button for updating notification times.
        (findViewById(R.id.updateNotif)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateNotifSettings();
            }
        });

        // Button listener for "Update" button for updating the water goal.
        (findViewById(R.id.updateGoal)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateGoal();
            }
        });

        // Button listener for "Reject" button for rejecting the terms of service.
        (findViewById(R.id.reject)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rejectTerms();
            }
        });

        // Setup for the terms and conditions external link.
        ((TextView) findViewById(R.id.terms)).setMovementMethod(LinkMovementMethod.getInstance());
    }

    // Reject the terms and conditions retroactively. Kicks user to login page.
    //
    // Takes no parameters and returns nothing.
    private void rejectTerms() {
        database.collection("users").document(user.getEmail())
                .update("terms", false);

        SharedPreferences.Editor editTerms = sharedPrefs.edit();
        editTerms.putBoolean(getString(R.string.accepted_terms), false);
        editTerms.apply();

        userAuth.signOut();

        Intent chooseLogin = new Intent(settings.this, chooseLogin.class);
        chooseLogin.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(chooseLogin);
        finish();
    }

    // Update the goal water amount.
    //
    // Takes no parameters and returns nothing.
    private void updateGoal() {
        String goal = ((EditText) findViewById(R.id.goalAmt)).getText().toString();

        String goalUnit = ((RadioButton) findViewById(((RadioGroup) findViewById(R.id.goalUnit)).getCheckedRadioButtonId())).getText().toString();

        SharedPreferences.Editor editPrefs = sharedPrefs.edit();
        editPrefs.putString(getString(R.string.goal_id), goal + " " + goalUnit);
        editPrefs.apply();

        database.collection("users").document(user.getEmail())
                .update("goal", goal, "goalUnit", goalUnit);

        Intent main = new Intent(settings.this, quenchMain.class);
        main.putExtra(getString(R.string.goalUpdate), true);
        startActivity(main);
        finish();
    }

    // Update the notification times.
    //
    // Takes no parameters and returns nothing.
    private void updateNotifSettings() {
        String start = ((EditText) findViewById(R.id.start)).getText().toString();

        String end = ((EditText) findViewById(R.id.end)).getText().toString();

        String startUnit = ((Spinner) findViewById(R.id.startUnit)).getSelectedItem().toString();

        String endUnit = ((Spinner) findViewById(R.id.endUnit)).getSelectedItem().toString();

        String interval = ((Spinner) findViewById(R.id.interval)).getSelectedItem().toString();

        if(validateNotifSettings(start, startUnit, end, endUnit)) {
            SharedPreferences.Editor editPrefs = sharedPrefs.edit();
            editPrefs.putString(getString(R.string.start_id), start);
            editPrefs.putString(getString(R.string.end_id), end);
            editPrefs.putString(getString(R.string.start_unit_id), startUnit);
            editPrefs.putString(getString(R.string.end_unit_id), endUnit);
            editPrefs.putString(getString(R.string.interval_id), interval);
            editPrefs.apply();

            Intent main = new Intent(settings.this, quenchMain.class);
            startActivity(main);
        } else {
            Toast.makeText(settings.this, "Invalid interval. Notification settings not updated",
                    Toast.LENGTH_SHORT).show();
        }

    }

    // Populate the time spinners.
    //
    // Takes no parameters and returns nothing.
    public void setUpSpinners() {
        Spinner spinner1 = findViewById(R.id.startUnit);
        ArrayAdapter<CharSequence> adapter1 = ArrayAdapter.createFromResource(this,
                R.array.timeUnit, android.R.layout.simple_spinner_item);
        adapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner1.setAdapter(adapter1);

        Spinner spinner2 = findViewById(R.id.endUnit);
        ArrayAdapter<CharSequence> adapter2 = ArrayAdapter.createFromResource(this,
                R.array.timeUnit, android.R.layout.simple_spinner_item);
        adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner2.setAdapter(adapter2);

        Spinner spinner3 = findViewById(R.id.interval);
        ArrayAdapter<CharSequence> adapter3 = ArrayAdapter.createFromResource(this,
                R.array.timeInterval, android.R.layout.simple_spinner_item);
        adapter3.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner3.setAdapter(adapter3);
    }

    // Helper method for getting the index of a given spinner and a value.
    //
    // Takes a spinner and a string value.
    //
    // Returns the index of the value in the spinner or 0.
    private int getIndexOfSpinner(Spinner spinner, String myString){
        for (int i = 0; i < spinner.getCount(); i++){
            if (spinner.getItemAtPosition(i).toString().equalsIgnoreCase(myString)){
                return i;
            }
        }

        return 0;
    }

    // Populate the various text fields with the current user information.
    //
    // Takes no parameters and returns nothing.
    public void populateFields(){
        String start = sharedPrefs.getString(getString(R.string.start_id), getString(R.string.default_start));

        String end = sharedPrefs.getString(getString(R.string.end_id), getString(R.string.default_end));

        String startUnit = sharedPrefs.getString(getString(R.string.start_unit_id), getString(R.string.default_start_unit));

        String endUnit = sharedPrefs.getString(getString(R.string.end_unit_id), getString(R.string.default_end_unit));

        String interval = sharedPrefs.getString(getString(R.string.interval_id), getString(R.string.default_interval));

        String goalAmt = sharedPrefs.getString(getString(R.string.goal_baseline), getString(R.string.default_goal)).split(" ")[0];

        String goalUnit = sharedPrefs.getString(getString(R.string.goal_id), getString(R.string.default_goal)).split(" ")[1];

        EditText startTime = findViewById(R.id.start);
        startTime.setText(start);

        EditText endTime = findViewById(R.id.end);
        endTime.setText(end);

        EditText goal = findViewById(R.id.goalAmt);
        goal.setText(String.format(Locale.ROOT,"%.1f", (Math.round(Double.parseDouble(goalAmt) * 100.0) / 100.0)));

        Spinner startTimeUnit =  findViewById(R.id.startUnit);
        startTimeUnit.setSelection(getIndexOfSpinner(startTimeUnit, startUnit));

        Spinner endTimeUnit = findViewById(R.id.endUnit);
        endTimeUnit.setSelection(getIndexOfSpinner(endTimeUnit, endUnit));

        Spinner timeInterval = findViewById(R.id.interval);
        timeInterval.setSelection(getIndexOfSpinner(timeInterval, interval));

        RadioButton liters = findViewById(R.id.liters);
        RadioButton ounces = findViewById(R.id.ounces);

        RadioGroup goalUnits = findViewById(R.id.goalUnit);

        if(goalUnit.contentEquals(liters.getText())) {
            goalUnits.check(liters.getId());
        } else if(goalUnit.contentEquals(ounces.getText())) {
            goalUnits.check(ounces.getId());
        }
    }

    // Validates the entered notification times to ensure they can be used.
    //
    // Takes a start time, a start unit, an end time, and an end unit all in string format (AM or PM for unit).
    //
    // Returns true or false depending on validity.
    public boolean validateNotifSettings(String start, String startUnit, String end, String endUnit) {

        if(start.matches("\\d?\\d:[0-5]\\d") && end.matches("\\d?\\d:[0-5]\\d")) {
            String[] startHourMin = start.split(":");
            int startHour = Integer.parseInt(startHourMin[0]);
            int startMinute = Integer.parseInt(startHourMin[1]);
            if(startUnit.equals("PM")) {
                startHour += 12;
                if (startHour >= 24) {
                    startHour = 0;
                }
            }
            long startTime = (startHour * 60 * 60 * 1000) + (startMinute * 60 * 1000);

            String[] endHourMin = end.split(":");
            int endHour = Integer.parseInt(endHourMin[0]);
            int endMinute = Integer.parseInt(endHourMin[1]);
            if(endUnit.equals("PM")) {
                endHour += 12;
                if (endHour >= 24) {
                    endHour = 0;
                }
            }
            long endTime = (endHour * 60 * 60 * 1000) + (endMinute * 60 * 1000);

            return ((endTime - startTime) > 0);
        }
        return false;
    }

    // Button listener for back button. Takes user back to main activity.
    //
    // Takes the clicked view.
    //
    // Returns nothing.
    public void backToMain(View view) {
        Intent main = new Intent(settings.this, quenchMain.class);
        startActivity(main);
        finish();
    }
}
