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
public class FaceDetector {
	static boolean classify(String file){
		//create classifier by lodaing xml data
		CascadeClassifier faceDetector = new CascadeClassifier(
						     "haarcascade_frontalface_default.xml");
		
		//load image
		Mat image = Imgcodecs.imread(file);
		
		//perform face detection (magic)
		MatOfRect faceDetections = new MatOfRect();
		faceDetector.detectMultiScale(image, faceDetections);
		
		return faceDetections.toArray().length > 0;
		
		//below code will draw rectangles around the faces and print them to an output file
		/*for (Rect rect : faceDetections.toArray()) {
			Imgproc.rectangle(image, new Point(rect.x, rect.y), 
			new Point(rect.x + rect.width, rect.y + rect.height),
						 new Scalar(0, 255, 0));
		
		String filename = "output.png";
		Imgcodecs.imwrite("./"+filename, image);*/

	}
	static void classifyDirectory(String directory){
		File folder = new File(directory);
		File[] images = folder.listFiles();
		int selfies = 0, other = 0;
		System.out.println("Classifying Selfies: ");
		for(File img : images){	
			System.out.print(directory + "/" + img.getName());
			//classify on image 
			if(classify(directory + "/" + img.getName())) {
				++selfies;
				System.out.println(" Selfie");
			}
			else {
				++other;
				System.out.println(" Other");
			}
			
		}
		System.out.println("Selfies " + selfies + "\nOther: "+other);
	}
	public static void main(String[] args){
		File JNI = new File("jni/libopencv_java331.so");
		System.load(JNI.getAbsolutePath());

		classifyDirectory("Images/Selfie");
		classifyDirectory("Images/Other");	
	}
}
