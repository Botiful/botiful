package com.botiful.sdk.robot;

import ioio.lib.api.DigitalOutput;
import ioio.lib.api.IOIO;
import ioio.lib.api.exception.ConnectionLostException;

/**
 * Basic switch - can be turned on or off<br />
 * Wraps a digital output<br />
 * WARNING: the inner state is the OPPOSITE of the digital output value!
 */
public class Switch extends AbstractRoboticElement {
	private boolean mState;
	private DigitalOutput mDigitalOutput;
	
	public Switch(IOIO ioio, int digitalOutputPin, boolean initialState) throws ConnectionLostException {
		super(ioio);
		mState = initialState;
		mDigitalOutput = ioio.openDigitalOutput(digitalOutputPin, !initialState);
	}
	
	/**
	 * Requires the digital output to change state. has no effect if this state is already set.<br />
	 * If the command fails, the inner state is not changed.
	 * @param state new value.
	 */
	public void set(boolean state) throws ConnectionLostException {
		if (state != mState) {
			mDigitalOutput.write(!state);
			mState = state;
		}
	}

	/**
	 * Requires the digital output to change state. Sends the command even if the state is already set.<br />
	 * If the command fails, the inner state is not changed.
	 * @param state new value.
	 */
	public void forceSet(boolean state) throws ConnectionLostException {
		mDigitalOutput.write(!state);
		mState = state;
	}

}
