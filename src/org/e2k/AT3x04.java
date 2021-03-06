package org.e2k;

// AT-3004D & AT-3014
// has 12 * 120Bd BPSK or QPSK modulated carriers *
// these carriers are 200 Hz apart with a pilot tone 400 Hz higher than the last carrier
//
// * See http://signals.radioscanner.ru/base/signal37/ for further information

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;

public class AT3x04 extends OFDM {
	
	private int state=0;
	private Rivet theApp;
	private long sampleCount=0;
	private long symbolCounter=0;
	private double samplesPerSymbol;
	private int carrierBinNos[][][]=new int[12][20][2];
	private double totalCarriersEnergy;
	
	private double mag[]=new double[3];
	
	private final int TIMINGBUFFERSIZE=3;
	private int timingBufferCounter=0;
	private double timingBuffer[]=new double[TIMINGBUFFERSIZE];
	
	double realV[]=new double[12];
	double imagV[]=new double[12];
	
	List<CarrierInfo> startCarrierList1=new ArrayList<CarrierInfo>();
	List<CarrierInfo> startCarrierList2=new ArrayList<CarrierInfo>();
	List<CarrierInfo> startCarrierList3=new ArrayList<CarrierInfo>();
	private int startCarrierCounter=0;
	private int pilotToneBin=0;
	
	public AT3x04 (Rivet tapp)	{
		theApp=tapp;
	}

	public int getState() {
		return state;
	}
	
	// Set the state and change the contents of the status label
	public void setState(int state) {
		this.state=state;
		if (state==0) theApp.setStatusLabel("Setup");
		else if (state==1) theApp.setStatusLabel("Signal Hunt");
		else if (state==2) theApp.setStatusLabel("Msg Hunt");
	}
	
	
	// The main decode routine
	public boolean decode (CircularDataBuffer circBuf,WaveData waveData)	{
		// Initial startup
		if (state==0)	{
			// Check the sample rate
			if (waveData.getSampleRate()!=8000.0)	{
				state=-1;
				JOptionPane.showMessageDialog(null,"WAV files containing\nAT3x04 recordings must have\nbeen recorded at a sample rate\nof 8 KHz.","Rivet", JOptionPane.INFORMATION_MESSAGE);
				return false;
			}
			// Check this is a mono recording
			if (waveData.getChannels()!=1)	{
				state=-1;
				JOptionPane.showMessageDialog(null,"Rivet can only process\nmono WAV files.","Rivet", JOptionPane.INFORMATION_MESSAGE);
				return false;
			}
			// Check this is a 16 bit WAV file
			if (waveData.getSampleSizeInBits()!=16)	{
				state=-1;
				JOptionPane.showMessageDialog(null,"Rivet can only process\n16 bit WAV files.","Rivet", JOptionPane.INFORMATION_MESSAGE);
				return false;
			}
			// sampleCount must start negative to account for the buffer gradually filling
			sampleCount=0-circBuf.retMax();
			symbolCounter=0;
			samplesPerSymbol=samplesPerSymbol(120.0,waveData.getSampleRate());
			startCarrierCounter=0;
			// Add a user warning that AT3x04 doesn't yet decode
			//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			theApp.writeLine("Please note that this mode is experimental and doesn't work yet !",Color.RED,theApp.italicFont);
			//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			setState(1);
			return true;
		}
		// Look for the 12 carriers from this mode
		else if (state==1)	{
			sampleCount++;
			if (sampleCount<0) return true;
			// Only run this check every 100 samples as this is rather maths intensive
			if (sampleCount%100==0)	{
				double spr[]=doRDFTFFTSpectrum(circBuf,waveData,0,true,800,true);
				// Collect three lots of carrier info lists searching between 2700 Hz and 3500 Hz
			    if (startCarrierCounter==0)	{
			    	startCarrierList1=findOFDMCarriersWithinRange(spr,waveData.getSampleRate(),RDFT_FFT_SIZE,0.8,270,350);
			    	startCarrierCounter++;
			    }
			    else if (startCarrierCounter==1)	{
			    	startCarrierList2=findOFDMCarriersWithinRange(spr,waveData.getSampleRate(),RDFT_FFT_SIZE,0.8,270,350);
			    	startCarrierCounter++;
			    }
			    else if (startCarrierCounter==2)	{
			    	startCarrierList3=findOFDMCarriersWithinRange(spr,waveData.getSampleRate(),RDFT_FFT_SIZE,0.8,270,350);
			    	startCarrierCounter++;
			    }    
			    else if (startCarrierCounter==3)	{
			    	// Look for the AT3x04 pilot tone
			    	if (AT3x04PilotToneHunt(spr)==true)	{
			    		// Get a full list of carriers present
			    		List<CarrierInfo> clist=findOFDMCarriers(spr,waveData.getSampleRate(),RDFT_FFT_SIZE,0.8);
			    		// Check this list of carriers looks like a AT3x04 waveform
			    		if (AT3x04CarrierConfirm(clist)==true)	{
			    			setState(2);
			    			sampleCount=0;
			    			// Calculate the carrier tone bins
			    			populateCarrierTonesBins();
			    			// Tell the user
			    			StringBuilder sb=new StringBuilder();
			    			double toneFreq=pilotToneBin*10;
					    	sb.append(theApp.getTimeStamp()+" AT3x04 Pilot Tone found at "+Double.toString(toneFreq)+" Hz");
					    	toneFreq=toneFreq-400;
					    	sb.append(" , Carrier 12 at "+Double.toString(toneFreq)+" Hz");
					    	toneFreq=toneFreq-2200;
					    	sb.append(" + Carrier 1 at "+Double.toString(toneFreq)+" Hz");
					    	theApp.writeLine(sb.toString(),Color.BLACK,theApp.boldFont);	
			    			
			    		}
			    	}
			    	else	{
			    		startCarrierCounter=0;
			    	}	
			    }
			}
		}
		else if (state==2)	{
			sampleCount++;
			
			double r[]=doRDFTFFTSpectrum(circBuf,waveData,0,false,(int)samplesPerSymbol,false);
			List<Complex> sc=extractCarrierSymbols(r);
			double rv=sc.get(11).getReal();
			double iv=sc.get(11).getImag();
			double mg=sc.get(11).getMagnitude();
			String line=Double.toString(rv)+","+Double.toString(iv)+","+Double.toString(mg);
	
			// Early symbol
			if (sampleCount==17)	{
				double ri[]=doRDFTFFTSpectrum(circBuf,waveData,0,false,(int)samplesPerSymbol,false);
				extractCarrierSymbols(ri);
				mag[0]=totalCarriersEnergy;
				line=line+",0";
			}
			// Mid Symbol
			else if (sampleCount==33)	{
				double ri[]=doRDFTFFTSpectrum(circBuf,waveData,0,false,(int)samplesPerSymbol,false);
				List<Complex> symbolComplex=extractCarrierSymbols(ri);
				mag[1]=totalCarriersEnergy;
				int a;
				for (a=0;a<symbolComplex.size();a++)	{
					realV[a]=symbolComplex.get(a).getReal();
					imagV[a]=symbolComplex.get(a).getImag();
				}
				line=line+",10000";
				
			}
			// Late symbol
			else if (sampleCount==49)	{
				double ri[]=doRDFTFFTSpectrum(circBuf,waveData,0,false,(int)samplesPerSymbol,false);
				extractCarrierSymbols(ri);
				mag[2]=totalCarriersEnergy;
				line=line+",0";
			}
			else line=line+",0";
			
			// End of symbol
			if (sampleCount==66)	{
				symbolCounter++;
				// Symbol timing code
				double pdif;
				double total=mag[0]+mag[2];
				if (mag[0]>mag[2])	{
					double d=mag[0]-mag[2];
					pdif=(d/total)*100;
					pdif=0-pdif;
					
				}
				else	{
					double d=mag[2]-mag[0];
					pdif=(d/total)*100;
				}
				addToTimingBuffer(pdif);
				pdif=getBufferAverage();
				sampleCount=(long)pdif/5;
				
				int a;
				StringBuffer sb=new StringBuffer();
				sb.append(Long.toString(symbolCounter)+",");
				for (a=0;a<12;a++)	{
					sb.append(Double.toString(realV[a])+","+Double.toString(imagV[a])+",");
				}
				sb.append(Double.toString(pdif));
				//theApp.debugDump(sb.toString());
				
			}
				
			//theApp.debugDump(line);	
			
		}
		return true;
	}	

	// Check we have a AT3x04 pilot tone here
	private boolean AT3x04PilotToneHunt (double spectrum[])	{
		int a,pbin;
		// Look if a particular bin in startCarrierList1 also exists in startCarrierList2 and startCarrierList3
		for (a=startCarrierList1.size()-1;a>=0;a--)	{
			pbin=startCarrierList1.get(a).getBinFFT();
			if (checkBinExists(startCarrierList2,pbin)==true)	{
				if (checkBinExists(startCarrierList3,pbin)==true)	{
					// Store this bin and return true
					pilotToneBin=pbin;
					return true;	
				}
			}
		}
		return false;
	}	
	
	
	// A method for checking in a bin exists in a carrier info list
	private boolean checkBinExists (List<CarrierInfo> cil,int bin)	{
		int a;
		for (a=0;a<cil.size();a++)	{
			if (cil.get(a).getBinFFT()==bin) return true;
		}
		return false;
	}
	
	// Look if we have at least half of the AT3x04 carriers in their expected places 
	// from what we believe is the pilot tone bin
	private boolean AT3x04CarrierConfirm (List<CarrierInfo> clist)	{
		int expectedCarrierBins[]=new int[12];
		int a,b,p=pilotToneBin-40;
		int findCounter=0;
		for (a=11;a>=0;a--){
			expectedCarrierBins[a]=p;
			p=p-20;
		}
		// Check if there are carriers where we think there should be
		for (a=0;a<clist.size();a++)	{
			for (b=0;b<12;b++)	{
				double dif=Math.abs(clist.get(a).getBinFFT()-expectedCarrierBins[b]);
				if (dif<2) findCounter++;
			}
		}
		
		if (findCounter>=6) return true;
		else return false;
	}
	
	// Populate the carrierBinNos[][][] variable
	private void populateCarrierTonesBins ()	{
		int binNos,carrierNos,lastCarrierBin=pilotToneBin-40;
		// Run though each carrier
		for (carrierNos=11;carrierNos>=0;carrierNos--)	{
			int mod=-10;
			for (binNos=0;binNos<20;binNos++)	{
				int rb=lastCarrierBin+mod;
				carrierBinNos[carrierNos][binNos][0]=returnRealBin(rb);
				carrierBinNos[carrierNos][binNos][1]=returnImagBin(rb);
				mod++;
			}
			lastCarrierBin=lastCarrierBin-20;
		}
	}
	
	// Do an inverse FFT to recover one particular carrier
	private double[] recoverCarrier (int carrierNo,double spectrumIn[])	{
		double spectrum[]=new double[spectrumIn.length];
		int b;
		for (b=0;b<20;b++)	{
				int rBin=carrierBinNos[carrierNo][b][0];
				int iBin=carrierBinNos[carrierNo][b][1];	
				spectrum[rBin]=spectrumIn[rBin];
				spectrum[iBin]=spectrumIn[iBin];
			}
		RDFTfft.realInverse(spectrum,false);		
		return spectrum;
	}	
	
	private List<Complex> extractCarrierSymbols (double fdata[])	{
		List<Complex> complexList=new ArrayList<Complex>();
		int carrierNo;
		totalCarriersEnergy=0.0;
		// Run through each carrier
		for (carrierNo=0;carrierNo<12;carrierNo++)	{
			int b;
			Complex total=new Complex();
			for (b=0;b<20;b++)	{
				int rBin=carrierBinNos[carrierNo][b][0];
				int iBin=carrierBinNos[carrierNo][b][1];
				Complex tbin=new Complex(fdata[rBin],fdata[iBin]);
				total=total.add(tbin);
			}
			// Add this to the list
			complexList.add(total);
			// Calculate the total energy
			totalCarriersEnergy=totalCarriersEnergy+total.getMagnitude();
		}
		return complexList;
	}	

	
	private void addToTimingBuffer(double in)	{
		timingBuffer[timingBufferCounter]=in;
		timingBufferCounter++;
		if (timingBufferCounter==TIMINGBUFFERSIZE) timingBufferCounter=0;
	}
	
	private double getBufferAverage()	{
		double total=0.0,size=0.0;
		int a;
		for (a=0;a<timingBuffer.length;a++)	{
			total=total+timingBuffer[a];
			size++;
		}
		return (total/size);
	}
	
	
	
}
