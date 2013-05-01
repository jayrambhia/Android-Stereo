/*
 * Copyright (C) 2011 HTC Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.evodisp;

import com.htc.view.DisplaySetting;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;

import java.io.File;
import java.io.IOException;
import java.util.List;

// S3D example of live camera preview (touch screen to toggle mode) and overlay text indicating 3D or 2D mode
public class MainActivity extends Activity implements SurfaceHolder.Callback {

	private final static String TAG = "S3DCameraActivity";
	/*
	 * CAMERA_STEREOSCOPIC Used to open S3D camera on devices running Android older than
	 * ICS, the id value is 2. For Android ICS devices, the id value is 100
	 * See how to check the device's Android version below in getS3DCamera()
	 */
	private final static int CAMERA_STEREOSCOPIC = 2;
	private final static int CAMERA_STEREOSCOPIC_ICS = 100;
	private boolean is3Denabled = true;
	private SurfaceHolder holder;
	private SurfaceView preview;
	private Camera camera;
	private TextView text;
	private int width, height;
	Mat img, gray;
	
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    //mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
		setContentView(R.layout.video);
		preview = (SurfaceView) findViewById(R.id.surface);
		text = (TextView) findViewById(R.id.text);
		holder = preview.getHolder();
		holder.addCallback(this);
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	public void surfaceCreated(SurfaceHolder holder) {
		camera = getS3DCamera();
		if (is3Denabled) {
			text.setText("S3D");
		} else {
			camera = get2DCamera();
			text.setText("2D");
		}
		text.setVisibility(View.VISIBLE);
	}

	public Camera get2DCamera() {
		Camera camera = null;
		try {
			camera = Camera.open();
			camera.setPreviewDisplay(holder);
		} catch (IOException ioe) {
			if (camera != null) {
				camera.release();
			}
			camera = null;
		} catch (RuntimeException rte) {
			if (camera != null) {
				camera.release();
			}
			camera = null;
		}
		return camera;
	}

	public Camera getS3DCamera() {
		Camera camera = null;
		int cameraID = Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH ?
				CAMERA_STEREOSCOPIC : CAMERA_STEREOSCOPIC_ICS;
		try {
			camera = Camera.open(cameraID);
			camera.setPreviewDisplay(holder);
			is3Denabled = true;
		} catch (IOException ioe) {
			if (camera != null) {
				camera.release();
			}
			camera = null;
		} catch (NoSuchMethodError nsme) {
			is3Denabled = false;
			text.setVisibility(View.VISIBLE);
			Log.w(TAG, Log.getStackTraceString(nsme));
		} catch (UnsatisfiedLinkError usle) {
			is3Denabled = false;
			text.setVisibility(View.VISIBLE);
			Log.w(TAG, Log.getStackTraceString(usle));
		} catch (RuntimeException re) {
			is3Denabled = false;
			text.setVisibility(View.VISIBLE);
			Log.w(TAG, Log.getStackTraceString(re));
			if (camera != null) {
				camera.release();
			}
			camera = null;
		}
		return camera;
	}

	public void surfaceDestroyed(SurfaceHolder surfaceholder) {
		stopPreview();
		holder = surfaceholder;
		enableS3D(false, surfaceholder.getSurface()); // to make sure it's off
	}

	private void stopPreview() {
		if (camera != null) {
			camera.stopPreview();
			camera.release();
			camera = null;
		}
	}

	private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
		final double ASPECT_TOLERANCE = 0.05;
		double targetRatio = (double) w / h;
		if (sizes == null)
			return null;

		Size optimalSize = null;
		double minDiff = Double.MAX_VALUE;

		int targetHeight = h;

		for (Size size : sizes) {
			Log.i(TAG, String.format("width=%d height=%d", size.width, size.height));
			double ratio = (double) size.width / size.height;
			if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
				continue;
			if (Math.abs(size.height - targetHeight) < minDiff) {
				optimalSize = size;
				minDiff = Math.abs(size.height - targetHeight);
			}
		}
		if (optimalSize == null) {
			minDiff = Double.MAX_VALUE;
			for (Size size : sizes) {
				if (Math.abs(size.height - targetHeight) < minDiff) {
					optimalSize = size;
					minDiff = Math.abs(size.height - targetHeight);
				}
			}
		}
		return optimalSize;
	}

	public void surfaceChanged(SurfaceHolder surfaceholder, int format, int w, int h) {
		holder = surfaceholder;
		width = w;
		height = h;
		startPreview(width, height);
	}

	private void startPreview(int w, int h) {
		if (camera != null) {
			Camera.Parameters parameters = camera.getParameters();
			List<Size> sizes = parameters.getSupportedPreviewSizes();
			Size optimalSize = getOptimalPreviewSize(sizes, w, h);
			parameters.setPreviewSize(optimalSize.width, optimalSize.height);
			Log.i(TAG, "optimalSize.width=" + optimalSize.width
					+ " optimalSize.height=" + optimalSize.height);
			camera.setParameters(parameters);
			camera.startPreview();
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
		//	toggle();
			//Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE); 
            //startActivityForResult(cameraIntent, 1337);
			int bufferSize = width * height * 3;
			byte[] mPreviewBuffer = null;

			// New preview buffer.
			mPreviewBuffer = new byte[bufferSize + 4096];

			// with buffer requires addbuffer.
			camera.addCallbackBuffer(mPreviewBuffer);
			camera.setPreviewCallbackWithBuffer(mCameraCallback);
			break;
		default:
			break;
		}
		return true;
	}
	
	

	private final Camera.PreviewCallback mCameraCallback = new Camera.PreviewCallback() {
	public void onPreviewFrame(byte[] data, Camera c) {
		Log.d(TAG, "ON Preview frame");
		img = new Mat(height, width, CvType.CV_8UC1);
		gray = new Mat(height, width, CvType.CV_8UC3);
		img.put(0, 0, data);		
		
		
		
		Imgproc.cvtColor(img, gray, Imgproc.COLOR_YUV420sp2RGB);
		String pixvalue = String.valueOf(gray.get(300, 400)[0]);
		String pixval1 = String.valueOf(gray.get(300, 400+width/2)[0]);
		Log.d(TAG, pixvalue);
		Log.d(TAG, pixval1);
		File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
		String filename = "evodispimg2.jpg";
		File file = new File(path, filename);

		Boolean bool = null;
		filename = file.toString();
		bool = Highgui.imwrite(filename, gray);
		
		path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
		filename = "evodispimg3.jpg";
		file = new File(path, filename);

		bool = null;
		filename = file.toString();
		bool = Highgui.imwrite(filename, img);

		if (bool == true)
		    Log.d(TAG, "SUCCESS writing image to external storage");
		else
		    Log.d(TAG, "Fail writing image to external storage");
	        // to do the camera image split processing using "data"
	}
	};

/*	public void toggle() {
		is3Denabled = !is3Denabled;
		stopPreview();
		if (is3Denabled) {
			camera = getS3DCamera();
		}
		if (!is3Denabled) {
			camera = get2DCamera();
		}
		startPreview(width, height);
		if (is3Denabled) {
			text.setText("S3D");
		} else {
			text.setText("2D");
		}
	}*/

	private void enableS3D(boolean enable, Surface surface) {
		Log.i(TAG, "enableS3D(" + enable + ")");
		int mode = DisplaySetting.STEREOSCOPIC_3D_FORMAT_SIDE_BY_SIDE;
		if (!enable) {
			mode = DisplaySetting.STEREOSCOPIC_3D_FORMAT_OFF;
		} else {
			is3Denabled = true;
		}
		boolean formatResult = true;
		try {
			formatResult = DisplaySetting
					.setStereoscopic3DFormat(surface, mode);
		} catch (NoClassDefFoundError e) {
			android.util.Log.i(TAG,
					"class not found - S3D display not available");
			is3Denabled = false;
		}
		Log.i(TAG, "return value:" + formatResult);
		if (!formatResult) {
			android.util.Log.i(TAG, "S3D format not supported");
			is3Denabled = false;
		}
	}

}
