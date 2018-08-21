package com.luugiathuy.apps.remotebluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

public class BluetoothCommandService {
	// Debugging
    private static final String TAG = "BluetoothCommandService";
    private static final boolean D = true;

    // Unique UUID for this application
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    
    
    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
//    private BluetoothDevice mSavedDevice;
//    private int mConnectionLostCount;
    
    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    
    // Constants that indicate command to computer
    public static final int EXIT_CMD = -1;
    public static final int VOL_UP = 1;
    public static final int VOL_DOWN = 2;
    public static final int MOUSE_MOVE = 3;
    
    /**
     * Constructor. Prepares a new BluetoothChat session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public BluetoothCommandService(Context context, Handler handler) {
    	mAdapter = BluetoothAdapter.getDefaultAdapter();
    	mState = STATE_NONE;
    	//mConnectionLostCount = 0;
    	mHandler = handler;
    }
    
    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(RemoteBluetooth.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }
    
    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        setState(STATE_LISTEN);
    }
    
    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
    	if (D) Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }
    
    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (D) Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(RemoteBluetooth.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(RemoteBluetooth.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // save connected device
        //mSavedDevice = device;
        // reset connection lost count
        //mConnectionLostCount = 0;
        
        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
        
        setState(STATE_NONE);
    }
    
    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }
    

    
    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed(String e) {
        setState(STATE_LISTEN);

        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(RemoteBluetooth.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(RemoteBluetooth.TOAST, e);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
//        mConnectionLostCount++;
//        if (mConnectionLostCount < 3) {
//        	// Send a reconnect message back to the Activity
//	        Message msg = mHandler.obtainMessage(RemoteBluetooth.MESSAGE_TOAST);
//	        Bundle bundle = new Bundle();
//	        bundle.putString(RemoteBluetooth.TOAST, "Device connection was lost. Reconnecting...");
//	        msg.setData(bundle);
//	        mHandler.sendMessage(msg);
//	        
//        	connect(mSavedDevice);   	
//        } else {
        	setState(STATE_LISTEN);
	        // Send a failure message back to the Activity
	        Message msg = mHandler.obtainMessage(RemoteBluetooth.MESSAGE_TOAST);
	        Bundle bundle = new Bundle();
	        bundle.putString(RemoteBluetooth.TOAST, "Device connection was lost");
	        msg.setData(bundle);
	        mHandler.sendMessage(msg);
//        }
    }
    
    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                Log.e("device","error- "+e);

                try {
                    mmSocket =(BluetoothSocket) mmDevice.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(mmDevice,1);
                    mmSocket.connect();
                } catch (IllegalAccessException e1) {
                    connectionFailed(" "+e1);
                    e1.printStackTrace();
                } catch (InvocationTargetException e1) {
                    e1.printStackTrace();
                    connectionFailed(" "+e1);
                } catch (NoSuchMethodException e1) {
                    e1.printStackTrace();
                    connectionFailed(" "+e1);
                } catch (IOException e1) {
                    e1.printStackTrace();
                    connectionFailed(" "+e1);
                }

                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                // Start the service over to restart listening mode
                BluetoothCommandService.this.start();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothCommandService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        int frameSequence=0;
        boolean isConfigFrame=true;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    int bytes = mmInStream.read(buffer);

                    Log.v("bufferconfig",buffer[1]+"");

                    if(buffer[1]== 0x55 && isConfigFrame)
                    {
                        byte[] configBuffer=new byte[]{(byte) 0xa5,0x55,0x01,0x00, (byte) 0xa2};
                        write(configBuffer);
                        isConfigFrame=false;
                    }
                    else if(!isConfigFrame && Integer.parseInt(String.valueOf(buffer[1]))==-86){

                    //    public static String byteArrayToHex(byte[] a){
                    StringBuilder sb = new StringBuilder(buffer.length * 2);
                    for (byte b : buffer)
                        sb.append(String.format("%02x", b));
                    String bufferSteing = sb.toString();



                        Log.v("bytes", bufferSteing);


                        byte[] writeBuffer = new byte[]{(byte) 0xa5, (byte) 0xaa, 0x02, 0x00, 0x00, (byte) 0xf7};


                      //  write(writeBuffer);
                    //    isFirstFrame = false;


                       /*String frameNo = String.valueOf(buffer[3]);
                        frameSequence = Integer.parseInt(frameNo, 16);

                        if (frameSequence < 0) {
                            frameSequence = 1;
                        }*/
                        writeBuffer[3] = buffer[3];

                        writeBuffer[1]=buffer[1];


                        StringBuilder sb01 = new StringBuilder(writeBuffer.length * 2);
                        for (int i = 0; i < 5; i++)
                            sb01.append(String.format("%02x", writeBuffer[i]));
                        String bufferSteing01 = sb01.toString();
                        Log.v("ackstring", bufferSteing01);


                        writeBuffer[5] = (byte) CRC_check(bufferSteing01);


                                             StringBuilder sb02 = new StringBuilder(writeBuffer.length * 2);
                        for (byte b : writeBuffer)
                            sb02.append(String.format("%02x", b));
                        String bufferSteing02 = sb02.toString();

                        Log.v("hex", String.valueOf(writeBuffer[5]));

                        // for(int i=0;i<5;i++)
                        //{
                        Log.v("byte", bufferSteing02);
                        //  }
                        StringBuilder sb04 = new StringBuilder(buffer.length * 2);
                            for(int i=0;i<59;i++)
                            sb04.append(String.format("%02x", buffer[i]));
                        String bufferSteing04 = sb04.toString();

                        Log.v("hex", String.valueOf(bufferSteing04));

                        int crcRead = CRC_check(bufferSteing04);
                        


                        Log.v("Crc check",buffer[59]+" "+crcRead);

                        if (buffer[60] == crcRead) {

                            write(writeBuffer);
                        }
                        frameSequence++;


                        }
                        // Send the obtained bytes to the UI Activity
                        mHandler.obtainMessage(RemoteBluetooth.MESSAGE_READ, bytes, -1, buffer)
                                .sendToTarget();
                    } catch(IOException e){
                        Log.e(TAG, "disconnected", e);
                        connectionLost();
                        break;
                    }
                }
            }


        public char CRC_check(String dump)
        {
            char[] crc_table={
                    0x00,0x5e,0xbc,0xe2,0x61,0x3f,0xdd,0x83,0xc2,0x9c,0x7e,0x20,0xa3,0xfd,0x1f,0x41,
                    0x9d,0xc3,0x21,0x7f,0xfc,0xa2,0x40,0x1e,0x5f,0x01,0xe3,0xbd,0x3e,0x60,0x82,0xdc,
                    0x23,0x7d,0x9f,0xc1,0x42,0x1c,0xfe,0xa0,0xe1,0xbf,0x5d,0x03,0x80,0xde,0x3c,0x62,
                    0xbe,0xe0,0x02,0x5c,0xdf,0x81,0x63,0x3d,0x7c,0x22,0xc0,0x9e,0x1d,0x43,0xa1,0xff,
                    0x46,0x18,0xfa,0xa4,0x27,0x79,0x9b,0xc5,0x84,0xda,0x38,0x66,0xe5,0xbb,0x59,0x07,
                    0xdb,0x85,0x67,0x39,0xba,0xe4,0x06,0x58,0x19,0x47,0xa5,0xfb,0x78,0x26,0xc4,0x9a,
                    0x65,0x3b,0xd9,0x87,0x04,0x5a,0xb8,0xe6,0xa7,0xf9,0x1b,0x45,0xc6,0x98,0x7a,0x24,
                    0xf8,0xa6,0x44,0x1a,0x99,0xc7,0x25,0x7b,0x3a,0x64,0x86,0xd8,0x5b,0x05,0xe7,0xb9,
                    0x8c,0xd2,0x30,0x6e,0xed,0xb3,0x51,0x0f,0x4e,0x10,0xf2,0xac,0x2f,0x71,0x93,0xcd,
                    0x11,0x4f,0xad,0xf3,0x70,0x2e,0xcc,0x92,0xd3,0x8d,0x6f,0x31,0xb2,0xec,0x0e,0x50,
                    0xaf,0xf1,0x13,0x4d,0xce,0x90,0x72,0x2c,0x6d,0x33,0xd1,0x8f,0x0c,0x52,0xb0,0xee,
                    0x32,0x6c,0x8e,0xd0,0x53,0x0d,0xef,0xb1,0xf0,0xae,0x4c,0x12,0x91,0xcf,0x2d,0x73,
                    0xca,0x94,0x76,0x28,0xab,0xf5,0x17,0x49,0x08,0x56,0xb4,0xea,0x69,0x37,0xd5,0x8b,
                    0x57,0x09,0xeb,0xb5,0x36,0x68,0x8a,0xd4,0x95,0xcb,0x29,0x77,0xf4,0xaa,0x48,0x16,
                    0xe9,0xb7,0x55,0x0b,0x88,0xd6,0x34,0x6a,0x2b,0x75,0x97,0xc9,0x4a,0x14,0xf6,0xa8,
                    0x74,0x2a,0xc8,0x96,0x15,0x4b,0xa9,0xf7,0xb6,0xe8,0x0a,0x54,0xd7,0x89,0x6b,0x35
            };



            // String s1 = "a5";
//	 String s2 = "55";


            char buff = 0;
            String s3= "00";
            for(int i=0;i<dump.length();i++) {
                String s1=s3;

                String s2=dump.substring(i,i+2);
                int i1=Integer.parseInt(s1,16);
                int i2=Integer.parseInt(s2,16);


                int res=i1^i2;

                buff=crc_table[res];
                String hex=String.format("%02x", (int) buff );
                s3=hex;


                i++;
            }
            Log.v("crc",s3);
            return buff;
        }




        boolean isConnection=true;

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                String bufferstring="a5550100a2";
             //   byte [] buffer03=new byte[]{(byte) 0xa5, Byte.parseByte("ffaa"),0x01,0x00, (byte) 0xa2};
                byte buffer01[]=bufferstring.getBytes();
                byte [] buffer02=new byte[]{(byte) 0xa5,0x55,0x01,0x00, (byte) 0xa2};


                //for(int i=0;i<100000;i++) {
                    mmOutStream.write(buffer);
                  //  Thread.sleep(1000);
                //}
                //
                //Share the sent message back to the UI Activity
//                mHandler.obtainMessage(BluetoothChat.MESSAGE_WRITE, -1, -1, buffer)
//                        .sendToTarget();
            } catch (Exception e) {
                Log.e(TAG, "Exception during write", e);
            }
        }
        
     /*   public void write(int out) {
        	try {

        	    byte[] buffer01=new byte[1024];
                String bufferstring="a5550100a2";
                byte [] buffer03=new byte[]{(byte) 0xa5, (byte) 0x55,0x02,0x00,0x00, (byte) 0x1c};
                byte [] buffer02=new byte[]{(byte) 0xa5,0x55,0x01,0x00, (byte) 0xa2};

                if(isConnection) {
                    mmOutStream.write(buffer02);
                    isConnection=false;
                }
                else{
                  //  for(int i=0;i<1000000;i++) {
                        mmOutStream.write(buffer03);

                    //    Thread.sleep(1000);
                    //}
                }

                // Share the sent message back to the UI Activity
//                mHandler.obtainMessage(BluetoothChat.MESSAGE_WRITE, -1, -1, buffer)
//                        .sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
*/
        public void cancel() {
            try {
            	mmOutStream.write(EXIT_CMD);
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}