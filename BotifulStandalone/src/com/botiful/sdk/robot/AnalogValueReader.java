package com.botiful.sdk.robot;

import ioio.lib.api.AnalogInput;
import ioio.lib.api.IOIO;
import ioio.lib.api.exception.ConnectionLostException;
import android.os.SystemClock;
import android.util.Log;

/**
 * A class to manage analog inputs.<br />
 * It provides asynchronous updates to a subscribing AnalogValueObserver for either:
 * - raw values: careful with these the output rate is 1kHz
 * - alerts when reaching a predefined threshold
 */
public class AnalogValueReader extends AbstractRoboticElement {
	/** Threshold to protect from abusive above/under threshold notifications 
	 * @value {@value #HYSTERESIS_PROTECTION_RANGE} */
	private static final float HYSTERESIS_PROTECTION_RANGE = 0.01f;
	
	private AnalogInput mAnalogInput;
	/** Observer notified of new values or alerts. Also used as control variable for the thread */
	private AnalogValueObserver mObserver;
	/** Last value observed if any, or NaN */
	private float mLastValue;
	/** a thread to wait for incoming value and store them */
	private Thread mReaderThread;
	// two kinds of notifications: regular value updates or threshold detection
	/** minimum update period to notify the observer in milliseconds */
	private long mNotificationPeriodMillis;
	private boolean mObserverWantsPeriodicNotifications;
	/** Threshold detectors */
	private HysteresisComparator mRisingEdgeDetector,mFallingEdgeDetector;
	
	/**
	 * A custom interface to specify entry point for asynchronous updates
	 */
	public interface AnalogValueObserver {
		/**
		 * This interface method is called when a new value is available<br />
		 * Warning: the output rate can be very fast (1 kHz)<br />
		 * <b>Delegate the processing to another thread, CPU time here can lead to delayed/lost samples</b>
		 * @param value new value in the [0,1] range
		 */
		public void onNewValue(final float value);
		
		/**
		 * This interface method is called when the value gets above the predefined threshold<br />
		 * <b>Delegate the processing to another thread, CPU time here can lead to delayed/lost samples</b>
		 * @param value new analog value in the [0,1] range<br />
		 */
		public void onValueAlertAboveThreshold(float value);

		/**
		 * This interface method is called when the value gets below the predefined threshold<br />
		 * <b>Delegate the processing to another thread, CPU time here can lead to delayed/lost samples</b>
		 * @param value new analog value in the [0,1] range<br />
		 */
		public void onValueAlertBelowThreshold(float value);
	}

	/**
	 * Build the analog input
	 * @param ioio handle to the ioio interface
	 * @param pin index number of the analog input pin
	 * @throws ConnectionLostException when connection to the robot is lost
	 */
	public AnalogValueReader(IOIO ioio, int pin) throws ConnectionLostException {
		super(ioio);
		
		mAnalogInput = mIOIO.openAnalogInput(pin);
		mAnalogInput.setBuffer(8); // we don't need a large buffer, 8 samples is more that enough
		mObserver = null;
		mLastValue = Float.NaN;
		mObserverWantsPeriodicNotifications = false;
		mRisingEdgeDetector = null;
		mFallingEdgeDetector = null;
		mReaderThread = null;
		
	}
	
	private void createAndStartReaderThread() {
		mReaderThread = new Thread() {
			@Override
			public void run() {
				long lastMotificationTimestampMillis = 0;
				while (mObserver!=null) {
					try {
						// get the current value (blocks until data is available)
						mLastValue=mAnalogInput.readBuffered();
						
						// notify of new values if needed, record the date
						long timestamp = SystemClock.elapsedRealtime();
						if (mObserverWantsPeriodicNotifications &&
								(timestamp-lastMotificationTimestampMillis>mNotificationPeriodMillis)) {
							if (mObserver!=null) {
								mObserver.onNewValue(mLastValue);
							}
							lastMotificationTimestampMillis = timestamp;
						}
						
						// notify of threshold detection
						if (mRisingEdgeDetector != null && 
								HysteresisComparator.EVENT_RISING_EDGE == mRisingEdgeDetector.inputNewValue(mLastValue) &&
								mObserver!=null) {
							mObserver.onValueAlertAboveThreshold(mLastValue);
						}
						if (mFallingEdgeDetector != null && 
								HysteresisComparator.EVENT_FALLING_EDGE == mFallingEdgeDetector.inputNewValue(mLastValue) &&
								mObserver!=null) {
							mObserver.onValueAlertBelowThreshold(mLastValue);
						}
						
					} catch (InterruptedException e) {
						// just warn and loop
						Log.w(this.getClass().getName(),e);
					} catch (ConnectionLostException e) {
						// connection to bot lost -- cancel this thread
						mObserver = null;
						mObserverWantsPeriodicNotifications = false;
						mRisingEdgeDetector = null;
						mFallingEdgeDetector = null;
						// log
						Log.e(this.getClass().getName(),e.getMessage());
					}
				} // end of while loop

			}
		};
		mReaderThread.setPriority(8); // should be as close to real-time as possible, but we don't want to freeze the UI either
		mReaderThread.start();
	}
	
	/**
	 * Read the value available at the pin in [0,1]<br />
	 * Note that if no observer has been set at any time prior to this call, 
	 * the value will not be available.
	 * @return the latest analog value read, in the [0,1] range, or Float.Nan if not available
	 */
	public float getLastValue() {
		return mLastValue;
	}
	
	/**
	 * Set the observer for async value updates and threshold detection alerts, start the reader thread.<br />
	 * If the argument is null, has no effect (use {@link #deleteObserver} to cancel updates)
	 * @param observer the AnalogValueObserver to add
	 */
	public void setObserver(AnalogValueObserver observer) {
		mObserver = observer;
		if (observer==null) {
			return;
		}
		if (mReaderThread==null) {
			// start the treader thread, be prepared for observations.
			createAndStartReaderThread();
		}
	}
	
	/**
	 * Subscribe to asynchronous value updates.<br />
	 * Use {@link #AnalogValueReader.setObserver(AnalogValueObserver) setObserver} to actually start the updates.
	 * @param updatePeriodMillis minimum update period in milliseconds
	 */
	public void subscribeToValuesUpdates(int updatePeriodMillis) {
		mNotificationPeriodMillis = Math.max(0, updatePeriodMillis);
		mObserverWantsPeriodicNotifications = true;
	}

	
	/**
	 * Subscribe to asynchronous rising edge threshold detection (detects if a value goes above a certain threshold)<br />
	 * Use {@link #AnalogValueReader.setObserver(AnalogValueObserver) setObserver} to actually start the updates.<br />
	 * If threshold passed if Float.NaN, it cancels edge detection.
	 * @param threshold threshold to trigger alerts.
	 */
	public void subscribeToRisingEdgeThresholdDetection(float threshold) {
		if (Float.isNaN(threshold)) {
			mRisingEdgeDetector = null;
		} else {
			mRisingEdgeDetector = new HysteresisComparator(threshold-HYSTERESIS_PROTECTION_RANGE,
					threshold,
					false);
		}
	}
	
	/**
	 * Subscribe to asynchronous falling edge threshold detection (detects if a value goes below a certain threshold)<br />
	 * Use {@link #AnalogValueReader.setObserver(AnalogValueObserver) setObserver} to actually start the updates.<br />
	 * If threshold passed if Float.NaN, it cancels edge detection.
	 * @param threshold threshold to trigger alerts.
	 */
	public void subscribeToFallingEdgeThresholdDetection(float threshold) {
		if (Float.isNaN(threshold)) {
			mFallingEdgeDetector = null;
		} else {
			mFallingEdgeDetector = new HysteresisComparator(threshold,
					threshold+HYSTERESIS_PROTECTION_RANGE,
					true);
		}
	}
	
	/**
	 * Removes the observer if any. Resets all observations and stops the reading thread (no more async anything)
	 */
	public void deleteObserver() {
		mObserver = null; // this causes the thread to quit
		mObserverWantsPeriodicNotifications = false;
		mRisingEdgeDetector = null;
		mFallingEdgeDetector = null;
		try {
			mReaderThread.join();
		} catch (InterruptedException e) {
			// void
		}
	}
	
}
