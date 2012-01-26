package net.aircable.nativeairi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

public class AIRiService extends Thread {
	private final static int BUFFER_SIZE=256*1024; // max size of any picture
	// we read max 256 bytes, usually we get RFCOMM packages of 127 bytes
	private final static int BLOCK_SIZE=256;
	
	public final static int CONNECTION_STABLISHED = 1;
	public final static int CONNECTION_FINISHED = 2;
	public final static int CONNECTION_FAILED = 3;
	public final static int PICTURE_AVAILABLE = 4;
	public final static int OUT_OF_MEMORY = -1;
	
	private final BluetoothSocket mSocket;
	private InputStream mIn;
	private OutputStream mOut;
	private final Handler mHandler;
	
	// juergen: changed buffering
	public static byte[] readBuffer;
	public static byte[] mBuffer;
	private int head;
	
	public enum BUFSTATE { NOIMG, COPYING };
	public BUFSTATE mBufUsing = BUFSTATE.NOIMG;
	private enum IMGSTATE { FF_ATTN, NORMAL }
	private IMGSTATE mImageState = IMGSTATE.NORMAL;
	
	private boolean running;
	
	private final String TAG = "AIRiService";
	private final boolean D = true;
	
	public enum SIZE {
		QVGA,
		VGA,
		XGA,
		QXGA,
		P720,
		QVGA_Zoom1,
		QVGA_Zoom2,
		QVGA_Zoom3
	}
	
	public enum PAN {
		UP,
		DOWN,
		LEFT,
		RIGHT
	}
	
	private static AIRiService sInstance;
	
	public AIRiService( BluetoothSocket socket, Handler handler ){
		super();
		
		if (sInstance != null) 
			throw new RuntimeException("Instance is running all ready");
		this.setName("AIRiService");
		this.setDaemon(true);
		this.mSocket = socket;
		this.head = 0;
		this.mHandler = handler;
		// readBuffer, 256 Bytes
		if (readBuffer == null)
			try {
				readBuffer = new byte[BLOCK_SIZE];
			} catch (OutOfMemoryError e) {
				Log.e(TAG, "out of RAM", e);
				mHandler.obtainMessage(OUT_OF_MEMORY, BLOCK_SIZE).sendToTarget();
				this.running = false;
				return;
			}
		// buffer A = 256k
		if (mBuffer == null)
			try {
				mBuffer = new byte[BUFFER_SIZE];
				head = 0;
			} catch (OutOfMemoryError e) {
				Log.e(TAG, "out of RAM", e);
				mHandler.obtainMessage(OUT_OF_MEMORY, BUFFER_SIZE).sendToTarget();
				this.running = false;
				return;
			}
		sInstance = this;
		this.running = true;
	}
	
	public void stopService() {
		this.running = false;
	}
	
	private void close(){
		try {
			mSocket.close();
		} catch (IOException e2){
			Log.e(TAG, "unable to close() during connection failure", e2);
		}
	}
	
	private boolean sendCommand(char l, String arg){
		try {
			this.mOut.write('$');
			this.mOut.write(l);
			this.mOut.write(arg.getBytes());
			this.mOut.write(13);
			this.mOut.write(10);
			this.mOut.flush();
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean setSize(SIZE s) {
		String arg = "";
		if (s==SIZE.QVGA)
			arg="1";
		else if (s==SIZE.VGA)
			arg="2";
		else if (s==SIZE.XGA)
			arg="3";
		else if (s==SIZE.QXGA)
			arg="4";
		else if (s==SIZE.P720)
			arg="5";
		else if (s==SIZE.QVGA_Zoom1)
			arg="11";
		else if (s==SIZE.QVGA_Zoom2)
			arg="12";
		else if (s==SIZE.QVGA_Zoom3)
			arg="13";
		return this.sendCommand('S', arg);
	}

	public boolean setFlash(boolean s) {
		return this.sendCommand('F', s==true?"1":"0");
	}
	
	public boolean doPan(PAN p){
		String arg = "";
		if (p==PAN.UP)
			arg="U";
		else if (p==PAN.DOWN)
			arg="D";
		else if (p==PAN.LEFT)
			arg="L";
		else if (p==PAN.RIGHT)
			arg="R";
		return this.sendCommand('P', arg);		
	}
	
	public boolean setExposure(int v){
		if (v<0)
			v=0;
		if (v>31)
			v=31;
		return this.sendCommand('E', ""+v);
	}

	public boolean setLink(boolean s){
		return this.sendCommand('L', s==true?"1":"0");
	}
	
	public boolean setDate(Date d){
		//TODO: implement
		return false;
	}
	
	public boolean takePicture(String fName){
		return this.sendCommand('T', fName);
	}
	
	public void run(){
		if (D) Log.d(TAG, "Connecting");
		try {
			mSocket.connect(); 
		} catch (IOException e) {
			// close the socket
			close();
			mHandler.obtainMessage(CONNECTION_FAILED).sendToTarget();
			Log.e(TAG, "Failed", e);
			sInstance = null;
			return;
		}
		
		if (D) Log.d(TAG, "Getting input streams");
		try {
			// this will block and return only if the connection was established
			this.mIn = mSocket.getInputStream();
			this.mOut = mSocket.getOutputStream();					
		} catch (IOException e){
			Log.e(TAG, "Failed", e);
			close();
			mHandler.obtainMessage(CONNECTION_FAILED).sendToTarget();
			sInstance = null;
			return;
		}
		
		int i, read;
		
		mHandler.postDelayed(new Runnable(){
			public void run() {
				mHandler.obtainMessage(CONNECTION_STABLISHED).sendToTarget();
			}
		}, 1000);
		
		//process each block that comes from the BT socket
		while (this.running){
			try {
				// get next block, read up to 512 Bytes
				read = this.mIn.read( readBuffer, 0, BLOCK_SIZE );
				if (D)Log.d(TAG, "Read " + read + " head " + head );
				
				// copy bytes to buffer, look for start of image 
				for( i=0; i < read; i++ ){
					
					// state NORMAL
					if( mImageState == IMGSTATE.NORMAL ) {

						// after JPEG_START we copy the JPEG byte into buffer
						if( mBufUsing == BUFSTATE.COPYING )
							mBuffer[ head++ ] = readBuffer[ i ];
						// change state if we got an 0xFF
						if( readBuffer[ i ] == -1 )
							mImageState = IMGSTATE.FF_ATTN;
						
					// state 0xFF was read before
					} else if( mImageState == IMGSTATE.FF_ATTN ) {
					
						// 0xD8 START JPEG
						if( readBuffer[ i ] == -40 ) { 
							// start new image at index zero
							if (D)Log.d(TAG, "JPEG start" );
							mImageState = IMGSTATE.NORMAL;
							mBufUsing = BUFSTATE.COPYING;
							// process the previous ignored 0xFF
							mBuffer[ 0 ] = -1;
							mBuffer[ 1 ] = readBuffer[ i ];
							head = 2;
		
						// 0xD9 END JPEG
						} else if( readBuffer[ i ] == -39 ){ 
							if (D)Log.d(TAG, "JPEG size " + head );
							// this is a END IMAGE then
							mImageState = IMGSTATE.NORMAL;
							mBufUsing = BUFSTATE.NOIMG;
							
							// write end marker into buffer
							mBuffer[ head++ ] = readBuffer[ i ];
							
							// make message and send
							byte[] b = new byte[ head ];
							System.arraycopy( mBuffer, 0, b, 0, b.length);
							// send message which buffer has the image
							mHandler.obtainMessage( PICTURE_AVAILABLE, 0, head, b ).sendToTarget();
														
						} else { 
							// everything else, just copy and back to normal
							mBuffer[ head++ ] = readBuffer[ i ];
							mImageState = IMGSTATE.NORMAL;
						}
							
					}
					

				}
			} catch (IOException e) {
				e.printStackTrace();
				break;
			}
		}
		mHandler.obtainMessage(CONNECTION_FINISHED).sendToTarget();
		sInstance = null;
	}
}
