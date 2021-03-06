package appservweb.isilm.beaconguide;

import android.Manifest;
import android.app.AlertDialog;
import android.app.LauncherActivity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.util.Log;

import com.estimote.sdk.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

public class MainMenu extends AppCompatActivity implements TextToSpeech.OnInitListener {

    //Variables
    private ListView menuListItems;
    private ListViewSpeaker speaker;
    private ArrayList<String> values;
    private ArrayAdapter<String> adapter;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean blindFlag = true;
    private boolean handicapFlag = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);
        Toolbar toolbar = (Toolbar) findViewById(R.id.myToolbar);
        setActionBar(toolbar);
        menuListItems = (ListView) findViewById(R.id.lstDownloadedMaps);

        //Check se bluetooth e network sono abilitati.
        //Attenzione: se l'IF non ha successo, l'app si chiude. Se ha successo inizializziamo il TTS e procediamo
        /*
        if(checkNetwork() && checkBluetooth(mBluetoothAdapter)) {
            speaker = new ListViewSpeaker(this, this);
            menuListItems.setLongClickable(true);
            menuListItems.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    Object listItem = menuListItems.getItemAtPosition(position);
                    speaker.speakItem(listItem.toString());
                }
            });
            menuListItems.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener(){
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    Log.v("main long clicked","pos: " + position);
                    Object listItem = menuListItems.getItemAtPosition(position);
                    speaker.speakItem(listItem.toString());
                    sendMessage(position);
                    return true;
                }

            });
        }
        */
        //Lista di test
        values = new ArrayList<String>();

        //Adattatore per lista
        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, android.R.id.text1, values);
        menuListItems.setAdapter(adapter);

        //Check di Permessi su Android SDK per beacons
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {             final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
                    }
                });
                builder.show();
            }
        }

        //Broadcast manager di messaggi per message passing da UI a Background thread di ranging
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter("custom-event-name"));
    }

    @Override
    public void onStop() {
        super.onStop();
        //speaker.destroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        checkNetwork();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBluetooth(mBluetoothAdapter);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        blindFlag = settings.getBoolean("blindFlag", true);
        handicapFlag = settings.getBoolean("handicapFlag", false);
        if (blindFlag) {
            setBlindSpeaker();
        }
        else{
            removeBlindSpeaker();
        }
    }

    private void sendMessage(int id) {
        Intent intent = new Intent("custom-event-name2");
        String selected = values.get(id);
        Log.d("SendMessageMainMenu", selected);
        intent.putExtra("MainMenuClicked", selected);
        intent.putExtra("message", "MessageMainMenuDescr");
        intent.putExtra("blindFlag", blindFlag);
        intent.putExtra("handicapFlag", handicapFlag);
        LocalBroadcastManager.getInstance(this).sendBroadcastSync(intent);
    }

    private void changeAct(ArrayList<Beacon> beacons, ArrayList<Graph> graphs){
        Intent changeActivity = new Intent(this, BeaconsMenu.class);
        changeActivity.putExtra("beacons", beacons);
        changeActivity.putExtra("graphs", graphs);
        startActivity(changeActivity);
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        startActivity(intent);
    }


    //Classe di Broadcast Receiver con comportamento custom per gestire i messaggi ricevuti
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getIntExtra("id", 0) == 1) { //Se l'id del messaggio è 1, sto ricevendo la lista mappe
                String message = intent.getStringExtra("message");
                Log.d("receiver", "Got message: " + message);
                ArrayList<String> places;
                places = (ArrayList<String>) intent.getSerializableExtra("places");
                values.clear();
                for (int k = 0; k < places.size(); k++) {
                    values.add(k, places.get(k));
                }
                adapter.notifyDataSetChanged();
            }
            else if (intent.getIntExtra("id", 0) == 2) { //ID = 2, sono i beacon
                ArrayList<Beacon> beacons;
                ArrayList<Graph> graphs;
                beacons = (ArrayList<Beacon>) intent.getSerializableExtra("beacons");
                graphs = (ArrayList<Graph>) intent.getSerializableExtra("graphs");
                changeAct(beacons, graphs);
            }
            //Il thread di ranging manda messaggi a seconda degli spostamenti. Riceviamo i messaggi ed agiamo
            //Qui andrà gestito il messaggio, vengono fatte cose a seconda del beacon più vicino (incluso nel messaggio)
        }
    };

    //Setup del Text2Speech
    @Override
    public void onInit(int status) {
        Log.d("onInit", "MainMenu");
        speaker.onInit(status);
    }

    //Riempimento del menu opzioni
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options, menu);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = settings.edit();
        boolean isCheckedBlind = settings.getBoolean("blindFlag", true);
        MenuItem item = menu.findItem(R.id.blind);
        item.setChecked(isCheckedBlind);
        boolean isCheckedHandicap = settings.getBoolean("handicapFlag", false);
        item = menu.findItem(R.id.handicap);
        item.setChecked(isCheckedHandicap);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        item.setChecked(!item.isChecked());
        //SharedPreferences settings = getSharedPreferences("settings", 0);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = settings.edit();
        int id = item.getItemId();
        switch (id){
            case R.id.blind: blindFlag = item.isChecked();
                editor.putBoolean("blindFlag", item.isChecked());
                if(!blindFlag) {
                    removeBlindSpeaker();
                }
                else{
                    speaker = new ListViewSpeaker(this, this);
                    setBlindSpeaker();
                }
                editor.commit();
                break;
            case R.id.handicap: handicapFlag = item.isChecked();
                editor.putBoolean("handicapFlag", item.isChecked());
                editor.commit();
                break;
        }

        return super.onOptionsItemSelected(item);
    }



    //Check se c'è connessione ad internet, ritorna true/false
    public boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    //Check se il bluetooth è attivo
    public void checkBluetooth(BluetoothAdapter mBluetoothAdapter){
        if (!mBluetoothAdapter.isEnabled()){
            AlertDialog alertDialog = new AlertDialog.Builder(MainMenu.this).create();
            alertDialog.setTitle("Warning");
            alertDialog.setMessage("Bluetooth non abilitato. Abilitare e riaprire l'app.");
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            Intent intent = new Intent(Intent.ACTION_MAIN);
                            intent.addCategory(Intent.CATEGORY_HOME);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                    });
            alertDialog.show();

        }
    }

    //Check se la connessione ad internet è presente
    public void checkNetwork(){
        if (!isOnline()){
            AlertDialog alertDialog = new AlertDialog.Builder(MainMenu.this).create();
            alertDialog.setTitle("Warning");
            alertDialog.setMessage("Connessione alla rete non abilitata. Abilitare e riaprire l'app per scaricare dati mappe.");
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            Intent intent = new Intent(Intent.ACTION_MAIN);
                            intent.addCategory(Intent.CATEGORY_HOME);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                    });
            alertDialog.show();

        }
    }

    @Override
    protected void onDestroy() {
        speaker.destroy();
        super.onDestroy();
    }

    public void setBlindSpeaker(){
        speaker = new ListViewSpeaker(this, this);
        menuListItems.setLongClickable(true);
        menuListItems.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Object listItem = menuListItems.getItemAtPosition(position);
                if (speaker.isOn())
                    speaker.speakItem(listItem.toString());
            }
        });
        menuListItems.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener(){
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Log.v("main long clicked","pos: " + position);
                Object listItem = menuListItems.getItemAtPosition(position);
                if (speaker.isOn())
                    speaker.speakItem(listItem.toString());
                sendMessage(position);
                return true;
            }

        });
    }

    public void removeBlindSpeaker(){
        if (speaker != null)
            speaker.destroy();
        menuListItems.setLongClickable(false);
        menuListItems.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                sendMessage(position);
            }
        });
    }

}

