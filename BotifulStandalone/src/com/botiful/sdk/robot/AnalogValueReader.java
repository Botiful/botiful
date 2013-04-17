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
 * - alerts when reaching a predefined threshold TODO
 */
public class AnalogValueReader extends AbstractRoboticElement {
	private AnalogInput mAnalogInput;
	/** Observer notified of new values or alerts. Also used as control variable for the thread */
	private AnalogValueObserver mObserver;
	/** Last value observed if any, or NaN */
	private float mLastValue;
	/** a thread to wait for incoming value and store them */
	private Thread mReaderThread;
	/** minimum update period to notify the observer in milliseconds */
	private long mNotificationPeriodMillis;
	
	/**
	 * A custom interface to specify entry point for asynchronous updates
	 */
	public interface AnalogValueObserver {
		/**
		 * This interface method is called when a new value is available<br />
		 * Warning: the output rate is very fast (1 kHz)
		 * @param value new value in the [0,1] range
		 */
		public void onNewValue(final float value);
		
		/**
		 * This interface method is called when the value has reached the predefined threshold
		 */
		public void onValueReachedThreshold();
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
		
		// create and start the thread
		mReaderThread = new Thread() {
			@Override
			public void run() {
				long lastMotificationTimestampMillis = 0;
				while (mObserver!=null) {
					try {
						// get the current value (blocks until data is available)
						mLastValue=mAnalogInput.readBuffered();
						// notify if needed, record the date
						long timestamp = SystemClock.elapsedRealtime();
						if (timestamp-lastMotificationTimestampMillis>mNotificationPeriodMillis) {
							notifyObserver();
							lastMotificationTimestampMillis = timestamp;
						}
					} catch (InterruptedException e) {
						// just warn and loop
						Log.w(this.getClass().getName(),e);
					} catch (ConnectionLostException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		};
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
	 * Adds the specified observer to the list of observers.<br />
	 * If some observer is already registered, it has no effect.
	 * @param observer the Observer to add
	 * @param updatePeriodMillis minimum update period in milliseconds
	 */
	public void subscribeToValuesUpdates(AnalogValueObserver observer, int updatePeriodMillis) {
		if (observer==null || mObserver != null) {
			return;
		}
		mObserver = observer;
		mNotificationPeriodMillis = Math.max(0, updatePeriodMillis);
		mReaderThread.start();
	}
	
	/**
	 * Removes the observer if any.
	 */
	public void deleteObserver() {
		mObserver = null; // this causes the thread to quit
		try {
			mReaderThread.join();
		} catch (InterruptedException e) {
			// void
		}
	}
	
	private void notifyObserver() {
		if (mObserver!=null) {
			mObserver.onNewValue(mLastValue);
		}
	}

}
