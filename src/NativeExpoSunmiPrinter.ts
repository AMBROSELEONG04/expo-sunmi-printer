import { TurboModuleRegistry, type TurboModule } from 'react-native';
import type { UnsafeObject } from 'react-native/Libraries/Types/CodegenTypes';

/**
 * TurboModule spec for SunmiLabelPrinter.
 * Defines the native interface boundary.
 */
export interface Spec extends TurboModule {
  // Connection
  initPrinter(): Promise<boolean>;
  deInitPrinter(): Promise<boolean>;
  isConnected(): Promise<boolean>;

  // Status & Info
  getPrinterStatus(): Promise<UnsafeObject>;
  getPrinterInfo(): Promise<UnsafeObject>;

  // Label positioning (low-level)
  labelLocate(): Promise<boolean>;
  labelOutput(): Promise<boolean>;

  // Text
  setAlignment(alignment: number): Promise<boolean>;
  setFontSize(size: number): Promise<boolean>;
  setBold(bold: boolean): Promise<boolean>;
  printText(text: string): Promise<boolean>;
  printTextWithFont(text: string, typeFace: string, fontSize: number): Promise<boolean>;
  printColumnsText(texts: string[], widths: number[], alignments: number[]): Promise<boolean>;

  // Barcode & QR
  printBarCode(data: string, symbology: number, height: number, width: number, textPosition: number): Promise<boolean>;
  printQRCode(data: string, moduleSize: number, errorLevel: number): Promise<boolean>;

  // Image
  printBitmapBase64(base64Image: string, type: number): Promise<boolean>;

  // High-level label API
  printLabel(labelConfig: UnsafeObject): Promise<boolean>;
  printLabels(labelsConfig: UnsafeObject[]): Promise<boolean>;

  // Utility
  feedPaper(lines: number): Promise<boolean>;
  printerSelfChecking(): Promise<boolean>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('SunmiLabelPrinter');