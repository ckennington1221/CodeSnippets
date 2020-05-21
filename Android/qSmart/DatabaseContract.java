package com.pitmasteriq.qsmart.database;

import android.provider.BaseColumns;

public class DatabaseContract
{

    //TABLES
    //-data
    //--stores data to be graphed or exported
    //--date, address, session id, pit set, probe 1 temp, probe 2 temp, probe 3 temp

    //-units
    //--stores information about each unit
    //--address, default name, display name, probe 2 name, probe 3 name


    public static final String CREATE_DATA_TABLE =
            "CREATE TABLE IF NOT EXISTS " + Data.TABLE_NAME + " (" +
                    Data._ID + " INTEGER PRIMARY KEY," +
                    Data.COL_DATE + " INTEGER," +
                    Data.COL_SESSION_ID + " INTEGER," +
                    Data.COL_ADDRESS + " TEXT," +
                    Data.COL_PIT_SET + " INTEGER," +
                    Data.COL_PROBE1_TEMP + " INTEGER," +
                    Data.COL_PROBE2_TEMP + " INTEGER," +
                    Data.COL_PROBE3_TEMP + " INTEGER)";


    public static final String CREATE_UNITS_TABLE =
            "CREATE TABLE IF NOT EXISTS " + Units.TABLE_NAME + " (" +
                    Units._ID + " INTEGER PRIMARY KEY," +
                    Units.COL_ADDRESS + " TEXT," +
                    Units.COL_DEFAULT_NAME + " TEXT," +
                    Units.COL_NAME + " TEXT," +
                    Units.COL_PROBE2_NAME + " TEXT," +
                    Units.COL_PROBE3_NAME + " TEXT," +
                    Units.COL_PASSCODE + " INTEGER)";



    public static class Data implements BaseColumns
    {
        static final String TABLE_NAME = "data";
        static final String COL_DATE = "date";
        static final String COL_ADDRESS = "address";
        static final String COL_SESSION_ID = "session_id";
        static final String COL_PIT_SET = "pit_set";
        static final String COL_PROBE1_TEMP = "probe1_temp";
        static final String COL_PROBE2_TEMP = "probe2_temp";
        static final String COL_PROBE3_TEMP = "probe3_temp";
    }

    public static class Units implements BaseColumns
    {
        static final String TABLE_NAME = "units";
        static final String COL_ADDRESS = "address";
        static final String COL_DEFAULT_NAME = "default_name";
        static final String COL_NAME = "name";
        static final String COL_PROBE2_NAME = "probe2_name";
        static final String COL_PROBE3_NAME = "probe3_name";
        static final String COL_PASSCODE = "passcode";
    }





    // Recipes
    //TABLES
    //-recipes
    //--recipe id, name, description

    //-recipe steps
    //--recipe id, step number, instructions

    //-recipe step ingredients
    //--recipe id, step number, ingredient id, amount required

    //-ingredients
    //--ingredient id, name



}
