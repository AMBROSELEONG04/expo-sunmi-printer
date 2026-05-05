package com.exposunmiprinter

import com.facebook.react.bridge.ReactApplicationContext

class ExpoSunmiPrinterModule(reactContext: ReactApplicationContext) :
  NativeExpoSunmiPrinterSpec(reactContext) {

  override fun multiply(a: Double, b: Double): Double {
    return a * b
  }

  companion object {
    const val NAME = NativeExpoSunmiPrinterSpec.NAME
  }
}
