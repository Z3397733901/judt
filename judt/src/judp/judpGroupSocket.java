/**
 * 
 */
package judp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

import udt.UDTSocket;


/**
 * 
 *     
 * ��Ŀ���ƣ�judt    
 * �����ƣ�judpGroupSocket    
 * ��������    ����Ŀ�ĵ�ַ����socket
 * �����ˣ�jinyu    
 * ����ʱ�䣺2018��8��25�� ����10:53:59    
 * �޸��ˣ�jinyu    
 * �޸�ʱ�䣺2018��8��25�� ����10:53:59    
 * �޸ı�ע��    
 * @version   1.0   
 *
 */
public class judpGroupSocket {
private ArrayList<UDTSocket> list=new ArrayList<UDTSocket>();
private static final Logger logger=Logger.getLogger(judpGroupSocket.class.getName());
public judpGroupSocket()
{
	
}

/**
 * ����socket
 * @param socket
 */
public void addSocket(UDTSocket socket)
{
	list.add(socket);
}

/**
 * ��ȡ������socket
 * �����Ƴ���������socket
 * @return
 */
public UDTSocket getSocket()
{
	
	int index=-1;
	int size=list.size();
	for( int i = 0 ; i < size ; i++) {
	    try {
	    	if(index==-1)
	    	{
			  if(list.get(i).getInputStream().isHasData())
			   {
				 //�Ѿ��ҵ���������Ƴ���
				  index=i;
				   i=-1;//���±���
			   }
	    	}
	    	else
	    	{
	    		//
	    		if(i==index)
	    		{
	    			continue;
	    		}
	    		else
	    		{
	    			list.get(i).close();
	    			long id=list.get(i).getSession().getSocketID();
	    			list.get(i).getEndpoint().removeSession(id);
	    			list.get(i).getReceiver().stop();
	    			list.get(i).getSender().stop();
	    			list.get(i).getSender().pause();
	    			logger.info("�Ƴ�����socket:"+id);
	    		}
	    		
	    	}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	if(index!=-1)
	{
		return list.get(index);
	}
	return null;
	
}

/**
 * �������socket
 */
public void clear()
{
	list.clear();
}
}