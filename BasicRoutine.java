package org.reroutlab.code.auav.routines;

import java.util.HashMap;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapResponse;

//sockets
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.Socket;
import java.net.ServerSocket;

//openCV
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import java.io.*;

/**
 * BasicRoutine takes off, calibrates camera and lands
 * It does not sense from it's environment
 * Invoke this routine through external commands driver
 * <br><b>URL: coap:\\localhost:port\cr?</b>
 * <br>Example: coap:\\127.0.0.1:5117\cr?dn=rtn-dc=start-dp=BasicRoutine
 * <br>
 * Note: all AUAV drivers have resource component "cr" for coap resource
 *
 * @author  Christopher Charles Stewart
 * @version 1.0.3
 * @since   2017-10-01
 */
public class BasicRoutine extends org.reroutlab.code.auav.routines.AuavRoutines{
		/**
		 *	 Check forceStop often and safely end routine if set
		 */
		public boolean forceStop = false;

		/**
		 *	 Routines are Java Threads.  The run() function is the 
		 *	 starting point for execution. 
		 * @version 1.0.1
		 * @since   2017-10-01			 
		 */
		public void run() {	
			//This driver currently assumes AUAVSim is active
			
			//this loads the JNI for tensorflow java on linux (assumes jar is in $AUAVHome/routines
			File JNI = new File("../externalDLLs/libopencv_java331.so");
			System.load(JNI.getAbsolutePath());
			
			String succ = "";
			String args[] = params.split("-");
			String fileName = args[0].substring(3);
			String auavSim;
			
			succ = invokeDriver("org.reroutlab.code.auav.drivers.FlyDroneDriver",
					     "dc=lft-dp=AUAVsim", chResp );
			rtnSpin();
			rtnLock("free");

			succ = invokeDriver("org.reroutlab.code.auav.drivers.DroneGimbalDriver",
						    "dc=cal-dp=AUAVsim", chResp );
			rtnSpin();
			System.out.println("BasicRoutine: " + resp);
			rtnLock("free");
			
			System.out.println("Invoking camera driver using dir command");
			//set image directory
			succ = invokeDriver("org.reroutlab.code.auav.drivers.CaptureImageDriver",
					    "dc=dir-dp="+fileName+"-dp=AUAVsim", chResp);
			rtnSpin();
			System.out.println("BasicRoutine: " + resp);
			rtnLock("free");
				
			System.out.println("Invoking camera driver using get command");
			//get image and put it in said directory
			succ = invokeDriver("org.reroutlab.code.auav.drivers.CaptureImageDriver",
					    "dc=get-dp=AUAVsim", chResp);
			rtnSpin();
			System.out.println("BasicRoutine: " + resp);
			rtnLock("free");
			
			System.out.println("Invoking camera driver using dmp command");
			//dump image data to .dat file in given directory for picTrace driver
			succ = invokeDriver("org.reroutlab.code.auav.drivers.CaptureImageDriver",
					    "dc-dmp-dp=AUAVsim", chResp);
			rtnSpin();
			System.out.println("BasicRoutine: " + resp);
			rtnLock("free");

			System.out.println("Invoking PicTrace to set trace directory");
			//read index.dat file in provided directory
			succ = invokeDriver("org.reroutlab.code.auav.drivers.PicTraceDriver",
					    "dc=dir-dp="+fileName+"-dp-AUAVsim", chResp);
			rtnSpin();
			System.out.println("BasicRoutine: "+ resp);
			rtnLock("free");			

			byte[] pic;
			int picNum = 0;
			do{
				System.out.println("Querying PicTrace for image data for image: "+picNum);
				pic = new byte[0];			
				//get images from pictrace h2 database
				byte[] buff = new byte[1024];
				String query = "SELECT * FROM data";
				Socket client = null;

				succ = invokeDriver("org.reroutlab.code.auav.drivers.PicTraceDriver",
						    "dc=qrb-dp="+query+"-dp="+picNum+"-dp=AUAVsim", chResp);
				rtnSpin();
				try{
					client = new Socket("127.0.0.1", 44044); //connect to pictrace driver
					int k = -1;
					while((k = client.getInputStream().read(buff, 0, buff.length)) > -1){
						byte[] tbuff = new byte[pic.length + k];
						System.arraycopy(pic, 0, tbuff, 0, pic.length);
						System.arraycopy(buff, 0, tbuff, pic.length, k);
						pic = tbuff;
					}
					System.out.println(pic.length + "Bytes read from PicTrace");
					client.close();
				} catch(Exception e){
					System.out.println("Problem reading from PicTrace");
					e.printStackTrace();
				}
				
				System.out.println("BasicRoutine: "+ resp);
				rtnLock("free");	
				
				if(pic.length == 0) {
					System.out.println("No Picture "+picNum);
					break;
				}
				//write bytes to image	
				try{
					OutputStream out = new FileOutputStream("../trace.data/routine/test.jpg");
					out.write(pic);
					out.flush();
					out.close();
				} catch(Exception e){
					System.out.println("Problem writing image");
					e.printStackTrace();
				}


				System.out.println("bytes read: "+pic.length);
				//read image into openCV and classify
				if(classify("../trace.data/routine/test.jpg")){
					try{
						OutputStream out = new FileOutputStream("../trace.data/selfies/selfie"+picNum+".jpg");
						out.write(pic);
						out.flush();
						out.close();
					} catch(Exception e){
						System.out.println("Problem writing image");
						e.printStackTrace();
					}
					System.out.println("It's a selfie");
				}
				else
					System.out.println("Not a selfie");
				
				++picNum;
			} while(pic.length > 0);
			succ = invokeDriver("org.reroutlab.code.auav.drivers.FlyDroneDriver",
						    "dc=lnd-dp=AUAVsim", chResp );
			rtnSpin();	
			System.out.println("BasicRoutine: " + resp);
		}
				
		static boolean classify(String file){
                    //create classifier by lodaing xml data
                    CascadeClassifier faceDetector = new CascadeClassifier(
                                                         "../openCV/haarcascade_frontalface_default.xml"); 
                    //load image
                    Mat image = Imgcodecs.imread(file);
  
                    //perform face detection (magic)
                    MatOfRect faceDetections = new MatOfRect();
                    faceDetector.detectMultiScale(image, faceDetections);
    			
                    return faceDetections.toArray().length > 0;
          	}


		//  The code below is mostly template material
		//  Most routines will not change the code below
		//
		//
		//
		//
		//
		//  Christopher Stewart
		//  2017-10-1
		//

		private Thread t = null;
		private String csLock = "free";
		private String resp="";
		CoapHandler chResp = new CoapHandler() {
						@Override public void onLoad(CoapResponse response) {
								resp = response.getResponseText();
								rtnLock("barrier-1");
						}
						
						@Override public void onError() {
								System.err.println("FAILED");
								rtnLock("barrier-1");
						}};
				


		public BasicRoutine() {t = new Thread (this, "Main Thread");	}
		public String startRoutine() {
				if (t != null) {
						t.start(); return "BasicRoutine: Started";
				}
				return "BasicRoutine not Initialized";
		}
		public String stopRoutine() {
				forceStop = true;	return "BasicRoutine: Force Stop set";
		}
		synchronized void rtnLock(String value) {
				csLock = value;
		}
		public void rtnSpin() {
				while (csLock.equals("barrier-1") == false) {
						try { Thread.sleep(1000); }
						catch (Exception e) {}
				}

		}
		

		
}
