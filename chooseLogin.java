package com.quench;

import android.content.Intent;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.jetbrains.annotations.NotNull;

public class chooseLogin extends Activity {

    // Called on activity creation.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_login);

        // Firebase configuration.
        FirebaseApp.initializeApp(this);
        FirebaseAuth userAuth = FirebaseAuth.getInstance();

        // If a user is already logged in send them to the main activity.
        if(checkLogin(userAuth)) {
            Intent main = new Intent(chooseLogin.this, quenchMain.class);
            startActivity(main);
        }

        // Manual onClick listener for the custom Google button. Only works this way...
        findViewById(R.id.google_login_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                googleLogin(findViewById(R.id.google_login_button));
            }
        });
    }

    // Called when the "Login with email" button is clicked.
    //
    // Takes in the clicked view.
    //
    // Sends the user to the emailLogin activity.
    public void emailLogin(View clickedView) {
        Intent emailLogin = new Intent(this, emailLogin.class);
        startActivity(emailLogin);
    }

    // Called when the "Login with Google" button is clicked.
    //
    // Takes in the clicked view.
    //
    // Sends the user to the googleLogin activity.
    public void googleLogin(View clickedView) {
        Intent googleLogin = new Intent(this, googleLogin.class);
        startActivity(googleLogin);
    }

    // Called when the "Register" button is clicked.
    //
    // Takes in the clicked view.
    //
    // Sends the user to the register activity.
    public void register(View clickedView) {
        Intent register = new Intent(this, register.class);
        startActivity(register);
    }

    // Check if a Firebase authentication instance has a
    // user attached to it.
    //
    // Takes a FirebaseAuth instance and returns true or false
    // depending.
    private boolean checkLogin(@NotNull FirebaseAuth userAuth) {
        FirebaseUser user = userAuth.getCurrentUser();
        return user != null;
    }
}
