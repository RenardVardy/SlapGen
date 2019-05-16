package cz.muni.fi.lazon.wsq;

import java.io.*;

/**
 * This implementation of WSQ encoder is based on NBIS NIST library
 * https://www.nist.gov/services-resources/software/nist-biometric-image-software-nbis
 *
 * @author sebastian.lazon@gmail.com
 */
public class Encoder {

    private final int width;
    private final int height;
    private final float quality;
    private final int ppi;
    private final boolean includeMetadata;
    private final String comment;

    private Encoder(Builder builder) {
        width = builder.width;
        height = builder.height;
        quality = builder.quality;
        ppi = builder.ppi;
        includeMetadata = builder.includeMetadata;
        comment = builder.comment;
    }

    public static class Builder {
        private final int width;
        private final int height;
        private float quality = 2.2f;
        private int ppi = 500;
        private boolean includeMetadata = true;
        private String comment = "";

        /**
         * @param width source image width
         * @param height source image height
         */
        public Builder(int width, int height) {
            if(width<=0) {
                throw new IllegalArgumentException("Width must be greater than 0");
            }
            if(height<=0) {
                throw new IllegalArgumentException("Height must be greater than 0");
            }
            this.width = width;
            this.height = height;
        }

        /**
         * @param quality image quality from interval <0.75,2.2>
         */
        public Builder quality(float quality) {
            if(quality>2.2 || quality<0.75) {
                throw new IllegalArgumentException("Quality must be greater in <0.75,2.2>");
            }
            this.quality = quality;
            return this;
        }

        /**
         * @param ppi image resolution (used only in wsq header for info)
         */
        public Builder ppi(int ppi) {
            if(ppi<=0) {
                throw new IllegalArgumentException("PPI must be greater than 0");
            }
            this.ppi = ppi;
            return this;
        }

        /**
         * @param includeMetadata if false, metadata containing encoder info are omitted
         */
        public Builder includeMetadata(boolean includeMetadata) {
            this.includeMetadata = includeMetadata;
            return this;
        }

        /**
         * @param comment custom information to be used as a part of metadata
         */
        public Builder comment(String comment) {
            this.comment = comment;
            return this;
        }

        public Encoder build() {
            return new Encoder(this);
        }
    }

    /**
     * Converts source image in 256-grayscale as byte array to wsq image as byte array.
     *
     * @param input     raw source image as byte array     
     * @return WSQ-encoded image as byte array
     * @throws IOException
     */
    public byte[] encode(final byte[] input) throws IOException {
        final Bitmap bitmap = new Bitmap(input, width, height, ppi);
        byte[] output;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dataos = new DataOutputStream(baos)) {
            EncoderImpl.encode(dataos, bitmap, quality, includeMetadata, comment);
            output = baos.toByteArray();
        }
        return output;
    }

    /**
     * Converts source image in 256-grayscale as input stream to wsq image as the output stream.
     *
     * @param input     raw source image as byte array     
     * @return WSQ-encoded image as output stream
     * @throws IOException
     */
    public void encode(InputStream input, OutputStream output) throws IOException {
        final Bitmap bitmap = new Bitmap(toByteArray(input), width, height, ppi);
        try (DataOutputStream dataos = new DataOutputStream(output)) {
            EncoderImpl.encode(dataos, bitmap, quality, includeMetadata, comment);
        }
    }

    private static byte[] toByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            byte[] b = new byte[4096];
            int n = 0;
            while ((n = is.read(b)) != -1) {
                output.write(b, 0, n);
            }
            return output.toByteArray();
        } finally {
            output.close();
        }
    }
}
