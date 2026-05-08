import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-sunmi-label-printer' doesn't seem to be linked.\n` +
  Platform.select({ ios: "• Run `pod install`\n", default: '' }) +
  '• Rebuild the app after installing the package\n';

const Native = NativeModules.SunmiLabelPrinter
  ? NativeModules.SunmiLabelPrinter
  : new Proxy({}, { get() { throw new Error(LINKING_ERROR); } });

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

/** Text element — pixel-positioned on the canvas */
export interface TextElement {
  type: 'text';
  text: string;
  /** X position in pixels (1mm = 8px) */
  x: number;
  /** Y position in pixels */
  y: number;
  /** Max width for text wrapping */
  width?: number;
  height?: number;
  /** Font size in pixels (default 24) */
  fontSize?: number;
  bold?: boolean;
  italic?: boolean;
  underline?: boolean;
  widthRatio?: number;
  heightRatio?: number;
}

/** Barcode element */
export interface BarcodeElement {
  type: 'barcode';
  data: string;
  x: number;
  y: number;
  width?: number;
  height?: number;
  /** Bar module width (default 2) */
  dotWidth?: number;
  /** Bar height in pixels (default 60) */
  barHeight?: number;
  /** Human readable: 0=none, 1=above, 2=below (default) */
  readable?: 0 | 1 | 2;
}

/** QR Code element */
export interface QRCodeElement {
  type: 'qrcode';
  data: string;
  x: number;
  y: number;
  width?: number;
  height?: number;
  /** Module size (default 4) */
  dot?: number;
}

/** Bitmap/image element from base64 */
export interface ImageElement {
  type: 'image';
  /** Base64 encoded PNG or JPEG */
  imageBase64: string;
  x: number;
  y: number;
  width?: number;
  height?: number;
  /** Use dithering algorithm for better photo quality (default false = binarization) */
  dithering?: boolean;
}

/** Box/border rectangle element */
export interface BoxElement {
  type: 'box';
  x: number;
  y: number;
  width: number;
  height: number;
}

export type LabelElement =
  | TextElement
  | BarcodeElement
  | QRCodeElement
  | ImageElement
  | BoxElement;

/**
 * Label canvas configuration.
 *
 * Size guide (1mm = 8px):
 *   30×20mm → width:240, height:160
 *   48×30mm → width:384, height:240
 *   50×25mm → width:400, height:200
 */
export interface LabelConfig {
  /** Canvas width in pixels */
  width: number;
  /** Canvas height in pixels */
  height: number;
  /** Number of copies (default 1) */
  copies?: number;
  /** Elements to render on the canvas */
  elements: LabelElement[];
}

export interface PrinterInfo {
  model: string;
  name: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Module API
// ─────────────────────────────────────────────────────────────────────────────

const SunmiLabelPrinter = {

  // ── Connection ─────────────────────────────────────────────────────────────

  /** Bind to PrinterX SDK. Call once on app start. */
  initPrinter(): Promise<boolean> {
    return Native.initPrinter();
  },

  /** Release SDK resources. Call on app unmount. */
  deInitPrinter(): Promise<boolean> {
    return Native.deInitPrinter();
  },

  isConnected(): Promise<boolean> {
    return Native.isConnected();
  },

  getPrinterInfo(): Promise<PrinterInfo> {
    return Native.getPrinterInfo();
  },

  // ── Label Printing ─────────────────────────────────────────────────────────

  /**
   * Print a single label using the Canvas API.
   *
   * @example
   * // 48×30mm label (384×240 px)
   * await SunmiLabelPrinter.printLabel({
   *   width: 384, height: 240, copies: 1,
   *   elements: [
   *     { type: 'box',     x: 0,   y: 0,   width: 384, height: 239 },
   *     { type: 'text',    text: 'Widget Pro', x: 10, y: 10, fontSize: 30, bold: true },
   *     { type: 'text',    text: 'RM 49.90',   x: 10, y: 50, fontSize: 24 },
   *     { type: 'barcode', data: '9781234567897', x: 10,  y: 90,  width: 180, barHeight: 60 },
   *     { type: 'qrcode',  data: 'https://sunmi.com', x: 260, y: 60, width: 110, height: 110, dot: 3 },
   *   ],
   * });
   */
  printLabel(config: LabelConfig): Promise<boolean> {
    return Native.printLabel(config);
  },

  /**
   * Print multiple different labels in sequence.
   *
   * @example
   * await SunmiLabelPrinter.printLabels([
   *   { width: 384, height: 240, elements: [...] },
   *   { width: 384, height: 240, elements: [...] },
   * ]);
   */
  printLabels(configs: LabelConfig[]): Promise<boolean> {
    return Native.printLabels(configs);
  },
};

export default SunmiLabelPrinter;