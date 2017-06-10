package com.swblabs.notify

import grails.transaction.Transactional

import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.geom.PathIterator
import java.awt.image.BufferedImage
import java.text.SimpleDateFormat
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@Transactional
class BoardBotService {

	String[] pollURLs=["http://localhost:8080/admin/bbPoll","http://ibb.jjrobots.com/ibbsvr/ibb.php"]
	Map botHandlerMap=[:]

	class BotHandler {
		String errCode=null //if set transmit error code on next poll
		LinkedBlockingQueue blockQueue=new LinkedBlockingQueue()
		byte[] last=null
		int seq=0 //block sequence counter

		def sendBlock(cmds) { //send block of commands as int array
			cmds[3]=seq
			seq=(seq+1)%256 //just keep to 8-bits
			blockQueue.add(packBlock(cmds))
		}

		def sendError(String code) { //send error -- in particular "ER"
			errCode=code
		}

		def poll() {
			poll(-1)
		}

		def poll(int ack) {
			if (errCode!=null) { //mainly for "ER" to reset everything
				def result=errCode
				errCode=null
				blockQueue.clear() //flush queue
				last=null //no packet to ack
				return(result)
			} else {
				if (last!=null) {
					if (ack==-1 || ((0xFF&last[5])!=(0xFF&ack))) { //if no ack, or ack not satisfactory return last one again
						return(last)
					}
				}
				def result=blockQueue.poll(30,TimeUnit.SECONDS) //30 second long poll
				if (result==null) { //if nothing
					last=null //clear last
					return("OK") //return OK to begin next poll cycle
				} else {
					last=result //save last block for possible retransmit
					return(result) //return it
				}
			}
		}
	}
	
	def poll(String mac) {
		poll(mac,-1)
	}
	
	synchronized getBotHandler(String mac) {
		BotHandler bh=botHandlerMap[mac]
		if (bh==null) {
			bh=new BotHandler()
			botHandlerMap[mac]=bh
		}
		return(bh)
	}
	
	def poll(String mac,int ack) {
		println("Polling mac=${mac} ack=${ack}")
		BotHandler bh=getBotHandler(mac)
		bh.poll(ack)
	}
	
	def sendBlock(String mac,cmds) {
		BotHandler bh=getBotHandler(mac)
		bh.sendBlock(cmds)
	}
	
	def sendBlock(cmds) {
		BoardBot bb=BoardBot.first()
		BotHandler bh=getBotHandler(bb.mac)
		bh.sendBlock(cmds)
	}

	def test() {
		BoardBot bb=BoardBot.first()
		//sendBlock(bb.mac,testBoard[0])
		SimpleDateFormat sdf=new SimpleDateFormat("HH:mm")
		String last=""
		while(true) {
			String next=sdf.format(new Date())
			if (next!=last) plotText(next)
			last=next
			Thread.sleep(5000)
		}
	}
	
	def clear() {
		BoardBot bb=BoardBot.first()
		sendBlock(bb.mac,clearBoard[0])
	}
	
	def plotText(String text) {
		BufferedImage img=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB) //any other way to get a g2d?
		Graphics2D g2=img.createGraphics()
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			RenderingHints.VALUE_ANTIALIAS_ON)
		g2.setRenderingHint(RenderingHints.KEY_RENDERING,
			RenderingHints.VALUE_RENDER_QUALITY)
		FontRenderContext frc = g2.getFontRenderContext()
		Font font = new Font("Helvetica", 1, 120) //parm font later
		TextLayout tl= new TextLayout(text, font, frc)
		Shape outline = tl.getOutline(null)
		Rectangle rect = outline.getBounds()
		//println(rect)
		int bw=3600 //board width
		int bh=1200 //board height
		int tmargin=200 //minimum margin around text
		double sx=(bw-2*tmargin)/rect.width //scale if based on x
		double sy=(bh-2*tmargin)/rect.height //scale if based on y
		double scale=sx
		if (sy<sx) scale=sy
		int xmargin=(bw-scale*rect.width)/2
		int ymargin=(bh-scale*rect.height)/2
		//println("xmargin="+xmargin)
		//println("ymargin="+ymargin)
		double tx=-1*rect.x
		double ty=-1*rect.y
		def block=[4009,4001,4009,0,4001,4001,4003,4003]
		/*
		//uncomment to show bounding box
		block.addAll([(int)(xmargin+scale*(tx+rect.x)),(int)(bh-ymargin-scale*(ty+rect.y))])
		block.addAll([4004,4004,(int)(xmargin+scale*(tx+rect.x)),(int)(bh-ymargin-scale*(ty+rect.y+rect.height))])
		block.addAll([(int)(xmargin+scale*(tx+rect.x+rect.width)),(int)(bh-ymargin-scale*(ty+rect.y+rect.height))])
		block.addAll([(int)(xmargin+scale*(tx+rect.x+rect.width)),(int)(bh-ymargin-scale*(ty+rect.y))])
		block.addAll([(int)(xmargin+scale*(tx+rect.x)),(int)(bh-ymargin-scale*(ty+rect.y))])
		*/
		PathIterator path=outline.getPathIterator(null,0.25)
		float[] pt=new float[2]
		boolean up=true
		while(!path.isDone()) {
			int type=path.currentSegment(pt)
			if (type==PathIterator.SEG_CLOSE) {
			  block.addAll([4003,4003,0,0,4002,4002])
			  //println("close")
			} else if (type==PathIterator.SEG_LINETO) {
			  if (up) {
				  block.addAll([4004,4004])
				  up=false
			  }
			  block.addAll([(int)(xmargin+scale*(tx+pt[0])),(int)(bh-ymargin-scale*(ty+pt[1]))])
			  //println("lineto")
			} else if (type==PathIterator.SEG_MOVETO) {
			  if (!up) {
				  block.addAll([4003,4003])
				  up=true
			  }
			  block.addAll([(int)(xmargin+scale*(tx+pt[0])),(int)(bh-ymargin-scale*(ty+pt[1]))])
			  //println("moveto")
			}			
			//println("pt="+pt)
			path.next()
		}
		sendBlock(block)
	}

	def receiver(int ext) { //test receiver on first board bot
		BoardBot bb=BoardBot.first()
		def blocks=receiver(bb.mac,ext)
		return(blocks)
	}

	//examples of raw block sequences for decoding practice
	def clearBoard=[[4009,4001,4009,11,4001,4001,4003,0,1,1,10,10,4005,0,3580,10,3580,120,10,120,10,230,3580,230,3580,340,10,340,10,450,3580,450,3580,560,10,560,10,670,3580,670,3580,780,10,780,10,890,3580,890,3580,1000,10,1000,10,1110,3580,1110,3580,1000,10,1000,10,890,3580,890,3580,780,10,780,10,670,3580,670,3580,560,10,560,10,450,3580,450,3580,340,10,340,10,230,3580,230,3580,120,10,120,10,10,3580,10,3580,0,10,0,10,0,4003,0,1,1,4002,4002]]
	def testBoard=[[4009, 4001, 4009, 56, 4001, 4001, 4003, 0, 1, 1, 4003, 0, 1395, 450, 4004, 0, 1395, 712, 1295, 712, 1295, 748, 1534, 748, 1534, 712, 1434, 712, 1434, 450, 1395, 450, 1395, 450, 4003, 0, 4003, 0, 1592, 450, 4004, 0, 1592, 748, 1778, 748, 1778, 712, 1631, 712, 1631, 631, 1768, 631, 1768, 596, 1631, 596, 1631, 485, 1778, 485, 1778, 450, 1592, 450, 1592, 450, 4003, 0, 4003, 0, 1918, 445, 4004, 0, 1903, 445, 1888, 447, 1875, 449, 1863, 451, 1841, 459, 1821, 468, 1821, 518, 1824, 518, 1834, 509, 1844, 501, 1856, 495, 1868, 489, 1880, 485, 1892, 481, 1904, 480, 1916, 479, 1931, 480, 1944, 482, 1956, 487, 1965, 493, 1972, 500, 1978, 508, 1981, 518, 1982, 529, 1981, 537, 1980, 545, 1977, 552, 1973, 558, 1968, 563, 1962, 567, 1954, 571, 1945, 574, 1920, 581, 1891, 588, 1877, 592, 1864, 598, 1852, 605, 1842, 614, 1834, 625, 1828, 637, 1824, 652, 1823, 668, 1823, 677, 1825, 685, 1827, 693, 1830, 701, 1834, 708, 1839, 715, 1845, 722, 1851, 728, 1858, 734, 1866, 739, 1874, 743, 1883, 747, 1892, 749, 1902, 751, 1912, 753, 1923, 753, 1947, 752, 1970, 748, 1992, 742, 2011, 734, 2011, 686, 2008, 686, 2001, 693, 1992, 699, 1983, 704, 1972, 709, 1961, 713, 1949, 716, 1938, 718, 1926, 719, 1912, 718, 1901, 716, 1890, 712, 1881, 706, 1874, 699, 1868, 691, 1865, 682, 1864, 672, 1865, 663, 1867, 655, 1870, 648, 1874, 642, 1880, 637, 1886, 632, 1894, 629, 1903, 626, 1934, 618, 1964, 610, 1978, 605, 1991, 598, 2001, 591, 2009, 582, 2015, 572, 2019, 560, 2022, 548, 2023, 535, 2023, 526, 2021, 518, 2019, 509, 2016, 501, 2012, 492, 2007, 485, 2002, 478, 1996, 472, 1989, 466, 1981, 460, 1972, 456, 1964, 452, 1954, 449, 1943, 447, 1931, 446, 1918, 445, 1918, 445, 4003, 0, 4003, 0, 2151, 450, 4004, 0, 2151, 712, 2051, 712, 2051, 748, 2290, 748, 2290, 712, 2190, 712, 2190, 450, 2151, 450, 2151, 450, 4003, 0, 4003, 0, 1, 1, 4002, 4002]]
	static Map blockNumberMap=[:]

	/*
	 * Receive the next drawing for the bot as a list of blocks (or empty if a reset is sent).  We
	 * might retransmit the set (possibly altering sequence numbers).
	 */
	def receiver(String mac,int ext) {
		//return(clearBoard)
		def blockNumber=blockNumberMap[mac]
		if (blockNumber==null) {
			blockNumber=-1
		}
		def blocks=[]
		while(true) {
			String url=pollURLs[ext]+"?ID_IWBB="+mac
			if (blockNumber==-1) {
				url+="&STATUS=READY"
			} else {
				url+="&STATUS=ACK&NUM="+blockNumber
			}
			def data=new URL(url).getBytes()
			println("size="+data.size())
			if (data.size()>6) {
				def block=[]
				for(int i=0;i<data.size();i=i+3) {
					block<<(((0xFF&data[i])<<4)|((0xF0&data[i+1])>>4)) //decode high 12-bits of 3 bytes
					block<<(((data[i+1]&0xF)<<8)|(0xFF&data[i+2])) //decode low 12-bits of 3 bytes
				}
				//println(block)
				blockNumber=block[3]
				if (block[4]==4001 && block[5]==4001) {
					blocks=[block] //first block
				} else {
					blocks<<block
				}
				int size=block.size()
				if (block[size-2]==4002 && block[size-1]==4002) {  //watches for end of draw so web page can clear and draw at once
					blockNumberMap[mac]=blockNumber				   //could relax this to simulate interactive plotting of blocks
					return(blocks) //end of drawing return set of blocks
				}
			} else {
				//ER=reset, OK=ok
				String resp=new String(data)
				print(resp)
				blockNumberMap[mac]=-1
				if (resp=="ER") return([])
				if (resp=="OK") return(blocks)
			}
		}
	}

	byte[] packBlock(cmds) {
		int len=cmds.size()
		if (len%2==0) {
			byte[] buf=new byte[len/2*3]
			for(int i=0;i<len;i=i+2) {
				int pos=i/2*3
				buf[pos]=0xFF&(cmds[i]>>4)
				buf[pos+1]=((0xF&cmds[i])<<4)|(0xF&(cmds[i+1]>>8))
				buf[pos+2]=0xFF&cmds[i+1]
			}
			return(buf)
		} else {
			println("Block must be multiple of two to pack")
			return(null)
		}
	}

	def showPackedBlock(byte[] buf) {
		for(int i=0;i<buf.size();i=i+3) {
			println(String.format("%02X%02X%02X",buf[i],buf[i+1],buf[i+2]))
		}
	}
}
