package com.quench;

import android.content.Intent;
import androidx.annotation.NonNull;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

public class emailLogin extends Activity {

    // User information array indexes. Stores as array
    // for return purposes.
    private final int USER_EMAIL = 0;
    private final int USER_PASSWORD = 1;

    // Firebase configuration.
    private FirebaseAuth userAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_login);

        // Firebase configuration.
        FirebaseApp.initializeApp(this);
        userAuth = FirebaseAuth.getInstance();

        // Login button listener.
        final Button emailLoginButton = findViewById(R.id.login_button);
        emailLoginButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String[] user = getCredentials();
                signIn(user);
            }
        });
    }

    // Gets the email and password from the respective fields.
    //
    // Takes no parameters.
    //
    // Returns a string array with the email and password.
    private String[] getCredentials() {
        EditText emailField = findViewById(R.id.email);
        String userEmail = emailField.getText().toString();

        EditText passwordField = findViewById(R.id.password);
        String userPassword = passwordField.getText().toString();

        return new String[] {userEmail, userPassword};
    }

    // Tries to sign in to Firebase with the provided email and password.
    //
    // Takes the string array of user info.
    //
    // Returns nothing.
    private void signIn(String[] user) {
        String userEmail = user[USER_EMAIL];
        String userPassword = user[USER_PASSWORD];
        userAuth.signInWithEmailAndPassword(userEmail, userPassword)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            signInSuccess();
                        } else {
                            Toast.makeText(emailLogin.this, "Login failed.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // Launch the main activity.
    //
    // Takes no parameters and returns nothing.
    private void signInSuccess() {
        Intent main = new Intent(emailLogin.this, quenchMain.class);
        startActivity(main);
    }
}
