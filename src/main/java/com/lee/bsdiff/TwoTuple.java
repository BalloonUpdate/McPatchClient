package com.lee.bsdiff;

/**
 * 本文件修改自Github SparkInLee 的开源项目 https://github.com/SparkInLee/jbsdiff
 */

public class TwoTuple<T, R> {
	T t;
	R r;

	public TwoTuple() {

	}

	public TwoTuple(T t, R r) {
		this.t = t;
		this.r = r;
	}

	public TwoTuple<T, R> setFirst(T t) {
		this.t = t;
		return this;
	}

	public T getFirst() {
		return t;
	}

	public TwoTuple<T, R> setSecond(R r) {
		this.r = r;
		return this;
	}

	public R getSecond() {
		return r;
	}
}
