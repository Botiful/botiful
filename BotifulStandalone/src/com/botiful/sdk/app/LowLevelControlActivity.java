package com.botiful.sdk.app;

import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.botiful.sdk.helpers.BluetoothHelper;
import com.botiful.sdk.models.PwmSpeed;
import com.botiful.sdk.robot.AnalogValueReader;
import com.botiful.sdk.robot.AnalogValueReader.AnalogValueObserver;
import com.botiful.sdk.robot.Constants;
import com.botiful.sdk.robot.PwmMotor;
import com.botiful.sdk.robot.Switch;
import com.botiful.standalone.sdk.R;

/**
 * Simple Example activity to manage all peripherals at low level
 */
public class LowLevelControlActivity extends IOIOActivity {
	// constants
	private static final float ROTARY_ENCODER_MAX = .49f;
	private static final float ROTARY_ENCODER_MIN = .26f;
	
	// members - widgets
	private ToggleButton mPeripheralCircuitToggle;
	private ToggleButton mWheelsSleepMode;
	private ToggleButton mHeadSleepMode;
	private SeekBar mSeekbarLeftWheel;
	private SeekBar mSeekbarRightWheel;
	private SeekBar mSeekbarHead;
	private TextView mConnectionStatusText;
	private Button mResetAllButton;
	private ProgressBar mSpinnerStatus;
	private TextView mRotaryEncoderValueLabel;
	private CheckBox mRotEncStopAbove,mRotEncStopBelow;
	private TextView mRotEncStopAboveText,mRotEncStopBelowText;
	
	// other members
	/** Two wheels with independent motors */
	private PwmMotor leftMotor,rightMotor;
	/** Another motor to tilt the head */
	private PwmMotor headMotor;
	/** rotary encoder: reads a the position of the head */
	private AnalogValueReader mRotaryEncoder;
	private RotaryEncoderObserver mRotaryEncoderObserver;
	/** Main circuit switch */
	Switch mPeripheralCircuitSwitch;
	/** Sleep switch for the motors */
	Switch mWheelsSleepSwitch,mHeadSleepSwitch;
	
	private class RotaryEncoderObserver implements AnalogValueObserver {

		@Override
		public void onNewValue(final float value) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mRotaryEncoderValueLabel.setText(Float.toString(value));
				}
			});
		}

		@Override
		public void onValueAlertAboveThreshold(float value) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (headMotor != null && !headMotor.getSpeed().isPositive()) {
						try {
							headMotor.stop();
						} catch (ConnectionLostException e) {
							e.printStackTrace();
							setConnectionStatus(false);
						}
					}
				}
			});			
		}

		@Override
		public void onValueAlertBelowThreshold(float value) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (headMotor != null && headMotor.getSpeed().isPositive()) {
						try {
							headMotor.stop();
						} catch (ConnectionLostException e) {
							e.printStackTrace();
							setConnectionStatus(false);
						}
					}
				}
			});	
			
		}
		
	}
	
	
	// manages actions for the toggle buttons
	private OnCheckedChangeListener mOnCheckedChangeListener = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			switch (buttonView.getId()) {
			case R.id.toggleButton_peripheral_circuit_control:
				switchSwitch(mPeripheralCircuitSwitch,isChecked);
				break;
			case R.id.toggleButton_wheels_sleep_mode_control:
				switchSwitch(mWheelsSleepSwitch,isChecked);
				break;
			case R.id.toggleButton_head_sleep_mode_control:
				switchSwitch(mHeadSleepSwitch,isChecked);
				break;
			case R.id.checkbox_rotary_encoder_threshold_above:
				setThresholdDetection(mRotaryEncoder, isChecked, true);
				break;
			case R.id.checkbox_rotary_encoder_threshold_below:
				setThresholdDetection(mRotaryEncoder, isChecked, false);
				break;
			default:
				// void
				break;
			}
			
		}		
	};
	
	// manages action on the various seekbars
	private OnSeekBarChangeListener mSeekBarChangeListener = new OnSeekBarChangeListener() {
		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			switch (seekBar.getId()) {
			case (R.id.seekBarLeftWheel):
				setMotorSpeed(leftMotor,new PwmSpeed(progress-PwmSpeed.MAX));
				break;
			case (R.id.seekBarRightWheel):
				setMotorSpeed(rightMotor,new PwmSpeed(progress-PwmSpeed.MAX));
				break;
			case (R.id.seekBarHead):
				setMotorSpeed(headMotor,new PwmSpeed(progress-PwmSpeed.MAX));
				break;
			default:
				// void
				break;
			}
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {}
	};


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_low_level_control);
		
		// Get handles to the widgets
		mPeripheralCircuitToggle = (ToggleButton) findViewById(R.id.toggleButton_peripheral_circuit_control);
		mWheelsSleepMode = (ToggleButton) findViewById(R.id.toggleButton_wheels_sleep_mode_control);
		mConnectionStatusText = (TextView) findViewById(R.id.textStatus);
		mSeekbarLeftWheel = (SeekBar) findViewById(R.id.seekBarLeftWheel);
		mSeekbarRightWheel = (SeekBar) findViewById(R.id.seekBarRightWheel);
		mHeadSleepMode = (ToggleButton) findViewById(R.id.toggleButton_head_sleep_mode_control);
		mSeekbarHead = (SeekBar) findViewById(R.id.seekBarHead);
		mResetAllButton = (Button) findViewById(R.id.button_reset);
		mSpinnerStatus = (ProgressBar) findViewById(R.id.spinner_status);
		mRotaryEncoderValueLabel = (TextView) findViewById(R.id.value_rotary_encoder);
		mRotEncStopAbove = (CheckBox) findViewById(R.id.checkbox_rotary_encoder_threshold_above);
		mRotEncStopAboveText = (TextView) findViewById(R.id.label_rotary_encoder_threshold_above_value);
		mRotEncStopBelow = (CheckBox) findViewById(R.id.checkbox_rotary_encoder_threshold_below);
		mRotEncStopBelowText = (TextView) findViewById(R.id.label_rotary_encoder_threshold_below_value);
		
		// Initialize all views
		resetViewAndCommands();
		
		// connect the listeners
		mPeripheralCircuitToggle.setOnCheckedChangeListener(mOnCheckedChangeListener);
		mWheelsSleepMode.setOnCheckedChangeListener(mOnCheckedChangeListener);
		mSeekbarLeftWheel.setOnSeekBarChangeListener(mSeekBarChangeListener);
		mSeekbarRightWheel.setOnSeekBarChangeListener(mSeekBarChangeListener);
		mSeekbarHead.setOnSeekBarChangeListener(mSeekBarChangeListener);
		mHeadSleepMode.setOnCheckedChangeListener(mOnCheckedChangeListener);
		mRotEncStopAbove.setOnCheckedChangeListener(mOnCheckedChangeListener);
		mRotEncStopBelow.setOnCheckedChangeListener(mOnCheckedChangeListener);
		mResetAllButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				resetViewAndCommands();
			}
		});	

		// Ask to enable Bluetooth if not already enabled
		setConnectionStatus(false);
		BluetoothHelper.enableBluetooth(this);
	}
	
	@Override
	public void onPause() {
		// remove observer to the rotary encoder if any
		if (mRotaryEncoder != null) {
			mRotaryEncoder.deleteObserver();
		}
		super.onPause();
	}
	
	/**
	 * Reset all widgets to their initial position, and resets all the commands
	 */
	private void resetViewAndCommands() {
		mSeekbarLeftWheel.setMax(PwmSpeed.MAX*2+1);
		mSeekbarLeftWheel.setProgress(PwmSpeed.MAX);
		mSeekbarRightWheel.setMax(PwmSpeed.MAX*2+1);
		mSeekbarRightWheel.setProgress(PwmSpeed.MAX);
		mSeekbarHead.setMax(PwmSpeed.MAX*2+1);
		mSeekbarHead.setProgress(PwmSpeed.MAX);
		mPeripheralCircuitToggle.setChecked(true);
		mWheelsSleepMode.setChecked(false);
		mHeadSleepMode.setChecked(false);
		mRotaryEncoderValueLabel.setText(R.string.rotary_encoder_value_unknown);
		mRotEncStopAbove.setChecked(false);
		mRotEncStopBelow.setChecked(false);
		mRotEncStopAboveText.setText(Float.toString(ROTARY_ENCODER_MAX));
		mRotEncStopBelowText.setText(Float.toString(ROTARY_ENCODER_MIN));
	}

	/**
	 * This is the thread on which all the IOIO activity happens. It will be run
	 * every time the application is resumed and aborted when it is paused. The
	 * method setup() will be called right after a connection with the IOIO has
	 * been established (which might happen several times!). Then, loop() will
	 * be called repetitively until the IOIO gets disconnected.
	 */
	class Looper extends BaseIOIOLooper {
		private static final int LOOP_CYCLE_MILLISECONDS = 100;
		

		/**
		 * Called every time a connection with IOIO has been established.
		 * Typically used to open pins.
		 * 
		 * @throws ConnectionLostException
		 *             When IOIO connection is lost.
		 * 
		 * @see ioio.lib.util.AbstractIOIOActivity.IOIOThread#setup()
		 */
		@Override
		protected void setup() throws ConnectionLostException {

			setConnectionStatus(true);
			
			// ========= Wheels configuration =========
			leftMotor = new PwmMotor(ioio_,
					Constants.LEFT_WHEEL_POSITIVE_PWM_OUTPUT_PIN,
					Constants.LEFT_WHEEL_REVERSE_PWM_OUTPUT_PIN);
			leftMotor.setSpeed(new PwmSpeed(mSeekbarLeftWheel.getProgress()-PwmSpeed.MAX));
			
			rightMotor = new PwmMotor(ioio_,
					Constants.RIGHT_WHEEL_POSITIVE_PWM_OUTPUT_PIN,
					Constants.RIGHT_WHEEL_REVERSE_PWM_OUTPUT_PIN);
			rightMotor.setSpeed(new PwmSpeed(mSeekbarRightWheel.getProgress()-PwmSpeed.MAX));
			

			// The peripheral circuit can be turned OFF if necessary. It can be
			// for instance used to minimize energy consumption (for instance
			// when the robot is charging). Right now we set it to ON
			mPeripheralCircuitSwitch = new Switch(ioio_,
					Constants.PERIPHERAL_CIRCUIT_DIGITAL_OUTPUT_PIN,
					mPeripheralCircuitToggle.isChecked());

			// Motors drivers can use a sleep mode that allows minimun current
			// consumption. When set to ON, motors are consuming a very small
			// amount of current but cannot move anymore
			// For the example we turn the sleep mode to OFF
			mWheelsSleepSwitch = new Switch(ioio_,
					Constants.WHEELS_MOTOR_DRIVERS_SLEEP_MODE_PIN,
					mWheelsSleepMode.isChecked());

			// ========= Head configuration =========
			headMotor = new PwmMotor(ioio_,
					Constants.HEAD_POSITIVE_PWM_OUTPUT_PIN,
					Constants.HEAD_REVERSE_PWM_OUTPUT_PIN);
			headMotor.setSpeed(new PwmSpeed(mSeekbarHead.getProgress()-PwmSpeed.MAX));
			
			mHeadSleepSwitch = new Switch(ioio_,
					Constants.HEAD_MOTOR_DRIVERS_SLEEP_MODE_PIN,
					mHeadSleepMode.isChecked());
			
			mRotaryEncoder = new AnalogValueReader(ioio_,
					Constants.ROTARY_ENCODER_ANALOG_INPUT_PIN);

			mRotaryEncoderObserver = new RotaryEncoderObserver();
			mRotaryEncoder.setObserver(mRotaryEncoderObserver);
			mRotaryEncoder.subscribeToValuesUpdates(100);
			setThresholdDetection(mRotaryEncoder,mRotEncStopAbove.isChecked(),true);
			setThresholdDetection(mRotaryEncoder,mRotEncStopBelow.isChecked(),false);
		}

		/**
		 * Called repetitively while the IOIO is connected.
		 * 
		 * @throws ConnectionLostException
		 *             When IOIO connection is lost.
		 * 
		 * @see ioio.lib.util.AbstractIOIOActivity.IOIOThread#loop()
		 */
		@Override
		public void loop() throws ConnectionLostException {
			try {
				Thread.sleep(LOOP_CYCLE_MILLISECONDS);
			} catch (InterruptedException e) {
				// wait period has been interrupted - most probably because the user has left the app
				// no action
			}
		}

	}

	/**
	 * A method to create our IOIO thread.
	 * 
	 * @see ioio.lib.util.AbstractIOIOActivity#createIOIOThread()
	 */
	@Override
	protected IOIOLooper createIOIOLooper() {
		return new Looper();
	}
	
	/**
	 * Changes the motor speed and handles errors gracefully.
	 * @param motor target motor
	 * @param newspeed 
	 */
	private void setMotorSpeed(PwmMotor motor, PwmSpeed newSpeed) {
		if (motor!=null) {
			try {
				motor.setSpeed(newSpeed);
			} catch (ConnectionLostException e) {
				setConnectionStatus(false);
			}
		}
	}
	
	/**
	 * Switches any switch which has to be switched, and handles errors gracefully like a wicked switch.
	 * @param switchtoSwitch switch which we wish switched
	 * @param newState state into which we switch the switch
	 */
	private void switchSwitch(Switch switchToSwitch, boolean newState) {
		if (switchToSwitch != null) {
			try {
				switchToSwitch.set(newState);
			} catch (ConnectionLostException e) {
				setConnectionStatus(false);
			}
		}
	}
	
	/**
	 * Sets the threshold detection (or reset it) for a target analog value reader.
	 * @param reader target reader
	 * @param newState if true, the detection will be activated, else it will be deactivated
	 * @param isAbove tells which threshold detector (high[true] or low[false] values) to target
	 */
	private void setThresholdDetection(AnalogValueReader reader,boolean newState, boolean isAbove) {
		if (reader!=null) {
			if (isAbove) {
				float threshold = newState?ROTARY_ENCODER_MAX:Float.NaN;
				reader.subscribeToRisingEdgeThresholdDetection(threshold);				
			} else {
				float threshold = newState?ROTARY_ENCODER_MIN:Float.NaN;
				reader.subscribeToFallingEdgeThresholdDetection(threshold);						
			}
		}
	}
	
	/**
	 * Changes the view according to the connection status. Can be run from any thread.
	 * @param isConnected true IIF Botiful is connected
	 */
	private void setConnectionStatus(boolean isConnected) {
		if (isConnected) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mConnectionStatusText.setText(R.string.connection_status_ok);
					mSpinnerStatus.setVisibility(View.GONE);
				}
			});
		} else {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mConnectionStatusText.setText(R.string.connection_status_notok);
					mSpinnerStatus.setVisibility(View.VISIBLE);
				}
			});
		}
	}

}
