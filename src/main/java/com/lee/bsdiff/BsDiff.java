package com.lee.bsdiff;

import mcpatch.extension.StreamExtension;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// this file is modified from the GitHub repo of SparkInLee: https://github.com/SparkInLee/jbsdiff

public class BsDiff {
	private long wrote = 0;

	public long bsdiff(InputStream oldFile, InputStream newFile, OutputStream patchFile, int oldDataLength, int newDataLength) throws IOException {
		byte[] oldData = new byte[oldDataLength];
		byte[] newData = new byte[newDataLength];
//		oldFile.read(oldData);
		StreamExtension.INSTANCE.actuallyRead(oldFile, oldData, 0, oldDataLength);
//		newFile.read(newData);
		StreamExtension.INSTANCE.actuallyRead(newFile, newData, 0, newDataLength);

		SuffixArray suffixArray = new SuffixArray(oldData);

		// write header
		byte[] header = "ENDSLEY/BSDIFF43".getBytes();
		patchFile.write(header);
		wrote += header.length;

		writeSize(patchFile, newData.length);
		bsdiffInternal(oldData, newData, patchFile, suffixArray);

		return wrote;
	}

	private void bsdiffInternal(byte[] oldData, byte[] newData, OutputStream out, SuffixArray suffixArray) throws IOException {
		int scan = 0, lastScan = 0;
		int pos = 0, lastPos = 0;
		int len = 0, lastOffset = 0;
		int extraLen = 0;
		while (scan < newData.length) {
			int match = 0;
			for (int scsc = (scan += len); scan < newData.length; ++scan) {
				TwoTuple<Integer, Integer> searchRet = suffixArray.search(scan, newData, oldData, 0, oldData.length);
				pos = searchRet.getFirst();
				len = searchRet.getSecond();

				for (; scsc < scan + len; ++scsc) {
					if (scsc + lastOffset < oldData.length && oldData[scsc + lastOffset] == newData[scsc]) {
						++match;
					}
				}

				if ((len == match && len != 0) || len > match + 8) {
					break;
				}

				if (scan + lastOffset < oldData.length && oldData[scan + lastOffset] == newData[scan]) {
					--match;
				}
			}

			if (len != match || scan == newData.length) {
				int f = 0, F = 0, lenF = 0;
				for (int i = 0; i < scan - lastScan && i < oldData.length - lastPos;) {
					if (newData[lastScan + i] == oldData[lastPos + i]) {
						++f;
					}
					++i;
					if (2 * f - i > 2 * F - lenF) {
						F = f;
						lenF = i;
					}
				}

				int b = 0, B = 0, lenB = 0;
				if (scan < newData.length) {
					for (int i = 1; i < scan - lastScan + 1 && i < pos + 1; ++i) {
						if (newData[scan - i] == oldData[pos - i]) {
							++b;
						}
						if (2 * b - i > 2 * B - lenB) {
							B = b;
							lenB = i;
						}
					}
				}

				int overlap = -1;
				if (lenF + lenB > scan - lastScan) {
					overlap = (lastScan + lenF) - (scan - lenB);
					int s = 0, S = 0, lenS = 0;
					for (int i = 0; i < overlap; ++i) {
						if (oldData[lastPos + lenF - overlap + i] == newData[lastScan + lenF - overlap + i]) {
							++s;
						}
						if (oldData[pos - lenB + i] == newData[scan - lenB + i]) {
							--s;
						}
						if (s > S) {
							S = s;
							lenS = i;
						}
					}
					lenF = lenF - overlap + lenS;
					lenB -= lenS;
				}

				writeSize(out, lenF);
				writeSize(out, scan - lastScan - lenF - lenB);
				writeSize(out, pos - lastPos - lenF - lenB);
				extraLen += 12;

				byte[] buf = new byte[lenF];
				for (int i = 0; i < lenF; ++i) {
					buf[i] = (byte) (toPositive(oldData[lastPos + i]) - toPositive(newData[lastScan + i]));
				}
				out.write(buf);
				wrote += buf.length;

				if (overlap == -1) {
					buf = new byte[scan - lastScan - lenF - lenB];
					for (int i = 0; i < buf.length; ++i) {
						buf[i] = newData[lastScan + lenF + i];
					}
					out.write(buf);
					wrote += buf.length;
				}

				lastPos = pos - lenB;
				lastScan = scan - lenB;
				lastOffset = pos - scan;
			}
		}
	}

	private void writeSize(OutputStream out, int size) throws IOException {
		int tempSize;
		byte[] buf = new byte[4];
		if (size < 0)
			tempSize = -size;
		else
			tempSize = size;

		buf[0] = (byte) (tempSize % 256);
		tempSize -= buf[0];
		tempSize = tempSize / 256;

		buf[1] = (byte) (tempSize % 256);
		tempSize -= buf[1];
		tempSize = tempSize / 256;

		buf[2] = (byte) (tempSize % 256);
		tempSize -= buf[2];
		tempSize = tempSize / 256;

		buf[3] = (byte) (tempSize % 256);
		if (size < 0)
			buf[3] |= 0x80;

		out.write(buf);
		wrote += buf.length;
	}

	private int toPositive(byte b) {
		return b + 128;
	}
}
