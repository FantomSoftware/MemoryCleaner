package com.twicebiz.memorycleaner;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

public class MainScrollingActivity extends AppCompatActivity {

    private InternalFileEnumerator ife = new InternalFileEnumerator(true); // true = TEST AND NO DELETE! / false = DELETE ORIGINAL => RELEASE VERSION!
    private int PERMISSION_ALL = 1;
    private String[] PERMISSIONS = {android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private Uri uriSD = null;
    private int COPY_COUNT_SETS = 5; // count of files to copy in one step

    protected boolean hasPermissions() {
        boolean perm = true;
        for (String sp: PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(MainScrollingActivity.this, sp) != PackageManager.PERMISSION_GRANTED) {
                perm = false;
            }
        }
        return perm;
    } // hasPermissions

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), R.string.Permissions_granted, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), R.string.Permissions_denied, Toast.LENGTH_SHORT).show();
                }
                return;
            }
            default: {
                Log.d("PERMISSIONS", "Request code for onRequestPermissionsResult is "+ requestCode);
            }
        }
    } // onRequestPermissionsResult

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_scrolling);
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActivityCompat.requestPermissions(MainScrollingActivity.this, PERMISSIONS, PERMISSION_ALL);

        NumberPicker daysPicker = (NumberPicker)findViewById(R.id.daysNumberPicker);
        daysPicker.setMaxValue(120);
        daysPicker.setMinValue(0);
        daysPicker.setWrapSelectorWheel(false);
        daysPicker.setValue(30);

        FloatingActionButton fapprove = (FloatingActionButton)findViewById(R.id.fapprove);
        fapprove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, R.string.Snack_approve_label, Snackbar.LENGTH_LONG).setAction(R.string.Snack_approve_button,
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                // Start to clean (moving, deleting, ...) --- let the last user approve before???
                                TextView scrollingTV = (TextView) findViewById(R.id.mainScrollingTextview);
                                CheckBox testmodeCB = (CheckBox) findViewById(R.id.testModeCB);
                                FloatingActionButton fapprove = (FloatingActionButton)findViewById(R.id.fapprove);
                                fapprove.setVisibility(View.GONE);

                                scrollingTV.setText("");

                                if (!ife.readyToMove()) {
                                    scrollingTV.append("\nError: NOTHING TO MOVE OR SD CARD ERROR!"); // TODO RESOURCE
                                    return;
                                }

                                takeCardUriPermission(ife.SDPATH);
                                Uri uri = getUri();
                                if (uri!=null) {
                                    if (testmodeCB.isChecked()) scrollingTV.append("Running in TEST MODE - not moving, just copying into "+ife.copyTestFolder+" folder!\nPLEASE WAIT AND NOT IT TURN OFF TILL FINISHED!\n");
                                    else scrollingTV.append("Checked... Let's start to move!\nPLEASE WAIT AND NOT IT TURN OFF TILL FINISHED!\n");
                                    uriSD = uri;
                                    int testMode = 0;
                                    if (testmodeCB.isChecked()) testMode = 1;
                                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                                    new AsyncMoveTask().execute(testMode);
                                    //String ret = ife.moveFilesBySAF(getApplicationContext(), uri);
                                    //scrollingTV.append(ret);
                                    return;
                                }

                                scrollingTV.setText("Grant permission to SD Card please and run it again.\n\nIf you don't see any dialog for permission:\nYour Android phone is not fully supported!\nSorry, you can use it only for analysis...");
                                return;

                                // backup for older phones = legacy
                                //scrollingTV.append("API issue - probably older Android.\nSwitching to compatibility mode...\n");
                                //new AsyncMoveTask().execute();
                            }
                        }
                ).show();
            }
        }); // fapprove.setOnClickListener

        FloatingActionButton fab = (FloatingActionButton)findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView scrollingTV = (TextView)findViewById(R.id.mainScrollingTextview);
                NumberPicker daysPicker = (NumberPicker)findViewById(R.id.daysNumberPicker);
                FloatingActionButton fapprove = (FloatingActionButton)findViewById(R.id.fapprove);
                String text = "";
                int days = daysPicker.getValue();
                fapprove.setVisibility(View.GONE);

                if (!hasPermissions()) {
                    Toast.makeText(getApplicationContext(), R.string.Permissions_denied, Toast.LENGTH_SHORT).show();
                    scrollingTV.setText(R.string.Permissions_denied_explain);
                    return;
                }

                scrollingTV.setText(R.string.Analysing);

                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                new AsyncAnalyseTask().execute(days);
            }
        }); // fab.setOnClickListener


    } // onCreate

    //https://stackoverflow.com/questions/9671546/asynctask-android-example
    //https://stackoverflow.com/questions/6053602/what-arguments-are-passed-into-asynctaskarg1-arg2-arg3
    protected class AsyncAnalyseTask extends AsyncTask<Integer, Void, String> {
        @Override protected String doInBackground(Integer... params) {
            int days = params[0];
            return ife.scanMemoryForOld(getApplicationContext(), days);
        }

        @Override protected void onPostExecute(String result) {
            FloatingActionButton fapprove = (FloatingActionButton)findViewById(R.id.fapprove);
            TextView scrollingTV = (TextView)findViewById(R.id.mainScrollingTextview);
            CheckBox testmodeCB = (CheckBox) findViewById(R.id.testModeCB);
            if (testmodeCB.isChecked()) result = result + "\n\nRUNNING IN TEST MODE!\nNot move, just copy files to demo folder on SD Card: " + ife.copyTestFolder + "\n";
            scrollingTV.setText(result);
            if (ife.bLastrunSuccess) {
                if (ife.getLastCountToClean()>0)
                    fapprove.setVisibility(View.VISIBLE);
            }
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    } // AsyncAnalyseTask

    protected class AsyncMoveTask extends AsyncTask<Integer, Void, String> {
        @Override protected String doInBackground(Integer... params) {
            //return ife.moveLegacyFiles();
            int testMode = params[0];
            Boolean bTestMode = (testMode > 0);
            if (uriSD != null) return ife.moveFilesBySAF(getApplicationContext(), uriSD, COPY_COUNT_SETS, bTestMode);
            return "\nINTERNAL ERROR!";
        }

        @Override protected void onPostExecute(String result) {
            TextView scrollingTV = (TextView)findViewById(R.id.mainScrollingTextview);
            CheckBox testmodeCB = (CheckBox) findViewById(R.id.testModeCB);
            int testMode = 0;
            if (testmodeCB.isChecked()) testMode = 1;
            if (ife.readyToMove()) {
                scrollingTV.append(result);
                new AsyncMoveTask().execute(testMode);
            } else {
                scrollingTV.append(result + "\n\n -- FINISHED -- ");
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
    } // AsyncMoveTask


    // -- https://stackoverflow.com/questions/54945401/android-ask-write-to-sd-card-permission-dialog
    // dalsi kroky zde:
    // INTERNAL FILE ENUMERATOR si jen pri analyze vybuduje seznam souboru k presunu, presun samotny bude delat main scroling aktivita - vezme si to z verejneho seznamu
    // https://stackoverflow.com/questions/36862675/android-sd-card-write-permission-using-saf-storage-access-framework
    // https://stackoverflow.com/questions/36023334/android-how-to-use-new-storage-access-framework-to-copy-files-to-external-sd-c

    private void takeCardUriPermission(String sdCardRootPath) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            File sdCard = new File(sdCardRootPath);
            StorageManager storageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
            StorageVolume storageVolume = storageManager.getStorageVolume(sdCard);
            Intent intent = storageVolume.createAccessIntent(null);
            try {
                startActivityForResult(intent, 4010);
                Toast.makeText(getApplicationContext(), "takeCardUriPermission - activity started", Toast.LENGTH_SHORT).show();
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getApplicationContext(), "takeCardUriPermission - activity failed", Toast.LENGTH_SHORT).show();
            }
        }
    } // takeCardUriPermission

    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 4010) {
            Toast.makeText(getApplicationContext(), "onActivityResult - activity from takeCardUriPermission", Toast.LENGTH_SHORT).show();
            Uri uri = data.getData();
            grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        }
    } // onActivityResult

    protected Uri getUri() {
        List<UriPermission> persistedUriPermissions = getContentResolver().getPersistedUriPermissions();
        if (persistedUriPermissions.size() > 0) {
            UriPermission uriPermission = persistedUriPermissions.get(0);
            return uriPermission.getUri();
        }
        return null;
    } // getUri
}

// Poznamky:
// https://stackoverflow.com/questions/45898179/edittext-not-set-after-device-rotation
// https://zapisky.info/idea-qwertz-klavesnice-altgr-a-hranate-zavorky/
