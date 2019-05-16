package cz.muni.fi.lazon.wsq;

import java.io.DataOutput;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import static cz.muni.fi.lazon.wsq.Constants.*;

/**
 * Implementation of WSQ encoder
 */
final class EncoderImpl {

    private static final Logger log = Logger.getLogger(EncoderImpl.class.getName());
    
    private EncoderImpl() {}

    static void encode(
            final DataOutput dataOutput, 
            final Bitmap bitmap, 
            final float bitRate,
            final boolean includeHeader,
            final String comment) throws IOException {
        final double[] fdata;                   /* floating point pixel image  */
        final int[] qdata;                      /* quantized image pointer     */
        final Ref<Float> mShift = new Ref<>();
        final Ref<Float> rScale = new Ref<>();
        final Ref<Integer> qsize = new Ref<>();  /* quantized block size */
        final Ref<Integer> qsize1 = new Ref<>(); /* quantized block size */
        final Ref<Integer> qsize2 = new Ref<>(); /* quantized block size */
        final Ref<Integer> qsize3 = new Ref<>(); /* quantized block size */
        final Ref<int[]> huffbits = new Ref<>();
        final Ref<int[]> huffvalues = new Ref<>(); /* huffman code parameters */
        HuffCode[] hufftable;                      /* huffcode table */

        WaveletTree[] wTree = new WaveletTree[Constants.W_TREELEN];
        for (int i = 0; i < wTree.length; i++) {
            wTree[i] = new WaveletTree();
        }
        QuantTree[] qTree = new QuantTree[Constants.Q_TREELEN];
        for (int i = 0; i < qTree.length; i++) {
            qTree[i] = new QuantTree();
        }
        Quantization quantVals = new Quantization();

        /* Convert image pixels to floating point. */
        fdata = convImg2FltRet(bitmap.getPixels(), mShift, rScale);
        log.info("Input image pixels converted to floating point.");

        /* Build WSQ decomposition trees */
        DataStructures.buildWSQTrees(wTree, qTree, bitmap.getWidth(), bitmap.getHeight());
        log.info("Tables for wavelet decomposition finished.");

        /* WSQ decompose the image */
        wsqDecompose(fdata, bitmap.getWidth(), bitmap.getHeight(), wTree, HI_FILT_NOT_EVEN_8X8_1, LO_FILT_NOT_EVEN_8X8_1);
        log.info("WSQ decomposition of image finished.");

        /* Assign specified r-bitrate into quantization structure. */
        quantVals.r = bitRate;

        /* Compute subband variances. */
        variance(quantVals, qTree, fdata, bitmap.getWidth());
        log.info("Subband variances computed");

        /* Quantize the floating point pixmap. */
        qdata = quantize(qsize, quantVals, qTree, fdata, bitmap.getWidth(), bitmap.getHeight());
        log.info("WSQ subband decomposition data quantized");

        /* Compute quantized WSQ subband block sizes */
        quantBlockSizes(qsize1, qsize2, qsize3, quantVals, wTree, qTree);

        if (qsize.value != qsize1.value + qsize2.value + qsize3.value) {
            throw new IllegalStateException("ERROR : wsq_encode_1 : problem w/quantization block sizes");
        }

        /* Add a Start Of Image (SOI) marker to the WSQ buffer. */
        dataOutput.writeShort(SOI_WSQ);

        if(includeHeader) {
            putcNistcomWsq(dataOutput, bitmap, bitRate, comment);
        }

        /* Store the Wavelet filter taps to the WSQ buffer. */
        putcTransformTable(dataOutput, LO_FILT_NOT_EVEN_8X8_1, HI_FILT_NOT_EVEN_8X8_1);

        /* Store the quantization parameters to the WSQ buffer. */
        putcQuantizationTable(dataOutput, quantVals);

        /* Store a frame header to the WSQ buffer. */
        putcFrameHeaderWsq(dataOutput, bitmap.getWidth(), bitmap.getHeight(), mShift.value, rScale.value);
        log.info("SOI, tables, and frame header writte.");

        /*----------------*/
        /* ENCODE Block 1 */
        /*----------------*/
        /* Compute Huffman table for Block 1. */
        hufftable = genHufftableWsq(huffbits, huffvalues, qdata, 0, new int[]{qsize1.value});

        /* Store Huffman table for Block 1 to WSQ buffer. */
        putcHuffmanTable(dataOutput, DHT_WSQ, 0, huffbits.value, huffvalues.value);
        log.info("Huffman code Table 1 generated and written");

        /* Store Block 1's header to WSQ buffer. */
        putcBlockHeader(dataOutput, 0);

        /* Compress Block 1 data. */
        compressBlock(dataOutput, qdata, 0, qsize1.value, MAX_HUFFCOEFF, MAX_HUFFZRUN, hufftable);
        log.info("Block 1 compressed and written.");

        /*----------------*/
        /* ENCODE Block 2 */
        /*----------------*/
        /* Compute  Huffman table for Blocks 2 & 3. */
        hufftable = genHufftableWsq(huffbits, huffvalues, qdata, qsize1.value, new int[]{qsize2.value, qsize3.value});

        /* Store Huffman table for Blocks 2 & 3 to WSQ buffer. */
        putcHuffmanTable(dataOutput, DHT_WSQ, 1, huffbits.value, huffvalues.value);
        log.info("Huffman code Table 2 generated and written.");

        /* Store Block 2's header to WSQ buffer. */
        putcBlockHeader(dataOutput, 1);

        /* Compress Block 2 data. */
        compressBlock(dataOutput, qdata, qsize1.value, qsize2.value, MAX_HUFFCOEFF, MAX_HUFFZRUN, hufftable);
        log.info("Block 2 compressed and written.");

        /*----------------*/
        /* ENCODE Block 3 */
        /*----------------*/
        /* Store Block 3's header to WSQ buffer. */
        putcBlockHeader(dataOutput, 1);

        /* Compress Block 3 data. */
        compressBlock(dataOutput, qdata, qsize1.value + qsize2.value, qsize3.value, MAX_HUFFCOEFF, MAX_HUFFZRUN, hufftable);
        log.info("Block 3 compressed and written.");

        /* Add a End Of Image (EOI) marker to the WSQ buffer. */
        dataOutput.writeShort(EOI_WSQ);
    }

    /**
     * This routine converts the unsigned char data to float. In the process it shifts and scales the data so the values
     * range from +/- 128.0
     *
     * @param data   input image data as byte array
     * @param mShift shifting parameter
     * @param rScale scaling parameter
     * @return image data as float array
     */
    private static double[] convImg2FltRet(
            final byte[] data,
            final Ref<Float> mShift,
            final Ref<Float> rScale) {
        if (data == null) {
            throw new IllegalArgumentException("Image data cannot be null");
        }
        if (mShift == null) {
            throw new IllegalArgumentException("mShift cannot be null");
        }
        if (rScale == null) {
            throw new IllegalArgumentException("rScale cannot be null");
        }

        int cnt;                     /* pixel cnt */
        long sum, overflow;          /* sum of pixel values */
        int low, high;               /* low/high pixel values */
        float lowDiff, highDiff;   /* new low/high pixels values shifting */
        final double[] fip = new double[data.length]; /* output float image data  */

        sum = 0;
        overflow = 0;
        low = 255;
        high = 0;
        //& 0xFF required to compensate overflow caused by signed byte data type
        for (cnt = 0; cnt < data.length; cnt++) {
            if ((data[cnt] & 0xFF) > high) {
                high = data[cnt] & 0xFF;
            }
            if ((data[cnt] & 0xFF) < low) {
                low = data[cnt] & 0xFF;
            }
            sum += (data[cnt] & 0xFF);
            if (sum < overflow) {
                throw new IllegalStateException("Image data overflow, input too big");
            }
            overflow = sum;
        }

        mShift.value = (float) ((double) sum / data.length);

        lowDiff = mShift.value - low;
        highDiff = high - mShift.value;

        if (lowDiff >= highDiff) {
            rScale.value = lowDiff;
        } else {
            rScale.value = highDiff;
        }

        rScale.value /= 128f;

        //& 0xFF required to compensate overflow caused by signed byte data type
        for (cnt = 0; cnt < data.length; cnt++) {
            fip[cnt] = ((data[cnt] & 0xFF) - mShift.value) / rScale.value;
        }

        return fip;
    }

    /**
     * @param fdata
     * @param width
     * @param height
     * @param wTree
     * @param hifilt
     * @param lofilt
     */
    private static void wsqDecompose(final double[] fdata,
                                     final int width,
                                     final int height,
                                     final WaveletTree[] wTree,
                                     final double[] hifilt,
                                     final double[] lofilt) {
        final int numPix = width * height;
        /* Allocate temporary floating point pixmap. */
        final double[] fdata1 = new double[numPix];

        /* Compute the Wavelet image decomposition. */
        for (int node = 0; node < wTree.length; node++) {
            final int fdataBseIndex = (wTree[node].y * width) + wTree[node].x;

            getLets(fdata1, fdata, 0, fdataBseIndex, wTree[node].leny, wTree[node].lenx,
                    width, 1, hifilt, lofilt, wTree[node].invrw);
            getLets(fdata, fdata1, fdataBseIndex, 0, wTree[node].lenx, wTree[node].leny,
                    1, width, hifilt, lofilt, wTree[node].invcl);
        }

    }

    /**
     * @param newdata
     * @param olddata
     * @param newIndex
     * @param oldIndex
     * @param len1
     * @param len2
     * @param pitch
     * @param stride
     * @param hi
     * @param lo
     * @param inv
     */
    private static void getLets(final double[] newdata,
                                final double[] olddata,
                                final int newIndex,
                                final int oldIndex,
                                final int len1,       /* temporary length parameters */
                                final int len2,
                                final int pitch,      /* pitch gives next row_col to filter */
                                final int stride,    /*           stride gives next pixel to filter */
                                final double[] hi,
                                final double[] lo,      /* filter coefficients */
                                final int inv)        /* spectral inversion? */ {
        if (newdata == null) {
            throw new IllegalArgumentException("newdata cannot be null");
        }
        if (olddata == null) {
            throw new IllegalArgumentException("olddata cannot be null");
        }
        if (lo == null) {
            throw new IllegalArgumentException("lo cannot be null");
        }

        int lopassIndex, hipassIndex;	/* indexes where to put lopass and hipass filter outputs */
        int p0Index, p1Index;		/* indexes of image pixels used */
        int daEv;		/* even or odd row/column of pixels */
        int fiEv;
        int loc, hoc, nstr, pstr;
        int llen, hlen;
        int lpxstr, lspxstr;
        int lpxIndex, lspxIndex;
        int hpxstr, hspxstr;
        int hpxIndex, hspxIndex;
        int olle, ohle;
        int olre, ohre;
        int lle, lle2;
        int lre, lre2;
        int hle, hle2;
        int hre, hre2;

        daEv = len2 % 2;
        fiEv = lo.length % 2;

        if (fiEv != 0) {
            loc = (lo.length - 1) / 2;
            hoc = (hi.length - 1) / 2 - 1;
            olle = 0;
            ohle = 0;
            olre = 0;
            ohre = 0;
        } else {
            loc = lo.length / 2 - 2;
            hoc = hi.length / 2 - 2;
            olle = 1;
            ohle = 1;
            olre = 1;
            ohre = 1;

            if (loc == -1) {
                loc = 0;
                olle = 0;
            }
            if (hoc == -1) {
                hoc = 0;
                ohle = 0;
            }

            for (int i = 0; i < hi.length; i++) {
                hi[i] *= -1.0;
            }
        }

        pstr = stride;
        nstr = -pstr;

        if (daEv != 0) {
            llen = (len2 + 1) / 2;
            hlen = llen - 1;
        } else {
            llen = len2 / 2;
            hlen = llen;
        }

        for (int rwCl = 0; rwCl < len1; rwCl++) {
            if (inv != 0) {
                hipassIndex = newIndex + rwCl * pitch;
                lopassIndex = hipassIndex + hlen * stride;
            } else {
                lopassIndex = newIndex + rwCl * pitch;
                hipassIndex = lopassIndex + llen * stride;
            }

            p0Index = oldIndex + rwCl * pitch;
            p1Index = p0Index + (len2 - 1) * stride;

            lspxIndex = p0Index + (loc * stride);
            lspxstr = nstr;
            lle2 = olle;
            lre2 = olre;
            hspxIndex = p0Index + (hoc * stride);
            hspxstr = nstr;
            hle2 = ohle;
            hre2 = ohre;
            for (int pix = 0; pix < hlen; pix++) {
                lpxstr = lspxstr;
                lpxIndex = lspxIndex;
                lle = lle2;
                lre = lre2;
                newdata[lopassIndex] = olddata[lpxIndex] * lo[0];
                for (int i = 1; i < lo.length; i++) {
                    if (lpxIndex == p0Index) {
                        if (lle != 0) {
                            lpxstr = 0;
                            lle = 0;
                        } else {
                            lpxstr = pstr;
                        }
                    }
                    if (lpxIndex == p1Index) {
                        if (lre != 0) {
                            lpxstr = 0;
                            lre = 0;
                        } else {
                            lpxstr = nstr;
                        }
                    }
                    lpxIndex += lpxstr;
                    newdata[lopassIndex] += olddata[lpxIndex] * lo[i];
                }
                lopassIndex += stride;

                hpxstr = hspxstr;
                hpxIndex = hspxIndex;
                hle = hle2;
                hre = hre2;
                newdata[hipassIndex] = olddata[hpxIndex] * hi[0];
                for (int i = 1; i < hi.length; i++) {
                    if (hpxIndex == p0Index) {
                        if (hle != 0) {
                            hpxstr = 0;
                            hle = 0;
                        } else {
                            hpxstr = pstr;
                        }
                    }
                    if (hpxIndex == p1Index) {
                        if (hre != 0) {
                            hpxstr = 0;
                            hre = 0;
                        } else {
                            hpxstr = nstr;
                        }
                    }
                    hpxIndex += hpxstr;
                    newdata[hipassIndex] += olddata[hpxIndex] * hi[i];
                }
                hipassIndex += stride;

                for (int i = 0; i < 2; i++) {
                    if (lspxIndex == p0Index) {
                        if (lle2 != 0) {
                            lspxstr = 0;
                            lle2 = 0;
                        } else {
                            lspxstr = pstr;
                        }
                    }
                    lspxIndex += lspxstr;
                    if (hspxIndex == p0Index) {
                        if (hle2 != 0) {
                            hspxstr = 0;
                            hle2 = 0;
                        } else {
                            hspxstr = pstr;
                        }
                    }
                    hspxIndex += hspxstr;
                }
            }
            if (daEv != 0) {
                lpxstr = lspxstr;
                lpxIndex = lspxIndex;
                lle = lle2;
                lre = lre2;
                newdata[lopassIndex] = olddata[lpxIndex] * lo[0];
                for (int i = 1; i < lo.length; i++) {
                    if (lpxIndex == p0Index) {
                        if (lle != 0) {
                            lpxstr = 0;
                            lle = 0;
                        } else {
                            lpxstr = pstr;
                        }
                    }
                    if (lpxIndex == p1Index) {
                        if (lre != 0) {
                            lpxstr = 0;
                            lre = 0;
                        } else {
                            lpxstr = nstr;
                        }
                    }
                    lpxIndex += lpxstr;
                    newdata[lopassIndex] += olddata[lpxIndex] * lo[i];
                }
                lopassIndex += stride;
            }
        }
        if (fiEv == 0) {
            for (int i = 0; i < hi.length; i++) {
                hi[i] *= -1.0;
            }
        }
    }

    /**
     * This routine calculates the variances of the subbands.
     *
     * @param quantVals contains quant_vals quantization parameters and quantization "tree" and treelen.NOTE: This routine will write to var field inside quant_vals
     * @param qTree     quantization "tree"
     * @param fip       image pointer
     * @param width     image width
     */
    private static void variance(
            final Quantization quantVals,
            final QuantTree[] qTree,
            final double[] fip,
            final int width) {
        int fpIndex;            /* temp image index */
        int lenx, leny;         /* dimensions of area to calculate variance */
        int skipx, skipy;       /* pixels to skip to get to area for variance calculation */
        int row, col;           /* dimension counters */
        float ssq;              /* sum of squares */
        float sum2;             /* variance calculation parameter */
        float sumPix;          /* sum of pixels */
        float vsum;             /* variance sum for subbands 0-3 */

        vsum = 0;
        for (int cvr = 0; cvr < 4; cvr++) {
            fpIndex = ((qTree[cvr].y) * width) + qTree[cvr].x;
            ssq = 0.0f;
            sumPix = 0.0f;

            skipx = qTree[cvr].lenx / 8;
            skipy = (9 * qTree[cvr].leny) / 32;

            lenx = (3 * qTree[cvr].lenx) / 4;
            leny = (7 * qTree[cvr].leny) / 16;

            fpIndex += (skipy * width) + skipx;
            for (row = 0; row < leny; row++, fpIndex += (width - lenx)) {
                for (col = 0; col < lenx; col++) {
                    sumPix += fip[fpIndex];
                    ssq += fip[fpIndex] * fip[fpIndex];
                    fpIndex++;
                }
            }
            sum2 = (sumPix * sumPix) / (lenx * leny);
            quantVals.var[cvr] = ((ssq - sum2) / ((lenx * leny) - 1.0f));
            vsum += quantVals.var[cvr];
        }

        if (vsum < 20000.0) {
            for (int cvr = 0; cvr < NUM_SUBBANDS; cvr++) {
                fpIndex = (qTree[cvr].y * width) + qTree[cvr].x;
                ssq = 0.0f;
                sumPix = 0.0f;

                lenx = qTree[cvr].lenx;
                leny = qTree[cvr].leny;

                for (row = 0; row < leny; row++, fpIndex += (width - lenx)) {
                    for (col = 0; col < lenx; col++) {
                        sumPix += fip[fpIndex];
                        ssq += fip[fpIndex] * fip[fpIndex];
                        fpIndex++;
                    }
                }
                sum2 = (sumPix * sumPix) / (lenx * leny);
                quantVals.var[cvr] = ((ssq - sum2) / ((lenx * leny) - 1.0f));
            }
        } else {
            for (int cvr = 4; cvr < NUM_SUBBANDS; cvr++) {
                fpIndex = (qTree[cvr].y * width) + qTree[cvr].x;
                ssq = 0.0f;
                sumPix = 0.0f;

                skipx = qTree[cvr].lenx / 8;
                skipy = (9 * qTree[cvr].leny) / 32;

                lenx = (3 * qTree[cvr].lenx) / 4;
                leny = (7 * qTree[cvr].leny) / 16;

                fpIndex += (skipy * width) + skipx;
                for (row = 0; row < leny; row++, fpIndex += (width - lenx)) {
                    for (col = 0; col < lenx; col++) {
                        sumPix += fip[fpIndex];
                        ssq += fip[fpIndex] * fip[fpIndex];
                        fpIndex++;
                    }
                }
                sum2 = (sumPix * sumPix) / (lenx * leny);
                quantVals.var[cvr] = ((ssq - sum2) / ((lenx * leny) - 1.0f));
            }
        }
    }

    /**
     * This routine quantizes the wavelet subbands.
     *
     * @param qsize     size of quantized output
     * @param quantVals quantization parameters
     * @param qTree     quantization "tree"
     * @param fip       floating point image pointer
     * @param width     image width
     * @param height    image height
     * @return quantized image
     */
    private static int[] quantize(
            final Ref<Integer> qsize,
            final Quantization quantVals,
            final QuantTree[] qTree,
            final double[] fip,
            final int width,
            final int height) {
        int row, col;          /* temp image characteristic parameters */
        float zbin;            /* zero bin size */
        float[] A = new float[NUM_SUBBANDS]; /* subband "weights" for quantization */
        float[] m = new float[NUM_SUBBANDS]; /* subband size to image size ratios */
            /* (reciprocal of FBI spec for 'm')  */
        float m1, m2, m3;      /* reciprocal constants for 'm' */
        float[] sigma = new float[NUM_SUBBANDS]; /* square root of subband variances */
        int[] K0 = new int[NUM_SUBBANDS];  /* initial list of subbands w/variance >= thresh */
        int[] K1 = new int[NUM_SUBBANDS];  /* working list of subbands */
        int KIndex, nKIndex;           /* indexes in sets of subbands */
        boolean[] NP = new boolean[NUM_SUBBANDS];  /* current subbounds with nonpositive bit rates. */
        int K0len;             /* number of subbands in K0 */
        int Klen, nKlen;       /* number of subbands in other subband lists */
        int NPlen;             /* number of subbands flagged in NP */
        float S;               /* current frac of subbands w/positive bit rate */
        float q;               /* current proportionality constant */
        float P;               /* product of 'q/Q' ratios */

        /* Set up 'A' table. */
        Arrays.fill(A, 0, STRT_SUBBAND_3, 1.0f);
        A[STRT_SUBBAND_3 /*52*/] = 1.32f;
        A[STRT_SUBBAND_3 + 1 /*53*/] = 1.08f;
        A[STRT_SUBBAND_3 + 2 /*54*/] = 1.42f;
        A[STRT_SUBBAND_3 + 3 /*55*/] = 1.08f;
        A[STRT_SUBBAND_3 + 4 /*56*/] = 1.32f;
        A[STRT_SUBBAND_3 + 5 /*57*/] = 1.42f;
        A[STRT_SUBBAND_3 + 6 /*58*/] = 1.08f;
        A[STRT_SUBBAND_3 + 7 /*59*/] = 1.08f;

        /* Set up 'Q1' (prime) table. */
        for (int cnt = 0; cnt < NUM_SUBBANDS; cnt++) {
            if (quantVals.var[cnt] < VARIANCE_THRESH) {
                quantVals.qbss[cnt] = 0.0f;
            } else {
                /* NOTE: q has been taken out of the denominator in the next 2 formulas from the original code. */
                if (cnt < STRT_SIZE_REGION_2 /*4*/) {
                    quantVals.qbss[cnt] = 1.0f;
                } else {
                    quantVals.qbss[cnt] = 10.0f / (A[cnt] * (float) Math.log(quantVals.var[cnt]));
                }
            }
        }

        /* Set up output buffer. */
        int[] sip = new int[width * height];
        /* Index in quantized image array*/
        int sptrIndex = 0;

        /* Set up 'm' table (these values are the reciprocal of 'm' in the FBI spec). */
        m1 = 1.0f / 1024.0f;
        m2 = 1.0f / 256.0f;
        m3 = 1.0f / 16.0f;
        Arrays.fill(m, 0, STRT_SIZE_REGION_2, m1);
        Arrays.fill(m, STRT_SIZE_REGION_2, STRT_SIZE_REGION_3, m2);
        Arrays.fill(m, STRT_SIZE_REGION_3, NUM_SUBBANDS, m3);

        /* Initialize 'K0' and 'K1' lists. */
        K0len = 0;
        for (int cnt = 0; cnt < NUM_SUBBANDS; cnt++) {
            if (quantVals.var[cnt] >= VARIANCE_THRESH) {
                K0[K0len] = cnt;
                K1[K0len++] = cnt;
                    /* Compute square root of subband variance. */
                sigma[cnt] = (float) Math.sqrt(quantVals.var[cnt]);
            }
        }
        KIndex = 0;
        Klen = K0len;

        while (true) {
            /* Compute new 'S' */
            S = 0.0f;
            for (int i = 0; i < Klen; i++) {
                /* Remember 'm' is the reciprocal of spec. */
                S += m[K1[KIndex + i]];
            }

            /* Compute product 'P' */
            P = 1.0f;
            for (int i = 0; i < Klen; i++) {
                /* Remember 'm' is the reciprocal of spec. */
                P *= (float) Math.pow(sigma[K1[KIndex + i]] / quantVals.qbss[K1[KIndex + i]], m[K1[KIndex + i]]);
            }

            /* Compute new 'q' */
            q = ((float) Math.pow(2, ((quantVals.r / S) - 1.0f)) / 2.5f) / (float) Math.pow(P, (1.0f / S));

            /* Flag subbands with non-positive bitrate. */
            NP = new boolean[NUM_SUBBANDS];
            NPlen = 0;
            for (int i = 0; i < Klen; i++) {
                if ((quantVals.qbss[K1[KIndex + i]] / q) >= (5.0 * sigma[K1[KIndex + i]])) {
                    NP[K1[KIndex + i]] = true;
                    NPlen++;
                }
            }

            /* If list of subbands with non-positive bitrate is empty ... */
            if (NPlen == 0) {
                /* Then we are done, so break from while loop. */
                break;
            }

            /* Assign new subband set to previous set K minus subbands in set NP. */
            nKIndex = 0;
            nKlen = 0;
            for (int i = 0; i < Klen; i++) {
                if (!NP[K1[KIndex + i]]) {
                    K1[nKIndex + nKlen++] = K1[KIndex + i];
                }
            }

            /* Assign new set as K. */
            KIndex = nKIndex;
            Klen = nKlen;
        }

        /* Flag subbands that are in set 'K0' (the very first set). */
        nKIndex = 0;

        Arrays.fill(K1, nKIndex, NUM_SUBBANDS, 0);
        for (int i = 0; i < K0len; i++) {
            K1[nKIndex + K0[i]] = 1;
        }
        /* Set 'Q' values. */
        for (int cnt = 0; cnt < NUM_SUBBANDS; cnt++) {
            if (K1[nKIndex + cnt] != 0) {
                quantVals.qbss[cnt] /= q;
            } else {
                quantVals.qbss[cnt] = 0.0f;
            }
            quantVals.qzbs[cnt] = 1.2f * quantVals.qbss[cnt];
        }

        /* Now ready to compute and store bin widths for subbands. */
        for (int cnt = 0; cnt < NUM_SUBBANDS; cnt++) {
            int fptrIndex = (qTree[cnt].y * width) + qTree[cnt].x;

            if (quantVals.qbss[cnt] != 0.0f) {

                zbin = quantVals.qzbs[cnt] / 2.0f;

                for (row = 0; row < qTree[cnt].leny; row++, fptrIndex += width - qTree[cnt].lenx) {
                    for (col = 0; col < qTree[cnt].lenx; col++) {
                        if (-zbin <= fip[fptrIndex] && fip[fptrIndex] <= zbin) {
                            sip[sptrIndex] = 0;
                        } else if (fip[fptrIndex] > 0.0f) {
                            sip[sptrIndex] = (int) (((fip[fptrIndex] - zbin) / quantVals.qbss[cnt]) + 1.0f);
                        } else {
                            sip[sptrIndex] = (int) (((fip[fptrIndex] + zbin) / quantVals.qbss[cnt]) - 1.0f);
                        }
                        sptrIndex++;
                        fptrIndex++;
                    }
                }
            }
        }
        qsize.value = sptrIndex;

        return sip;
    }

    /**
     * Compute quantized WSQ subband block sizes.
     *
     * @param oqsize1
     * @param oqsize2
     * @param oqsize3
     * @param quantVals
     * @param wTree
     * @param qTree
     */
    private static void quantBlockSizes(
            final Ref<Integer> oqsize1,
            final Ref<Integer> oqsize2,
            final Ref<Integer> oqsize3,
            final Quantization quantVals,
            final WaveletTree[] wTree,
            final QuantTree[] qTree) {
        int qsize1, qsize2, qsize3;

        /* Compute temporary sizes of 3 WSQ subband blocks. */
        qsize1 = wTree[14].lenx * wTree[14].leny;
        qsize2 = (wTree[5].leny * wTree[1].lenx) +
                (wTree[4].lenx * wTree[4].leny);
        qsize3 = (wTree[2].lenx * wTree[2].leny) +
                (wTree[3].lenx * wTree[3].leny);

        /* Adjust size of quantized WSQ subband blocks. */
        for (int node = 0; node < STRT_SUBBAND_2; node++) {
            if (quantVals.qbss[node] == 0.0f) {
                qsize1 -= (qTree[node].lenx * qTree[node].leny);
            }
        }

        for (int node = STRT_SUBBAND_2; node < STRT_SUBBAND_3; node++) {
            if (quantVals.qbss[node] == 0.0f) {
                qsize2 -= (qTree[node].lenx * qTree[node].leny);
            }
        }

        for (int node = STRT_SUBBAND_3; node < STRT_SUBBAND_DEL; node++) {
            if (quantVals.qbss[node] == 0.0f) {
                qsize3 -= (qTree[node].lenx * qTree[node].leny);
            }
        }

        oqsize1.value = qsize1;
        oqsize2.value = qsize2;
        oqsize3.value = qsize3;
    }

    /**
     * Writes huffman table to the compressed memory buffer
     *
     * @param dataOutput output byte buffer
     * @param marker     Markers are different for JPEGL and WSQ
     * @param tableId    huffman table indicator
     * @param huffbits   huffman table parameters
     * @param huffvalues huffman table parameters
     * @throws IOException
     */
    private static void putcHuffmanTable(final DataOutput dataOutput,
                                         final int marker,
                                         final int tableId,
                                         final int[] huffbits,
                                         final int[] huffvalues) throws IOException {
        /* DHT */
        dataOutput.writeShort(marker);

        /* "value(2) + table id(1) + bits(16)" */
        int tableLen = 3 + MAX_HUFFBITS;
        int valuesOffset = tableLen;
        for (int i = 0; i < MAX_HUFFBITS; i++) {
            tableLen += huffbits[i];   /* values size */
        }

        /* Table Len */
        dataOutput.writeShort(tableLen & 0xFFFF);

        /* Table ID */
        dataOutput.writeByte(tableId & 0xFF);

        /* Huffbits (MAX_HUFFBITS) */
        for (int i = 0; i < MAX_HUFFBITS; i++) {
            dataOutput.writeByte(huffbits[i] & 0xFF);
        }

        /* Huffvalues (MAX_HUFFCOUNTS) */
        for (int i = 0; i < tableLen - valuesOffset; i++) {
            dataOutput.writeByte(huffvalues[i] & 0xFF);
        }

    }

    /**
     * @param dataOutput
     * @param width
     * @param height
     * @param mShift     image shifting paramete
     * @param rScale     image scaling parameter
     * @throws IOException
     */
    private static void putcFrameHeaderWsq(
            final DataOutput dataOutput,
            final int width,
            final int height,
            final float mShift,
            final float rScale) throws IOException {
        float fltTmp;         /* temp variable */
        int scaleEx;         /* exponent scaling parameter */

        int shrtDat;       /* temp variable */
        dataOutput.writeShort(SOF_WSQ); /* +2 = 2 */

        /* size of frame header */
        dataOutput.writeShort(17);

        /* black pixel */
        dataOutput.writeByte(0); /* +1 = 3 */

        /* white pixel */
        dataOutput.writeByte(255); /* +1 = 4 */

        dataOutput.writeShort(height); /* +2 = 5 */
        dataOutput.writeShort(width); /* +2 = 7 */

        fltTmp = mShift;
        scaleEx = 0;
        if (fltTmp != 0.0f) {
            while (fltTmp < 65535f) {
                scaleEx += 1;
                fltTmp *= 10f;
            }
            scaleEx -= 1;
            shrtDat = Math.round(fltTmp / 10.0f);
        } else {
            shrtDat = 0;
        }
        dataOutput.writeByte(scaleEx & 0xFF); /* +1 = 9 */
        dataOutput.writeShort(shrtDat); /* +2 = 11 */

        fltTmp = rScale;
        scaleEx = 0;
        if (fltTmp != 0.0f) {
            while (fltTmp < 65535f) {
                scaleEx += 1;
                fltTmp *= 10f;
            }
            scaleEx -= 1;
            shrtDat = Math.round(fltTmp / 10.0f);
        } else {
            shrtDat = 0;
        }
        dataOutput.writeByte(scaleEx); /* +1 = 12 */
        dataOutput.writeShort(shrtDat); /* +2 = 13 */

        dataOutput.writeByte(2); /* +1 = 15 */
        dataOutput.writeShort(0x2B8E); /* +2 = 17 */

    }

    /**
     * Stores transform table to the output buffer
     *
     * @param dataOutput output byte buffer
     * @param lofilt     filter coefficients
     * @param hifilt     filter coefficients
     * @throws IOException
     */
    private static void putcTransformTable(final DataOutput dataOutput,
                                           final double[] lofilt,
                                           final double[] hifilt) throws IOException {
        long intDat;		/* temp variable */
        double dblTmp;		/* temp variable */
        int scaleEx, sign;	/* exponent scaling and sign parameters */

        dataOutput.writeShort(DTT_WSQ);

        /* table size */
        dataOutput.writeShort(58);

        /* number analysis lowpass coefficients */
        dataOutput.writeByte(lofilt.length);

        /* number analysis highpass coefficients */
        dataOutput.writeByte(hifilt.length);

        for (int coef = (lofilt.length >> 1); coef < lofilt.length; coef++) {
            dblTmp = lofilt[coef];
            if (dblTmp >= 0.0) {
                sign = 0;
            } else {
                sign = 1;
                dblTmp *= -1.0;
            }
            scaleEx = 0;
            if (dblTmp == 0.0) {
                intDat = 0;
            } else if (dblTmp < 4294967295.0) {
                while (dblTmp < 4294967295.0) {
                    scaleEx += 1;
                    dblTmp *= 10.0;
                }
                scaleEx -= 1;
                intDat = Math.round(dblTmp / 10.0);
            } else {
                dblTmp = lofilt[coef];
                throw new IllegalStateException(String.format("ERROR: putc_transform_table : lofilt[%d] to high at %f", coef, dblTmp));
            }
            dataOutput.writeByte(sign & 0xFF);
            dataOutput.writeByte(scaleEx & 0xFF);
            dataOutput.writeInt((int) (intDat & 0xFFFFFFFFL));
        }

        for (int coef = (hifilt.length >> 1); coef < hifilt.length; coef++) {
            dblTmp = hifilt[coef];
            if (dblTmp >= 0.0) {
                sign = 0;
            } else {
                sign = 1;
                dblTmp *= -1.0;
            }
            scaleEx = 0;
            if (dblTmp == 0.0) {
                intDat = 0;
            } else if (dblTmp < 4294967295.0) {
                while (dblTmp < 4294967295.0) {
                    scaleEx += 1;
                    dblTmp *= 10.0;
                }
                scaleEx -= 1;
                intDat = Math.round(dblTmp / 10.0);
            } else {
                dblTmp = hifilt[coef];
                throw new IllegalStateException("ERROR: putc_transform_table : hifilt[" + coef + "] to high at " + dblTmp + "");
            }
            dataOutput.writeByte(sign & 0xFF);
            dataOutput.writeByte(scaleEx & 0xFF);
            dataOutput.writeInt((int) (intDat & 0xFFFFFFFFL));
        }
    }

    /**
     * Stores quantization table in the output buffer.
     *
     * @param dataOutput output byte buffer
     * @param quantVals  quantization parameters
     * @throws IOException
     */
    private static void putcQuantizationTable(
            final DataOutput dataOutput,
            final Quantization quantVals) throws IOException {
        int scaleEx, scaleEx2; /* exponent scaling parameters */
        int shrtDat, shrtDat2;  /* temp variables */
        float fltTmp;            /* temp variable */

        dataOutput.writeShort(DQT_WSQ);

        /* table size */
        dataOutput.writeShort(389);

        /* exponent scaling value */
        dataOutput.writeByte(2);

        /* quantizer bin center parameter */
        dataOutput.writeShort(44);

        for (int sub = 0; sub < 64; sub++) {
            if (sub >= 0 && sub < 60) {
                if (quantVals.qbss[sub] != 0.0f) {
                    fltTmp = quantVals.qbss[sub];
                    scaleEx = 0;
                    if (fltTmp < 65535) {
                        while (fltTmp < 65535) {
                            scaleEx += 1;
                            fltTmp *= 10;
                        }
                        scaleEx -= 1;
                        shrtDat = Math.round(fltTmp / 10.0f);
                    } else {
                        fltTmp = quantVals.qbss[sub];
                        throw new IllegalStateException(String.format("ERROR : putc_quantization_table : Q[%d] too high at %f", sub, fltTmp));
                    }

                    fltTmp = quantVals.qzbs[sub];
                    scaleEx2 = 0;
                    if (fltTmp < 65535) {
                        while (fltTmp < 65535) {
                            scaleEx2 += 1;
                            fltTmp *= 10;
                        }
                        scaleEx2 -= 1;
                        shrtDat2 = Math.round(fltTmp / 10.0f);
                    } else {
                        fltTmp = quantVals.qzbs[sub];
                        throw new IllegalArgumentException(String.format("ERROR : putc_quantization_table : Z[%d] too high at %f", sub, fltTmp));
                    }
                } else {
                    scaleEx = 0;
                    scaleEx2 = 0;
                    shrtDat = 0;
                    shrtDat2 = 0;
                }
            } else {
                scaleEx = 0;
                scaleEx2 = 0;
                shrtDat = 0;
                shrtDat2 = 0;
            }
            dataOutput.writeByte(scaleEx & 0xFF);
            dataOutput.writeShort(shrtDat & 0xFFFF);
            dataOutput.writeByte(scaleEx2 & 0xFF);
            dataOutput.writeShort(shrtDat2 & 0xFFFF);
        }
    }

    /**
     * Stores block header to the output buffer.
     *
     * @param dataOutput output byte buffer
     * @param table      huffman table indicator
     * @throws IOException
     */
    private static void putcBlockHeader(final DataOutput dataOutput, final int table) throws IOException {
        dataOutput.writeShort(SOB_WSQ);

        /* block header size */
        dataOutput.writeShort(3);

        dataOutput.writeByte(table & 0xFF);
    }

    /**
     * @param dataOutput
     * @param bitmap
     * @param rBitrate
     * @throws IOException
     */
    private static void putcNistcomWsq(final DataOutput dataOutput,
                                       final Bitmap bitmap,
                                       final float rBitrate,
                                       final String comment) throws IOException {
        Map<String, String> nistcom = new LinkedHashMap<>();

        nistcom.put(NCM_HEADER, "");
        nistcom.put(NCM_PIX_WIDTH, "" + bitmap.getWidth());
        nistcom.put(NCM_PIX_HEIGHT, "" + bitmap.getHeight());
        nistcom.put(NCM_PIX_DEPTH, "8"); //WSQ has always 8 bpp
        nistcom.put(NCM_PPI, "" + bitmap.getPpi());
        nistcom.put(NCM_LOSSY, "1"); //WSQ is always lossy
        nistcom.put(NCM_COLORSPACE, "GRAY");
        nistcom.put(NCM_COMPRESSION, "WSQ");
        nistcom.put(NCM_WSQ_RATE, "" + rBitrate);

        //Update size
        nistcom.put(NCM_HEADER, "" + nistcom.size());

        putcComment(dataOutput, COM_WSQ, fetToString(nistcom));
        putcComment(dataOutput, COM_WSQ, "");
    }

    /**
     * Puts comment field in output buffer.
     *
     * @param dataOutput
     * @param marker
     * @param comment
     * @throws IOException
     */
    private static void putcComment(final DataOutput dataOutput, final int marker, final String comment) throws IOException {
        dataOutput.writeShort(marker);

        /* comment size */
        int hdrSize = 2 + comment.length();
        dataOutput.writeShort(hdrSize & 0xFFFF);

        dataOutput.write(comment.getBytes("UTF-8"));
    }

    /**
     * Generate a Huffman code table for a quantized data block.
     *
     * @param ohuffbits   should contain one byte[] reference
     * @param ohuffvalues should contain one byte[] reference
     * @param sip
     * @param offset
     * @param blockSizes
     * @return
     */
    private static HuffCode[] genHufftableWsq(
            final Ref<int[]> ohuffbits,
            final Ref<int[]> ohuffvalues,
            final int[] sip,
            final int offset,
            final int[] blockSizes) {
        int[] codesize;       /* code sizes to use */
        Ref<Integer> lastSize = new Ref<>();       /* last huffvalue */
        int[] huffbits;     /* huffbits values */
        int[] huffvalues;   /* huffvalues */
        int[] huffcounts;     /* counts for each huffman category */
        int[] huffcounts2;    /* counts for each huffman category */
        HuffCode[] hufftable1, hufftable2;  /* hufftables */

        huffcounts = countBlock(MAX_HUFFCOUNTS_WSQ, sip, offset, blockSizes[0], MAX_HUFFCOEFF, MAX_HUFFZRUN);

        for (int i = 1; i < blockSizes.length; i++) {
            huffcounts2 = countBlock(MAX_HUFFCOUNTS_WSQ, sip, offset + blockSizes[i - 1], blockSizes[i], MAX_HUFFCOEFF, MAX_HUFFZRUN);

            for (int j = 0; j < MAX_HUFFCOUNTS_WSQ; j++) {
                huffcounts[j] += huffcounts2[j];
            }
        }

        codesize = findHuffSizes(huffcounts, MAX_HUFFCOUNTS_WSQ);

            /* tells if codesize is greater than MAX_HUFFBITS */
        Ref<Boolean> adjust = new Ref<>();

        huffbits = findNumHuffSizes(adjust, codesize, MAX_HUFFCOUNTS_WSQ);

        if (adjust.value) {
            sortHuffbits(huffbits);
        }

        huffvalues = sortCodeSizes(codesize, MAX_HUFFCOUNTS_WSQ);

        hufftable1 = buildHuffsizes(lastSize, huffbits, MAX_HUFFCOUNTS_WSQ);

        buildHuffcodes(hufftable1);
        checkHuffcodesWsq(hufftable1, lastSize.value);

        hufftable2 = buildHuffcodeTable(hufftable1, lastSize.value, huffvalues, MAX_HUFFCOUNTS_WSQ);

        ohuffbits.value = huffbits;
        ohuffvalues.value = huffvalues;
        return hufftable2;
    }

    /**
     * This routine counts the number of occurences of each category in the huffman coding tables.
     *
     * @param maxHuffcounts maximum number of counts
     * @param sip           quantized data
     * @param sipOffset     offset into sip
     * @param sipSiz        size of block being compressed
     * @param MaxCoeff      maximum values for coefficients
     * @param MaxZRun       maximum zero runs
     * @return output count for each huffman catetory
     */
    private static int[] countBlock(
            final int maxHuffcounts,
            final int[] sip,
            final int sipOffset,
            final int sipSiz,
            final int MaxCoeff,
            final int MaxZRun) {
        int[] counts;         /* count for each huffman category */
        int LoMaxCoeff;        /* lower (negative) MaxCoeff limit */
        int pix;             /* temp pixel pointer */
        int rcnt = 0, state;  /* zero run count and if current pixel is in a zero run or just a coefficient */
        int cnt;               /* pixel counter */

        if (MaxCoeff < 0 || MaxCoeff > 0xffff) {
            throw new IllegalArgumentException("ERROR : compressBlock : MaxCoeff out of range.");
        }
        if (MaxZRun < 0 || MaxZRun > 0xffff) {
            throw new IllegalArgumentException("ERROR : compressBlock : MaxZRun out of range.");
        }
            /* Ininitalize vector of counts to 0. */
        counts = new int[maxHuffcounts + 1];
            /* Set last count to 1. */
        counts[maxHuffcounts] = 1;

        LoMaxCoeff = 1 - MaxCoeff;
        state = COEFF_CODE;
        for (cnt = sipOffset; cnt < sipSiz + sipOffset; cnt++) {
            pix = sip[cnt];
            switch (state) {

                case COEFF_CODE:   /* for runs of zeros */
                    if (pix == 0) {
                        state = RUN_CODE;
                        rcnt = 1;
                        break;
                    }
                    if (pix > MaxCoeff) {
                        if (pix > 255)
                            counts[103]++; /* 16bit pos esc */
                        else
                            counts[101]++; /* 8bit pos esc */
                    } else if (pix < LoMaxCoeff) {
                        if (pix < -255)
                            counts[104]++; /* 16bit neg esc */
                        else
                            counts[102]++; /* 8bit neg esc */
                    } else
                        counts[pix + 180]++; /* within table */
                    break;

                case RUN_CODE:  /* get length of zero run */
                    if (pix == 0 && rcnt < 0xFFFF) {
                        ++rcnt;
                        break;
                    }
                    /* limit rcnt to avoid EOF problem in bitio.c */
                    if (rcnt <= MaxZRun) {
                        counts[rcnt]++;  /* log zero run length */
                    } else if (rcnt <= 0xFF) {
                        counts[105]++;
                    } else if (rcnt <= 0xFFFF) {
                        counts[106]++; /* 16bit zrun esc */
                    } else {
                        throw new IllegalStateException("ERROR: countBlock : Zrun too long in count block.");
                    }

                    if (pix != 0) {
                        if (pix > MaxCoeff) { /* log current pix */
                            if (pix > 255) {
                                counts[103]++; /* 16bit pos esc */
                            } else {
                                counts[101]++; /* 8bit pos esc */
                            }
                        } else if (pix < LoMaxCoeff) {
                            if (pix < -255) {
                                counts[104]++; /* 16bit neg esc */
                            } else {
                                counts[102]++; /* 8bit neg esc */
                            }
                        } else {
                            counts[pix + 180]++; /* within table */
                        }
                        state = COEFF_CODE;
                    } else {
                        rcnt = 1;
                        state = RUN_CODE;
                    }
                    break;
            }
        }
        if (state == RUN_CODE) { /* log zero run length */
            if (rcnt <= MaxZRun) {
                counts[rcnt]++;
            } else if (rcnt <= 0xFF) {
                counts[105]++;
            } else if (rcnt <= 0xFFFF) {
                counts[106]++; /* 16bit zrun esc */
            } else {
                throw new IllegalStateException("ERROR: countBlock : Zrun too long in count block.");
            }
        }

        return counts;
    }

    /**
     * Routine to find number of codes of each size.
     *
     * @param adjust
     * @param codesize
     * @param maxHuffcounts
     * @return
     */
    private static int[] findNumHuffSizes(
            final Ref<Boolean> adjust,
            final int[] codesize,
            final int maxHuffcounts) {
        adjust.value = false;

        /* Allocate 2X desired number of bits due to possible codesize. */
        int[] bits = new int[2 * MAX_HUFFBITS];

        for (int i = 0; i < maxHuffcounts; i++) {
            if (codesize[i] != 0) {
                bits[codesize[i] - 1]++;
            }
            if (codesize[i] > MAX_HUFFBITS) {
                adjust.value = true;
            }
        }

        return bits;
    }

    /**
     * routine to sort the huffman code sizes
     *
     * @param codesize
     * @param maxHuffcounts
     * @return
     */
    private static int[] sortCodeSizes(final int[] codesize, final int maxHuffcounts) {
        /*defines order of huffman codelengths in relation to the code sizes*/
        int[] values = new int[maxHuffcounts + 1];

        int i2 = 0;
        for (int i = 1; i <= (MAX_HUFFBITS << 1); i++) {
            for (int i3 = 0; i3 < maxHuffcounts; i3++) {
                if (codesize[i3] == i) {
                    values[i2] = i3;
                    i2++;
                }
            }
        }
        return values;

    }

    /**
     * This routine defines the huffman code sizes for each difference category
     *
     * @param tempSize
     * @param huffbits
     * @param maxHuffcounts
     * @return
     */
    private static HuffCode[] buildHuffsizes(final Ref<Integer> tempSize, final int[] huffbits, final int maxHuffcounts) {
        int numberOfCodes = 1;     /*the number codes for a given code size*/

        /* table of huffman codes and sizes */
        HuffCode[] huffcodeTable = new HuffCode[maxHuffcounts + 1];
        for (int i = 0; i < huffcodeTable.length; i++) {
            huffcodeTable[i] = new HuffCode();
        }

        tempSize.value = 0;

        for (int code_size = 1; code_size <= MAX_HUFFBITS; code_size++) {
            while (numberOfCodes <= huffbits[code_size - 1]) {
                huffcodeTable[tempSize.value].size = code_size;
                tempSize.value++;
                numberOfCodes++;
            }
            numberOfCodes = 1;
        }
        huffcodeTable[tempSize.value].size = 0;
        return huffcodeTable;

    }

    /**
     * Routine to optimize code sizes by frequency of difference values.
     *
     * @param freq          should be array of length 1
     * @param maxHuffcounts
     * @return
     */
    private static int[] findHuffSizes(final int[] freq, final int maxHuffcounts) {
        Ref<Integer> value1Ref = new Ref<>();          /* smallest and next smallest frequency*/
        Ref<Integer> value2Ref = new Ref<>();          /* of difference occurrence in the largest difference category*/

        int value1;
        int value2;
        /*codesizes for each category*/
        int[] codesize = new int[maxHuffcounts + 1];

        /*pointer used to generate codesizes*/
        int[] others = new int[maxHuffcounts + 1];

        for (int i = 0; i <= maxHuffcounts; i++) {
            others[i] = -1;
        }

        while (true) {
            findLeastFreq(value1Ref, value2Ref, freq, maxHuffcounts);
            value1 = value1Ref.value;
            value2 = value2Ref.value;

            if (value2 == -1) {
                break;
            }

            freq[value1] += freq[value2];
            freq[value2] = 0;

            codesize[value1]++;
            while (others[value1] != -1) {
                value1 = others[value1];
                codesize[value1]++;
            }
            others[value1] = value2;
            codesize[value2]++;

            while (others[value2] != -1) {
                value2 = others[value2];
                codesize[value2]++;
            }
        }

        return codesize;
    }

    /**
     * Routine to find the largest difference with the least frequency value
     *
     * @param value1
     * @param value2
     * @param freq
     * @param maxHuffcounts
     * @return
     */
    private static void findLeastFreq(final Ref<Integer> value1, final Ref<Integer> value2, final int[] freq, final int maxHuffcounts) {
        int codeTemp;       /*store code*/
        int valueTemp;      /*store size*/
        int code2 = Integer.MAX_VALUE;   /*next smallest frequency in largest diff category*/
        int code1 = Integer.MAX_VALUE;   /*smallest frequency in largest difference category*/
        int set = 1;         /*flag first two non-zero frequency values*/

        int value1Temp = -1;
        int value2Temp = -1;

        for (int i = 0; i <= maxHuffcounts; i++) {
            if (freq[i] == 0) {
                continue;
            }
            if (set == 1) {
                code1 = freq[i];
                value1Temp = i;
                set++;
                continue;
            }
            if (set == 2) {
                code2 = freq[i];
                value2Temp = i;
                set++;
            }
            codeTemp = freq[i];
            valueTemp = i;
            if (code1 < codeTemp && code2 < codeTemp) {
                continue;
            }
            if ((codeTemp < code1) || (codeTemp == code1 && valueTemp > value1Temp)) {
                code2 = code1;
                value2Temp = value1Temp;
                code1 = codeTemp;
                value1Temp = valueTemp;
                continue;
            }
            if ((codeTemp < code2) || (codeTemp == code2 && valueTemp > value1Temp)) {
                code2 = codeTemp;
                value2Temp = valueTemp;
            }
        }
        value1.value = value1Temp;
        value2.value = value2Temp;
    }

    /**
     * routine to insure that no huffman code size is greater than 16
     *
     * @param bits
     */
    private static void sortHuffbits(final int[] bits) {
        int i, j;
        int l1, l2, l3;

        l3 = MAX_HUFFBITS << 1;     /* 32 */
        l1 = l3 - 1;                /* 31 */
        l2 = MAX_HUFFBITS - 1;      /* 15 */

        int[] tbits = new int[l3];

        for (i = 0; i < MAX_HUFFBITS << 1; i++) {
            tbits[i] = bits[i];
        }

        for (i = l1; i > l2; i--) {
            while (tbits[i] > 0) {
                j = i - 2;
                while (tbits[j] == 0) {
                    j--;
                }
                tbits[i] -= 2;
                tbits[i - 1] += 1;
                tbits[j + 1] += 2;
                tbits[j] -= 1;
            }
            tbits[i] = 0;
        }

        while (tbits[i] == 0) {
            i--;
        }

        tbits[i] -= 1;

        for (i = 0; i < MAX_HUFFBITS << 1; i++) {
            bits[i] = tbits[i];
        }

        for (i = MAX_HUFFBITS; i < l3; i++) {
            if (bits[i] > 0) {
                throw new IllegalStateException("ERROR : sortHuffbits : Code length of %d is greater than 16.");
            }
        }

    }

    /**
     * This routine defines the huffman codes needed for each difference category
     *
     * @param huffcodeTable
     */
    private static void buildHuffcodes(final HuffCode[] huffcodeTable) {
        int pointer = 0;/*pointer to code word information*/
        int tempCode = 0;/*used to construct code word*/

        int tempSize = huffcodeTable[0].size;/*used to construct code size*/

        do {
            do {
                huffcodeTable[pointer].code = tempCode;
                tempCode++;
                pointer++;
            } while (huffcodeTable[pointer].size == tempSize);

            if (huffcodeTable[pointer].size == 0) {
                return;
            }

            do {
                tempCode <<= 1;
                tempSize++;
            } while (huffcodeTable[pointer].size != tempSize);
        } while (huffcodeTable[pointer].size == tempSize);

    }

    /**
     * @param hufftable
     * @param lastSize
     */
    private static void checkHuffcodesWsq(final HuffCode[] hufftable, final int lastSize) {
        boolean allOnes;

        for (int i = 0; i < lastSize; i++) {
            allOnes = true;
            for (int k = 0; (k < hufftable[i].size) && allOnes; k++) {
                allOnes = (allOnes && (((hufftable[i].code >> k) & 0x0001) != 0));
            }
            if (allOnes) {
                throw new IllegalStateException("WARNING: A code in the hufftable contains an "
                        + "all 1's code. This image may still be "
                        + "decodable. It is not compliant with "
                        + "the WSQ specification.");
            }
        }

    }

    /**
     * routine to sort huffman codes and sizes
     *
     * @param inHuffcodeTable
     * @param lastSize
     * @param values
     * @param maxHuffcounts
     * @return
     */
    private static HuffCode[] buildHuffcodeTable(final HuffCode[] inHuffcodeTable,
                                                 final int lastSize, int[] values, final int maxHuffcounts) {
        HuffCode[] newHuffcodeTable = new HuffCode[maxHuffcounts + 1];
        for (int i = 0; i < newHuffcodeTable.length; i++) {
            newHuffcodeTable[i] = new HuffCode();
        }

        for (int size = 0; size < lastSize; size++) {
            newHuffcodeTable[values[size]].code = inHuffcodeTable[size].code;
            newHuffcodeTable[values[size]].size = inHuffcodeTable[size].size;
        }

        return newHuffcodeTable;
    }

    /**
     * Routine "codes" the quantized image using the huffman tables.
     *
     * @param dataOutput compressed output buffer
     * @param sip        quantized image
     * @param offset
     * @param length
     * @param MaxCoeff   Maximum values for coefficients
     * @param MaxZRun    Maximum zero runs
     * @param codes      huffman code table
     * @throws IOException
     */
    private static void compressBlock(final DataOutput dataOutput,
                                      final int[] sip,
                                      final int offset,
                                      final int length,
                                      final int MaxCoeff,  /*   */
                                      final int MaxZRun,
                                      final HuffCode[] codes) throws IOException {
        int LoMaxCoeff;        /* lower (negative) MaxCoeff limit */
        int pix;             /* temp pixel pointer */
        int rcnt = 0, state;  /* zero run count and if current pixel
                                         is in a zero run or just a coefficient */
        int cnt;               /* pixel counter */

        if (MaxCoeff < 0 || MaxCoeff > 0xffff) {
            throw new IllegalArgumentException("ERROR : compressBlock : MaxCoeff out of range.");
        }
        if (MaxZRun < 0 || MaxZRun > 0xffff) {
            throw new IllegalArgumentException("ERROR : compressBlock : MaxZRun out of range.");
        }
        LoMaxCoeff = 1 - MaxCoeff;

        Ref<Integer> outbit = new Ref<>(7);
        Ref<Integer> bytes = new Ref<>(0);
        Ref<Integer> bits = new Ref<>(0);

        state = COEFF_CODE;
        for (cnt = offset; cnt < length + offset; cnt++) {
            pix = sip[cnt];

            switch (state) {
                case COEFF_CODE:
                    if (pix == 0) {
                        state = RUN_CODE;
                        rcnt = 1;
                        break;
                    }
                    if (pix > MaxCoeff) {
                        if (pix > 255) {
                            /* 16bit pos esc */
                            writeBits(dataOutput, codes[103].size, codes[103].code, outbit, bits, bytes);
                            writeBits(dataOutput, 16, pix, outbit, bits, bytes);
                        } else {
                            /* 8bit pos esc */
                            writeBits(dataOutput, codes[101].size, codes[101].code, outbit, bits, bytes);
                            writeBits(dataOutput, 8, pix, outbit, bits, bytes);
                        }
                    } else if (pix < LoMaxCoeff) {
                        if (pix < -255) {
                            /* 16bit neg esc */
                            writeBits(dataOutput, codes[104].size, codes[104].code, outbit, bits, bytes);
                            writeBits(dataOutput, 16, -(pix), outbit, bits, bytes);
                        } else {
                            /* 8bit neg esc */
                            writeBits(dataOutput, codes[102].size, codes[102].code, outbit, bits, bytes);
                            writeBits(dataOutput, 8, -(pix), outbit, bits, bytes);
                        }
                    } else {
                        /* within table */
                        writeBits(dataOutput, codes[pix + 180].size, codes[pix + 180].code, outbit, bits, bytes);
                    }
                    break;

                case RUN_CODE:
                    if (pix == 0 && rcnt < 0xFFFF) {
                        ++rcnt;
                        break;
                    }
                    if (rcnt <= MaxZRun) {
                        /* log zero run length */
                        writeBits(dataOutput, codes[rcnt].size, codes[rcnt].code, outbit, bits, bytes);
                    } else if (rcnt <= 0xFF) {
                        /* 8bit zrun esc */
                        writeBits(dataOutput, codes[105].size, codes[105].code, outbit, bits, bytes);
                        writeBits(dataOutput, 8, rcnt, outbit, bits, bytes);
                    } else if (rcnt <= 0xFFFF) {
                        /* 16bit zrun esc */
                        writeBits(dataOutput, codes[106].size, codes[106].code, outbit, bits, bytes);
                        writeBits(dataOutput, 16, rcnt, outbit, bits, bytes);
                    } else {
                        throw new IllegalStateException("ERROR : compressBlock : zrun too large.");
                    }

                    if (pix != 0) {
                        if (pix > MaxCoeff) {
                            /* log current pix */
                            if (pix > 255) {
                                /* 16bit pos esc */
                                writeBits(dataOutput, codes[103].size, codes[103].code, outbit, bits, bytes);
                                writeBits(dataOutput, 16, pix, outbit, bits, bytes);
                            } else {
                                /* 8bit pos esc */
                                writeBits(dataOutput, codes[101].size, codes[101].code, outbit, bits, bytes);
                                writeBits(dataOutput, 8, pix, outbit, bits, bytes);
                            }
                        } else if (pix < LoMaxCoeff) {
                            if (pix < -255) {
                                /* 16bit neg esc */
                                writeBits(dataOutput, codes[104].size, codes[104].code, outbit, bits, bytes);
                                writeBits(dataOutput, 16, -pix, outbit, bits, bytes);
                            } else {
                                /* 8bit neg esc */
                                writeBits(dataOutput, codes[102].size, codes[102].code, outbit, bits, bytes);
                                writeBits(dataOutput, 8, -pix, outbit, bits, bytes);
                            }
                        } else {
                            /* within table */
                            writeBits(dataOutput, codes[pix + 180].size, codes[pix + 180].code, outbit, bits, bytes);
                        }
                        state = COEFF_CODE;
                    } else {
                        rcnt = 1;
                        state = RUN_CODE;
                    }
                    break;
            }
        }
        if (state == RUN_CODE) {
            if (rcnt <= MaxZRun) {
                writeBits(dataOutput, codes[rcnt].size, codes[rcnt].code, outbit, bits, bytes);
            } else if (rcnt <= 0xFF) {
                writeBits(dataOutput, codes[105].size, codes[105].code, outbit, bits, bytes);
                writeBits(dataOutput, 8, rcnt, outbit, bits, bytes);
            } else if (rcnt <= 0xFFFF) {
                writeBits(dataOutput, codes[106].size, codes[106].code, outbit, bits, bytes);
                writeBits(dataOutput, 16, rcnt, outbit, bits, bytes);
            } else {
                throw new IllegalStateException("ERROR : compressBlock : zrun2 too large.");
            }
        }

        flushBits(dataOutput, outbit, bits, bytes);
    }

    private static String fetToString(final Map<String, String> fet) throws UnsupportedEncodingException {
        try {
            StringBuilder result = new StringBuilder();

            for (Map.Entry<String, String> entry : fet.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                if (entry.getValue() == null) {
                    continue;
                }
                String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.toString());
                String value = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.toString());

                result.append(key);
                result.append(" ");
                result.append(value);
                result.append("\n");
            }

            return result.toString();
        } catch (UnsupportedEncodingException e) {
            throw e;
        }

    }

    /**
     * Routine to write "compressed" bits to output buffer.
     *
     * @param outbuf output data buffer
     * @param size   numbers bits of code to write into buffer
     * @param code   info to write into buffer
     * @param outbit current bit location in out buffer byte
     * @param bits   byte to write to output buffer
     * @param bytes  count of number bytes written to the buffer
     * @throws IOException
     */
    private static void writeBits(
            final DataOutput outbuf,
            final int size,
            final int code,
            final Ref<Integer> outbit,
            final Ref<Integer> bits,
            final Ref<Integer> bytes) throws IOException {
        int num;
        num = size;

        for (--num; num >= 0; num--) {
            int tempValue = bits.value;
            tempValue <<= 1;
            tempValue |= (((code >> num) & 0x0001)) & 0xFF;
            bits.value = tempValue;

            outbit.value--;
            if (outbit.value < 0) {
                outbuf.write(bits.value);
                if ((bits.value & 0xFF) == 0xFF) {
                    outbuf.write(0);
                    bytes.value++;
                }
                bytes.value++;
                outbit.value = 7;
                bits.value = 0;
            }
        }
    }

    /**
     * Routine to "flush" left over bits in last byte after compressing a block.
     *
     * @param outbuf output data buffer
     * @param outbit current bit location in out buffer byte
     * @param bits   byte to write to output buffer
     * @param bytes  count of number bytes written to the buffer
     * @throws IOException
     */
    private static void flushBits(
            final DataOutput outbuf,
            final Ref<Integer> outbit,
            final Ref<Integer> bits,
            final Ref<Integer> bytes) throws IOException {

        int cnt; /* temp counter */

        if (outbit.value != 7) {
            for (cnt = outbit.value; cnt >= 0; cnt--) {
                int tempValue = bits.value;
                tempValue <<= 1;
                tempValue |= 0x01;
                bits.value = tempValue;
            }

            outbuf.write(bits.value);
            if (bits.value == 0xFF) {
                bits.value = 0;
                outbuf.write(0);
                bytes.value++;
            }
            bytes.value++;
            outbit.value = 7;
            bits.value = 0;
        }

    }
}
