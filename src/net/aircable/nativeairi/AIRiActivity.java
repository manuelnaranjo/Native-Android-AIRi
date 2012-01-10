package net.aircable.nativeairi;

import java.io.IOException;

import net.aircable.nativeairi.AIRiService.PAN;
import net.aircable.nativeairi.AIRiService.SIZE;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.hexad.bluezime.ImprovedBluetoothDevice;

public class AIRiActivity extends Activity {
	private static final String TAG="AIRiActivity";
	private static final boolean D = true;
	private static final int SELECT_NEW_DEVICE = 1;
	private BluetoothAdapter mBtAdapter;
	private BluetoothDevice mDevice;
	private ImprovedBluetoothDevice mIDevice;
	private BluetoothSocket mSocket;
	private AIRiService mService;
	private ImageView mImgDisplay;
	private SharedPreferences mPreferences;
	
	enum STATES {
		IDLE,
		SCANNING,
		PAIRED,
		CONNECTING,
		CONNECTED,
	}
	private STATES mState;
	
	public static final String SETTINGS="AIRiNativeSettings";
	public static final String SETTINGS_TARGET="target";
	
	private static final int AIRCABLE_SPP = 1;
	
	@Override
	public void onBackPressed() {
		if (this.mState!=STATES.IDLE)
			return;
		super.onBackPressed();
	}
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mState=STATES.IDLE;
        setContentView(R.layout.main);
        
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
        	Toast.makeText(this, this.getString(R.string.no_bluetooth), 
        			Toast.LENGTH_LONG).show();
        	this.finish();
        	return;
        }
        if (!mBtAdapter.isEnabled()){
        	Toast.makeText(this, this.getString(R.string.activate_bluetooth), 
        			Toast.LENGTH_SHORT).show();
        	mBtAdapter.enable();
        }
    }
    
    private void doScan(){
    	mState = STATES.SCANNING;
    	startActivityForResult(
				new Intent(this, BluetoothInquiry.class),
				SELECT_NEW_DEVICE);
		
    }

	@Override
	protected void onResume() {
		if (D) Log.d(TAG, "onResume");
		super.onResume();
		
		if (this.mState == STATES.CONNECTED){
			return;
		}
		mImgDisplay = (ImageView)this.findViewById(R.id.img);
		mPreferences = this.getSharedPreferences(SETTINGS, MODE_PRIVATE);
		if (mPreferences.contains(SETTINGS_TARGET)) {
			String addr = mPreferences.getString(SETTINGS_TARGET, null);
			if (BluetoothAdapter.checkBluetoothAddress(addr)){
				setRemote(addr);
				return;
			}
		}
		Editor e = mPreferences.edit();
		e.remove(SETTINGS_TARGET);
		e.commit();
		doScan();
	}
		
	private void setRemote(String addr){
		this.mState = STATES.CONNECTING;
		mImgDisplay.setImageDrawable(getResources().getDrawable(R.drawable.ic_launcher_airi_72));
		BluetoothDevice device = mBtAdapter.getRemoteDevice(addr);
		Editor e = this.mPreferences.edit();
		e.putString(SETTINGS_TARGET, addr);
		e.commit();
		mDevice = device;
		mIDevice = new ImprovedBluetoothDevice(mDevice);
		mBtAdapter.cancelDiscovery();
		try {
			mSocket = mIDevice.createRfcommSocket(AIRCABLE_SPP);
			//mSocket = mIDevice.createLCAPSocket(0x1001);
			mService = new AIRiService(mSocket, mHandler);
			mService.start();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
	}
	
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case SELECT_NEW_DEVICE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK && mState == STATES.SCANNING) {
                // Get the device MAC address
                String address = data.getExtras()
                                     .getString(BluetoothInquiry.EXTRA_ADDRESS);
                this.finishActivity(SELECT_NEW_DEVICE);
                
                this.setRemote(address);
            }
            break;
        }
    }
	
	private void BluetoothConnected(final boolean display){
		if (D)Log.d(TAG, "BluetoothConnected");
		mState = STATES.CONNECTED;
		this.runOnUiThread(new Runnable(){
			public void run(){
				if (display)
					Toast.makeText(getApplicationContext(), 
						getString(R.string.connection_ok), Toast.LENGTH_LONG).show();
				try{
					mService.setSize(AIRiService.SIZE.QVGA);
					mService.setLink(true);
				} catch (Exception e){
					Log.e(TAG, "found issue", e);
					disconnect();
					return;
				}
				getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
				getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			}
		});
	}
	
	private void BluetoothDisconnected(){
		if (D)Log.d(TAG, "BluetoothDisconnected");
		mState = STATES.IDLE;
		this.runOnUiThread(new Runnable(){
			public void run(){
				getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
				getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
				Toast.makeText(getApplicationContext(), 
						getString(R.string.connection_done), Toast.LENGTH_LONG).show();
			}
		});
	}
	
	
	private void BluetoothNotConnected(){
		if (D)Log.d(TAG, "BluetoothDisconnected");
		mState = STATES.IDLE;
		this.runOnUiThread(new Runnable(){
			public void run(){
				Toast.makeText(getApplicationContext(), 
						getString(R.string.connection_failed), Toast.LENGTH_LONG).show();
			}
		});	
	}
	
	private void NewFrame(byte[] data){
		final Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);

		this.runOnUiThread(new Runnable(){
			public void run(){
				mImgDisplay.setImageBitmap(b);
			}
		});
		
	}
	
	
	private Handler mHandler = new Handler(){

		@Override
		public void handleMessage(Message msg) {
			if (D) Log.d(TAG, msg.toString());
			
			switch (msg.what) {
			case AIRiService.CONNECTION_STABLISHED:
				BluetoothConnected(true);
				this.postDelayed(new Runnable(){
					public void run(){
						if (mService!=null && mService.isDirty())
							BluetoothConnected(false);
					}
				}, 4*1000);
				break;
			case AIRiService.CONNECTION_FAILED:
				BluetoothNotConnected();
				break;
			case AIRiService.PICTURE_AVAILABLE:
				byte[] frame = (byte[])msg.obj;
				NewFrame(frame);
				break;
			case AIRiService.CONNECTION_FINISHED:
				BluetoothDisconnected();
				break;
			case AIRiService.OUT_OF_MEMORY:
				Context c = getApplicationContext();
				Toast.makeText(c, 
						c.getText(R.string.out_of_memory)+Integer.toString(msg.arg1), 
						Toast.LENGTH_LONG).show();
				break;
			}
		}
		
	};

	private static final int MENU_CONNECT=1;
	private static final int MENU_DISCONNECT=2;
	private static final int MENU_FORGET=3;
	private static final int MENU_SIZE=4;
	private static final int MENU_FLASH=5;
	private static final int MENU_PAN=6;
	private static final int MENU_EXPOSURE=7;
	private static final int MENU_EXIT=8;
	
	private static final int MENU_SIZE_QVGA=100;
	private static final int MENU_SIZE_VGA=101;
	private static final int MENU_SIZE_XGA=102;
	private static final int MENU_SIZE_QXGA=103;
	private static final int MENU_SIZE_720P=104;
	private static final int MENU_SIZE_QVGA_Z1=105;
	private static final int MENU_SIZE_QVGA_Z2=106;
	private static final int MENU_SIZE_QVGA_Z3=107;
	
	private static final int MENU_FLASH_ON=200;
	private static final int MENU_FLASH_OFF=201;
	
	private static final int MENU_PAN_U=300;
	private static final int MENU_PAN_D=301;
	private static final int MENU_PAN_L=302;
	private static final int MENU_PAN_R=303;
	
	private static final int MENU_EXPOSURE_BASE=400;
	private static final int MENU_EXPOSURE_66=MENU_EXPOSURE_BASE+1;
	private static final int MENU_EXPOSURE_135=MENU_EXPOSURE_BASE+2;
	private static final int MENU_EXPOSURE_270=MENU_EXPOSURE_BASE+4;
	private static final int MENU_EXPOSURE_533=MENU_EXPOSURE_BASE+8;
	private static final int MENU_EXPOSURE_1000=MENU_EXPOSURE_BASE+15;
	private static final int MENU_EXPOSURE_2000=MENU_EXPOSURE_BASE+31;
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		if (mState!=STATES.IDLE){
			menu.add(0, MENU_DISCONNECT, 0, this.getString(R.string.bluetooth_disconnect));
			//if (this.mPreferences.contains(SETTINGS_TARGET))
			//	menu.add(0, MENU_FORGET, 1, this.getString(R.string.bluetooth_forget));
			
			SubMenu s = menu.addSubMenu(0, MENU_SIZE, 1, this.getString(R.string.menu_size));
			s.add(0, MENU_SIZE_QVGA, 0, "QVGA");
			s.add(0, MENU_SIZE_VGA, 1, "VGA");
			s.add(0, MENU_SIZE_XGA, 2, "XGA");
			s.add(0, MENU_SIZE_QXGA, 3, "QXGA");
			s.add(0, MENU_SIZE_720P, 4, "720P");
			s.add(0, MENU_SIZE_QVGA_Z1, 5, "QVGA Zoom 1");
			s.add(0, MENU_SIZE_QVGA_Z2, 6, "QVGA Zoom 2");
			s.add(0, MENU_SIZE_QVGA_Z3, 7, "QVGA Zoom 3");
			
			s = menu.addSubMenu(0, MENU_FLASH, 2, this.getString(R.string.menu_flash));
			s.add(0, MENU_FLASH_ON, 0, this.getString(R.string.on));
			s.add(0, MENU_FLASH_OFF, 1, this.getString(R.string.off));
			
			s = menu.addSubMenu(0, MENU_PAN, 3, this.getString(R.string.menu_pan));
			s.add(0, MENU_PAN_U, 0, this.getString(R.string.menu_pan_u));
			s.add(0, MENU_PAN_L, 1, this.getString(R.string.menu_pan_l));
			s.add(0, MENU_PAN_D, 2, this.getString(R.string.menu_pan_d));
			s.add(0, MENU_PAN_R, 3, this.getString(R.string.menu_pan_r));
			
			s = menu.addSubMenu(0, MENU_EXPOSURE, 4, this.getString(R.string.menu_exposure));
			s.add(0, MENU_EXPOSURE_66, 0, "66ms");
			s.add(0, MENU_EXPOSURE_135, 1, "135ms");
			s.add(0, MENU_EXPOSURE_270, 0, "270ms");
			s.add(0, MENU_EXPOSURE_533, 0, "533ms");
			s.add(0, MENU_EXPOSURE_1000, 0, "1s");
			s.add(0, MENU_EXPOSURE_2000, 0, "2s");
			
			menu.add(0, MENU_EXIT, 5, this.getString(R.string.menu_exit));
		} else {
			menu.add(0, MENU_CONNECT, 0, this.getString(R.string.bluetooth_connect));
			if (this.mPreferences.contains(SETTINGS_TARGET))
				menu.add(0, MENU_FORGET, 1, this.getString(R.string.bluetooth_forget));
			menu.add(0, MENU_EXIT, 2, this.getString(R.string.menu_exit));
		}
		return true;
	}

	private void disconnect()
	{
		new Thread(){
			public void run(){
				if (mService!=null)
					mService.stopService();
				try {
					mSocket.getInputStream().close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				try {
					mSocket.getOutputStream().close();
				} catch (IOException e){
					e.printStackTrace();
				}
				try {
					mSocket.close();
				} catch (IOException e){
					
				}
				mIDevice = null;
				mDevice = null;
				mService = null; 
				mState = STATES.IDLE;		
			}
		}.start();
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()){
		case MENU_FORGET:
			Editor ed = this.mPreferences.edit();
			ed.remove(SETTINGS_TARGET);
			ed.commit();
			return true;
		case MENU_DISCONNECT:
			this.disconnect();
			return true;
		case MENU_CONNECT:
			if (mPreferences.contains(SETTINGS_TARGET)) {
				String addr = mPreferences.getString(SETTINGS_TARGET, null);
				if (BluetoothAdapter.checkBluetoothAddress(addr)){
					setRemote(addr);
					return true;
				}
			}
			this.doScan();
			return true;
		case MENU_SIZE_QVGA:
			if (this.mState==STATES.CONNECTED)
				this.mService.setSize(SIZE.QVGA);
			return true;
		case MENU_SIZE_VGA:
			if (this.mState==STATES.CONNECTED)
				this.mService.setSize(SIZE.VGA);
			return true;
		case MENU_SIZE_XGA:
			if (this.mState==STATES.CONNECTED)
				this.mService.setSize(SIZE.XGA);
			return true;
		case MENU_SIZE_QXGA:
			if (this.mState==STATES.CONNECTED)
				this.mService.setSize(SIZE.QXGA);
			return true;
		case MENU_SIZE_720P:
			if (this.mState==STATES.CONNECTED)
				this.mService.setSize(SIZE.P720);
			return true;
		case MENU_SIZE_QVGA_Z1:
			if (this.mState==STATES.CONNECTED)
				this.mService.setSize(SIZE.QVGA_Zoom1);
			return true;
		case MENU_SIZE_QVGA_Z2:
			if (this.mState==STATES.CONNECTED)
				this.mService.setSize(SIZE.QVGA_Zoom2);
			return true;
		case MENU_SIZE_QVGA_Z3:
			if (this.mState==STATES.CONNECTED)
				this.mService.setSize(SIZE.QVGA_Zoom3);
			return true;
		case MENU_FLASH_ON:
			if (this.mState==STATES.CONNECTED)
				this.mService.setFlash(true);
			return true;
		case MENU_FLASH_OFF:
			if (this.mState==STATES.CONNECTED)
				this.mService.setFlash(false);
			return true;
		case MENU_PAN_U:
			if (this.mState==STATES.CONNECTED)
				this.mService.doPan(PAN.UP);
			return true;
		case MENU_PAN_L:
			if (this.mState==STATES.CONNECTED)
				this.mService.doPan(PAN.LEFT);
			return true;
		case MENU_PAN_R:
			if (this.mState==STATES.CONNECTED)
				this.mService.doPan(PAN.RIGHT);
			return true;
		case MENU_PAN_D:
			if (this.mState==STATES.CONNECTED)
				this.mService.doPan(PAN.DOWN);
			return true;
		case MENU_EXPOSURE_66:
		case MENU_EXPOSURE_135:
		case MENU_EXPOSURE_270:
		case MENU_EXPOSURE_533:
		case MENU_EXPOSURE_1000:
		case MENU_EXPOSURE_2000:
			if (this.mState==STATES.CONNECTED)
				this.mService.setExposure(item.getItemId()-MENU_EXPOSURE_BASE);
			return true;	
		case MENU_EXIT:
			if (this.mService!=null)
				this.mService.stopService();
			if (this.mSocket!=null)
				try {
					this.mSocket.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			this.mService=null;
			this.mSocket=null;
			System.gc();
			this.finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}	
}
