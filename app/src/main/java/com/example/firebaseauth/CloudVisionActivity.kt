package com.example.firebaseauth

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.Image
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import com.example.firebaseauth.view.ExtractedColorFragment
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.ktx.Firebase
import com.google.gson.*
import kotlinx.android.synthetic.main.activity_main.*
import java.io.ByteArrayOutputStream
import java.net.URI

class CloudVisionActivity: AppCompatActivity(){
    lateinit var imageUrl: URI
    private lateinit var functions: FirebaseFunctions


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloud_vison)

        functions = FirebaseFunctions.getInstance()


        btnSelectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, 0)

//            var bitmap: Bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUrl)
        }

        verifyUserIsLoggedIn()
    }

    private fun verifyUserIsLoggedIn() {

        val uid = FirebaseAuth.getInstance().uid
        if  (uid == null) {
            val intent = Intent(this, RegisterActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK.or(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }



    // 三つ点押した際に出るやつ
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_new_message -> {
                val intent = Intent(this, NewMessageActivity::class.java)
                startActivity(intent)
            }

            R.id.menu_sign_out -> {
                FirebaseAuth.getInstance().signOut()
                val intent = Intent(this, RegisterActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK.or(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }

        return super.onOptionsItemSelected(item)
    }

    // これが三つ点ボタンのリスナーになる
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.nav_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 0 && resultCode == Activity.RESULT_OK && data != null) {
            // proceed and check what the select image was ...
            Log.d("RegisterActivity", "Photo was selected")

            val selectedPhotoUri = data.data

            var bitmap = MediaStore.Images.Media.getBitmap(contentResolver, selectedPhotoUri)
            bitmap = scaleBitmapDown(bitmap, 640)

            val bitmapDrawable = BitmapDrawable(bitmap)
//          btnSelectPhot.setBackground(bitmapDrawable)

            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
            val imageBytes: ByteArray = byteArrayOutputStream.toByteArray()
            val base64encoded = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            // Create json request to cloud vision
            val request = JsonObject()

            // Add image to request
            val image = JsonObject()

            image.add("content", JsonPrimitive(base64encoded))
            request.add("image", image)

            //Add features to the request
            val feature = JsonObject()
            feature.add("type", JsonPrimitive("TEXT_DETECTION"))

            // Alternatively, for DOCUMENT_TEXT_DETECTION:
            // feature.add("type", JsonPrimitive("DOCUMENT_TEXT_DETECTION"))
            val features = JsonArray()
            features.add(feature)
            request.add("features", features)

            val imageContext = JsonObject()
            val languageHints = JsonArray()

            languageHints.add("en")
            imageContext.add("languageHints", languageHints)
            request.add("imageContext", imageContext)

            annotateImage(request.toString())
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("cloud", "success")
                        Log.d("cloud", annotation["text"].asString)

                        val annotation = task.result!!.asJsonArray[0].asJsonObject["fullTextAnnotation"].asJsonObject
                        Toast.makeText(this, annotation["text"].asString, Toast.LENGTH_LONG).show()

                        val fragment: ExtractedColorFragment = ExtractedColorFragment()
                        supportFragmentManager.beginTransaction()
                            .setTransition(fragment)
                            .addToBackStack(null)
                            .commit();

                        supportFragmentManager.beginTransaction()
                        replaceFragment(fragment)
                    } else {
                        Log.d("cloud", "failure: " + task.exception.toString())
                    }
                }

        }
    }

    private fun replaceFragment(fragment: ExtractedColorFragment) {

    }

    private fun annotateImage(requestJson: String): Task<JsonElement> {
        return functions
            .getHttpsCallable("annotateImage")
            .call(requestJson)
            .continueWith { task ->
                // This continuation runs on either success or failure, but if the task
                // has failed then result will throw an Exception which will be
                // propagated down.
                val result = task.result?.data
                JsonParser.parseString(Gson().toJson(result))
            }
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var resizedWidth = maxDimension
        var resizedHeight = maxDimension
        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension
            resizedWidth =
                (resizedHeight * originalWidth.toFloat() / originalHeight.toFloat()).toInt()
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension
            resizedHeight =
                (resizedWidth * originalHeight.toFloat() / originalWidth.toFloat()).toInt()
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension
            resizedWidth = maxDimension
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false)
    }

}