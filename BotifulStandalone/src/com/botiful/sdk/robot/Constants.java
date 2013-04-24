package com.botiful.sdk.robot;

/**
 * Pin numbers and other constants
 */
public class Constants {
	// various hardware settings
	public static final int PWM_FREQUENCY = 50000;
	
	// pin numbers
	public static final int LEFT_WHEEL_REVERSE_PWM_OUTPUT_PIN = 10;
	public static final int LEFT_WHEEL_POSITIVE_PWM_OUTPUT_PIN = 11;
	public static final int RIGHT_WHEEL_REVERSE_PWM_OUTPUT_PIN = 12;
	public static final int RIGHT_WHEEL_POSITIVE_PWM_OUTPUT_PIN = 13;

	public static final int PERIPHERAL_CIRCUIT_DIGITAL_OUTPUT_PIN = 20;

	public static final int WHEELS_MOTOR_DRIVERS_SLEEP_MODE_PIN = 29;
	public static final int HEAD_MOTOR_DRIVERS_SLEEP_MODE_PIN = 30;
	
	public static final int HEAD_REVERSE_PWM_OUTPUT_PIN = 39;
	public static final int HEAD_POSITIVE_PWM_OUTPUT_PIN = 40;
	
	public static final int ROTARY_ENCODER_ANALOG_INPUT_PIN = 45;
	
	// other constants
	/** max value of the rotary encoder (minimum angle for the head) */
	public static final float ROTARY_ENCODER_MAX_VALUE = .49f;
	/** min value of the rotary encoder (maximum angle for the head) */
	public static final float ROTARY_ENCODER_MIN_VALUE = .26f;
}
