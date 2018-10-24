package com.ctech.bitmp4;

import static android.media.MediaCodec.CONFIGURE_FLAG_ENCODE;
import static android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED;
import static android.media.MediaCodec.INFO_TRY_AGAIN_LATER;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
import static android.media.MediaCodecInfo.CodecProfileLevel.MPEG2ProfileHigh;
import static android.media.MediaFormat.KEY_AAC_PROFILE;
import static android.media.MediaFormat.KEY_BIT_RATE;
import static android.media.MediaFormat.KEY_COLOR_FORMAT;
import static android.media.MediaFormat.KEY_FRAME_RATE;
import static android.media.MediaFormat.KEY_I_FRAME_INTERVAL;
import static android.media.MediaFormat.MIMETYPE_AUDIO_AAC;
import static android.media.MediaFormat.MIMETYPE_VIDEO_AVC;
import static android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import java.io.IOException;
import java.nio.ByteBuffer;
import timber.log.Timber;

public class MP4Encoder extends Encoder {

  private static final int BIT_RATE = 2000000;
  private static final int FRAME_RATE = 20;
  private static final int I_FRAME_INTERVAL = 5;
  private static final long ONE_SEC = 1000000;
  private static final String TAG = MP4Encoder.class.getSimpleName();
  private static final int TIMEOUT_US = 10000;
  private int addedFrameCount;
  private byte[] audioArray = new byte[4096];
  private MediaCodec audioCodec;
  private int audioTrackIndex;
  private BufferInfo bufferInfo;
  private int encodedFrameCount;
  private boolean isMuxerStarted = false;
  private boolean isStarted = false;
  private MediaMuxer mediaMuxer;
  private int trackCount = 0;
  private MediaCodec videoCodec;
  private int videoTrackIndex;

  @Override
  protected void onInit() {
  }

  @Override
  protected void onStart() {
    isStarted = true;
    addedFrameCount = 0;
    encodedFrameCount = 0;
    int width = getWidth();
    int height = getHeight();
    try {
      bufferInfo = new BufferInfo();
      videoCodec = MediaCodec.createEncoderByType(MIMETYPE_VIDEO_AVC);
      MediaFormat videoFormat = MediaFormat.createVideoFormat(MIMETYPE_VIDEO_AVC, width, height);
      videoFormat.setInteger(KEY_BIT_RATE, BIT_RATE);
      videoFormat.setInteger(KEY_FRAME_RATE, FRAME_RATE);
      videoFormat.setInteger(KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
      videoFormat.setInteger(KEY_COLOR_FORMAT, COLOR_FormatYUV420SemiPlanar);
      videoCodec.configure(videoFormat, null, null, CONFIGURE_FLAG_ENCODE);
      videoCodec.start();
      audioCodec = MediaCodec.createEncoderByType(MIMETYPE_AUDIO_AAC);
      MediaFormat audioFormat = MediaFormat.createAudioFormat(MIMETYPE_AUDIO_AAC, 44100, 1);
      int profile;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        profile = MPEG2ProfileHigh;
      } else {
        profile = 5;
      }
      audioFormat.setInteger(KEY_AAC_PROFILE, profile);
      audioFormat.setInteger(KEY_BIT_RATE, 65536);
      audioCodec.configure(audioFormat, null, null, CONFIGURE_FLAG_ENCODE);
      audioCodec.start();
      mediaMuxer = new MediaMuxer(outputFilePath, MUXER_OUTPUT_MPEG_4);
    } catch (IOException ioe) {
      throw new RuntimeException("MediaMuxer creation failed", ioe);
    }
  }

  @Override
  protected void onStop() {
    if (isStarted) {
      encode();
      if (this.addedFrameCount > 0) {
        Timber.i(TAG, "Total frame count = %s", this.addedFrameCount);
        if (videoCodec != null) {
          videoCodec.stop();
          videoCodec.release();
          videoCodec = null;
          Timber.i(TAG, "RELEASE VIDEO CODEC");
        }
        if (audioCodec != null) {
          audioCodec.stop();
          audioCodec.release();
          audioCodec = null;
          Timber.i(TAG, "RELEASE AUDIO CODEC");
        }
        if (mediaMuxer != null) {
          mediaMuxer.stop();
          mediaMuxer.release();
          mediaMuxer = null;
          Timber.i(TAG, "RELEASE MUXER");
        }
      } else {
        Timber.e(TAG, "not added any frame");
      }
      isStarted = false;
    }
  }

  @Override
  protected void onAddFrame(Bitmap bitmap) {
    if (!isStarted) {
      Timber.d(TAG, "already finished. can't add Frame ");
    } else if (bitmap == null) {
      Timber.e(TAG, "Bitmap is null");
    } else {
      int inputBufIndex = videoCodec.dequeueInputBuffer(TIMEOUT_US);
      if (inputBufIndex >= 0) {
        byte[] input = getNV12(bitmap.getWidth(), bitmap.getHeight(), bitmap);
        ByteBuffer inputBuffer = videoCodec.getInputBuffer(inputBufIndex);
        inputBuffer.clear();
        inputBuffer.put(input);
        videoCodec.queueInputBuffer(inputBufIndex, 0, input.length,
            getPresentationTimeUsec(addedFrameCount), 0);
      }
      int audioInputBufferIndex = audioCodec.dequeueInputBuffer(TIMEOUT_US);
      if (audioInputBufferIndex >= -1) {
        ByteBuffer encoderInputBuffer = audioCodec.getInputBuffer(audioInputBufferIndex);
        encoderInputBuffer.clear();
        encoderInputBuffer.put(audioArray);
        audioCodec.queueInputBuffer(audioInputBufferIndex, 0, audioArray.length,
            getPresentationTimeUsec(addedFrameCount), 0);
      }
      addedFrameCount++;
      while (addedFrameCount > encodedFrameCount) {
        encode();
      }
    }
  }

  private void encode() {
    encodeVideo();
    encodeAudio();

  }

  private void encodeAudio() {
    int audioStatus = audioCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
    Timber.i("Audio encoderStatus = " + audioStatus + ", presentationTimeUs = "
        + bufferInfo.presentationTimeUs);
    if (audioStatus == INFO_OUTPUT_FORMAT_CHANGED) {
      MediaFormat audioFormat = audioCodec.getOutputFormat();
      Timber.i("output format changed. audio format: %s", audioFormat.toString());
      audioTrackIndex = mediaMuxer.addTrack(audioFormat);
      trackCount++;
      if (trackCount == 2) {
        Timber.i("started media muxer.");
        mediaMuxer.start();
        isMuxerStarted = true;
      }
    } else if (audioStatus == INFO_TRY_AGAIN_LATER) {
      Timber.d("no output from audio encoder available");
    } else {
      ByteBuffer audioData = audioCodec.getOutputBuffer(audioStatus);
      if (audioData != null) {
        audioData.position(bufferInfo.offset);
        audioData.limit(bufferInfo.offset + bufferInfo.size);
        if (isMuxerStarted) {
          mediaMuxer.writeSampleData(audioTrackIndex, audioData, bufferInfo);
        }
        audioCodec.releaseOutputBuffer(audioStatus, false);
      }
    }
  }

  private void encodeVideo() {
    int encoderStatus = videoCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
    Timber.i("Video encoderStatus = " + encoderStatus + ", presentationTimeUs = "
        + bufferInfo.presentationTimeUs);
    if (encoderStatus == INFO_OUTPUT_FORMAT_CHANGED) {
      MediaFormat videoFormat = videoCodec.getOutputFormat();
      Timber.i("output format changed. video format: %s", videoFormat.toString());
      videoTrackIndex = mediaMuxer.addTrack(videoFormat);
      trackCount++;
      if (trackCount == 2) {
        Timber.i("started media muxer.");
        mediaMuxer.start();
        isMuxerStarted = true;
      }
    } else if (encoderStatus == INFO_TRY_AGAIN_LATER) {
      Timber.d("no output from video encoder available");
    } else {
      ByteBuffer encodedData = videoCodec.getOutputBuffer(encoderStatus);
      if (encodedData != null) {
        encodedData.position(bufferInfo.offset);
        encodedData.limit(bufferInfo.offset + bufferInfo.size);
        if (isMuxerStarted) {
          mediaMuxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo);
        }
        videoCodec.releaseOutputBuffer(encoderStatus, false);
        encodedFrameCount++;
      }
      Timber.i("encoderOutputBuffer " + encoderStatus + " was null");
    }
  }

  private static long getPresentationTimeUsec(int frameIndex) {
    return (((long) frameIndex) * ONE_SEC) / 20;
  }

  private byte[] getNV12(int inputWidth, int inputHeight, Bitmap scaled) {
    int[] argb = new int[(inputWidth * inputHeight)];
    scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);
    byte[] yuv = new byte[(((inputWidth * inputHeight) * 3) / 2)];
    encodeYUV420SP(yuv, argb, inputWidth, inputHeight);
    scaled.recycle();
    return yuv;
  }

  private void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
    int yIndex = 0;
    int uvIndex = width * height;
    int index = 0;
    int j = 0;
    while (j < height) {
      int uvIndex2;
      int yIndex2;
      int i = 0;
      while (true) {
        uvIndex2 = uvIndex;
        yIndex2 = yIndex;
        if (i >= width) {
          break;
        }
        int R = (argb[index] & 0xFF0000) >> 16;
        int G = (argb[index] & 0xFF00) >> 8;
        int B = (argb[index] & 0x0000FF) >> 0;
        int Y = ((((R * 77) + (G * 150)) + (B * 29)) + 128) >> 8;
        int V = (((((R * -43) - (G * 84)) + (B * 127)) + 128) >> 8) + 128;
        int U = (((((R * 127) - (G * 106)) - (B * 21)) + 128) >> 8) + 128;
        yIndex = yIndex2 + 1;
        if (Y < 0) {
          Y = 0;
        } else if (Y > 255) {
          Y = 255;
        }
        yuv420sp[yIndex2] = (byte) Y;
        if (j % 2 == 0 && index % 2 == 0) {
          uvIndex = uvIndex2 + 1;
          if (V < 0) {
            V = 0;
          } else if (V > 255) {
            V = 255;
          }
          yuv420sp[uvIndex2] = (byte) V;
          uvIndex2 = uvIndex + 1;
          if (U < 0) {
            U = 0;
          } else if (U > 255) {
            U = 255;
          }
          yuv420sp[uvIndex] = (byte) U;
        }
        uvIndex = uvIndex2;
        index++;
        i++;
      }
      j++;
      uvIndex = uvIndex2;
      yIndex = yIndex2;
    }
  }
}