package com.performancetestgurus;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import java.util.Base64;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;

/**
 * Simple UI for creating slaps (Right hand slap, left hand slap and thumb slaps) 
 * from individual fingerprint files.
 * 
 * Input file formats: wsq (Wavelet Scalar Quantization) and txt (base64 encoded wsq)
 * Output file formats: wsq (Wavelet Scalar Quantization) and txt (Base64 encoded wsq)
 * For missing fingers - leave the text box blank
 * <a href="https://en.wikipedia.org/wiki/Wavelet_scalar_quantization">Wavelet Scalar Quantization</a>
 * 
 * Reading and writing WSQ format uses:
 * <a href="https://github.com/E3V3A/JMRTD/tree/master/wsq_imageio">GitHub repository</a>
 * <a href="http://jmrtd.org/">JMRTD</a>
 * 
 * 
 * @author <a href="mailto:renard.vardy@performancetestgurus.com">Renard Vardy</a>
 * @version 0.1
 * @date April 25, 2019
 * Copyright (C) 2019 Renard Vardy, www.performancetestgururus.com/copyright
 * 
**/



public class App implements ActionListener
{
    private JTextField fingerTextField01 = new JTextField(100);
    private JTextField fingerTextField02 = new JTextField(100);
    private JTextField fingerTextField03 = new JTextField(100);
    private JTextField fingerTextField04 = new JTextField(100);
    private JTextField fingerTextField05 = new JTextField(100);
    private JTextField fingerTextField06 = new JTextField(100);
    private JTextField fingerTextField07 = new JTextField(100);
    private JTextField fingerTextField08 = new JTextField(100);
    private JTextField fingerTextField09 = new JTextField(100);
    private JTextField fingerTextField10 = new JTextField(100);
    private JTextField fingerTextField13 = new JTextField(100);
    private JTextField fingerTextField14 = new JTextField(100);	    
    private JTextField fingerTextField15 = new JTextField(100);

    private JButton getFinger01Button = new JButton("Get Right Thumb");
    private JButton getFinger02Button = new JButton("Get Right Index");
    private JButton getFinger03Button = new JButton("Get Right Middle");
    private JButton getFinger04Button = new JButton("Get Right Ring");
    private JButton getFinger05Button = new JButton("Get Right Little");
    private JButton getFinger06Button = new JButton("Get Left Thumb");
    private JButton getFinger07Button = new JButton("Get Left Index");
    private JButton getFinger08Button = new JButton("Get Left Middle");
    private JButton getFinger09Button = new JButton("Get Left Ring");
    private JButton getFinger10Button = new JButton("Get Left Little");
    
    private JButton generateSlapsButton = new JButton("Create Slap");
    
    private File currentDirectory;
	
    public static void main( String[] args )
    {
    	App app = new App();
    	app.run();
    }
    
    public App() {};
    
   public void run() {
	   
	    this.currentDirectory = FileSystemView.getFileSystemView().getHomeDirectory();
	    
	    JLabel fingerLabel01 = new JLabel("Finger01 - Right Thumb");
	    JLabel fingerLabel02 = new JLabel("Finger02 - Right Index");
	    JLabel fingerLabel03 = new JLabel("Finger03 - Right Middle");
	    JLabel fingerLabel04 = new JLabel("Finger04 - Right Ring");
	    JLabel fingerLabel05 = new JLabel("Finger05 - Right Little");
	    JLabel fingerLabel06 = new JLabel("Finger06 - Left Thumb");
	    JLabel fingerLabel07 = new JLabel("Finger07 - Left Index");
	    JLabel fingerLabel08 = new JLabel("Finger08 - Left Middle");
	    JLabel fingerLabel09 = new JLabel("Finger09 - Left Ring");
	    JLabel fingerLabel10 = new JLabel("Finger10 - Left Little");
	    JLabel fingerLabel13 = new JLabel("Finger13 - Right Slap");
	    JLabel fingerLabel14 = new JLabel("Finger14 - Left Slap");
	    JLabel fingerLabel15 = new JLabel("Finger15 - Thumb Slap");
	    JLabel fingerHeading = new JLabel("***** Input Fingerprints *****");
	    JLabel slapsHeading = new JLabel("***** Output Slaps *****");
	    
	    JPanel fingerPanel01 = new JPanel();
	    JPanel fingerPanel02 = new JPanel();
	    JPanel fingerPanel03 = new JPanel();
	    JPanel fingerPanel04 = new JPanel();
	    JPanel fingerPanel05 = new JPanel();
	    JPanel fingerPanel06 = new JPanel();
	    JPanel fingerPanel07 = new JPanel();
	    JPanel fingerPanel08 = new JPanel();
	    JPanel fingerPanel09 = new JPanel();
	    JPanel fingerPanel10 = new JPanel();
	    JPanel fingerPanel13 = new JPanel();
	    JPanel fingerPanel14 = new JPanel();
	    JPanel fingerPanel15 = new JPanel();
	    JPanel fingerHeadingPanel = new JPanel();    
	    JPanel slapsHeadingPanel = new JPanel();    
	    
	    JPanel fingerPanel = new JPanel();
	    fingerPanel.setLayout(new BoxLayout(fingerPanel, BoxLayout.Y_AXIS));
	   
	    
	    
	    fingerPanel01.setLayout(new FlowLayout());
	    fingerPanel01.add(fingerLabel01);
	    fingerPanel01.add(fingerTextField01);
	    fingerPanel01.add(getFinger01Button);
	    
	    fingerPanel02.setLayout(new FlowLayout());	    
	    fingerPanel02.add(fingerLabel02);
	    fingerPanel02.add(fingerTextField02);
	    fingerPanel02.add(getFinger02Button);
	    
	    fingerPanel03.setLayout(new FlowLayout());
	    fingerPanel03.add(fingerLabel03);
	    fingerPanel03.add(fingerTextField03);
	    fingerPanel03.add(getFinger03Button);
	    
	    fingerPanel04.setLayout(new FlowLayout());
	    fingerPanel04.add(fingerLabel04);
	    fingerPanel04.add(fingerTextField04);
	    fingerPanel04.add(getFinger04Button);
	    
	    fingerPanel05.setLayout(new FlowLayout());
	    fingerPanel05.add(fingerLabel05);
	    fingerPanel05.add(fingerTextField05);
	    fingerPanel05.add(getFinger05Button);
	    	    
	    fingerPanel06.setLayout(new FlowLayout());
	    fingerPanel06.add(fingerLabel06);
	    fingerPanel06.add(fingerTextField06);
	    fingerPanel06.add(getFinger06Button);
	    
	    fingerPanel07.setLayout(new FlowLayout());
	    fingerPanel07.add(fingerLabel07);
	    fingerPanel07.add(fingerTextField07);
	    fingerPanel07.add(getFinger07Button);
	    
	    fingerPanel08.setLayout(new FlowLayout());
	    fingerPanel08.add(fingerLabel08);
	    fingerPanel08.add(fingerTextField08);
	    fingerPanel08.add(getFinger08Button);
	    
	    fingerPanel09.setLayout(new FlowLayout());
	    fingerPanel09.add(fingerLabel09);
	    fingerPanel09.add(fingerTextField09);
	    fingerPanel09.add(getFinger09Button);
	    
	    fingerPanel10.setLayout(new FlowLayout());
	    fingerPanel10.add(fingerLabel10);
	    fingerPanel10.add(fingerTextField10);
	    fingerPanel10.add(getFinger10Button);
	    
	    fingerPanel13.setLayout(new FlowLayout());
	    fingerPanel13.add(fingerLabel13);
	    fingerPanel13.add(fingerTextField13);
	    
	    fingerPanel14.setLayout(new FlowLayout());
	    fingerPanel14.add(fingerLabel14);
	    fingerPanel14.add(fingerTextField14);
	    
	    fingerPanel15.setLayout(new FlowLayout());
	    fingerPanel15.add(fingerLabel15);
	    fingerPanel15.add(fingerTextField15);
	    
	    
	    fingerHeadingPanel.setLayout(new FlowLayout());
	    fingerHeadingPanel.add(fingerHeading);
	    
	    slapsHeadingPanel.setLayout(new FlowLayout());
	    slapsHeadingPanel.add(slapsHeading);
	    
        JFrame frame = new JFrame("SlapCreator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800,600);

                

        this.generateSlapsButton.addActionListener(this);
        this.getFinger01Button.addActionListener(this);
        this.getFinger02Button.addActionListener(this);
        this.getFinger03Button.addActionListener(this);
        this.getFinger04Button.addActionListener(this);
        this.getFinger05Button.addActionListener(this);
        this.getFinger06Button.addActionListener(this);
        this.getFinger07Button.addActionListener(this);
        this.getFinger08Button.addActionListener(this);
        this.getFinger09Button.addActionListener(this);
        this.getFinger10Button.addActionListener(this);       
        
        fingerPanel.add(fingerHeadingPanel);        
        fingerPanel.add(fingerPanel01);
        fingerPanel.add(fingerPanel02);
        fingerPanel.add(fingerPanel03);
        fingerPanel.add(fingerPanel04);
        fingerPanel.add(fingerPanel05);
        fingerPanel.add(fingerPanel06);
        fingerPanel.add(fingerPanel07);
        fingerPanel.add(fingerPanel08);
        fingerPanel.add(fingerPanel09);
        fingerPanel.add(fingerPanel10);

        fingerPanel.add(slapsHeadingPanel);
        fingerPanel.add(fingerPanel13);
        fingerPanel.add(fingerPanel14);
        fingerPanel.add(fingerPanel15);
        
        

        frame.add(fingerPanel,  BorderLayout.CENTER);
    
        frame.getContentPane().add(generateSlapsButton, BorderLayout.PAGE_END);
        frame.pack();
        frame.setVisible(true);   
    }
    
    
    public void actionPerformed(ActionEvent e) {
    	
    	if (e.getSource() == this.generateSlapsButton) {
	    	processSlaps();
	    	return;
    	}
    	
    	if (e.getSource() == this.getFinger01Button) {
    		getFileName(this.fingerTextField01);
    		return;
    	}    	
    
    	if (e.getSource() == this.getFinger02Button) {
    		getFileName(this.fingerTextField02);
    		return;
    	}
    	
    	if (e.getSource() == this.getFinger03Button) {
    		getFileName(this.fingerTextField03);
    		return;
    	}
    	
    	if (e.getSource() == this.getFinger04Button) {
    		getFileName(this.fingerTextField04);
    		return;
    	}
    	
    	if (e.getSource() == this.getFinger05Button) {
    		getFileName(this.fingerTextField05);
    		return;
    	}
    	
    	if (e.getSource() == this.getFinger06Button) {
    		getFileName(this.fingerTextField06);
    		return;
    	}
    	
    	if (e.getSource() == this.getFinger07Button) {
    		getFileName(this.fingerTextField07);
    		return;
    	}
    	
    	if (e.getSource() == this.getFinger08Button) {
    		getFileName(this.fingerTextField08);
    		return;
    	}
    	
    	if (e.getSource() == this.getFinger09Button) {
    		getFileName(this.fingerTextField09);
    		return;
    	}
    	
    	if (e.getSource() == this.getFinger10Button) {
    		getFileName(this.fingerTextField10);
    		return;
    	}
  	
    }
    
    private byte[] getBytesFromFile(String fileName){
    	if(fileName.trim().compareTo("")==0) {
        	System.out.println("File Name Null : " + fileName);
    		return null;
    	}
    	if(fileName.trim().length() > 4 && 
    			fileName.substring(fileName.length() - 3 ).toUpperCase().compareTo("TXT")==0) {
	    	try {
	    		String contents = new String(Files.readAllBytes(Paths.get(fileName.trim()))); 
	    		return Base64.getDecoder().decode(contents);
	    	}catch(Exception e) {
	    		e.printStackTrace();
	    		return null;
	    	}
    	}
    	if(fileName.trim().length() > 4 && 
    			fileName.substring(fileName.length() - 3 ).toUpperCase().compareTo("WSQ")==0) {
	    	try {
	    		return Files.readAllBytes(Paths.get(fileName.trim()));
	    	}catch(Exception e) {
	    		e.printStackTrace();
	    		return null;
	    	}
    	}
    	System.out.println("File Type not supported : " + fileName);
    	return null;
    	}

    
    private void writeSlapToFile(String fileName, byte[] slap) {
    	if(fileName.trim().compareTo("")==0) {
    		System.out.println("Unable to write slap - no file name!!");
    	}
    	
    	if(fileName.trim().length() > 4 && 
    			fileName.substring(fileName.length() - 3 ).toUpperCase().compareTo("TXT")==0) {
	    	try {
	    		String contents = Base64.getEncoder().encodeToString(slap);
	    		Files.write( Paths.get(fileName.trim()), contents.getBytes(), StandardOpenOption.CREATE);
	    		return;
	    	}catch(Exception e) {
	    		e.printStackTrace();
	    		return;
	    		}
    		}
	       	if(fileName.trim().length() > 4 && 
	    			fileName.substring(fileName.length() - 3 ).toUpperCase().compareTo("WSQ")==0) {
		    	try {
		    		Files.write( Paths.get(fileName.trim()), slap, StandardOpenOption.CREATE);
		    		return;
		    	}catch(Exception e) {
		    		e.printStackTrace();
		    		return;
		    	}
	    	}
	    	System.out.println( "**** Unknown filetype - Please use TXT/WSQ : " + fileName );
	    	}
    
    private void processSlaps() {
 	   byte[] finger01 = getBytesFromFile(fingerTextField01.getText());
 	   byte[] finger02 = getBytesFromFile(fingerTextField02.getText());
 	   byte[] finger03 = getBytesFromFile(fingerTextField03.getText());
 	   byte[] finger04 = getBytesFromFile(fingerTextField04.getText());
 	   byte[] finger05 = getBytesFromFile(fingerTextField05.getText());
 	   byte[] finger06 = getBytesFromFile(fingerTextField06.getText());
 	   byte[] finger07 = getBytesFromFile(fingerTextField07.getText());
 	   byte[] finger08 = getBytesFromFile(fingerTextField08.getText());
 	   byte[] finger09 = getBytesFromFile(fingerTextField09.getText());
 	   byte[] finger10 = getBytesFromFile(fingerTextField10.getText());
 	   byte[] slap13 = SlapGen.rightSlap(finger02, finger03, finger04, finger05);
 	   byte[] slap14 = SlapGen.leftSlap(finger07, finger08, finger09, finger10);
 	   byte[] slap15 = SlapGen.thumbSlap(finger01, finger06);
 	   
 	   writeSlapToFile(fingerTextField13.getText(), slap13);
 	   writeSlapToFile(fingerTextField14.getText(), slap14);
 	   writeSlapToFile(fingerTextField15.getText(), slap15);
    }
    
   private void getFileName(JTextField fingerTextField) {
	   
	    JFileChooser jfc;
	    if(fingerTextField.getText().compareTo("")==0) {
	    	jfc = new JFileChooser(this.currentDirectory);
	    }else {
	    	jfc = new JFileChooser(new File(fingerTextField.getText()));
	    }
	    jfc.setDialogTitle("Select FingerPrint");
	    FileNameExtensionFilter filter = new FileNameExtensionFilter("Fingerprint Files", "wsq", "txt");
	    jfc.addChoosableFileFilter(filter);
	    int returnValue = jfc.showOpenDialog(null);
		if (returnValue == JFileChooser.APPROVE_OPTION) {
			fingerTextField.setText(jfc.getSelectedFile().getPath());
			this.currentDirectory = jfc.getSelectedFile();
		}
	    
   }
   }   



