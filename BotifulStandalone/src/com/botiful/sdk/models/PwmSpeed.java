package com.botiful.sdk.models;



/**
 * Model for the speed values (controlling motor commands)<br />
 * Manages the conversion to PWN duty cycle values for IOIO.
 * Values are clipped in a [-10;10] range - zero is neutral
 */
public class PwmSpeed {
	public static final int MAX = 10;
	
	// members
	/** speed value, user-friendly */
	private int mValue;
	/** PWM pulse width in micro-seconds. Computed from the other speed value */
	private int mPulseWidth;
	
	/**
	 * Default constructor: sets the speed to 0
	 */
	public PwmSpeed() {
		setValue(0);
	}
	
	/**
	 * Constructor: sets the value. 
	 * @param value: value between -10 (full speed, reverse) and 10 (full speed). Clipped if out of range.
	 */
	public PwmSpeed(int value) {
		setValue(value);
	}

	/**
	 * Copy constructor.
	 */
	public PwmSpeed(PwmSpeed s) {
		mValue = s.mValue;
		mPulseWidth = s.mPulseWidth;
	}
	
	/**
	 * @return true if the speed is positive or null, false else
	 */
	public boolean isPositive() {
		return (mValue>=0);
	}
	
	/**
	 * Sets the speed value.
	 * @param value: value between -10 (full speed, reverse) and 10 (full speed). Clipped if out of range.
	 */
	public void setValue(int value) {
		if (value>MAX) {
			mValue = MAX;
		} else if (value<-MAX) {
			mValue = -MAX;
		} else {
			mValue = value;
		}
		mPulseWidth = scaleDutyCyle(mValue);
	}
	
	public int getValue() {
		return mValue;
	}
	
	/**
	 * Computes a scaled value of the duty cycle, fit for a PWM frequency of 50kHz<br />
	 * Values below 11 have no effect on the motor driver. at 50kHz, 20us is the full period (full power)
	 * @param speed input user-defined speed value in the 0-10 range
	 * @return the scaled duty cycle period in microseconds, ready to be sent to IOIO
	 */
	private static int scaleDutyCyle(int speed) {
		if (speed == 0) {
			return 0;
		} else {
			// the following formula assumes PWM_FREQ=50kHz
			return ((int) (4.1 * Math.log(Math.abs(speed)) + 11.0));
		}
	}
	
	/**
	 * Get the pre-computed PWM pulse width in us (duty cycle) for the direct (positive) pin.
	 * @return the value to be passed to IOIO
	 */
	public int getPulseWidthForPositivePin() {
		return (mValue>0)?mPulseWidth:0;
	}
	
	/**
	 * Get the pre-computed PWM pulse width in us (duty cycle) for the reverse (negative) pin.
	 * @return the value to be passed to IOIO
	 */
	public int getPulseWidthForReversePin() {
		return (mValue<0)?mPulseWidth:0;
	}
	
	/**
	 * Comparison by value
	 * @param s other speed object to compare
	 * @return true if an only if the <i>value</i> fields match.
	 */
	public boolean equals(PwmSpeed s) {
		return ((s!=null) && (this.mValue == s.mValue));
	}
	
}
