package com.happyplaces.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.startActivityForResult
import com.google.android.gms.location.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.happyplaces.R
import com.happyplaces.database.DatabaseHandler
import com.happyplaces.model.HappyPlaceModel
import com.happyplaces.utils.GetAddressFromLatLng
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_add_happy_place.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class AddHappyPlaceActivity : AppCompatActivity(), View.OnClickListener {

    private var cal = Calendar.getInstance()

    private lateinit var dateSetListener: DatePickerDialog.OnDateSetListener
    private var saveImageToInternalStorage : Uri ? = null
    private var mHappyPlaceDetails: HappyPlaceModel? = null
    private var mLongitude : Double = 0.00
    private var mLatitude : Double = 0.00

    private lateinit var mFusedLocationClient: FusedLocationProviderClient


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_happy_place)


        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Set up toolbar and navigation
        setSupportActionBar(toolbar_add_place)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar_add_place.setNavigationOnClickListener {
            onBackPressed()
        }

        // Initialize Places API
        if (!Places.isInitialized()) {
            Places.initialize(
                this@AddHappyPlaceActivity,
                resources.getString(R.string.google_maps_api_key)
            )
        }

        // If intent contains an extra with HappyPlaceModel data, set the activity to edit mode for update
        if (intent.hasExtra(MainActivity.EXTRA_PLACE_DETAILS)) {
            mHappyPlaceDetails =
                intent.getParcelableExtra(MainActivity.EXTRA_PLACE_DETAILS) as HappyPlaceModel?
        }

        // Set up date picker dialog listener and update the date view
        dateSetListener =
            DatePickerDialog.OnDateSetListener { view, year, monthOfYear, dayOfMonth ->
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, monthOfYear)
                cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                updateDateInView()
            }

        updateDateInView()
        // If in edit mode, prepopulate fields with existing data for update
        if (mHappyPlaceDetails != null) {
            supportActionBar?.title = "Edit Happy Place"

            et_title.setText(mHappyPlaceDetails!!.title)
            et_description.setText(mHappyPlaceDetails!!.description)
            et_date.setText(mHappyPlaceDetails!!.date)
            et_location.setText(mHappyPlaceDetails!!.location)
            mLatitude = mHappyPlaceDetails!!.latitude
            mLongitude = mHappyPlaceDetails!!.longitude

            saveImageToInternalStorage = Uri.parse(mHappyPlaceDetails!!.image)

            iv_place_image.setImageURI(saveImageToInternalStorage)

            btn_save.text = "UPDATE"
        }

        // Set click listeners for date, image, location, and save button
        et_date.setOnClickListener(this)
        tv_add_image.setOnClickListener(this)
        btn_save.setOnClickListener (this)
        et_location.setOnClickListener(this)
        tv_select_current_location.setOnClickListener(this)
    }

    // Handle clicks on various UI elements
    override fun onClick(v: View?) {
        when (v!!.id) {
            // Handle click on date edit text field
            R.id.et_date -> {
                DatePickerDialog(
                    this@AddHappyPlaceActivity,
                    dateSetListener,
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
                // Handle click on add image text view
            R.id.tv_add_image -> {
                val pictureDialog = AlertDialog.Builder(this)
                pictureDialog.setTitle("Select Action")
                val pictureDialogItems =
                    arrayOf("Select photo from gallery", "Capture photo from camera")
                pictureDialog.setItems(
                    pictureDialogItems
                ) { _, which ->
                    when (which) {
                        0 -> choosePhotoFromGallery()
                        1 -> takePhotoFromCamera()
                    }
                }
                pictureDialog.show()
            }

            R.id.btn_save -> {
                when{
                    et_title.text.isNullOrEmpty() -> {
                        Toast.makeText(this, "Please enter title", Toast.LENGTH_SHORT).show()
                    }
                    et_description.text.isNullOrEmpty() -> {
                        Toast.makeText(this, "Please enter description", Toast.LENGTH_SHORT)
                            .show()
                    }
                    saveImageToInternalStorage == null -> {
                        Toast.makeText(this, "Please add image", Toast.LENGTH_SHORT).show()
                    }

                    et_location.text.isNullOrEmpty() -> {
                        Toast.makeText(this, "Please select location", Toast.LENGTH_SHORT)
                            .show()
                    }

                    else -> {

                        val happyPlaceModel = HappyPlaceModel(
                            if (mHappyPlaceDetails == null) 0 else mHappyPlaceDetails!!.id,
                            et_title.text.toString(),
                            saveImageToInternalStorage.toString(),
                            et_description.text.toString(),
                            et_date.text.toString(),
                            et_location.text.toString(),
                            mLatitude,
                            mLongitude
                        )

                        // Here we initialize the database handler class.
                        val dbHandler = DatabaseHandler(this)

                        if (mHappyPlaceDetails == null) {
                            val addHappyPlace = dbHandler.addHappyPlace(happyPlaceModel)

                            if (addHappyPlace > 0) {
                                setResult(Activity.RESULT_OK);
                                finish()//finishing activity
                            }
                        } else {
                            val updateHappyPlace = dbHandler.updateHappyPlace(happyPlaceModel)

                            if (updateHappyPlace > 0) {
                                setResult(Activity.RESULT_OK);
                                finish()//finishing activity
                            }
                        }
                    }
                    }

                }

            R.id.et_location -> {
                try {
                    // These are the list of fields which we required is passed
                    val fields = listOf(
                        Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG,
                        Place.Field.ADDRESS
                    )
                    // Start the autocomplete intent with a unique request code.
                    val intent =
                        Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                            .build(this@AddHappyPlaceActivity)
                    startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            R.id.tv_select_current_location -> {

                if (!isLocationEnabled()) {
                    Toast.makeText(
                        this,
                        "Your location provider is turned off. Please turn it on.",
                        Toast.LENGTH_SHORT
                    ).show()

                    // This will redirect you to settings from where you need to turn on the location provider.
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivity(intent)
                } else {
                    // For Getting current location of user please have a look at below link for better understanding
                    // https://www.androdocs.com/kotlin/getting-current-location-latitude-longitude-in-android-using-kotlin.html
                    Dexter.withActivity(this)
                        .withPermissions(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        .withListener(object : MultiplePermissionsListener {
                            override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                                if (report!!.areAllPermissionsGranted()) {
                                    requestNewLocationData()
                                }
                            }

                            override fun onPermissionRationaleShouldBeShown(
                                permissions: MutableList<PermissionRequest>?,
                                token: PermissionToken?
                            ) {
                                showRationalDialogForPermissions()
                            }
                        }).onSameThread()
                        .check()
                }
            }

            }
        }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            // Check if the request code matches the Gallery request code
            if (requestCode == GALLERY) {
                if (data != null) {
                    // Get the URI of the selected image from the returned dat
                    val contentURI = data.data
                    try {
                        @Suppress("DEPRECATION")
                        // Get the selected image bitmap from the URI using the content resolver
                        val selectedImageBitmap =
                            MediaStore.Images.Media.getBitmap(this.contentResolver, contentURI)

                        // Save the selected image to internal storage and get its path
                         saveImageToInternalStorage = saveImageToInternalStorage(selectedImageBitmap)
                        Log.e("Saved Image : ", "Path :: $saveImageToInternalStorage")

                        // Display the selected image in the image view
                        iv_place_image!!.setImageBitmap(selectedImageBitmap)
                    } catch (e: IOException) {
                        e.printStackTrace()
                        Toast.makeText(this@AddHappyPlaceActivity, "Failed!", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            else if (requestCode == CAMERA) {

                // Get the thumbnail image bitmap from the data extras
                val thumbnail: Bitmap = data!!.extras!!.get("data") as Bitmap // Bitmap from camera

                // Save the thumbnail image to internal storage and get its path
                 saveImageToInternalStorage = saveImageToInternalStorage(thumbnail)
                Log.e("Saved Image : ", "Path :: $saveImageToInternalStorage")

                // Display the thumbnail image in the image view
                iv_place_image!!.setImageBitmap(thumbnail) // Set to the imageView.
            }

            else if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE) {

                val place: Place = Autocomplete.getPlaceFromIntent(data!!)

                // Set the location text and latitude/longitude values based on the retrieved Place object
                et_location.setText(place.address)
                mLatitude = place.latLng!!.latitude
                mLongitude = place.latLng!!.longitude
            }

        } else if (resultCode == Activity.RESULT_CANCELED) {
            Log.e("Cancelled", "Cancelled")
        }
    }

    private fun updateDateInView() {
        val myFormat = "dd.MM.yyyy" // mention the format you need
        val sdf = SimpleDateFormat(myFormat, Locale.getDefault()) // A date format
        et_date.setText(sdf.format(cal.time).toString()) // A selected date using format which we have used is set to the UI.
    }

    private fun choosePhotoFromGallery() {
        // Request permission to read from and write to external storage using the Dexter library
        Dexter.withContext(this)
            .withPermissions(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            .withListener(object : MultiplePermissionsListener {
                // Handle the result of the permission request
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {

                    // Check if all permissions are granted
                    if (report!!.areAllPermissionsGranted()) {
                        // If all permissions are granted, create an intent to pick an image from the gallery
                        val galleryIntent = Intent(
                            Intent.ACTION_PICK,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        )
                        // Launch the gallery intent and specify the request code
                        startActivityForResult(galleryIntent, GALLERY)
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    showRationalDialogForPermissions()
                }
            }).onSameThread()
            .check()
    }

    private fun takePhotoFromCamera() {
        Dexter.withActivity(this)
            .withPermissions(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    // Here after all the permission are granted launch the CAMERA to capture an image.
                    if (report!!.areAllPermissionsGranted()) {
                        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        startActivityForResult(intent, CAMERA)
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    showRationalDialogForPermissions()
                }
            }).onSameThread()
            .check()
    }

    private fun showRationalDialogForPermissions() {
        // Create an alert dialog to inform the user that the required permissions are turned off
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton("GO TO SETTINGS" // Add a button to open the app settings
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) // Create an intent to open the app settings
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent) // Launch the intent to open the app settings
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog,   // Add a cancel button to dismiss the dialog
                                           _ ->
                dialog.dismiss()
            }.show()
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap): Uri {

        val wrapper = ContextWrapper(applicationContext)

        var file = wrapper.getDir(IMAGE_DIRECTORY, Context.MODE_PRIVATE)
        file = File(file, "${UUID.randomUUID()}.jpg")

        try {
            val stream: OutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            stream.flush()
            stream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return Uri.parse(file.absolutePath)
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 0
        mLocationRequest.fastestInterval = 0
        mLocationRequest.numUpdates = 1

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            mLatitude = mLastLocation.latitude
            Log.e("Current Latitude", "$mLatitude")
            mLongitude = mLastLocation.longitude
            Log.e("Current Longitude", "$mLongitude")

            val addressTask =
                GetAddressFromLatLng(this@AddHappyPlaceActivity, mLatitude, mLongitude)

            addressTask.setAddressListener(object :
                GetAddressFromLatLng.AddressListener {
                override fun onAddressFound(address: String?) {
                    Log.e("Address ::", "" + address)
                    et_location.setText(address)
                }

                override fun onError() {
                    Log.e("Get Address ::", "Something is wrong...")
                }
            })

            addressTask.getAddress()
        }
    }

    companion object {
        private const val GALLERY = 1
        private const val CAMERA = 2
        private const val IMAGE_DIRECTORY = "HappyPlacesImages"
        private const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 3
    }
}


