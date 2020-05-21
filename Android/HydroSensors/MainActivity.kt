package ckapps.hydrosensor

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getReadings()

        val refresh: ImageButton = findViewById(R.id.status_refresh)
        refresh.setOnClickListener{ getReadings() }
    }

    private fun getReadings() {
        val txtPPM: TextView = findViewById(R.id.txt_ppm)
        val txtPH: TextView = findViewById(R.id.txt_ph)
        val txtUpdated: TextView = findViewById(R.id.txt_updated)


        val queue = Volley.newRequestQueue(this)
        val url = "http://<ip redacted>/view1.php"

        // Request a string response from the provided URL.
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            Response.Listener<String> { response ->


                Log.e("test", response)

                val fixed = response.substring(0, response.lastIndexOf("<br>")) // remove last <br> tag

                val sensors = fixed.split("<br>")
                Log.e("test", "" + sensors.size)

                val sensor = sensors[1].split(" ")
                txtUpdated.setText(sensor[1] + " " + sensor[2])
                txtPPM.setText(sensor[3])
                txtPH.setText(sensor[4])
            },
            Response.ErrorListener {
                Toast.makeText(this, "HTTP request failed", Toast.LENGTH_SHORT).show()
            Log.e("tag", it.message)
            })

        queue.add(stringRequest)
    }

}

