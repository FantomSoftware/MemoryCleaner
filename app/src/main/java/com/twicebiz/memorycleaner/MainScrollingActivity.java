package com.twicebiz.memorycleaner;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

public class MainScrollingActivity extends AppCompatActivity {

    private InternalFileEnumerator ife = new InternalFileEnumerator();
    private int PERMISSION_ALL = 1;
    private String[] PERMISSIONS = {android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE};

    protected boolean hasPermissions() {
        boolean perm = true;
        for (String sp: PERMISSIONS) {
            //if (ContextCompat.checkSelfPermission(MainScrollingActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
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
                    Toast.makeText(getApplicationContext(), "permission was granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Permission denied", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    } // onRequestPermissionsResult

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_scrolling);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActivityCompat.requestPermissions(MainScrollingActivity.this, PERMISSIONS, PERMISSION_ALL);

        NumberPicker daysPicker = (NumberPicker) findViewById(R.id.daysNumberPicker);
        daysPicker.setMaxValue(120);
        daysPicker.setMinValue(0);
        daysPicker.setWrapSelectorWheel(false);
        daysPicker.setValue(30);

        FloatingActionButton fapprove = (FloatingActionButton) findViewById(R.id.fapprove);
        fapprove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "MOVE FILES?", Snackbar.LENGTH_LONG).setAction("DO IT!",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                // Start to clean (moving, deleting, ...) --- let the last user approve before???
                                FloatingActionButton fapprove = (FloatingActionButton) findViewById(R.id.fapprove);
                                NumberPicker daysPicker = (NumberPicker) findViewById(R.id.daysNumberPicker);
                                int days = daysPicker.getValue();
                                fapprove.setVisibility(View.GONE);
                                new AsyncMoveTask().execute(days);
                            }
                        }
                ).show();
            }
        }); // fapprove.setOnClickListener

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView scrollingTV = (TextView) findViewById(R.id.mainScrollingTextview);
                NumberPicker daysPicker = (NumberPicker) findViewById(R.id.daysNumberPicker);
                FloatingActionButton fapprove = (FloatingActionButton) findViewById(R.id.fapprove);
                String text = "";
                int days = daysPicker.getValue();
                fapprove.setVisibility(View.GONE);

                if (!hasPermissions()) {
                    Toast.makeText(getApplicationContext(), "Permissions denied!", Toast.LENGTH_SHORT).show();
                    scrollingTV.setText("Some of permissions were denied!");
                    return;
                }

                //StorageManager storageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
                //startActivityForResult(storageManager.getPrimaryStorageVolume().createAccessIntent("aaa");
                //startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_CODE__DIRECTORTY_PERMISSION);

                scrollingTV.setText("Analysing...");

                new AsyncAnalyseTask().execute(days);
            }
        }); // fab.setOnClickListener
    } // onCreate

    //https://stackoverflow.com/questions/9671546/asynctask-android-example
    //https://stackoverflow.com/questions/6053602/what-arguments-are-passed-into-asynctaskarg1-arg2-arg3
    protected class AsyncAnalyseTask extends AsyncTask<Integer, Void, String> {
        @Override protected String doInBackground(Integer... params) {
            int days = params[0];
            return ife.scanMemoryForOld(getApplicationContext(), days, false);
        }

        @Override protected void onPostExecute(String result) {
            FloatingActionButton fapprove = (FloatingActionButton) findViewById(R.id.fapprove);
            TextView scrollingTV = (TextView) findViewById(R.id.mainScrollingTextview);
            scrollingTV.setText(result);
            if (!result.startsWith("ERROR:")) {
                if (ife.getLastCountToClean()>0)
                    fapprove.setVisibility(View.VISIBLE);
            }
        }
    } // AsyncAnalyseTask

    protected class AsyncMoveTask extends AsyncTask<Integer, Void, String> {
        @Override protected String doInBackground(Integer... params) {
            int days = params[0];
            return ife.scanMemoryForOld(getApplicationContext(), days, true);
        }

        @Override protected void onPostExecute(String result) {
            FloatingActionButton fapprove = (FloatingActionButton) findViewById(R.id.fapprove);
            TextView scrollingTV = (TextView) findViewById(R.id.mainScrollingTextview);
            scrollingTV.setText(result);
        }
    } // AsyncMoveTask
}

// Poznamky:
// https://stackoverflow.com/questions/45898179/edittext-not-set-after-device-rotation
