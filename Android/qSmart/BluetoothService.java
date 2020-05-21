package com.pitmasteriq.qsmart.monitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import com.pitmasteriq.qsmart.AlarmReceiver;
import com.pitmasteriq.qsmart.MyApplication;
import com.pitmasteriq.qsmart.R;
import com.pitmasteriq.qsmart.database.DatabaseHelper;

import java.nio.ByteBuffer;

import static com.pitmasteriq.qsmart.monitor.Uuid.CONFIG_BASIC;
import static com.pitmasteriq.qsmart.monitor.Uuid.CONFIG_DESCRIPTOR;
import static com.pitmasteriq.qsmart.monitor.Uuid.PASSCODE;
import static com.pitmasteriq.qsmart.monitor.Uuid.SERVICE;
import static com.pitmasteriq.qsmart.monitor.Uuid.STATUS_BASIC;

public class BluetoothService extends Service implements BluetoothCallbackInterface
{

    private static final String LEGACY_IQ_REGEX = "[I][Q][A-F0-9]{4}";
    private static final String IQ_REGEX = "[i][Q][A-F0-9]{4}";

    private static final int EXCEPTION_NOTIFICATION = 1122;
    private static final int CONNECTION_TIMEOUT = 20000;        //20 seconds
    private static final int TEMPERATURE_OFFSET = 145;
    private static final int HEALTH_CHECK_SLEEP = 4000;         //4 seconds
    private static final int DATA_UPDATE_TIMEOUT = 15000;       //max time between updates before forcing a read


    //System Objects
    private IBinder binder = new LocalBinder();
    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private MediaPlayer mp;
    private Handler handler = new Handler();
    private SharedPreferences prefs;
    private Vibrator vibrator;
    private BluetoothGatt connectionHandle;

    //Custom Objects
    private DeviceManager deviceManager;
    private ExceptionManager exceptionManager;
    private LocalBluetoothCallback callbackHandle;
    private DatabaseHelper dbHelper;
    private OnServiceEvent activity;


    private int lastTemperatureHash = 0;
    private int lastExceptionHash = 0;

    private String connectingAddress = "";

    private long lastUpdate = 0;

    private boolean runHealthCheck = true;
    private boolean forceRead = false;
    private boolean writingPasscode = false;
    private boolean alarmActive = false;
    private boolean serviceIsBound = false;


    private int passcodeReadAttempts = 0;

    /*
        Interface used to report events back to Activity
     */
    public interface OnServiceEvent
    {
        void onUnitConnected(String address);
        void onUnitDisconnected(String address, boolean intentional);
        void onUnitAlreadyConnected();
        void onDataUpdate(UnitData data, boolean updateGraph);
        void onWriteSuccess();
        void onWriteFailed();
        void onPasscodeRequest();
        void onConnectionFailed();
        void onBluetoothDisabled();
        void onUnitConnecting();
        void onPasscodeWrong();
    }

    public class LocalBinder extends Binder
    {
        BluetoothService getService()
        {
            return BluetoothService.this;
        }
    }

    public BluetoothService(){}

    public void setCallbacks(OnServiceEvent serviceCallbacks)
    {
        this.activity = serviceCallbacks;
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        //init bluetooth objects
        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        dbHelper = new DatabaseHelper(this);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        deviceManager = DeviceManager.get();

        exceptionManager = ExceptionManager.get();

        //start health check thread
        healthCheckThread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.i(LogTag.DEBUG, "Building notification");
        startForeground(1111, buildNotification());
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        serviceIsBound = true;
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        serviceIsBound = false;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        runHealthCheck = false;
    }

    /**
     * Performs routine health checks on the service and manages starting or stopping the alarm.
     */
    private Thread healthCheckThread = new Thread()
    {
        @Override
        public void run()
        {
            super.run();

            while(runHealthCheck)
            {

                //make sure periodic updates are still coming in
                if(connectionHandle != null)
                {
                    if(lastUpdate > 0 && ((System.currentTimeMillis() - lastUpdate) > DATA_UPDATE_TIMEOUT))
                    {
                        //force a read
                        try
                        {
                            forceRead = true;
                            BluetoothGattCharacteristic c = connectionHandle.getService(SERVICE).getCharacteristic(STATUS_BASIC);
                            connectionHandle.readCharacteristic(c);
                        }
                        catch(NullPointerException e)
                        {
                            e.printStackTrace();
                            Log.e(LogTag.DEBUG, "Failed to force a read. Null connection handle.");
                        }
                    }
                }

                //Check if alarm should start sounding
                if(exceptionManager.shouldNotify())
                {
                    buildExceptionNotification();
                    exceptionManager.notified();
                }

                //cancel alarms
                if(!exceptionManager.hasActiveAlarm() && !exceptionManager.hasActiveNotification())
                {
                    NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    notificationManager.cancel(EXCEPTION_NOTIFICATION);
                }

                //start alarm
                if(exceptionManager.shouldAlarm())
                {
                    startAlarm();
                }


                try
                {
                    //sleep for x seconds
                    Thread.sleep(HEALTH_CHECK_SLEEP);
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }
    };


    /*
        Alarm used to wake users from sleep if grill requires attention
     */
    private Runnable alarmRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), alarm);
            r.play();

            try
            {
                Thread.sleep(20000);
            }
            catch(InterruptedException e){e.printStackTrace();}

            r.stop();
        }
    };

    /*
        Custom notifcation layout with temperature information displayed
     */
    private Notification buildNotification()
    {
        String channelID = "";
        RemoteViews contentView = new RemoteViews(getPackageName(),R.layout.app_notification);
        contentView.setImageViewResource(R.id.notif_logo, R.drawable.ic_logo_color_48);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            channelID = "pmiq_bt_service";
            String channelName = "qSmart Bluetooth Service";
            NotificationChannel channel = new NotificationChannel(channelID,channelName, NotificationManager.IMPORTANCE_LOW);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelID);
        builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_logo_color_48));
        builder.setSmallIcon(R.drawable.ic_logo_color_24);
        builder.setContent(contentView);
        builder.setVisibility(Notification.VISIBILITY_PUBLIC);

        return builder.build();
    }


    private Notification buildExceptionNotification()
    {
        String channelID = "";

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            channelID = "pmiq_exceptions";
            String channelName = "qSmart Exceptions";
            NotificationChannel channel = new NotificationChannel(channelID,channelName, NotificationManager.IMPORTANCE_LOW);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelID);
        builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_logo_color_48));
        builder.setSmallIcon(R.drawable.ic_logo_color_24);
        builder.setVisibility(Notification.VISIBILITY_PUBLIC);
        builder.setContentTitle("qSmart detected an error.");
        builder.setContentText(exceptionManager.getExceptionString()); //set list of errors in the notification layout

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(EXCEPTION_NOTIFICATION, builder.build());

        return builder.build();
    }

    /*
        Updates the custom notification layout with new data when temps change
     */
    private void updateNotification(boolean connected, int t, boolean newError)
    {
        String channelID = "pmiq_bt_service";
        RemoteViews contentView = new RemoteViews(getPackageName(),R.layout.app_notification);
        contentView.setImageViewResource(R.id.notif_logo, R.drawable.ic_logo_color_48);

        if(newError)
            contentView.setImageViewResource(R.id.notif_error, R.drawable.ic_alert_24);
        else
            contentView.setImageViewResource(R.id.notif_error, R.drawable.blank);


        if(connected)
        {
            contentView.setTextViewText(R.id.notif_message, String.valueOf(t));
        }
        else
        {
            contentView.setTextViewText(R.id.notif_message, getString(R.string.no_connection));
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelID);
        builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_logo_color_48));
        builder.setSmallIcon(R.drawable.ic_logo_color_24);
        builder.setContent(contentView);
        builder.setVisibility(Notification.VISIBILITY_PUBLIC);

        if(newError && prefs.getBoolean(Preferences.SOUND, true))
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(1111, builder.build());
    }


    /**
     * Connect to a IQ device
     * @param address The MAC address to connect to
     */
    /*
        This method handles setting up the connection and makes sure that the device is ready for
        a connection to be made.
     */
    public void connect(String address)
    {

        //Make sure blutooth is enabled. otherwise stop and notify user
        if(!btAdapter.isEnabled())
        {
            if(activity !=null )
                activity.onBluetoothDisabled();

            return;
        }

        //Check if we are already connected to a unit, and the last update was less than 10
        //seconds ago. If so, notify user and stop otherwise, start connection attempt process
        if(prefs.getBoolean(Preferences.UNIT_CONNECTED, false) && (System.currentTimeMillis() - lastUpdate < 10000))
        {
            //Already connected
            if(activity != null)
                activity.onUnitAlreadyConnected();
        }
        else
        {
            //Start connection
            BluetoothDevice d = btAdapter.getRemoteDevice(address);

            //Check if a device object already exists, if not create a new one
            if(deviceManager.device() == null)
                deviceManager.newDevice(d.getAddress(), d.getName());
            //check if the connected device is the same as the one we are trying to connect to
            //if not, create a new object
            else if (!deviceManager.device().is(address))
                deviceManager.newDevice(d.getAddress(), d.getName());


            //load device data
            deviceManager.device().load(dbHelper.loadDevice(address));


            //check if passcode is needed, request from user
            if(deviceManager.device().doesRequirePasscode())
            {
                Log.i(LogTag.DEBUG, "Connecting to legacy unit");

                if (deviceManager.device().getPasscode() == 0)
                {
                    connectingAddress = address;
                    Log.i(LogTag.DEBUG, "Requesting passcode from user");
                    if (activity != null)
                        activity.onPasscodeRequest();
                    return;
                }
            }

            //start the actual connection
            initiateConnection(d);
        }
    }

    private void initiateConnection(BluetoothDevice d)
    {
        //notify user that we are starting the connection
        if(activity != null)
            activity.onUnitConnecting();

        callbackHandle = new LocalBluetoothCallback(this);
        connectionHandle = d.connectGatt(this, true, callbackHandle);

        //set up connection timeout
        handler.postDelayed(connectionTimeout, CONNECTION_TIMEOUT);
    }


    public void disconnect()
    {
        if(connectionHandle != null)
            connectionHandle.disconnect();
        else
        {
            for(BluetoothDevice d : btManager.getConnectedDevices(BluetoothProfile.GATT))
            {
                if(d.getName().matches(IQ_REGEX) || d.getName().matches(LEGACY_IQ_REGEX))
                    Log.e(LogTag.DEBUG, "Gatt null, but still connected");
            }
        }
    }


    public void passcodeEntered()
    {
        BluetoothDevice d = btAdapter.getRemoteDevice(deviceManager.device().getAddress());
        initiateConnection(d);
    }

    /*
    Called when a connection with a device was successful. Handles bonding and setting up
    notifications from the bluetooth device.
     */
    private void connectionSuccessful(ConnectionEvent event)
    {
        String address = event.gatt().getDevice().getAddress();
        String name = event.gatt().getDevice().getName();

        //create bond
        if(event.gatt().getDevice().getBondState() != BluetoothDevice.BOND_BONDED)
            event.gatt().getDevice().createBond();

        //update last known address and set unit connected to true
        prefs.edit().putString(Preferences.LAST_CONNECTED_ADDRESS, address)
                .putBoolean(Preferences.UNIT_CONNECTED, true)
                .putBoolean(Preferences.UNINTENTIONAL_DISCONNECT, false).apply();


        //get characteristic to enable notifications on
        BluetoothGattCharacteristic c = event.gatt().getService(SERVICE).getCharacteristic(STATUS_BASIC);
        event.gatt().setCharacteristicNotification(c, true);

        //enable notifications on characteristic
        BluetoothGattDescriptor d = c.getDescriptor(CONFIG_DESCRIPTOR);
        d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        event.gatt().writeDescriptor(d);

        //notify activity of connection
        if(activity != null)
            activity.onUnitConnected(address);
    }


    @Override
    public void onEvent(ConnectionEvent event)
    {
        switch(event.type())
        {
            case ConnectionStateChanged:
                handleConnectionStateChange(event);
                break;

            case CharacteristicChanged:
                handleCharacteristicChanged(event);
                break;

            case CharacteristicWrite:
                handleCharacteristicWrite(event);
                break;
            case CharacteristicRead:
                handleCharacteristicRead(event);
                break;

            case PasscodeWrite:
                handlePasscodeWrite(event);
                        break;
        }
    }



    private void handleConnectionStateChange(ConnectionEvent event)
    {
        String address = event.gatt().getDevice().getAddress();
        String name = event.gatt().getDevice().getName();

        //Device was successfully connected
        if (event.status() == ConnectionEvent.Status.Connected)
        {
            Log.d(LogTag.CONNECTION, "  ");
            Log.d(LogTag.CONNECTION, "Connection State Change:");
            Log.d(LogTag.CONNECTION, "State: Connected");
            Log.d(LogTag.CONNECTION, "Address: " + address);
            Log.d(LogTag.CONNECTION, "  ");

            //stop the connection timeout
            handler.removeCallbacks(connectionTimeout);

            //update the connection state of the currently connected device
            deviceManager.device().connectionStateChanged(Device.ConnectionStatus.CONNECTED);

            //check if this is a connection to a legacy iq. Legacy IQ's require a passcode.
            //if so, check for an existing passcode. If one does not exist, ask user to
            //enter one.
            if(deviceManager.device().doesRequirePasscode())
            {
                writeLegacyPasscode();
            }
            else
            {
                connectionSuccessful(event);
            }
        }


        //device was disconnected
        else if (event.status() == ConnectionEvent.Status.Disconnected || event.status() == ConnectionEvent.Status.Intentional_Disconnect)
        {
            Log.d(LogTag.CONNECTION, "  ");
            Log.d(LogTag.CONNECTION, "Connection State Change:");
            Log.d(LogTag.CONNECTION, "State: Diconnected");
            Log.d(LogTag.CONNECTION, "Status: Intentional");
            Log.d(LogTag.CONNECTION, "Address: " + address);
            Log.d(LogTag.CONNECTION, "  ");

            connectionHandle.close();
            connectionHandle = null;
            callbackHandle = null;

            //update the connection state of the currently connected device
            deviceManager.device().connectionStateChanged(Device.ConnectionStatus.DISCONNECTED);

            //set unit connected preference to false
            prefs.edit().putBoolean(Preferences.UNIT_CONNECTED, false)
                    .putBoolean(Preferences.UNINTENTIONAL_DISCONNECT, false).apply();

            activity.onUnitDisconnected(address, true);

            updateNotification(false, 0, false);
        }


        //Device lost connection
        else if (event.status() == ConnectionEvent.Status.Unintentional_Disconnect)
        {
            Log.d(LogTag.CONNECTION, "  ");
            Log.d(LogTag.CONNECTION, "Connection State Change:");
            Log.d(LogTag.CONNECTION, "State: Diconnected");
            Log.d(LogTag.CONNECTION, "Status: Unintentional");
            Log.d(LogTag.CONNECTION, "Address: " + address);
            Log.d(LogTag.CONNECTION, "  ");

            //update the connection state of the currently connected device
            deviceManager.device().connectionStateChanged(Device.ConnectionStatus.LOST_CONNECTION);

            //set unit connected preference to false
            prefs.edit().putBoolean(Preferences.UNIT_CONNECTED, false)
                    .putBoolean(Preferences.UNINTENTIONAL_DISCONNECT, true).apply();
            activity.onUnitDisconnected(address, false);

            updateNotification(false, 0, false);
        }
    }

    private void handleCharacteristicChanged(final ConnectionEvent event)
    {
        //start a new parsing thread
        new Thread(new UpdateRunnable(event)).start();
    }


    /*
        This should only be called after a passcode write event
     */
    private void handleCharacteristicRead(ConnectionEvent event)
    {
        int attemptCount = 0;

        if(event.status() == ConnectionEvent.Status.Success)
        {
            Log.i(LogTag.DEBUG, "check");
            if(writingPasscode)
            {
                Log.i(LogTag.DEBUG, "Read back response from unit");

                byte[] data = event.characteristic().getValue();
                int valueCheck = data[0] + data[1] + data[2] + data[3] + data[4];
                Log.d(LogTag.DEBUG, "ValueCheck: " + valueCheck);

                if(passcodeReadAttempts < 5)
                {
                    if (valueCheck == 0)
                    {
                        passcodeReadAttempts++;
                        try {
                            Thread.currentThread().sleep(1000);
                            BluetoothGattCharacteristic c = connectionHandle.getService(SERVICE).getCharacteristic(STATUS_BASIC);
                            connectionHandle.readCharacteristic(c);
                            return;
                        } catch (InterruptedException e) {
                        }
                    }
                    else
                    {
                        writingPasscode = false;
                        Log.i(LogTag.DEBUG, "Passcode good");
                        connectionSuccessful(event);
                    }
                }
                else
                {
                    //Bad Passcode
                    Log.i(LogTag.DEBUG, "passcode bad");
                    passcodeReadAttempts = 0;
                    writingPasscode = false;

                    connectionHandle.disconnect();

                    if (activity != null)
                        activity.onPasscodeWrong();

                }

            }

            if(forceRead)
            {
                //performed a forced data update
                forceRead = false;
                new Thread(new UpdateRunnable(event)).start();
            }
        }
    }

    private void handlePasscodeWrite(ConnectionEvent event)
    {
        if(event.status() == ConnectionEvent.Status.Success)
        {
            Log.i(LogTag.DEBUG, "passcode write succeeded");
            BluetoothGattCharacteristic c = connectionHandle.getService(SERVICE).getCharacteristic(STATUS_BASIC);
            writingPasscode = true;
            connectionHandle.readCharacteristic(c);
        }
        else
        {
            Log.i(LogTag.DEBUG, "Passcode write failed");
        }
    }


    private void handleCharacteristicWrite(ConnectionEvent event)
    {
        if (event.status() == ConnectionEvent.Status.Success)
        {
            hapticFeedback();
            successSound();

            if (activity != null)
                activity.onWriteSuccess();
        }
        else
        {
            if (activity != null)
                activity.onWriteFailed();
        }
    }

    public void writeLegacyPasscode()
    {
        BluetoothGattCharacteristic c = connectionHandle.getService(SERVICE).getCharacteristic(PASSCODE);

        if(c == null)
        {
            //TODO handle this
            Log.e(LogTag.CONNECTION, "Passcode characteristic null");
            return;
        }

        byte[] data = ByteBuffer.allocate(2).putShort(deviceManager.device().getPasscode()).array();

        Log.i(LogTag.DEBUG, "writing passcode");
        c.setValue(data);
        connectionHandle.writeCharacteristic(c);
    }


    public void writeConfigChange(int selector, int value)
    {
        if(!prefs.getBoolean(Preferences.UNIT_CONNECTED, false))
        {
            activity.onWriteFailed();
            return;
        }

        BluetoothGattCharacteristic c = connectionHandle.getService(SERVICE).getCharacteristic(CONFIG_BASIC);

        if(c == null)
        {
            if(activity !=  null)
                activity.onWriteFailed();
            return;
        }

        short sVal = (short) value;
        if((selector == ConfigBuilder.PIT_SET ||
                selector == ConfigBuilder.DELAY_PIT_SET ||
                selector == ConfigBuilder.PROBE2_PIT_SET ||
                selector == ConfigBuilder.PROBE3_PIT_SET) &&
                sVal != 0)
        {
            sVal -= TEMPERATURE_OFFSET;
        }

        ByteBuffer bytes = ByteBuffer.allocate(2).putShort(sVal);
        byte[] array = bytes.array();

        byte[] data = new byte[3];
        data[0] = (byte) selector;
        data[1] = array[0];
        data[2] = array[1];

        c.setValue(data);
        connectionHandle.writeCharacteristic(c);
    }




    private void successSound()
    {
        if(prefs.getBoolean(Preferences.SOUND, true))
        {
            //play sound
            Log.d("Sound", "Playing successs sound");
            MediaPlayer mp = MediaPlayer.create(this, R.raw.config_edit_success);
            mp.start();
        }
        else
        {
            Log.d("Sound", "Not playing successs sound");
        }
    }



    private void hapticFeedback()
    {
        //Check if app has user permission to use haptic feedback
        if(prefs.getBoolean(Preferences.HAPTIC_FEEDBACK, true))
            //check if phone has vibrator to use
            if(vibrator.hasVibrator())
                vibrator.vibrate(100);
    }


    private Runnable connectionTimeout = new Runnable()
    {
        @Override
        public void run()
        {
            connectionHandle = null;
            callbackHandle = null;

            if(activity != null)
                activity.onConnectionFailed();
        }
    };

    private class UpdateRunnable implements Runnable
    {
        private ConnectionEvent connectionEvent;

        public UpdateRunnable(ConnectionEvent e)
        {
            this.connectionEvent = e;
        }

        @Override
        public void run()
        {
            Log.d(LogTag.BLUETOOTH, "Data update from " + connectionEvent.gatt().getDevice().getAddress());

            UnitData unitData = DataParser.parseData(connectionEvent.characteristic().getValue(), prefs.getString(Preferences.TEMPERATURE_UNITS, "0").equals("1"));

            boolean graphDataUpdated = false;
            if(unitData.getTemperatureHash() != lastTemperatureHash)
            {
                int sessionId = prefs.getInt(Preferences.SESSION_ID, 0);
                dbHelper.insertData(connectionEvent.gatt().getDevice().getAddress(), unitData, sessionId);
                lastTemperatureHash = unitData.getTemperatureHash();
                graphDataUpdated = true;
            }

            exceptionManager.updateExceptionsList(unitData.getFlagValue());


            //notify other activities of data update
            if(activity != null)
                activity.onDataUpdate(unitData, graphDataUpdated);

            updateNotification(true, unitData.getProbe1Temp(), false);

            //keep track of last update time
            lastUpdate = System.currentTimeMillis();
            deviceManager.device().setUnitData(unitData);
        }
    }

    private void startAlarm()
    {
        /*
            This check is to see if the app is in the foreground. If it is in the background the service
            will not be bound to any other views. We do not want to sound an alarm when the app is
            in the foreground
        */
        if(!MyApplication.isActive())
        {
            alarmActive = true;
            exceptionManager.alarmSounded();

            Intent intent = new Intent(getApplicationContext(), AlarmReceiver.class);
            startActivity(intent);
        }
    }

    public void stopAlarm()
    {
        alarmActive = false;

        if(mp != null && mp.isPlaying())
            mp.stop();
    }
}
