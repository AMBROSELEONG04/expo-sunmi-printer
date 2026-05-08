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

import com.sunmi.printerx.PrinterSdk;
import com.sunmi.printerx.SdkException;
import com.sunmi.printerx.api.CanvasApi;
import com.sunmi.printerx.api.LineApi;
import com.sunmi.printerx.api.PrintResult;
import com.sunmi.printerx.enums.HumanReadable;
import com.sunmi.printerx.enums.ImageAlgorithm;
import com.sunmi.printerx.style.AreaStyle;
import com.sunmi.printerx.style.BarcodeStyle;
import com.sunmi.printerx.style.BaseStyle;
import com.sunmi.printerx.style.BitmapStyle;
import com.sunmi.printerx.style.QrStyle;
import com.sunmi.printerx.style.TextStyle;

/**
 * React Native module for Sunmi Label Printer — PrinterX SDK
 *
 * Uses Canvas API for precise label layout (pixel-based positioning).
 * 1mm = 8 pixels on Sunmi label printers.
 *
 * Printing flow:
 *   initPrinter()
 *   → printLabel({ width, height, copies, elements: [...] })
 *       internally: initCanvas → render elements → printCanvas
 *
 * Supported devices: Sunmi V2 Pro, V2s, and other Sunmi devices with label printer
 */
public class SunmiLabelPrinterModule extends ReactContextBaseJavaModule {

    private static final String TAG = "SunmiLabelPrinter";
    private static final String MODULE_NAME = "SunmiLabelPrinter";

    private PrinterSdk.Printer printer;
    private PrinterSdk printerSdk;

    public SunmiLabelPrinterModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @NonNull
    @Override
    public String getName() {
        return MODULE_NAME;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Connection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Initialise the PrinterX SDK and get the first available printer.
     * Must be called before any printing.
     */
    @ReactMethod
    public void initPrinter(final Promise promise) {
        try {
            PrinterSdk.getInstance().getPrinter(getReactApplicationContext(),
                new PrinterSdk.PrinterListen() {
                    @Override
                    public void onDefPrinter(PrinterSdk.Printer defPrinter) {
                        printer = defPrinter;
                        promise.resolve(true);
                    }

                    @Override
                    public void onPrinters(java.util.List<PrinterSdk.Printer> printers) {
                        // Called with all available printers; we use the default one
                    }
                });
        } catch (SdkException e) {
            Log.e(TAG, "initPrinter error", e);
            promise.reject("INIT_ERROR", e.getMessage());
        }
    }

    /**
     * Release the printer SDK resources.
     */
    @ReactMethod
    public void deInitPrinter(Promise promise) {
        try {
            PrinterSdk.getInstance().destroy(getReactApplicationContext());
            printer = null;
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("DEINIT_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void isConnected(Promise promise) {
        promise.resolve(printer != null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status & Info
    // ─────────────────────────────────────────────────────────────────────────

    @ReactMethod
    public void getPrinterInfo(Promise promise) {
        if (!checkPrinter(promise)) return;
        try {
            WritableMap map = Arguments.createMap();
            map.putString("model",   printer.getInfo("type", ""));
            map.putString("name",    printer.getInfo("name", ""));
            promise.resolve(map);
        } catch (Exception e) {
            promise.reject("INFO_ERROR", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // High-level Label API  (Canvas-based, pixel-positioned)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Print a complete label using the Canvas API.
     *
     * labelConfig keys:
     *   width   (int)  – canvas width in pixels  (1mm = 8px; e.g. 48mm → 384)
     *   height  (int)  – canvas height in pixels (e.g. 30mm → 240, 25mm → 200)
     *   copies  (int)  – number of copies (default 1)
     *   elements (array) – list of elements to render:
     *
     * Element types:
     *
     *   TEXT:
     *     { type:"text", text, x, y, width?, height?,
     *       fontSize?, bold?, italic?, underline?,
     *       widthRatio?, heightRatio? }
     *
     *   BARCODE:
     *     { type:"barcode", data, x, y, width?, height?,
     *       dotWidth?,   (bar width, default 2)
     *       barHeight?,  (bar height in px, default 60)
     *       readable?    (0=none, 1=above, 2=below, default 2) }
     *
     *   QRCODE:
     *     { type:"qrcode", data, x, y, width?, height?,
     *       dot? (module size, default 4) }
     *
     *   IMAGE:
     *     { type:"image", imageBase64, x, y, width?, height?,
     *       dithering? (bool, default false) }
     *
     *   BOX (border/rectangle):
     *     { type:"box", x, y, width, height }
     */
    @ReactMethod
    public void printLabel(ReadableMap labelConfig, final Promise promise) {
        if (!checkPrinter(promise)) return;
        try {
            int canvasWidth  = labelConfig.hasKey("width")  ? labelConfig.getInt("width")  : 384;
            int canvasHeight = labelConfig.hasKey("height") ? labelConfig.getInt("height") : 240;
            int copies       = labelConfig.hasKey("copies") ? labelConfig.getInt("copies") : 1;
            ReadableArray elements = labelConfig.hasKey("elements") ? labelConfig.getArray("elements") : null;

            CanvasApi api = printer.canvasApi();

            // Step 1: initialise canvas with dimensions
            api.initCanvas(BaseStyle.getStyle()
                    .setWidth(canvasWidth)
                    .setHeight(canvasHeight));

            // Step 2: render each element
            if (elements != null) {
                for (int i = 0; i < elements.size(); i++) {
                    ReadableMap el = elements.getMap(i);
                    if (el == null) continue;
                    renderElement(api, el);
                }
            }

            // Step 3: print with callback
            api.printCanvas(copies, new PrintResult() {
                @Override
                public void onResult(int resultCode, String message) throws RemoteException {
                    if (resultCode == 0) {
                        promise.resolve(true);
                    } else {
                        promise.reject("PRINT_FAILED", "Print failed: " + message);
                    }
                }
            });

        } catch (SdkException e) {
            Log.e(TAG, "printLabel error", e);
            promise.reject("PRINT_LABEL_ERROR", e.getMessage());
        }
    }

    /**
     * Print multiple different labels in one call.
     * Each entry in labelsConfig is a full label config (same as printLabel).
     * Callback resolves after all labels are printed.
     */
    @ReactMethod
    public void printLabels(ReadableArray labelsConfig, final Promise promise) {
        if (!checkPrinter(promise)) return;
        // Print sequentially; resolve after the last one
        printLabelAt(labelsConfig, 0, promise);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Recursively print labels one by one to preserve order */
    private void printLabelAt(final ReadableArray labelsConfig, final int idx, final Promise promise) {
        if (idx >= labelsConfig.size()) {
            promise.resolve(true);
            return;
        }
        ReadableMap labelConfig = labelsConfig.getMap(idx);
        if (labelConfig == null) {
            printLabelAt(labelsConfig, idx + 1, promise);
            return;
        }
        try {
            int canvasWidth  = labelConfig.hasKey("width")  ? labelConfig.getInt("width")  : 384;
            int canvasHeight = labelConfig.hasKey("height") ? labelConfig.getInt("height") : 240;
            int copies       = labelConfig.hasKey("copies") ? labelConfig.getInt("copies") : 1;
            ReadableArray elements = labelConfig.hasKey("elements") ? labelConfig.getArray("elements") : null;

            CanvasApi api = printer.canvasApi();
            api.initCanvas(BaseStyle.getStyle().setWidth(canvasWidth).setHeight(canvasHeight));

            if (elements != null) {
                for (int i = 0; i < elements.size(); i++) {
                    ReadableMap el = elements.getMap(i);
                    if (el == null) continue;
                    renderElement(api, el);
                }
            }

            api.printCanvas(copies, new PrintResult() {
                @Override
                public void onResult(int resultCode, String message) throws RemoteException {
                    if (resultCode == 0) {
                        printLabelAt(labelsConfig, idx + 1, promise);
                    } else {
                        promise.reject("PRINT_FAILED", "Label " + idx + " failed: " + message);
                    }
                }
            });
        } catch (SdkException e) {
            promise.reject("PRINT_LABELS_ERROR", e.getMessage());
        }
    }

    /**
     * Render a single element onto the canvas.
     */
    private void renderElement(CanvasApi api, ReadableMap el) throws SdkException {
        String type = el.hasKey("type") ? el.getString("type") : "text";
        int x = el.hasKey("x") ? el.getInt("x") : 0;
        int y = el.hasKey("y") ? el.getInt("y") : 0;

        switch (type != null ? type : "text") {

            case "text": {
                TextStyle style = TextStyle.getStyle()
                        .setPosX(x)
                        .setPosY(y);
                if (el.hasKey("fontSize"))    style.setTextSize(el.getInt("fontSize"));
                if (el.hasKey("bold") && el.getBoolean("bold"))           style.enableBold(true);
                if (el.hasKey("italic") && el.getBoolean("italic"))       style.enableItalic(true);
                if (el.hasKey("underline") && el.getBoolean("underline")) style.enableUnderline(true);
                if (el.hasKey("width"))       style.setWidth(el.getInt("width"));
                if (el.hasKey("height"))      style.setHeight(el.getInt("height"));
                if (el.hasKey("widthRatio"))  style.setTextWidthRatio((float) el.getDouble("widthRatio"));
                if (el.hasKey("heightRatio")) style.setTextHeightRatio((float) el.getDouble("heightRatio"));
                String text = el.hasKey("text") ? el.getString("text") : "";
                api.renderText(text, style);
                break;
            }

            case "barcode": {
                BarcodeStyle style = BarcodeStyle.getStyle()
                        .setPosX(x)
                        .setPosY(y);
                if (el.hasKey("width"))     style.setWidth(el.getInt("width"));
                if (el.hasKey("height"))    style.setHeight(el.getInt("height"));
                if (el.hasKey("dotWidth"))  style.setDotWidth(el.getInt("dotWidth"));
                if (el.hasKey("barHeight")) style.setBarHeight(el.getInt("barHeight"));
                int readable = el.hasKey("readable") ? el.getInt("readable") : 2;
                style.setReadable(readable == 1 ? HumanReadable.POS_ONE
                                : readable == 2 ? HumanReadable.POS_TWO
                                : HumanReadable.NONE);
                String data = el.hasKey("data") ? el.getString("data") : "";
                api.renderBarCode(data, style);
                break;
            }

            case "qrcode": {
                QrStyle style = QrStyle.getStyle()
                        .setPosX(x)
                        .setPosY(y);
                if (el.hasKey("width"))  style.setWidth(el.getInt("width"));
                if (el.hasKey("height")) style.setHeight(el.getInt("height"));
                if (el.hasKey("dot"))    style.setDot(el.getInt("dot"));
                String data = el.hasKey("data") ? el.getString("data") : "";
                api.renderQrCode(data, style);
                break;
            }

            case "image": {
                if (!el.hasKey("imageBase64")) break;
                byte[] bytes = Base64.decode(el.getString("imageBase64"), Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp == null) break;
                BitmapStyle style = BitmapStyle.getStyle()
                        .setPosX(x)
                        .setPosY(y);
                if (el.hasKey("width"))  style.setWidth(el.getInt("width"));
                if (el.hasKey("height")) style.setHeight(el.getInt("height"));
                boolean dithering = el.hasKey("dithering") && el.getBoolean("dithering");
                style.setAlgorithm(dithering ? ImageAlgorithm.DITHERING : ImageAlgorithm.BINARIZATION);
                api.renderBitmap(bmp, style);
                break;
            }

            case "box": {
                AreaStyle style = AreaStyle.getStyle()
                        .setStyle(com.sunmi.printerx.enums.Shape.BOX)
                        .setPosX(x)
                        .setPosY(y);
                if (el.hasKey("width"))  style.setWidth(el.getInt("width"));
                if (el.hasKey("height")) style.setHeight(el.getInt("height"));
                api.renderArea(style);
                break;
            }

            default:
                Log.w(TAG, "Unknown element type: " + type);
        }
    }

    private boolean checkPrinter(Promise promise) {
        if (printer == null) {
            promise.reject("NOT_CONNECTED",
                    "Printer not connected. Call initPrinter() first.");
            return false;
        }
        return true;
    }
}