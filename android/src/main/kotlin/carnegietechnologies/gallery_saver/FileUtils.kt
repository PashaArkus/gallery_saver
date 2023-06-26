package carnegietechnologies.gallery_saver

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import java.io.*
import android.media.MediaMetadataRetriever

/**
 * Core implementation of methods related to File manipulation
 */
internal object FileUtils {

    private const val TAG = "FileUtils"
    private const val SCALE_FACTOR = 50.0
    private const val BUFFER_SIZE = 1024 * 1024 * 8
    private const val DEGREES_90 = 90
    private const val DEGREES_180 = 180
    private const val DEGREES_270 = 270
    private const val EOF = -1

    /**
     * Inserts image into external storage
     *
     * @param contentResolver - content resolver
     * @param path            - path to temp file that needs to be stored
     * @param folderName      - folder name for storing image
     * @param toDcim          - whether the file should be saved to DCIM
     * @return true if image was saved successfully
     */
    fun insertImage(
            contentResolver: ContentResolver,
            path: String,
            folderName: String?,
            toDcim: Boolean
    ): Boolean {
        val file = File(path)
        val extension = MimeTypeMap.getFileExtensionFromUrl(file.toString())
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        var source = getBytesFromFile(file)

        var directory = Environment.DIRECTORY_PICTURES
        if (toDcim) {
            directory = Environment.DIRECTORY_DCIM
        }

        val rotatedBytes = getRotatedBytesIfNecessary(source, path)

        if (rotatedBytes != null) {
            source = rotatedBytes
        }
        val albumDir = File(getAlbumFolderPath(folderName, MediaType.image, toDcim))
        val imageFilePath = File(albumDir, file.name).absolutePath

        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, file.name)
        values.put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        values.put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
        values.put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
        values.put(MediaStore.Images.Media.SIZE, file.length())

        if (android.os.Build.VERSION.SDK_INT < 29) {
            values.put(MediaStore.Images.ImageColumns.DATA, imageFilePath)
        } else {
            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            values.put(MediaStore.Images.Media.RELATIVE_PATH, directory + File.separator + folderName)
        }

        var imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        try {
            imageUri = contentResolver.insert(imageUri, values)

            if (source != null) {
                var outputStream: OutputStream? = null
                if (imageUri != null) {
                    outputStream = contentResolver.openOutputStream(imageUri)
                }

                outputStream?.use {
                    outputStream.write(source)
                }

                if (imageUri != null && android.os.Build.VERSION.SDK_INT < 29) {
                    val pathId = ContentUris.parseId(imageUri)
                    val baseUri = Uri.parse("content://media/external/images/media")
                    imageUri = Uri.withAppendedPath(baseUri, "" + pathId)
                }

                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert image", e)
        }

        return false
    }

    /**
     * Returns the folder path for storing media files
     *
     * @param folderName - folder name for storing media files
     * @param mediaType  - media type (image or video)
     * @param toDcim     - whether the file should be saved to DCIM
     * @return folder path
     */
    private fun getAlbumFolderPath(folderName: String?, mediaType: MediaType, toDcim: Boolean): String {
        val baseDirectory = if (toDcim) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        }

        val albumFolder = if (!TextUtils.isEmpty(folderName)) {
            File(baseDirectory, folderName)
        } else {
            File(baseDirectory, mediaType.folderName)
        }

        if (!albumFolder.exists()) {
            albumFolder.mkdirs()
        }

        return albumFolder.absolutePath
    }

    /**
     * Rotates the image if necessary based on its orientation value
     *
     * @param bytes - image byte array
     * @param path  - image file path
     * @return rotated image byte array, or null if no rotation is required
     */
    private fun getRotatedBytesIfNecessary(bytes: ByteArray?, path: String): ByteArray? {
        if (bytes == null || !isRotationRequired(path)) {
            return null
        }

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val orientation = getExifOrientation(path)
        val rotatedBitmap = rotateBitmap(bitmap, orientation)
        return bitmapToArray(rotatedBitmap)
    }

    /**
     * Checks if image rotation is required based on its EXIF orientation value
     *
     * @param path - image file path
     * @return true if rotation is required, false otherwise
     */
    private fun isRotationRequired(path: String): Boolean {
        val orientation = getExifOrientation(path)
        return orientation == DEGREES_90 || orientation == DEGREES_180 || orientation == DEGREES_270
    }

    /**
     * Retrieves the EXIF orientation value of the image
     *
     * @param path - image file path
     * @return EXIF orientation value
     */
    private fun getExifOrientation(path: String): Int {
        var orientation = DEGREES_0
        try {
            val exif = ExifInterface(path)
            val exifOrientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            )

            when (exifOrientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> orientation = DEGREES_90
                ExifInterface.ORIENTATION_ROTATE_180 -> orientation = DEGREES_180
                ExifInterface.ORIENTATION_ROTATE_270 -> orientation = DEGREES_270
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to get Exif data", e)
        }

        return orientation
    }

    /**
     * Rotates the given bitmap by the specified degrees
     *
     * @param bitmap     - input bitmap
     * @param degrees    - rotation degrees
     * @return rotated bitmap
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
        )
    }

    /**
     * Converts bitmap to byte array
     *
     * @param bitmap - input bitmap
     * @return byte array
     */
    private fun bitmapToArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        return stream.toByteArray()
    }

    /**
     * Retrieves byte array from file
     *
     * @param file - input file
     * @return byte array
     */
    private fun getBytesFromFile(file: File): ByteArray? {
        var source: InputStream? = null
        var byteArray: ByteArray? = null

        try {
            source = FileInputStream(file)
            val bufferSize = Math.min(source.available(), BUFFER_SIZE)
            byteArray = ByteArray(bufferSize)

            while (source.read(byteArray, 0, bufferSize) != EOF) {
                // Reading from source stream
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read bytes from file", e)
        } finally {
            try {
                source?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to close input stream", e)
            }
        }

        return byteArray
    }
}
