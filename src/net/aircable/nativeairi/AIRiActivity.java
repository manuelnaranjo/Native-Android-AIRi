package net.aircable.nativeairi;

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

	@Override
	protected void onResume() {
		if (D) Log.d(TAG, "onResume");
		super.onResume();
		mImgDisplay = (ImageView)this.findViewById(R.id.img);
		if (mDevice == null){
			startActivityForResult(
				new Intent(this, BluetoothInquiry.class),
				SELECT_NEW_DEVICE);
		}
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
			}
		}
		
	};
    
    
}