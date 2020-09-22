package com.quench;

import android.content.Intent;
import androidx.annotation.NonNull;
import android.app.Activity;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class googleLogin extends Activity {

    // App required user information.
    private String[] userInfo;

    // Indexes for information in the userInfo array.
    private final int USER_NAME_INDEX = 0;
    private final int USER_EMAIL_INDEX = 1;
    private final int USER_GOAL_INDEX = 2;
    private final int USER_UNIT_INDEX = 3;

    // Google sign-in activity id.
    private final int RC_SIGN_IN = 1;

    // Firebase configuration.
    private FirebaseFirestore database;
    private FirebaseAuth userAuth;
    private FirebaseUser user;

    // Google sign-in configuration.
    private GoogleSignInOptions userAuthGoogle;
    private GoogleSignInClient userClientGoogle;
    private GoogleSignInAccount userAccountGoogle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_google_login);

        // Firebase configuration.
        FirebaseApp.initializeApp(googleLogin.this);
        userAuth = FirebaseAuth.getInstance();
        database = FirebaseFirestore.getInstance();

        // Google sign-in configuration.
        userAuthGoogle = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.client_id))
                .requestEmail()
                .build();
        userClientGoogle = GoogleSignIn.getClient(googleLogin.this, userAuthGoogle);

        // Launch sign-in.
        googleSignIn();

        // Setup the "Not Sure?" external link.
        ((TextView) findViewById(R.id.help2)).setMovementMethod(LinkMovementMethod.getInstance());
    }

    // Starts the Google provided sign-in activity.
    //
    // No parameters and returns nothing.
    private void googleSignIn() {
        Intent signInIntent = userClientGoogle.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    // Listener for when the Google sign-in activity finishes.
    //
    // View google documentation for parameter descriptions.
    //
    // Returns nothing.
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(resultCode != RESULT_CANCELED && data != null) {
            if (requestCode == RC_SIGN_IN) {
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                handleSignInResult(task);
            }
        } else {
            Toast.makeText(this, "Login failed.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // Called when when the Google sign-in activity finishes.
    //
    // View Google documentation for parameter description.
    //
    // Returns nothing.
    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            System.out.println("1");
            userAccountGoogle = completedTask.getResult(ApiException.class);
            System.out.println("2");
            firebaseAuthWithGoogle(userAccountGoogle);
        } catch (ApiException e) {
            Toast.makeText(googleLogin.this, e.getStatusCode(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Setup firebase with the Google sign-in auth instance.
    //
    // Takes a valid Google account instance.
    //
    // Returns nothing.
    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        System.out.println("3");
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        userAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            user = userAuth.getCurrentUser();
                            checkForNewUser();
                        } else {
                            Toast.makeText(googleLogin.this, getString(R.string.connection_error),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // Determine if the user logging in is new or not.
    //
    // Takes no parameters and returns nothing.
    private void checkForNewUser() {
        DocumentReference userInfo = database.collection("users").document(user.getEmail());

        userInfo.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        launchMain();
                    } else {
                        newUser();
                    }
                } else {
                    Toast.makeText(googleLogin.this, getString(R.string.connection_error),
                            Toast.LENGTH_SHORT).show();
                }

            }
        });
    }

    // Sets up a new user by displaying the newUser layout.
    //
    // Takes no parameters and returns nothing.
    private void newUser() {
        System.out.println(user.getDisplayName());

        LinearLayout returningUser = findViewById(R.id.returningUser);
        returningUser.setVisibility(View.INVISIBLE);

        LinearLayout newUser = findViewById(R.id.newUser);
        newUser.setVisibility(View.VISIBLE);

        ((TextView) findViewById(R.id.welcome))
                .setText(getString(R.string.welcome, user.getDisplayName().split(" ")[0]));

        findViewById(R.id.submit).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                userInfo = getUserInfo();
                boolean validData = validateData();
                if(validData) {
                    updateDatabase();
                }
            }
        });
    }

    // Get the needed user information from the account.
    //
    // Takes no parameters.
    //
    // Returns an array of strings of user information.
    private String[] getUserInfo() {
        String userName = user.getDisplayName().split(" ")[0];

        String userEmail = user.getEmail();

        EditText waterGoal = (EditText) findViewById(R.id.watergoal);
        String userWaterGoal = waterGoal.getText().toString();

        RadioButton unit = findViewById(((RadioGroup) findViewById(R.id.unit)).getCheckedRadioButtonId());
        String userUnit;
        if(unit != null) {
            userUnit = unit.getText().toString();
        } else {
            userUnit = null;
        }

        return new String[] {userName, userEmail, userWaterGoal, userUnit};
    }

    // Validates a new users data.
    //
    // Takes no parameters.
    //
    // Returns true or false depending on valid data.
    private boolean validateData() {
        Pattern validGoal = Pattern.compile("[0-9]*");

        if(userInfo.length != 4) {
            Toast.makeText(googleLogin.this, "Please fill out all fields.",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        for(int i = 0; i < userInfo.length; i++) {
            if (userInfo[i] == null || userInfo[i].equals("")) {
                Toast.makeText(googleLogin.this, "Please fill out all fields.",
                        Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        if(!(Pattern.matches(validGoal.toString(), userInfo[USER_GOAL_INDEX]))) {
            Toast.makeText(googleLogin.this, "Please enter a valid goal.",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    // Updates the data base with a new users information.
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
                        Toast.makeText(googleLogin.this, getString(R.string.connection_error),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Launches the main activity.
    //
    // Takes no parameters and returns nothing.
    private void launchMain() {
        Intent main = new Intent(googleLogin.this, quenchMain.class);
        startActivity(main);
    }
}
