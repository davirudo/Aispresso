package com.bangkit.aispresso.view.camera.leafprocessing

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.ThumbnailUtils
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.bangkit.aispresso.R
import com.bangkit.aispresso.data.model.history.coffe.ClassifyCoffeModel
import com.bangkit.aispresso.data.sqlite.ClassifyHelper
import com.bangkit.aispresso.data.sqlite.ClassifybaseRegsiter
import com.bangkit.aispresso.data.utils.Helper
import com.bangkit.aispresso.databinding.ActivityCoffeClasifyBinding
import com.bangkit.aispresso.databinding.ActivityLeafBinding
import com.bangkit.aispresso.ml.Mlkopi
import com.bangkit.aispresso.ml.Modeltanaman
import com.bangkit.aispresso.view.dashboard.DashboardActivity
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LeafActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var binding : ActivityLeafBinding

    var imageSize = 224

    private lateinit var classifyHelper: ClassifyHelper
    private var imageView: ImageView? = null

    private val RESULT_LOAD_IMAGE = 123
    private val REQUEST_CODE_GALLERY = 999


    private var isEdit = false
    private var classify: ClassifyCoffeModel? = null
    private var position: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeafBinding.inflate(layoutInflater)
        setContentView(binding.root)

        classifyHelper = ClassifyHelper.getInstance(applicationContext)
        classifyHelper.open()

        classify = intent.getParcelableExtra(Helper.EXTRA_REGISTRATION)
        if (classify != null){
            position = intent.getIntExtra(Helper.EXTRA_POSITION, 0)
            isEdit = true
        } else {
            classify = ClassifyCoffeModel()
        }

        val actionBarTitle: String
        val btnTitle: String

        if (isEdit){
            actionBarTitle = "Ubah"
            btnTitle = "Update"
            classify?.let { it ->
                binding.result.text = it.classified
                binding.confidence.text = it.considers

                val registerImage: ByteArray? = it.image
                val bitmap =
                    registerImage?.let { it1 -> BitmapFactory.decodeByteArray(registerImage, 0, it1.size) }
                binding.imageView.setImageBitmap(bitmap)
            }!!
        } else {
            actionBarTitle = "Tambah"
            btnTitle = "Simpan"
        }


        actionBar?.title = actionBarTitle
        binding.save.text = btnTitle
        binding.save.setOnClickListener(this)

        imageView = binding.imageView

        imageView!!.setOnClickListener {

            ActivityCompat.requestPermissions(
                this@LeafActivity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE

                ),
                REQUEST_CODE_GALLERY
            )
        }

        binding.result.setOnEditorActionListener { v, actionId, event ->
            classify?.let { it ->
                it.classified = v.text.toString()
            }
            false
        }

        binding.confidence.setOnEditorActionListener { v, actionId, event ->
            classify?.let { it ->
                it.considers = v.text.toString()
            }
            false
        }

        binding.ivBack.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }

        binding.button.setOnClickListener{
            // Launch camera if we have permission
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(cameraIntent, 1)
            } else {
                //Request camera permission if we don't have it.
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
            }
        }
    }

    private fun showAlertDialog(type: Int) {
        val isDialogClose = type == Helper.ALERT_DIALOG_CLOSE
        val dialogTitle: String
        val dialogMessage: String
        if (isDialogClose) {
            dialogTitle = "Batal"
            dialogMessage = "Apakah anda ingin membatalkan perubahan pada form?"
        } else {
            dialogMessage = "Apakah anda yakin ingin menghapus item ini?"
            dialogTitle = "Hapus Register"
        }
        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setTitle(dialogTitle)
        alertDialogBuilder
            .setMessage(dialogMessage)
            .setCancelable(false)
            .setPositiveButton("Ya") { _, _ ->
                if (isDialogClose) {
                    finish()
                } else {
                    val result = classifyHelper.deleteById(classify?.id.toString()).toLong()
                    if (result > 0) {
                        val intent = Intent()
                        intent.putExtra(Helper.EXTRA_POSITION, position)
                        setResult(Helper.RESULT_DELETE, intent)
                        finish()
                    } else {
                        Toast.makeText(
                            this@LeafActivity,
                            "Gagal menghapus data",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Tidak") { dialog, _ -> dialog.cancel() }
        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if(isEdit) {
            menuInflater.inflate(R.menu.menu_form, menu)
        }
        return super.onCreateOptionsMenu(menu)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_delete -> showAlertDialog(Helper.ALERT_DIALOG_DELETE)
            android.R.id.home -> showAlertDialog(Helper.ALERT_DIALOG_CLOSE)
        }
        return super.onOptionsItemSelected(item)
    }

    fun classifyImage(image: Bitmap?) {
        try {
            val model = Modeltanaman.newInstance(applicationContext)

            // Creates inputs for reference.
            val inputFeature0 =
                TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
            val byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3)
            byteBuffer.order(ByteOrder.nativeOrder())

            // get 1D array of 224 * 224 pixels in image
            val intValues = IntArray(imageSize * imageSize)
            image!!.getPixels(intValues, 0, image.width, 0, 0, image.width, image.height)

            // iterate over pixels and extract R, G, and B values. Add to bytebuffer.
            var pixel = 0
            for (i in 0 until imageSize) {
                for (j in 0 until imageSize) {
                    val bytes = intValues[pixel++] // RGB
                    byteBuffer.putFloat((bytes shr 16 and 0xFF) * (1f / 255f))
                    byteBuffer.putFloat((bytes shr 8 and 0xFF) * (1f / 255f))
                    byteBuffer.putFloat((bytes and 0xFF) * (1f / 255f))
                }
            }
            inputFeature0.loadBuffer(byteBuffer)

            // Runs model inference and gets result.
            val outputs: Modeltanaman.Outputs = model.process(inputFeature0)
            val outputFeature0: TensorBuffer = outputs.outputFeature0AsTensorBuffer
            val confidences = outputFeature0.floatArray
            // find the index of the class with the biggest confidence.
            var maxPos = 0
            var maxConfidence = 0f
            for (i in confidences.indices) {
                if (confidences[i] > maxConfidence) {
                    maxConfidence = confidences[i]
                    maxPos = i
                }
            }
            val classes = arrayOf("miner", "phoma", "rust")
            binding.result.text = classes[maxPos]
            var s = ""
            for (i in classes.indices) {
                s += String.format("%s: %.1f%%\n", classes[i], confidences[i] * 100)
            }
            binding.confidence.text = s


            // Releases model resources if no longer used.
            model.close()
        } catch (e: IOException) {
            // TODO Handle the exception
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1 && resultCode == RESULT_OK) {
            var image = data!!.extras!!["data"] as Bitmap?
            val dimension = image!!.width.coerceAtMost(image.height)
            image = ThumbnailUtils.extractThumbnail(image, dimension, dimension)
            binding.imageView.setImageBitmap(image)
            image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false)
            classifyImage(image)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
    private fun imageViewToByte(image: ImageView): ByteArray? {
        val bitmap = (image.drawable as BitmapDrawable).bitmap
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, stream)
        return stream.toByteArray()
    }
    override fun onClick(v: View?) {

        if(v?.id == R.id.save) {

            val classified = binding.result.text.toString().trim()
            val consider = binding.confidence.text.toString().trim()

            classify?.classified = classified
            classify?.considers = consider
            classify?.image = imageViewToByte(binding.imageView)

            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.putExtra(Helper.EXTRA_REGISTRATION, classify)
            intent.putExtra(Helper.EXTRA_POSITION, position)
            val values = ContentValues()
            values.put(ClassifybaseRegsiter.ClassifyColumns.CLASSIFIED, classify?.classified)
            values.put(ClassifybaseRegsiter.ClassifyColumns.CONSIDER, classify?.considers)
            values.put(ClassifybaseRegsiter.ClassifyColumns.IMAGE, classify?.image)

            if(isEdit) {
                val result = classifyHelper.update(
                    classify?.id.toString(),
                    values
                ).toLong()
                if (result > 0) {
                    setResult(Helper.RESULT_UPDATE, intent)
                    finish()
                } else {
                    Toast.makeText(this, "Gagal mengupdate data", Toast.LENGTH_SHORT).show()
                }
            } else {
                val result = classifyHelper.insert(values)
                if (result > 0) {
                    classify?.id = result.toInt()
                    setResult(Helper.RESULT_ADD, intent)
                    Log.d("sukses add", "$result")
                    Toast.makeText(
                        this@LeafActivity,
                        "Sukses menambah data",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                } else {
                    Toast.makeText(
                        this@LeafActivity,
                        "Gagal menambah data",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}