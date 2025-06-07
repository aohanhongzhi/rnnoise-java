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

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * @author HOX4SGH
 * @description
 * @date 2025/6/7
 */
public class App {

    public static void main(String[] args) throws UnsupportedAudioFileException, IOException {
        String inFile = "/Users/HOX4SGH/Downloads/Resourses_48KHz_rnn_origin.wav";
        String outFile = "output-denoised.wav";

        // 1. 打开 WAV 文件
        AudioInputStream ais = AudioSystem.getAudioInputStream(new File(inFile));
        AudioFormat format = ais.getFormat();

        System.out.println(format);


        AudioFormat targetFormat = new AudioFormat(
                16000.0f, // sampleRate
                16,       // sampleSizeInBits
                1,        // channels
                true,     // signed
                false     // little-endian
        );

        AudioInputStream convertedAis = AudioSystem.getAudioInputStream(targetFormat, ais);

        System.out.println("convertedAis format: " + convertedAis.getFormat());
        System.out.println("convertedAis available: " + convertedAis.available());

        // 检查格式（16kHz 单声道 16bit PCM）
//        if (format.getSampleRate() != 16000.0f || format.getChannels() != 1 || format.getSampleSizeInBits() != 16) {
//            throw new IllegalArgumentException("WAV 必须为 16kHz 单声道 16bit PCM");
//        }

        // 2. 预备输出流
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        DenoiseState state = new DenoiseState();
        byte[] frameBytes = new byte[480 * 2];
        short[] frameShorts = new short[480];
        float[] frameIn = new float[480];
        float[] frameOut = new float[480];

        int n;
        while ((n = convertedAis.read(frameBytes)) > 0) {
            if (n < frameBytes.length) {
                for (int i = n; i < frameBytes.length; i++) frameBytes[i] = 0;
            }
            // byte -> short
            for (int i = 0; i < 480; i++) {
                int low = frameBytes[i * 2] & 0xff;
                int high = frameBytes[i * 2 + 1];
                frameShorts[i] = (short) ((high << 8) | low);
                frameIn[i] = frameShorts[i] / 32768f;
            }
            // denoise
            state.processFrame(frameOut, frameIn);
            // float -> short -> byte
            for (int i = 0; i < 480; i++) {
                float v = frameOut[i];
                if (v > 1.0f) v = 1.0f;
                if (v < -1.0f) v = -1.0f;
                frameShorts[i] = (short) (v * 32767f);
                frameBytes[i * 2] = (byte) (frameShorts[i] & 0xff);
                frameBytes[i * 2 + 1] = (byte) ((frameShorts[i] >> 8) & 0xff);
            }
            baos.write(frameBytes, 0, frameBytes.length);
        }
        ais.close();
        convertedAis.close();

        // 3. 写回 WAV
        byte[] outBytes = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(outBytes);

        AudioFormat outFormat = new AudioFormat(16000.0f, 16, 1, true, false); // PCM_SIGNED, little-endian
        AudioInputStream oais = new AudioInputStream(bais, outFormat, outBytes.length / 2);

        AudioSystem.write(oais, AudioFileFormat.Type.WAVE, new File(outFile));

        System.out.println("降噪完成，输出文件: " + outFile);
    }
}
