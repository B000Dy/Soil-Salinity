package com.scu.soilsalinity

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.scu.soilsalinity.model.TFLiteModelHelper
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private lateinit var tfliteHelper: TFLiteModelHelper
    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var REQUEST_CODE_FILE_CHOOSER = 100
    private var TAG = "MainActivity"
    private var currentImageData: String? = null
    private var currentPhotoPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupWebView()
        tfliteHelper = TFLiteModelHelper(this)
        Log.d(TAG, "Model input shape: ${tfliteHelper.inputShape.contentToString()}")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                // Cancel any existing callbacks
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                try {
                    // Create camera intent
                    var takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        // Ensure there's a camera activity to handle the intent
                        if (resolveActivity(packageManager) != null) {
                            // Create the File where the photo should go
                            var photoFile = try {
                                createImageFile()
                            } catch (ex: IOException) {
                                null
                            }
                            photoFile?.also {
                                var photoURI = FileProvider.getUriForFile(
                                    this@MainActivity,
                                    "${packageName}.fileprovider",
                                    it
                                )
                                putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                            }
                        }
                    }

                    // Create gallery intent
                    var contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    }

                    // Combine intents
                    var intentArray = if (takePictureIntent.resolveActivity(packageManager) != null) {
                        arrayOf(takePictureIntent)
                    } else {
                        arrayOf()
                    }

                    var chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
                        putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                        putExtra(Intent.EXTRA_TITLE, "Select Image Source")
                        putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
                    }

                    @Suppress("DEPRECATION")
                    startActivityForResult(chooserIntent, REQUEST_CODE_FILE_CHOOSER)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Cannot open file chooser", Toast.LENGTH_LONG).show()
                    filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = null
                    return false
                }

                return true
            }
        }

        webView.addJavascriptInterface(WebAppInterface(), "AndroidInterface")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page loaded: $url")

                when {
                    url?.endsWith("result.html") == true -> {
                        currentImageData?.let { imageData ->
                            injectPredictionData(imageData)
                        }
                    }
                    url?.endsWith("loading.html") == true -> {
                        //currentImageData?.let { imageData ->
                          //  startPrediction(imageData)
                        //}
                    }
                }
            }
        }
        webView.loadUrl("file:///android_asset/index.html")
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        var timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        var imageFileName = "JPEG_${timeStamp}_"
        var storageDir = getExternalFilesDir(null)
        var image = File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )
        currentPhotoPath = image.absolutePath
        return image
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_CODE_FILE_CHOOSER || this@MainActivity.filePathCallback == null) {
            return
        }

        var results: Array<Uri>? = null // Changed from var to var

        if (resultCode == RESULT_OK) {
            if (data == null || data.data == null) {
                // Handle camera capture
                currentPhotoPath?.let { path ->
                    results = arrayOf(Uri.parse("file://$path")) // Changed from Uri.fromFile()
                }
            } else {
                // Handle gallery selection
                data.data?.let { uri ->
                    results = arrayOf(uri)
                }
            }
        }

        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    private fun startPrediction(imageData: String) {
        Thread {
            try {
                Log.d(TAG, "Starting prediction process")
                var base64Data = if (imageData.contains(",")) imageData.split(",")[1] else imageData
                var imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ?: throw Exception("Failed to decode image")

                var inputData = preprocessImage(bitmap)
                Log.d(TAG, "Image preprocessed, running prediction...")

                var result = tfliteHelper.predict(inputData)
                Log.d(TAG, "Prediction completed: ${result.label} (${result.confidence}%)")

                runOnUiThread {
                    currentImageData = imageData
                    webView.loadUrl("file:///android_asset/result.html")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Prediction error", e)
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Prediction failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    webView.loadUrl("file:///android_asset/index.html")
                }
            }
        }.start()
    }

    private fun injectPredictionData(imageData: String) {
        try {
            var base64Data = if (imageData.contains(",")) imageData.split(",")[1] else imageData
            var imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
            var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: throw Exception("Failed to decode image")

            var inputData = preprocessImage(bitmap)
            var result = tfliteHelper.predict(inputData)

            var jsCode = """
                if (typeof receivePrediction === 'function') {
                    receivePrediction('${result.label}', ${result.confidence}, '$imageData');
                } else {
                    console.error('receivePrediction function not found');
                }
            """.trimIndent()

            webView.post {
                webView.evaluateJavascript(jsCode) { result ->
                    Log.d(TAG, "JavaScript evaluation result: $result")
                }
            }

            bitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject prediction data", e)
            runOnUiThread {
                webView.loadUrl("javascript:showError('Prediction failed: ${e.message}')")
                webView.loadUrl("file:///android_asset/index.html")
            }
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun predictImage(imageData: String) {
            Log.d(TAG, "Received predictImage call")
            runOnUiThread {
                currentImageData = imageData
                webView.loadUrl("file:///android_asset/loading.html")

                // Delay 7 seconds before starting prediction
                webView.postDelayed({
                    startPrediction(imageData)
                }, 7000) // 7000 ms = 7 seconds
            }
        }

        @JavascriptInterface
        fun cancelPrediction() {
            runOnUiThread {
                webView.loadUrl("file:///android_asset/index.html")
            }
        }

        @JavascriptInterface
        fun getPredictionDetails(label: String): String {
            return when (label.lowercase()) {
                "high" -> "This indicates high soil salinity which may require immediate attention."
                "medium" -> "Moderate salinity levels detected. Consider monitoring regularly."
                "low" -> "Low salinity levels detected. Soil condition appears good."
                else -> "No additional information available for this prediction."
            }
        }

        @JavascriptInterface
        fun returnToHome() {
            runOnUiThread {
                webView.loadUrl("file:///android_asset/index.html")
            }
        }
    }

    private fun preprocessImage(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        var inputShape = tfliteHelper.inputShape
        var width = inputShape[1]
        var height = inputShape[2]
        var channels = inputShape[3]

        var resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
        var pixels = IntArray(width * height)
        resizedBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var inputArray = Array(1) {
            Array(height) {
                Array(width) {
                    FloatArray(channels)
                }
            }
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                var pixel = pixels[y * width + x]
                inputArray[0][y][x][0] = ((pixel shr 16) and 0xFF) / 255.0f
                inputArray[0][y][x][1] = ((pixel shr 8) and 0xFF) / 255.0f
                inputArray[0][y][x][2] = (pixel and 0xFF) / 255.0f
            }
        }

        return inputArray
    }

    override fun onBackPressed() {
        if (webView.url?.endsWith("index.html") == true) {
            super.onBackPressed()
        } else {
            webView.loadUrl("file:///android_asset/index.html")
        }
    }

    override fun onDestroy() {
        tfliteHelper.close()
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        super.onDestroy()
    }
}