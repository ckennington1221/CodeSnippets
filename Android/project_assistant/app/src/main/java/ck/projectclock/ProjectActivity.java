package ck.projectclock;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import ck.projectclock.database.DatabaseHelper;

public class ProjectActivity extends Activity
{
    Project project;
    DatabaseHelper dbHelper;

    TextView projectName, projectDescription, totalTime;
    Switch timeSwitch;
    CheckBox completed, archived;
    ConstraintLayout timeContainer;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project);

        dbHelper = new DatabaseHelper(this);
        project = dbHelper.getProject(getIntent().getIntExtra("id", -1));
        if(project == null)
            finish();

        projectName = findViewById(R.id.project_name);
        projectDescription = findViewById(R.id.project_description);
        timeSwitch = findViewById(R.id.time_switch);
        completed = findViewById(R.id.chk_completed);
        archived = findViewById(R.id.chk_archived);
        timeContainer = findViewById(R.id.project_time_container);
        totalTime = findViewById(R.id.project_total_time);

        projectName.setText(project.getName());
        projectDescription.setText(project.getDescription());
        timeSwitch.setChecked(project.isActive());
        completed.setChecked(project.isCompleted());
        archived.setChecked(project.isArchived());
        timeContainer.setOnClickListener(onTimeContainerClickListener);
        calculateTotalTime();

        timeSwitch.setOnCheckedChangeListener(onTimeSwitchChanged);
        completed.setOnCheckedChangeListener(onCompletedChanged);
        archived.setOnCheckedChangeListener(onArchivedChanged);
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        timeSwitch.setOnCheckedChangeListener(null);
        completed.setOnCheckedChangeListener(null);
        archived.setOnCheckedChangeListener(null);
    }

    private void calculateTotalTime()
    {
        int totalMinutes = 0;
        int totalHours = 0;
        int difference;

        for(TimeEvent e : project.getTimeEvents())
        {
            if(e.timeOut == 0)
                difference = (int)(System.currentTimeMillis() - e.timeIn);
            else
                difference = (int)(e.timeOut - e.timeIn);

            int seconds = (difference/1000);
            int hours = (int)Math.floor(seconds / 3600.0);
            int minutes = (int)Math.ceil((seconds - (hours*3600.0)) / 60.0);

            totalHours += hours;
            totalMinutes += minutes;
        }

        int additionalHours = totalHours / 60;
        totalHours += additionalHours;
        totalMinutes = totalMinutes % 60;

        totalTime.setText(String.format("%02d:%02d", totalHours, totalMinutes));
    }

    private View.OnClickListener onTimeContainerClickListener = new View.OnClickListener()
    {
        @Override
        public void onClick(View v)
        {
            Intent i = new Intent(ProjectActivity.this, TimeEventActivity.class);
            i.putExtra("id", project.getId());
            startActivity(i);
        }
    };

    private CompoundButton.OnCheckedChangeListener onCompletedChanged = new CompoundButton.OnCheckedChangeListener()
    {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
            dbHelper.updateCompletedState(project.getId(), isChecked);
        }
    };

    private CompoundButton.OnCheckedChangeListener onArchivedChanged = new CompoundButton.OnCheckedChangeListener()
    {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
            dbHelper.updateArchivedState(project.getId(), isChecked);
        }
    };

    private CompoundButton.OnCheckedChangeListener onTimeSwitchChanged = new CompoundButton.OnCheckedChangeListener()
    {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
            Log.i("TEST", "TEST");
            project.setActive(dbHelper.addTimeEvent(project.getId()));
        }
    };
}
