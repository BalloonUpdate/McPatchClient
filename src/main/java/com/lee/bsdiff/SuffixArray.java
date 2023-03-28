package com.lee.bsdiff;

// this file is modified from the GitHub repo of SparkInLee: https://github.com/SparkInLee/jbsdiff

/**
 * suffix array
 *
 * @see <a href="http://www.larsson.dogma.net/ssrev-tr.pdf">...</a>
 *
 * @author jianglee
 *
 */
public class SuffixArray {
	private int[] I = null;
	private byte[] rawData;

	public SuffixArray(byte[] rawData) {
		this.rawData = rawData;
	}

	private void checkInitialized() {
		if (null == I) {
			try {
				// read data
				int size = rawData.length;
				byte[] data = rawData;

				// radix sort
				int[] bucket = new int[256];
				for (int i = 0; i < size; ++i) {
					bucket[toPositive(data[i])]++;
				}
				for (int i = 1; i < bucket.length; ++i) {
					bucket[i] += bucket[i - 1];
				}
				for (int i = bucket.length - 1; i > 0; --i) {
					bucket[i] = bucket[i - 1];
				}
				bucket[0] = 0;

				// initialize the I array
				I = new int[size]; // index of I decide the h-order,value of
									// I is the origin index in file
				for (int i = 0; i < size; ++i) {
					I[bucket[toPositive(data[i])]++] = i;
				}

				int[] V = new int[size]; // value of V is the group of
											// origin data of the same index
				for (int i = 0; i < size; ++i) {
					V[i] = bucket[toPositive(data[i])] - 1;
				}

				for (int i = 1; i < bucket.length; ++i) {
					if (bucket[i] == bucket[i - 1] + 1) {
						I[bucket[i] - 1] = -1;
					}
				}
				if (bucket[0] == 1) {
					I[0] = -1;
				}

				// fast suffix sort
				for (int h = 1; I[0] != -size; h += h) {
					int len = 0;
					for (int i = 0; i < size; /* no-op */) {
						if (I[i] < 0) {
							len -= I[i];
							i -= I[i];
						} else {
							if (len > 0) {
								I[i - len] = -len;
							}
							int groupLen = V[I[i]] - i + 1;
							split(I, V, i, groupLen, h);
							i += groupLen;
							len = 0;
						}
					}
					if (len > 0) {
						I[size - len] = -len;
					}
				}

				// rebuild I
				for (int i = 0; i < size; ++i) {
					I[V[i]] = i;
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	private int toPositive(byte b) {
		return b + 128;
	}

	private void split(int[] I, int[] V, int start, int len, int h) {
		int temp = 0;

		if (len < 16) {
			// insert sort
			for (int i = start, k = 0; i < start + len; i = k) {
				int X = getVValue(V, I[i] + h);
				k = i + 1;
				for (int j = i + 1; j < start + len; ++j) {
					if (getVValue(V, I[j] + h) < X) {
						X = getVValue(V, I[j] + h);
						k = i;
					}
					if (getVValue(V, I[j] + h) == X) {
						temp = I[j];
						I[j] = I[k];
						I[k] = temp;
						++k;
					}
				}
				for (int j = i; j < k; ++j) {
					V[I[j]] = k - 1;
				}
				if (k == i + 1) {
					I[i] = -1;
				}
			}
			return;
		}

		// Ternary-Split quick sort
		int X = getVValue(V, I[start + len / 2] + h);
		int smallCount = 0;
		int equalCount = 0;
		for (int i = 0; i < len; ++i) {
			if (getVValue(V, I[start + i] + h) < X) {
				++smallCount;
			} else if (getVValue(V, I[start + i] + h) == X) {
				++equalCount;
			}
		}

		int smallPos = start + smallCount;
		int equalPos = smallPos + equalCount;
		int i = start, j = i + smallCount, k = j + equalCount;
		while (i < smallPos) {
			if (getVValue(V, I[i] + h) < X) {
				++i;
			} else if (getVValue(V, I[i] + h) == X) {
				temp = I[i];
				I[i] = I[j];
				I[j] = temp;
				++j;
			} else {
				temp = I[i];
				I[i] = I[k];
				I[k] = temp;
				++k;
			}
		}
		while (j < equalPos) {
			if (getVValue(V, I[j] + h) == X) {
				++j;
			} else {
				temp = I[j];
				I[j] = I[k];
				I[k] = temp;
				++k;
			}
		}

		if (smallPos > start) {
			split(I, V, start, smallPos - start, h);
		}

		for (i = smallPos; i < equalPos; ++i) {
			V[I[i]] = equalPos - 1;
		}
		if (equalPos == smallPos + 1) {
			I[smallPos] = -1;
		}

		if (equalPos < start + len) {
			split(I, V, equalPos, len - (equalPos - start), h);
		}
	}

	private int getVValue(int[] V, int pos) {
		if (pos < V.length) {
			return V[pos];
		} else {
			return -1;
		}
	}

	/**
	 * 
	 * search the best match
	 * 
	 * @param index
	 * @param newData
	 * @param oldData
	 * @param start
	 * @param end
	 * @return TwoTuple<T,R>, t is start position and r is length.
	 */
	public TwoTuple<Integer, Integer> search(int index, byte[] newData, byte[] oldData, int start, int end) {
		checkInitialized();

		if (end - start < 2) {
			TwoTuple<Integer, Integer> ret = new TwoTuple<>();
			int len1 = matchLen(oldData, I[start], newData, index);
			int len2 = -1;
			if (end != start && end < I.length) {
				len2 = matchLen(oldData, I[end], newData, index);
			}
			if (len1 > len2) {
				ret.setFirst(I[start]);
				ret.setSecond(len1);
			} else {
				ret.setFirst(I[end]);
				ret.setSecond(len2);
			}
			return ret;
		}

		int mid = (end - start) / 2 + start;
		if (arrayCompare(oldData, I[mid], newData, index,
				Math.min(oldData.length - I[mid], newData.length - index)) < 0) {
			return search(index, newData, oldData, mid, end);
		} else {
			return search(index, newData, oldData, start, mid);
		}
	}

	private int arrayCompare(byte[] lData, int lStart, byte[] rData, int rStart, int size) {
		int i, j;
		for (i = lStart, j = rStart; i < lStart + size; ++i, ++j) {
			if (lData[i] < rData[j]) {
				return -1;
			} else if (lData[i] > rData[j]) {
				return 1;
			}
		}
		return 0;
	}

	private int matchLen(byte[] lData, int lStart, byte[] rData, int rStart) {
		int i, j;
		for (i = lStart, j = rStart; i < lData.length && j < rData.length; ++i, ++j) {
			if (lData[i] != rData[j]) {
				break;
			}
		}
		return i - lStart;
	}
}
