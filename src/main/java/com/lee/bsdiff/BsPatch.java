package com.lee.bsdiff;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 本文件修改自Github SparkInLee 的开源项目 https://github.com/SparkInLee/jbsdiff
 */

public class BsPatch {
	private long wrote = 0;

	@SuppressWarnings("resource")
	public long bspatch(InputStream oldFile, InputStream patchFile, OutputStream newFile, int oldFileLength, int patchFileLenth) throws IOException {
		byte[] oldData = new byte[oldFileLength];
		oldFile.read(oldData);

		byte[] patchData = new byte[patchFileLenth];
		patchFile.read(patchData);

		byte[] header = "ENDSLEY/BSDIFF43".getBytes();
		int pos = 0;
		int oldPos = 0;
		for (; pos < header.length; ++pos)
			if (header[pos] != patchData[pos])
				throw new IllegalStateException("patch file is corrupted.");

		int newFileSize = readSize(patchData, pos);
		pos += 4;

		byte[] data = null;
		while (pos < patchData.length) {
			int diffLen = readSize(patchData, pos);
			pos += 4;
			int extraLen = readSize(patchData, pos);
			pos += 4;
			int offset = readSize(patchData, pos);
			pos += 4;

			data = new byte[diffLen];
			for (int i = 0; i < diffLen; ++i) {
				data[i] = (byte) toNormal(toPositive(oldData[oldPos++]) - patchData[pos++]);
			}
			newFile.write(data);
			wrote += data.length;

			data = new byte[extraLen];
			for (int i = 0; i < extraLen; ++i) {
				data[i] = patchData[pos++];
			}
			newFile.write(data);
			wrote += data.length;

			oldPos += offset;
		}

		return wrote;
	}

	private int readSize(byte[] data, int pos) {
		int size;

		size = data[pos + 3] & 0x7F;
		size = size * 256;
		size += data[pos + 2];
		size = size * 256;
		size += data[pos + 1];
		size = size * 256;
		size += data[pos + 0];

		if ((data[pos + 3] & 0x80) != 0) {
			size = -size;
		}

		return size;
	}

	private int toPositive(byte b) {
		return b + 128;
	}

	private int toNormal(int b) {
		return b - 128;
	}
}
