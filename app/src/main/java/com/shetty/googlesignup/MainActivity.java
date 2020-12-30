package com.shetty.googlesignup;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public class MainActivity extends AppCompatActivity {
    public static final int RC_SIGN_IN = 1;
    private LocationManager locationManager;
    private Button buttonUploadData;
    private static final int PERMISSION_REQUEST_CODE = 2;
    public static final String spreadsheetId = "1OOjLS6RoB9Z-7sWzz6Rpz0NfBuQAa7bPg2ClBQSA0nA";
    private static GoogleSignInAccount account;
    private TextView mOutputText;
    ProgressDialog mProgress;
    boolean status = false;
    private GoogleAccountCredential mCredential;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mOutputText = findViewById(R.id.tv_message);
        mProgress = new ProgressDialog(this);
        mProgress.setCanceledOnTouchOutside(false);
        mProgress.show();
        mProgress.setMessage(getString(R.string.checking_internet_connection));
        createButton();
        checkForDeviceStatus();

    }

    //To Check whether app has all the permission
    private void checkForDeviceStatus() {
        if (isDeviceOnline()) {
            mProgress.hide();
            mOutputText.setText(R.string.network_available);
            checkSignInStatus();
        } else {
            buttonUploadData.setText(R.string.try_again);
            mProgress.hide();
            status = false;
            mOutputText.setText(R.string.network_not_available);
        }
    }


    //To Create Upload Button
    private void createButton() {
        buttonUploadData = findViewById(R.id.btn_upload_data);
        buttonUploadData.setVisibility(View.VISIBLE);
        buttonUploadData.setOnClickListener(v -> {
            if (status) {
                getLocation();
            } else {
                checkForDeviceStatus();
            }
        });
    }


    //Checking Internet Connection
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    //Checking Google sign in status
    private void checkSignInStatus() {
        account = GoogleSignIn.getLastSignedInAccount(this);
        mProgress.hide();
        if(account==null){
            signInToGoogle();
        }
        else {
            mOutputText.setText(R.string.click_to_upload);
            requestPermission();
        }
    }

    //Signing in to google
    private void signInToGoogle() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(SheetsScopes.SPREADSHEETS))
                .build();
        GoogleSignInClient mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == -1) {
                handleSignInResult(data);
                mOutputText.setText(R.string.upload_data);
                requestPermission();
            } else {
                mOutputText.setText(R.string.no_login);
                buttonUploadData.setText(R.string.try_again);
                status = false;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0) {
                boolean fineLocationAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                if (fineLocationAccepted) {
                    locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                    if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        turnOnGPS();
                    }
                    buttonUploadData.setText(R.string.upload_data);
                    status = true;
                } else {
                    mOutputText.setText(R.string.no_location_permission);
                    status = false;
                    buttonUploadData.setText(R.string.try_again);
                }
            }
        }
    }


    private void handleSignInResult(Intent result) {
        GoogleSignIn.getSignedInAccountFromIntent(result)
                .addOnSuccessListener(googleAccount -> {
                    Task<GoogleSignInAccount> completedTask = GoogleSignIn.getSignedInAccountFromIntent(result);
                    try {
                        account = completedTask.getResult(ApiException.class);
                        mCredential =
                                GoogleAccountCredential.usingOAuth2(
                                        MainActivity.this, Collections.singleton(SheetsScopes.SPREADSHEETS));
                        mCredential.setSelectedAccount(googleAccount.getAccount());
                    } catch (ApiException e) {
                        mOutputText.setText(R.string.no_login);
                        e.printStackTrace();
                    }
                })
                .addOnFailureListener(exception -> {
                    mOutputText.setText(R.string.no_login);
                    Toast.makeText(this, R.string.no_login, Toast.LENGTH_SHORT).show();
                });
    }

    //To Get Current time
    private String getTime() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.US);
        return sdf.format(cal.getTime());
    }

    //To Request Location permission
    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
    }

    //To turn on GPS if it is off
    private void turnOnGPS() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Enable GPS").setCancelable(false).setPositiveButton("Yes", (dialog, which) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).setNegativeButton("No", (dialog, which) -> dialog.cancel());
        final AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    //To get Current Location
    private void getLocation() {
        if (ActivityCompat.checkSelfPermission(
                MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
        } else {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location locationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (locationGPS != null) {
                double lat = locationGPS.getLatitude();
                double longi = locationGPS.getLongitude();
                String userEmail = account.getEmail();
                String userCurrentTime = getTime();
                String coordinates = lat + "'" + longi + "\"";
                sendData(userEmail, userCurrentTime, coordinates);
            } else {
                Log.d("pavan", "getLocation: " + locationManager);
                Toast.makeText(this, "Unable to find location.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    //To send Data
    private void sendData(String userEmail, String userCurrentTime, String coordinates) {
        mCredential = GoogleAccountCredential.usingOAuth2(MainActivity.this, Collections.singleton(SheetsScopes.SPREADSHEETS));
        mCredential.setSelectedAccount(account.getAccount());
        networkOperation(mCredential, userEmail, userCurrentTime, coordinates);
    }

    //Sheet API Call
    private AppendValuesResponse putDataToSheetUsingApi(GoogleAccountCredential mCredential, String userEmail, String userTime, String userCoordination) {
        HttpTransport transport = new NetHttpTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        com.google.api.services.sheets.v4.Sheets mService = new com.google.api.services.sheets.v4.Sheets.Builder(
                transport, jsonFactory, mCredential)
                .setApplicationName("GoogleSignIn")
                .build();
        String range = "Class Data!A2:B";
        ValueRange body = new ValueRange()
                .setValues(Collections.singletonList(
                        Arrays.asList(userEmail, userTime, userCoordination)
                ));
        try {
            return mService.spreadsheets().values().append(spreadsheetId, range, body)
                    .setValueInputOption("RAW")
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    //Network operation using RXJava
    private void networkOperation(GoogleAccountCredential mCredential, String userEmail, String userTime, String userCoordination) {
        Observable.fromCallable(() -> {
            AppendValuesResponse response = putDataToSheetUsingApi(mCredential, userEmail, userTime, userCoordination);
            return response != null;

        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Boolean>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        mOutputText.setText("");
                        mProgress.show();
                        mProgress.setMessage("Uploading Data.");
                    }

                    @Override
                    public void onNext(@NonNull Boolean aBoolean) {
                        mProgress.hide();
                        if (aBoolean) {
                            mOutputText.setText(R.string.data_uploaded);
                        } else {
                            mOutputText.setText(R.string.data_not_uploaded);
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        mProgress.hide();
                        mOutputText.setText(R.string.data_not_uploaded);
                    }

                    @Override
                    public void onComplete() {
                    }
                });
    }

}

