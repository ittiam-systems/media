/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.exoplayer.rtsp.reader;

import static androidx.media3.common.util.Util.getBytesFromHexString;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.exoplayer.rtsp.RtpPacket;
import androidx.media3.exoplayer.rtsp.RtpPayloadFormat;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.media3.test.utils.FakeTrackOutput;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Bytes;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Unit test for {@link RtpVp9Reader}.
 */
@RunWith(AndroidJUnit4.class)
public final class RtpVp9ReaderTest {
  private static final byte[] FRAME_1_FRAGMENT_1_DATA =
      getBytesFromHexString("000102030405060708090A");
  private static final RtpPacket FRAME_1_FRAGMENT_1 =
      new RtpPacket.Builder()
          .setTimestamp((int) 2599168056L)
          .setSequenceNumber(40289)
          .setMarker(false)
          .setPayloadData(
              Bytes.concat(
                  /*payload descriptor */ getBytesFromHexString("08"), FRAME_1_FRAGMENT_1_DATA))
          .build();
  private static final byte[] FRAME_1_FRAGMENT_2_DATA = getBytesFromHexString("0B0C0D0E");
  private static final RtpPacket FRAME_1_FRAGMENT_2 =
      new RtpPacket.Builder()
          .setTimestamp((int) 2599168056L)
          .setSequenceNumber(40290)
          .setMarker(true)
          .setPayloadData(
              Bytes.concat(
                  /*payload descriptor */ getBytesFromHexString("00"), FRAME_1_FRAGMENT_2_DATA))
          .build();
  private static final byte[] FRAME_1_DATA =
      Bytes.concat(FRAME_1_FRAGMENT_1_DATA, FRAME_1_FRAGMENT_2_DATA);

  private static final byte[] FRAME_2_FRAGMENT_1_DATA =
      getBytesFromHexString("0D0C0B0A090807060504");
  private static final RtpPacket FRAME_2_FRAGMENT_1 =
      new RtpPacket.Builder()
          .setTimestamp((int) 2599168344L)
          .setSequenceNumber(40291)
          .setMarker(false)
          .setPayloadData(
              Bytes.concat(
                  /*payload descriptor */ getBytesFromHexString("08"), FRAME_2_FRAGMENT_1_DATA))
          .build();
  private static final byte[] FRAME_2_FRAGMENT_2_DATA = getBytesFromHexString("03020100");
  private static final RtpPacket FRAME_2_FRAGMENT_2 =
      new RtpPacket.Builder()
          .setTimestamp((int) 2599168344L)
          .setSequenceNumber(40292)
          .setMarker(true)
          .setPayloadData(
              Bytes.concat(
                  /*payload descriptor */ getBytesFromHexString("00"), FRAME_2_FRAGMENT_2_DATA))
          .build();
  private static final byte[] FRAME_2_DATA =
      Bytes.concat(FRAME_2_FRAGMENT_1_DATA, FRAME_2_FRAGMENT_2_DATA);

  private static final RtpPayloadFormat VP9_FORMAT =
      new RtpPayloadFormat(
          new Format.Builder()
              .setSampleMimeType(MimeTypes.VIDEO_VP9)
              .build(),
          /* rtpPayloadType= */ 98,
          /* clockRate= */ 48_000,
          /* fmtpParameters= */ ImmutableMap.of());

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private FakeTrackOutput trackOutput;
  private FakeExtractorOutput extractorOutput;

  @Before
  public void setUp() {
    extractorOutput = new FakeExtractorOutput();
  }

  @Test
  public void consume_validPackets() {
    RtpVp9Reader vp9Reader = new RtpVp9Reader(VP9_FORMAT);
    vp9Reader.createTracks(extractorOutput, /* trackId= */ 0);
    vp9Reader.onReceivingFirstPacket(
        FRAME_1_FRAGMENT_1.timestamp, FRAME_1_FRAGMENT_1.sequenceNumber);
    consume(vp9Reader, FRAME_1_FRAGMENT_1);
    consume(vp9Reader, FRAME_1_FRAGMENT_2);
    consume(vp9Reader, FRAME_2_FRAGMENT_1);
    consume(vp9Reader, FRAME_2_FRAGMENT_2);

    trackOutput = extractorOutput.trackOutputs.get(0);
    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(FRAME_1_DATA);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(FRAME_2_DATA);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(3200);
  }

  @Test
  public void consume_fragmentedFrameMissingFirstFragment() {
    RtpVp9Reader vp9Reader = new RtpVp9Reader(VP9_FORMAT);
    vp9Reader.createTracks(extractorOutput, /* trackId= */ 0);
    // First packet timing information is transmitted over RTSP, not RTP.
    vp9Reader.onReceivingFirstPacket(
        FRAME_1_FRAGMENT_1.timestamp, FRAME_1_FRAGMENT_1.sequenceNumber);
    consume(vp9Reader, FRAME_1_FRAGMENT_2);
    consume(vp9Reader, FRAME_2_FRAGMENT_1);
    consume(vp9Reader, FRAME_2_FRAGMENT_2);

    trackOutput = extractorOutput.trackOutputs.get(0);
    assertThat(trackOutput.getSampleCount()).isEqualTo(1);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(FRAME_2_DATA);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(3200);
  }

  @Test
  public void consume_fragmentedFrameMissingBoundaryFragment() {
    RtpVp9Reader vp9Reader = new RtpVp9Reader(VP9_FORMAT);
    vp9Reader.createTracks(extractorOutput, /* trackId= */ 0);
    vp9Reader.onReceivingFirstPacket(
        FRAME_1_FRAGMENT_1.timestamp, FRAME_1_FRAGMENT_1.sequenceNumber);
    consume(vp9Reader, FRAME_1_FRAGMENT_1);
    consume(vp9Reader, FRAME_2_FRAGMENT_1);
    consume(vp9Reader, FRAME_2_FRAGMENT_2);

    trackOutput = extractorOutput.trackOutputs.get(0);
    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(FRAME_1_FRAGMENT_1_DATA);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(FRAME_2_DATA);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(3200);
  }

  @Test
  public void consume_outOfOrderFragmentedFrame() {
    RtpVp9Reader vp9Reader = new RtpVp9Reader(VP9_FORMAT);
    vp9Reader.createTracks(extractorOutput, /* trackId= */ 0);
    vp9Reader.onReceivingFirstPacket(
        FRAME_1_FRAGMENT_1.timestamp, FRAME_1_FRAGMENT_1.sequenceNumber);
    consume(vp9Reader, FRAME_1_FRAGMENT_1);
    consume(vp9Reader, FRAME_2_FRAGMENT_1);
    consume(vp9Reader, FRAME_1_FRAGMENT_2);
    consume(vp9Reader, FRAME_2_FRAGMENT_2);

    trackOutput = extractorOutput.trackOutputs.get(0);
    assertThat(trackOutput.getSampleCount()).isEqualTo(2);
    assertThat(trackOutput.getSampleData(0)).isEqualTo(FRAME_1_FRAGMENT_1_DATA);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);
    assertThat(trackOutput.getSampleData(1)).isEqualTo(FRAME_2_DATA);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(3200);
  }

  private static void consume(RtpVp9Reader vp9Reader, RtpPacket rtpPacket) {
    ParsableByteArray packetData = new ParsableByteArray();
    packetData.reset(rtpPacket.payloadData);
    vp9Reader.consume(
        packetData,
        rtpPacket.timestamp,
        rtpPacket.sequenceNumber,
        /* isFrameBoundary= */ rtpPacket.marker);
  }
}
