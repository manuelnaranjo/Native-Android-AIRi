package net.aircable.nativeairi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

public class AIRiService extends Thread {
	private final static int BUFFER_SIZE=150*1024; // assume no frame is going to be over 150KB
	private final static int BLOCK_SIZE=4096;
	public final static int CONNECTION_STABLISHED = 1;
	public final static int CONNECTION_FINISHED = 2;
	public final static int CONNECTION_FAILED = 3;
	public final static int PICTURE_AVAILABLE = 4;
	public final static int OUT_OF_MEMORY = -1;
	private final BluetoothSocket mSocket;
	private int head;
	private InputStream mIn;
	private OutputStream mOut;
	private final Handler mHandler;
	private static int[] mBuffer;
	private static byte[] sBuffer;
	private static byte[] mTempBuffer;
	
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
	private boolean dirty;
	
	public AIRiService(BluetoothSocket socket, Handler handler){
		super();
		
		if (sInstance != null) 
			throw new RuntimeException("Instance is running all ready");
		this.setName("AIRiService");
		this.setDaemon(true);
		this.mSocket = socket;
		this.head = 0;
		this.mHandler = handler;
		if (mBuffer == null)
			try {
				mBuffer = new int[BUFFER_SIZE];
			} catch (OutOfMemoryError e) {
				Log.e(TAG, "out of RAM", e);
				mHandler.obtainMessage(OUT_OF_MEMORY, BUFFER_SIZE).sendToTarget();
				this.running = false;
				return;
			}
		if (mTempBuffer == null)
			try {
				mTempBuffer = new byte[BLOCK_SIZE];
			} catch (OutOfMemoryError e) {
				Log.e(TAG, "out of RAM", e);
				mHandler.obtainMessage(OUT_OF_MEMORY, BLOCK_SIZE).sendToTarget();
				this.running = false;
				return;
			}
		if (sBuffer == null)
			try {
				sBuffer = new byte[BUFFER_SIZE];
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
		dirty = true;
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
		
		int i, start=0, end=0;
		int read;
		
		mHandler.postDelayed(new Runnable(){
			public void run() {
				mHandler.obtainMessage(CONNECTION_STABLISHED).sendToTarget();
			}
		}, 1000);
		
		//allocate enough space for a full non compressed frame
		while (this.running){
			try {
				read=this.mIn.read(mTempBuffer, 0, BLOCK_SIZE);
				if (head+read>=BUFFER_SIZE)
					head = 0;
				for (i=0; i<read;i++)
					mBuffer[i+head]=mTempBuffer[i] & 0xff;
				//if (D)Log.d(TAG, "Read " + read + " head " + head);
				if (read == -1)
					break;
				for (i=0; i<head+read-2; i++){
					if (mBuffer[i]==0xff) { 
						//bytes in java are signed, so we can't just compare to 0xff
						//if (D)Log.d(TAG, "Found 0xff " + i);
						i++;
						if (mBuffer[i]==0xd8){
							//if (D)Log.d(TAG, "Found 0xd8 " + i);
							start = i-1;
						} else if (mBuffer[i]==0xd9) {
							//if (D)Log.d(TAG, "Found 0xd9 " + i);
							end = i-1;
							if (start>-1 && end>start)
								i=head+read; // force loop to complete
						} //else
						//	if (D)Log.d(TAG, String.format("Found 0x%02x", mBuffer[i]));
					}
				}
				head+=read;
					
				if (start>-1 && end>start){
					//if (D) Log.d(TAG,
					//	"found frame start " + start + " end " + end);
					for (i=0; i<end-start+2; i++)
						sBuffer[i]=(byte)(mBuffer[i+start]&0xff);
					mHandler.post(new NewFrame(start, end+2));
					
					//b = new byte[head-end+2];
					//System.arraycopy(mBuffer, end, b, 0, b.length);
					//System.arraycopy(b, 0, mBuffer, 0, b.length);
					//head=b.length;
					head=0;
					start=end=-1;
					if (dirty)
						dirty = false;
				}
			} catch (IOException e) {
				e.printStackTrace();
				break;
			}
		}
		mHandler.obtainMessage(CONNECTION_FINISHED).sendToTarget();
		sInstance = null;
	}
	
	public boolean isDirty(){
		return dirty;
	}
	
	class NewFrame implements Runnable {
		int start, end;
		public NewFrame(int start, int end){
			this.start = start;
			this.end = end;
		}
			
		@Override
		public void run() {
			mHandler.obtainMessage(PICTURE_AVAILABLE, 0, end-start, 
					sBuffer).sendToTarget();
		}
		
	}
}
