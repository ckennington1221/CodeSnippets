package ckdev.smartpaws;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.iid.FirebaseInstanceId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ckdev.smartpaws.ButtonSetup.ButtonSetupActivity;
import ckdev.smartpaws.ButtonSetup.Console;
import ckdev.smartpaws.database.ButtonDbHelper;

import static ckdev.smartpaws.ButtonSetup.ButtonSetupActivity.PARAM_BUTTON;
import static ckdev.smartpaws.ButtonSetup.ButtonSetupActivity.PARAM_NEW;

public class MainActivity extends Activity
{

    public static final String HOST = "http://smartpaws.org/sp/";
    public static final String ADD_SCRIPT = "add.php?";
    public static final String UPDATE_TOKEN_SCRIPT = "updateToken.php?";
    public static final String UPDATE_SETTINGS_SCRIPT = "updateSettings.php?";
    public static final String REMOVE_SCRIPT = "remove.php?";
    public static final String SEARCH_SCRIPT = "search.php?";

    private ImageButton addDevice;
    private ListView list;
    private DeviceAdapter adapter;
    private String token;
    private ButtonDbHelper dbHelper;

    private SharedPreferences appPrefs;
    private SharedPreferences.Editor appPrefsEditor;

    private SharedPreferences devicePrefs;
    private SharedPreferences.Editor deviceEditor;

    private Set<String> addresses = new HashSet<>();
    private ArrayList<PetButton> buttons = new ArrayList<>();

    private String addressToRemove = null;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //start firebase messaging service, used to receive messages from the button
        startService(new Intent(this, MyFirebaseMessagingService.class));

        //start firebase instance service, used to get unique token from firebase
        startService(new Intent(this, MyFirebaseInstanceIDService.class));

        //create database helper object
        dbHelper = new ButtonDbHelper(this);

        //get token from firebase
        FirebaseInstanceId.getInstance().getToken();

        //get an app preference object
        appPrefs = getSharedPreferences(AppPreferences.FILE, MODE_PRIVATE);
        appPrefsEditor = appPrefs.edit();

        //set up UI list that displays buttons
        list = findViewById(R.id.list);
        adapter = new DeviceAdapter(this, new ArrayList<PetButton>());
        list.setAdapter(adapter);

        list.setOnItemClickListener(onItemClickListener);
        list.setOnItemLongClickListener(onItemLongClickListener);

        addDevice = findViewById(R.id.btn_add_device);
        addDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(MainActivity.this, ButtonSetupActivity.class);
                startActivity(i);
            }
        });

        loadDevices();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDevices();
    }

    @Override
    protected void onDestroy()
    {
        dbHelper.close();
        super.onDestroy();
    }

    /**
     * Load a list of devices from the local app database. Store each device as a PetButton object
     */
    private void loadDevices()
    {
        buttons.clear();

        buttons = dbHelper.getButtons();
        for(PetButton b : buttons)
            adapter.addButton(b);
    }


    /**
     * When a list item is clicked open the button setup in edit mode.
     * Essentially allows editing of a button using the same system as adding with minor changes
     */
    private AdapterView.OnItemClickListener onItemClickListener = new AdapterView.OnItemClickListener()
    {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l)
        {
            Intent i = new Intent(MainActivity.this, ButtonSetupActivity.class);
            i.putExtra(PARAM_NEW, false);
            i.putExtra(PARAM_BUTTON, buttons.get(pos));
            startActivity(i);
        }
    };

    /**
     * On a long click event open a prompt asking if the user would like to remove the button.
     * If yes.. do that..
     * If no.. don't..
     */
    private AdapterView.OnItemLongClickListener onItemLongClickListener = new AdapterView.OnItemLongClickListener()
    {
        @Override
        public boolean onItemLongClick(AdapterView<?> adapterView, View view, final int pos, long l)
        {
            addressToRemove = adapter.getItem(pos).getMac();

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Remove " + adapter.getItem(pos).getName());
            builder.setMessage("Are you sure you want to remove this button?");
            builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int i)
                {
                    addressToRemove = null;
                    dialog.dismiss();
                }
            });

            builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int i) {
                    String url = HOST + REMOVE_SCRIPT;
                    url += "address=" + adapter.getItem(pos).getMac();
                    url += "&token=" + getSharedPreferences(AppPreferences.FILE, MODE_PRIVATE).getString(AppPreferences.TOKEN, "");

                    sendRemoveRequestToServer(url);
                    dialog.dismiss();
                }
            });

            builder.show();
            return true;
        }
    };


    private void removeButton()
    {
        //remove button from local database
        dbHelper.removeButton(addressToRemove);

        //remove button from UI list
        adapter.removeButton(addressToRemove);
    }


    /**
     * sends an http get request to remove button to the url specified
     * @param url to send get request to
     */
    private void sendRemoveRequestToServer(String url)
    {
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
                        //if request succeeded show toast indicating that!
                        Log.i(Console.REMOVE, response.substring(0,response.length()));
                        Toast.makeText(MainActivity.this, "Button removed", Toast.LENGTH_SHORT).show();

                        //button has been removed from server, remove it locally
                        removeButton();
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        // TODO Error handling
                        Log.i(Console.REMOVE,"Something went wrong!");
                        Toast.makeText(MainActivity.this, "Button not removed", Toast.LENGTH_SHORT).show();
                        error.printStackTrace();
                    }
                });

        Volley.newRequestQueue(this).add(stringRequest);
    }
}
