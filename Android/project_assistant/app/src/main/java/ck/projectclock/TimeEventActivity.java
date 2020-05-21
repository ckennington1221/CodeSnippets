package ck.projectclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import ck.projectclock.database.DatabaseHelper;

public class TimeEventActivity extends Activity
{
    RecyclerView list;
    TimesListAdapter adapter;
    ArrayList<TimeEvent> events = new ArrayList<>();
    DatabaseHelper dbHelper;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_time_event);

        dbHelper = new DatabaseHelper(this);
        events = dbHelper.getProjectTimeEvents(getIntent().getIntExtra("id", -1));
        Log.i("TimeEvent", "Got " + events.size() + " events");


        list = findViewById(R.id.times_list);
        adapter = new TimesListAdapter(this, events, onDeleteClicked);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
    }



    private OnItemClickListener onDeleteClicked = new OnItemClickListener()
    {
        @Override
        public void onItemClick(View v, int position)
        {
            errorMessageDialog("LOL Nope...", "This feature is scheduled for a later date. Try Again Later.");
        }
    };


    private void errorMessageDialog(String title, String message)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(TimeEventActivity.this);
        builder.setTitle(title);
        builder.setMessage(message);

        builder.setPositiveButton("Close", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builder.show();
    }
}


