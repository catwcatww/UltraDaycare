package com.kingpixel.ultradaycare.config;

/**
 * Defines the method used for hatching eggs within the Incubators module.
 *
 * @author Carlos Varas Alonso
 */
public enum IncubatorHatchMode {
  /**
   * Eggs hatch purely by elapsed active play time.
   */
  TIME,

  /**
   * Eggs hatch purely by number of walked steps.
   */
  STEPS,

  /**
   * Eggs hatch by either elapsed play time OR walked steps (whichever condition is met first).
   */
  TIME_OR_STEPS,

  /**
   * Eggs require both elapsed play time AND walked steps to be fulfilled.
   */
  TIME_AND_STEPS
}
