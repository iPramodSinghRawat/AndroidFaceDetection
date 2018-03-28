package it.ipramodsinghrawat.alphafacedetection;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;

import java.io.*;

import android.app.*;
import android.os.*;

import com.microsoft.projectoxford.face.*;
import com.microsoft.projectoxford.face.contract.*;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.FaceServiceClient;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.Landmark;

public class MainActivity extends AppCompatActivity {

    private ProgressDialog detectionProgressDialog;

    ImageView userImageView;
    TextView resultTextView;

    int REQUEST_CAMERA = 0, SELECT_FILE = 1;

    private FaceServiceClient faceServiceClient;
    private FaceDetector detector;
    Bitmap editedBitmap;
    Bitmap imagePickedBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        userImageView = findViewById(R.id.userImageView);
        resultTextView = findViewById(R.id.resultTextView);

        faceServiceClient = new FaceServiceRestClient(getString(R.string.m_azure_face_api_end_point), getString(R.string.face_api_subscription_key));
        detectionProgressDialog = new ProgressDialog(this);

        detector = new FaceDetector.Builder(getApplicationContext())
                .setTrackingEnabled(false)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

    }

    public void selectImage(View view) {
        final CharSequence[] items = {"Take Photo", "Choose from Library", "Cancel"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Photo!");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                if (items[item].equals("Take Photo")) {

                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(intent, REQUEST_CAMERA);

                } else if (items[item].equals("Choose from Library")) {

                    Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    intent.setType(MediaStore.Images.Media.CONTENT_TYPE);
                    startActivityForResult(Intent.createChooser(intent, "Select File"), SELECT_FILE);

                } else if (items[item].equals("Cancel")) {
                    dialog.dismiss();
                }
            }
        });
        builder.show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == SELECT_FILE){
                try {
                    imagePickedBitmap = decodeBitmapUri(this, data.getData());
                    userImageView.setImageBitmap(imagePickedBitmap);
                    processImageForFaceDet();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }else if (requestCode == REQUEST_CAMERA){

                imagePickedBitmap = (Bitmap) data.getExtras().get("data");
                userImageView.setImageBitmap(imagePickedBitmap);
                processImageForFaceDet();

            }
        }
    }

    public void processImageForFaceDet(){
        final CharSequence[] items = {"Google", "MicroSoft", "Cancel"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chose Method");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                if (items[item].equals("Google")) {
                    try {
                        googleScanFaces();
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), "Failed to load Image", Toast.LENGTH_SHORT).show();
                    }
                } else if (items[item].equals("MicroSoft")) {
                    //microSoftFaceDetect(null);
                    microsoftDetectAndFrame();
                } else if (items[item].equals("Cancel")) {
                    dialog.dismiss();
                }
            }
        });
        builder.show();
    }

    private Bitmap decodeBitmapUri(Context ctx, Uri uri) throws FileNotFoundException {
        int targetW = 600;
        int targetH = 600;
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;

        // Set internal configuration to RGB_565
        //bmOptions.inPreferredConfig = Bitmap.Config.RGB_565;

        BitmapFactory.decodeStream(ctx.getContentResolver().openInputStream(uri), null, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        int scaleFactor = Math.min(photoW / targetW, photoH / targetH);
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;

        return BitmapFactory.decodeStream(ctx.getContentResolver()
                .openInputStream(uri), null, bmOptions);
    }

    //google method2 to detect face //working
    private void googleScanFaces() throws Exception {
        // Set internal configuration to RGB_565
        //BitmapFactory.Options bitmap_options = new BitmapFactory.Options();
        //bitmap_options.inPreferredConfig = Bitmap.Config.RGB_565;
        //bitmap = BitmapFactory.decodeFile(image_fn, bitmap_options);
        if (detector.isOperational() && imagePickedBitmap != null) {
            editedBitmap = Bitmap.createBitmap(imagePickedBitmap.getWidth(), imagePickedBitmap
                    .getHeight(), imagePickedBitmap.getConfig());
            float scale = getResources().getDisplayMetrics().density;
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.rgb(255, 61, 61));
            paint.setTextSize((int) (14 * scale));
            paint.setShadowLayer(1f, 0f, 1f, Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            Canvas canvas = new Canvas(editedBitmap);
            canvas.drawBitmap(imagePickedBitmap, 0, 0, paint);
            Frame frame = new Frame.Builder().setBitmap(editedBitmap).build();
            SparseArray<com.google.android.gms.vision.face.Face> faces = detector.detect(frame);
            resultTextView.setText(null);
            for (int index = 0; index < faces.size(); ++index) {
                com.google.android.gms.vision.face.Face face = faces.valueAt(index);
                canvas.drawRect(
                        face.getPosition().x,
                        face.getPosition().y,
                        face.getPosition().x + face.getWidth(),
                        face.getPosition().y + face.getHeight(), paint);

                resultTextView.setText(resultTextView.getText() + "Face " + (index + 1) + "\n");
                resultTextView.setText(resultTextView.getText() + "Smile probability:" + "\n");
                resultTextView.setText(resultTextView.getText() + String.valueOf(face.getIsSmilingProbability()) + "\n");
                resultTextView.setText(resultTextView.getText() + "Left Eye Open Probability: " + "\n");
                resultTextView.setText(resultTextView.getText() + String.valueOf(face.getIsLeftEyeOpenProbability()) + "\n");
                resultTextView.setText(resultTextView.getText() + "Right Eye Open Probability: " + "\n");
                resultTextView.setText(resultTextView.getText() + String.valueOf(face.getIsRightEyeOpenProbability()) + "\n");
                resultTextView.setText(resultTextView.getText() + "---------" + "\n");

                for (Landmark landmark : face.getLandmarks()) {
                    int cx = (int) (landmark.getPosition().x);
                    int cy = (int) (landmark.getPosition().y);
                    canvas.drawCircle(cx, cy, 5, paint);
                }
            }

            if (faces.size() == 0) {
                resultTextView.setText("Scan Failed: Found nothing to scan");
            } else {
                userImageView.setImageBitmap(editedBitmap);
                resultTextView.setText(resultTextView.getText() + "No of Faces Detected: " + "\n");
                resultTextView.setText(resultTextView.getText() + String.valueOf(faces.size()) + "\n");
                resultTextView.setText(resultTextView.getText() + "---------" + "\n");
            }
        } else {
            resultTextView.setText("Could not set up the detector!");
        }
    }

    //using MicroSoftLibrary
    //Detect faces by uploading face images
    //Frame faces after detection
    private void microsoftDetectAndFrame(){
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        imagePickedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);

        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(outputStream.toByteArray());

        AsyncTask<InputStream, String, com.microsoft.projectoxford.face.contract.Face[]> detectTask =
                new AsyncTask<InputStream, String, com.microsoft.projectoxford.face.contract.Face[]>() {
                    @Override
                    protected com.microsoft.projectoxford.face.contract.Face[] doInBackground(InputStream... params) {
                        try {
                            publishProgress("Detecting...");
                            com.microsoft.projectoxford.face.contract.Face[] result = faceServiceClient.detect(
                                    params[0],
                                    true,         // returnFaceId
                                    false,        // returnFaceLandmarks
                                    new com.microsoft.projectoxford.face.FaceServiceClient.FaceAttributeType[] {
                                            FaceServiceClient.FaceAttributeType.Age,
                                            FaceServiceClient.FaceAttributeType.Gender,
                                            FaceServiceClient.FaceAttributeType.Smile,
                                            FaceServiceClient.FaceAttributeType.Glasses,
                                            FaceServiceClient.FaceAttributeType.FacialHair,
                                            FaceServiceClient.FaceAttributeType.Emotion,
                                            FaceServiceClient.FaceAttributeType.HeadPose,
                                            FaceServiceClient.FaceAttributeType.Accessories,
                                            FaceServiceClient.FaceAttributeType.Blur,
                                            FaceServiceClient.FaceAttributeType.Exposure,
                                            FaceServiceClient.FaceAttributeType.Hair,
                                            FaceServiceClient.FaceAttributeType.Makeup,
                                            FaceServiceClient.FaceAttributeType.Noise,
                                            FaceServiceClient.FaceAttributeType.Occlusion
                                    }           // returnFaceAttributes: a string like "age, gender"
                            );
                            if (result == null){
                                publishProgress("Detection Finished. Nothing detected");
                                return null;
                            }
                            publishProgress(
                                    String.format("Detection Finished. %d face(s) detected",
                                            result.length));
                            return result;
                        } catch (Exception e) {
                            publishProgress("Detection failed");
                            return null;
                        }
                    }

                    @Override
                    protected void onPreExecute() {
                        detectionProgressDialog.show();
                    }
                    @Override
                    protected void onProgressUpdate(String... progress) {

                        detectionProgressDialog.setMessage(progress[0]);
                    }
                    @Override
                    protected void onPostExecute(com.microsoft.projectoxford.face.contract.Face[] faceDetResult) {
                        detectionProgressDialog.dismiss();
                        if (faceDetResult == null) return;
                        //ImageView imageView = (ImageView)findViewById(R.id.imageView1);
                        resultTextView.setText(String.format("%d face(s) detected",
                                faceDetResult.length));

                        for(int i=0; i<faceDetResult.length;i++){
                            String face_description = String.format("\n\n Age: %s  Gender: %s\nHair: %s  FacialHair: %s\nMakeup: %s  %s\nForeheadOccluded: %s  Blur: %s\nEyeOccluded: %s  %s\n" +
                                            "MouthOccluded: %s  Noise: %s\nGlassesType: %s\nHeadPose: %s\nAccessories: %s",
                                    faceDetResult[i].faceAttributes.age,
                                    faceDetResult[i].faceAttributes.gender,
                                    getHair(faceDetResult[i].faceAttributes.hair),
                                    getFacialHair(faceDetResult[i].faceAttributes.facialHair),
                                    getMakeup((faceDetResult[i]).faceAttributes.makeup),
                                    getEmotion(faceDetResult[i].faceAttributes.emotion),
                                    faceDetResult[i].faceAttributes.occlusion.foreheadOccluded,
                                    faceDetResult[i].faceAttributes.blur.blurLevel,
                                    faceDetResult[i].faceAttributes.occlusion.eyeOccluded,
                                    faceDetResult[i].faceAttributes.exposure.exposureLevel,
                                    faceDetResult[i].faceAttributes.occlusion.mouthOccluded,
                                    faceDetResult[i].faceAttributes.noise.noiseLevel,
                                    faceDetResult[i].faceAttributes.glasses,
                                    getHeadPose(faceDetResult[i].faceAttributes.headPose),
                                    getAccessories(faceDetResult[i].faceAttributes.accessories)
                            );
                            resultTextView.setText(resultTextView.getText()+face_description);
                        }
                        userImageView.setImageBitmap(drawFaceRectanglesOnBitmap(imagePickedBitmap, faceDetResult));
                        imagePickedBitmap.recycle();
                    }
                };
        detectTask.execute(inputStream);
    }

    private String getHair(Hair hair) {
        if (hair.hairColor.length == 0){
            if (hair.invisible)
                return "Invisible";
            else
                return "Bald";
        }else{
            int maxConfidenceIndex = 0;
            double maxConfidence = 0.0;

            for (int i = 0; i < hair.hairColor.length; ++i){
                if (hair.hairColor[i].confidence > maxConfidence){
                    maxConfidence = hair.hairColor[i].confidence;
                    maxConfidenceIndex = i;
                }
            }

            return hair.hairColor[maxConfidenceIndex].color.toString();
        }
    }
    private String getFacialHair(FacialHair facialHair) {
        return (facialHair.moustache + facialHair.beard + facialHair.sideburns > 0) ? "Yes" : "No";
    }

    private String getMakeup(Makeup makeup) {
        return  (makeup.eyeMakeup || makeup.lipMakeup) ? "Yes" : "No" ;
    }

    private String getEmotion(Emotion emotion){
        String emotionType = "";
        double emotionValue = 0.0;
        if (emotion.anger > emotionValue){
            emotionValue = emotion.anger;
            emotionType = "Anger";
        }
        if (emotion.contempt > emotionValue){
            emotionValue = emotion.contempt;
            emotionType = "Contempt";
        }
        if (emotion.disgust > emotionValue){
            emotionValue = emotion.disgust;
            emotionType = "Disgust";
        }
        if (emotion.fear > emotionValue){
            emotionValue = emotion.fear;
            emotionType = "Fear";
        }
        if (emotion.happiness > emotionValue){
            emotionValue = emotion.happiness;
            emotionType = "Happiness";
        }
        if (emotion.neutral > emotionValue){
            emotionValue = emotion.neutral;
            emotionType = "Neutral";
        }
        if (emotion.sadness > emotionValue){
            emotionValue = emotion.sadness;
            emotionType = "Sadness";
        }
        if (emotion.surprise > emotionValue){
            emotionValue = emotion.surprise;
            emotionType = "Surprise";
        }
        return String.format("%s: %f", emotionType, emotionValue);
    }

    private String getHeadPose(HeadPose headPose){
        return String.format("Pitch: %s, Roll: %s, Yaw: %s", headPose.pitch, headPose.roll, headPose.yaw);
    }

    private String getAccessories(Accessory[] accessories) {
        if (accessories.length == 0){
            return "NoAccessories";
        }
        else{
            String[] accessoriesList = new String[accessories.length];
            for (int i = 0; i < accessories.length; ++i){
                accessoriesList[i] = accessories[i].type.toString();
            }
            return TextUtils.join(",", accessoriesList);
        }
    }
    //microsoft
    private static Bitmap drawFaceRectanglesOnBitmap(Bitmap originalBitmap, com.microsoft.projectoxford.face.contract.Face[] faces) {
        Bitmap bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.RED);
        paint.setTextSize(14);
        int stokeWidth = 2;
        paint.setStrokeWidth(stokeWidth);
        if (faces != null) {
            int face_count=0;
            for (com.microsoft.projectoxford.face.contract.Face face : faces) {
                face_count++;
                com.microsoft.projectoxford.face.contract.FaceRectangle faceRectangle = face.faceRectangle;

                canvas.drawText(String.valueOf(face_count), faceRectangle.left, faceRectangle.top, paint);

                canvas.drawRect(
                        faceRectangle.left,
                        faceRectangle.top,
                        faceRectangle.left + faceRectangle.width,
                        faceRectangle.top + faceRectangle.height,
                        paint);
            }
        }
        return bitmap;
    }

}
