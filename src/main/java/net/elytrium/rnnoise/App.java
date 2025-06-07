/*
 * Copyright (C) 2023 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.rnnoise;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.io.jvm.WaveformWriter;
import be.tarsos.dsp.resample.RateTransposer;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author HOX4SGH
 * @description
 * @date 2025/6/7
 */
public class App {

    public static void main(String[] args) throws UnsupportedAudioFileException, IOException {
        String outFile = "output-denoised.wav";

        String inFile = "/Users/HOX4SGH/Downloads/Resourses_48KHz_rnn_origin.wav";


        AudioDispatcher dispatcher = AudioDispatcherFactory.fromPipe(
                inFile,
                48000,
                1024,
                0
        );
        dispatcher.addAudioProcessor(new RateTransposer(16000.0 / 48000.0));
        dispatcher.addAudioProcessor(new WaveformWriter(new AudioFormat(16000, 16, 1, true, false), "temp_16k.wav"));
        dispatcher.run();


        // 1. 打开 WAV 文件
        AudioInputStream ais = AudioSystem.getAudioInputStream(new File("temp_16k.wav"));
        AudioFormat format = ais.getFormat();

        System.out.println(format);

        // 检查格式（16kHz 单声道 16bit PCM）
        if (format.getSampleRate() != 16000.0f || format.getChannels() != 1 || format.getSampleSizeInBits() != 16) {
            throw new IllegalArgumentException("WAV 必须为 16kHz 单声道 16bit PCM");
        }

        // 2. 预备输出流

        DenoiseState state = new DenoiseState();
        System.out.println("DenoiseState created: frameSize = " + state.getFrameSize());

        byte[] frameBytes = new byte[480 * 2];
        float[] frameIn = new float[480];
        float[] frameOut = new float[480];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int n;
        while ((n = ais.read(frameBytes)) > 0) {
            if (n < frameBytes.length) {
                Arrays.fill(frameBytes, n, frameBytes.length, (byte)0);
            }
            // byte -> float
            for (int i = 0; i < 480; i++) {
                int low = frameBytes[i * 2] & 0xff;
                int high = frameBytes[i * 2 + 1];
                short sample = (short)((high << 8) | low);
                frameIn[i] = sample / 32768f;
            }
            System.out.printf("in: %f %f %f %f %f\n", frameIn[0], frameIn[1], frameIn[2], frameIn[3], frameIn[4]);
            System.out.printf("out: %f %f %f %f %f\n", frameOut[0], frameOut[1], frameOut[2], frameOut[3], frameOut[4]);

            state.processFrame(frameOut, frameIn);
            // float -> byte
            for (int i = 0; i < 480; i++) {
                int v = (int)(frameOut[i] * 32767.0);
                if (v > 32767) v = 32767;
                if (v < -32768) v = -32768;
                frameBytes[i * 2] = (byte)(v & 0xff);
                frameBytes[i * 2 + 1] = (byte)((v >> 8) & 0xff);
            }
            baos.write(frameBytes);
        }
        ais.close();

        // 3. 写回 WAV
        byte[] outBytes = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(outBytes);

        AudioFormat outFormat = new AudioFormat(16000.0f, 16, 1, true, false); // PCM_SIGNED, little-endian
        AudioInputStream oais = new AudioInputStream(bais, outFormat, outBytes.length / 2);

        AudioSystem.write(oais, AudioFileFormat.Type.WAVE, new File(outFile));

        System.out.println("降噪完成，输出文件: " + outFile);
    }
}
