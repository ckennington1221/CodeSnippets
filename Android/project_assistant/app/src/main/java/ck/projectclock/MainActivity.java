package ck.projectclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

import java.util.ArrayList;

import ck.projectclock.database.DatabaseHelper;

public class MainActivity extends AppCompatActivity
{
    RecyclerView list;
    ImageButton addProject;
    ProjectsAdapter adapter;
    ArrayList<Project> projects = new ArrayList<>();
    DatabaseHelper dbHelper;
    Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.main_activity_toolbar);
        setSupportActionBar(toolbar);

        dbHelper = new DatabaseHelper(this);

        addProject = findViewById(R.id.btn_add);
        addProject.setOnClickListener(addProjectClicked);

        list = findViewById(R.id.projects_list);
        adapter = new ProjectsAdapter(this, projects, onItemClickListener);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        getProjects();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.main_activity_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        return super.onOptionsItemSelected(item);
    }

    private void getProjects()
    {
        projects.clear();
        projects.addAll(dbHelper.getProjects());
        adapter.notifyDataSetChanged();
    }


    private View.OnClickListener addProjectClicked = new View.OnClickListener()
    {
        @Override
        public void onClick(View v)
        {
            LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
            View dialog = inflater.inflate(R.layout.dialog_new_project, null);

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Create new project");
            builder.setView(dialog);

            final EditText pName = dialog.findViewById(R.id.dialog_new_p_name);
            final EditText pDesc = dialog.findViewById(R.id.dialog_new_p_desc);

            builder.setPositiveButton("Done", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    String name = pName.getText().toString();
                    if(name.length() == 0 || name.trim().length() == 0)
                    {
                        errorMessageDialog("Nice Try...", "You failed to enter a name.");
                    }
                    else
                    {
                        dbHelper.createNewProject(pName.getText().toString(), pDesc.getText().toString());
                        dialog.dismiss();
                        getProjects();
                    }
                }
            });

            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    dialog.dismiss();
                }
            });

            builder.show();
        }
    };



    private void errorMessageDialog(String title, String message)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
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


    private OnItemClickListener onItemClickListener = new OnItemClickListener()
    {
        @Override
        public void onItemClick(View v, int position)
        {
            Intent i = new Intent(MainActivity.this, ProjectActivity.class);
            i.putExtra("id", projects.get(position).getId());
            startActivity(i);
        }
    };
}
