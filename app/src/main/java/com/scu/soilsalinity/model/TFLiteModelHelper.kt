package com.scu.soilsalinity.model

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class PredictionResult(
    val label: String,
    val confidence: Float
)

class TFLiteModelHelper(context: Context) {
    private val interpreter: Interpreter
    val inputShape: IntArray
    private val outputShape: IntArray

    init {
        val modelFile = loadModelFile(context)
        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }
        interpreter = Interpreter(modelFile, options)
        inputShape = interpreter.getInputTensor(0).shape()
        outputShape = interpreter.getOutputTensor(0).shape()
        Log.d("TFLiteModelHelper", "Model loaded. Input shape: ${inputShape.contentToString()}")
    }

    fun predict(inputData: Array<Array<Array<FloatArray>>>): PredictionResult {
    // Create output buffer for 4 classes
    val output = Array(1) { FloatArray(4) }
    val labels = listOf("12K", "4K", "8K", "Control")

    try {
        // Run inference
        interpreter.run(inputData, output)

        // Find the index with the highest probability
        var maxIndex = 0
        var maxConfidence = output[0][0]
        for (i in 1 until output[0].size) {
            if (output[0][i] > maxConfidence) {
                maxConfidence = output[0][i]
                maxIndex = i
            }
        }

        // Map index to label
        val label = labels[maxIndex]
        return PredictionResult(label, maxConfidence * 100)
    } catch (e: Exception) {
        Log.e("TFLite", "Inference error", e)
        throw e
    }



}

    private fun loadModelFile(context: Context): MappedByteBuffer {
        return context.assets.openFd("model.tflite").use { fd ->
            FileInputStream(fd.fileDescriptor).use { fis ->
                fis.channel.use { channel ->
                    channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        fd.startOffset,
                        fd.declaredLength
                    )
                }
            }
        }
    }

    fun close() {
        interpreter.close()
    }
}