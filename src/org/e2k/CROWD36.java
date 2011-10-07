package org.e2k;

import javax.swing.JOptionPane;

public class CROWD36 extends MFSK {
	
	private int baudRate=40;
	private int state=0;
	private double samplesPerSymbol;
	private Rivet theApp;
	public long sampleCount=0;
	private long symbolCounter=0;
	private long energyStartPoint;
	private StringBuffer lineBuffer=new StringBuffer();
	private CircularDataBuffer energyBuffer=new CircularDataBuffer();
	private boolean figureShift=false; 
	private int lineCount=0;
	private int correctionValue=0;
	
	public CROWD36 (Rivet tapp,int baud)	{
		baudRate=baud;
		theApp=tapp;
	}
	
	public void setBaudRate(int baudRate) {
		this.baudRate = baudRate;
	}

	public int getBaudRate() {
		return baudRate;
	}

	public void setState(int state) {
		this.state = state;
	}

	public int getState() {
		return state;
	}
	
	public String[] decode (CircularDataBuffer circBuf,WaveData waveData)	{
		String outLines[]=new String[2];
		

		// Just starting
		if (state==0)	{
			// Check the sample rate
			if (waveData.getSampleRate()>11025)	{
				state=-1;
				JOptionPane.showMessageDialog(null,"WAV files containing\nCROWD36 recordings must have\nbeen recorded at a sample rate\nof 11.025 KHz or less.","Rivet", JOptionPane.INFORMATION_MESSAGE);
				return null;
			}
			// Check this is a mono recording
			if (waveData.getChannels()!=1)	{
				state=-1;
				JOptionPane.showMessageDialog(null,"Rivet can only process\nmono WAV files.","Rivet", JOptionPane.INFORMATION_MESSAGE);
				return null;
			}
			samplesPerSymbol=samplesPerSymbol(baudRate,waveData.getSampleRate());
			state=1;
			// sampleCount must start negative to account for the buffer gradually filling
			sampleCount=0-circBuf.retMax();
			symbolCounter=0;
			// Clear the energy buffer
			energyBuffer.setBufferCounter(0);
			// Clear the display side of things
			lineCount=0;
			lineBuffer.delete(0,lineBuffer.length());
			theApp.setStatusLabel("Known Tone Hunt");
			return null;
		}
		
		// Hunting for known tones
		if (state==1)	{
			outLines[0]=syncToneHunt(circBuf,waveData);
			if (outLines[0]!=null)	{
				state=2;
				energyStartPoint=sampleCount;
				energyBuffer.setBufferCounter(0);
				theApp.setStatusLabel("Calculating Symbol Timing");
			}
		}
		
		// Set the symbol timing
		if (state==2)	{
			final int lookAHEAD=1;
			// Obtain an average of the last few samples put through ABS
			double no=samplesPerSymbol/20.0;
			energyBuffer.addToCircBuffer(circBuf.getABSAverage(0,(int)no));
			// Gather a symbols worth of energy values
			if (energyBuffer.getBufferCounter()>(int)(samplesPerSymbol*lookAHEAD))	{
				// Now find the lowest energy value
				long perfectPoint=energyBuffer.returnLowestBin()+energyStartPoint+(int)samplesPerSymbol;
				// Calculate what the value of the symbol counter should be
				symbolCounter=(int)samplesPerSymbol-(perfectPoint-sampleCount);
				state=3;
				theApp.setStatusLabel("Symbol Timing Achieved");
				if (theApp.isSoundCardInput()==true) outLines[0]=theApp.getTimeStamp()+" Symbol timing found";
				else outLines[0]=theApp.getTimeStamp()+" Symbol timing found at position "+Long.toString(perfectPoint);
				sampleCount++;
				symbolCounter++;
				return outLines;
			}
		}
		
		// Decode traffic
		if (state==3)	{
			// Only do this at the start of each symbol
			if (symbolCounter>=samplesPerSymbol)	{
				symbolCounter=0;				
				int freq=crowd36Freq(circBuf,waveData,0);
				outLines=displayMessage(freq,waveData.isFromFile());
			}
		}
		sampleCount++;
		symbolCounter++;
		return outLines;				
	}
	
	private int crowd36Freq (CircularDataBuffer circBuf,WaveData waveData,int pos)	{
		
		// 8 KHz sampling
		if (waveData.getSampleRate()==8000.0)	{
			int freq=doCR36_8000FFT(circBuf,waveData,pos);
			freq=freq+correctionValue;
			return freq;
		}
		else if (waveData.getSampleRate()==11025.0)	{
			int freq=doCR36_11025FFT(circBuf,waveData,pos);
			freq=freq+correctionValue;
			return freq;
		}
		
		return -1;
	}
	
	private String[] displayMessage (int freq,boolean isFile)	{
		String outLines[]=new String[2];
		int tone=getTone(freq);
		String ch=getChar(tone);
		
		if (theApp.isDebug()==false)	{
			if (ch.equals("cr"))	{
				lineCount=50;
			}
			else 	{
				lineBuffer.append(ch);
				if (ch.length()>0) lineCount++;
			}	
			if (lineCount==50)	{
				outLines[0]=lineBuffer.toString();
				lineBuffer.delete(0,lineBuffer.length());
				lineCount=0;
				return outLines;
			}
			return null;
		}
		else	{
			outLines[0]=lineBuffer.toString();
			lineBuffer.delete(0,lineBuffer.length());
			lineCount=0;
			outLines[0]=freq+" Hz at "+Long.toString(sampleCount+(int)samplesPerSymbol)+" tone "+Long.toString(tone)+" "+ch;	
	        return outLines;
			
		}
	}
	
	private String getChar(int tone)	{
		String out="";
		final String C36A[]={"zero","unperf","Q","X","W","V","E","K"," ","B","R","J","ctl","G","T","F","","M","Y","C","cr","Z","U","L","*","D","I","H","ls","S","O","N","-","A","P","=",""};
		final String F36A[]={"zero","unperf","1","/","2",";","3","("," ","?","4","'","ctl","8","5","!","",".","6",":","cr","+","7",")","*","$","8","#","ls","bell","9",",","-","-","0","",""};
		
		if (tone==16) figureShift=true;
		else if (tone==28) figureShift=false;
		
		figureShift=false;
		
		try	{
			if ((tone<0)||(tone>36)) tone=35;
			if (figureShift==false) out=C36A[tone];
			else out=F36A[tone];
		}
		catch (Exception e)	{
			JOptionPane.showMessageDialog(null,e.toString(),"Rivet", JOptionPane.INFORMATION_MESSAGE);
			return "";
		}
		
		return out;
	}
	
	// Convert from a frequency to a tone number
	private int getTone (int freq)	{
		int a,index=-1,lowVal=999,dif;
		final int Tones[]={0,340,380,420,460,500,540,580,620,660,700,740,780,820,860,900,940,980,1020,1060,1100,1140,1180,1220,1260,1300,1340,1380,1420,1460,1500,1540,1580,1620,1660,1700,1740};
		for (a=0;a<Tones.length;a++)	{
			dif=Math.abs(Tones[a]-freq);
			if (dif<lowVal)	{
				lowVal=dif;
				index=a;
			}
		}
		return index;
	}
	
	
	// Hunt for known CROWD 36 tones
	private String syncToneHunt (CircularDataBuffer circBuf,WaveData waveData)	{
			// Get 4 symbols
			int freq1=crowd36Freq(circBuf,waveData,0);
			// Check this first tone isn't just noise
			if (getPercentageOfTotal()<5.0) return null;
			int freq2=crowd36Freq(circBuf,waveData,(int)samplesPerSymbol*1);
			// Check we have a high low
			if (freq2>freq1) return null;
			int freq3=crowd36Freq(circBuf,waveData,(int)samplesPerSymbol*2);
			// Don't waste time carrying on if freq1 isn't the same as freq3
			if (freq1!=freq3) return null;
			int freq4=crowd36Freq(circBuf,waveData,(int)samplesPerSymbol*3);
			// Check 2 of the symbol frequencies are different
			if ((freq1!=freq3)||(freq2!=freq4)) return null;
			// Check that 2 of the symbol frequencies are the same
			if ((freq1==freq2)||(freq3==freq4)) return null;
			// Calculate the difference between the sync tones
			int difference=freq1-freq2;
			// was 1700
			correctionValue=1700-freq1;
			String line;
			if (theApp.isSoundCardInput()==true)  line=theApp.getTimeStamp()+" CROWD36 Sync Tones Found (Correcting by "+Integer.toString(correctionValue)+" Hz) sync tone difference "+Integer.toString(difference)+" Hz";
			else line=theApp.getTimeStamp()+" CROWD36 Sync Tones Found (Correcting by "+Integer.toString(correctionValue)+" Hz) at "+Long.toString(sampleCount)+" sync tone difference "+Integer.toString(difference)+" Hz";
			return line;
		}
	
	public int getLineCount()	{
		return this.lineCount;
	}
	
	public String getLineBuffer ()	{
		return this.lineBuffer.toString();
	}
	


}
