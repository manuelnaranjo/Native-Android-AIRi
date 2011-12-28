package net.aircable.nativeairi;


import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class BluetoothInquiry extends ListActivity {
	private static final String TAG = "BluetoothInquiry";
	private static final boolean D = true;
	private ArrayAdapter<BluetoothDeviceWrapper> mArrayAdapter;
	private BluetoothAdapter mBtAdapter;
	private boolean running;
	
	public static final String EXTRA_ADDRESS = "address";
	
	private void startDiscovery(){
		if (D) Log.d(TAG, "startDiscovery");
		if (!this.running){
			this.finish();
			return;
		}
        Toast.makeText(getApplicationContext(), getString(R.string.bluetooth_inquiry), 
        		Toast.LENGTH_SHORT).show();
        if (mBtAdapter.isDiscovering())
        	mBtAdapter.cancelDiscovery();
    	boolean ret = mBtAdapter.startDiscovery();
    	if (D) Log.d(TAG, "" + ret);
	}
	
	
	
    @Override
	protected void onDestroy() {
    	if (D) Log.d(TAG, "onDestroy");
        super.onDestroy();
        
        this.running = false;
        // stop any pending discovery
        if (mBtAdapter!=null){
        	mBtAdapter.cancelDiscovery();
        }
        
        // unregister listener
        if (mReceiver!=null)
        	this.unregisterReceiver(mReceiver);
	}

	@Override
	protected void onResume() {
		if (D) Log.d(TAG, "onResume");
		super.onResume();
		this.startDiscovery();
	}

	// The BroadcastReceiver that listens for discovered devices and
    // changes the title when discovery is finished
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        	String action = intent.getAction();
        	if (D) Log.d(TAG, "onReceive " + action);

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                
                BluetoothDeviceWrapper w = new BluetoothDeviceWrapper(d);
                int i = mArrayAdapter.getPosition(w);
                if (i>-1)
                	mArrayAdapter.remove(w);
                mArrayAdapter.add(w);
                mArrayAdapter.notifyDataSetChanged();
            // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
            	startDiscovery();
            }
        }
    };

	

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (D) Log.d(TAG, "onCreate");
		
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);
        
        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);

        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mReceiver, filter);
        
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        registerReceiver(mReceiver, filter);

        // Get the local Bluetooth adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        
        if (mBtAdapter==null){
        	this.finish();
        	return;
        }
        
		mArrayAdapter = new ArrayAdapter<BluetoothDeviceWrapper>(this, R.layout.list_inquiry);
		mArrayAdapter.setNotifyOnChange(true);
		for (BluetoothDevice d: mBtAdapter.getBondedDevices()){
        	BluetoothDeviceWrapper w = new BluetoothDeviceWrapper(d);
        	Log.d(TAG, "Adding " + w);
        	mArrayAdapter.add(w);
        }
		
        mArrayAdapter.notifyDataSetChanged();
        
		ListView lv = getListView();
		lv.setTextFilterEnabled(true);

		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				if (D) Log.d(TAG, "onItemClickListener " + position);
				unregisterReceiver(mReceiver);
				mReceiver = null;
				Intent i = new Intent();
				i.putExtra(EXTRA_ADDRESS, mArrayAdapter.getItem(position).address);
				setResult(RESULT_OK, i);
				running = false;
				BluetoothInquiry.this.finish();
			}
		});
		lv.setAdapter(mArrayAdapter);
		this.running = true;
	}

	class BluetoothDeviceWrapper {
		String address;
		String name;
		
		public BluetoothDeviceWrapper(String address, String name){
			if (D) Log.d(TAG, "new wrapper " + address + ", " + name);
			this.address = address;
			this.name = name;
		}
		
		public BluetoothDeviceWrapper(BluetoothDevice device) {
			if (D) Log.d(TAG, "new wrapper " + device);
			this.address = device.getAddress();
			this.name = device.getName();
		}

		public String toString(){
			return this.name + "\n" + this.address;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result
					+ ((address == null) ? 0 : address.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			BluetoothDeviceWrapper other = (BluetoothDeviceWrapper) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (address == null) {
				if (other.address != null)
					return false;
			} else if (!address.equals(other.address))
				return false;
			return true;
		}

		private BluetoothInquiry getOuterType() {
			return BluetoothInquiry.this;
		}
	}
}
