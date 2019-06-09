/*********************************************************************************
 * Copyright (c) 2010 Forschungszentrum Juelich GmbH 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * (1) Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the disclaimer at the end. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution.
 * 
 * (2) Neither the name of Forschungszentrum Juelich GmbH nor the names of its 
 * contributors may be used to endorse or promote products derived from this 
 * software without specific prior written permission.
 * 
 * DISCLAIMER
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *********************************************************************************/

package udt;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import udt.packets.ConnectionHandshake;
import udt.packets.Destination;
import udt.packets.KeepAlive;
import udt.packets.Shutdown;
import udt.util.Tools;

/**
 * server side session in client-server mode
 */
public class ServerSession extends UDTSession {

	private static final Logger logger=Logger.getLogger(ServerSession.class.getName());

	private final UDPEndPoint endPoint;

	//last received packet (for testing purposes)
	private UDTPacket lastPacket;
	
	private long cookie=0;//cd 2018-08-28
	//private String key="judp";//标记，暂时不用了，和c++一致
	private String client="";
	int n_handshake=0;
	public ServerSession(DatagramPacket dp, UDPEndPoint endPoint)throws SocketException,UnknownHostException{
		super("ServerSession localPort="+endPoint.getLocalPort()+" peer="+dp.getAddress()+":"+dp.getPort(),new Destination(dp.getAddress(),dp.getPort()));
		this.endPoint=endPoint;
		client=dp.getAddress()+":"+dp.getPort();
		logger.info("Created "+toString()+" talking to "+dp.getAddress()+":"+dp.getPort());
	}

	

	@Override
	public void received(UDTPacket packet, Destination peer){
		lastPacket=packet;

		if(packet instanceof ConnectionHandshake) {
			ConnectionHandshake connectionHandshake=(ConnectionHandshake)packet;
			logger.info("Received "+connectionHandshake);
           
			if (getState()<=ready){
				destination.setSocketID(connectionHandshake.getSocketID());
				if(getState()<=handshaking){
					setState(handshaking);
				}
				try{
					handleHandShake(connectionHandshake);
					n_handshake++;
					try{
						//理论上应该先检验cookie
						setState(ready);
						socket=new UDTSocket(endPoint, this);
						cc.init();
					}catch(Exception uhe){
						//session is invalid
						logger.log(Level.SEVERE,"",uhe);
						setState(invalid);
					}
				}catch(IOException ex){
					//session invalid
					logger.log(Level.WARNING,"Error processing ConnectionHandshake",ex);
					setState(invalid);
				}
				return;
			}
			else
			{
				//cd  回复
				try {
					handleHandShake(connectionHandshake);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}else if(packet instanceof KeepAlive) {
			socket.getReceiver().resetEXPTimer();
			active = true;
			return;
		}

		if(getState()== ready) {
			active = true;
            
			if (packet instanceof KeepAlive) {
				//nothing to do here
				return;
			}else if (packet instanceof Shutdown) {
				try{
					socket.getReceiver().stop();
				}catch(IOException ex){
					logger.log(Level.WARNING,"",ex);
				}
				setState(shutdown);
				System.out.println("SHUTDOWN ***");
				active = false;
				logger.info("Connection shutdown initiated by the other side.");
				return;
			}

			else{
				try{
				
					if(packet.forSender()){
						socket.getSender().receive(packet);
					}else{
						socket.getReceiver().receive(packet);	
					}
				}catch(Exception ex){
					//session invalid
					logger.log(Level.SEVERE,"",ex);
					setState(invalid);
				}
			}
			return;

		}


	}

	/**
	 * for testing use only
	 */
	UDTPacket getLastPacket(){
		return lastPacket;
	}

	/**
	 * handle the connection handshake:<br/>
	 * <ul>
	 * <li>set initial sequence number</li>
	 * <li>send response handshake</li>
	 * </ul>
	 * @param handshake
	 * @param peer
	 * @throws IOException
	 */
	protected void handleHandShake(ConnectionHandshake handshake)throws IOException{
		ConnectionHandshake responseHandshake = new ConnectionHandshake();
		//compare the packet size and choose minimun
		long clientBufferSize=handshake.getPacketSize();
		long myBufferSize=getDatagramSize();
		long bufferSize=Math.min(clientBufferSize, myBufferSize);
		long initialSequenceNumber=handshake.getInitialSeqNo();
		setInitialSequenceNumber(initialSequenceNumber);
		setDatagramSize((int)bufferSize);
		responseHandshake.setPacketSize(bufferSize);
		responseHandshake.setUdtVersion(4);
		responseHandshake.setInitialSeqNo(initialSequenceNumber);
		responseHandshake.setConnectionType(-1);
		responseHandshake.setMaxFlowWndSize(handshake.getMaxFlowWndSize());
		//tell peer what the socket ID on this side is 
		responseHandshake.setSocketID(mySocketID);
		responseHandshake.setDestinationID(this.getDestination().getSocketID());
		//cd 2018-08-28
		if(this.cookie==0)
		{
			this.cookie=createCookie();
		}
		responseHandshake.setcookie(cookie);
		
		responseHandshake.setSession(this);
		logger.info("Sending reply "+responseHandshake);
		endPoint.doSend(responseHandshake);
	}
	
	/**
	 * cd 
	* @Title: createcCookie
	* @Description: 生产Cookie
	* @param @return    参数
	* @return long    返回类型
	 */
	private long createCookie()
	{
		byte[] bytes=null;
		byte[] result=new byte[4];
		ByteBuffer buf=ByteBuffer.wrap(result);
		//修改用于c++一致
		String src=Tools.string2MD5(GetClientCookie());
		try {
			bytes=src.getBytes("utf-8");
		} catch (UnsupportedEncodingException e)
		{
			bytes=src.getBytes();
		}
		if(bytes.length>4)
		{
			buf.put(bytes, 0, 4);
		}
		else
		{
			buf.put(bytes);
		}
		buf.flip();
		return buf.getInt();
	}
	
	/**
	 * 
	* @Title: GetClientCookie
	* @Description: 输出字符串Cookie
	* @param @return    参数
	* @return String    返回类型
	 */
	private String GetClientCookie()
	{
		long timespan=(System.currentTimeMillis()-startTime)/60000;//转换分钟
		return client+":"+timespan;
	}
}

