package com.lizardlens.core.inference

import android.content.Context
import android.graphics.Bitmap
import com.lizardlens.core.logging.AppLogger
import com.lizardlens.core.model.Detection
import com.lizardlens.core.model.DetectionResult
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

class TfliteInferenceEngine private constructor(
    private val context: Context?,
    @Volatile private var interpreter: Interpreter?,
    @Volatile private var _config: InferenceConfig
) : InferenceEngine {

    private val isMock = interpreter == null

    override val activeDelegate: InferenceConfig.Delegate
        get() = _config.delegate

    override fun detect(bitmap: Bitmap): DetectionResult {
        return if (isMock) {
            detectMock(bitmap)
        } else {
            detectReal(bitmap)
        }
    }

    private fun detectReal(bitmap: Bitmap): DetectionResult {
        val startTime = System.currentTimeMillis()

        val inputTensor = Preprocessor.preprocessNCHW(bitmap, _config.inputSize)

        val inputBuffer = ByteBuffer.allocateDirect(inputTensor.size * 4).apply {
            order(ByteOrder.nativeOrder())
            inputTensor.forEach { putFloat(it) }
        }

        val currentInterpreter = interpreter!!
        val outputDetails = currentInterpreter.getOutputTensor(0)
        val outputShape = outputDetails.shape()
        val outputSize = outputShape.fold(1) { acc, i -> acc * i }
        val outputBuffer = ByteBuffer.allocateDirect(outputSize * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        currentInterpreter.run(inputBuffer, outputBuffer)

        outputBuffer.rewind()
        val rawOutput = FloatArray(outputSize)
        for (i in rawOutput.indices) {
            rawOutput[i] = outputBuffer.float
        }

        val inferenceTimeMs = System.currentTimeMillis() - startTime

        val decoded = NmsProcessor.decodeOutput(rawOutput, outputShape, _config.inputSize)
        return NmsProcessor.postprocess(decoded, outputShape, _config, inferenceTimeMs)
    }

    private fun detectMock(bitmap: Bitmap): DetectionResult {
        val startTime = System.currentTimeMillis()

        val numDetections = Random.nextInt(0, 4)
        val detections = mutableListOf<Detection>()

        for (i in 0 until numDetections) {
            val cx = Random.nextFloat() * bitmap.width
            val cy = Random.nextFloat() * bitmap.height
            val w = Random.nextFloat() * 100f + 20f
            val h = Random.nextFloat() * 100f + 20f

            val x1 = (cx - w / 2).coerceIn(0f, bitmap.width.toFloat())
            val y1 = (cy - h / 2).coerceIn(0f, bitmap.height.toFloat())
            val x2 = (cx + w / 2).coerceIn(0f, bitmap.width.toFloat())
            val y2 = (cy + h / 2).coerceIn(0f, bitmap.height.toFloat())

            val confidence = Random.nextFloat() * 0.5f + 0.5f

            detections.add(
                Detection(
                    boundingBox = com.lizardlens.core.model.BoundingBox(x1, y1, x2, y2),
                    confidence = confidence,
                    label = "Lizard"
                )
            )
        }

        val inferenceTimeMs = System.currentTimeMillis() - startTime

        return DetectionResult(
            detections = detections,
            inferenceTimeMs = inferenceTimeMs
        )
    }

    override fun switchDelegate(delegate: InferenceConfig.Delegate) {
        if (_config.delegate == delegate) return
        if (isMock) {
            _config = _config.copy(delegate = delegate)
            AppLogger.i("Mock engine delegate switched to $delegate")
            return
        }

        AppLogger.i("Switching delegate from ${_config.delegate} to $delegate")
        val oldInterpreter = interpreter
        interpreter = null

        try {
            val newConfig = _config.copy(delegate = delegate)
            val options = buildInterpreterOptions(delegate)
            val newInterpreter = loadModel(context!!, options)
            interpreter = newInterpreter
            _config = newConfig
            AppLogger.i("Delegate switched to $delegate successfully")
        } catch (e: Exception) {
            AppLogger.e(e, "Failed to switch delegate to $delegate, falling back to CPU")
            try {
                val fallbackOptions = buildInterpreterOptions(InferenceConfig.Delegate.CPU)
                val fallbackInterpreter = loadModel(context!!, fallbackOptions)
                interpreter = fallbackInterpreter
                _config = _config.copy(delegate = InferenceConfig.Delegate.CPU)
            } catch (e2: Exception) {
                AppLogger.e(e2, "Failed to create fallback CPU interpreter")
            }
        } finally {
            oldInterpreter?.close()
        }
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }

    private fun buildInterpreterOptions(delegate: InferenceConfig.Delegate): Interpreter.Options {
        return Interpreter.Options().apply {
            setNumThreads(4)

            if (delegate == InferenceConfig.Delegate.GPU ||
                delegate == InferenceConfig.Delegate.AUTO
            ) {
                try {
                    val gpuDelegate = Class.forName("org.tensorflow.lite.gpu.GpuDelegate")
                        .getConstructor()
                        .newInstance()
                    addDelegate(gpuDelegate as org.tensorflow.lite.Delegate)
                    AppLogger.i("GPU delegate attached")
                } catch (e: Exception) {
                    AppLogger.w("GPU delegate unavailable, falling back to CPU: ${e.message}")
                }
            }
        }
    }

    companion object {
        private const val MODEL_FILENAME = "yolov8n_lizard.tflite"

        fun create(
            context: Context,
            config: InferenceConfig = InferenceConfig()
        ): TfliteInferenceEngine {
            return try {
                var actualDelegate = InferenceConfig.Delegate.CPU

                val options = Interpreter.Options().apply {
                    setNumThreads(4)

                    if (config.delegate == InferenceConfig.Delegate.GPU ||
                        config.delegate == InferenceConfig.Delegate.AUTO
                    ) {
                        try {
                            val gpuDelegate = Class.forName("org.tensorflow.lite.gpu.GpuDelegate")
                                .getConstructor()
                                .newInstance()
                            addDelegate(gpuDelegate as org.tensorflow.lite.Delegate)
                            actualDelegate = InferenceConfig.Delegate.GPU
                            AppLogger.i("GPU delegate attached")
                        } catch (e: Exception) {
                            AppLogger.w("GPU delegate unavailable, falling back to CPU: ${e.message}")
                        }
                    }
                }

                val interpreter = loadModel(context, options)

                if (interpreter != null) {
                    val inputShape = interpreter.getInputTensor(0).shape()
                    val outputShape = interpreter.getOutputTensor(0).shape()
                    AppLogger.i("Model loaded: input=${inputShape.contentToString()}, output=${outputShape.contentToString()}")

                    require(inputShape.size == 4 && inputShape[1] == 3) {
                        "Expected NCHW input [1, 3, H, W], got ${inputShape.contentToString()}"
                    }
                    require(outputShape.size == 3 && outputShape[1] == 5) {
                        "Expected 5 output features (cx,cy,w,h,conf), got shape ${outputShape.contentToString()}"
                    }
                }

                TfliteInferenceEngine(context, interpreter, config)
            } catch (e: Exception) {
                AppLogger.e(e, "Failed to create inference engine, falling back to mock")
                TfliteInferenceEngine(null, null, config)
            }
        }

        private fun loadModel(context: Context, options: Interpreter.Options): Interpreter? {
            return try {
                val modelFile = File(context.cacheDir, MODEL_FILENAME)
                if (!modelFile.exists()) {
                    context.assets.open(MODEL_FILENAME).use { input ->
                        modelFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                Interpreter(modelFile, options)
            } catch (e: Exception) {
                AppLogger.w("Model asset '$MODEL_FILENAME' not found: ${e.message}")
                null
            }
        }

        fun createMock(config: InferenceConfig = InferenceConfig()): TfliteInferenceEngine {
            return TfliteInferenceEngine(null, null, config)
        }
    }
}
