package com.botiful.sdk.robot;

import ioio.lib.api.IOIO;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;

import com.botiful.sdk.models.PwmSpeed;

/**
 * Class to manage PWN-based motors
 */
public class PwmMotor extends AbstractRoboticElement {
	private PwmOutput pwmPositiveOutput_;
	private PwmOutput pwmReverseOutput_;
	private PwmSpeed pwmSpeed_; 
	
	/**
	 * Build the PwmMotor objects and associates it to the target PWM output pins
	 * @param ioio handle to the ioio interface
	 * @param positivePwmOutputPin index number of the PWM output pin for positive values (use one of the constants)
	 * @param reversePwmOutputPin index number of the PWM output pin for negative values (use one of the constants)
	 * @throws ConnectionLostException when connection to the robot is lost
	 */
	public PwmMotor(IOIO ioio, int positivePwmOutputPin, int reversePwmOutputPin) throws ConnectionLostException {
		super(ioio);
		
		pwmSpeed_ = new PwmSpeed();
		pwmPositiveOutput_ = ioio.openPwmOutput(positivePwmOutputPin, Constants.PWM_FREQUENCY);
		pwmReverseOutput_ = ioio.openPwmOutput(reversePwmOutputPin, Constants.PWM_FREQUENCY);
	}
	
	/**
	 * Changes the speed of the motor. Has no effect if the speed was already applied.<br />
	 * Call this method from inside the IOIO looper's loop;
	 * @param newSpeed speed command to process
	 * @throws ConnectionLostException when connection to the robot is lost
	 */
	public void setSpeed(PwmSpeed newSpeed) throws ConnectionLostException {
		if (newSpeed!=null && !pwmSpeed_.equals(newSpeed)) {
			pwmSpeed_ = newSpeed;
			pwmPositiveOutput_.setPulseWidth(pwmSpeed_.getPulseWidthForPositivePin());
			pwmReverseOutput_.setPulseWidth(pwmSpeed_.getPulseWidthForReversePin());
		}
	}
	
	/**
	 * Stops the motor (applies a null speed)
	 * @throws ConnectionLostException when connection to the robot is lost
	 */
	public void stop() throws ConnectionLostException {
		setSpeed(new PwmSpeed(0));
	}
	
	public PwmSpeed getSpeed() {
		return pwmSpeed_;
	}

}
