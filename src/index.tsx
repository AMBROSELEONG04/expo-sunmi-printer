import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-sunmi-label-printer' doesn't seem to be linked. Make sure to:\n\n` +
  Platform.select({ ios: "• Run `pod install`\n", default: '' }) +
  '• Rebuild the app after installing the package\n\n';

const SunmiLabelPrinterNative = NativeModules.SunmiLabelPrinter
  ? NativeModules.SunmiLabelPrinter
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

export type Alignment = 0 | 1 | 2; // 0=left, 1=center, 2=right

/** Barcode symbology types */
export type BarcodeSymbology =
  | 0  // UPC-A
  | 1  // UPC-E
  | 2  // EAN-13
  | 3  // EAN-8
  | 4  // CODE39
  | 5  // ITF
  | 6  // CODABAR
  | 7  // CODE93
  | 8; // CODE128 (recommended default)

/** QR code error correction levels */
export type QRErrorLevel =
  | 0  // L – 7% correction
  | 1  // M – 15% correction (default)
  | 2  // Q – 25% correction
  | 3; // H – 30% correction

/** A text line on a label */
export interface TextLine {
  type: 'text';
  text: string;
  /** Font size (default 24) */
  fontSize?: number;
  /** Text alignment (default 0 = left) */
  alignment?: Alignment;
  /** Bold text */
  bold?: boolean;
}

/** A barcode line on a label */
export interface BarcodeLine {
  type: 'barcode';
  barcodeData: string;
  /** Symbology (default 8 = CODE128) */
  barcodeSymbology?: BarcodeSymbology;
  /** Height in dots, 1–255 (default 80) */
  barcodeHeight?: number;
  /** Width multiplier 2–6 (default 2) */
  barcodeWidth?: number;
  /** Text position: 0=none, 1=above, 2=below (default), 3=both */
  barcodeTextPos?: 0 | 1 | 2 | 3;
  alignment?: Alignment;
}

/** A QR code line on a label */
export interface QRCodeLine {
  type: 'qrcode';
  qrData: string;
  /** Module size 4–16 (default 8) */
  qrModuleSize?: number;
  /** Error level 0–3 (default 1 = M) */
  qrErrorLevel?: QRErrorLevel;
  alignment?: Alignment;
}

/** A base64 image line on a label */
export interface ImageLine {
  type: 'image';
  /** Base64 encoded PNG or JPEG */
  imageBase64: string;
  alignment?: Alignment;
}

/** Blank lines (vertical spacing) */
export interface BlankLine {
  type: 'blank';
  /** Number of blank lines (default 1) */
  height?: number;
}

export type LabelLine = TextLine | BarcodeLine | QRCodeLine | ImageLine | BlankLine;

/** Full label configuration */
export interface LabelConfig {
  lines: LabelLine[];
  /** Number of copies to print (default 1) */
  copies?: number;
}

export interface PrinterStatus {
  code: number;
  message: string;
}

export interface PrinterInfo {
  serialNo: string;
  model: string;
  firmwareVersion: string;
  serviceVersion: string;
  /** 1 = 58mm, 2 = 80mm */
  paperWidth: number;
}

// ─────────────────────────────────────────────────────────────────────────────
// Module API
// ─────────────────────────────────────────────────────────────────────────────

const SunmiLabelPrinter = {

  // ── Connection ─────────────────────────────────────────────────────────────

  /**
   * Bind to Sunmi printer service.
   * Must be called once before any printing, typically in a useEffect/componentDidMount.
   */
  initPrinter(): Promise<boolean> {
    return SunmiLabelPrinterNative.initPrinter();
  },

  /**
   * Unbind the printer service. Call in cleanup (componentWillUnmount / useEffect cleanup).
   */
  deInitPrinter(): Promise<boolean> {
    return SunmiLabelPrinterNative.deInitPrinter();
  },

  /**
   * Returns true if the printer service is currently connected.
   */
  isConnected(): Promise<boolean> {
    return SunmiLabelPrinterNative.isConnected();
  },

  // ── Status & Info ──────────────────────────────────────────────────────────

  /**
   * Get current printer status code and human-readable message.
   *   1 = Running normally
   *   4 = Out of paper
   *   5 = Overheating
   *   6 = Cover open
   * 505 = No printer found
   */
  getPrinterStatus(): Promise<PrinterStatus> {
    return SunmiLabelPrinterNative.getPrinterStatus();
  },

  /**
   * Get device and printer information (model, firmware, serial number, paper width).
   */
  getPrinterInfo(): Promise<PrinterInfo> {
    return SunmiLabelPrinterNative.getPrinterInfo();
  },

  // ── Label Positioning (Low-level) ──────────────────────────────────────────

  /**
   * Locate to the next label gap.
   * Must be called before printing each label's content when using low-level APIs.
   */
  labelLocate(): Promise<boolean> {
    return SunmiLabelPrinterNative.labelLocate();
  },

  /**
   * Push label to paper cut/peel position.
   * Call after all content for the current batch is sent.
   */
  labelOutput(): Promise<boolean> {
    return SunmiLabelPrinterNative.labelOutput();
  },

  // ── Text Controls (Low-level) ──────────────────────────────────────────────

  setAlignment(alignment: Alignment): Promise<boolean> {
    return SunmiLabelPrinterNative.setAlignment(alignment);
  },

  setFontSize(size: number): Promise<boolean> {
    return SunmiLabelPrinterNative.setFontSize(size);
  },

  setBold(bold: boolean): Promise<boolean> {
    return SunmiLabelPrinterNative.setBold(bold);
  },

  /** Print text. Append '\n' to flush the line immediately. */
  printText(text: string): Promise<boolean> {
    return SunmiLabelPrinterNative.printText(text);
  },

  printTextWithFont(text: string, typeFace: string, fontSize: number): Promise<boolean> {
    return SunmiLabelPrinterNative.printTextWithFont(text, typeFace, fontSize);
  },

  printColumnsText(
    texts: string[],
    widths: number[],
    alignments: Alignment[]
  ): Promise<boolean> {
    return SunmiLabelPrinterNative.printColumnsText(texts, widths, alignments);
  },

  // ── Barcode & QR (Low-level) ───────────────────────────────────────────────

  printBarCode(
    data: string,
    symbology: BarcodeSymbology = 8,
    height: number = 80,
    width: number = 2,
    textPosition: 0 | 1 | 2 | 3 = 2
  ): Promise<boolean> {
    return SunmiLabelPrinterNative.printBarCode(data, symbology, height, width, textPosition);
  },

  printQRCode(
    data: string,
    moduleSize: number = 8,
    errorLevel: QRErrorLevel = 1
  ): Promise<boolean> {
    return SunmiLabelPrinterNative.printQRCode(data, moduleSize, errorLevel);
  },

  // ── Image (Low-level) ─────────────────────────────────────────────────────

  /**
   * Print a bitmap from a Base64 encoded string.
   * type: 0=normal, 1=double width, 2=double height, 3=double both
   */
  printBitmapBase64(base64Image: string, type: 0 | 1 | 2 | 3 = 0): Promise<boolean> {
    return SunmiLabelPrinterNative.printBitmapBase64(base64Image, type);
  },

  // ── Utility ────────────────────────────────────────────────────────────────

  feedPaper(lines: number = 1): Promise<boolean> {
    return SunmiLabelPrinterNative.feedPaper(lines);
  },

  printerSelfChecking(): Promise<boolean> {
    return SunmiLabelPrinterNative.printerSelfChecking();
  },

  // ── High-level Label API (Recommended) ────────────────────────────────────

  /**
   * Print a single complete label.
   *
   * This handles the full flow automatically:
   *   labelLocate → content lines → labelOutput
   *
   * @example
   * await SunmiLabelPrinter.printLabel({
   *   copies: 1,
   *   lines: [
   *     { type: 'text', text: 'Product Name', fontSize: 28, alignment: 1 },
   *     { type: 'text', text: 'SKU: ABC-001', fontSize: 20 },
   *     { type: 'blank', height: 1 },
   *     { type: 'barcode', barcodeData: '1234567890', barcodeHeight: 80 },
   *     { type: 'blank', height: 1 },
   *     { type: 'qrcode', qrData: 'https://example.com', qrModuleSize: 8 },
   *   ],
   * });
   */
  printLabel(labelConfig: LabelConfig): Promise<boolean> {
    return SunmiLabelPrinterNative.printLabel(labelConfig);
  },

  /**
   * Print multiple different labels in one efficient batch.
   * Internally: labelLocate→content1 → labelLocate→content2 … → labelOutput
   *
   * @example
   * await SunmiLabelPrinter.printLabels([
   *   { lines: [{ type: 'text', text: 'Label A' }] },
   *   { lines: [{ type: 'text', text: 'Label B' }] },
   * ]);
   */
  printLabels(labelsConfig: LabelConfig[]): Promise<boolean> {
    return SunmiLabelPrinterNative.printLabels(labelsConfig);
  },
};

export default SunmiLabelPrinter;