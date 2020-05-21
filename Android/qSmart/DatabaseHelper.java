package com.pitmasteriq.qsmart.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.pitmasteriq.qsmart.monitor.Device;
import com.pitmasteriq.qsmart.monitor.GraphData;
import com.pitmasteriq.qsmart.monitor.LogTag;
import com.pitmasteriq.qsmart.monitor.Unit;
import com.pitmasteriq.qsmart.monitor.UnitData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.pitmasteriq.qsmart.database.DatabaseContract.CREATE_DATA_TABLE;
import static com.pitmasteriq.qsmart.database.DatabaseContract.CREATE_UNITS_TABLE;

public class DatabaseHelper extends SQLiteOpenHelper
{
    private static final int DATABASE_VERSION = 3;
    private static final String DATABASE_NAME = "pmiq_data.db";
    private SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public DatabaseHelper(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
        Log.i(LogTag.DATABASE, "Creating database tables");
        db.execSQL(CREATE_UNITS_TABLE);
        db.execSQL(CREATE_DATA_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        Log.i(LogTag.DATABASE, "database onUpdate");
        db.execSQL("DROP TABLE IF EXISTS units");
        db.execSQL("DROP TABLE IF EXISTS data");
        onCreate(db);
    }

    public void prepareDatabase()
    {
        SQLiteDatabase db = getWritableDatabase();


        //verify units table exists
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name='" + DatabaseContract.Units.TABLE_NAME + "'";
        Cursor c = db.rawQuery(sql, null);

        if(!c.moveToFirst())
        {
            Log.w(LogTag.DATABASE, "Units table does not exist, creating it");
            db.execSQL(CREATE_UNITS_TABLE);
        }

        sql = "SELECT name FROM sqlite_master WHERE type='table' AND name='" + DatabaseContract.Data.TABLE_NAME + "'";
        c = db.rawQuery(sql, null);

        if(!c.moveToFirst())
        {
            Log.w(LogTag.DATABASE, "Data table does not exist, creating it");
            db.execSQL(CREATE_DATA_TABLE);
        }
    }

    public void saveDevice(Device device)
    {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        ContentValues values = new ContentValues();

        long result = 0;

        if(unitExists(db, device.getAddress()))
        {
            //Update unit record

            if(device.getDisplayName()!= null)
                values.put(DatabaseContract.Units.COL_NAME, device.getDisplayName());

            if(device.getProbe2Name()!= null)
                values.put(DatabaseContract.Units.COL_PROBE2_NAME, device.getProbe2Name());

            if(device.getProbe3Name()!= null)
                values.put(DatabaseContract.Units.COL_PROBE3_NAME, device.getProbe3Name());


            values.put(DatabaseContract.Units.COL_PASSCODE, device.getPasscode());


            if(values.size() > 0)
            {
                result = db.update(DatabaseContract.Units.TABLE_NAME, values,
                        DatabaseContract.Units.COL_ADDRESS + "=?", new String[]{device.getAddress()});
            }
        }
        else
        {
            //Insert unit record
            values.put(DatabaseContract.Units.COL_ADDRESS, device.getAddress());
            values.put(DatabaseContract.Units.COL_DEFAULT_NAME, device.getName());
            values.put(DatabaseContract.Units.COL_NAME, device.getDisplayName());
            values.put(DatabaseContract.Units.COL_PROBE2_NAME, device.getProbe2Name());
            values.put(DatabaseContract.Units.COL_PROBE3_NAME, device.getProbe3Name());

            result = db.insert(DatabaseContract.Units.TABLE_NAME,null, values);
        }


        if(result == -1 || result == 0)
            Log.d(LogTag.DATABASE, "unit save failed");
        else
            Log.d(LogTag.DATABASE, "unit save succeeded");

        db.setTransactionSuccessful();
        db.endTransaction();
    }

    public Device loadDevice(String address)
    {

        SQLiteDatabase db = getReadableDatabase();

        String sql = "Select * FROM " + DatabaseContract.Units.TABLE_NAME +
                " WHERE " + DatabaseContract.Units.COL_ADDRESS + "='" + address + "'";

        Cursor c = db.rawQuery(sql, null);

        if(c.moveToFirst())
        {
            Device device = new Device(
                    address,
                    c.getString(c.getColumnIndex(DatabaseContract.Units.COL_DEFAULT_NAME)));

            device.setDisplayName(c.getString(c.getColumnIndex(DatabaseContract.Units.COL_NAME)));
            device.setProbe2Name(c.getString(c.getColumnIndex(DatabaseContract.Units.COL_PROBE2_NAME)));
            device.setProbe3Name(c.getString(c.getColumnIndex(DatabaseContract.Units.COL_PROBE3_NAME)));
            device.setPasscode((short)c.getInt(c.getColumnIndex(DatabaseContract.Units.COL_PASSCODE)));

            c.close();
            return device;
        }
        else
        {
            c.close();
            return null;
        }
    }


    public void insertData(String address, UnitData data, int sessionId)
    {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        ContentValues v = new ContentValues();
        v.put(DatabaseContract.Data.COL_DATE, System.currentTimeMillis());
        v.put(DatabaseContract.Data.COL_SESSION_ID, sessionId);
        v.put(DatabaseContract.Data.COL_ADDRESS, address);
        v.put(DatabaseContract.Data.COL_PIT_SET, data.getPitSet());
        v.put(DatabaseContract.Data.COL_PROBE1_TEMP, data.getProbe1Temp());
        v.put(DatabaseContract.Data.COL_PROBE2_TEMP, data.getProbe2Temp());
        v.put(DatabaseContract.Data.COL_PROBE3_TEMP, data.getProbe3Temp());

        db.insert(DatabaseContract.Data.TABLE_NAME, null, v);

        db.setTransactionSuccessful();
        db.endTransaction();
    }


    public ArrayList<GraphData> loadData(int session)
    {
       return loadData(session, 0);
    }

    public ArrayList<GraphData> loadData(int session, long time)
    {
        SQLiteDatabase db = getReadableDatabase();
        ArrayList<GraphData> data = new ArrayList<>();

        String sql = "SELECT * FROM " + DatabaseContract.Data.TABLE_NAME +
                " WHERE " + DatabaseContract.Data.COL_SESSION_ID + "=" + session +
                " AND " + DatabaseContract.Data.COL_DATE + ">" + time;

        Cursor c = db.rawQuery(sql, null);

        while(c.moveToNext())
        {
            GraphData d = new GraphData(
                    c.getLong(c.getColumnIndex(DatabaseContract.Data.COL_DATE)),
                    c.getInt(c.getColumnIndex(DatabaseContract.Data.COL_PIT_SET)),
                    c.getInt(c.getColumnIndex(DatabaseContract.Data.COL_PROBE1_TEMP)),
                    c.getInt(c.getColumnIndex(DatabaseContract.Data.COL_PROBE2_TEMP)),
                    c.getInt(c.getColumnIndex(DatabaseContract.Data.COL_PROBE3_TEMP))
            );

            data.add(d);
        }


        c.close();
        return data;
    }

    public Map<String, String> getDataNameList()
    {
        SQLiteDatabase db = getReadableDatabase();
        Map<String, String> results = new HashMap<>();

        String sql = "SELECT DISTINCT(" + DatabaseContract.Data.TABLE_NAME + "." +DatabaseContract.Data.COL_ADDRESS + "),"
                + DatabaseContract.Units.TABLE_NAME + "." + DatabaseContract.Units.COL_DEFAULT_NAME
                + " FROM " + DatabaseContract.Data.TABLE_NAME
                + " JOIN " + DatabaseContract.Units.TABLE_NAME
                + " ON " + DatabaseContract.Data.TABLE_NAME + "." + DatabaseContract.Data.COL_ADDRESS + " = "
                + DatabaseContract.Units.TABLE_NAME + "." + DatabaseContract.Units.COL_ADDRESS;

        Cursor c = db.rawQuery(sql, null);

        while(c.moveToNext())
        {
            results.put(
                    c.getString(0),
                    c.getString(1)
            );
        }

        c.close();
        return results;
    }


    public long[] getMinMaxDateForAddress(String address)
    {
        SQLiteDatabase db = getReadableDatabase();
        long[] data = new long[2];

        String sql = "SELECT MIN(" + DatabaseContract.Data.COL_DATE + "), MAX(" + DatabaseContract.Data.COL_DATE + ")"
                + " FROM " + DatabaseContract.Data.TABLE_NAME
                + " WHERE " + DatabaseContract.Data.COL_ADDRESS + "='" + address + "'";


        Cursor c = db.rawQuery(sql, null);

        if(c.moveToFirst())
        {
            data[0] = c.getLong(0);
            data[1] = c.getLong(1);
        }


        c.close();
        return data;
    }



    public ArrayList<GraphData> getExportData(String address, long start, long end)
    {
        SQLiteDatabase db = getReadableDatabase();
        ArrayList<GraphData> data = new ArrayList<>();


        String sql = "SELECT * FROM " + DatabaseContract.Data.TABLE_NAME +
                " WHERE " + DatabaseContract.Data.COL_ADDRESS + "='" + address + "'" +
                " AND " + DatabaseContract.Data.COL_DATE + " BETWEEN " + start + " AND " + end;

        Cursor c = db.rawQuery(sql, null);

        while(c.moveToNext())
        {
            GraphData d = new GraphData(
                    c.getLong(c.getColumnIndex(DatabaseContract.Data.COL_DATE)),
                    c.getInt(c.getColumnIndex(DatabaseContract.Data.COL_PIT_SET)),
                    c.getInt(c.getColumnIndex(DatabaseContract.Data.COL_PROBE1_TEMP)),
                    c.getInt(c.getColumnIndex(DatabaseContract.Data.COL_PROBE2_TEMP)),
                    c.getInt(c.getColumnIndex(DatabaseContract.Data.COL_PROBE3_TEMP))
            );
            data.add(d);
        }

        c.close();
        return data;
    }


    public void clearGraphData()
    {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        db.execSQL("DELETE FROM " + DatabaseContract.Data.TABLE_NAME);

        db.setTransactionSuccessful();
        db.endTransaction();
    }


    private boolean unitExists(SQLiteDatabase db, String address)
    {
        Cursor c = db.rawQuery("SELECT * FROM " + DatabaseContract.Units.TABLE_NAME + " WHERE "
                + DatabaseContract.Units.COL_ADDRESS + "='" + address + "'", null);

        boolean found = c.moveToFirst();

        c.close();

        return found;
    }
}
