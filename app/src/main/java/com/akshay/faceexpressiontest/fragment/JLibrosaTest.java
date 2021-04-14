package com.akshay.faceexpressiontest.fragment;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Arrays;

import com.jlibrosa.audio.wavFile.WavFileException;
import com.jlibrosa.audio.wavFile.WavFile;
import com.jlibrosa.audio.JLibrosa;

public class JLibrosaTest {

	public void getMfcc() throws IOException, WavFileException {
		// TODO Auto-generated method stub

		int mNumFrames;
		int mSampleRate;
		int mChannels;
		JLibrosa jLibrosa = new JLibrosa();

		File sourceFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/temp.wav");
        WavFile wavFile = null;
        Log.d("TAG","File Exist "+sourceFile.exists());

            wavFile = WavFile.openWavFile(sourceFile);
            mNumFrames = (int) (wavFile.getNumFrames());
            mSampleRate = (int) wavFile.getSampleRate();
            mChannels = wavFile.getNumChannels();

            float[][] buffer = new float[mChannels][mNumFrames];
            int frameOffset = 0;
            int loopCounter = ((mNumFrames * mChannels)/4096) + 1;
            for (int i = 0; i < loopCounter; i++) {
                frameOffset = (int)wavFile.readFrames(buffer, mNumFrames, frameOffset);
            }


            DecimalFormat df = new DecimalFormat("#.#####");
            df.setRoundingMode(RoundingMode.CEILING);

            double [] meanBuffer = new double[mNumFrames];
            for(int q=0;q<mNumFrames;q++){
                double frameVal = 0;
                for(int p=0;p<mChannels;p++){
                    frameVal = frameVal + buffer[p][q];
                }
                    meanBuffer[q]=Double.parseDouble(df.format(frameVal/mChannels));
            }


            //MFCC java library.
            MFCC mfccConvert = new MFCC();
            mfccConvert.setSampleRate(mSampleRate);
            int nMFCC = 40;
            mfccConvert.setN_mfcc(nMFCC);
            float[] mfccInput = mfccConvert.process(meanBuffer);

            int nFFT = mfccInput.length/nMFCC;
            double [][] mfccValues = new double[nMFCC][nFFT];

            //loop to convert the mfcc values into multi-dimensional array
            for(int i=0;i<nFFT;i++){
                int indexCounter = i * nMFCC;
                int rowIndexValue = i%nFFT;
                for(int j=0;j<nMFCC;j++){
                    mfccValues[j][rowIndexValue]=mfccInput[indexCounter];
                    indexCounter++;
                }
            }

            //code to take the mean of mfcc values across the rows such that
            //[nMFCC x nFFT] matrix would be converted into
            //[nMFCC x 1] dimension - which would act as an input to tflite model
            float [] meanMFCCValues = new float[nMFCC];
            for(int p=0;p<nMFCC;p++){
                double fftValAcrossRow = 0;
                for(int q=0;q<nFFT;q++){
                    fftValAcrossRow = fftValAcrossRow + mfccValues[p][q];
                }
                double fftMeanValAcrossRow = fftValAcrossRow/nFFT;
                meanMFCCValues[p] = (float) fftMeanValAcrossRow;
            }

            Log.d("TAG","Mean MFCC Values "+ Arrays.toString(meanMFCCValues));



	}

}
