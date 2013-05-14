package com.botiful.sdk.robot;

import ioio.lib.api.IOIO;

/**
 * Abstract class to be implemented by all parts of the robot.
 */
public abstract class AbstractRoboticElement {
	/** reference to the target IOIO */
	protected IOIO IOIO_;
	
	/**
	 * Constructor
	 * @param ioio handle to the IOIO object used to communicate with the physical robot
	 */
	protected AbstractRoboticElement(IOIO ioio) {
		IOIO_ = ioio;
	}
}
