package com.performancetestgurus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.jnbis.Bitmap;
import org.jnbis.WSQEncoder;

/**
 * A bunch of utilities to support creation of slaps  
 * 
 * Reading and writing WSQ format uses:
 * <a href="https://github.com/E3V3A/JMRTD/tree/master/wsq_imageio">GitHub repository</a>
 * <a href="http://jmrtd.org/">JMRTD</a>
 *  
 * @author <a href="mailto:renard.vardy@performancetestgurus.com">Renard Vardy</a>
 * @version 0.1
 * @date April 25, 2019
 * Copyright (C) 2018 Renard Vardy, www.performancetestgururus.com/copyright
 * 
**/
public class Util {

	   /**
	   * Show Image - Displays an image on the screen - Used for reviewing slaps during development
	   * @param BufferedImage image Image to display
	   * 
	   */
	
	public static void showImage(BufferedImage image) {
		JFrame frame = new JFrame("Image " + image.getWidth() + " x " + image.getHeight());
		Container contentPane = frame.getContentPane();
		JPanel imgPanel = new JPanel(new BorderLayout());
		imgPanel.add(new JLabel(new ImageIcon(image)), BorderLayout.CENTER);
		contentPane.add(imgPanel);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
		frame.setVisible(true);
	}

	   /**
	   * Convert - converts a Bitmap image to BufferedImage
	   * @param Bitmap bitmap Bitmap image to be converted
	   * @return Grey Scale (TYPE_BYTE_GRAY) Buffered Image 
	   * 
	   */
	
	public static BufferedImage convert(Bitmap bitmap) {
		int width = bitmap.getWidth();
		int height = bitmap.getHeight();
		byte[] data = bitmap.getPixels();
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		WritableRaster raster = image.getRaster();
		raster.setDataElements(0, 0, width, height, data);
		return image;
	}	

	
	   /**
	   * Convert - converts a Bitmap image to BufferedImage
	   * 
	   * Note: WSQ images are always 500dpt, 8 bit
	   * 
	   * @param BufferedImage image - Grey Scale (TYPE_BYTE_GRAY) image to be converted to Bitmap
	   * @return Bitmap of buffered image
	   * 
	   */
	
	public static byte[] convert(BufferedImage image) {
		System.out.println(image.getColorModel());
        WritableRaster raster = image.getRaster();
        DataBufferByte buffer = (DataBufferByte) raster.getDataBuffer();
        byte[] databuffer = buffer.getData();
        Bitmap bitmap = new   Bitmap(databuffer, image.getWidth(),  image.getHeight(), 500, 8, 1); 
        
        System.out.println(bitmap.toString());
        ByteArrayOutputStream byteBuffer2 = new ByteArrayOutputStream();
        try {
			WSQEncoder.encode(byteBuffer2, bitmap, 0.75f, "");
		} catch (IOException e) {
			e.printStackTrace();
		}
     
        return byteBuffer2.toByteArray();
        
	}	
}
