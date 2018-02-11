// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.imagepicker;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener;
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import pl.aprilapps.easyphotopicker.DefaultCallback;
import pl.aprilapps.easyphotopicker.EasyImage;
import pl.aprilapps.easyphotopicker.EasyImage.ImageSource;

/** Location Plugin */
public class ImagePickerPlugin implements MethodCallHandler, ActivityResultListener, RequestPermissionsResultListener {
  private static String TAG = "ImagePicker";
  private static final String CHANNEL = "image_picker";

  public static final int REQUEST_CODE_PICK = 2342;
  public static final int REQUEST_CODE_CAMERA = 2343;

  private static final int SOURCE_ASK_USER = 0;
  private static final int SOURCE_CAMERA = 1;
  private static final int SOURCE_GALLERY = 2;

  private final PluginRegistry.Registrar registrar;

  // Pending method call to obtain an image
  private Result pendingResult;
  private MethodCall methodCall;

  public static void registerWith(PluginRegistry.Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), CHANNEL);
    final ImagePickerPlugin instance = new ImagePickerPlugin(registrar);
    registrar.addActivityResultListener(instance);
    registrar.addRequestPermissionsResultListener(instance);
    channel.setMethodCallHandler(instance);
  }

  private ImagePickerPlugin(PluginRegistry.Registrar registrar) {
    this.registrar = registrar;
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    if (pendingResult != null) {
      result.error("ALREADY_ACTIVE", "Image picker is already active", null);
      return;
    }

    Activity activity = registrar.activity();
    if (activity == null) {
      result.error("no_activity", "image_picker plugin requires a foreground activity.", null);
      return;
    }

    pendingResult = result;
    methodCall = call;

    if (checkPermissions()) {
      performCall();
    }

  }

  private void performCall() {
    if (methodCall.method.equals("pickImage")) {
      int imageSource = methodCall.argument("source");

      switch (imageSource) {
        case SOURCE_ASK_USER:
          new AlertDialog.Builder(registrar.activity())
                  .setItems(new CharSequence[]{"Gallery", "Camera"}, new OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialogInterface, final int i) {
                      if (i == 0) {
                        EasyImage.openChooserWithGallery(registrar.activity(), "", 0);
                      }
                      else {
                        EasyImage.openCamera(registrar.activity(), 0);
                      }
                    }
                  })
                  .setNegativeButton(android.R.string.cancel, null)
                  .create().show();

          break;
        case SOURCE_GALLERY:
          EasyImage.openChooserWithGallery(registrar.activity(), "", 0);
          break;
        case SOURCE_CAMERA:
          EasyImage.openCamera(registrar.activity(), 0);
          break;
        default:
          throw new IllegalArgumentException("Invalid image source: " + imageSource);
      }
    } else {
      throw new IllegalArgumentException("Unknown method " + methodCall.method);
    }
  }

  @Override
  public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    if (checkPermissions()) {
      performCall();
    }

    return true;
  }

  private boolean checkPermissions() {
    if (ActivityCompat.checkSelfPermission(registrar.activity(), WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
      return true;
    }
    else {
      ActivityCompat.requestPermissions(registrar.activity(), new String[] {WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_CAMERA);
      return false;
    }
  }


  @Override
  public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.i("IMAGE_PICKER", "RESULT " + data);

    EasyImage.handleActivityResult(requestCode, resultCode, data, registrar.activity(), new DefaultCallback() {
      @Override
      public void onImagePickerError(Exception e, EasyImage.ImageSource source, int type) {
        //Some error handling
        pendingResult = null;
        methodCall = null;
      }

      @Override
      public void onImagePicked(final File imageFile, final ImageSource source, final int type) {
        handleResult(imageFile);
      }

    });

    return true;
  }

  private void handleResult(File file) {
    if (pendingResult != null) {
      Double maxWidth = methodCall.argument("maxWidth");
      Double maxHeight = methodCall.argument("maxHeight");
      boolean shouldScale = maxWidth != null || maxHeight != null;

      if (!shouldScale) {
        pendingResult.success(file.getPath());
      } else {
        try {
          File imageFile = scaleImage(file, maxWidth, maxHeight);
          pendingResult.success(imageFile.getPath());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      pendingResult = null;
      methodCall = null;
    } else {
      throw new IllegalStateException("Received images from picker that were not requested");
    }
  }

  private File scaleImage(File file, Double maxWidth, Double maxHeight) throws IOException {
    Bitmap bmp = BitmapFactory.decodeFile(file.getPath());
    double originalWidth = bmp.getWidth() * 1.0;
    double originalHeight = bmp.getHeight() * 1.0;

    boolean hasMaxWidth = maxWidth != null;
    boolean hasMaxHeight = maxHeight != null;

    Double width = hasMaxWidth ? Math.min(originalWidth, maxWidth) : originalWidth;
    Double height = hasMaxHeight ? Math.min(originalHeight, maxHeight) : originalHeight;

    boolean shouldDownscaleWidth = hasMaxWidth && maxWidth < originalWidth;
    boolean shouldDownscaleHeight = hasMaxHeight && maxHeight < originalHeight;
    boolean shouldDownscale = shouldDownscaleWidth || shouldDownscaleHeight;

    if (shouldDownscale) {
      double downscaledWidth = (height / originalHeight) * originalWidth;
      double downscaledHeight = (width / originalWidth) * originalHeight;

      if (width < height) {
        if (!hasMaxWidth) {
          width = downscaledWidth;
        } else {
          height = downscaledHeight;
        }
      } else if (height < width) {
        if (!hasMaxHeight) {
          height = downscaledHeight;
        } else {
          width = downscaledWidth;
        }
      } else {
        if (originalWidth < originalHeight) {
          width = downscaledWidth;
        } else if (originalHeight < originalWidth) {
          height = downscaledHeight;
        }
      }
    }

    Bitmap scaledBmp = Bitmap.createScaledBitmap(bmp, width.intValue(), height.intValue(), false);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    scaledBmp.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);

    String scaledCopyPath = file.getPath().replace(file.getName(), "scaled_" + file.getName());
    File imageFile = new File(scaledCopyPath);

    FileOutputStream fileOutput = new FileOutputStream(imageFile);
    fileOutput.write(outputStream.toByteArray());
    fileOutput.close();

    if (shouldDownscale) {
      copyExif(file.getPath(), scaledCopyPath);
    }

    return imageFile;
  }

  private void copyExif(String filePathOri, String filePathDest) {
    try {
      ExifInterface oldExif = new ExifInterface(filePathOri);
      ExifInterface newExif = new ExifInterface(filePathDest);

      List<String> attributes =
          Arrays.asList(
              "FNumber",
              "ExposureTime",
              "ISOSpeedRatings",
              "GPSAltitude",
              "GPSAltitudeRef",
              "FocalLength",
              "GPSDateStamp",
              "WhiteBalance",
              "GPSProcessingMethod",
              "GPSTimeStamp",
              "DateTime",
              "Flash",
              "GPSLatitude",
              "GPSLatitudeRef",
              "GPSLongitude",
              "GPSLongitudeRef",
              "Make",
              "Model",
              "Orientation");
      for (String attribute : attributes) {
        setIfNotNull(oldExif, newExif, attribute);
      }

      newExif.saveAttributes();

    } catch (Exception ex) {
      Log.e(TAG, "Error preserving Exif data on selected image: " + ex);
    }
  }

  private void setIfNotNull(ExifInterface oldExif, ExifInterface newExif, String property) {
    if (oldExif.getAttribute(property) != null) {
      newExif.setAttribute(property, oldExif.getAttribute(property));
    }
  }
}
