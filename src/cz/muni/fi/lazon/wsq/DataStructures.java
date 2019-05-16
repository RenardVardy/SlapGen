package cz.muni.fi.lazon.wsq;

import static cz.muni.fi.lazon.wsq.Constants.MAX_SUBBANDS;

class Bitmap {

    private int width;
    private int height;
    private int ppi;
    private byte[] pixels;

    Bitmap(byte[] pixels, int width, int height, int ppi) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
        this.ppi = ppi;
    }

    int getWidth() {
        return width;
    }

    int getHeight() {
        return height;
    }

    int getPpi() {
        return ppi;
    }

    byte[] getPixels() {
        return pixels;
    }

}

class HuffCode {
    public int size;
    public int code;
}

class Ref<T> {
    public T value;

    public Ref() {
        this(null);
    }

    public Ref(T value) {
        this.value = value;
    }
}

class WaveletTree {
    public int x;
    public int y;
    public int lenx;
    public int leny;
    public int invrw;
    public int invcl;
}

class QuantTree {
    public int x;    /* UL corner of block */
    public int y;
    public int lenx;  /* block size */
    public int leny;  /* block size */
}

class Quantization {
    public float r; /* compression bitrate */
    public float[] qbss = new float[MAX_SUBBANDS];
    public float[] qzbs = new float[MAX_SUBBANDS];
    public float[] var = new float[MAX_SUBBANDS];
}

/**
 * This class contains definitions of data structures and related methods.
 */
final class DataStructures {
    
    private DataStructures() {}

    /**
     * Builds WSQ decomposition trees.
     *
     * @param wTree
     * @param qTree
     * @param width
     * @param height
     */
    static void buildWSQTrees(final WaveletTree[] wTree,
                              final QuantTree[] qTree,
                              final int width,
                              final int height) {
        /* Build a W-TREE structure for the image. */
        buildWTree(wTree, width, height);
        /* Build a Q-TREE structure for the image. */
        buildQTree(wTree, qTree);
    }

    /**
     * Routine to obtain subband "x-y locations" for creating wavelets.
     *
     * @param wTree  wavelet tree structure
     * @param width  image width
     * @param height image height
     */
    static void buildWTree(final WaveletTree[] wTree, final int width, final int height) {
        final int lenx, lenx2, leny, leny2;  /* starting lengths of sections of the image being split into subbands */

        wTree[2].invrw = 1;
        wTree[4].invrw = 1;
        wTree[7].invrw = 1;
        wTree[9].invrw = 1;
        wTree[11].invrw = 1;
        wTree[13].invrw = 1;
        wTree[16].invrw = 1;
        wTree[18].invrw = 1;
        wTree[3].invcl = 1;
        wTree[5].invcl = 1;
        wTree[8].invcl = 1;
        wTree[9].invcl = 1;
        wTree[12].invcl = 1;
        wTree[13].invcl = 1;
        wTree[17].invcl = 1;
        wTree[18].invcl = 1;

        wTree4(wTree, 0, 1, width, height, 0, 0, 1);

        if ((wTree[1].lenx % 2) == 0) {
            lenx = wTree[1].lenx / 2;
            lenx2 = lenx;
        } else {
            lenx = (wTree[1].lenx + 1) / 2;
            lenx2 = lenx - 1;
        }

        if ((wTree[1].leny % 2) == 0) {
            leny = wTree[1].leny / 2;
            leny2 = leny;
        } else {
            leny = (wTree[1].leny + 1) / 2;
            leny2 = leny - 1;
        }

        wTree4(wTree, 4, 6, lenx2, leny, lenx, 0, 0);
        wTree4(wTree, 5, 10, lenx, leny2, 0, leny, 0);
        wTree4(wTree, 14, 15, lenx, leny, 0, 0, 0);

        wTree[19].x = 0;
        wTree[19].y = 0;
        if ((wTree[15].lenx % 2) == 0) {
            wTree[19].lenx = wTree[15].lenx / 2;
        } else {
            wTree[19].lenx = (wTree[15].lenx + 1) / 2;
        }
        if ((wTree[15].leny % 2) == 0) {
            wTree[19].leny = wTree[15].leny / 2;
        } else {
            wTree[19].leny = (wTree[15].leny + 1) / 2;
        }
    }

    /**
     * Gives location and size of subband splits for build_w_tree.
     *
     * @param wTree  wavelet tree structure
     * @param start1 w_tree locations to start calculating
     * @param start2 subband split locations and sizes
     * @param lenx   (temp) subband split location and sizes
     * @param leny
     * @param x
     * @param y
     * @param stop1  0 normal operation, 1 used to avoid marking
     */
    static void wTree4(final WaveletTree[] wTree,
                       final int start1,
                       final int start2,
                       final int lenx,
                       final int leny,
                       final int x,
                       final int y,
                       final int stop1) {
        final int evenx, eveny;   /* Check length of subband for even or odd */
        final int p1, p2;         /* w_tree locations for storing subband sizes and locations */

        p1 = start1;
        p2 = start2;

        evenx = lenx % 2;
        eveny = leny % 2;

        wTree[p1].x = x;
        wTree[p1].y = y;
        wTree[p1].lenx = lenx;
        wTree[p1].leny = leny;

        wTree[p2].x = x;
        wTree[p2 + 2].x = x;
        wTree[p2].y = y;
        wTree[p2 + 1].y = y;

        if (evenx == 0) {
            wTree[p2].lenx = lenx / 2;
            wTree[p2 + 1].lenx = wTree[p2].lenx;
        } else {
            if (p1 == 4) {
                wTree[p2].lenx = (lenx - 1) / 2;
                wTree[p2 + 1].lenx = wTree[p2].lenx + 1;
            } else {
                wTree[p2].lenx = (lenx + 1) / 2;
                wTree[p2 + 1].lenx = wTree[p2].lenx - 1;
            }
        }
        wTree[p2 + 1].x = wTree[p2].lenx + x;
        if (stop1 == 0) {
            wTree[p2 + 3].lenx = wTree[p2 + 1].lenx;
            wTree[p2 + 3].x = wTree[p2 + 1].x;
        }
        wTree[p2 + 2].lenx = wTree[p2].lenx;


        if (eveny == 0) {
            wTree[p2].leny = leny / 2;
            wTree[p2 + 2].leny = wTree[p2].leny;
        } else {
            if (p1 == 5) {
                wTree[p2].leny = (leny - 1) / 2;
                wTree[p2 + 2].leny = wTree[p2].leny + 1;
            } else {
                wTree[p2].leny = (leny + 1) / 2;
                wTree[p2 + 2].leny = wTree[p2].leny - 1;
            }
        }
        wTree[p2 + 2].y = wTree[p2].leny + y;
        if (stop1 == 0) {
            wTree[p2 + 3].leny = wTree[p2 + 2].leny;
            wTree[p2 + 3].y = wTree[p2 + 2].y;
        }
        wTree[p2 + 1].leny = wTree[p2].leny;
    }

    /**
     * @param wTree wavelet tree structure
     * @param qTree quantization tree structure
     */
    static void buildQTree(final WaveletTree[] wTree, final QuantTree[] qTree) {
        qtree16(qTree, 3, wTree[14].lenx, wTree[14].leny, wTree[14].x, wTree[14].y, 0, 0);
        qtree16(qTree, 19, wTree[4].lenx, wTree[4].leny, wTree[4].x, wTree[4].y, 0, 1);
        qtree16(qTree, 48, wTree[0].lenx, wTree[0].leny, wTree[0].x, wTree[0].y, 0, 0);
        qtree16(qTree, 35, wTree[5].lenx, wTree[5].leny, wTree[5].x, wTree[5].y, 1, 0);
        qtree4(qTree, 0, wTree[19].lenx, wTree[19].leny, wTree[19].x, wTree[19].y);
    }

    /**
     * @param qTree quantization tree structure
     * @param start q_tree location of first subband in the subband group being calculated
     * @param lenx  (temp) subband location and sizes
     * @param leny
     * @param x
     * @param y
     */
    static void qtree4(
            final QuantTree[] qTree,
            final int start,
            final int lenx,
            final int leny,
            final int x,
            final int y) {
        final int evenx, eveny;    /* Check length of subband for even or odd */
        final int p;               /* indicates subband information being stored */

        p = start;
        evenx = lenx % 2;
        eveny = leny % 2;

        qTree[p].x = x;
        qTree[p + 2].x = x;
        qTree[p].y = y;
        qTree[p + 1].y = y;
        if (evenx == 0) {
            qTree[p].lenx = lenx / 2;
            qTree[p + 1].lenx = qTree[p].lenx;
            qTree[p + 2].lenx = qTree[p].lenx;
            qTree[p + 3].lenx = qTree[p].lenx;
        } else {
            qTree[p].lenx = (lenx + 1) / 2;
            qTree[p + 1].lenx = qTree[p].lenx - 1;
            qTree[p + 2].lenx = qTree[p].lenx;
            qTree[p + 3].lenx = qTree[p + 1].lenx;
        }
        qTree[p + 1].x = x + qTree[p].lenx;
        qTree[p + 3].x = qTree[p + 1].x;
        if (eveny == 0) {
            qTree[p].leny = leny / 2;
            qTree[p + 1].leny = qTree[p].leny;
            qTree[p + 2].leny = qTree[p].leny;
            qTree[p + 3].leny = qTree[p].leny;
        } else {
            qTree[p].leny = (leny + 1) / 2;
            qTree[p + 1].leny = qTree[p].leny;
            qTree[p + 2].leny = qTree[p].leny - 1;
            qTree[p + 3].leny = qTree[p + 2].leny;
        }
        qTree[p + 2].y = y + qTree[p].leny;
        qTree[p + 3].y = qTree[p + 2].y;
    }

    /**
     * @param qTree quantization tree structure
     * @param start q_tree location of first subband in the subband group being calculated
     * @param lenx  (temp) subband location and sizes
     * @param leny
     * @param x
     * @param y
     * @param rw    spectral invert 1st row split
     * @param cl    spectral invert 1st col split
     */
    static void qtree16(final QuantTree[] qTree,
                        final int start,
                        final int lenx,
                        final int leny,
                        final int x,
                        final int y,
                        final int rw,
                        final int cl) {
        final int tempx, temp2x;   /* temporary x values */
        final int tempy, temp2y;   /* temporary y values */
        int evenx, eveny;    /* Check length of subband for even or odd */
        final int p;               /* indicates subband information being stored */

        p = start;
        evenx = lenx % 2;
        eveny = leny % 2;

        if (evenx == 0) {
            tempx = lenx / 2;
            temp2x = tempx;
        } else {
            if (cl != 0) {
                temp2x = (lenx + 1) / 2;
                tempx = temp2x - 1;
            } else {
                tempx = (lenx + 1) / 2;
                temp2x = tempx - 1;
            }
        }

        if (eveny == 0) {
            tempy = leny / 2;
            temp2y = tempy;
        } else {
            if (rw != 0) {
                temp2y = (leny + 1) / 2;
                tempy = temp2y - 1;
            } else {
                tempy = (leny + 1) / 2;
                temp2y = tempy - 1;
            }
        }

        evenx = tempx % 2;
        eveny = tempy % 2;

        qTree[p].x = x;
        qTree[p + 2].x = x;
        qTree[p].y = y;
        qTree[p + 1].y = y;
        if (evenx == 0) {
            qTree[p].lenx = tempx / 2;
            qTree[p + 1].lenx = qTree[p].lenx;
            qTree[p + 2].lenx = qTree[p].lenx;
            qTree[p + 3].lenx = qTree[p].lenx;
        } else {
            qTree[p].lenx = (tempx + 1) / 2;
            qTree[p + 1].lenx = qTree[p].lenx - 1;
            qTree[p + 2].lenx = qTree[p].lenx;
            qTree[p + 3].lenx = qTree[p + 1].lenx;
        }
        qTree[p + 1].x = x + qTree[p].lenx;
        qTree[p + 3].x = qTree[p + 1].x;
        if (eveny == 0) {
            qTree[p].leny = tempy / 2;
            qTree[p + 1].leny = qTree[p].leny;
            qTree[p + 2].leny = qTree[p].leny;
            qTree[p + 3].leny = qTree[p].leny;
        } else {
            qTree[p].leny = (tempy + 1) / 2;
            qTree[p + 1].leny = qTree[p].leny;
            qTree[p + 2].leny = qTree[p].leny - 1;
            qTree[p + 3].leny = qTree[p + 2].leny;
        }
        qTree[p + 2].y = y + qTree[p].leny;
        qTree[p + 3].y = qTree[p + 2].y;

        evenx = temp2x % 2;

        qTree[p + 4].x = x + tempx;
        qTree[p + 6].x = qTree[p + 4].x;
        qTree[p + 4].y = y;
        qTree[p + 5].y = y;
        qTree[p + 6].y = qTree[p + 2].y;
        qTree[p + 7].y = qTree[p + 2].y;
        qTree[p + 4].leny = qTree[p].leny;
        qTree[p + 5].leny = qTree[p].leny;
        qTree[p + 6].leny = qTree[p + 2].leny;
        qTree[p + 7].leny = qTree[p + 2].leny;
        if (evenx == 0) {
            qTree[p + 4].lenx = temp2x / 2;
            qTree[p + 5].lenx = qTree[p + 4].lenx;
            qTree[p + 6].lenx = qTree[p + 4].lenx;
            qTree[p + 7].lenx = qTree[p + 4].lenx;
        } else {
            qTree[p + 5].lenx = (temp2x + 1) / 2;
            qTree[p + 4].lenx = qTree[p + 5].lenx - 1;
            qTree[p + 6].lenx = qTree[p + 4].lenx;
            qTree[p + 7].lenx = qTree[p + 5].lenx;
        }
        qTree[p + 5].x = qTree[p + 4].x + qTree[p + 4].lenx;
        qTree[p + 7].x = qTree[p + 5].x;


        eveny = temp2y % 2;

        qTree[p + 8].x = x;
        qTree[p + 9].x = qTree[p + 1].x;
        qTree[p + 10].x = x;
        qTree[p + 11].x = qTree[p + 1].x;
        qTree[p + 8].y = y + tempy;
        qTree[p + 9].y = qTree[p + 8].y;
        qTree[p + 8].lenx = qTree[p].lenx;
        qTree[p + 9].lenx = qTree[p + 1].lenx;
        qTree[p + 10].lenx = qTree[p].lenx;
        qTree[p + 11].lenx = qTree[p + 1].lenx;
        if (eveny == 0) {
            qTree[p + 8].leny = temp2y / 2;
            qTree[p + 9].leny = qTree[p + 8].leny;
            qTree[p + 10].leny = qTree[p + 8].leny;
            qTree[p + 11].leny = qTree[p + 8].leny;
        } else {
            qTree[p + 10].leny = (temp2y + 1) / 2;
            qTree[p + 11].leny = qTree[p + 10].leny;
            qTree[p + 8].leny = qTree[p + 10].leny - 1;
            qTree[p + 9].leny = qTree[p + 8].leny;
        }
        qTree[p + 10].y = qTree[p + 8].y + qTree[p + 8].leny;
        qTree[p + 11].y = qTree[p + 10].y;


        qTree[p + 12].x = qTree[p + 4].x;
        qTree[p + 13].x = qTree[p + 5].x;
        qTree[p + 14].x = qTree[p + 4].x;
        qTree[p + 15].x = qTree[p + 5].x;
        qTree[p + 12].y = qTree[p + 8].y;
        qTree[p + 13].y = qTree[p + 8].y;
        qTree[p + 14].y = qTree[p + 10].y;
        qTree[p + 15].y = qTree[p + 10].y;
        qTree[p + 12].lenx = qTree[p + 4].lenx;
        qTree[p + 13].lenx = qTree[p + 5].lenx;
        qTree[p + 14].lenx = qTree[p + 4].lenx;
        qTree[p + 15].lenx = qTree[p + 5].lenx;
        qTree[p + 12].leny = qTree[p + 8].leny;
        qTree[p + 13].leny = qTree[p + 8].leny;
        qTree[p + 14].leny = qTree[p + 10].leny;
        qTree[p + 15].leny = qTree[p + 10].leny;
    }

}
