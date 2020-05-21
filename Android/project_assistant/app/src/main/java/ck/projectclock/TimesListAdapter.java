package ck.projectclock;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import ck.projectclock.database.DatabaseHelper;

public class TimesListAdapter extends RecyclerView.Adapter<TimesListAdapter.ViewHolder>
{
    ArrayList<TimeEvent> data;
    static DatabaseHelper dbHelper;
    OnItemClickListener listener;

    public TimesListAdapter(Context context, ArrayList<TimeEvent> data, OnItemClickListener listener)
    {
        this.data = data;
        dbHelper = new DatabaseHelper(context);
        this.listener = listener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
    {
        TextView timeIn, timeOut, total, date;
        ImageButton delete;
        public ViewHolder(View v)
        {
            super(v);
            timeIn = v.findViewById(R.id.time_event_row_start);
            timeOut = v.findViewById(R.id.time_event_row_end);
            total = v.findViewById(R.id.time_event_row_total);
            date = v.findViewById(R.id.time_event_row_date);
            delete = v.findViewById(R.id.time_event_row_delete);
        }
    }

    @NonNull
    @Override
    public TimesListAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_time_event, parent, false);
        final TimesListAdapter.ViewHolder vh = new TimesListAdapter.ViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull TimesListAdapter.ViewHolder holder, final int position)
    {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm");

        Date startTime = new Date(data.get(position).getTimeIn());
        Date endTime;

        if(data.get(position).getTimeOut() == 0)
            endTime = new Date(System.currentTimeMillis());
        else
            endTime = new Date(data.get(position).getTimeOut());


        holder.date.setText(dateFormat.format(startTime));
        holder.timeIn.setText(timeFormat.format(startTime));

        holder.timeOut.setText(timeFormat.format(endTime));

        long difference = endTime.getTime() - startTime.getTime();
        int seconds = (int)(difference/1000);
        int hours = (int)Math.floor(seconds / 3600.0);
        int minutes = (int)Math.ceil((seconds - (hours*3600.0)) / 60.0);
        holder.total.setText(String.format("%02d:%02d", hours, minutes));

        holder.delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onItemClick(v, position);
            }
        });
    }

    @Override
    public int getItemCount()
    {
        return data.size();
    }
}
