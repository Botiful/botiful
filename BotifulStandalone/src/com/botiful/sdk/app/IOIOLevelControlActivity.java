package com.botiful.sdk.app;

import ioio.lib.api.AnalogInput;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.botiful.sdk.R;
import com.botiful.sdk.helpers.BluetoothHelper;
import com.botiful.sdk.models.PwmSpeed;
import com.botiful.sdk.robot.Constants;

/**
 * This simple example activity demonstrates how to use IOIO to control Botiful.<br />
 * It uses a IOIOActivity, which is a helper activity very easy to manage.<br />
 * All actions are managed inside a periodic loop which runs in its own thread, it is mostly
 * managed by the IOIOActivity (except for its creation in the createIOIOLooper() method).
 */
public class IOIOLevelControlActivity extends IOIOActivity {
	
	// speeds of the various motors
	private PwmSpeed leftSpeed,rightSpeed,headSpeed;
	// Head angle, range: Constants.ROTARY_ENCODER_MIN_VALUE to ROTARY_ENCODER_MAX_VALUE
	private float encoderValue;

	// Manage the button UI
	private ImageButton btnForward;
	private ImageButton btnLeft;
	private ImageButton btnRight;
	private ImageButton btnBackward;
	private Button btnHeadUp;
	private Button btnHeadDown;

	private TextView txtStatus;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// get handles to UI components
		btnForward = (ImageButton) findViewById(R.id.moveForward);
		btnLeft = (ImageButton) findViewById(R.id.moveLeft);
		btnRight = (ImageButton) findViewById(R.id.moveRight);
		btnBackward = (ImageButton) findViewById(R.id.moveBackward);

		btnHeadUp = (Button) findViewById(R.id.buttonHeadUp);
		btnHeadDown = (Button) findViewById(R.id.buttonHeadDown);
		
		txtStatus = (TextView) findViewById(R.id.textStatus);

		// set the action listeners
		MoveOnTouchListener mv = new MoveOnTouchListener();
		btnForward.setOnTouchListener(mv);
		btnLeft.setOnTouchListener(mv);
		btnRight.setOnTouchListener(mv);
		btnBackward.setOnTouchListener(mv);

		HeadOnTouchListener hl = new HeadOnTouchListener();
		btnHeadUp.setOnTouchListener(hl);
		btnHeadDown.setOnTouchListener(hl);

		// Ask to enable Bluetooth if not already enabled
		BluetoothHelper.enableBluetooth(this);
		
		// init motor speeds
		leftSpeed = new PwmSpeed(0);
		rightSpeed = new PwmSpeed(0);
		headSpeed = new PwmSpeed(0);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	private class MoveOnTouchListener implements View.OnTouchListener {

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
				switch (v.getId()) {
				case R.id.moveForward:
					leftSpeed = new PwmSpeed(PwmSpeed.MAX);
					rightSpeed = new PwmSpeed(PwmSpeed.MAX);
					break;
				case R.id.moveBackward:
					leftSpeed = new PwmSpeed(-PwmSpeed.MAX);
					rightSpeed = new PwmSpeed(-PwmSpeed.MAX);
					break;
				case R.id.moveLeft:
					leftSpeed = new PwmSpeed(-PwmSpeed.MAX);
					rightSpeed = new PwmSpeed(PwmSpeed.MAX);
					break;
				case R.id.moveRight:
					leftSpeed = new PwmSpeed(PwmSpeed.MAX);
					rightSpeed = new PwmSpeed(-PwmSpeed.MAX);
					break;
				}
			} else {
				leftSpeed = new PwmSpeed(0);
				rightSpeed = new PwmSpeed(0);
			}
			return false;
		}
	}

	private class HeadOnTouchListener implements View.OnTouchListener {

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
				switch (v.getId()) {
				case R.id.buttonHeadUp:
					headSpeed.increase();
					break;
				case R.id.buttonHeadDown:
					headSpeed.decrease();
					break;
				}
			}
			return false;
		}
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
		 * The wheels motors are controlled by PWM. There is two PWMs per motors
		 */
		private PwmOutput pwmLeft1;
		private PwmOutput pwmLeft2;
		private PwmOutput pwmRight1;
		private PwmOutput pwmRight2;

		/** The head motor is controlled by a 2 PWM */
		private PwmOutput pwmHead1;
		private PwmOutput pwmHead2;
		private AnalogInput rotaryEncoder;

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

			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					txtStatus.setText(R.string.connection_status_ok);
				}
			});
			
			// ========= Wheels configuration =========
			pwmRight1 = ioio_.openPwmOutput(Constants.LEFT_WHEEL_POSITIVE_PWM_OUTPUT_PIN, Constants.PWM_FREQUENCY);
			pwmRight2 = ioio_.openPwmOutput(Constants.LEFT_WHEEL_REVERSE_PWM_OUTPUT_PIN, Constants.PWM_FREQUENCY);
			pwmLeft1 = ioio_.openPwmOutput(Constants.RIGHT_WHEEL_POSITIVE_PWM_OUTPUT_PIN, Constants.PWM_FREQUENCY);
			pwmLeft2 = ioio_.openPwmOutput(Constants.RIGHT_WHEEL_REVERSE_PWM_OUTPUT_PIN, Constants.PWM_FREQUENCY);

			// The peripheral circuit can be turned OFF if necessary. It can be
			// for instance used to minimize energy consumption (for instance
			// when the robot is charging). Right now we set it to ON
			DigitalOutput powerInMotorCircuit = ioio_.openDigitalOutput(Constants.PERIPHERAL_CIRCUIT_DIGITAL_OUTPUT_PIN);
			powerInMotorCircuit.write(false);

			// Motors drivers can use a sleep mode that allows minimun current
			// consumption. When set to ON, motors are consuming a very small
			// amount of current but cannot move anymore
			// For the example we turn the sleep mode to OFF
			DigitalOutput sleepMode = ioio_.openDigitalOutput(Constants.WHEELS_MOTOR_DRIVERS_SLEEP_MODE_PIN);
			sleepMode.write(true);

			// ========= Head configuration =========
			pwmHead1 = ioio_.openPwmOutput(Constants.HEAD_POSITIVE_PWM_OUTPUT_PIN, Constants.PWM_FREQUENCY);
			pwmHead2 = ioio_.openPwmOutput(Constants.HEAD_REVERSE_PWM_OUTPUT_PIN, Constants.PWM_FREQUENCY);

			// Turn the sleep mode of the motor driver to OFF
			sleepMode = ioio_.openDigitalOutput(Constants.HEAD_MOTOR_DRIVERS_SLEEP_MODE_PIN);
			sleepMode.write(true);

			// Open the rotary encoder
			// the voltage is proportional to the head angle
			// minimum value ~0.26 <=> highest possible angle
			// maximum value ~0.49 <=> lowest possible angle
			rotaryEncoder = ioio_.openAnalogInput(Constants.ROTARY_ENCODER_ANALOG_INPUT_PIN);

		}

		/**
		 * Called repetitively while the IOIO is connected. In this loop we send the commands to 
		 * the various outputs (e.g. PWM controlled motors).<br />
		 * All commands will be run for the duration of the loop 
		 * 
		 * @throws ConnectionLostException
		 *             When IOIO connection is lost.
		 * 
		 * @see ioio.lib.util.AbstractIOIOActivity.IOIOThread#loop()
		 */
		@Override
		public void loop() throws ConnectionLostException {
			pwmLeft1.setPulseWidth(leftSpeed.getPulseWidthForPositivePin());
			pwmLeft2.setPulseWidth(leftSpeed.getPulseWidthForReversePin());

			pwmRight1.setPulseWidth(rightSpeed.getPulseWidthForPositivePin());
			pwmRight2.setPulseWidth(rightSpeed.getPulseWidthForReversePin());
			
			// reset wheels speed
			leftSpeed = new PwmSpeed(0);
			rightSpeed = new PwmSpeed(0);

			try {
				encoderValue = rotaryEncoder.read();
				
				// depending on the current position of the head (angle) we move or not				
				// if we are raising the head
				if (headSpeed.isPositive()) {
					if (encoderValue <= Constants.ROTARY_ENCODER_MIN_VALUE) {
						// we are at the highest point (lowest value), stop the motor
						headSpeed = new PwmSpeed(0);
					}
				} else {
					// we are lowering the head
					if (encoderValue >= Constants.ROTARY_ENCODER_MAX_VALUE) {
						// we are at the lowest point (highest value), stop the motor
						headSpeed = new PwmSpeed(0);				
					}					
				}
				// apply speed
				pwmHead1.setPulseWidth(headSpeed.getPulseWidthForPositivePin());
				pwmHead2.setPulseWidth(headSpeed.getPulseWidthForReversePin());
				

			} catch (InterruptedException e1) {
				// this means we have not been able to get the angle of the head (rotary encoder position)
				//  no action
			}

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
}
