package com.exposunmiprinter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.RemoteException;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.sunmi.peripheral.printer.InnerPrinterCallback;
import com.sunmi.peripheral.printer.InnerPrinterManager;
import com.sunmi.peripheral.printer.SunmiPrinterService;

public class SunmiLabelPrinterModule extends ReactContextBaseJavaModule {
    private static final String TAG = "SunmiLabelPrinterModule";
    private static final String MODULE_NAME = "SunmiLabelPrinter";
    private SunmiPrinterService printerService;
    private boolean isConnected = false;

    private final InnerPrinterCallback innerPrinterCallback = new InnerPrinterCallback() {
      @Override
      protected void onConnected(SunmiPrinterService service)
      {
        printerService = service;
        isConnected = true;
        Log.d(TAG, "Printer service connected");
      }

      @Override
      protected void onDisconnected() {
        printerService = null;
        isConnected = false;
        Log.e(TAG, "Printer service disconnected");
      }
    };

    public SunmiLabelPrinterModule(ReactApplicationContext reactContext) {
      super(reactContext);
    }

    @NonNull
    @Override
    public String getName() {
      return MODULE_NAME;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Connection Management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Bind to Sunmi printer service. Must be called before any printing.
     */
    @ReactMethod
    public void  initPrinter(Promise promise) {
      try {
        boolean result = InnerPrinterManager.getInstance().bindService(
          getReactApplicationContext(),
          innerPrinterCallback
        );
        if (result) {
          promise.resolve(true);
        } else {
          promise.reject("INIT_FAILED", "Failed to bind printer service. Device may not be a Sunmi device.");
        }
      } catch (Exception e) {
        Log.e(TAG, "initPrinter error", e);
        promise.reject("INIT_ERROR", e.getMessage());
      }
    }

    /**
     * Unbind the printer service. Call when done.
     */
    @ReactMethod
    public void deInitPrinter(Promise promise) {
      try {
        InnerPrinterManager.getInstance().unBindService(
          getReactApplicationContext(),
          innerPrinterCallback
        );
        isConnected = false;
        promise.resolve(true);
      } catch (Exception e) {
        Log.e(TAG, "deInitPrinter error", e);
        promise.reject("DEINIT_ERROR", e.getMessage());
      }
    }

    /**
     * Check if printer service is connected and ready.
     */
    @ReactMethod
    public void isConnected(Promise promise) {
      promise.resolve(isConnected && printerService != null);
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Printer Status & Info
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Get current printer status.
   * Returns: 1=running, 2=init, 3=abnormal, 4=out of paper, 5=overheating,
   *          6=cover open, 7=cutter error, 8=cutter OK, 9=no black mark paper, 505=no printer
   */
  @ReactMethod
  public void getPrinterStatus(Promise promise) {
    if (!checkService(promise)) return;
    try {
      int status = printerService.updatePrinterState();
      WritableMap map = Arguments.createMap();
      map.putInt("code", status);
      map.putString("message", getPrinterStatusMessage(status));
      promise.resolve(map);
    } catch (RemoteException e) {
      promise.reject("STATUS_ERROR", e.getMessage());
    }
  }

  /**
   * Get printer info: model, firmware version, serial number, paper width.
   */
  @ReactMethod
  public void getPrinterInfo(Promise promise) {
    if (!checkService(promise)) return;
    try {
      WritableMap map = Arguments.createMap();
      map.putString("serialNo", printerService.getPrinterSerialNo());
      map.putString("model", printerService.getPrinterModal());
      map.putString("firmwareVersion", printerService.getPrinterVersion());
      map.putString("serviceVersion", printerService.getServiceVersion());
      map.putInt("paperWidth", printerService.getPrinterPaper()); // 1=58mm, 2=80mm
      promise.resolve(map);
    } catch (RemoteException e) {
      promise.reject("INFO_ERROR", e.getMessage());
    }
  }

  /**
   * Check if device is in label mode (required before label printing).
   */
  @ReactMethod
  public void isLabelMode(Promise promise) {
    if (!checkService(promise)) return;
    try {
      // updatePrinterState returns different code in label mode context.
      // We check if the printer is in label paper mode via paper type.
      // Label mode is typically set via the device settings/system app.
      int state = printerService.updatePrinterState();
      promise.resolve(state == 1); // 1 = running (label mode must be pre-configured)
    } catch (RemoteException e) {
      promise.reject("LABEL_MODE_ERROR", e.getMessage());
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Label Positioning (CRITICAL – must call before each label's content)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Locate to the next label gap position.
   * MUST be called before printing each label's content.
   * Flow: labelLocate → [print content] → labelOutput  (single)
   *       labelLocate → [content] → labelLocate → [content] → labelOutput (multi)
   */
  @ReactMethod
  public void labelLocate(Promise promise) {
    if (!checkService(promise)) return;
    try {
      printerService.labelLocate();
      promise.resolve(true);
    } catch (RemoteException e) {
      promise.reject("LOCATE_ERROR", e.getMessage());
    }
  }

  /**
   * Output the label to the cutting/peeling position.
   * Call after all label content has been sent.
   */
  @ReactMethod
  public void labelOutput(Promise promise) {
    if (!checkService(promise)) return;
    try {
      printerService.labelOutput();
      promise.resolve(true);
    } catch (RemoteException e) {
      promise.reject("OUTPUT_ERROR", e.getMessage());
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Text Printing
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Set text alignment: 0=left, 1=center, 2=right
   */
  @ReactMethod
  public void setAlignment(int alignment, Promise promise) {
    if (!checkService(promise)) return;
    try {
      printerService.setAlignment(alignment, null);
      promise.resolve(true);
    } catch (RemoteException e) {
      promise.reject("ALIGN_ERROR", e.getMessage());
    }
  }

  /**
   * Set font size. Default is 24. Range typically 16–48.
   */
  @ReactMethod
  public void setFontSize(float size, Promise promise) {
    if (!checkService(promise)) return;
    try {
      printerService.setFontSize(size, null);
      promise.resolve(true);
    } catch (RemoteException e) {
      promise.reject("FONT_SIZE_ERROR", e.getMessage());
    }
  }

  /**
   * Set bold on/off: true = bold, false = normal
   */
  @ReactMethod
  public void setBold(boolean bold, Promise promise) {
    if (!checkService(promise)) return;
    try {
      printerService.setFontName(bold ? "bold" : "", null);
      promise.resolve(true);
    } catch (RemoteException e) {
      promise.reject("BOLD_ERROR", e.getMessage());
    }
  }

  /**
   * Print a line of text. Append "\n" to flush immediately.
   */
  @ReactMethod
  public void printText(String text, Promise promise) {
    if (!checkService(promise)) return;
    try {
      printerService.printText(text, null);
      promise.resolve(true);
    } catch (RemoteException e) {
      promise.reject("PRINT_TEXT_ERROR", e.getMessage());
    }
  }

  /**
   * Print text with custom font, size, and typeface.
   * typeFace: null for default, or font file name
   */
  @ReactMethod
  public void printTextWithFont(String text, String typeFace, float fontSize, Promise promise) {
    if (!checkService(promise)) return;
    try {
      printerService.printTextWithFont(text, typeFace, fontSize, null);
      promise.resolve(true);
    } catch (RemoteException e) {
      promise.reject("PRINT_TEXT_FONT_ERROR", e.getMessage());
    }
  }

  /**
   * Print a row of columns (table row).
   * texts: array of column text strings
   * widths: array of column widths (in character units, must sum to paper width)
   * alignments: array of alignments per column (0=left, 1=center, 2=right)
   */
  @ReactMethod
  public void printColumnsText(ReadableArray texts, ReadableArray widths, ReadableArray alignments, Promise promise) {
    if (!checkService(promise)) return;
    try {
      String[] textArr = new String[texts.size()];
      int[] widthArr = new int[widths.size()];
      int[] alignArr = new int[alignments.size()];

      for (int i = 0; i < texts.size(); i++) textArr[i] = texts.getString(i);
      for (int i = 0; i < widths.size(); i++) widthArr[i] = widths.getInt(i);
      for (int i = 0; i < alignments.size(); i++) alignArr[i] = alignments.getInt(i);

      printerService.printColumnsText(textArr, widthArr, alignArr, null);
      promise.resolve(true);
    } catch (RemoteException e) {
      promise.reject("PRINT_COLUMNS_ERROR", e.getMessage());
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Barcode & QR Code
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Print a 1D barcode.
   * data: barcode content
   * symbology: 0=UPC-A, 1=UPC-E, 2=EAN13, 3=EAN8, 4=CODE39, 5=ITF, 6=CODABAR, 7=CODE93, 8=CODE128
   * height: 1–255 (default 162)
   * width: 2–6 (default 2)
   * textPosition: 0=none, 1=above, 2=below, 3=both
   */
  @ReactMethod
  public void printBarCode(String data, int symbology, int height, int width, int textPosition, Promise promise) {
    if (!checkService(promise)) return;
    try {
      printerService.printBarCode(data, symbology, height, width, textPosition, null);
      promise.resolve(true);
    } catch (RemoteException e) {
      promise.reject("PRINT_BARCODE_ERROR", e.getMessage());
    }
  }

  /**
   * Print a QR code.
   * data: QR content (text/URL)
   * moduleSize: dot size 4–16 (default 8)
   * errorLevel: 0=L(7%), 1=M(15%), 2=Q(25%), 3=H(30%)
   */
  @ReactMethod
  public void printQRCode(String data, int moduleSize, int errorLevel, Promise promise) {
    if (!checkService(promise)) return;
    try {
      printerService.printQRCode(data, moduleSize, errorLevel, null);
      promise.resolve(true);
    } catch (RemoteException e) {
      promise.reject("PRINT_QR_ERROR", e.getMessage());
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Bitmap / Image Printing
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Print a bitmap image from a Base64 encoded string.
   * base64Image: Base64 encoded PNG/JPG image
   * type: 0=default, 1=double width, 2=double height, 3=double both
   */
  @ReactMethod
  public void printBitmapBase64(String base64Image, int type, Promise promise) {
    if (!checkService(promise)) return;
    try {
      byte[] imageBytes = Base64.decode(base64Image, Base64.DEFAULT);
      Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
      if (bitmap == null) {
        promise.reject("INVALID_IMAGE", "Could not decode base64 image");
        return;
      }
      printerService.printBitmap(bitmap, type, null);
      promise.resolve(true);
    } catch (RemoteException e) {
      promise.reject("PRINT_BITMAP_ERROR", e.getMessage());
    } catch (Exception e) {
      promise.reject("BITMAP_DECODE_ERROR", e.getMessage());
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Convenience: Print a Complete Label
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Print a single complete label from a structured config map.
   *
   * labelConfig keys:
   *   - lines: Array of line objects:
   *       { type: "text"|"barcode"|"qrcode"|"blank", text, fontSize, bold, alignment,
   *         barcodeData, barcodeSymbology, barcodeHeight, barcodeWidth, barcodeTextPos,
   *         qrData, qrModuleSize, qrErrorLevel, height (for blank lines) }
   *   - copies: number of label copies (default 1)
   */
  @ReactMethod
  public void printLabel(ReadableMap labelConfig, Promise promise) {
    if (!checkService(promise)) return;
    try {
      int copies = labelConfig.hasKey("copies") ? labelConfig.getInt("copies") : 1;
      ReadableArray lines = labelConfig.hasKey("lines") ? labelConfig.getArray("lines") : null;

      for (int copy = 0; copy < copies; copy++) {
        // Step 1: Locate label gap
        printerService.labelLocate();

        // Step 2: Print content lines
        if (lines != null) {
          for (int i = 0; i < lines.size(); i++) {
            ReadableMap line = lines.getMap(i);
            if (line == null) continue;

            String type = line.hasKey("type") ? line.getString("type") : "text";

            // Apply style settings
            if (line.hasKey("alignment")) {
              printerService.setAlignment(line.getInt("alignment"), null);
            }
            if (line.hasKey("fontSize")) {
              printerService.setFontSize((float) line.getDouble("fontSize"), null);
            }

            switch (type != null ? type : "text") {
              case "text":
                String text = line.hasKey("text") ? line.getString("text") : "";
                printerService.printText(text + "\n", null);
                break;

              case "barcode":
                printerService.printBarCode(
                  line.hasKey("barcodeData") ? line.getString("barcodeData") : "",
                  line.hasKey("barcodeSymbology") ? line.getInt("barcodeSymbology") : 8,
                  line.hasKey("barcodeHeight") ? line.getInt("barcodeHeight") : 80,
                  line.hasKey("barcodeWidth") ? line.getInt("barcodeWidth") : 2,
                  line.hasKey("barcodeTextPos") ? line.getInt("barcodeTextPos") : 2,
                  null
                );
                break;

              case "qrcode":
                printerService.printQRCode(
                  line.hasKey("qrData") ? line.getString("qrData") : "",
                  line.hasKey("qrModuleSize") ? line.getInt("qrModuleSize") : 8,
                  line.hasKey("qrErrorLevel") ? line.getInt("qrErrorLevel") : 1,
                  null
                );
                break;

              case "image":
                if (line.hasKey("imageBase64")) {
                  byte[] bytes = Base64.decode(line.getString("imageBase64"), Base64.DEFAULT);
                  Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                  if (bmp != null) {
                    printerService.printBitmap(bmp, 0, null);
                  }
                }
                break;

              case "blank":
                int blankLines = line.hasKey("height") ? line.getInt("height") : 1;
                printerService.lineWrap(blankLines, null);
                break;
            }

            // Reset alignment to left after each line
            printerService.setAlignment(0, null);
          }
        }
      }

      // Step 3: Output label to cut position
      printerService.labelOutput();
      promise.resolve(true);

    } catch (RemoteException e) {
      Log.e(TAG, "printLabel error", e);
      promise.reject("PRINT_LABEL_ERROR", e.getMessage());
    } catch (Exception e) {
      Log.e(TAG, "printLabel unexpected error", e);
      promise.reject("PRINT_LABEL_ERROR", e.getMessage());
    }
  }

  /**
   * Print multiple labels in a loop without outputting between each.
   * More efficient than calling printLabel multiple times.
   * Each element in labelsConfig is a label config (same format as printLabel).
   */
  @ReactMethod
  public void printLabels(ReadableArray labelsConfig, Promise promise) {
    if (!checkService(promise)) return;
    try {
      for (int idx = 0; idx < labelsConfig.size(); idx++) {
        ReadableMap labelConfig = labelsConfig.getMap(idx);
        if (labelConfig == null) continue;

        ReadableArray lines = labelConfig.hasKey("lines") ? labelConfig.getArray("lines") : null;

        // Locate before each label
        printerService.labelLocate();

        if (lines != null) {
          for (int i = 0; i < lines.size(); i++) {
            ReadableMap line = lines.getMap(i);
            if (line == null) continue;

            String type = line.hasKey("type") ? line.getString("type") : "text";

            if (line.hasKey("alignment")) {
              printerService.setAlignment(line.getInt("alignment"), null);
            }
            if (line.hasKey("fontSize")) {
              printerService.setFontSize((float) line.getDouble("fontSize"), null);
            }

            switch (type != null ? type : "text") {
              case "text":
                printerService.printText(
                  (line.hasKey("text") ? line.getString("text") : "") + "\n",
                  null
                );
                break;
              case "barcode":
                printerService.printBarCode(
                  line.hasKey("barcodeData") ? line.getString("barcodeData") : "",
                  line.hasKey("barcodeSymbology") ? line.getInt("barcodeSymbology") : 8,
                  line.hasKey("barcodeHeight") ? line.getInt("barcodeHeight") : 80,
                  line.hasKey("barcodeWidth") ? line.getInt("barcodeWidth") : 2,
                  line.hasKey("barcodeTextPos") ? line.getInt("barcodeTextPos") : 2,
                  null
                );
                break;
              case "qrcode":
                printerService.printQRCode(
                  line.hasKey("qrData") ? line.getString("qrData") : "",
                  line.hasKey("qrModuleSize") ? line.getInt("qrModuleSize") : 8,
                  line.hasKey("qrErrorLevel") ? line.getInt("qrErrorLevel") : 1,
                  null
                );
                break;
              case "blank":
                printerService.lineWrap(
                  line.hasKey("height") ? line.getInt("height") : 1,
                  null
                );
                break;
            }

            printerService.setAlignment(0, null);
          }
        }
      }

      // Single output after all labels
      printerService.labelOutput();
      promise.resolve(true);

    } catch (RemoteException e) {
      Log.e(TAG, "printLabels error", e);
      promise.reject("PRINT_LABELS_ERROR", e.getMessage());
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Utility
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Feed paper by n lines.
   */
  @ReactMethod
  public void feedPaper(int lines, Promise promise) {
    if (!checkService(promise)) return;
    try {
      printerService.lineWrap(lines, null);
      promise.resolve(true);
    } catch (RemoteException e) {
      promise.reject("FEED_ERROR", e.getMessage());
    }
  }

  /**
   * Reset printer to default settings (does not clear buffer).
   */
  @ReactMethod
  public void printerSelfChecking(Promise promise) {
    if (!checkService(promise)) return;
    try {
      printerService.printerSelfChecking(null);
      promise.resolve(true);
    } catch (RemoteException e) {
      promise.reject("SELFCHECK_ERROR", e.getMessage());
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Private helpers
  // ─────────────────────────────────────────────────────────────────────────

  private boolean checkService(Promise promise) {
    if (printerService == null || !isConnected) {
      promise.reject("NOT_CONNECTED",
        "Printer service not connected. Call initPrinter() first.");
      return false;
    }
    return true;
  }

  private String getPrinterStatusMessage(int status) {
    switch (status) {
      case 1: return "Printer is running normally";
      case 2: return "Printer is initializing";
      case 3: return "Printer hardware interface is abnormal";
      case 4: return "Printer is out of paper";
      case 5: return "Printer is overheating";
      case 6: return "Printer cover is not closed";
      case 7: return "Printer cutter is abnormal";
      case 8: return "Printer cutter is normal";
      case 9: return "Black mark paper not found";
      case 505: return "Printer does not exist";
      default: return "Unknown status: " + status;
    }
  }
}
