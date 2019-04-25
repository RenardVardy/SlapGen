package com.performancetestgurus;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.jnbis.WSQDecoder;

/**
 * Java API for creating Slaps from individual fingerprint prints files 
 * 
 * Input: wsq (Wavelet Scalar Quantization) in byte array 
 * Output: wsq in byte array 
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

public class SlapGen {

	   /**
	   * rightSlap - Generates the right slap (Finger 13) based on a set of WSQ byte arrays 
	   * 
	   * To remove a finger - set finger value to null
	   * 
	   * @param byte[] finger2 - finger 2 (Right Index) in wsq format as byte array - use null to remove finger from slap 
	   * @param byte[] finger3 - finger 3 (Right Middle) in wsq format as byte array - use null to remove finger from slap 
	   * @param byte[] finger4 - finger 4 (Right Ring) in wsq format as byte array - use null to remove finger from slap 
	   * @param byte[] finger5 - finger 5 (Right Little) wsq format as byte array - use null to remove finger from slap 
	   * @return byte[] finger 13 (right slap) in wsq format as byte array
	   * 
	   **/
	
	public static byte[] rightSlap(
			byte[] finger2,
			byte[] finger3,
			byte[] finger4, 
			byte[] finger5
			)  {

		BufferedImage target = new BufferedImage(1600, 1500, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g = (Graphics2D) target.getGraphics();	
		g.setBackground(Color.WHITE);
		g.clearRect(0, 0, target.getWidth(), target.getHeight());

		placeFingerOnImage(g, finger2, 70, 617);
		placeFingerOnImage(g, finger3, 452, 339);
		placeFingerOnImage(g, finger4, 870, 409);
		placeFingerOnImage(g, finger5, 1287, 861);
		
		//Util.showImage(target);  //I used this to view the generated slaps during development
		return Util.convert(target);
	}
	
	   /**
	   * rightSlap - Generates the right slap (Finger 13) based on a set of WSQ byte arrays 
	   * 
	   * To remove a finger - set finger value to null
	   * 
	   * @param byte[] finger7 - finger 7 (Left Index) in wsq format as byte array - use null to remove finger from slap 
	   * @param byte[] finger6 - finger 8 (Left Middle) in wsq format as byte array - use null to remove finger from slap 
	   * @param byte[] finger9 - finger 9 (Left Ring) in wsq format as byte array - use null to remove finger from slap 
	   * @param byte[] finger10 - finger 10 (Left Little) in wsq format as byte array - use null to remove finger from slap 
	   * @return byte[] finger 14 (left slap) in wsq format as byte array
	   * 
	   **/
	
	public static byte[] leftSlap(
			byte[] finger7,
			byte[] finger8,
			byte[] finger9, 
			byte[] finger10
			)  {

		BufferedImage target = new BufferedImage(1600, 1500, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g = (Graphics2D) target.getGraphics();	
		g.setBackground(Color.WHITE);
		g.clearRect(0, 0, target.getWidth(), target.getHeight());
		
		placeFingerOnImage(g, finger7,1287, 617);
		placeFingerOnImage(g, finger8,870, 339);
		placeFingerOnImage(g, finger9,452, 409);
		placeFingerOnImage(g, finger10,70, 861);

		//Util.showImage(target);  //I used this to view the generated slaps during development
		return Util.convert(target);
	}	

	
	   /**
	   * ThumbSlap - Generates the thumb slap (Finger 15) based on a set of WSQ byte arrays 
	   * 
	   * To remove a finger - set finger value to null
	   * 
	   * @param byte[] finger1 - finger 1 (Right Thumb) as wsq format as byte array - use null to remove finger from slap 
	   * @param byte[] finger6 - finger 6 (Left Thumb) as wsq format as byte array - use null to remove finger from slap 
	   * @return byte[] thumb slap (finger 15) in wsq format as byte array
	   * 
	   **/
	
	public static byte[] thumbSlap(
			byte[] finger1,
			byte[] finger6
			)  {

		BufferedImage target = 	new BufferedImage(1600, 1500, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g = (Graphics2D) target.getGraphics();	
		g.setBackground(Color.WHITE);
		g.clearRect(0, 0, target.getWidth(), target.getHeight());

		placeFingerOnImage(g, finger6, 400, 400);
		placeFingerOnImage(g, finger1, 940, 400);
		
		//Util.showImage(target);  //I used this to view the generated slaps during development
		return Util.convert(target);
	}	
	
	private static void placeFingerOnImage(Graphics2D g, byte [] finger, int x, int y) {
		if(finger != null) {
		try {
			BufferedImage image = Util.convert(WSQDecoder.decode(new ByteArrayInputStream(finger)));
			g.drawImage(cropFinger(image, 330, 440), x, y, null);	//I have set the maximum finger size to 330x440
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private static BufferedImage cropFinger(BufferedImage finger, int x, int y) {
 		return finger.getSubimage(finger.getWidth() > x ? finger.getWidth()/2 - x/2 : 0 , 
 				finger.getHeight() > y ? finger.getHeight()/2 - y/2 : 0, 
 				finger.getWidth() > x ? x/2 + finger.getWidth()/2 : finger.getWidth(), 
 				finger.getHeight() > y ? y/2 + finger.getHeight()/2 : finger.getHeight());
    }
}  
