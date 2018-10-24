package com.ctech.bitmp4;


import org.jetbrains.annotations.NotNull;

public class EncodingOptions {
    public static final int COMPRESS_HIGH = 2;
    public static final int COMPRESS_LOW = 0;
    public static final int COMPRESS_MID = 1;
    public int compressLevel;

    @NotNull
    @Override
    public String toString() {
        return "EncodingOptions : compLevel = " + this.compressLevel;
    }
}