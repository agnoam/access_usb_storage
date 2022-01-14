package com.agnoam.access_usb_storage

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED
import android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED
import android.os.Build
import android.util.Log
import androidx.annotation.NonNull
import com.github.mjdev.libaums.UsbMassStorageDevice
import com.github.mjdev.libaums.fs.*
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import org.json.JSONObject
import java.io.*
import java.nio.Buffer


/** AccessUsbStoragePlugin */
class AccessUsbStoragePlugin: FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private lateinit var eventChannel: EventChannel
  private lateinit var context: Context

  // Custom
  private var reqPermissionResultObj: Result? = null
  private var deviceMounted: UsbMassStorageDevice? = null
  private var deviceStateChangedEventSink: EventChannel.EventSink? = null

  // Constants
  companion object {
    private const val TAG: String = "AccessUsbStoragePlugin"
    private const val ACTION_USB_PERMISSION: String = "com.agnoam.usb.storage.plugin.USB_PERMISSION"
    private const val USB_PATH_SPLITTER: Char = '/'
    private const val EVENT_CHANNEL_NAME: String = "access_usb_state_events"
  }

  private val eventsHandler: BroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      Log.d(TAG, "Broadcast receiver executing")
      when (intent.action) {
        ACTION_USB_PERMISSION -> showPermissionRequest(intent)
        ACTION_USB_DEVICE_ATTACHED -> dispatchUsbChangedEvent(intent, UsbChangeEvents.Attach)
        ACTION_USB_DEVICE_DETACHED -> dispatchUsbChangedEvent(intent, UsbChangeEvents.Detached)
      }
    }
  }

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "access_usb_storage")
    channel.setMethodCallHandler(this)

    context = flutterPluginBinding.applicationContext

    // Registers a broadcast receiver for the permission request
    context.registerReceiver(eventsHandler, IntentFilter(ACTION_USB_PERMISSION))

    // Registers also ATTACH, DETACHED Usb event handlers
    context.registerReceiver(eventsHandler, IntentFilter(ACTION_USB_DEVICE_ATTACHED))
    context.registerReceiver(eventsHandler, IntentFilter(ACTION_USB_DEVICE_DETACHED))

    // Registers Dart's EventChannel
    eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, EVENT_CHANNEL_NAME)
    eventChannel.setStreamHandler(this)
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    // Switch case of `kotlin` language
    when (call.method) {
      "availableUSBStorageDevices" -> availableStorageHandler(result)
      "requestUSBPermission" -> requestPermissionHandler(call.arguments.toString(), result)
//      "USBChangeEventListener" -> addUsbStateListener(result)
//      "deleteListener" -> removeUsbStateListener(result)
      "write" -> writeFileHandler(call.arguments as Map<String, Any>, result)
      "read" -> readFileHandler(call.arguments as Map<String, String>, result)
      "delete" -> deleteFileHandler(call.arguments as Map<String, String>, result)

      // Like default: in `JS`
      else -> result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    Log.d(TAG, "Plugin is Detached from engine (flutter)")
    channel.setMethodCallHandler(null)

    unmountDevice()
    context.unregisterReceiver(eventsHandler)
  }

  /**
   * EventChannel override method, emits on each new listening
   *
   * @param arguments Arguments given by the dart side
   * @param events Events sink
   */
  override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
    // Dart client registers to the event channel
    deviceStateChangedEventSink = events
  }

  /**
   * EventChannel override method, emits on each cancel (listener remove)
   * @param arguments Some arguments from dart side
   */
  override fun onCancel(arguments: Any?) {
    // Dart client stops to listen
    deviceStateChangedEventSink = null
  }

  /**
   * In case a there are any changes about usb devices/accessories it will notify about them
   *
   * @param intent Intent given by the `BroadcastReceiver`
   * @param newState The new state to dispatch
   */
  private fun dispatchUsbChangedEvent(intent: Intent, newState: UsbChangeEvents) {
    Log.e(TAG, "Dispatching $newState event");

    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
    device?.apply {
      // Commented because it have to request a permission for the device
//      when (newState) {
//        UsbChangeEvents.Attach -> getUsbMassStorageDeviceByKey(deviceName)?.init()
//        UsbChangeEvents.Detached -> getUsbMassStorageDeviceByKey(deviceName)?.close()
//      }

      val returnObject = JSONObject()
      returnObject.put("event", newState.toString())
      returnObject.put("deviceId", deviceName)

      deviceStateChangedEventSink?.success(returnObject.toString())
    }
  }

  /**
   * Showing (android pure) and returning the response of the permission request
   * @param intent The intent of the permission
   */
  private fun showPermissionRequest(intent: Intent) {
    // synchronized(this) {
    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
      if (device == null) {
        Log.e(TAG, "Trying to request permission to unknown device")
      } else {
        device.apply {
          if (reqPermissionResultObj != null) {
            Log.d(TAG, "File system of ${device.deviceName}:")
            File(device.deviceName).walk().forEach {
              Log.d(TAG, it.name)
            }

            reqPermissionResultObj?.success(true)
            reqPermissionResultObj = null
          }

          Log.d(TAG, "Permission accepted")
        }
      }
    } else {
      if (reqPermissionResultObj != null) {
        reqPermissionResultObj?.success(false)
        reqPermissionResultObj = null
      }

      Log.d(TAG, "permission denied for device $device")
    }
    // }
  }

  /**
   * Flutter plugin request permission handler
   *
   * @param deviceKey Device key given by the flutter side
   * @param result Result object given by the flutter plugin
   *
   * @throws Exception In case of exception it will be thrown by the Result object
   */
  private fun requestPermissionHandler(deviceKey: String, @NonNull result: Result) {
    try {
      reqPermissionResultObj = result
      requestPermissionUSB(deviceKey)
    } catch (ex: Exception) {
      result.error("EX", "Was an error requesting permission for usb storage device", ex)
    }
  }

  /**
   * Request permission to access a UsbDevice by it's key
   * @param usbDeviceKey The key of the selected device given by `getUSBDevices()`
   */
  private fun requestPermissionUSB(usbDeviceKey: String) {
    Log.d(TAG, "Requesting permission for device key: $usbDeviceKey")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val usbManager: UsbManager = context.getSystemService(UsbManager::class.java)
      val usbDevice: UsbDevice? = usbManager.deviceList[usbDeviceKey]
      if (usbDevice != null) {
        val permissionIntent = PendingIntent.getBroadcast(
          context, 0,
          Intent(ACTION_USB_PERMISSION), 0
        )

        usbManager.requestPermission(usbDevice, permissionIntent)
      }
    }
  }

  /**
   * Flutter plugin receiver for getting available usb storage devices
   * @param result Result object given by `onMethodCall()`
   */
  private fun availableStorageHandler(@NonNull result: Result) {
    try {
      // val usbStorageDevices: List<String>? = listUsbStorageDevices();
      val usbStorageDevices: List<String> = getUSBDevices()
      result.success(usbStorageDevices)
    } catch (ex: Exception) {
      result.error("EX", "Was an error searching for usb storage devices", ex)
    }
  }

  /**
   * Get all usb devices by `UsbMassStorageDevice`
   * @see libaums_github https://github.com/magnusja/libaums
   */
  private fun getUSBDevices(): List<String> {
    val devicesToReturn: MutableList<String> = ArrayList()
    val devices: Array<UsbMassStorageDevice> = UsbMassStorageDevice.getMassStorageDevices(context)

    devices.forEach { device: UsbMassStorageDevice ->
      devicesToReturn += device.usbDevice.deviceName
    }

    return devicesToReturn
  }

  /**
    Get all usb storage devices absolute paths (pure Android code)
    @return List of UsbStorage devices keys (paths)
  */
  /*
    private fun listUsbStorageDevices(): List<String>? {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val usbManager: UsbManager = context.getSystemService(UsbManager::class.java);
        val storageDevicesPaths: MutableList<String> = ArrayList();

        /*
          deviceList: List<HashMap<String, UsbDevice>>
            The key of the hash map is the absolute path to the device
            The value is a UsbDevice object
         */
        usbManager.deviceList.forEach {
          val deviceInterface: UsbInterface = it.value.getInterface(0);
          if (deviceInterface.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE)
            storageDevicesPaths += it.key;
        }

        Log.e(TAG, "Found storage devices: ${storageDevicesPaths.toString()}");
        Log.e(TAG, "USB FS: ${File(storageDevicesPaths[0])?.list()?.toString()}");

        return storageDevicesPaths;
      }

      return null;
    }
  */

  /**
    Get `UsbMassStorageDevice` object by USB's key (path)
    @return An UsbMassStorageDevice object if found or null
   */
  private fun getUsbMassStorageDeviceByKey(deviceKey: String): UsbMassStorageDevice? {
    val devices: Array<UsbMassStorageDevice> = UsbMassStorageDevice.getMassStorageDevices(context)
    devices.forEach { storageDevice: UsbMassStorageDevice ->
      if (storageDevice.usbDevice.deviceName == deviceKey)
        return storageDevice
    }

    return null
  }

  /**
   * Before reading or writing file to partition, The usb device must be mounted
   * @param device The device to mount
   */
  private fun mountDevice(device: UsbMassStorageDevice) {
    device.init()
    deviceMounted = device
  }

  private fun ejectDeviceHandler(@NonNull result: Result) {
    try {

    } catch (ex: Exception) {

    }
  }

  /**
   * When you done with all the read and write, remove (unMount) the device.
   * In case the device is not mounted, it will ignore
   */
  private fun unmountDevice() {
    if (deviceMounted != null) {
      deviceMounted?.close()
      deviceMounted = null
    }
  }

  /**
   * Handle Kotlin's writeFile function needs with bridge to Flutter's plugin channel
   *
   * @param arguments Arguments passed from the flutter side by the plugin channel
   * @param result Result object to pass function's result to Flutter side
   */
  private fun writeFileHandler(arguments: Map<String, Any>, @NonNull result: Result) {
    try {
      Log.e(TAG, "Arguments received: $arguments")
      writeFile(
        "${arguments["deviceKey"]}",
        "${arguments["relativePath"]}",
        "${arguments["content"]}",
        SavingType.valueOf("${arguments["savingType"]}")
      )
      result.success(true)
    } catch(ex: Exception) {
       result.error(TAG, "Was an error with writing ${arguments["relativePath"]}, in ${arguments["deviceKey"]}", ex)
    }
  }

  /**
   * Write file to USB device by path and content, don't forget to ask for permission first
   * In case the file exists, it will be overwritten
   *
   * @param deviceKey Key of the usb device got from the
   * @param relativePath path relative to the usb root - for example "/<some-dir>/<filename>.txt"
   * @param content content of file
   * @param savingType Which way to save the file
   *
   * @throws NoPermissionException In case there is no permission to write to this usb device
   */
  private fun writeFile(deviceKey: String, relativePath: String, content: String, savingType: SavingType) {
    try {
      val device: UsbMassStorageDevice = getUsbMassStorageDeviceByKey(deviceKey)
        // Kotlin's shortcut for: `if (device == null)`
        ?: throw Exception("$deviceKey Not found")

      deviceMounted ?: mountDevice(device)

      val rootDir: UsbFile = deviceMounted!!.partitions[0].fileSystem.rootDirectory
      val pathSplit: List<String> = relativePath.split(USB_PATH_SPLITTER)
      val filename: String  = pathSplit.last()
      val parentDirPath: String = relativePath.replace(filename, "")

      Log.d(TAG, "filename is: $filename");
      createDirTree(rootDir, parentDirPath, pathSplit)
      writeFileInDir(rootDir, parentDirPath, filename, content, savingType)

      device.close()
    } catch(ex: IllegalStateException) {
      throw NoPermissionException(TAG, deviceKey, "Permission denied to access this device", ex)
    }
  }

  /**
   * Creates the directory tree in case it does not exists
   * @param path Full path without the filename to check and create
   */
  private fun createDirTree(rootDir: UsbFile, path: String, pathSplit: List<String>): UsbFile {
    val destDir: UsbFile? = rootDir.search(path)
    if (destDir != null)
      return destDir

    var currentDirPath: String = "$USB_PATH_SPLITTER"
    var parentDir: UsbFile? = rootDir.search(currentDirPath)

    for (dirname: String in pathSplit) {
      if (parentDir != null) {
        val nextPath: String =
          if (currentDirPath != "$USB_PATH_SPLITTER") currentDirPath + USB_PATH_SPLITTER + dirname
          else currentDirPath + dirname

        val currentDir: UsbFile = rootDir.search(nextPath)
          ?: parentDir.createDirectory(dirname)

        currentDirPath = nextPath
        parentDir = currentDir
      }
    }

    if (parentDir != null)
      return parentDir
    throw Exception("Failed to create directory tree")
  }


  /**
   * Create the file only if the parent directory exists
   *
   * @param rootDir `UsbFile` of the root directory of the USB partition
   * @param containingDirPath Relative directory path of the file's containing directory of the `rootDir`
   * @param filename The file name
   * @param content The content of the file (as string for now)
   * @param savingType Which way to save the file, as String or ByteArray
   */
  private fun writeFileInDir(
    rootDir: UsbFile, containingDirPath: String,
    filename: String, content: Any, savingType: SavingType
  ) {
    // Parent directory of the file
    val parentDir: UsbFile = rootDir.search(containingDirPath)
      ?: throw Exception("Parent dir does not exists")

    try {
        val file: UsbFile = parentDir.createFile(filename)
        val os = UsbFileOutputStream(file)

        if (savingType == SavingType.StringData)
          os.write((content as String).toByteArray())
        else if (savingType == SavingType.BytesData)
          // TODO: Check type of `dart ByteArray class`
          os.write(content as ByteArray)

        os.close()
    } catch(fileExistsEx: IOException) {
      val file: UsbFile? = parentDir.search(filename)
      if (file != null)
         overrideFile(rootDir, file, content, savingType)
    }
  }

  /**
   * Overwrites file by `UsbFile` object and replace the content with the new one
   *
   * @param rootDir The root directory of the partition
   * @param file The exists file `UsbFile` to overwrite
   * @param newContent Content of the file
   *
   * @throws IOException in case the file does not exists or the containing dir
   */
  private fun overrideFile(rootDir: UsbFile, file: UsbFile, newContent: Any, savingType: SavingType) {
    file.delete()

    // Delete the last USB_
    val filePath: String = file.absolutePath
    filePath.dropLast(filePath.lastIndex)
    val filename: String = filePath.split(USB_PATH_SPLITTER).last()
    filePath.replace(filename, "")

    writeFileInDir(rootDir, filePath, filename, newContent, savingType)
  }

  /**
   * Delete function flutter plugin handler
   *
   * @param arguments Map of received args
   * @param result Result object of flutter plugin framework
   */
  private fun deleteFileHandler(arguments: Map<String, String>, @NonNull result: Result) {
    try {
      delete(arguments["deviceKey"].toString(), arguments["path"].toString())
      result.success(true)
    } catch (ex: Exception) {
      result.error(TAG, "Exception occurred during delete command", ex);
    }
  }

  /**
   * Delete file by it's Usb device key, and the relative path
   *
   * @param deviceKey The key of the selected device given by `getUSBDevices()`
   * @param path Relative file path inside the usb to delete
   *
   * @throws IOException in case the file does not exists
   */
  private fun delete(deviceKey: String, path: String) {
    val device: UsbMassStorageDevice = getUsbMassStorageDeviceByKey(deviceKey)
      // Kotlin's shortcut for: `if (device == null)`
      ?: throw Exception("$deviceKey Not found")

    deviceMounted ?: mountDevice(device)
    val fileToDelete: UsbFile = device.partitions[0].fileSystem.rootDirectory.search(path)
      ?: throw IOException("File does not exists")

    fileToDelete.delete()
//    unmountDevice()
  }

  /**
   * Read file flutter plugin handler
   *
   * @param arguments Map of received args
   * @param result Result object of flutter plugin framework
   */
  private fun readFileHandler(arguments: Map<String, String>, @NonNull result: Result) {
    try {
      val savingType = SavingType.valueOf("${arguments["savingType"]}")
      val fileContent: Any = read(
        "${arguments["deviceKey"]}",
        "${arguments["relativePath"]}",
        savingType
      )

      when (savingType) {
        SavingType.BytesData -> result.success(fileContent as ByteArray)
        SavingType.StringData -> result.success(fileContent.toString())
      }
    } catch (ex: Exception) {
      result.error(TAG, "Exception occurred during read command", ex);
    }
  }

  /**
   * Get file content from usb device by usb device key and path
   *
   * @param deviceKey The key of the selected device given by `getUSBDevices()`
   * @param path Relative file path inside the usb to read
   * @param outputType Type of output (available types in `SavingType` enum)
   *
   * @return File content
   * @throws IOException in case the file is not exists
   */
  private fun read(deviceKey: String, path: String, outputType: SavingType): Any {
    val device: UsbMassStorageDevice = getUsbMassStorageDeviceByKey(deviceKey)
      ?: throw Exception("$deviceKey Not found")

    deviceMounted ?: mountDevice(device)

    val deviceFs: FileSystem = deviceMounted!!.partitions[0]?.fileSystem
    val file: UsbFile = deviceFs.rootDirectory.search(path)
      ?: throw IOException("File does not exists")

    return readFile(file, deviceFs, outputType)

    // Closing file connection
    // inputStream.close()

    // unmountDevice()
    // throw Exception("Unsupported return type")
  }

  /**
   * Actual read file implementation
   *
   * @param file `UsbFile` object of wanted file
   * @return The content as ByteArray or as a String
   * @throws IOException in case of read fails
   */
  private fun readFile(file: UsbFile, fs: FileSystem, outputType: SavingType): Any {
    val inputStream: InputStream = UsbFileStreamFactory.createBufferedInputStream(file, fs)
    val outputStream = ByteArrayOutputStream()

    inputStream.use { input ->
      outputStream.use { output ->
        input.copyTo(output)
      }
    }

    val byteArray = outputStream.toByteArray()
    val outputString = String(byteArray, Charsets.UTF_8)
    Log.d(TAG, "Output string from read file: $outputString")

    inputStream.close()
    outputStream.close()

    if (outputType == SavingType.BytesData)
      return byteArray
    if (outputType == SavingType.StringData)
      return outputString

    throw Exception("Unsupported outputType")
  }
}