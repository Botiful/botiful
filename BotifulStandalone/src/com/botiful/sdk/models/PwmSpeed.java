package com.botiful.sdk.models;



/**
 * Model for the speed values (controlling motor commands)<br />
 * Manages the conversion to PWN duty cycle values for IOIO.<br />
 * User-friendly input Values are clipped in a [-10;10] range - zero is neutral<br /><br />
 * The duty cycle scaling parameters are computed through: tau = alpha*log(abs(x))+tau0<br />
 * Alpha can be adjusted, for example to calibrate the rotation spped of the wheels to make sure
 * the robot moves in a straight line when the same command is passed to both wheels.
 */
public class PwmSpeed {
	/** max value (absolute value) for the user friendly scale */
	public static final int MAX = 10;
	// default duty cycle scaling parameters, these values assume PWM_FREQ=50kHz
	public static final float ALPHA_DEFAULT = 4.1f; // [us]
	public static final float TAU0_DEFAULT = 11.0f; // [us]
	
	// members
	/** speed value, user-friendly */
	private int value_;
	/** PWM pulse width in micro-seconds. Computed from the other speed value */
	private float pulseWidth_;
	/** value of alpha */
	private float alpha_ = ALPHA_DEFAULT;
	
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
		value_ = s.value_;
		pulseWidth_ = s.pulseWidth_;
	}
	
	/**
	 * @return true if the speed is positive or null, false else
	 */
	public boolean isPositive() {
		return (value_>=0);
	}
	
	/**
	 * Sets the speed value.
	 * @param value: value between -10 (full speed, reverse) and 10 (full speed). Clipped if out of range.
	 */
	public void setValue(int value) {
		if (value>MAX) {
			value_ = MAX;
		} else if (value<-MAX) {
			value_ = -MAX;
		} else {
			value_ = value;
		}
		pulseWidth_ = computeDutyCyleFromSpeed();
	}
	
	/**
	 * Increase the speed value by one. Has no effect if already at maximum.
	 */
	public void increase() {
		setValue(value_+1);
	}
	
	/**
	 * Decrease the speed value by one. Has no effect if already at minimum.
	 */
	public void decrease() {
		setValue(value_-1);
	}
	
	public int getValue() {
		return value_;
	}
	
	/**
	 * Change the value of the alpha parameter in the duty cycle scaling formula.
	 * Default value is ALPHA_DEFAULT, you can adjust it e.g. to 110% of the default value if you 
	 * want to increase the speed of one wheel to match the rotation speed of the other one.<br />
	 * This method triggers a new computation of the duty cycle.
	 * @param alpha new value to use, e.g. 1.10*ALPHA_DEFAULT
	 */
	public void setAlpha(float alpha) {
		alpha_ = alpha;
		computeDutyCyleFromSpeed();
	}
	
	/**
	 * Computes a scaled value of the duty cycle, fit for a PWM frequency of 50kHz<br />
	 * Values below 11 have no effect on the motor driver. at 50kHz, 20us is the full period (full power)
	 * uses as input the speed value in the 0-10 range
	 * @return the scaled duty cycle period in microseconds, ready to be sent to IOIO
	 */
	private float computeDutyCyleFromSpeed() {
		if (value_ == 0) {
			return 0;
		} else {
			return ((float) (alpha_ * Math.log(Math.abs(value_)) + TAU0_DEFAULT));
		}
	}
	
	/**
	 * Get the pre-computed PWM pulse width in us for the direct (positive) pin.
	 * @return the value to be passed to IOIO
	 */
	public float getPulseWidthForPositivePin() {
		return (value_>0)?pulseWidth_:0;
	}
	
	/**
	 * Get the pre-computed PWM pulse width in us for the reverse (negative) pin.
	 * @return the value to be passed to IOIO
	 */
	public float getPulseWidthForReversePin() {
		return (value_<0)?pulseWidth_:0;
	}
	
	/**
	 * Comparison by value
	 * @param s other speed object to compare
	 * @return true if an only if the <i>value</i> fields match.
	 */
	public boolean equals(PwmSpeed s) {
		return ((s!=null) && (this.value_ == s.value_));
	}
	
}
