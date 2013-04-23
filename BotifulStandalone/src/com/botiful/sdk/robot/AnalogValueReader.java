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
	/** Threshold set to notify observer */
	private HysteresisComparator mEdgeDetector;
	/** Tells if the observer wants to be notified of one of the HysteresisComparator.EVENT_... constants */
	private int mTypeOfEdgeToDetect;
	
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
		 * This interface method is called when the value has reached the predefined threshold<br />
		 * <b>Delegate the processing to another thread, CPU time here can lead to delayed/lost samples</b>
		 * @param value new analog value in the [0,1] range<br />
		 * @param hysteresisComparatorEvent event that has triggered this callback (one of the {@link #HysteresisComparator} constants).
		 */
		public void onValueReachedThreshold(final float value, final int hysteresisComparatorEvent);
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
		mEdgeDetector = null;
		mTypeOfEdgeToDetect = HysteresisComparator.EVENT_NONE;
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
						if (mEdgeDetector != null && 
								0 != (mTypeOfEdgeToDetect & mEdgeDetector.inputNewValue(mLastValue)) &&
								mObserver!=null) {
							mObserver.onValueReachedThreshold(mLastValue,mEdgeDetector.getLastEvent());
						}
						
					} catch (InterruptedException e) {
						// just warn and loop
						Log.w(this.getClass().getName(),e);
					} catch (ConnectionLostException e) {
						// connection to bot lost -- cancel this thread
						mObserver = null;
						mObserverWantsPeriodicNotifications = false;
						mEdgeDetector = null;
						mTypeOfEdgeToDetect = HysteresisComparator.EVENT_NONE;
						// log
						Log.e(this.getClass().getName(),e.getMessage());
					}
				} // end of while loop

			}
		};
		mReaderThread.setPriority(Thread.MAX_PRIORITY); // should be as close to real-time as possible
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
	 * Subscribe to asynchronous threshold detection alerts<br />
	 * Use {@link #AnalogValueReader.setObserver(AnalogValueObserver) setObserver} to actually start the updates.<br />
	 * If EVENT_NONE is passed, it cancels edge detection.
	 * <ul>
	 * <li>if event to detect is rising edge, the effective comparator thresholds are (t-{@value #HYSTERESIS_PROTECTION_RANGE},t)
	 * <li>if event to detect is falling edge, the effective comparator thresholds are (t,t+{@value #HYSTERESIS_PROTECTION_RANGE})
	 * <li>if both events are required, the effective comparator thresholds are (t-{@value #HYSTERESIS_PROTECTION_RANGE},t+{@value #HYSTERESIS_PROTECTION_RANGE})
	 * </ul>
	 * @param threshold threshold to trigger alerts.
	 * @param edgesToDetect type of events to detect, a combination of the HysteresisComparator events.
	 * @throws IllegalArgumentException if the edges argument is not valid. 
	 */
	public void subscribeToThresholdDetectionUpdates(float threshold, int edgesToDetect) throws IllegalArgumentException {
		if (0 != (edgesToDetect & (HysteresisComparator.EVENT_FALLING_EDGE | HysteresisComparator.EVENT_RISING_EDGE))) {
			// both edges - high state by default (one has to be picked)
			mEdgeDetector = new HysteresisComparator(threshold-HYSTERESIS_PROTECTION_RANGE,
					threshold+HYSTERESIS_PROTECTION_RANGE,
					threshold,
					true);
			mTypeOfEdgeToDetect = edgesToDetect;
		} else if (0 != (edgesToDetect & HysteresisComparator.EVENT_FALLING_EDGE)) {
			// falling edge only - high state by default
			mEdgeDetector = new HysteresisComparator(threshold,
					threshold+HYSTERESIS_PROTECTION_RANGE,
					threshold+HYSTERESIS_PROTECTION_RANGE,
					true);
			mTypeOfEdgeToDetect = edgesToDetect;
		} else if (0 != (edgesToDetect & HysteresisComparator.EVENT_RISING_EDGE)) {
			// rising edge only - low state by default
			mEdgeDetector = new HysteresisComparator(threshold-HYSTERESIS_PROTECTION_RANGE,
					threshold,
					threshold-HYSTERESIS_PROTECTION_RANGE,
					true);
			mTypeOfEdgeToDetect = edgesToDetect;
		} else if (edgesToDetect == HysteresisComparator.EVENT_NONE) {
			// cancel edge detection
			mEdgeDetector = null;
		} else {
			// bad argument
			// cancel edge detection
			mEdgeDetector = null;
			// then throw an exception
			throw new IllegalArgumentException();
		}
		
	}
	
	/**
	 * Removes the observer if any. Resets all observations and stops the reading thread (no more async anything)
	 */
	public void deleteObserver() {
		mObserver = null; // this causes the thread to quit
		mObserverWantsPeriodicNotifications = false;
		mEdgeDetector = null;
		mTypeOfEdgeToDetect = HysteresisComparator.EVENT_NONE;
		try {
			mReaderThread.join();
		} catch (InterruptedException e) {
			// void
		}
	}
	
}
