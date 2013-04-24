package com.botiful.sdk.robot;

/**
 * Comparator with state and two thresholds.<br />
 * This class helps manipulating detection of rising/falling edges.
 */
public class HysteresisComparator {
	// types of events reported
	public static final int EVENT_NONE=0;
	public static final int EVENT_RISING_EDGE=1;
	public static final int EVENT_FALLING_EDGE=2;
	
	private float mLowThreshold;
	private float mHighThreshold;
	private boolean mIsStateHigh;
	private int mLastEvent;
	
	/**
	 * Instantiate the comparator, initializes all values<br />
	 * @param lowThreshold lower threshold (to detect falling edge events)
	 * @param highThreshold higher threshold (to detect rising edge events)
	 * @param defaultState default state to use (true<=>high state)
	 */
	public HysteresisComparator(float lowThreshold,float highThreshold, boolean defaultState) {
		mLowThreshold =  lowThreshold;
		mHighThreshold = highThreshold;
		mIsStateHigh = defaultState;
	}
	
	/**
	 * Returns the last comparator event triggered if any
	 * @return the latest comparator event triggered (one of the EVENT_... constants)
	 */
	public int getLastEvent() {
		return mLastEvent;
	}
	
	/**
	 * @return the current state of the comparator (high=true, low=false)
	 */
	public boolean getState() {
		return mIsStateHigh;
	}

	/**
	 * Updates the state of the comparator, sets the last event triggered if any and return it<br /> 
	 * <ul><li>A high state can be turned into a low stated with a value below or equal to the lower threshold
	 * <li>A low state can be turned into a high stated with a value above or equal to the higher threshold</ul>
	 * If the new value does not change the state, the event recorded and returned is reset to NONE.
	 * 
	 * @param newValue input value
	 * @return a comparator event constant (one of the EVENT_... constants) if one has just triggered by the new value
	 */
	public int inputNewValue(float newValue) {
		mLastEvent = EVENT_NONE;
		if (mIsStateHigh) {
			if (newValue <= mLowThreshold) {
				mIsStateHigh = false;
				mLastEvent = EVENT_FALLING_EDGE;
			}
		} else {
			if (newValue >= mHighThreshold) {
				mIsStateHigh = true;
				mLastEvent = EVENT_RISING_EDGE;
			}
		}
		
		return mLastEvent;
	}
	
}
