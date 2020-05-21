package ck.projectclock.database;

import android.provider.BaseColumns;

public class DatabaseContract
{


    public static final String CREATE_PROJECTS_TABLE =
            "CREATE TABLE IF NOT EXISTS " + Projects.TABLE_NAME + " (" +
                    Projects._ID + " INTEGER PRIMARY KEY," +
                    Projects.COL_PROJECT_ID + " INTEGER, " +
                    Projects.COL_PROJECT_NAME + " TEXT, " +
                    Projects.COL_DESCRIPTION + " TEXT, " +
                    Projects.COL_COMPLETED + " INTEGER, " +
                    Projects.COL_ARCHIVED + " INTEGER)";



    public static final String CREATE_TIME_EVENT_TABLE =
            "CREATE TABLE IF NOT EXISTS " + TimeEvent.TABLE_NAME + " (" +
                    TimeEvent._ID + " INTEGER PRIMARY KEY," +
                    TimeEvent.COL_PROJECT_ID + " INTEGER, " +
                    TimeEvent.COL_TIME_IN + " INTEGER, " +
                    TimeEvent.COL_TIME_OUT + " INTEGER DEFAULT 0)";




    public static class Projects implements BaseColumns
    {
        static final String TABLE_NAME = "projects";
        static final String COL_PROJECT_ID = "pid";
        static final String COL_PROJECT_NAME = "project_name";
        static final String COL_DESCRIPTION = "description";
        static final String COL_COMPLETED = "completed";
        static final String COL_ARCHIVED = "archive";
    }


    public static class TimeEvent implements BaseColumns
    {
        static final String TABLE_NAME = "TimeEvent";
        static final String COL_PROJECT_ID = "pid";
        static final String COL_TIME_IN = "time_in";
        static final String COL_TIME_OUT = "time_out";
    }
}
