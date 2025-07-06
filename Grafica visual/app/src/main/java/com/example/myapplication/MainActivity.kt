package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
// ELIMINAR ESTA LÍNEA -> import com.github.dhaval2404.imagepicker.ImagePicker // ¡BORRAR ESTA LÍNEA!
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var btnSelectImage: Button
    private var objectDetector: ObjectDetector? = null

    // Usamos el nuevo Activity Result API para seleccionar imágenes de la galería
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            detectObjectsInImage(uri)
        } else {
            Toast.makeText(this, "Selección de imagen cancelada", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        btnSelectImage = findViewById(R.id.btnSelectImage)

        // Inicializar el detector de objetos
        setupObjectDetector()

        btnSelectImage.setOnClickListener {
            // Lanza la actividad para obtener contenido (seleccionar imagen de galería)
            pickImageLauncher.launch("image/*") // Solicita cualquier tipo de imagen
        }
    }

    private fun setupObjectDetector() {
        val modelName = "lite2-detection-metadata.tflite" // Asegúrate que este sea el nombre de tu archivo .tflite

        try {
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setMaxResults(5) // Número máximo de detecciones a mostrar
                .setScoreThreshold(0.5f) // Confianza mínima para mostrar una detección
                // .setNumThreads(4) // Opcional: número de hilos para inferencia
                // .setUseGpu() // Opcional: para usar la GPU si está disponible (requiere la dependencia tensorflow-lite-gpu)
                .build()
            objectDetector = ObjectDetector.createFromFileAndOptions(this, modelName, options)
            Log.d("MainActivity", "Modelo cargado exitosamente: $modelName")
        } catch (e: IOException) {
            Log.e("MainActivity", "Error al cargar el modelo TFLite: ${e.message}")
            Toast.makeText(this, "Error al cargar el modelo de detección", Toast.LENGTH_LONG).show()
        }
    }

    private fun detectObjectsInImage(imageUri: Uri) {
        if (objectDetector == null) {
            Toast.makeText(this, "Detector de objetos no inicializado", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Cargar la imagen desde la URI a un Bitmap
            val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(imageUri))
            if (bitmap == null) {
                Toast.makeText(this, "No se pudo cargar la imagen", Toast.LENGTH_SHORT).show()
                return
            }

            // Convertir Bitmap a TensorImage (formato requerido por el modelo)
            // La Task Library usa los metadatos para el pre-procesamiento (ej. resize)
            val tensorImage = TensorImage.fromBitmap(bitmap)

            // Realizar la detección
            val results = objectDetector?.detect(tensorImage)

            // Mostrar la imagen original
            imageView.setImageBitmap(bitmap)

            // Dibujar los resultados de la detección en la imagen
            results?.let { detections ->
                val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutableBitmap)
                val paint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 5f
                    textSize = 40f
                }

                for (detection in detections) {
                    val boundingBox = detection.boundingBox
                    val categories = detection.categories

                    if (categories.isNotEmpty()) {
                        val category = categories[0] // Tomar la categoría con mayor puntuación
                        val label = category.label
                        val score = category.score * 100 // Convertir a porcentaje

                        // Colores aleatorios para las cajas
                        val randomColor = Color.rgb(
                            (0..255).random(),
                            (0..255).random(),
                            (0..255).random()
                        )
                        paint.color = randomColor

                        // Dibujar el cuadro delimitador
                        canvas.drawRect(boundingBox, paint)

                        // Dibujar la etiqueta
                        val labelText = "$label (${"%.2f".format(score)}%)"
                        paint.style = Paint.Style.FILL
                        canvas.drawText(labelText, boundingBox.left, boundingBox.top - 10, paint)
                        paint.style = Paint.Style.STROKE // Volver a estilo de trazo para futuras cajas

                        Log.d("MainActivity", "Detectado: $labelText en ${boundingBox.toShortString()}")
                    }
                }
                imageView.setImageBitmap(mutableBitmap) // Actualizar el ImageView con las detecciones
            } ?: run {
                Toast.makeText(this, "No se detectaron objetos", Toast.LENGTH_SHORT).show()
            }

        } catch (e: IOException) {
            Log.e("MainActivity", "Error al procesar la imagen: ${e.message}")
            Toast.makeText(this, "Error al procesar la imagen", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Ocurrió un error inesperado: ${e.message}")
            Toast.makeText(this, "Ocurrió un error inesperado", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cierra el detector cuando la actividad se destruye para liberar recursos
        objectDetector?.close()
    }
}