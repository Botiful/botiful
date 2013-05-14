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
	/**
	 * Threshold to protect from abusive above/under threshold notifications.
	 * It prevents multiple detections when the analog value is slightly oscillating
	 * around its value. 
	 * @value {@value #HYSTERESIS_PROTECTION_RANGE} */
	private static final float HYSTERESIS_PROTECTION_RANGE = 0.01f;
	
	private AnalogInput analogInput_;
	/** Observer notified of new values or alerts. Also used as control variable for the thread */
	private AnalogValueObserver observer_;
	/** Last value observed if any, or NaN */
	private float lastValue_;
	/** a thread to wait for incoming value and store them */
	private Thread readerThread_;
	// two kinds of notifications: regular value updates or threshold detection
	/** minimum update period to notify the observer in milliseconds */
	private long notificationPeriodMillis_;
	private boolean observerWantsPeriodicNotifications_;
	/** Threshold detectors */
	private HysteresisComparator risingEdgeDetector_,fallingEdgeDetector_;
	
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
		
		analogInput_ = IOIO_.openAnalogInput(pin);
		analogInput_.setBuffer(8); // we don't need a large buffer, 8 samples is more that enough
		observer_ = null;
		lastValue_ = Float.NaN;
		observerWantsPeriodicNotifications_ = false;
		risingEdgeDetector_ = null;
		fallingEdgeDetector_ = null;
		readerThread_ = null;
		
	}
	
	private void createAndStartReaderThread() {
		readerThread_ = new Thread() {
			@Override
			public void run() {
				long lastMotificationTimestampMillis = 0;
				while (observer_!=null) {
					try {
						// get the current value (blocks until data is available)
						lastValue_=analogInput_.readBuffered();
						
						// notify of new values if needed, record the date
						long timestamp = SystemClock.elapsedRealtime();
						if (observerWantsPeriodicNotifications_ &&
								(timestamp-lastMotificationTimestampMillis>notificationPeriodMillis_)) {
							if (observer_!=null) {
								observer_.onNewValue(lastValue_);
							}
							lastMotificationTimestampMillis = timestamp;
						}
						
						// notify of threshold detection
						if (risingEdgeDetector_ != null && 
								HysteresisComparator.EVENT_RISING_EDGE == risingEdgeDetector_.inputNewValue(lastValue_) &&
								observer_!=null) {
							observer_.onValueAlertAboveThreshold(lastValue_);
						}
						if (fallingEdgeDetector_ != null && 
								HysteresisComparator.EVENT_FALLING_EDGE == fallingEdgeDetector_.inputNewValue(lastValue_) &&
								observer_!=null) {
							observer_.onValueAlertBelowThreshold(lastValue_);
						}
						
					} catch (InterruptedException e) {
						// just warn and loop
						Log.w(this.getClass().getName(),e);
					} catch (ConnectionLostException e) {
						// connection to bot lost -- cancel this thread
						observer_ = null;
						observerWantsPeriodicNotifications_ = false;
						risingEdgeDetector_ = null;
						fallingEdgeDetector_ = null;
						// log
						Log.e(this.getClass().getName(),e.getMessage());
					}
				} // end of while loop

			}
		};
		readerThread_.setPriority(8); // should be as close to real-time as possible, but we don't want to freeze the UI either
		readerThread_.start();
	}
	
	/**
	 * Read the value available at the pin in [0,1]<br />
	 * Note that if no observer has been set at any time prior to this call, 
	 * the value will not be available.
	 * @return the latest analog value read, in the [0,1] range, or Float.Nan if not available
	 */
	public float getLastValue() {
		return lastValue_;
	}
	
	/**
	 * Set the observer for async value updates and threshold detection alerts, start the reader thread.<br />
	 * If the argument is null, has no effect (use {@link #deleteObserver} to cancel updates)
	 * @param observer the AnalogValueObserver to add
	 */
	public void setObserver(AnalogValueObserver observer) {
		observer_ = observer;
		if (observer==null) {
			return;
		}
		if (readerThread_==null) {
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
		notificationPeriodMillis_ = Math.max(0, updatePeriodMillis);
		observerWantsPeriodicNotifications_ = true;
	}

	
	/**
	 * Subscribe to asynchronous rising edge threshold detection (detects if a value goes above a certain threshold)<br />
	 * Use {@link #AnalogValueReader.setObserver(AnalogValueObserver) setObserver} to actually start the updates.<br />
	 * If threshold passed if Float.NaN, it cancels edge detection.
	 * @param threshold threshold to trigger alerts.
	 */
	public void subscribeToRisingEdgeThresholdDetection(float threshold) {
		if (Float.isNaN(threshold)) {
			risingEdgeDetector_ = null;
		} else {
			risingEdgeDetector_ = new HysteresisComparator(threshold-HYSTERESIS_PROTECTION_RANGE,
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
			fallingEdgeDetector_ = null;
		} else {
			fallingEdgeDetector_ = new HysteresisComparator(threshold,
					threshold+HYSTERESIS_PROTECTION_RANGE,
					true);
		}
	}
	
	/**
	 * Removes the observer if any. Resets all observations and stops the reading thread (no more async anything)
	 */
	public void deleteObserver() {
		observer_ = null; // this causes the thread to quit
		observerWantsPeriodicNotifications_ = false;
		risingEdgeDetector_ = null;
		fallingEdgeDetector_ = null;
		try {
			readerThread_.join();
		} catch (InterruptedException e) {
			// void
		}
	}
	
}
