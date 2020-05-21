package ck.projectclock;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;

import ck.projectclock.database.DatabaseHelper;

public class ProjectsAdapter extends RecyclerView.Adapter<ProjectsAdapter.ViewHolder>
{
    ArrayList<Project> data;
    static DatabaseHelper dbHelper;
    OnItemClickListener listener;
    Context context;

    public ProjectsAdapter(Context context, ArrayList<Project> data, OnItemClickListener listener)
    {
        this.context = context;
        this.data = data;
        dbHelper = new DatabaseHelper(context);
        this.listener = listener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
    {
        TextView name;
        Switch timeSwitch;
        ConstraintLayout container;
        public ViewHolder(View v)
        {

            super(v);
            name = v.findViewById(R.id.row_project_name);
            timeSwitch = v.findViewById(R.id.row_time_switch);
            container = v.findViewById(R.id.row_project_container);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_item, parent, false);
        final ViewHolder vh = new ViewHolder(v);

        v.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                listener.onItemClick(v, vh.getAdapterPosition());
            }
        });

        return vh;
    }



    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, final int position)
    {

        holder.container.setBackground(context.getDrawable(R.drawable.rounded_corners_no_color));

        if(data.get(position).isCompleted())
            holder.container.setBackground(context.getDrawable(R.drawable.rounded_corners_completed));
        if(data.get(position).isArchived())
            holder.container.setBackground(context.getDrawable(R.drawable.rounded_corners_archived));



        holder.name.setText(data.get(position).getName());

        if(data.get(position).isActive())
            holder.timeSwitch.setChecked(true);
        else
            holder.timeSwitch.setChecked(false);

        holder.timeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                Log.i("TEST", "TEST");
                dbHelper.addTimeEvent(data.get(position).getId());
            }
        });
    }


    @Override
    public void onViewDetachedFromWindow(@NonNull ViewHolder holder)
    {
        super.onViewDetachedFromWindow(holder);

        //This removes the listener when the activity switches to the project view
        //...i think... it fixed an issue with the active switches syncing
        holder.timeSwitch.setOnCheckedChangeListener(null);
    }

    @Override
    public int getItemCount()
    {
        return data.size();
    }
}
