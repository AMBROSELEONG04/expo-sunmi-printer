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
import com.sunmi.printerx.api.PrintResult;
import com.sunmi.printerx.enums.HumanReadable;
import com.sunmi.printerx.enums.ImageAlgorithm;
import com.sunmi.printerx.enums.Shape;
import com.sunmi.printerx.style.AreaStyle;
import com.sunmi.printerx.style.BarcodeStyle;
import com.sunmi.printerx.style.BaseStyle;
import com.sunmi.printerx.style.BitmapStyle;
import com.sunmi.printerx.style.QrStyle;
import com.sunmi.printerx.style.TextStyle;

import java.util.List;

public class SunmiLabelPrinterModule extends ReactContextBaseJavaModule {

    private static final String TAG = "SunmiLabelPrinter";
    private static final String MODULE_NAME = "SunmiLabelPrinter";

    private PrinterSdk.Printer printer;

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
                    public void onPrinters(List<PrinterSdk.Printer> printers) {
                        // use default printer from onDefPrinter
                    }
                });
        } catch (SdkException e) {
            Log.e(TAG, "initPrinter error", e);
            promise.reject("INIT_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void deInitPrinter(Promise promise) {
        try {
            // destroy() takes no arguments in printerx 1.0.18
            PrinterSdk.getInstance().destroy();
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

    @ReactMethod
    public void getPrinterInfo(Promise promise) {
        // PrinterSdk.Printer has no getInfo() in 1.0.18 — return what we have
        WritableMap map = Arguments.createMap();
        map.putBoolean("connected", printer != null);
        promise.resolve(map);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Label Printing  (Canvas API)
    //
    // Size guide: 1mm = 8px
    //   30×20mm  → width:240,  height:160
    //   48×30mm  → width:384,  height:240
    //   53×28mm  → width:420,  height:220
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Print a single label.
     *
     * labelConfig:
     *   width    (int)   canvas width in px
     *   height   (int)   canvas height in px
     *   copies   (int)   number of copies (default 1)
     *   elements (array) see renderElement() for element types
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
            api.initCanvas(BaseStyle.getStyle()
                    .setWidth(canvasWidth)
                    .setHeight(canvasHeight));

            if (elements != null) {
                for (int i = 0; i < elements.size(); i++) {
                    ReadableMap el = elements.getMap(i);
                    if (el != null) renderElement(api, el);
                }
            }

            api.printCanvas(copies, new PrintResult() {
                @Override
                public void onResult(int resultCode, String message) throws RemoteException {
                    if (resultCode == 0) {
                        promise.resolve(true);
                    } else {
                        promise.reject("PRINT_FAILED", "Print error: " + message);
                    }
                }
            });

        } catch (SdkException e) {
            Log.e(TAG, "printLabel error", e);
            promise.reject("PRINT_LABEL_ERROR", e.getMessage());
        }
    }

    /**
     * Print multiple different labels sequentially.
     */
    @ReactMethod
    public void printLabels(ReadableArray labelsConfig, final Promise promise) {
        if (!checkPrinter(promise)) return;
        printLabelAt(labelsConfig, 0, promise);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

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
                    if (el != null) renderElement(api, el);
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
     * Render one element onto the canvas.
     *
     * Supported types: text | barcode | qrcode | image | box
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
                if (el.hasKey("fontSize")) style.setTextSize(el.getInt("fontSize"));
                if (el.hasKey("bold") && el.getBoolean("bold")) style.enableBold(true);
                // Note: enableItalic() does not exist in printerx 1.0.18 — omitted
                if (el.hasKey("underline") && el.getBoolean("underline")) style.enableUnderline(true);
                if (el.hasKey("width"))       style.setWidth(el.getInt("width"));
                if (el.hasKey("height"))      style.setHeight(el.getInt("height"));
                // setTextWidthRatio / setTextHeightRatio accept int in 1.0.18
                if (el.hasKey("widthRatio"))  style.setTextWidthRatio((int) el.getDouble("widthRatio"));
                if (el.hasKey("heightRatio")) style.setTextHeightRatio((int) el.getDouble("heightRatio"));
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
                // HumanReadable enum values in 1.0.18: POS_ONE, POS_TWO only
                int readable = el.hasKey("readable") ? el.getInt("readable") : 2;
                if (readable == 1) {
                    style.setReadable(HumanReadable.POS_ONE);
                } else if (readable == 2) {
                    style.setReadable(HumanReadable.POS_TWO);
                }
                // readable == 0: don't call setReadable → no text shown (SDK default)
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
                        .setStyle(Shape.BOX)
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
            promise.reject("NOT_CONNECTED", "Printer not connected. Call initPrinter() first.");
            return false;
        }
        return true;
    }
}