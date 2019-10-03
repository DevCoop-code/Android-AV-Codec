package com.hankyojeong.mediacodecusage;

import androidx.appcompat.app.AppCompatActivity;

import android.content.res.AssetManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = "MC";
    private static String SAMPLE_VIDEO = null;
    private PlayerThread mPlayer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SAMPLE_VIDEO = "android.resource://" + getPackageName() + "/" + R.raw.overwatchhighlight;

        //SurfaceHolder Callback
        SurfaceHolder.Callback callback = new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                Log.d(LOG_TAG, "surfaceCreated() calling");
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
                Log.d(LOG_TAG, "surfaceChanged() calling");
                if (mPlayer==null)
                {
                    mPlayer = new PlayerThread(surfaceHolder.getSurface());
                    mPlayer.start();
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                Log.d(LOG_TAG, "surfaceDestroyed() calling");
                if(mPlayer != null){
                    mPlayer.interrupt();
                }
            }
        };

        SurfaceView sv = new SurfaceView(this);
        sv.getHolder().addCallback(callback);
        setContentView(sv);
    }

    private class PlayerThread extends Thread{
        /*
        encoder/decoder components
        (It is part of the Android low-level multimedia support infrastructure)
         */
        private MediaCodec decoder;
        /*
        extraction of demuxed, typically encoded, media data from a data source
         */
        private MediaExtractor extractor;
        /*
        Provides information about a given media codec available on the device
         */
        private MediaCodecInfo codecInfo;

        private Surface surface;

        public PlayerThread(Surface surface){
            this.surface = surface;
        }

        public MediaCodec getDecoder(){
            return decoder;
        }

        @Override
        public void run(){
            extractor = new MediaExtractor();
            try{
                //Sets the data source to use
                extractor.setDataSource(/*SAMPLE_VIDEO*/getResources().openRawResourceFd(R.raw.overwatchhighlight));
            }catch (IOException e){
                e.printStackTrace();
            }

            //[getTrackCount] : Count the number of tracks found in the data source
            Log.d(LOG_TAG, "This contents track count = " + extractor.getTrackCount());

            for(int index = 0; index < extractor.getTrackCount(); index++){
                /*
                Encapsulates the information describing the format of media data
                 */
                MediaFormat format = extractor.getTrackFormat(index);
                String mime = format.getString(MediaFormat.KEY_MIME);

                if(mime.startsWith("video/"))
                {
                    extractor.selectTrack(index);

                    try{
                        //Create MediaCodec
                        decoder = MediaCodec.createDecoderByType(mime);
                    }catch(IOException e){
                        e.printStackTrace();
                    }

                    /*
                    MediaCodec configure
                    public void configure (MediaFormat format,
                                            Surface surface,
                                            MediaCrypto crypto,
                                            int flags)
                    Parameter]
                    format : The format of the input data(decoder) or the desired format of the outputdata(encoder)
                    surface : Specify a surface on which to render the output of this decoder
                    crypto : Specify a crypto object
                    flags : Specify to configure the component as an encoder
                     */
                    decoder.configure(format, surface, null, 0);

                    int width, height = 0;
                    width = format.getInteger(MediaFormat.KEY_WIDTH);
                    height = format.getInteger(MediaFormat.KEY_HEIGHT);
                    Log.d(LOG_TAG, "Media format info : width = " + width + ", height = " + height);
                    break;
                }
            }

            if(decoder == null){
                Log.e(LOG_TAG, "Cannot find video info");
                return;
            }

            //After successfully configuring the component, call start
            decoder.start();

            /*
            Initialize Input/Output Buffer
            (Deprecated Android L)
             */
            ByteBuffer[] inputBuffers = null;
            ByteBuffer[] outputBuffers = null;

            if(Build.VERSION.SDK_INT < 21){
                inputBuffers = decoder.getInputBuffers();
                outputBuffers = decoder.getOutputBuffers();
            }

            /*
            Create BufferInfo Instance for get information from OutputBuffer
            There is information such as flags, size, offset, presentationTimeUs in BufferInfo Instance
             */
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            //Flag of EndProcess
            boolean isEOS = false;
            long startMs = System.currentTimeMillis();

            while(!Thread.interrupted()){
                if(!isEOS){
                    /*
                    Achive buffer to put FrameData
                    When index is larger than '-1', Write the data and decoding on decoder
                     */
                    int inIndex = decoder.dequeueInputBuffer(5000);
                    if(inIndex > -1){
                        ByteBuffer buffer = null;
                        if(Build.VERSION.SDK_INT >= 21)
                            buffer = decoder.getInputBuffer(inIndex);
                        else
                            buffer = inputBuffers[inIndex];

                        //Retrieve the current encoded sample and store it in the byte buffer starting at the given offset
                        int sampleSize = extractor.readSampleData(buffer, 0);
                        if(sampleSize < 0){
                            //In order to process the decoding end, end flag(isEOS) set in the decoder is notified
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isEOS = true;
                        }else{
                            //getSampleTime : Returns the current sample's presentation time in microseconds. or -1 if no more samples are available
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                            //advance : Advance to the next sample
                            extractor.advance();
                        }
                    }else if(inIndex < 0){
                        Log.d(LOG_TAG, "timeout");
                    }
                }

                /*
                Achive buffer index for getting decoded output data
                There is 4 index type
                1. INFO_OUTPUT_BUFFERS_CHANGED : The output buffers have changed
                2. INFO_OUTPUT_FORMAT_CHANGED : When changed MediaFormat
                3. INFO_TRY_AGAIN_LATER : Normal State
                 */
                int outIndex = decoder.dequeueOutputBuffer(info, 5000);

                switch (outIndex)
                {
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        Log.d(LOG_TAG, "INFO OUTPUT BUFFERS CHANGED");
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        Log.d(LOG_TAG, "INFO OUTPUT FORMAT CHANGED");
                        MediaFormat mediaFormat = decoder.getOutputFormat();
                        Log.d(LOG_TAG, "Format Change Info : MIME = " + mediaFormat.getString(MediaFormat.KEY_MIME) + ", width=" + mediaFormat.getInteger(MediaFormat.KEY_WIDTH) + ", height=" + mediaFormat.getInteger(MediaFormat.KEY_HEIGHT));
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:

                        break;
                    default:
                        //Get Data from output buffer index
                        ByteBuffer buffer;
                        if(Build.VERSION.SDK_INT >= 21)
                            buffer = decoder.getOutputBuffer(outIndex);
                        else
                            buffer = outputBuffers[outIndex];

                        while (info.presentationTimeUs/1000 > System.currentTimeMillis()-startMs){
                            try{
                                sleep(10);
                            }catch(InterruptedException e){
                                e.printStackTrace();
                                break;
                            }
                        }

                        if(Build.VERSION.SDK_INT >= 21){
                            long nanoTime = info.presentationTimeUs * 1000;
                            Log.d("MC_Test", "Rendering output buffer index:" + outIndex + ", Render ts = " + info.presentationTimeUs / 1000 + ", nanoTime = " + nanoTime);
                            decoder.releaseOutputBuffer(outIndex, nanoTime);
                        }else{
                            decoder.releaseOutputBuffer(outIndex, true);
                        }
                        break;
                }
            }
            decoder.stop();
            decoder.release();
            decoder = null;
            extractor.release();
            extractor = null;
        }
    }
}
