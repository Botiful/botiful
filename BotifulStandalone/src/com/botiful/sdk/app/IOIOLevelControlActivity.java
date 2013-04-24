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

/**
 * This simple example activity demonstrates how to use IOIO to control Botiful
 */
public class IOIOLevelControlActivity extends IOIOActivity {

	// Speed for the left motor (range:0-10)
	private int leftSpeed;

	// Speed for the right motor (range:0-10)
	private int rightSpeed;

	// Speed for the head motor (range:0-10)
	private int headSpeed;

	// Head position (range:0-10)
	private int encoderValue;

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

		btnForward = (ImageButton) findViewById(R.id.moveForward);
		btnLeft = (ImageButton) findViewById(R.id.moveLeft);
		btnRight = (ImageButton) findViewById(R.id.moveRight);
		btnBackward = (ImageButton) findViewById(R.id.moveBackward);

		btnHeadUp = (Button) findViewById(R.id.buttonHeadUp);
		btnHeadDown = (Button) findViewById(R.id.buttonHeadDown);

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

		txtStatus = (TextView) findViewById(R.id.textStatus);

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
					leftSpeed = 10;
					rightSpeed = 10;
					break;
				case R.id.moveBackward:
					leftSpeed = -10;
					rightSpeed = -10;
					break;
				case R.id.moveLeft:
					leftSpeed = -10;
					rightSpeed = 10;
					break;
				case R.id.moveRight:
					leftSpeed = 10;
					rightSpeed = -10;
					break;
				}
			} else {
				leftSpeed = 0;
				rightSpeed = 0;
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
					headSpeed += 1;
					if (headSpeed>10)
						headSpeed = 10;
					break;
				case R.id.buttonHeadDown:
					headSpeed -= 1;
					if (headSpeed<-10)
						headSpeed = -10;
					break;
				}
			} else {
				leftSpeed = 0;
				rightSpeed = 0;
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
			
			final int freq = 50000; // PWM frequency in Hz
			// ========= Wheels configuration =========
			pwmRight1 = ioio_.openPwmOutput(11, freq);
			pwmRight2 = ioio_.openPwmOutput(10, freq);
			pwmLeft1 = ioio_.openPwmOutput(13, freq);
			pwmLeft2 = ioio_.openPwmOutput(12, freq);

			// The peripheral circuit can be turned OFF if necessary. It can be
			// for instance used to minimize energy consumption (for instance
			// when the robot is charging). Right now we set it to ON
			DigitalOutput powerInMotorCircuit = ioio_.openDigitalOutput(20);
			powerInMotorCircuit.write(false);

			// Motors drivers can use a sleep mode that allows minimun current
			// consumption. When set to ON, motors are consuming a very small
			// amount of current but cannot move anymore
			// For the example we turn the sleep mode to OFF
			DigitalOutput sleepMode = ioio_.openDigitalOutput(29);
			sleepMode.write(true);

			// ========= Head configuration =========
			pwmHead1 = ioio_.openPwmOutput(40, freq);
			pwmHead2 = ioio_.openPwmOutput(39, freq);

			// Turn the sleep mode of the motor driver to OFF
			sleepMode = ioio_.openDigitalOutput(30);
			sleepMode.write(true);

			// Open the rotary encoder
			// the voltage is proportional to the head angle
			// minimum value ~0.9V <=> highest possible angle
			// maximum value ~1.6V <=> lowest possible angle
			rotaryEncoder = ioio_.openAnalogInput(45);

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
			int pwmValues[] = getPwmFromSpeed(leftSpeed);
			pwmLeft1.setPulseWidth(pwmValues[0]);
			pwmLeft2.setPulseWidth(pwmValues[1]);

			pwmValues = getPwmFromSpeed(rightSpeed);
			pwmRight1.setPulseWidth(pwmValues[0]);
			pwmRight2.setPulseWidth(pwmValues[1]);
			
			// reset wheels speed
			leftSpeed = 0;
			rightSpeed = 0;

			try {
				encoderValue = (int) (rotaryEncoder.getVoltage() * 100);
				pwmValues = getPwmFromSpeed(headSpeed);
				
				// depending on the current position of the head (angle) we move or not
				
				// if we want to raise the head
				if (headSpeed>0) {
					if (encoderValue > 92) {
						// go ahead, there is still room
						pwmHead1.setPulseWidth(pwmValues[0]);
						pwmHead2.setPulseWidth(pwmValues[1]);
					} else {
						// we are at the highest point, stop the motor
						headSpeed = 0;
						pwmHead1.setPulseWidth(0);
						pwmHead2.setPulseWidth(0);
					}
				} else {
					// we want to lower the head
					if (encoderValue < 158) {
						// go ahead, there is still room
						pwmHead1.setPulseWidth(pwmValues[0]);
						pwmHead2.setPulseWidth(pwmValues[1]);						
					} else {
						// we are at the lowest point, stop the motor
						headSpeed = 0;
						pwmHead1.setPulseWidth(0);
						pwmHead2.setPulseWidth(0);						
					}					
				}
				

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

		private int[] getPwmFromSpeed(int speed) {
			int pwmDutyCyle[] = new int[2];

			if (speed > 0) {
				pwmDutyCyle[0] = scaleDutyCyle(speed);
				pwmDutyCyle[1] = 0;
			} else if (speed < 0) {
				pwmDutyCyle[0] = 0;
				pwmDutyCyle[1] = scaleDutyCyle(speed);
			} else {
				pwmDutyCyle[0] = 0;
				pwmDutyCyle[1] = 0;
			}

			return pwmDutyCyle;
		}

		/**
		 * Converts a user-friendly speed value in 0-10 to a PWM pulse width in microseconds<br />
		 * The formula assumes a PWM frequency of 50kHz
		 * @param speed user friendly speed value in [-10;10]
		 * @return PWM width in microseconds, to be fed to the IOIO PwmOutput.
		 */
		private int scaleDutyCyle(int speed) {
			return ((int) (4.1 * Math.log(Math.abs(speed)) + 11.0));
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
