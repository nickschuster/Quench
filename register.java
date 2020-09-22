package com.quench;

import android.content.Intent;
import androidx.annotation.NonNull;
import android.app.Activity;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.*;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class register extends Activity {

    // Array of user information.
    private String[] userInfo;

    // User information array indexes.
    private final int USER_NAME_INDEX = 0;
    private final int USER_EMAIL_INDEX = 1;
    private final int USER_PASS_INDEX = 2;
    private final int USER_PASSCONF_INDEX = 3;
    private final int USER_GOAL_INDEX = 4;
    private final int USER_UNIT_INDEX = 5;

    // Firebase configuration.
    private FirebaseFirestore database;
    private FirebaseAuth userAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Firebase configuration.
        FirebaseApp.initializeApp(register.this);
        userAuth = FirebaseAuth.getInstance();
        database = FirebaseFirestore.getInstance();

        // Setup "Not Sure?" as external link.
        ((TextView) findViewById(R.id.help)).setMovementMethod(LinkMovementMethod.getInstance());

        // Register button onClick listener.
        findViewById(R.id.register_button).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                userInfo = getUserInfo();
                boolean validData = validateData();
                if(validData) {
                    createUser();
                }
            }
        });
    }

    // Get all the user information from the text fields.
    //
    // Takes no parameters.
    //
    // Returns an array of all the user information.
    private String[] getUserInfo() {
        EditText name = findViewById(R.id.name);
        String userName = name.getText().toString();

        EditText email = findViewById(R.id.email);
        String userEmail = email.getText().toString();

        EditText password = findViewById(R.id.password);
        String userPassword = password.getText().toString();

        EditText passwordConf = findViewById(R.id.passwordconf);
        String userPasswordConf = passwordConf.getText().toString();

        EditText waterGoal = findViewById(R.id.watergoal);
        String userWaterGoal = waterGoal.getText().toString();

        RadioButton unit = findViewById(((RadioGroup) findViewById(R.id.unit)).getCheckedRadioButtonId());
        String userUnit;
        if(unit != null) {
            userUnit = unit.getText().toString();
        } else {
            userUnit = null;
        }

        return new String[] {userName, userEmail, userPassword, userPasswordConf, userWaterGoal, userUnit};
    }

    // Validate the user information.
    //
    // Takes no parameters.
    //
    // Returns true or false depending on validity of data.
    private boolean validateData() {
        Pattern validName = Pattern.compile("[a-zA-Z]*\\s?[a-zA-Z]*");
        Pattern validEmail= Pattern.compile("[a-zA-Z1-90]*@[a-zA-Z1-90]+\\.[a-zA-Z]+");
        Pattern validPass = Pattern.compile("[a-zA-Z1-90@#$%^&*()!-]*");
        Pattern validGoal = Pattern.compile("[0-9]*\\.?[0-9]*");

        if(userInfo.length != 6) {
            Toast.makeText(register.this, "Please fill out all fields.",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        for (String s : userInfo) {
            if (s == null || s.equals("")) {
                Toast.makeText(register.this, "Please fill out all fields.",
                        Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        if(!(Pattern.matches(validName.toString(), userInfo[USER_NAME_INDEX]))) {
            Toast.makeText(register.this, "Please enter a valid name.",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        if(!(Pattern.matches(validEmail.toString(), userInfo[USER_EMAIL_INDEX]))) {
            Toast.makeText(register.this, "Please enter a valid email.",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        if(!(Pattern.matches(validPass.toString(), userInfo[USER_PASS_INDEX]))) {
            Toast.makeText(register.this, "Please enter a valid password.",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        if(!(userInfo[USER_PASS_INDEX].equals(userInfo[USER_PASSCONF_INDEX]))) {
            Toast.makeText(register.this, "Please enter a matching passwords.",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        if(!(Pattern.matches(validGoal.toString(), userInfo[USER_GOAL_INDEX]))) {
            Toast.makeText(register.this, "Please enter a valid goal.",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    // Create the new user with the entered information.
    //
    // Takes no parameters and returns nothing.
    private void createUser() {
        userAuth.createUserWithEmailAndPassword(userInfo[USER_EMAIL_INDEX], userInfo[USER_PASS_INDEX])
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            updateDatabase();
                        } else {
                            Toast.makeText(register.this, task.getException().getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // Upload the user data to the database.
    //
    // Takes no parameters and returns nothing.
    private void updateDatabase() {
        Map<String, Object> user = new HashMap<>();
        user.put("name", userInfo[USER_NAME_INDEX]);
        user.put("goal", userInfo[USER_GOAL_INDEX]);
        user.put("goalUnit", userInfo[USER_UNIT_INDEX]);
        user.put("terms", false);
        user.put("points", 0);
        user.put("premium", false);

        database.collection("users")
                .document(userInfo[USER_EMAIL_INDEX])
                .set(user)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        launchMain();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(register.this, getString(R.string.connection_error),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Launch the main activity.
    //
    // Takes no parameters and returns nothing.
    private void launchMain() {
        Intent main = new Intent(this, quenchMain.class);
        startActivity(main);
    }
}
