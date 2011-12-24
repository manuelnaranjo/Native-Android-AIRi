package net.aircable.nativeairi;

import java.io.IOException;

import net.aircable.nativeairi.AIRiService.PAN;
import net.aircable.nativeairi.AIRiService.SIZE;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
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
	
	private static final int AIRCABLE_SPP = 1;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
    	startActivityForResult(
				new Intent(this, BluetoothInquiry.class),
				SELECT_NEW_DEVICE);
		
    }

	@Override
	protected void onResume() {
		if (D) Log.d(TAG, "onResume");
		super.onResume();
		mImgDisplay = (ImageView)this.findViewById(R.id.img);
		if (mDevice == null)
			doScan();
	}
	
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case SELECT_NEW_DEVICE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK && mDevice == null) {
                // Get the device MAC address
                String address = data.getExtras()
                                     .getString(BluetoothInquiry.EXTRA_ADDRESS);
                this.finishActivity(SELECT_NEW_DEVICE);
                // Get the BLuetoothDevice object
                BluetoothDevice device = mBtAdapter.getRemoteDevice(address);
                if (D) Log.d(TAG, "Using " + device);
                mDevice = device;
                mIDevice = new ImprovedBluetoothDevice(mDevice);
                mBtAdapter.cancelDiscovery();
                try {
					mSocket = mIDevice.createInsecureRfcommSocket(AIRCABLE_SPP);
					mService = new AIRiService(mSocket, mHandler);
					mService.start();
				} catch (Exception e) {
					e.printStackTrace();
				}
            }
            break;
        }
    }
	
	private void BluetoothConnected(){
		this.runOnUiThread(new Runnable(){
			public void run(){
				Toast.makeText(getApplicationContext(), 
						getString(R.string.connection_ok), Toast.LENGTH_LONG).show();
				mService.setSize(AIRiService.SIZE.QVGA);
				mService.setLink(true);
			}
		});
	}
	
	private void BluetoothDisconnected(){
		this.runOnUiThread(new Runnable(){
			public void run(){
				Toast.makeText(getApplicationContext(), 
						getString(R.string.connection_done), Toast.LENGTH_LONG).show();
			}
		});
	}
	
	
	private void BluetoothNotConnected(){
		this.runOnUiThread(new Runnable(){
			public void run(){
				Toast.makeText(getApplicationContext(), 
						getString(R.string.connection_failed), Toast.LENGTH_LONG).show();
				mDevice = null;
				mService = null;
				mIDevice = null;
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
				BluetoothConnected();
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
			}
		}
		
	};

	private static final int MENU_CONNECT=1;
	private static final int MENU_SIZE=2;
	private static final int MENU_FLASH=3;
	private static final int MENU_PAN=4;
	private static final int MENU_EXPOSURE=5;
	
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
		if (mDevice!=null){
			menu.add(0, MENU_CONNECT, 0, this.getString(R.string.bluetooth_disconnect));
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
		} else {
			menu.add(0, MENU_CONNECT, 0, this.getString(R.string.bluetooth_connect));
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()){
		case MENU_CONNECT:
			if (this.mDevice!=null){
				try {
					this.mSocket.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				this.mIDevice = null;
				this.mDevice = null;
				this.mService = null; 
			} else {
				this.doScan();
			}
			return true;
			
		case MENU_SIZE_QVGA:
			if (this.mService!=null)
				this.mService.setSize(SIZE.QVGA);
			return true;
		case MENU_SIZE_VGA:
			if (this.mService!=null)
				this.mService.setSize(SIZE.VGA);
			return true;
		case MENU_SIZE_XGA:
			if (this.mService!=null)
				this.mService.setSize(SIZE.XGA);
			return true;
		case MENU_SIZE_QXGA:
			if (this.mService!=null)
				this.mService.setSize(SIZE.QXGA);
			return true;
		case MENU_SIZE_720P:
			if (this.mService!=null)
				this.mService.setSize(SIZE.P720);
			return true;
		case MENU_SIZE_QVGA_Z1:
			if (this.mService!=null)
				this.mService.setSize(SIZE.QVGA_Zoom1);
			return true;
		case MENU_SIZE_QVGA_Z2:
			if (this.mService!=null)
				this.mService.setSize(SIZE.QVGA_Zoom2);
			return true;
		case MENU_SIZE_QVGA_Z3:
			if (this.mService!=null)
				this.mService.setSize(SIZE.QVGA_Zoom3);
			return true;
		case MENU_FLASH_ON:
			if (this.mService!=null)
				this.mService.setFlash(true);
			return true;
		case MENU_FLASH_OFF:
			if (this.mService!=null)
				this.mService.setFlash(false);
			return true;
		case MENU_PAN_U:
			if (this.mService!=null)
				this.mService.doPan(PAN.UP);
			return true;
		case MENU_PAN_L:
			if (this.mService!=null)
				this.mService.doPan(PAN.LEFT);
			return true;
		case MENU_PAN_R:
			if (this.mService!=null)
				this.mService.doPan(PAN.RIGHT);
			return true;
		case MENU_PAN_D:
			if (this.mService!=null)
				this.mService.doPan(PAN.DOWN);
			return true;
		case MENU_EXPOSURE_66:
		case MENU_EXPOSURE_135:
		case MENU_EXPOSURE_270:
		case MENU_EXPOSURE_533:
		case MENU_EXPOSURE_1000:
		case MENU_EXPOSURE_2000:
			if (this.mService!=null)
				this.mService.setExposure(item.getItemId()-MENU_EXPOSURE_BASE);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
}