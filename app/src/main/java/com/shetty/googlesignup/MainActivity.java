package com.shetty.googlesignup;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.sheets.v4.SheetsScopes;

import android.Manifest;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public class MainActivity extends AppCompatActivity {
    private  final String TAG ="pavan";
    public static final int RC_SIGN_IN=1;
    private LocationManager locationManager;
    private Button buttonUploadData;
    private static final int PERMISSION_REQUEST_CODE = 2;
    private static   GoogleSignInAccount account;
    private static GoogleAccountCredential mCredential;
    private TextView mOutputText;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    ProgressDialog mProgress;
    public static final String spreadsheetId="1OOjLS6RoB9Z-7sWzz6Rpz0NfBuQAa7bPg2ClBQSA0nA";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mOutputText = findViewById(R.id.tv_message);
        mProgress = new ProgressDialog(this);
        mProgress.show();
        mProgress.setMessage("Connecting To Google.");
        getResultsFromApi();
        updateUI();
    }
    private void getResultsFromApi() {
         if (! isDeviceOnline()) {
            mOutputText.setText("No network connection available.");
            mProgress.hide();
        }
        else {
            checkSignInStatus();
        }
    }


    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }

    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    private void checkSignInStatus() {
        account = GoogleSignIn.getLastSignedInAccount(this);
        mProgress.hide();
        if(account==null){
            signInToGoogle();
        }
        else {
            mOutputText.setText("Click on the button to upload the data.");
            requestForLocationPermission();
        }
    }

    private void signInToGoogle() {
        Log.d(TAG, "signInToGoogle: ");
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
        Log.d(TAG, "onActivityResult: ");
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            handleSignInResult(data);
            mOutputText.setText("Click on the button to upload the data.");
            requestForLocationPermission();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0) {
                boolean fineLocationAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                if (fineLocationAccepted) {
                    locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                    if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        onGPS();
                    }
                    buttonUploadData.setText("Upload Data");
                }
            }
        }
    }


    private void handleSignInResult(Intent result) {
        Log.d(TAG, "handleSignInResult: ");
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
                        Toast.makeText(this, "Couldn't Sign-in!", Toast.LENGTH_SHORT).show();
                        mOutputText.setText("Couldn't Sign-in!");
                        e.printStackTrace();
                    }
                })
                .addOnFailureListener(exception -> {
                    mOutputText.setText("Couldn't Sign-in!");

                    Toast.makeText(this, "Couldn't Sign-in!", Toast.LENGTH_SHORT).show();
                });

    }

    private void requestForLocationPermission() {
        if(checkPermission()){
            locationManager=(LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                onGPS();
            }
        }
        else {
            requestPermission();
        }
    }


    private void updateUI() {
            buttonUploadData=findViewById(R.id.btn_upload_data);
            buttonUploadData.setVisibility(View.VISIBLE);
            if(account==null){
                buttonUploadData.setText("Give Permission");
            }
            buttonUploadData.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    account = GoogleSignIn.getLastSignedInAccount(getApplicationContext());
                    mProgress.hide();
                    if(account==null){
                        getResultsFromApi();
                    }
                    else {
                        mOutputText.setText("Click on the button to upload the data.");
                        locationManager=(LocationManager) getSystemService(Context.LOCATION_SERVICE);
                        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            onGPS();
                        } else {
                            getLocation();
                        }                    }
                }
            });


    }
    private String getTime(){
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        return sdf.format(cal.getTime());
    }

    private boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(), ACCESS_FINE_LOCATION);
        return result == PackageManager.PERMISSION_GRANTED;
    }
    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
    }
    private void onGPS() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Enable GPS").setCancelable(false).setPositiveButton("Yes", new  DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        }).setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        final AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }
    private void getLocation() {
        if (ActivityCompat.checkSelfPermission(
                MainActivity.this,Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
        } else {
            locationManager=(LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location locationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (locationGPS != null) {
                double lat = locationGPS.getLatitude();
                double longi = locationGPS.getLongitude();
                String userEmail=account.getEmail();
                String userCurrentTime=getTime();
                String coordinates=lat+"\'"+longi+"\"";
                sendData(userEmail,userCurrentTime,coordinates);
            } else {
                Toast.makeText(this, "Unable to find location.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void sendData(String userEmail, String userCurrentTime, String coordinates) {
        Log.d(TAG, "getLocation: "+userEmail+" "+userCurrentTime+" "+coordinates);
        mCredential =GoogleAccountCredential.usingOAuth2(MainActivity.this, Collections.singleton(SheetsScopes.SPREADSHEETS));
        mCredential.setSelectedAccount(account.getAccount());
        new MakeRequestTask(mCredential,userEmail,userCurrentTime,coordinates).execute();
    }


    private class MakeRequestTask extends AsyncTask<Void, Void, Boolean> {
        private com.google.api.services.sheets.v4.Sheets mService = null;
        private Exception mLastError = null;
        String userEmail,userTime,userCoordination;
        MakeRequestTask(GoogleAccountCredential credential,String userEmail,String userTime, String userCoordination){
            this.userEmail=userEmail;
            this.userTime=userTime;
            this.userCoordination=userCoordination;
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.sheets.v4.Sheets.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("GoogleSignIn")
                    .build();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                putDataFromApi(userEmail,userTime,userCoordination);
                return true;
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return false;
            }
        }



        private List<String> putDataFromApi(String userEmail,String userTime, String userCoordination) throws IOException {
            String range = "Class Data!A2:B";
            Object email= userEmail;
            Object time = userTime;
            Object coordinates = userCoordination;
            ValueRange body = new ValueRange()
                    .setValues(Arrays.asList(
                            Arrays.asList(email, time,coordinates)
                    ));
            AppendValuesResponse result =
                    this.mService.spreadsheets().values().append(spreadsheetId, range, body)
                            .setValueInputOption("RAW")
                            .execute();
            Log.d("pavan", "putDataFromApi: "+result.getUpdates().getUpdatedCells());
            return null;

        }


        @Override
        protected void onPreExecute() {
            mOutputText.setText("");
            mProgress.show();
            mProgress.setMessage("Uploading Data.");
        }

        @Override
        protected void onPostExecute(Boolean bool) {
            mProgress.hide();
            if (!bool) {
                mOutputText.setText("Could'nt upaload the data.");
            } else {
                mOutputText.setText("Data uploaded!");
            }
        }


    }


}

