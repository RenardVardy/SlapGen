package cz.muni.fi.lazon.wsq;

/**
 * This class contains definitions of reference implementation constants.
 */
final class Constants {

    static final double[] HI_FILT_NOT_EVEN_8X8_1 = {
            0.06453888262893845,
            -0.04068941760955844,
            -0.41809227322221221,
            0.78848561640566439,
            -0.41809227322221221,
            -0.04068941760955844,
            0.06453888262893845};

    static final double[] LO_FILT_NOT_EVEN_8X8_1 = {
            0.03782845550699546,
            -0.02384946501938000,
            -0.11062440441842342,
            0.37740285561265380,
            0.85269867900940344,
            0.37740285561265380,
            -0.11062440441842342,
            -0.02384946501938000,
            0.03782845550699546};

    /* NIST constants */
    static final String NCM_HEADER = "NIST_COM";         /* mandatory */
    static final String NCM_PIX_WIDTH = "PIX_WIDTH";     /* mandatory */
    static final String NCM_PIX_HEIGHT = "PIX_HEIGHT";   /* mandatory */
    static final String NCM_PIX_DEPTH = "PIX_DEPTH";     /* 1,8,24 (mandatory)*/
    static final String NCM_PPI = "PPI";                 /* -1 if unknown (mandatory)*/
    static final String NCM_COLORSPACE = "COLORSPACE";   /* RGB,YCbCr,GRAY */
    static final String NCM_COMPRESSION = "COMPRESSION"; /* NONE, JPEGB, JPEGL, WSQ */
    static final String NCM_WSQ_RATE = "WSQ_BITRATE";    /* ex. .75,2.25 (-1.0 if unknown)*/
    static final String NCM_LOSSY = "LOSSY";             /* 0,1 */

    static final int MAX_HUFFBITS = 16;
    static final int MAX_HUFFCOUNTS_WSQ = 256;

    static final int MAX_HUFFCOEFF = 74;/* -73 .. +74 */
    static final int MAX_HUFFZRUN = 100;

    static final int W_TREELEN = 20;
    static final int Q_TREELEN = 64;

    /* WSQ Marker Definitions */
    static final int SOI_WSQ = 0xffa0;
    static final int EOI_WSQ = 0xffa1;
    static final int SOF_WSQ = 0xffa2;
    static final int SOB_WSQ = 0xffa3;
    static final int DTT_WSQ = 0xffa4;
    static final int DQT_WSQ = 0xffa5;
    static final int DHT_WSQ = 0xffa6;
    static final int COM_WSQ = 0xffa8;

    static final int STRT_SUBBAND_2 = 19;
    static final int STRT_SUBBAND_3 = 52;
    static final int MAX_SUBBANDS = 64;
    static final int NUM_SUBBANDS = 60;
    static final int STRT_SUBBAND_DEL = NUM_SUBBANDS;
    static final int STRT_SIZE_REGION_2 = 4;
    static final int STRT_SIZE_REGION_3 = 51;

    static final int COEFF_CODE = 0;
    static final int RUN_CODE = 1;

    static final float VARIANCE_THRESH = 1.01f;

    private Constants() {

    }

}