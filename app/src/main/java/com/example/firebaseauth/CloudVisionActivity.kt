package com.example.firebaseauth

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.gson.*
import kotlinx.android.synthetic.main.activity_cloud_vison.*
import kotlinx.android.synthetic.main.activity_main.btnSelectImage
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.*

class CloudVisionActivity: AppCompatActivity(){
    lateinit var imageUrl: URI
    private lateinit var functions: FirebaseFunctions
    
    private final val ELEMENT_COLOR: String = "color"
    private final val ELEMENT_SCORE: String = "score"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloud_vison)

        container.setTransitionVisibility(View.INVISIBLE)


        functions = FirebaseFunctions.getInstance()


        btnSelectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, 0)

            btnSelectImage.visibility = View.INVISIBLE
        }

        dColor5.setOnClickListener {
            container.visibility = View.INVISIBLE
            btnSelectImage.visibility = View.VISIBLE
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

        if (requestCode == 0 && resultCode == RESULT_OK && data != null) {
            // proceed and check what the select image was ...
            Log.d("RegisterActivity", "Photo was selected")

            val selectedPhotoUri = data.data

            /* 画像を内部ストレージから取得 */
            var bitmap = MediaStore.Images.Media.getBitmap(contentResolver, selectedPhotoUri)

            /* 画像を内部ストレージから取得 */
            bitmap = scaleBitmapDown(bitmap, 640)

            /* サーバーに画像を送信するために，ビットマップをエンコードする */
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
            val imageBytes: ByteArray = byteArrayOutputStream.toByteArray()
            val base64encoded = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            // Add image to request
            val image = JsonObject()
            image.add("content", JsonPrimitive(base64encoded))

            // Create json request to cloud vision
            val request = JsonObject()
            request.add("image", image)

            //Add features to the request
            val feature = JsonObject()
            feature.add("maxResults", JsonPrimitive(10))
            feature.add("type", JsonPrimitive("IMAGE_PROPERTIES"))

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

                        // asJsonObject = element を key指定で取るやつ
                        val annotation = task.result!!.asJsonArray[0].asJsonObject["imagePropertiesAnnotation"].asJsonObject["dominantColors"]
                        val colors: JsonElement = annotation.asJsonObject["colors"]

                        var num = 0

                        var scoreMap: MutableMap<Int, Float> = hashMapOf()
                        var colorHexMap: MutableMap<Int, String> = hashMapOf()
                        var colorValueMap: MutableMap<Int, Int> = hashMapOf()

                        /* 上位 5位以内 の色だけ抽出し，UIに表示 (i を外で定義してやってたけど，こんなスマートな書き方があるんだ...)*/
                        for ((i, color) in colors.asJsonArray.withIndex()) {
                            val RGBElement: JsonElement = color.asJsonObject[ELEMENT_COLOR]

                            val red: Int = RGBElement.asJsonObject["red"].asInt
                            val green: Int = RGBElement.asJsonObject["green"].asInt
                            val blue: Int = RGBElement.asJsonObject["blue"].asInt

                            Log.d("test", "red: $red")
                            Log.d("test", "green: $green")
                            Log.d("test", "blue: $blue")

                            // key が intの場合，scoreMap.put(num, dominantScore) よりこちらのほうがいいらしい
                            scoreMap[i] = color.asJsonObject[ELEMENT_SCORE].asFloat
                            colorHexMap[i] = generateColorHexString(red, green, blue)
                            colorValueMap[i] = generateColorValue(red, green, blue)
                        }


                        // リスト型にして降順に並び変え，再びmap型に戻す
                        val sortedScoreMap = scoreMap.toList().sortedByDescending { it.second }.toMap()
//                        sortedScoreList.toMap()

                        sortedScoreMap.forEach { (k, v) -> Log.d("test","{ $k, $v") }
                        Log.d("test", "sortedScoreMap: $sortedScoreMap")

                        distributeParams(sortedScoreMap, colorHexMap, colorValueMap)
                        container.visibility = View.VISIBLE

                        Log.d("annotation", annotation.toString())
                    } else {
                        Log.d("cloud", "failure: " + task.exception.toString())
                    }
                }
        }
    }

    private fun changeLayoutParams(btn: Button, colorValue: Int, colorHexString: String ,dominantScore: Float) {
        btn.setBackgroundColor(colorValue)

        var viewParams: LinearLayout.LayoutParams = btn.layoutParams as LinearLayout.LayoutParams

        viewParams.weight = dominantScore

        btn.layoutParams = viewParams

        var displayScore = Math.floor(((dominantScore * 100 * 10).toDouble()))/10

        btn.text = "$colorHexString   $displayScore %"
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

    private fun distributeParams(sortedScore: Map<Int, Float>, colorHexMap: Map<Int, String>, colorValueMap: Map<Int, Int>) {
        val scoreList = sortedScore.toList()

        // map: 配列の引数から，要素を取り出すのが苦手...?
        // list: 配列として取るのが得意
        Log.d("test", "scoreList: $scoreList")
        for (i in 0..9) {
            Log.d("test", "scoreList: $i")
            val idx: Int = scoreList[i].first

            /* スマートじゃなけれど，変数名に番号が割り当てられているので，やむを得なく... */
            when (i) {
                0 -> changeLayoutParams(dColor0, colorValueMap.getValue(idx), colorHexMap.getValue(idx), sortedScore.getValue(idx))
                1 -> changeLayoutParams(dColor1, colorValueMap.getValue(idx), colorHexMap.getValue(idx), sortedScore.getValue(idx))
                2 -> changeLayoutParams(dColor2, colorValueMap.getValue(idx), colorHexMap.getValue(idx), sortedScore.getValue(idx))
                3 -> changeLayoutParams(dColor3, colorValueMap.getValue(idx), colorHexMap.getValue(idx), sortedScore.getValue(idx))
                4 -> changeLayoutParams(dColor4, colorValueMap.getValue(idx), colorHexMap.getValue(idx), sortedScore.getValue(idx))
                5 -> changeLayoutParams(dColor5, colorValueMap.getValue(idx), colorHexMap.getValue(idx), sortedScore.getValue(idx))
            }

        }

    }

}

private fun generateColorHexString(red: Int, green: Int, blue: Int): String {
    var colorHexString: StringBuffer = StringBuffer()
    colorHexString.append("#")
    colorHexString.append(Integer.toHexString(red))
    colorHexString.append(Integer.toHexString(green))
    colorHexString.append(Integer.toHexString(blue))

    Log.d("test", "colorHex: $colorHexString")


    return colorHexString.toString()
}

private fun generateColorValue(red: Int, green: Int, blue: Int): Int {
    var HSV: FloatArray = FloatArray(3)
    Color.RGBToHSV(red, green, blue, HSV)

    Log.d("test", "colorValue: ${Color.HSVToColor(HSV)}")
    return Color.HSVToColor(HSV)
}