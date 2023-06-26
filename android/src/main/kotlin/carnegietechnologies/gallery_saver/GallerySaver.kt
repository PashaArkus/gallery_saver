package carnegietechnologies.gallery_saver

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class MediaType { image, video }

/**
 * Class holding implementation of saving images and videos
 */
class GallerySaver internal constructor(private val activity: Activity) {

    private var pendingResult: MethodChannel.Result? = null
    private var mediaType: MediaType? = null
    private var filePath: String = ""
    private var albumName: String = ""
    private var toDcim: Boolean = false

    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)

    /**
     * Saves image or video to device
     *
     * @param methodCall - method call
     * @param result     - result to be set when saving operation finishes
     * @param mediaType    - media type
     */
    internal fun checkPermissionAndSaveFile(
            methodCall: MethodCall,
            result: MethodChannel.Result,
            mediaType: MediaType
    ) {
        filePath = methodCall.argument<Any>(KEY_PATH)?.toString() ?: ""
        albumName = methodCall.argument<Any>(KEY_ALBUM_NAME)?.toString() ?: ""
        toDcim = methodCall.argument<Any>(KEY_TO_DCIM) as Boolean
        this.mediaType = mediaType
        this.pendingResult = result

        if (isWritePermissionGranted() || android.os.Build.VERSION.SDK_INT >= 29) {
            saveMediaFile()
        } else {
            ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_EXTERNAL_IMAGE_STORAGE_PERMISSION
            )
        }
    }

    private fun isWritePermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
                activity, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun saveMediaFile() {
        uiScope.launch {
            val success = launch(Dispatchers.IO) {
                if (mediaType == MediaType.video) {
                    FileUtils.insertVideo(activity.contentResolver, filePath, albumName, toDcim)
                } else {
                    FileUtils.insertImage(activity.contentResolver, filePath, albumName, toDcim)
                }
            }
            success.join()
            finishWithSuccess()
        }
    }

    private fun finishWithSuccess() {
        pendingResult?.success(true)
        pendingResult = null
    }

    private fun finishWithFailure() {
        pendingResult?.success(false)
        pendingResult = null
    }

    companion object {
        private const val REQUEST_EXTERNAL_IMAGE_STORAGE_PERMISSION = 2408
        private const val KEY_PATH = "path"
        private const val KEY_ALBUM_NAME = "albumName"
        private const val KEY_TO_DCIM = "toDcim"
    }
}
