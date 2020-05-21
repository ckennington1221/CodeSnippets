package ck.projectclock.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Random;

import ck.projectclock.Project;
import ck.projectclock.TimeEvent;

import static ck.projectclock.database.DatabaseContract.CREATE_PROJECTS_TABLE;
import static ck.projectclock.database.DatabaseContract.CREATE_TIME_EVENT_TABLE;

public class DatabaseHelper extends SQLiteOpenHelper
{
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "projects_clock_data.db";

    public DatabaseHelper(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }


    @Override
    public void onCreate(SQLiteDatabase db)
    {
        db.execSQL(CREATE_PROJECTS_TABLE);
        db.execSQL(CREATE_TIME_EVENT_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {

    }


    public void createNewProject(String name, String description)
    {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        int id = StringRandomizer(name).hashCode();
        ContentValues values = new ContentValues();

        values.put(DatabaseContract.Projects.COL_PROJECT_ID, id);
        values.put(DatabaseContract.Projects.COL_PROJECT_NAME, name);
        values.put(DatabaseContract.Projects.COL_DESCRIPTION, description);
        values.put(DatabaseContract.Projects.COL_COMPLETED, 0);
        values.put(DatabaseContract.Projects.COL_ARCHIVED, 0);

        long result = db.insert(DatabaseContract.Projects.TABLE_NAME, null, values);
        if(result != -1)
            Log.i("DB", "Created project");

        db.setTransactionSuccessful();
        db.endTransaction();
    }


    public ArrayList<Project> getProjects()
    {
        ArrayList<Project> projects = new ArrayList<>();

        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT * FROM " + DatabaseContract.Projects.TABLE_NAME;

        Cursor c = db.rawQuery(sql, null);
        while (c.moveToNext())
        {
            Project p = new Project();
            p.setId(c.getInt(c.getColumnIndex(DatabaseContract.Projects.COL_PROJECT_ID)));
            p.setName(c.getString(c.getColumnIndex(DatabaseContract.Projects.COL_PROJECT_NAME)));
            p.setDescription(c.getString(c.getColumnIndex(DatabaseContract.Projects.COL_DESCRIPTION)));
            p.setCompleted((c.getInt(c.getColumnIndex(DatabaseContract.Projects.COL_COMPLETED)) == 1));
            p.setArchived((c.getInt(c.getColumnIndex(DatabaseContract.Projects.COL_ARCHIVED)) == 1));

            projects.add(p);
        }
        c.close();

        for(int i=0; i<projects.size(); i++)
        {
            projects.get(i).setTimeEvents(getProjectTimeEvents(projects.get(i).getId()));

            boolean active = false;
            for(TimeEvent e : projects.get(i).getTimeEvents())
                if(e.getTimeOut() == 0)
                    active = true;

            projects.get(i).setActive(active);

        }

        return projects;
    }



    public Project getProject(int id)
    {
        boolean found = false;
        Project p = new Project();

        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT * FROM " + DatabaseContract.Projects.TABLE_NAME +
                " WHERE " + DatabaseContract.Projects.COL_PROJECT_ID + "=" + id;

        Cursor c = db.rawQuery(sql, null);
        if(c.moveToFirst())
        {
            found = true;
            p.setId(c.getInt(c.getColumnIndex(DatabaseContract.Projects.COL_PROJECT_ID)));
            p.setName(c.getString(c.getColumnIndex(DatabaseContract.Projects.COL_PROJECT_NAME)));
            p.setDescription(c.getString(c.getColumnIndex(DatabaseContract.Projects.COL_DESCRIPTION)));
            p.setCompleted((c.getInt(c.getColumnIndex(DatabaseContract.Projects.COL_COMPLETED)) == 1));
            p.setArchived((c.getInt(c.getColumnIndex(DatabaseContract.Projects.COL_ARCHIVED)) == 1));
        }
        c.close();

        if (found)
        {
            //get list of project time events
            p.setTimeEvents(getProjectTimeEvents(p.getId()));


            //Check if project has an active timer
            boolean active = false;
            for(TimeEvent e : p.getTimeEvents())
                if(e.getTimeOut() == 0)
                    active = true;

            p.setActive(active);

            return p;
        }
        else
            return null;
    }


    public ArrayList<TimeEvent> getProjectTimeEvents(int id)
    {
        SQLiteDatabase db = getReadableDatabase();
        ArrayList<TimeEvent> events = new ArrayList<>();

        String sql = "SELECT * FROM " + DatabaseContract.TimeEvent.TABLE_NAME +
            " WHERE " + DatabaseContract.TimeEvent.COL_PROJECT_ID + "=" + id;

        Cursor c = db.rawQuery(sql, null);

        while(c.moveToNext())
        {
            TimeEvent e = new TimeEvent(
                    id,
                    c.getLong(c.getColumnIndex(DatabaseContract.TimeEvent.COL_TIME_IN)),
                    c.getLong(c.getColumnIndex(DatabaseContract.TimeEvent.COL_TIME_OUT))
            );

            events.add(e);
        }

        Log.i("TimeEvent", "returning " + events.size() + " events");
        return events;
    }


    public boolean addTimeEvent(int id)
    {
        SQLiteDatabase db = getWritableDatabase();
        boolean isActive = false;

        int rowID = hasActiveTimeEvent(db, id);

        db.beginTransaction();
        ContentValues values = new ContentValues();

        if(rowID > 0)
        {
            Log.i("TimeEvent", "Updated time event");
            values.put(DatabaseContract.TimeEvent.COL_TIME_OUT, System.currentTimeMillis());
            db.update(DatabaseContract.TimeEvent.TABLE_NAME,values, DatabaseContract.TimeEvent._ID+"=?", new String[]{String.valueOf(rowID)});
        }
        else
        {
            Log.i("TimeEvent", "Added new time event");
            isActive = true;
            values.put(DatabaseContract.TimeEvent.COL_PROJECT_ID, id);
            values.put(DatabaseContract.TimeEvent.COL_TIME_IN, System.currentTimeMillis());
            db.insert(DatabaseContract.TimeEvent.TABLE_NAME, null, values);
        }

        db.setTransactionSuccessful();
        db.endTransaction();

        return isActive;
    }


    public void updateCompletedState(int id, boolean completed)
    {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        ContentValues values = new ContentValues();
        values.put(DatabaseContract.Projects.COL_COMPLETED, (completed)?1:0);

        int result = db.update(DatabaseContract.Projects.TABLE_NAME, values, DatabaseContract.Projects.COL_PROJECT_ID + "=?", new String[]{String.valueOf(id)});
        Log.d("Database", "updateCompletedState: " + result);

        db.setTransactionSuccessful();
        db.endTransaction();
    }

    public void updateArchivedState(int id, boolean archived)
    {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        ContentValues values = new ContentValues();
        values.put(DatabaseContract.Projects.COL_ARCHIVED, (archived)?1:0);

        int result = db.update(DatabaseContract.Projects.TABLE_NAME, values, DatabaseContract.Projects.COL_PROJECT_ID + "=?", new String[]{String.valueOf(id)});
        Log.d("Database", "updateArchivedState: " + result);

        db.setTransactionSuccessful();
        db.endTransaction();
    }


    private int hasActiveTimeEvent(SQLiteDatabase db, int id)
    {
        String sql = "SELECT * FROM " + DatabaseContract.TimeEvent.TABLE_NAME +
                " WHERE " + DatabaseContract.TimeEvent.COL_PROJECT_ID + "=" + id +
                 " AND " + DatabaseContract.TimeEvent.COL_TIME_OUT + "=" + 0;

        Cursor c = db.rawQuery(sql, null);

        int rowId = 0;
        if(c.moveToFirst())
            rowId = c.getInt(c.getColumnIndex((DatabaseContract.TimeEvent._ID)));

        c.close();

        return rowId;
    }





    private String StringRandomizer(String s)
    {
        int length = s.length();
        char[] inputArray = s.toCharArray();

        String randomString = "";
        Random r = new Random();

        for(int i=0; i<length; i++)
        {
            int index = r.nextInt(length);
            while(inputArray[index] == ' ')
            {
                index = r.nextInt(length);
            }

            randomString += inputArray[index];
            inputArray[index] = ' ';
        }

        Log.i("StringRandomizer", "input: " + s + " --- output: " + randomString);
        return randomString;
    }
}
